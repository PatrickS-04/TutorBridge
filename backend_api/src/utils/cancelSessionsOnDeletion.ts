import { Types } from "mongoose";
import { Booking, BookingStatus, IBooking, NotificationCategory } from "../models";
import { syncBookingCancelled } from "./googleCalendar";
import { notify } from "./notify";

/**
 * Someone who has asked to delete their account will not turn up, so their upcoming
 * sessions end now rather than when the 14-day grace period does, and whoever was
 * meeting them finds out while there is still time to book someone else. Sessions
 * already held stay, because a tutor's claim depends on them.
 */
export function upcomingSessionsFilter(userId: string, now: Date) {
  return {
    status: { $in: [BookingStatus.REQUESTED, BookingStatus.CONFIRMED, BookingStatus.TRIAL_RUNNING, BookingStatus.ACTIVE] },
    startTime: { $gt: now },
    $or: [{ tutorId: userId }, { studentId: userId }],
  };
}

export interface CancellationNotice {
  otherParty: string;
  bookings: IBooking[];
}

/** One notice per single session and one per series, so a weekly series does not send eight pushes. */
export function noticesFor(bookings: IBooking[], userId: string): CancellationNotice[] {
  const notices = new Map<string, CancellationNotice>();
  for (const booking of bookings) {
    const otherParty = String(String(booking.tutorId) === userId ? booking.studentId : booking.tutorId);
    const key = String(booking.seriesId ?? booking._id);
    const notice = notices.get(key) ?? { otherParty, bookings: [] };
    notice.bookings.push(booking);
    notices.set(key, notice);
  }
  return [...notices.values()];
}

/** Returns how many sessions were cancelled. */
export async function cancelSessionsOnDeletion(institutionId: string, userId: string, now = new Date()): Promise<number> {
  const bookings = await Booking.find({ institutionId, ...upcomingSessionsFilter(userId, now) });

  for (const booking of bookings) {
    booking.status = BookingStatus.CANCELLED;
    booking.cancelledBy = new Types.ObjectId(userId);
    await booking.save();
    syncBookingCancelled(booking);
  }

  for (const { otherParty, bookings: session } of noticesFor(bookings, userId)) {
    const inSeries = session.length > 1;
    notify(
      institutionId,
      otherParty,
      NotificationCategory.BOOKING,
      inSeries ? "booking.seriesCancelled" : "booking.cancelled",
      { subject: session[0].subjectName, count: String(session.length) },
      `tutorbridge://bookings/${session[0]._id}`
    );
  }
  return bookings.length;
}
