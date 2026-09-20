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
