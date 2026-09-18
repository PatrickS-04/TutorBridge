import { Router } from "express";
import {
  TuitionRequest,
  Application,
  User,
  UserRole,
  TuitionRequestStatus,
  ApplicationStatus,
  NotificationCategory,
} from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, requireRole, AuthRequest } from "../middleware/auth";
import { notify } from "../utils/notify";

const router = Router();
router.use(authenticate);

router.post(
  "/",
  requireRole(UserRole.GUARDIAN_LEARNER),
  asyncHandler(async (req: AuthRequest, res) => {
    const { subjectId, areaId, gradeLevel, mode, description, preferredRateBand, availability } = req.body;
    if (!subjectId || !areaId || !mode) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "subjectId, areaId, and mode are required"));
    }

    const tuitionRequest = await TuitionRequest.create({
      institutionId: req.auth!.institutionId,
      studentId: req.auth!.userId,
      subjectId,
      areaId,
      gradeLevel,
      mode,
      description,
      preferredRateBand,
      availability,
      status: TuitionRequestStatus.OPEN,
    });

    res.status(201).json({
      id: tuitionRequest._id,
      status: tuitionRequest.status,
      matchScore: tuitionRequest.matchScore ?? null,
      createdAt: (tuitionRequest as any).createdAt,
    });
  })
);

router.get(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const filter: Record<string, unknown> = { institutionId: req.auth!.institutionId };
    if (req.query.subjectId) filter.subjectId = req.query.subjectId;
    if (req.query.areaId) filter.areaId = req.query.areaId;
    if (req.query.status) filter.status = req.query.status;
    // mine=true is the student's own board, which shows every status. Without
    // it this is the tutor board, which only ever shows open requests.
    else if (req.query.mine !== "true") filter.status = TuitionRequestStatus.OPEN;
    if (req.query.mine === "true") filter.studentId = req.auth!.userId;

    const result = await paginate(TuitionRequest, filter, req, ["subjectId", "areaId", "studentId"]);
    res.status(200).json(result);
  })
);

router.get(
  "/:id",
  asyncHandler(async (req: AuthRequest, res) => {
    const tuitionRequest = await TuitionRequest.findOne({
      _id: req.params.id,
      institutionId: req.auth!.institutionId,
    }).populate(["subjectId", "areaId", "studentId"]);
    if (!tuitionRequest) return res.status(404).json(apiError("NOT_FOUND", "Tuition request not found"));
    res.status(200).json(tuitionRequest);
  })
);

router.put(
  "/:id",
  asyncHandler(async (req: AuthRequest, res) => {
    const { description, preferredRateBand } = req.body;
    const tuitionRequest = await TuitionRequest.findOneAndUpdate(
      { _id: req.params.id, studentId: req.auth!.userId, institutionId: req.auth!.institutionId },
      { $set: { description, preferredRateBand } },
      { new: true, runValidators: true }
    );
    if (!tuitionRequest) return res.status(404).json(apiError("NOT_FOUND", "Tuition request not found or not owned by caller"));
    res.status(200).json(tuitionRequest);
  })
);

router.post(
  "/:id/applications",
  requireRole(UserRole.TUTOR),
  asyncHandler(async (req: AuthRequest, res) => {
    const tutor = await User.findById(req.auth!.userId);
    if (tutor?.verificationStatus !== "VERIFIED") {
      return res.status(403).json(apiError("UNVERIFIED", "Only verified tutors may apply"));
    }

    const tuitionRequest = await TuitionRequest.findOne({
      _id: req.params.id,
      institutionId: req.auth!.institutionId,
      status: TuitionRequestStatus.OPEN,
    });
    if (!tuitionRequest) return res.status(404).json(apiError("NOT_FOUND", "Open tuition request not found"));

    const { message, proposedRate } = req.body;
    if (proposedRate == null) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "proposedRate is required", "proposedRate"));
    }

    let application;
    try {
      application = await Application.create({
        institutionId: req.auth!.institutionId,
        tuitionRequestId: tuitionRequest._id,
        tutorId: req.auth!.userId,
        message,
        proposedRate,
        status: ApplicationStatus.PENDING,
      });
    } catch (err: any) {
      if (err?.code === 11000) {
        return res.status(409).json(apiError("ALREADY_APPLIED", "You have already applied to this request"));
      }
      throw err;
    }

    tuitionRequest.applicationCount += 1;
    await tuitionRequest.save();

    notify(
      req.auth!.institutionId,
      String(tuitionRequest.studentId),
      NotificationCategory.APPLICATION,
      "New tutor application",
      `${tutor.fullName} offered to take your tuition request.`,
      `tutorbridge://requests/${tuitionRequest._id}`
    );

    res.status(201).json({
      id: application._id,
      tuitionRequestId: application.tuitionRequestId,
      status: application.status,
      createdAt: (application as any).createdAt,
    });
  })
);

router.get(
  "/:id/applications",
  asyncHandler(async (req: AuthRequest, res) => {
    const tuitionRequest = await TuitionRequest.findOne({
      _id: req.params.id,
      studentId: req.auth!.userId,
    });
    if (!tuitionRequest) return res.status(404).json(apiError("NOT_FOUND", "Tuition request not found or not owned by caller"));

    const result = await paginate(Application, { tuitionRequestId: tuitionRequest._id }, req, "tutorId");
    res.status(200).json(result);
  })
);

export default router;
