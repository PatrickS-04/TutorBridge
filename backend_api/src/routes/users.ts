import { Router } from "express";
import multer from "multer";
import { Booking, BookingStatus, User, UserRole, UserSettings, VerificationDocument, VerificationStatus, DocumentType } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 10 * 1024 * 1024 } });

/**
 * Bookable tutors at the caller's institution - what the Find a Tutor screen
 * lists. Mirrors User.isBookable(): tutor, verified, not deactivated or pending deletion.
 * (`deletionScheduledFor: null` also matches documents where the field is absent.)
 */
router.get(
  "/tutors",
  asyncHandler(async (req: AuthRequest, res) => {
    const result = await paginate(
      User,
      {
        institutionId: req.auth!.institutionId,
        role: UserRole.TUTOR,
        verificationStatus: VerificationStatus.VERIFIED,
        deletionScheduledFor: null,
        deactivatedAt: null,
      },
      req,
      ["subjectIds", "areaId"]
    );

    // Sessions actually taught, for the "Total Consultations" figure. One
    // grouped count for the whole page rather than a query per tutor.
    const ids = result.data.map((tutor) => tutor._id);
    const completed = await Booking.aggregate<{ _id: unknown; count: number }>([
      { $match: { tutorId: { $in: ids }, status: BookingStatus.COMPLETED } },
      { $group: { _id: "$tutorId", count: { $sum: 1 } } },
    ]);
    const countByTutor = new Map(completed.map((row) => [String(row._id), row.count]));

    res.status(200).json({
      ...result,
      data: result.data.map((tutor) => ({
        ...tutor.toObject(),
        completedSessions: countByTutor.get(String(tutor._id)) ?? 0,
      })),
    });
  })
);

router.get(
  "/me",
  asyncHandler(async (req: AuthRequest, res) => {
    // areaId is populated as well so the client reads one shape for a user
    // whether it came from here or from GET /users/tutors.
    const user = await User.findById(req.auth!.userId).populate(["subjectIds", "areaId"]);
    if (!user) return res.status(404).json(apiError("NOT_FOUND", "User not found"));
    res.status(200).json(user);
  })
);

router.put(
  "/me",
  asyncHandler(async (req: AuthRequest, res) => {
    const { fullName, bio, hourlyRate, subjectIds, areaId, gradeLevel, availability, department } = req.body;
    // Only overwrite what the caller actually sent - the availability screen
    // PUTs availability alone and must not blank out the rest of the profile.
    const updates = Object.fromEntries(
      Object.entries({ fullName, bio, hourlyRate, subjectIds, areaId, gradeLevel, availability, department }).filter(
        ([, value]) => value !== undefined
      )
    );
    const user = await User.findByIdAndUpdate(
      req.auth!.userId,
      { $set: updates },
      { new: true, runValidators: true }
    );
    if (!user) return res.status(404).json(apiError("NOT_FOUND", "User not found"));
    res.status(200).json(user);
  })
);

router.get(
  "/me/completeness",
  asyncHandler(async (req: AuthRequest, res) => {
    const user = await User.findById(req.auth!.userId);
    if (!user) return res.status(404).json(apiError("NOT_FOUND", "User not found"));

    const checks: Record<string, boolean> = {
      profilePhoto: !!user.profilePhotoUrl,
      bio: !!user.bio,
      hourlyRate: user.hourlyRate != null,
      subjects: user.subjectIds.length > 0,
      availability: user.availability.length > 0,
    };
    const missingFields = Object.entries(checks)
      .filter(([, present]) => !present)
      .map(([field]) => field);
    const percentage = Math.round(
      ((Object.keys(checks).length - missingFields.length) / Object.keys(checks).length) * 100
    );

    user.profileCompleteness = percentage;
    await user.save();

    res.status(200).json({ percentage, missingFields });
  })
);

router.get(
  "/me/settings",
  asyncHandler(async (req: AuthRequest, res) => {
    let settings = await UserSettings.findOne({ userId: req.auth!.userId });
    if (!settings) settings = await UserSettings.create({ userId: req.auth!.userId });
    res.status(200).json(settings);
  })
);

router.put(
  "/me/settings",
  asyncHandler(async (req: AuthRequest, res) => {
    const { language, notifications, smsFallback, biometricEntry, theme } = req.body;
    const settings = await UserSettings.findOneAndUpdate(
      { userId: req.auth!.userId },
      { $set: { language, notifications, smsFallback, biometricEntry, theme } },
      { new: true, upsert: true, runValidators: true }
    );
    res.status(200).json(settings);
  })
);

router.post(
  "/me/verification-documents",
  upload.single("file"),
  asyncHandler(async (req: AuthRequest, res) => {
    const { type, requestedSubjectId } = req.body;
    if (!req.file || !type) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "file and type are required"));
    }
    if (!Object.values(DocumentType).includes(type)) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "type must be IDENTITY_DOCUMENT or QUALIFICATION", "type"));
    }

    const storagePath = `verification/${req.auth!.userId}/${Date.now()}-${req.file.originalname}`;

    await VerificationDocument.create({
      institutionId: req.auth!.institutionId,
      userId: req.auth!.userId,
      type,
      requestedSubjectId: requestedSubjectId || undefined,
      storagePath,
      mimeType: req.file.mimetype,
      sizeBytes: req.file.size,
      status: VerificationStatus.PENDING,
      submittedAt: new Date(),
    });

    await User.findByIdAndUpdate(req.auth!.userId, {
      $set: { verificationStatus: VerificationStatus.PENDING },
    });

    res.status(202).json({ verificationStatus: VerificationStatus.PENDING, submittedAt: new Date() });
  })
);

router.get(
  "/me/verification-status",
  asyncHandler(async (req: AuthRequest, res) => {
    const user = await User.findById(req.auth!.userId);
    if (!user) return res.status(404).json(apiError("NOT_FOUND", "User not found"));

    const latestDoc = await VerificationDocument.findOne({ userId: user._id }).sort({ submittedAt: -1 });

    res.status(200).json({
      status: user.verificationStatus,
      submittedAt: latestDoc?.submittedAt ?? null,
      reviewedAt: latestDoc?.reviewedAt ?? null,
      rejectionReason: latestDoc?.rejectionReason ?? null,
    });
  })
);

router.delete(
  "/me",
  asyncHandler(async (req: AuthRequest, res) => {
    const scheduledFor = new Date(Date.now() + 14 * 24 * 60 * 60 * 1000); // 14-day grace period
    await User.findByIdAndUpdate(req.auth!.userId, { $set: { deletionScheduledFor: scheduledFor } });
    res.status(202).json({ message: "Account deletion scheduled", scheduledFor });
  })
);

export default router;
