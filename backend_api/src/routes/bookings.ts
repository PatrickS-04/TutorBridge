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
import { ensureThread } from "../utils/threads";
import { syncBookingCreated, syncBookingCancelled, syncBookingRescheduled } from "../utils/googleCalendar";

const router = Router();
router.use(authenticate);

//Caps on a tutor's prep notes, mirroring the schema in models/Booking.ts.
const MAX_NOTE_ITEMS = 50;
const MAX_NOTE_ITEM_TEXT = 200;
const MAX_NOTE_TEXT = 2000;

/**
 * Schedule the session behind an application the student has already accepted.
 * This is what the "Accept & Match" flow ends in: the tutor offered and the
 * student accepted, so there is nothing left to agree - the booking is
 * confirmed outright and goes straight onto the tutor's Google Calendar.
 */
router.post(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const { applicationId, startTime, endTime, location } = req.body;
    if (!applicationId || !startTime || !endTime) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "applicationId, startTime, and endTime are required"));
    }

    const start = new Date(startTime);
    const end = new Date(endTime);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "endTime must be a valid time after startTime", "endTime"));
    }

    //Nested populate: the subject names the calendar event and the campus area
    //is the fallback venue - a single-level populate leaves both as bare ids.
    const application = await Application.findOne({
      _id: applicationId,
      institutionId: req.auth!.institutionId,
      status: ApplicationStatus.ACCEPTED,
    }).populate({ path: "tuitionRequestId", populate: [{ path: "subjectId" }, { path: "areaId" }] });

    if (!application) {
      return res.status(404).json(apiError("NOT_FOUND", "Accepted application not found"));
    }

    const tuitionRequest = application.tuitionRequestId as any;
    if (String(tuitionRequest.studentId) !== req.auth!.userId) {
      return res.status(403).json(apiError("FORBIDDEN", "Only the student who posted the request can schedule its session"));
    }

    //A second tap on "Confirm" must not mint a second session.
    const already = await Booking.findOne({
      applicationId: application._id,
      status: { $ne: BookingStatus.CANCELLED },
    });
    if (already) {
      return res.status(409).json(apiError("ALREADY_SCHEDULED", "This match already has a session booked"));
    }

    const clash = await Booking.findOne({
      tutorId: application.tutorId,
      status: BookingStatus.CONFIRMED,
      startTime: { $lt: end },
      endTime: { $gt: start },
    });
    if (clash) {
      return res
        .status(409)
        .json(apiError("SLOT_CONFLICT", "That slot is already taken - please pick another", "startTime"));
    }

    const booking = await Booking.create({
      institutionId: req.auth!.institutionId,
      applicationId: application._id,
      tutorId: application.tutorId,
      studentId: tuitionRequest.studentId,
      subjectName: tuitionRequest.subjectId?.name ?? "Tuition session",
      startTime: start,
      endTime: end,
      mode: tuitionRequest.mode,
      location: location ?? tuitionRequest.areaId?.name,
      status: BookingStatus.CONFIRMED,
      confirmedAt: new Date(),
      addressDisclosed: true,
      agreedRatePerHour: application.proposedRate,
    });

    //Made in the app, on the calendar a moment later.
    syncBookingCreated(booking);

    notify(
      req.auth!.institutionId,
      String(application.tutorId),
      NotificationCategory.BOOKING,
      "Session scheduled",
      //The venue is in the body because this flow confirms outright: the tutor
      //never taps accept, so this is the first they hear of where they are going.
      `${booking.subjectName} is booked for ${start.toISOString().slice(0, 16).replace("T", " ")}${booking.location ? ` at ${booking.location}` : ""}.`,
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
      deactivatedAt: null,
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

    //So the tutor can ask the student about the session before confirming it.
    await ensureThread(req.auth!.institutionId, req.auth!.userId, String(tutor._id), { bookingId: booking._id });

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

/**
 * Move a session to a new time without cancelling it and booking again.
 *
 * Either party may ask. A tutor moving their own session keeps it confirmed
 * and the student is told; a student moving one changes the tutor's diary, so
 * it goes back to REQUESTED for the tutor to confirm (and the calendar event
 * comes down until they do). Only sessions that have not happened yet and are
 * still on can move, and never onto a time the tutor already has a confirmed
 * session in.
 */
router.put(
  "/:id/reschedule",
  asyncHandler(async (req: AuthRequest, res) => {
    const { startTime, endTime, location } = req.body;
    if (!startTime || !endTime) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "startTime and endTime are required"));
    }

    const start = new Date(startTime);
    const end = new Date(endTime);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "endTime must be a valid time after startTime", "endTime"));
    }
    if (start <= new Date()) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "A session can only be moved to a future time", "startTime"));
    }

    const booking = await Booking.findOne({
      _id: req.params.id,
      institutionId: req.auth!.institutionId,
      $or: [{ tutorId: req.auth!.userId }, { studentId: req.auth!.userId }],
    });
    if (!booking) return res.status(404).json(apiError("NOT_FOUND", "Booking not found"));

    if (![BookingStatus.REQUESTED, BookingStatus.CONFIRMED].includes(booking.status)) {
      return res.status(409).json(apiError("INVALID_STATE", "Only an upcoming session can be rescheduled"));
    }

    const clash = await Booking.findOne({
      _id: { $ne: booking._id },
      tutorId: booking.tutorId,
      status: BookingStatus.CONFIRMED,
      startTime: { $lt: end },
      endTime: { $gt: start },
    });
    if (clash) {
      return res
        .status(409)
        .json(apiError("SLOT_CONFLICT", "That slot is already taken - please pick another", "startTime"));
    }

    const byTutor = String(booking.tutorId) === req.auth!.userId;
    const wasConfirmed = booking.status === BookingStatus.CONFIRMED;

    booking.startTime = start;
    booking.endTime = end;
    if (typeof location === "string" && location.trim()) booking.location = location.trim();

    if (byTutor) {
      //The tutor choosing the time is the tutor agreeing to it, so a session
      //they move is confirmed even if it was still only requested.
      booking.status = BookingStatus.CONFIRMED;
      if (!wasConfirmed) {
        booking.confirmedAt = new Date();
        booking.addressDisclosed = true;
      }
    } else if (wasConfirmed) {
      //A student moving a confirmed session changes the tutor's diary, so the
      //tutor has to say yes to the new time.
      booking.status = BookingStatus.REQUESTED;
      booking.confirmedAt = undefined;
    }
    await booking.save();

    if (byTutor) {
      if (wasConfirmed) syncBookingRescheduled(booking);
      else syncBookingCreated(booking);
    } else if (wasConfirmed) {
      //The old slot is off the calendar until the tutor confirms the new one.
      syncBookingCancelled(booking);
    }

    const when = start.toISOString().slice(0, 16).replace("T", " ");
    notify(
      req.auth!.institutionId,
      String(byTutor ? booking.studentId : booking.tutorId),
      NotificationCategory.BOOKING,
      byTutor ? "Session rescheduled" : "Reschedule requested",
      byTutor
        ? `${booking.subjectName} was moved to ${when}.`
        : `${booking.subjectName} was moved to ${when} - confirm the new time.`,
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

/**
 * Replace the tutor's prep notes for one session: the checklist of what has to
 * be done, and their free-text side notes about the student or guardian.
 *
 * The whole set is sent on every save rather than an endpoint per add, tick
 * and delete - a checklist is small, and one write cannot half-apply.
 */
router.put(
  "/:id/notes",
  asyncHandler(async (req: AuthRequest, res) => {
    const { items, text } = req.body;

    if (items !== undefined && !Array.isArray(items)) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "items must be an array", "items"));
    }
    if (Array.isArray(items) && items.length > MAX_NOTE_ITEMS) {
      return res
        .status(400)
        .json(apiError("VALIDATION_ERROR", `At most ${MAX_NOTE_ITEMS} checklist items`, "items"));
    }
    if (text !== undefined && typeof text !== "string") {
      return res.status(400).json(apiError("VALIDATION_ERROR", "text must be a string", "text"));
    }

    //A blank row is what an untouched "add item" box sends; drop it rather
    //than fail the save the tutor actually meant.
    const notes = {
      items: ((items ?? []) as unknown[])
        .map((entry) => {
          const item = (entry ?? {}) as { text?: unknown; done?: unknown };
          return { text: String(item.text ?? "").trim(), done: Boolean(item.done) };
        })
        .filter((item) => item.text !== ""),
      text: String(text ?? ""),
    };

    if (
      notes.items.some((item) => item.text.length > MAX_NOTE_ITEM_TEXT) ||
      notes.text.length > MAX_NOTE_TEXT
    ) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "A note is too long to store", "text"));
    }

    //Notes belong to the tutor teaching the session, so matching on tutorId is
    //the authorisation check: another tutor's booking simply is not found.
    const booking = await Booking.findOneAndUpdate(
      { _id: req.params.id, institutionId: req.auth!.institutionId, tutorId: req.auth!.userId },
      { $set: { tutorNotes: notes } },
      { new: true }
    ).select("+tutorNotes");
    if (!booking) return res.status(404).json(apiError("NOT_FOUND", "Booking not found"));

    res.status(200).json(booking.tutorNotes);
  })
);

router.get(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const isTutor = req.auth!.role === UserRole.TUTOR;
    const filter = isTutor
      ? { institutionId: req.auth!.institutionId, tutorId: req.auth!.userId }
      : { institutionId: req.auth!.institutionId, studentId: req.auth!.userId };

    //Only the tutor who wrote them gets their notes back with the list.
    const result = await paginate(
      Booking,
      filter,
      req,
      ["tutorId", "studentId"],
      isTutor ? "+tutorNotes" : undefined
    );
    res.status(200).json(result);
  })
);

export default router;
