import type { MulticastMessage, SendResponse } from "firebase-admin/messaging";

export interface PushPayload {
  title: string;
  body: string;
  category: string;
  notificationId: string;
  deepLink?: string;
}

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