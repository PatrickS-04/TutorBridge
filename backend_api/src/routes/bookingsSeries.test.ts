// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { AddressInfo } from "node:net";
import express from "express";
import admin from "firebase-admin";
import mongoose from "mongoose";
import { routeMounts } from ".";
import { errorHandler } from "../middleware/errors";
import { campusParts } from "../utils/series";
import {
  User,
  UserRole,
  Booking,
  BookingStatus,
  CalendarLink,
  NotificationEvent,
  NotificationToken,
  Thread,
  UserSettings,
} from "../models";

/**
 * Booking the same time every week as one series, and confirming and cancelling it as
 * one: what is checked before anything is written, what is written, and who is told.
 * Firebase and the database are stubbed, as in bookingsReschedule.test.ts.
 */

const institutionId = new mongoose.Types.ObjectId();
const tutor = new mongoose.Types.ObjectId();
const student = new mongoose.Types.ObjectId();
const stranger = new mongoose.Types.ObjectId();

// ---- who is signed in, and what the tutor lookup finds --------------------------------

/** The tutor the student is booking: verified, free on Mondays 09:00-17:00 campus time. */
let tutorDoc: any;
let signedIn: { id: mongoose.Types.ObjectId; role: UserRole };

function as(id: mongoose.Types.ObjectId, role: UserRole) {
  signedIn = { id, role };
}

admin.auth().verifyIdToken = (async () => ({ uid: "uid-test" })) as any;
User.findOne = ((filter: any) => {
  if (filter.firebaseUid) return Promise.resolve({ _id: signedIn.id, institutionId, role: signedIn.role });
  // The tutor lookup is a query chained with populate().
  const found = filter._id !== undefined && String(filter._id) === String(tutor) ? tutorDoc : null;
  return { populate: async () => found };
}) as any;

// ---- notifications, calendar and threads stay off the database ------------------------

const notifications: any[] = [];
NotificationEvent.create = (async (event: any) => {
  notifications.push(event);
  return { ...event, _id: new mongoose.Types.ObjectId() };
}) as any;
NotificationToken.find = (() => ({ select: () => ({ lean: async () => [] }) })) as any;
UserSettings.findOne = (() => ({ select: () => ({ lean: async () => null }) })) as any;
User.findById = (() => ({ select: () => ({ lean: async () => null }) })) as any;
CalendarLink.findOne = (() => ({ select: async () => null })) as any;
Thread.findOne = (async () => ({ _id: new mongoose.Types.ObjectId() })) as any;

// ---- the bookings collection ------------------------------------------------------------

/** What was written by Booking.create, in order. */
let created: any[];
/** The tutor's confirmed sessions that the series is checked against. */
let confirmedSessions: { startTime: Date; endTime: Date }[];
/** The filter the series clash check asked Booking.find for. */
let clashFilter: any;

beforeEach(() => {
  as(student, UserRole.GUARDIAN_LEARNER);
  tutorDoc = {
    _id: tutor,
    institutionId,
    role: UserRole.TUTOR,
    hourlyRate: 250,
    availability: [{ day: "MONDAY", startTime: "09:00", endTime: "17:00" }],
    subjectIds: [{ name: "Mathematics" }],
  };
  created = [];
  confirmedSessions = [];
  clashFilter = undefined;
  notifications.length = 0;

  Booking.findOne = (async () => null) as any; // the single-session clash check
  Booking.find = ((filter: any) => {
    clashFilter = filter;
    return Promise.resolve(confirmedSessions);
  }) as any;
  Booking.create = (async (docs: any) => {
    const list = (Array.isArray(docs) ? docs : [docs]).map((d: any) => ({ ...d, _id: new mongoose.Types.ObjectId() }));
    created.push(...list);
    return list;
  }) as any;
});

// ---- helpers ----------------------------------------------------------------------------

const DAY = 86_400_000;

/** The next Monday after today at `hour`:00 campus time (UTC+2), as an instant. */
function nextMonday(hour: number, weeksAhead = 1): Date {
  const d = new Date();
  d.setUTCHours(hour - 2, 0, 0, 0);
  d.setTime(d.getTime() + DAY);
  while (campusParts(d).day !== "MONDAY") d.setTime(d.getTime() + DAY);
  return new Date(d.getTime() + (weeksAhead - 1) * 7 * DAY);
}

const hours = (from: Date, n = 1) => new Date(from.getTime() + n * 3_600_000);

const app = express();
app.use(express.json());
for (const [path, router] of Object.entries(routeMounts)) app.use(path, router);
app.use(errorHandler);
const server = app.listen(0);
after(() => server.close());

function call(method: string, path: string, body?: object) {
  const { port } = server.address() as AddressInfo;
  return fetch(`http://127.0.0.1:${port}${path}`, {
    method,
    headers: { authorization: "Bearer stubbed", "content-type": "application/json" },
    body: body && JSON.stringify(body),
  }).then(async (res) => ({ status: res.status, body: await res.json().catch(() => null) }));
}

const tick = () => new Promise((resolve) => setTimeout(resolve, 20)); // notify() is fire-and-forget

/** A direct booking of `tutor` on the coming Monday at 14:00-15:00, repeated as asked. */
function bookSeries(repeat: unknown, start = nextMonday(14)) {
  return call("POST", "/bookings/direct", {
    tutorId: String(tutor),
    startTime: start.toISOString(),
    endTime: hours(start).toISOString(),
    location: "Library 2.14",
    ...(repeat === undefined ? {} : { repeat }),
  });
}

// ==========================================================================================
// Booking a series
// ==========================================================================================

test("a weekly series of four books four sessions that share one series id, a week apart", async () => {
  const start = nextMonday(14);
  const { status, body } = await bookSeries({ frequency: "WEEKLY", count: 4 }, start);

  assert.equal(status, 201);
  assert.equal(created.length, 4);
  const seriesId = String(created[0].seriesId);
  assert.ok(seriesId && seriesId !== "undefined");
  created.forEach((b, i) => {
    assert.equal(String(b.seriesId), seriesId, "every session carries the same series id");
    assert.equal(b.seriesIndex, i + 1);
    assert.equal(b.seriesCount, 4);
    assert.equal(b.status, BookingStatus.REQUESTED);
    assert.equal(b.startTime.getTime(), start.getTime() + i * 7 * DAY);
    assert.equal(b.endTime.getTime() - b.startTime.getTime(), 3_600_000);
    assert.equal(String(b.tutorId), String(tutor));
    assert.equal(String(b.studentId), String(student));
    assert.equal(b.subjectName, "Mathematics");
    assert.equal(b.location, "Library 2.14");
    assert.equal(b.agreedRatePerHour, 250);
  });

  assert.equal(body.seriesId, seriesId);
  assert.equal(body.seriesCount, 4);
  assert.equal(body.bookings.length, 4);
  assert.deepEqual(body.bookings.map((b: any) => b.seriesIndex), [1, 2, 3, 4]);
  assert.equal(body.id, body.bookings[0].id, "the top-level booking is the first session");
});

test("the series sizes at both ends, 2 and 8, are accepted", async () => {
  for (const count of [2, 8]) {
    created = [];
    const { status, body } = await bookSeries({ frequency: "WEEKLY", count });
    assert.equal(status, 201);
    assert.equal(created.length, count);
    assert.equal(body.seriesCount, count);
  }
});

test("booking without a repeat is a single session, exactly as before", async () => {
  const { status, body } = await bookSeries(undefined);

  assert.equal(status, 201);
  assert.equal(created.length, 1);
  assert.equal(created[0].seriesId, undefined);
  assert.equal(created[0].seriesIndex, undefined);
  assert.equal(created[0].seriesCount, undefined);
  assert.deepEqual(Object.keys(body).sort(), ["endTime", "id", "startTime", "status"]);
});

test("the tutor is told once about a series, not once a week", async () => {
  await bookSeries({ frequency: "WEEKLY", count: 4 });
  await tick();

  assert.equal(notifications.length, 1);
  assert.equal(String(notifications[0].userId), String(tutor));
  assert.equal(notifications[0].title, "New weekly booking");
  assert.match(notifications[0].body, /Mathematics/);
  assert.match(notifications[0].body, /Sessions: 4/);
});

test("a single session still sends the ordinary new-booking notification", async () => {
  await bookSeries(undefined);
  await tick();

  assert.equal(notifications.length, 1);
  assert.equal(notifications[0].title, "New student booking");
});

test("a series is checked against only the tutor's confirmed sessions, across every week at once", async () => {
  await bookSeries({ frequency: "WEEKLY", count: 4 });

  assert.equal(String(clashFilter.tutorId), String(tutor));
  assert.equal(clashFilter.status, BookingStatus.CONFIRMED);
  assert.equal(clashFilter.$or.length, 4);
});

for (const [name, repeat, field] of [
  ["a series of one", { frequency: "WEEKLY", count: 1 }, "repeat.count"],
  ["a series of nine", { frequency: "WEEKLY", count: 9 }, "repeat.count"],
  ["a count that is not a whole number", { frequency: "WEEKLY", count: 3.5 }, "repeat.count"],
  ["a count sent as text", { frequency: "WEEKLY", count: "4" }, "repeat.count"],
  ["no count", { frequency: "WEEKLY" }, "repeat.count"],
  ["a daily repeat", { frequency: "DAILY", count: 4 }, "repeat.frequency"],
  ["no frequency", { count: 4 }, "repeat.frequency"],
  ["a repeat that is not an object", "WEEKLY", "repeat"],
] as [string, unknown, string][]) {
  test(`${name} is refused with a 400 naming ${field}, and books nothing`, async () => {
    const { status, body } = await bookSeries(repeat);

    assert.equal(status, 400);
    assert.equal(body.error.code, "VALIDATION_ERROR");
    assert.equal(body.error.field, field);
    assert.equal(created.length, 0);
  });
}

test("a series whose first session is in the past is refused, and books nothing", async () => {
  const past = new Date(Date.now() - 2 * DAY);
  const { status, body } = await bookSeries({ frequency: "WEEKLY", count: 4 }, past);

  assert.equal(status, 400);
  assert.equal(body.error.field, "startTime");
  assert.match(body.error.message, /future/);
  assert.equal(created.length, 0);
});

test("a series at a time the tutor is not available is refused, listing every week", async () => {
  const evening = nextMonday(18); // 18:00-19:00, after the 17:00 close
  const { status, body } = await bookSeries({ frequency: "WEEKLY", count: 3 }, evening);

  assert.equal(status, 400);
  assert.equal(body.error.code, "OUTSIDE_AVAILABILITY");
  assert.equal(body.error.field, "startTime");
  assert.deepEqual(
    body.error.dates,
    [0, 1, 2].map((i) => new Date(evening.getTime() + i * 7 * DAY).toISOString())
  );
  assert.equal(created.length, 0);
});

test("a series on a day the tutor does not work is refused", async () => {
  const tuesday = new Date(nextMonday(14).getTime() + DAY);
  const { status, body } = await bookSeries({ frequency: "WEEKLY", count: 2 }, tuesday);

  assert.equal(status, 400);
  assert.equal(body.error.code, "OUTSIDE_AVAILABILITY");
  assert.equal(created.length, 0);
});

test("a tutor with no weekly hours cannot be booked as a series", async () => {
  tutorDoc.availability = [];
  const { status, body } = await bookSeries({ frequency: "WEEKLY", count: 4 });

  assert.equal(status, 400);
  assert.equal(body.error.code, "OUTSIDE_AVAILABILITY");
  assert.equal(created.length, 0);
});

test("one confirmed session in the way refuses the whole series, naming that week, and books nothing", async () => {
  const start = nextMonday(14);
  const week3 = new Date(start.getTime() + 2 * 7 * DAY);
  confirmedSessions = [{ startTime: week3, endTime: hours(week3) }];

  const { status, body } = await bookSeries({ frequency: "WEEKLY", count: 4 }, start);

  assert.equal(status, 409);
  assert.equal(body.error.code, "SERIES_CONFLICT");
  assert.equal(body.error.field, "startTime");
  assert.deepEqual(body.error.dates, [week3.toISOString()]);
  assert.equal(created.length, 0);
  await tick();
  assert.equal(notifications.length, 0, "nobody is told about a series that was not booked");
});

test("every clashing week is named, not only the first", async () => {
  const start = nextMonday(14);
  const week = (n: number) => new Date(start.getTime() + n * 7 * DAY);
  confirmedSessions = [
    { startTime: week(1), endTime: hours(week(1)) },
    { startTime: week(3), endTime: hours(week(3)) },
  ];

  const { status, body } = await bookSeries({ frequency: "WEEKLY", count: 4 }, start);

  assert.equal(status, 409);
  assert.deepEqual(body.error.dates, [week(1).toISOString(), week(3).toISOString()]);
});

test("a session that only touches the series at an edge is not a clash", async () => {
  const start = nextMonday(14);
  // Ends exactly when week 2 starts, and another starts exactly when it ends.
  const week2 = new Date(start.getTime() + 7 * DAY);
  confirmedSessions = [
    { startTime: hours(week2, -1), endTime: week2 },
    { startTime: hours(week2), endTime: hours(week2, 2) },
  ];

  const { status } = await bookSeries({ frequency: "WEEKLY", count: 3 }, start);

  assert.equal(status, 201);
  assert.equal(created.length, 3);
});

test("a tutor who is not bookable is a 404 for a series too", async () => {
  tutorDoc = null;
  const { status, body } = await bookSeries({ frequency: "WEEKLY", count: 4 });

  assert.equal(status, 404);
  assert.equal(body.error.code, "NOT_FOUND");
  assert.equal(created.length, 0);
});

test("a booking missing its tutor or times is refused before any series logic runs", async () => {
  const { status, body } = await call("POST", "/bookings/direct", { repeat: { frequency: "WEEKLY", count: 4 } });

  assert.equal(status, 400);
  assert.equal(body.error.code, "VALIDATION_ERROR");
  assert.equal(created.length, 0);
});

test("a signed-in stranger can book a series for themselves; the student on the series is whoever asked", async () => {
  as(stranger, UserRole.GUARDIAN_LEARNER);
  await bookSeries({ frequency: "WEEKLY", count: 2 });

  assert.equal(created.length, 2);
  assert.ok(created.every((b) => String(b.studentId) === String(stranger)));
});
