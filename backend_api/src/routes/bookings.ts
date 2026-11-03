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
import {
    CODE_TTL_MS,
    MAX_FAILED_ATTEMPTS,
    isCheckInOpen,
    newCheckInCode,
    hashCheckInCode,
    checkInCodeMatches,
    checkInQrPayload
} from "../utils/attendance";

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
      booking.location ? "booking.scheduledAtVenue" : "booking.scheduled",
      { subject: booking.subjectName, when: start.toISOString().slice(0, 16).replace("T", " "), venue: booking.location ?? "" },
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
      "booking.new",
      { subject: subjectName, when: start.toISOString().slice(0, 10) },
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
      "booking.confirmed",
      { subject: booking.subjectName, when: booking.startTime.toISOString().slice(0, 16).replace("T", " ") },
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
      reason ? "booking.cancelledWithReason" : "booking.cancelled",
      { subject: booking.subjectName, reason: reason ?? "" },
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
      byTutor ? "booking.rescheduled" : "booking.rescheduleRequested",
      { subject: booking.subjectName, when },
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

/**
 * The tutor asks for a check-in code to show the student. Each call issues a
 * fresh code (and forgets the old one and any wrong guesses), so "New code" on
 * the tutor's screen is just this call again.
 */
router.post(
"/:id/check-in-code",
asyncHandler(async (req: AuthRequest, res) => {
 //Matching on tutorId is the authorisation check: only the session's own tutor can issue a code
 const booking = await Booking.findOne({
     _id: req.params.id,
     institutionId: req.auth!.institutionId,
     tutorId: req.auth!.userId
 });
 if (!booking)
 {
     return res.status(404).json(apiError("NOT_FOUND", "Booking not found"));
 }

 if (booking.status !== BookingStatus.CONFIRMED)
 {
     return res.status(409).json(apiError("INVALID_STATE", "Only a confirmed session can be checked in to"));
 }

 if (booking.attendance?.checkedInAt)
 {
     return res.status(409).json(apiError("ALREADY_CHECKED_IN", "The student has already checked in"));
 }

 if (!isCheckInOpen(booking.startTime, booking.endTime))
 {
     return res.status(409).json(apiError("CHECK_IN_CLOSED", "Check-in opens 15 minutes before the session and closes when it ends"));
 }

 const code = newCheckInCode();
 const expiresAt = new Date(Date.now() + CODE_TTL_MS);
 await Booking.updateOne(
     { _id: booking._id },
     {
         $set: {
             "attendance.codeHash": hashCheckInCode(String(booking._id), code),
             "attendance.codeExpiresAt": expiresAt,
             "attendance.failedAttempts": 0
         },
     }
 );

 res.status(201).json({
     bookingId: booking._id,
     code,
     expiresAt,
     qrPayload: checkInQrPayload(String(booking._id), code)
 });
})
);

/**
 * The student checks in with the code from the tutor's screen - scanned from
 * the QR code or typed in. Checking in twice is harmless: the second call just
 * returns the first check-in
 */
router.post(
 "/:id/check-in",
 asyncHandler(async (req: AuthRequest, res) => {
     const code = String(req.body?.code ?? "").trim();
     const method = req.body?.method === "QR" ? "QR" : "CODE";
     if (!/^\d{6}$/.test(code))
     {
         return res.status(400).json(apiError("VALIDATION_ERROR", "code must be 6 digits", "code"));
     }

     // Only the session's own student may check in, the hidden code fields are asked for by name
     const booking = await Booking.findOne({
         _id: req.params.id,
         institutionId: req.auth!.institutionId,
         studentId: req.auth!.userId
     }).select("+attendance.codeHash +attendance.codeExpiresAt +attendance.failedAttempts");

     if (!booking)
     {
         return res.status(404).json(apiError("NOT_FOUND", "Booking not found"));
     }

     const attendance = booking.attendance;
     if (attendance?.checkedInAt)
     {
         return res.status(200).json({ bookingId: booking._id, checkedInAt: attendance.checkedInAt, method: attendance.method });
     }

     if (booking.status !== BookingStatus.CONFIRMED)
     {
         return res.status(409).json(apiError("INVALID_STATE", "Only a confirmed session can be checked in to"));
     }

     if (!isCheckInOpen(booking.startTime, booking.endTime))
     {
         return res.status(409).json(apiError("CHECK_IN_CLOSED", "Check-in opens 15 minutes before the session and closes when it ends"));
     }

     if (!attendance?.codeHash || !attendance.codeExpiresAt || attendance.codeExpiresAt <= new Date())
     {
         return res.status(409).json(apiError("CODE_EXPIRED", "That code has expired - ask your tutor for a new one", "code"));
     }

     if ((attendance.failedAttempts ?? 0) >= MAX_FAILED_ATTEMPTS)
     {
         return res.status(429).json(apiError("TOO_MANY_ATTEMPTS", "Too many wrong codes - ask your tutor for a new one", "code"));
     }

     if (!checkInCodeMatches(String(booking._id), code, attendance.codeHash))
     {
         await Booking.updateOne({ _id: booking._id }, { $inc: { "attendance.failedAttempts": 1 } });
         return res.status(400).json(apiError("WRONG_CODE", "That code is not right - check it and try again", "code"));
     }

     // Conditional on not being checked in yet, so two taps at once cannot both win
     const updated = await Booking.findOneAndUpdate(
         { _id: booking._id, "attendance.checkedInAt": null },
         {
             $set: { "attendance.checkedInAt": new Date(), "attendance.method": method },
             $unset: { "attendance.codeHash": "", "attendance.codeExpiresAt": "" }
         },
         { new: true }
     );
     const checkedInAt = updated?.attendance?.checkedInAt ?? new Date();

     notify(
         req.auth!.institutionId,
         String(booking.tutorId),
         NotificationCategory.BOOKING,
         "attendance.checkedIn",
         { subject: booking.subjectName },
         `tutorbridge://bookings/${booking._id}`
     );

     res.status(200).json({ bookingId: booking._id, checkedInAt, method });
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
