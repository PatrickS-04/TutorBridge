import admin from "firebase-admin";
import { Types } from "mongoose";
import {
  Application,
  CalendarLink,
  Message,
  NotificationEvent,
  NotificationToken,
  Thread,
  TuitionRequest,
  User,
  UserSettings,
  VerificationDocument,
} from "../models";

/**
 * The other half of DELETE /users/me: that route only stamps
 * `deletionScheduledFor`, this is what makes the stamp mean something once the
 * POPIA grace period has run out.
 *
 * `deactivatedAt` is deliberately not read here - an institution admin
 * removing a tutor deactivates that profile, it does not schedule a purge, and
 * the tutor's bookings and claims have to survive.
 */

/** Wiped with the profile. Mirrored in the names below for the coverage test. */
export const purgedCollections = [
  "UserSettings",
  "NotificationToken",
  "NotificationEvent",
  "CalendarLink",
  "VerificationDocument",
  "Application",
  "TuitionRequest",
  "Thread",
  "Message",
];

/**
 * Kept on purpose. A booking is the institution's record of a session and the
 * money behind it, and both timesheet paths already read the student as
 * `student?.fullName ?? ""` - so an erased profile reads as blank there
 * instead of taking a tutor's claim down with it. A Report is the institution's
 * safeguarding record, and erasing an account must not erase a complaint about it.
 */
export const keptCollections = ["Booking", "Report", "User"];

export async function purgeUser(userId: Types.ObjectId, firebaseUid?: string): Promise<void> {
  // Threads are two-person, so erasing one side takes the conversation.
  const threadIds = await Thread.find({ participants: userId }).distinct("_id");
  // A student's requests take the tutors' applications to them along.
  const requestIds = await TuitionRequest.find({ studentId: userId }).distinct("_id");

  await Promise.all([
    UserSettings.deleteMany({ userId }),
    NotificationToken.deleteMany({ userId }),
    NotificationEvent.deleteMany({ userId }),
    CalendarLink.deleteMany({ userId }),
    VerificationDocument.deleteMany({ userId }),
    Application.deleteMany({ tutorId: userId }),
    Application.deleteMany({ tuitionRequestId: { $in: requestIds } }),
    Message.deleteMany({ threadId: { $in: threadIds } }),
  ]);
  await Promise.all([
    TuitionRequest.deleteMany({ studentId: userId }),
    Thread.deleteMany({ _id: { $in: threadIds } }),
  ]);

  await deleteFirebaseUser(firebaseUid);
  await User.deleteOne({ _id: userId });
}

/** Returns how many profiles were purged. */
export async function purgeScheduledDeletions(now = new Date()): Promise<number> {
  // A Mongo range match never matches null or a missing field, so this picks
  // up scheduled profiles only.
  const due = await User.find({ deletionScheduledFor: { $lte: now } });
  for (const user of due) {
    await purgeUser(user._id as Types.ObjectId, user.firebaseUid);
  }
  return due.length;
}

// Reference: Firebase. 2026. Manage users. [Online].
//   Available at: https://firebase.google.com/docs/auth/admin/manage-users [Accessed 21 September 2026].
/**
 * The app deletes the Firebase account itself before calling DELETE
 * /users/me, so this is usually a no-op. It matters when that half failed and
 * left the identity behind.
 */
async function deleteFirebaseUser(uid?: string): Promise<void> {
  if (!uid) return;
  try {
    await admin.auth().deleteUser(uid);
  } catch (error) {
    if ((error as { code?: string }).code !== "auth/user-not-found") throw error;
  }
}
