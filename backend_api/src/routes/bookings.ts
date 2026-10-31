import { Router } from "express";
import { Types } from "mongoose";
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
import { parseRepeat, seriesWindows, fitsAvailability } from "../utils/series";

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
    const { tutorId, subjectId, startTime, endTime, mode, location, repeat } = req.body;
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

    //A weekly series: the first session and the same time on each following week.
    //Everything about it is checked before anything is written, so a series is
    //either booked whole or not at all.
    let windows = [{ start, end }];
    if (repeat !== undefined) {
      const parsed = parseRepeat(repeat);
      if ("error" in parsed) {
        return res.status(400).json(apiError("VALIDATION_ERROR", parsed.error, parsed.field));
      }
      if (start <= new Date()) {
        return res.status(400).json(apiError("VALIDATION_ERROR", "The first session of a series must be in the future", "startTime"));
      }
      windows = seriesWindows(start, end, parsed.repeat.count);

      //The app only offers slots inside the tutor's weekly hours; a series has to keep to them too.
      const outside = windows.filter((w) => !fitsAvailability(tutor.availability, w.start, w.end));
      if (outside.length > 0) {
        return res.status(400).json({
          error: {
            code: "OUTSIDE_AVAILABILITY",
            message: "The tutor is not available at this time on every week of the series",
            field: "startTime",
            dates: outside.map((w) => w.start.toISOString()),
          },
        });
      }

      const taken = await Booking.find({
        tutorId: tutor._id,
        status: BookingStatus.CONFIRMED,
        $or: windows.map((w) => ({ startTime: { $lt: w.end }, endTime: { $gt: w.start } })),
      });
      const clashing = windows.filter((w) => taken.some((b) => b.startTime < w.end && b.endTime > w.start));
      if (clashing.length > 0) {
        return res.status(409).json({
          error: {
            code: "SERIES_CONFLICT",
            message: "The tutor already has a confirmed session on some of these dates",
            field: "startTime",
            dates: clashing.map((w) => w.start.toISOString()),
          },
        });
      }
    }
    const isSeries = windows.length > 1;

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

    //The sessions of a series are ordinary bookings that share a seriesId.
    const seriesId = isSeries ? new Types.ObjectId() : undefined;
    const created = await Booking.create(
      windows.map((w, i) => ({
        institutionId: req.auth!.institutionId,
        tutorId: tutor._id,
        studentId: req.auth!.userId,
        subjectName,
        startTime: w.start,
        endTime: w.end,
        mode: mode ?? LessonMode.IN_PERSON,
        location,
        status: BookingStatus.REQUESTED,
        agreedRatePerHour: tutor.hourlyRate ?? 0,
        ...(isSeries ? { seriesId, seriesIndex: i + 1, seriesCount: windows.length } : {}),
      }))
    );
    const booking = created[0];

    //So the tutor can ask the student about the session before confirming it.
    await ensureThread(req.auth!.institutionId, req.auth!.userId, String(tutor._id), { bookingId: booking._id });

    notify(
      req.auth!.institutionId,
      String(tutor._id),
      NotificationCategory.BOOKING,
      isSeries ? "booking.seriesNew" : "booking.new",
      isSeries
        ? { subject: subjectName, when: start.toISOString().slice(0, 10), count: String(windows.length) }
        : { subject: subjectName, when: start.toISOString().slice(0, 10) },
      `tutorbridge://bookings/${booking._id}`
    );

    const summary = (b: (typeof created)[number]) => ({
      id: b._id,
      status: b.status,
      startTime: b.startTime,
      endTime: b.endTime,
    });

    //A single session answers as it always has. A series also names itself and lists
    //every session in it, so the app can show the whole run without a second call.
    if (isSeries) {
      return res.status(201).json({
        ...summary(booking),
        seriesId,
        seriesCount: created.length,
        bookings: created.map((b) => ({ ...summary(b), seriesIndex: b.seriesIndex })),
      });
    }
    res.status(201).json(summary(booking));
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
 * Confirm a whole weekly series in one go.
 *
 * Only the tutor it was booked with may. Every session still waiting and not yet
 * started is checked again against the tutor's confirmed sessions, since the diary may
 * have filled since the student asked; one clash and none are confirmed, so a series
 * is never left half-agreed (the tutor can still confirm the free dates one by one).
 * The student is told once, not once a week.
 */
router.put(
  "/series/:seriesId/confirm",
  asyncHandler(async (req: AuthRequest, res) => {
    const sessions = await Booking.find({
      seriesId: req.params.seriesId,
      institutionId: req.auth!.institutionId,
      tutorId: req.auth!.userId,
    }).sort({ startTime: 1 });
    if (sessions.length === 0) return res.status(404).json(apiError("NOT_FOUND", "Series not found"));

    const now = new Date();
    const waiting = sessions.filter((b) => b.status === BookingStatus.REQUESTED && b.startTime > now);
    if (waiting.length === 0) {
      return res.status(409).json(apiError("INVALID_STATE", "This series has no sessions waiting to be confirmed"));
    }

    const taken = await Booking.find({
      _id: { $nin: waiting.map((b) => b._id) },
      tutorId: req.auth!.userId,
      status: BookingStatus.CONFIRMED,
      $or: waiting.map((b) => ({ startTime: { $lt: b.endTime }, endTime: { $gt: b.startTime } })),
    });
    const clashing = waiting.filter((w) => taken.some((b) => b.startTime < w.endTime && b.endTime > w.startTime));
    if (clashing.length > 0) {
      return res.status(409).json({
        error: {
          code: "SERIES_CONFLICT",
          message: "You already have a confirmed session on some of these dates",
          field: "startTime",
          dates: clashing.map((b) => b.startTime.toISOString()),
        },
      });
    }

    for (const booking of waiting) {
      booking.status = BookingStatus.CONFIRMED;
      booking.confirmedAt = new Date();
      booking.addressDisclosed = true;
      await booking.save();
      syncBookingCreated(booking);
    }

    notify(
      req.auth!.institutionId,
      String(waiting[0].studentId),
      NotificationCategory.BOOKING,
      "booking.seriesConfirmed",
      {
        subject: waiting[0].subjectName,
        when: waiting[0].startTime.toISOString().slice(0, 10),
        count: String(waiting.length),
      },
      `tutorbridge://bookings/${waiting[0]._id}`
    );

    res.status(200).json({
      seriesId: req.params.seriesId,
      confirmed: waiting.length,
      bookings: waiting.map((b) => ({
        id: b._id,
        status: b.status,
        startTime: b.startTime,
        endTime: b.endTime,
        seriesIndex: b.seriesIndex,
      })),
    });
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
