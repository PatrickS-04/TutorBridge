import { Router, Response } from "express";
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
    const { subjectId, areaId, gradeLevel, mode, description, preferredRateBand, availability, clientRequestId } =
      req.body;
    if (!subjectId || !areaId || !mode) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "subjectId, areaId, and mode are required"));
    }
    if (clientRequestId !== undefined && (typeof clientRequestId !== "string" || clientRequestId.length > 100)) {
      return res
        .status(400)
        .json(apiError("VALIDATION_ERROR", "clientRequestId must be a string of at most 100 characters", "clientRequestId"));
    }

    //A repeat of a post the server already made - the app retrying after a lost
    //reply, or replaying its offline queue - gets that request back, not a second.
    const alreadyPosted = () =>
      clientRequestId
        ? TuitionRequest.findOne({ studentId: req.auth!.userId, clientRequestId })
        : Promise.resolve(null);
    const summary = (doc: any) => ({
      id: doc._id,
      status: doc.status,
      matchScore: doc.matchScore ?? null,
      createdAt: doc.createdAt,
    });

    const existing = await alreadyPosted();
    if (existing) return res.status(200).json(summary(existing));

    let tuitionRequest;
    try {
      tuitionRequest = await TuitionRequest.create({
        institutionId: req.auth!.institutionId,
        studentId: req.auth!.userId,
        subjectId,
        areaId,
        gradeLevel,
        mode,
        description,
        preferredRateBand,
        availability,
        clientRequestId,
        status: TuitionRequestStatus.OPEN,
      });
    } catch (err: any) {
      //Two copies of the same post arriving together: the unique index lets one
      //through, and the other is answered with the winner.
      if (err?.code === 11000) {
        const winner = await alreadyPosted();
        if (winner) return res.status(200).json(summary(winner));
      }
      throw err;
    }

    res.status(201).json(summary(tuitionRequest));
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

    // A matched request is only useful with the tutor's name on it. One query
    // for the page rather than one per request.
    const accepted = await Application.find({
      tuitionRequestId: { $in: result.data.map((request) => request._id) },
      status: ApplicationStatus.ACCEPTED,
    }).populate("tutorId");
    const tutorByRequest = new Map(
      accepted.map((application) => [
        String(application.tuitionRequestId),
        (application.tutorId as any)?.fullName ?? "",
      ])
    );

    res.status(200).json({
      ...result,
      data: result.data.map((request) => ({
        ...request.toObject(),
        matchedTutorName: tutorByRequest.get(String(request._id)) ?? "",
      })),
    });
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

//What a student may change on their own request. The status, the owner and
//the application count are deliberately not on the list.
const EDITABLE_FIELDS = [
  "subjectId",
  "areaId",
  "gradeLevel",
  "mode",
  "description",
  "preferredRateBand",
  "availability",
] as const;

/**
 * Edit a request the student posted. Only while it is still open: once a
 * tutor has been matched the terms are agreed, so changing the subject or
 * time underneath them would be a quiet breach of that agreement.
 */
router.put(
  "/:id",
  asyncHandler(async (req: AuthRequest, res) => {
    const updates = Object.fromEntries(
      EDITABLE_FIELDS.filter((field) => req.body[field] !== undefined).map((field) => [field, req.body[field]])
    );
    if (Object.keys(updates).length === 0) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "Nothing to update"));
    }

    const tuitionRequest = await TuitionRequest.findOne({
      _id: req.params.id,
      studentId: req.auth!.userId,
      institutionId: req.auth!.institutionId,
    });
    if (!tuitionRequest) return res.status(404).json(apiError("NOT_FOUND", "Tuition request not found or not owned by caller"));

    if (tuitionRequest.status !== TuitionRequestStatus.OPEN) {
      return res.status(409).json(apiError("INVALID_STATE", "Only an open request can be edited"));
    }

    tuitionRequest.set(updates);
    await tuitionRequest.save();

    //Tutors who already offered need to know the request they offered on moved.
    const pending = await Application.find({
      tuitionRequestId: tuitionRequest._id,
      status: ApplicationStatus.PENDING,
    });
    for (const application of pending) {
      notify(
        req.auth!.institutionId,
        String(application.tutorId),
        NotificationCategory.APPLICATION,
        "A request you applied to changed",
        "The student edited their tuition request - check it still suits you.",
        `tutorbridge://requests/${tuitionRequest._id}`
      );
    }

    res.status(200).json(tuitionRequest);
  })
);

/**
 * Shared by close and withdraw: both end the request, and differ only in the
 * status they leave behind (CLOSED = sorted, CANCELLED = no longer wanted).
 */
async function endRequest(req: AuthRequest, res: Response, finalStatus: TuitionRequestStatus) {
  const tuitionRequest = await TuitionRequest.findOne({
    _id: req.params.id,
    studentId: req.auth!.userId,
    institutionId: req.auth!.institutionId,
  });
  if (!tuitionRequest) return res.status(404).json(apiError("NOT_FOUND", "Tuition request not found or not owned by caller"));

  if (![TuitionRequestStatus.OPEN, TuitionRequestStatus.MATCHED].includes(tuitionRequest.status)) {
    return res.status(409).json(apiError("INVALID_STATE", "This request has already ended"));
  }

  tuitionRequest.status = finalStatus;
  await tuitionRequest.save();

  //Offers nobody will answer now: decline them so tutors stop waiting.
  const pending = await Application.find({
    tuitionRequestId: tuitionRequest._id,
    status: ApplicationStatus.PENDING,
  });
  for (const application of pending) {
    application.status = ApplicationStatus.REJECTED;
    application.decidedAt = new Date();
    await application.save();
    notify(
      req.auth!.institutionId,
      String(application.tutorId),
      NotificationCategory.APPLICATION,
      "A request you applied to is closed",
      "The student is no longer looking for a tutor for this request.",
      `tutorbridge://applications/${application._id}`
    );
  }

  res.status(200).json({ id: tuitionRequest._id, status: tuitionRequest.status });
}

router.put(
  "/:id/close",
  asyncHandler(async (req: AuthRequest, res) => endRequest(req, res, TuitionRequestStatus.CLOSED))
);

router.put(
  "/:id/withdraw",
  asyncHandler(async (req: AuthRequest, res) => endRequest(req, res, TuitionRequestStatus.CANCELLED))
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
