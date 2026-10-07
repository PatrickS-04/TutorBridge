import { Types } from "mongoose";
import { Thread, IThread } from "../models";

/**
 * The conversation between a student and a tutor, created the first time they
 * have something to talk about (an accepted offer, a booking request).
 *
 * One thread per pair, however many requests and bookings pass between them:
 * two people with several sessions together still expect one chat. The first
 * thing to link them is recorded on the thread, so a later link does not
 * overwrite it.
 */
export async function ensureThread(
  institutionId: string,
  userA: string,
  userB: string,
  link: { bookingId?: Types.ObjectId; applicationId?: Types.ObjectId } = {}
): Promise<IThread> {
  const existing = await Thread.findOne({ institutionId, participants: { $all: [userA, userB] } });
  if (existing) return existing;

  return Thread.create({
    institutionId,
    participants: [userA, userB],
    bookingId: link.bookingId,
    applicationId: link.applicationId,
  });
}
