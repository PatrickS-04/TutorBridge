// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import { parseRepeat, seriesWindows, campusParts, fitsAvailability, SERIES_MIN, SERIES_MAX } from "./series";

/**
 * The rules behind a weekly series that need no database: what a `repeat` may say,
 * the dates it expands to, and whether a session sits inside a tutor's weekly hours
 * in campus (South African) time.
 */

// Monday 2 November 2026, 14:00-15:00 in South Africa (UTC+2).
const start = new Date("2026-11-02T12:00:00Z");
const end = new Date("2026-11-02T13:00:00Z");

test("a weekly repeat of 2 to 8 sessions is accepted", () => {
  for (const count of [SERIES_MIN, 4, SERIES_MAX]) {
    assert.deepEqual(parseRepeat({ frequency: "WEEKLY", count }), { repeat: { count } });
  }
});

test("a repeat that is not a weekly count of 2 to 8 whole sessions is refused, naming the field", () => {
  const cases: [unknown, string][] = [
    [undefined, "repeat"],
    [null, "repeat"],
    ["WEEKLY", "repeat"],
    [[], "repeat"],
    [{ count: 4 }, "repeat.frequency"],
    [{ frequency: "DAILY", count: 4 }, "repeat.frequency"],
    [{ frequency: "weekly", count: 4 }, "repeat.frequency"],
    [{ frequency: "WEEKLY" }, "repeat.count"],
    [{ frequency: "WEEKLY", count: 1 }, "repeat.count"],
    [{ frequency: "WEEKLY", count: 9 }, "repeat.count"],
    [{ frequency: "WEEKLY", count: 0 }, "repeat.count"],
    [{ frequency: "WEEKLY", count: -3 }, "repeat.count"],
    [{ frequency: "WEEKLY", count: 2.5 }, "repeat.count"],
    [{ frequency: "WEEKLY", count: "4" }, "repeat.count"],
  ];
  for (const [input, field] of cases) {
    const parsed = parseRepeat(input) as { error?: string; field?: string };
    assert.ok(parsed.error, `expected a refusal for ${JSON.stringify(input)}`);
    assert.equal(parsed.field, field, JSON.stringify(input));
  }
});

test("a series is the first session and the same time each following week", () => {
  const windows = seriesWindows(start, end, 4);
  assert.equal(windows.length, 4);
  assert.equal(windows[0].start.getTime(), start.getTime());
  windows.forEach((w, i) => {
    assert.equal(w.start.toISOString(), new Date(start.getTime() + i * 7 * 86_400_000).toISOString());
    assert.equal(w.end.getTime() - w.start.getTime(), 3_600_000, "every session keeps the same length");
  });
  assert.deepEqual(
    windows.map((w) => w.start.toISOString()),
    ["2026-11-02T12:00:00.000Z", "2026-11-09T12:00:00.000Z", "2026-11-16T12:00:00.000Z", "2026-11-23T12:00:00.000Z"]
  );
});

test("building a series does not change the dates it was built from", () => {
  const before = [start.getTime(), end.getTime()];
  seriesWindows(start, end, 3);
  assert.deepEqual([start.getTime(), end.getTime()], before);
});

test("campus time is two hours ahead of UTC, and can be a different day", () => {
  assert.deepEqual(campusParts(start), { day: "MONDAY", minutes: 14 * 60, date: "2026-11-02" });
  // 22:30 UTC on Monday is 00:30 on Tuesday in South Africa.
  assert.deepEqual(campusParts(new Date("2026-11-02T22:30:00Z")), { day: "TUESDAY", minutes: 30, date: "2026-11-03" });
});

const monday = [{ day: "MONDAY", startTime: "09:00", endTime: "17:00" }] as any;

test("a session inside a window on the right day fits", () => {
  assert.equal(fitsAvailability(monday, start, end), true);
});

test("a session may start when the window opens and end when it closes", () => {
  assert.equal(fitsAvailability(monday, new Date("2026-11-02T07:00:00Z"), new Date("2026-11-02T08:00:00Z")), true); // 09:00-10:00
  assert.equal(fitsAvailability(monday, new Date("2026-11-02T14:00:00Z"), new Date("2026-11-02T15:00:00Z")), true); // 16:00-17:00
});

test("a session that starts too early, ends too late, or runs over either edge does not fit", () => {
  assert.equal(fitsAvailability(monday, new Date("2026-11-02T06:00:00Z"), new Date("2026-11-02T07:00:00Z")), false); // 08:00-09:00
  assert.equal(fitsAvailability(monday, new Date("2026-11-02T06:30:00Z"), new Date("2026-11-02T07:30:00Z")), false); // 08:30-09:30
  assert.equal(fitsAvailability(monday, new Date("2026-11-02T14:30:00Z"), new Date("2026-11-02T15:30:00Z")), false); // 16:30-17:30
  assert.equal(fitsAvailability(monday, new Date("2026-11-02T15:00:00Z"), new Date("2026-11-02T16:00:00Z")), false); // 17:00-18:00
});

test("the right hours on the wrong day do not fit, and a tutor with no windows fits nothing", () => {
  assert.equal(fitsAvailability(monday, new Date("2026-11-03T12:00:00Z"), new Date("2026-11-03T13:00:00Z")), false);
  assert.equal(fitsAvailability([], start, end), false);
});

test("any one of several windows is enough", () => {
  const slots = [
    { day: "MONDAY", startTime: "08:00", endTime: "10:00" },
    { day: "MONDAY", startTime: "13:00", endTime: "16:00" },
    { day: "TUESDAY", startTime: "09:00", endTime: "12:00" },
  ] as any;
  assert.equal(fitsAvailability(slots, start, end), true); // 14:00-15:00 is in the second
  assert.equal(fitsAvailability(slots, new Date("2026-11-02T09:00:00Z"), new Date("2026-11-02T10:00:00Z")), false); // 11:00-12:00 is between
});

test("a session that runs past midnight never fits, even if both days have hours", () => {
  const late = [
    { day: "MONDAY", startTime: "20:00", endTime: "23:59" },
    { day: "TUESDAY", startTime: "00:00", endTime: "06:00" },
  ] as any;
  // 23:30 Monday to 00:30 Tuesday, campus time.
  assert.equal(fitsAvailability(late, new Date("2026-11-02T21:30:00Z"), new Date("2026-11-02T22:30:00Z")), false);
});

test("the same wall-clock time every week stays inside the window all the way through a series", () => {
  const windows = seriesWindows(start, end, 8);
  assert.ok(windows.every((w) => fitsAvailability(monday, w.start, w.end)));
});
