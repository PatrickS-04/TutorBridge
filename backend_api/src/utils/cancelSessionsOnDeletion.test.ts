// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import { noticesFor, upcomingSessionsFilter } from "./cancelSessionsOnDeletion";
import { BookingStatus } from "../models";

const booking = (id: string, tutorId: string, studentId: string, seriesId?: string) =>
  ({ _id: id, tutorId, studentId, seriesId }) as any;

test("only sessions yet to happen are picked, so a held session stays on a tutor's claim", () => {
  const now = new Date("2026-11-04T12:00:00.000Z");
  const filter = upcomingSessionsFilter("me", now);

  assert.deepEqual(filter.startTime, { $gt: now });
  assert.ok(!filter.status.$in.includes(BookingStatus.COMPLETED));
  assert.ok(!filter.status.$in.includes(BookingStatus.CANCELLED));
});

test("it covers the person on either side of a session", () => {
  assert.deepEqual(upcomingSessionsFilter("me", new Date()).$or, [{ tutorId: "me" }, { studentId: "me" }]);
});

test("the notice goes to the other person, whichever side is leaving", () => {
  assert.equal(noticesFor([booking("1", "tutor", "student")], "tutor")[0].otherParty, "student");
  assert.equal(noticesFor([booking("1", "tutor", "student")], "student")[0].otherParty, "tutor");
});

test("a series gets one notice, and separate sessions get one each", () => {
  const notices = noticesFor(
    [
      booking("1", "tutor", "a", "series"),
      booking("2", "tutor", "a", "series"),
      booking("3", "tutor", "b"),
    ],
    "tutor"
  );
  assert.equal(notices.length, 2);
  assert.equal(notices[0].bookings.length, 2);
  assert.equal(notices[1].otherParty, "b");
});
