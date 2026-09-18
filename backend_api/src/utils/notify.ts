import { NotificationEvent, NotificationCategory } from "../models";

/**
 * Writes a notification_events row. The app's notifications screen reads these
 * back through GET /notifications; there is no push transport yet, so
 * pushSentAt stays unset.
 *
 * Fire-and-forget on purpose: a failed notification must never fail the
 * booking or application that triggered it.
 */
export function notify(
  institutionId: string,
  userId: string,
  category: NotificationCategory,
  title: string,
  body: string,
  deepLink?: string
) {
  NotificationEvent.create({ institutionId, userId, category, title, body, deepLink }).catch((error) =>
    console.error("notify failed:", error)
  );
}
