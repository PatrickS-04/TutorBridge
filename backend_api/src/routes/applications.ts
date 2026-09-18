import { Router } from "express";
import { Application, TuitionRequest, UserRole, ApplicationStatus, TuitionRequestStatus } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, requireRole, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);

router.get(
  "/mine",
  requireRole(UserRole.TUTOR),
  asyncHandler(async (req: AuthRequest, res) => {
    const result = await paginate(Application, { tutorId: req.auth!.userId }, req, "tuitionRequestId");
    res.status(200).json(result);
  })
);

router.put(
  "/:id",
  asyncHandler(async (req: AuthRequest, res) => {
    const { decision } = req.body;
    if (!["accept", "reject"].includes(decision)) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "decision must be 'accept' or 'reject'", "decision"));
    }

    const application = await Application.findById(req.params.id).populate("tuitionRequestId");
    if (!application) return res.status(404).json(apiError("NOT_FOUND", "Application not found"));

    const owningRequest = application.tuitionRequestId as any;
    if (String(owningRequest.studentId) !== req.auth!.userId) {
      return res.status(403).json(apiError("FORBIDDEN", "Only the request owner can decide on applications"));
    }
    if (application.status !== ApplicationStatus.PENDING) {
      return res.status(409).json(apiError("ALREADY_DECIDED", "This application has already been decided"));
    }

    application.status = decision === "accept" ? ApplicationStatus.ACCEPTED : ApplicationStatus.REJECTED;
    application.decidedAt = new Date();
    await application.save();

    if (decision === "accept") {
      await TuitionRequest.findByIdAndUpdate(owningRequest._id, {
        $set: { status: TuitionRequestStatus.MATCHED },
      });
    }

    res.status(200).json({ id: application._id, status: application.status });
  })
);

export default router;
