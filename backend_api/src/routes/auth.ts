import { Router } from "express";
import bcrypt from "bcryptjs";
import crypto from "crypto";
import jwt from "jsonwebtoken";
import { User, RefreshToken, UserRole, AuthProvider, VerificationStatus } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { signAccessToken, authenticate, AuthRequest } from "../middleware/auth";

const router = Router();

const REFRESH_TOKEN_TTL_DAYS = 30;

function hashToken(token: string) {
  return crypto.createHash("sha256").update(token).digest("hex");
}

async function issueTokenPair(user: {
  id: string;
  institutionId: string;
  role: UserRole;
  verificationStatus: VerificationStatus;
  profileCompleteness: number;
}) {
  const accessToken = signAccessToken({
    userId: user.id,
    institutionId: user.institutionId,
    role: user.role,
  });

  const refreshToken = crypto.randomUUID();
  await RefreshToken.create({
    userId: user.id,
    tokenHash: hashToken(refreshToken),
    issuedAt: new Date(),
    expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_DAYS * 24 * 60 * 60 * 1000),
  });

  return {
    accessToken,
    refreshToken,
    expiresIn: 900,
    user: {
      id: user.id,
      role: user.role,
      verificationStatus: user.verificationStatus,
      profileComplete: user.profileCompleteness,
    },
  };
}

router.post(
  "/sso/:provider",
  asyncHandler(async (req, res) => {
    const { idToken, institutionId, fullName } = req.body;
    if (!idToken) return res.status(400).json(apiError("VALIDATION_ERROR", "idToken is required", "idToken"));

    const decoded = jwt.decode(idToken) as { email?: string; sub?: string; name?: string } | null;
    if (!decoded?.email) {
      return res.status(401).json(apiError("INVALID_TOKEN", "Could not verify SSO token"));
    }

    let user = await User.findOne({ email: decoded.email.toLowerCase() });

    if (!user) {
      if (!institutionId) {
        return res
          .status(404)
          .json(apiError("NOT_FOUND", "No account for this SSO identity; institutionId is required to create one", "institutionId"));
      }
      user = await User.create({
        institutionId,
        role: UserRole.GUARDIAN_LEARNER,
        fullName: fullName || decoded.name || decoded.email.split("@")[0],
        email: decoded.email.toLowerCase(),
        authProvider: AuthProvider.GOOGLE,
        verificationStatus: VerificationStatus.UNVERIFIED,
        profileCompleteness: 20,
      });
    }

    const tokens = await issueTokenPair({
      id: String(user._id),
      institutionId: String(user.institutionId),
      role: user.role,
      verificationStatus: user.verificationStatus,
      profileCompleteness: user.profileCompleteness,
    });
    res.status(200).json(tokens);
  })
);

router.post(
  "/register",
  asyncHandler(async (req, res) => {
    const { email, password, fullName, institutionId } = req.body;
    if (!email || !password || !fullName || !institutionId) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "email, password, fullName, institutionId are required"));
    }

    const existing = await User.findOne({ email: String(email).toLowerCase() });
    if (existing) {
      return res.status(409).json(apiError("EMAIL_TAKEN", "An account with this email already exists", "email"));
    }

    const passwordHash = await bcrypt.hash(password, 12);
    const user = await User.create({
      institutionId,
      role: UserRole.GUARDIAN_LEARNER,
      fullName,
      email: String(email).toLowerCase(),
      passwordHash,
      authProvider: AuthProvider.PASSWORD,
      verificationStatus: VerificationStatus.UNVERIFIED,
      profileCompleteness: 20,
    });

    res.status(201).json({
      id: user._id,
      institutionId: user.institutionId,
      role: user.role,
      verificationStatus: user.verificationStatus,
      profileComplete: user.profileCompleteness,
    });
  })
);

router.post(
  "/login",
  asyncHandler(async (req, res) => {
    const { email, password } = req.body;
    if (!email || !password) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "email and password are required"));
    }

    const user = await User.findOne({ email: String(email).toLowerCase() }).select("+passwordHash");
    if (!user?.passwordHash || !(await bcrypt.compare(password, user.passwordHash))) {
      return res.status(401).json(apiError("INVALID_CREDENTIALS", "Email or password is incorrect"));
    }

    const tokens = await issueTokenPair({
      id: String(user._id),
      institutionId: String(user.institutionId),
      role: user.role,
      verificationStatus: user.verificationStatus,
      profileCompleteness: user.profileCompleteness,
    });
    res.status(200).json(tokens);
  })
);

router.post(
  "/refresh",
  asyncHandler(async (req, res) => {
    const { refreshToken } = req.body;
    if (!refreshToken) return res.status(400).json(apiError("VALIDATION_ERROR", "refreshToken is required"));

    const tokenHash = hashToken(refreshToken);
    const stored = await RefreshToken.findOne({ tokenHash });

    if (!stored || stored.revokedAt || stored.expiresAt < new Date()) {
      return res.status(401).json(apiError("INVALID_TOKEN", "Refresh token is invalid, expired, or revoked"));
    }

    const user = await User.findById(stored.userId);
    if (!user) return res.status(401).json(apiError("INVALID_TOKEN", "Account no longer exists"));

    const newRefreshToken = crypto.randomUUID();
    stored.revokedAt = new Date();
    stored.replacedByTokenHash = hashToken(newRefreshToken);
    await stored.save();

    await RefreshToken.create({
      userId: user._id,
      tokenHash: hashToken(newRefreshToken),
      issuedAt: new Date(),
      expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_DAYS * 24 * 60 * 60 * 1000),
    });

    const accessToken = signAccessToken({
      userId: String(user._id),
      institutionId: String(user.institutionId),
      role: user.role,
    });

    res.status(200).json({ accessToken, refreshToken: newRefreshToken, expiresIn: 900 });
  })
);

router.post(
  "/logout",
  asyncHandler(async (req, res) => {
    const { refreshToken } = req.body;
    if (refreshToken) {
      await RefreshToken.updateOne(
        { tokenHash: hashToken(refreshToken) },
        { $set: { revokedAt: new Date(), revokedReason: "logout" } }
      );
    }
    res.status(204).send();
  })
);

router.post(
  "/change-password",
  authenticate,
  asyncHandler(async (req: AuthRequest, res) => {
    const { currentPassword, newPassword } = req.body;
    if (!currentPassword || !newPassword) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "currentPassword and newPassword are required"));
    }

    const user = await User.findById(req.auth!.userId).select("+passwordHash");
    if (!user?.passwordHash || !(await bcrypt.compare(currentPassword, user.passwordHash))) {
      return res.status(401).json(apiError("INVALID_CREDENTIALS", "Current password is incorrect", "currentPassword"));
    }

    user.passwordHash = await bcrypt.hash(newPassword, 12);
    user.mustChangePassword = false;
    await user.save();

    await RefreshToken.updateMany(
      { userId: user._id, revokedAt: { $exists: false } },
      { $set: { revokedAt: new Date(), revokedReason: "password_changed" } }
    );

    res.status(200).json({ mustChangePassword: false });
  })
);

export default router;
