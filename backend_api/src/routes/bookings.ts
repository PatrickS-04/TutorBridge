import { Router } from "express";
import {
  Booking,
  Application,
  Subject,
  User,
  ApplicationStatus,
  BookingStatus,
  LessonMode,
  UserRole,
  VerificationStatus,
  NotificationCategory,
} from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, AuthRequest } from "../middleware/auth";
import { notify } from "../utils/notify";
import { syncBookingCreated, syncBookingCancelled } from "../utils/googleCalendar";

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

/**
 * Book a tutor straight from their profile. The application-driven flow above
 * still exists for requests that went through the board; this is the "Find a
 * Tutor -> pick a slot" path, so the booking simply has no application behind it.
 */
router.post(
  "/direct",
  asyncHandler(async (req: AuthRequest, res) => {
    const { tutorId, subjectId, startTime, endTime, mode, location } = req.body;
    if (!tutorId || !startTime || !endTime) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "tutorId, startTime, and endTime are required"));
    }

    const start = new Date(startTime);
    const end = new Date(endTime);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "endTime must be a valid time after startTime", "endTime"));
    }

    const tutor = await User.findOne({
      _id: tutorId,
      institutionId: req.auth!.institutionId,
      role: UserRole.TUTOR,
      verificationStatus: VerificationStatus.VERIFIED,
      deletionScheduledFor: null,
    }).populate("subjectIds");
    if (!tutor) return res.status(404).json(apiError("NOT_FOUND", "Bookable tutor not found"));

    const clash = await Booking.findOne({
      tutorId: tutor._id,
      status: BookingStatus.CONFIRMED,
      startTime: { $lt: end },
      endTime: { $gt: start },
    });
    if (clash) {
      return res
        .status(409)
        .json(apiError("SLOT_CONFLICT", "That slot is already taken - please pick another", "startTime"));
    }

    const subject = subjectId ? await Subject.findById(subjectId) : null;
    const subjectName =
      subject?.name ?? (tutor.subjectIds as any[])[0]?.name ?? "Tuition session";

    const booking = await Booking.create({
      institutionId: req.auth!.institutionId,
      tutorId: tutor._id,
      studentId: req.auth!.userId,
      subjectName,
      startTime: start,
      endTime: end,
      mode: mode ?? LessonMode.IN_PERSON,
      location,
      status: BookingStatus.REQUESTED,
      agreedRatePerHour: tutor.hourlyRate ?? 0,
    });

    notify(
      req.auth!.institutionId,
      String(tutor._id),
      NotificationCategory.BOOKING,
      "New student booking",
      `${subjectName} on ${start.toISOString().slice(0, 10)} - confirm or decline it.`,
      `tutorbridge://bookings/${booking._id}`
    );

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

    //The session is on: put it in the tutor's Google Calendar and invite the student.
    syncBookingCreated(booking);

    notify(
      req.auth!.institutionId,
      String(booking.studentId),
      NotificationCategory.BOOKING,
      "Session confirmed",
      `${booking.subjectName} is confirmed for ${booking.startTime.toISOString().slice(0, 16).replace("T", " ")}.`,
      `tutorbridge://bookings/${booking._id}`
    );

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

    syncBookingCancelled(booking);

    // Whoever did not press cancel is the one who needs telling.
    const otherParty =
      String(booking.tutorId) === req.auth!.userId ? booking.studentId : booking.tutorId;
    notify(
      req.auth!.institutionId,
      String(otherParty),
      NotificationCategory.BOOKING,
      "Session cancelled",
      reason ? `${booking.subjectName}: ${reason}` : `${booking.subjectName} was cancelled.`,
      `tutorbridge://bookings/${booking._id}`
    );

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

    const result = await paginate(Booking, filter, req, ["tutorId", "studentId"]);
    res.status(200).json(result);
  })
);

export default router;
