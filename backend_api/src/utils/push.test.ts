import { test } from "node:test";
import assert from "node:assert/strict";
import { buildMulticast, findStaleTokens } from "./push";

const ok = { success: true };
const failed = (code: string, message = "") => ({ success: false, error: { code, message } as any });

test("findStaleTokens prunes dead tokens only", () => {
  const tokens = ["live", "uninstalled", "malformed", "fcm-outage", "bad-payload"];
  const responses = [
    ok,
    failed("messaging/registration-token-not-registered"),
    failed("messaging/invalid-argument", "The registration token is not a valid FCM registration token"),
    failed("messaging/server-unavailable"),
    failed("messaging/invalid-argument", "Invalid JSON payload received."),
  ];
  assert.deepEqual(findStaleTokens(tokens, responses), ["uninstalled", "malformed"]);
});

test("buildMulticast is data-only with string values", () => {
  const message = buildMulticast(["t1"], {
    title: "Session confirmed", body: "Maths is confirmed", category: "BOOKING",
    notificationId: "abc123", deepLink: "tutorbridge://bookings/xyz",
  });
  assert.equal("notification" in message, false);
  assert.ok(Object.values(message.data!).every((v) => typeof v === "string"));
});