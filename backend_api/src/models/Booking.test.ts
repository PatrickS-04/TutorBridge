// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import { Types } from "mongoose";
import { Booking, LessonMode } from "../models";

/**
 * The venue is free text so a student can ask to meet at a coffee shop, which
 * leaves the schema as the only thing between the client and the database.
 * validateSync() runs that check without needing a connection.
 */
const session = {
  institutionId: new Types.ObjectId(),
  tutorId: new Types.ObjectId(),
  studentId: new Types.ObjectId(),
  subjectName: "Maths",
  startTime: new Date("2026-10-01T14:00:00.000Z"),
  endTime: new Date("2026-10-01T15:00:00.000Z"),
  mode: LessonMode.IN_PERSON,
  agreedRatePerHour: 250,
};

test("a venue the student named is accepted and trimmed", () => {
  const booking = new Booking({ ...session, location: "  The Daily Grind coffee shop, Main Road  " });
  assert.equal(booking.validateSync(), undefined);
  assert.equal(booking.location, "The Daily Grind coffee shop, Main Road");
});

test("an over-long venue is rejected, not stored", () => {
  const booking = new Booking({ ...session, location: "x".repeat(121) });
  assert.equal(booking.validateSync()?.name, "ValidationError");
  assert.ok(booking.validateSync()?.errors.location);
});

/**
 * The tutor's prep notes ride on the booking, which the student also reads,
 * so the projection is the only thing keeping them private.
 */
test("tutor notes are left out of a query that does not ask for them", () => {
  assert.equal(Booking.schema.path("tutorNotes").options.select, false);
});

test("a checklist row and side notes are accepted and trimmed", () => {
  const booking = new Booking({
    ...session,
    tutorNotes: {
      items: [{ text: "  Mark the problem set  ", done: true }, { text: "Set homework" }],
      text: "  Guardian wants a progress update.  ",
    },
  });

  assert.equal(booking.validateSync(), undefined);
  assert.equal(booking.tutorNotes?.items[0].text, "Mark the problem set");
  assert.equal(booking.tutorNotes?.items[0].done, true);
  assert.equal(booking.tutorNotes?.items[1].done, false); // defaults to not done
  assert.equal(booking.tutorNotes?.text, "Guardian wants a progress update.");
});

test("an over-long checklist row is rejected, not stored", () => {
  const booking = new Booking({ ...session, tutorNotes: { items: [{ text: "x".repeat(201) }] } });
  assert.equal(booking.validateSync()?.name, "ValidationError");
});
