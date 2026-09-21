import { Request, Response, NextFunction } from "express";
import admin from "firebase-admin";
import { User, UserRole } from "../models";

export interface FirebaseIdentity {
  uid: string;
  email?: string;
  name?: string;
  /** "password", "google.com", ... - straight from the Firebase token. */
  signInProvider?: string;
}

export interface AuthClaims {
  userId: string;
  institutionId: string;
  role: UserRole;
}

export interface AuthRequest extends Request {
  firebase?: FirebaseIdentity;
  auth?: AuthClaims;
}

// Firebase is TutorBridge's identity provider: the app signs in with the
// Firebase SDK and sends the resulting ID token as the bearer. Credentials
// are read from GOOGLE_APPLICATION_CREDENTIALS (path to a service-account
// JSON) - see .env.
// Reference: Firebase. 2026. Add the Firebase Admin SDK to your server. [Online].
//   Available at: https://firebase.google.com/docs/admin/setup [Accessed 21 September 2026].
if (admin.apps.length === 0) {
  admin.initializeApp({ credential: admin.credential.applicationDefault() });
}

function unauthenticated(res: Response, code: string, message: string) {
  return res.status(401).json({ error: { code, message, field: null } });
}

// Reference: Firebase. 2026. Verify ID tokens. [Online].
//   Available at: https://firebase.google.com/docs/auth/admin/verify-id-tokens [Accessed 21 September 2026].
/**
 * Verifies the Firebase ID token and nothing else. Use this for routes that
 * must work before a TutorBridge profile exists - i.e. POST /auth/bootstrap.
 */
export async function verifyFirebase(req: AuthRequest, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  if (!header?.startsWith("Bearer ")) {
    return unauthenticated(res, "UNAUTHENTICATED", "Missing bearer token");
  }

  try {
    const decoded = await admin.auth().verifyIdToken(header.slice("Bearer ".length));
    req.firebase = {
      uid: decoded.uid,
      email: decoded.email,
      name: decoded.name,
      signInProvider: decoded.firebase?.sign_in_provider,
    };
    next();
  } catch {
    return unauthenticated(res, "INVALID_TOKEN", "Firebase ID token is invalid or expired");
  }
}

/**
 * Verifies the Firebase ID token, then resolves it to this user's TutorBridge
 * profile so routes can keep reading req.auth.userId / institutionId / role.
 *
 * ponytail: one User lookup per authenticated request; add a uid -> claims
 * cache (or put the claims in Firebase custom claims) if it ever shows up in
 * profiling.
 */
export function authenticate(req: AuthRequest, res: Response, next: NextFunction) {
  verifyFirebase(req, res, async () => {
    try {
      const user = await User.findOne({ firebaseUid: req.firebase!.uid });
      if (!user) {
        return res.status(403).json({
          error: {
            code: "NO_PROFILE",
            message: "No TutorBridge profile for this account; call POST /auth/bootstrap first",
            field: null,
          },
        });
      }

      req.auth = {
        userId: String(user._id),
        institutionId: String(user.institutionId),
        role: user.role,
      };
      next();
    } catch (error) {
      next(error);
    }
  });
}

export function requireRole(...roles: UserRole[]) {
  return (req: AuthRequest, res: Response, next: NextFunction) => {
    if (!req.auth || !roles.includes(req.auth.role)) {
      return res.status(403).json({
        error: { code: "FORBIDDEN", message: "Role not permitted for this action", field: null },
      });
    }
    next();
  };
}
