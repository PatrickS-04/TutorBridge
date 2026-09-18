import { Router } from "express";
import { Booking, Application, ApplicationStatus, BookingStatus, UserRole } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);

router.post(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const { applicationId, startTime, endTime } = req.body;
    if (!applicationId || !startTime || !endTime) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "applicationId, startTime, and endTime are required"));
    }

    const application = await Application.findOne({
      _id: applicationId,
      institutionId: req.auth!.institutionId,
      status: ApplicationStatus.ACCEPTED,
    }).populate("tuitionRequestId");

    if (!application) {
      return res.status(404).json(apiError("NOT_FOUND", "Accepted application not found"));
    }

    const tuitionRequest = application.tuitionRequestId as any;

    const booking = await Booking.create({
      institutionId: req.auth!.institutionId,
      applicationId: application._id,
      tutorId: application.tutorId,
      studentId: tuitionRequest.studentId,
      subjectName: tuitionRequest.subjectId?.name ?? "Tuition session",
      startTime: new Date(startTime),
      endTime: new Date(endTime),
      mode: tuitionRequest.mode,
      status: BookingStatus.REQUESTED,
      agreedRatePerHour: application.proposedRate,
    });

    res.status(201).json({
      id: booking._id,
      status: booking.status,
      startTime: booking.startTime,
      endTime: booking.endTime,
    });
  })
);

router.put(
  "/:id/confirm",
  asyncHandler(async (req: AuthRequest, res) => {
    const booking = await Booking.findOne({
      _id: req.params.id,
      institutionId: req.auth!.institutionId,
      tutorId: req.auth!.userId,
    });
    if (!booking) return res.status(404).json(apiError("NOT_FOUND", "Booking not found"));

    if (booking.status !== BookingStatus.REQUESTED) {
      return res.status(409).json(apiError("INVALID_STATE", "Only a requested booking can be confirmed"));
    }

    const overlap = await Booking.findOne({
      _id: { $ne: booking._id },
      tutorId: booking.tutorId,
      status: BookingStatus.CONFIRMED,
      startTime: { $lt: booking.endTime },
      endTime: { $gt: booking.startTime },
    });

    if (overlap) {
      return res.status(409).json({
        error: {
          code: "SLOT_CONFLICT",
          message: "Tutor already has a confirmed booking in this window",
          field: "startTime",
        },
      });
    }

    booking.status = BookingStatus.CONFIRMED;
    booking.confirmedAt = new Date();
    booking.addressDisclosed = true;
    await booking.save();

    res.status(200).json({
      id: booking._id,
      status: booking.status,
      startTime: booking.startTime,
      endTime: booking.endTime,
    });
  })
);

router.put(
  "/:id/cancel",
  asyncHandler(async (req: AuthRequest, res) => {
    const { reason } = req.body;
    const booking = await Booking.findOne({
      _id: req.params.id,
      institutionId: req.auth!.institutionId,
      $or: [{ tutorId: req.auth!.userId }, { studentId: req.auth!.userId }],
    });
    if (!booking) return res.status(404).json(apiError("NOT_FOUND", "Booking not found"));

    if ([BookingStatus.COMPLETED, BookingStatus.CANCELLED].includes(booking.status)) {
      return res.status(409).json(apiError("INVALID_STATE", "This booking can no longer be cancelled"));
    }

    booking.status = BookingStatus.CANCELLED;
    booking.cancelledBy = req.auth!.userId as any;
    booking.cancellationReason = reason;
    await booking.save();

    res.status(200).json({
      id: booking._id,
      status: booking.status,
      cancelledBy: booking.cancelledBy,
      reason: booking.cancellationReason,
    });
  })
);

router.get(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const filter =
      req.auth!.role === UserRole.TUTOR
        ? { institutionId: req.auth!.institutionId, tutorId: req.auth!.userId }
        : { institutionId: req.auth!.institutionId, studentId: req.auth!.userId };

    const result = await paginate(Booking, filter, req);
    res.status(200).json(result);
  })
);

export default router;
