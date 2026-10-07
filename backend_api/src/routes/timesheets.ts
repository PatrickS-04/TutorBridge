import { Router } from "express";
import { Booking, User, UserRole, BookingStatus } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { authenticate, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);

//Two years of history: enough for a tutor to look back at last year's claims.
const MAX_PERIODS = 24;

router.get(
  "/summary",
  asyncHandler(async (req: AuthRequest, res) => {
    const month = String(req.query.month ?? "");
    if (!/^\d{4}-\d{2}$/.test(month)) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "month must be in YYYY-MM format", "month"));
    }

    const { start, end } = monthBounds(month);
    const bookings = await Booking.find({
      tutorId: req.auth!.userId,
      status: BookingStatus.COMPLETED,
      startTime: { $gte: start, $lt: end },
    });

    const totalHours = bookings.reduce((sum, b) => sum + hoursBetween(b.startTime, b.endTime), 0);
    const totalEarnings = bookings.reduce(
      (sum, b) => sum + hoursBetween(b.startTime, b.endTime) * b.agreedRatePerHour,
      0
    );

    res.status(200).json({
      month,
      totalHours: round1(totalHours),
      totalEarnings: round1(totalEarnings),
      currency: "ZAR",
      sessionCount: bookings.length,
    });
  })
);

/**
 * The claims screen shows several pay periods at once, each with the sessions
 * behind it, so this returns the last [count] months in one call instead of
 * making the app loop over /summary. Periods are derived from completed
 * bookings - there is no claims collection - so the current month is the
 * working draft and everything before it is closed.
 */
router.get(
  "/periods",
  asyncHandler(async (req: AuthRequest, res) => {
    const count = Math.min(MAX_PERIODS, Math.max(1, parseInt(String(req.query.count ?? "3"), 10) || 3));
    const tutorId = req.auth!.role === UserRole.TUTOR ? req.auth!.userId : String(req.query.tutorId ?? "");
    if (!tutorId) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "tutorId is required for non-tutors", "tutorId"));
    }

    const now = new Date();
    const oldest = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - (count - 1), 1));

    const [tutor, bookings] = await Promise.all([
      User.findById(tutorId),
      Booking.find({ tutorId, status: BookingStatus.COMPLETED, startTime: { $gte: oldest } })
        .populate("studentId")
        .sort({ startTime: -1 }),
    ]);

    const periods = [];
    for (let monthsBack = 0; monthsBack < count; monthsBack++) {
      const monthStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - monthsBack, 1));
      const monthEnd = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - monthsBack + 1, 1));

      const sessions = bookings
        .filter((b) => b.startTime >= monthStart && b.startTime < monthEnd)
        .map((b) => {
          const student = b.studentId as any;
          const hours = hoursBetween(b.startTime, b.endTime);
          return {
            id: b._id,
            subjectName: b.subjectName,
            date: b.startTime.toISOString().slice(0, 10),
            startTime: b.startTime,
            endTime: b.endTime,
            studentName: student?.fullName ?? "",
            location: b.location ?? "",
            hours: round1(hours),
            amount: round1(hours * b.agreedRatePerHour),
          };
        });

      periods.push({
        id: monthStart.toISOString().slice(0, 7),
        month: monthStart.toISOString().slice(0, 7),
        isCurrent: monthsBack === 0,
        totalHours: round1(sessions.reduce((sum, s) => sum + s.hours, 0)),
        claimValue: round1(sessions.reduce((sum, s) => sum + s.amount, 0)),
        sessionCount: sessions.length,
        sessions,
      });
    }

    res.status(200).json({
      currency: "ZAR",
      ratePerHour: tutor?.hourlyRate ?? 0,
      staffNumber: tutor?.staffNumber ?? "",
      department: tutor?.department ?? "",
      periods,
    });
  })
);

router.get(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const month = String(req.query.month ?? "");
    if (!/^\d{4}-\d{2}$/.test(month)) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "month must be in YYYY-MM format", "month"));
    }
    const { start, end } = monthBounds(month);

    const filter: Record<string, unknown> = {
      institutionId: req.auth!.institutionId,
      status: BookingStatus.COMPLETED,
      startTime: { $gte: start, $lt: end },
    };

    if (req.auth!.role === UserRole.TUTOR) {
      filter.tutorId = req.auth!.userId;
    } else if (req.query.tutorId) {
      filter.tutorId = req.query.tutorId;
    }

    const bookings = await Booking.find(filter).populate("tutorId").populate("studentId");

    const rows = ["date,tutorName,startTime,endTime,subjectName,studentName,hours,ratePerHour,lineTotal"];
    for (const b of bookings) {
      const tutor = b.tutorId as any;
      const student = b.studentId as any;
      const hours = hoursBetween(b.startTime, b.endTime);
      rows.push(
        [
          b.startTime.toISOString().slice(0, 10),
          tutor?.fullName ?? "",
          b.startTime.toISOString().slice(11, 16),
          b.endTime.toISOString().slice(11, 16),
          b.subjectName,
          student?.fullName ?? "",
          hours.toFixed(1),
          b.agreedRatePerHour,
          round1(hours * b.agreedRatePerHour),
        ]
          .map(csvCell)
          .join(",")
      );
    }

    res.set("Content-Type", "text/csv");
    res.status(200).send(rows.join("\n"));
  })
);

/**
 * One CSV field. A name or subject with a comma, quote or line break would
 * otherwise split into extra columns; those are quoted, with quotes doubled
 * (RFC 4180).
 */
export function csvCell(value: unknown): string {
  const text = String(value ?? "");
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function monthBounds(month: string) {
  const [year, m] = month.split("-").map(Number);
  const start = new Date(Date.UTC(year, m - 1, 1));
  const end = new Date(Date.UTC(year, m, 1));
  return { start, end };
}

function hoursBetween(start: Date, end: Date) {
  return (end.getTime() - start.getTime()) / (1000 * 60 * 60);
}

function round1(n: number) {
  return Math.round(n * 100) / 100;
}

export default router;
