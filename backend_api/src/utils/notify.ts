import admin from "firebase-admin";
import {
  NotificationEvent, NotificationToken, NotificationCategory, INotificationEvent, UserSettings, User, Locale,
} from "../models";
import { buildMulticast, findStaleTokens, pushAllowed } from "./push";
import { NotificationKey, NotificationValues, render } from "./messages";

export function notify(
  institutionId: string, userId: string, category: NotificationCategory,
  key: NotificationKey, values: NotificationValues, deepLink?: string
) {
  deliver(institutionId, userId, category, key, values, deepLink).catch((error) =>
    console.error("notify failed:", error)
  );
}

/**
 * The language the recipient reads the app in: what they chose in Settings, else the
 * locale on their account, else English. A lookup that fails must not lose the
 * notification, so it falls back rather than throws.
 */
export async function localeOf(userId: string): Promise<Locale> {
  try {
    const settings = await UserSettings.findOne({ userId }).select("language").lean();
    if (settings?.language) return settings.language;
    const user = await User.findById(userId).select("locale").lean();
    return user?.locale ?? Locale.EN;
  } catch (error) {
    console.error("locale lookup failed:", error);
    return Locale.EN;
  }
}

async function deliver(
  institutionId: string, userId: string, category: NotificationCategory,
  key: NotificationKey, values: NotificationValues, deepLink?: string
) {
  const locale = await localeOf(userId);
  const { title, body } = render(key, locale, values);
  const event = await NotificationEvent.create({ institutionId, userId, category, title, body, deepLink, locale });
  await sendPush(event).catch((error) => console.error("push failed:", error));
}

// Reference: Firebase. 2026. Send a message using Firebase Admin SDK. [Online].
//   Available at: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk [Accessed 21 September 2026].
async function sendPush(event: INotificationEvent) {
  const settings = await UserSettings.findOne({ userId: event.userId }).select("notifications").lean();
  if (!pushAllowed(event.category, settings?.notifications)) return;

  const rows = await NotificationToken.find({ userId: event.userId }).select("token").lean();
  if (rows.length === 0) return;

  const tokens = rows.map((row) => row.token);
  const response = await admin.messaging().sendEachForMulticast(
    buildMulticast(tokens, {
      title: event.title, body: event.body, category: event.category,
      notificationId: String(event._id), deepLink: event.deepLink,
    })
  );

  const stale = findStaleTokens(tokens, response.responses);
  if (stale.length > 0) await NotificationToken.deleteMany({ token: { $in: stale } });

  if (response.successCount > 0) {
    await NotificationEvent.updateOne({ _id: event._id }, { $set: { pushSentAt: new Date() } });
  }
}