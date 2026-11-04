// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";

process.env.CALENDAR_TOKEN_KEY = "11".repeat(32);

import { buildEvent, encryptToken, decryptToken, readIdTokenEmail, syncUpcomingBookings } from "./googleCalendar";
import { Booking, BookingStatus, CalendarLink, LessonMode } from "../models";

const session = {
  subjectName: "Maths",
  startTime: new Date("2026-10-01T14:00:00.000Z"),
  endTime: new Date("2026-10-01T15:30:00.000Z"),
  mode: LessonMode.IN_PERSON,
  location: "Library room 4",
};

test("buildEvent sends UTC instants and invites the student", () => {
  const event = buildEvent(session, "Thandi", "thandi@example.com") as any;
  assert.equal(event.summary, "Maths with Thandi");
  assert.equal(event.start.dateTime, "2026-10-01T14:00:00.000Z");
  assert.equal(event.end.dateTime, "2026-10-01T15:30:00.000Z");
  assert.equal(event.location, "Library room 4");
  assert.deepEqual(event.attendees, [{ email: "thandi@example.com" }]);
});

test("buildEvent omits the venue for online sessions and attendees without an email", () => {
  const event = buildEvent({ ...session, mode: LessonMode.ONLINE }, "Thandi") as any;
  assert.equal("location" in event, false);
  assert.equal("attendees" in event, false);
});

test("refresh tokens survive a round trip and are not stored in the clear", () => {
  const stored = encryptToken("1//refresh-token");
  assert.equal(stored.includes("1//refresh-token"), false);
  assert.equal(decryptToken(stored), "1//refresh-token");
  //Fresh IV per call, so the same token never encrypts to the same string.
  assert.notEqual(stored, encryptToken("1//refresh-token"));
});

test("a tampered token is rejected rather than half-decrypted", () => {
  const [iv, tag, body] = encryptToken("1//refresh-token").split(":");
  const flipped = body.startsWith("0") ? `1${body.slice(1)}` : `0${body.slice(1)}`;
  assert.throws(() => decryptToken([iv, tag, flipped].join(":")));
  assert.throws(() => decryptToken("not-a-stored-token"));
});

test("readIdTokenEmail reads the payload and shrugs off junk", () => {
  const payload = Buffer.from(JSON.stringify({ email: "tutor@example.com" })).toString("base64url");
  assert.equal(readIdTokenEmail(`header.${payload}.signature`), "tutor@example.com");
  assert.equal(readIdTokenEmail("not-a-jwt"), undefined);
  assert.equal(readIdTokenEmail(undefined), undefined);
});

test("syncUpcomingBookings picks only the user's upcoming unsynced sessions and survives a bad one", async (t) => {
  const find = t.mock.method(Booking, "find", async () => [
    { _id: "b1", tutorId: "bad-tutor", studentId: "u1" },
    { _id: "b2", tutorId: "t2", studentId: "u1" },
  ]);
  //The first session's lookup blows up; the second must still be attempted.
  const lookedUp: string[] = [];
  t.mock.method(CalendarLink, "findOne", (filter: { userId: string }) => ({
    select: async () => {
      lookedUp.push(filter.userId);
      if (filter.userId === "bad-tutor") throw new Error("boom");
      return null;
    },
  }));
  t.mock.method(console, "error", () => {});
  t.mock.method(console, "log", () => {});

  await syncUpcomingBookings("u1");

  const filter = find.mock.calls[0].arguments[0] as any;
  assert.deepEqual(filter.$or, [{ tutorId: "u1" }, { studentId: "u1" }]);
  assert.equal(filter.status, BookingStatus.CONFIRMED);
  assert.ok(filter.startTime.$gt instanceof Date);
  assert.deepEqual(filter.googleCalendarEventId, { $exists: false });
  assert.deepEqual(lookedUp, ["bad-tutor", "t2", "u1"]);
});
