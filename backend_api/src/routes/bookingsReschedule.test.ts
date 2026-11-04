// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test, after } from "node:test";
import assert from "node:assert/strict";
import { AddressInfo } from "node:net";
import express from "express";
import admin from "firebase-admin";
import mongoose from "mongoose";
import { routeMounts } from ".";
import { errorHandler } from "../middleware/errors";
import {
  User,
  UserRole,
  Booking,
  BookingStatus,
  CalendarLink,
  NotificationEvent,
  NotificationToken,
  UserSettings,
} from "../models";

/**
 * Moving a session instead of cancelling it and booking again: who may do it,
 * what state it leaves the booking in, and the clashes it refuses. Firebase
 * and the database are stubbed, as in tutorAccounts.test.ts.
 */

const institutionId = new mongoose.Types.ObjectId();
const tutor = new mongoose.Types.ObjectId();
const student = new mongoose.Types.ObjectId();
const stranger = new mongoose.Types.ObjectId();

function signedInAs(userId: mongoose.Types.ObjectId, role: UserRole) {
  admin.auth().verifyIdToken = (async () => ({ uid: `uid-${userId}` })) as any;
  User.findOne = ((filter: any) =>
    Promise.resolve(filter.firebaseUid ? { _id: userId, institutionId, role } : null)) as any;
}

// notify() and the calendar sync run after the response; keep both off the database.
const notifications: any[] = [];
NotificationEvent.create = (async (event: any) => {
  notifications.push(event);
  return { ...event, _id: new mongoose.Types.ObjectId() };
}) as any;
NotificationToken.find = (() => ({ select: () => ({ lean: async () => [] }) })) as any;
// The recipient's language is looked up before a notification is written: none chosen, no account locale.
UserSettings.findOne = (() => ({ select: () => ({ lean: async () => null }) })) as any;
User.findById = (() => ({ select: () => ({ lean: async () => null }) })) as any;
CalendarLink.findOne = (() => ({ select: async () => null })) as any;

const tomorrow = (hour: number) => {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + 1);
  d.setUTCHours(hour, 0, 0, 0);
  return d;
};

/** A booking at 10:00-11:00 tomorrow; `clash` says whether the tutor has another confirmed session in the way. */
function stubBooking(status: BookingStatus, clash = false) {
  const booking: any = {
    _id: new mongoose.Types.ObjectId(),
    tutorId: tutor,
    studentId: student,
    subjectName: "Mathematics",
    startTime: tomorrow(10),
    endTime: tomorrow(11),
    status,
    saved: false,
    async save() {
      this.saved = true;
    },
    async updateOne() {},
  };
  Booking.findOne = ((filter: any) => {
    // The clash lookup excludes the booking itself; the ownership lookup does not.
    if (filter._id?.$ne) return Promise.resolve(clash ? { _id: new mongoose.Types.ObjectId() } : null);
    // Participation is part of the query: the caller's id has to match the tutor or the student.
    const mine = filter.$or.some(
      (c: any) => String(c.tutorId) === String(booking.tutorId) || String(c.studentId) === String(booking.studentId)
    );
    return Promise.resolve(mine ? booking : null);
  }) as any;
  return booking;
}

const as = signedInAs;

const app = express();
app.use(express.json());
for (const [path, router] of Object.entries(routeMounts)) app.use(path, router);
app.use(errorHandler);
const server = app.listen(0);
after(() => server.close());

function call(path: string, body?: object) {
  const { port } = server.address() as AddressInfo;
  return fetch(`http://127.0.0.1:${port}${path}`, {
    method: "PUT",
    headers: { authorization: "Bearer stubbed", "content-type": "application/json" },
    body: body && JSON.stringify(body),
  }).then(async (res) => ({ status: res.status, body: await res.json().catch(() => null) }));
}

const id = "66f1a2b3c4d5e6f708192a3b";
const newWindow = () => ({ startTime: tomorrow(14).toISOString(), endTime: tomorrow(15).toISOString() });

test("a tutor moving a confirmed session keeps it confirmed and tells the student", async () => {
  as(tutor, UserRole.TUTOR);
  const booking = stubBooking(BookingStatus.CONFIRMED);
  const { status, body } = await call(`/bookings/${id}/reschedule`, newWindow());

  assert.equal(status, 200);
  assert.equal(body.status, BookingStatus.CONFIRMED);
  assert.equal(booking.startTime.toISOString(), tomorrow(14).toISOString());
  assert.equal(booking.saved, true);

  await new Promise((resolve) => setTimeout(resolve, 20)); // notify() is fire-and-forget
  assert.equal(String(notifications.at(-1)?.userId), String(student));
  assert.equal(notifications.at(-1)?.title, "Session rescheduled");
});

test("a student moving a confirmed session sends it back to the tutor to confirm", async () => {
  as(student, UserRole.GUARDIAN_LEARNER);
  const booking = stubBooking(BookingStatus.CONFIRMED);
  const { status, body } = await call(`/bookings/${id}/reschedule`, newWindow());

  assert.equal(status, 200);
  assert.equal(body.status, BookingStatus.REQUESTED);
  assert.equal(booking.status, BookingStatus.REQUESTED);

  await new Promise((resolve) => setTimeout(resolve, 20));
  assert.equal(String(notifications.at(-1)?.userId), String(tutor));
  assert.equal(notifications.at(-1)?.title, "Reschedule requested");
});

test("a tutor moving a still-requested session confirms it", async () => {
  as(tutor, UserRole.TUTOR);
  stubBooking(BookingStatus.REQUESTED);
  const { body } = await call(`/bookings/${id}/reschedule`, newWindow());
  assert.equal(body.status, BookingStatus.CONFIRMED);
});

test("a time the tutor already has a confirmed session in is refused", async () => {
  as(student, UserRole.GUARDIAN_LEARNER);
  const booking = stubBooking(BookingStatus.CONFIRMED, true);
  const { status, body } = await call(`/bookings/${id}/reschedule`, newWindow());

  assert.equal(status, 409);
  assert.equal(body?.error?.code, "SLOT_CONFLICT");
  assert.equal(booking.saved, false);
});

test("a finished or cancelled session cannot be moved", async () => {
  as(tutor, UserRole.TUTOR);
  for (const state of [BookingStatus.COMPLETED, BookingStatus.CANCELLED]) {
    stubBooking(state);
    const { status, body } = await call(`/bookings/${id}/reschedule`, newWindow());
    assert.equal(status, 409, state);
    assert.equal(body?.error?.code, "INVALID_STATE", state);
  }
});

test("only the two people on the session can move it", async () => {
  as(stranger, UserRole.GUARDIAN_LEARNER);
  stubBooking(BookingStatus.CONFIRMED);
  const { status } = await call(`/bookings/${id}/reschedule`, newWindow());
  assert.equal(status, 404);
});

test("a time in the past, or an end before the start, is a 400", async () => {
  as(tutor, UserRole.TUTOR);
  stubBooking(BookingStatus.CONFIRMED);
  const past = await call(`/bookings/${id}/reschedule`, {
    startTime: "2020-01-01T10:00:00Z",
    endTime: "2020-01-01T11:00:00Z",
  });
  assert.equal(past.status, 400);

  const backwards = await call(`/bookings/${id}/reschedule`, {
    startTime: tomorrow(15).toISOString(),
    endTime: tomorrow(14).toISOString(),
  });
  assert.equal(backwards.status, 400);

  assert.equal((await call(`/bookings/${id}/reschedule`, {})).status, 400);
});
