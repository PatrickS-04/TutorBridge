import type { MulticastMessage, SendResponse } from "firebase-admin/messaging";

import type { INotificationPreferences } from "../models/embedded";

const PREFERENCE_FOR_CATEGORY: Record<string, keyof INotificationPreferences> = {
  NEW_LISTING: "newListing",
  APPLICATION: "application",
  BOOKING: "booking",
  MESSAGE: "message",
  VERIFICATION: "verification"
}

export function pushAllowed(category: string, prefs?: Partial<INotificationPreferences> | null): boolean {
  const key = PREFERENCE_FOR_CATEGORY[category];
  if (!key || !prefs) return true;
  return prefs[key] !== false;
}

export interface PushPayload {
  title: string;
  body: string;
  category: string;
  notificationId: string;
  deepLink?: string;
}

// Reference: Firebase. 2026. Send a message using Firebase Admin SDK. [Online].
//   Available at: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk [Accessed 21 September 2026].
export function buildMulticast(tokens: string[], payload: PushPayload): MulticastMessage {
  const data: Record<string, string> = {
    title: payload.title,
    body: payload.body,
    category: payload.category,
    notificationId: payload.notificationId,
  };
  if (payload.deepLink) data.deepLink = payload.deepLink;

  return { tokens, data, android: { priority: "high", ttl: 24 * 60 * 60 * 1000 } }; // ttl in ms
}

type SendError = SendResponse["error"];

export function isStaleTokenError(error: SendError): boolean {
  if (!error) return false;
  if (
    error.code === "messaging/registration-token-not-registered" ||
    error.code === "messaging/invalid-registration-token"
  ) return true;
  return error.code === "messaging/invalid-argument" && /registration token/i.test(error.message ?? "");
}

export function findStaleTokens(
  tokens: string[],
  responses: Pick<SendResponse, "success" | "error">[]
): string[] {
  return tokens.filter((_, i) => {
    const r = responses[i];
    return r !== undefined && !r.success && isStaleTokenError(r.error);
  });
}