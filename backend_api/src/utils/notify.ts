import admin from "firebase-admin";
import {
  NotificationEvent, NotificationToken, NotificationCategory, INotificationEvent,
} from "../models";
import { buildMulticast, findStaleTokens } from "./push";

export function notify(
  institutionId: string, userId: string, category: NotificationCategory,
  title: string, body: string, deepLink?: string
) {
  deliver(institutionId, userId, category, title, body, deepLink).catch((error) =>
    console.error("notify failed:", error)
  );
}

async function deliver(
  institutionId: string, userId: string, category: NotificationCategory,
  title: string, body: string, deepLink?: string
) {
  const event = await NotificationEvent.create({ institutionId, userId, category, title, body, deepLink });
  await sendPush(event).catch((error) => console.error("push failed:", error));
}

async function sendPush(event: INotificationEvent) {
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