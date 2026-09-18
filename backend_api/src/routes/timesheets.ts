import { Router } from "express";
import { Booking, UserRole, BookingStatus } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { authenticate, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);

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
        ].join(",")
      );
    }

    res.set("Content-Type", "text/csv");
    res.status(200).send(rows.join("\n"));
  })
);

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
