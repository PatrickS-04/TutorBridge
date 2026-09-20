import { Booking, BookingStatus } from "../models";

/**
 * Nothing in the app ever tells the API a session happened - the tutor
 * confirms it and then teaches it - so without this the status sits on
 * CONFIRMED forever and every reader of COMPLETED stays empty: the claims
 * screen, /timesheets, and the "Total Consultations" count on a tutor.
 *
 * A confirmed session whose end time has passed is one that went ahead. A
 * session that did not is CANCELLED, and one the tutor never confirmed is
 * still REQUESTED - neither is swept, so neither reaches a claim.
 */
export function completableFilter(now: Date) {
  return { status: BookingStatus.CONFIRMED, endTime: { $lte: now } };
}

/** Returns how many sessions were closed off. */
export async function completePastSessions(now = new Date()): Promise<number> {
  // A pipeline update so completedAt is the moment the session ended rather
  // than whenever the sweep got round to it - an hour later, or months later
  // for the backfill on first boot. Mongoose needs it declared as one.
  const result = await Booking.updateMany(
    completableFilter(now),
    [{ $set: { status: BookingStatus.COMPLETED, completedAt: "$endTime" } }],
    { updatePipeline: true }
  );
  return result.modifiedCount;
}
