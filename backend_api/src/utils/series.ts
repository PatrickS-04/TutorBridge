import { IAvailabilitySlot, DayOfWeek } from "../models";

/**
 * A series is at least 2 sessions. A plain weekly series is 2 to 8 sessions (4 is "a month"); a pattern over
 * several days runs for 1 to 8 weeks on 1 to 7 different weekdays, and never more than 24 sessions in all.
 */
export const SERIES_MIN = 2;
export const SERIES_MAX = 8;
export const SERIES_MAX_DAYS = 7;
export const SERIES_MAX_SESSIONS = 24;

/** Availability windows are written in campus time, which is South African time and has no daylight saving. */
export const CAMPUS_TIME_ZONE = "Africa/Johannesburg";

const DAY_MS = 24 * 60 * 60 * 1000;

/** The weekday names in order, Monday first, as campus time and the availability windows write them. */
const WEEKDAYS: string[] = Object.values(DayOfWeek);

export interface SeriesRepeat {
  /** How many weeks the pattern runs for. For a plain weekly series this is also the number of sessions. */
  weeks: number;
  /** The weekdays to book, or undefined for just the weekday of the first session. */
  days?: string[];
}

/**
 * Reads the `repeat` a student sent with a direct booking. Only weekly series exist, so
 * `frequency` has to say so rather than being guessed at. Returns the parsed repeat, or the
 * message to send back with a 400.
 */
export function parseRepeat(raw: unknown): { repeat: SeriesRepeat } | { error: string; field: string } {
  if (typeof raw !== "object" || raw === null || Array.isArray(raw)) {
    return { error: "repeat must be an object like { frequency: \"WEEKLY\", count: 4 }", field: "repeat" };
  }
  const { frequency, count, weeks, days } = raw as {
    frequency?: unknown;
    count?: unknown;
    weeks?: unknown;
    days?: unknown;
  };
  if (frequency !== "WEEKLY") {
    return { error: "repeat.frequency must be WEEKLY", field: "repeat.frequency" };
  }

  // Without days it is the original shape: the weekday of the first session, `count` times.
  if (days === undefined) {
    const sessions = count ?? weeks;
    if (typeof sessions !== "number" || !Number.isInteger(sessions) || sessions < SERIES_MIN || sessions > SERIES_MAX) {
      return { error: `repeat.count must be a whole number from ${SERIES_MIN} to ${SERIES_MAX}`, field: "repeat.count" };
    }
    return { repeat: { weeks: sessions } };
  }

  if (
    !Array.isArray(days) ||
    days.length < 1 ||
    days.length > SERIES_MAX_DAYS ||
    days.some((d) => typeof d !== "string" || !WEEKDAYS.includes(d)) ||
    new Set(days).size !== days.length
  ) {
    return { error: `repeat.days must list 1 to ${SERIES_MAX_DAYS} different weekdays, such as MONDAY`, field: "repeat.days" };
  }
  const runFor = weeks ?? count;
  if (typeof runFor !== "number" || !Number.isInteger(runFor) || runFor < 1 || runFor > SERIES_MAX) {
    return { error: `repeat.weeks must be a whole number from 1 to ${SERIES_MAX}`, field: "repeat.weeks" };
  }
  const sessions = days.length * runFor;
  if (sessions < SERIES_MIN) {
    return { error: `A series needs at least ${SERIES_MIN} sessions: choose more days or more weeks`, field: "repeat" };
  }
  if (sessions > SERIES_MAX_SESSIONS) {
    return { error: `A series can have at most ${SERIES_MAX_SESSIONS} sessions: choose fewer days or weeks`, field: "repeat" };
  }
  return { repeat: { weeks: runFor, days: days as string[] } };
}

/**
 * The sessions of a recurring pattern: the same time of day on each of [days], for [weeks] weeks.
 *
 * Weeks are rolling blocks of seven days counted from the first session, not calendar weeks, so a
 * pattern of 3 days over 4 weeks is always exactly 12 sessions and no day is skipped. Each chosen
 * weekday is its next occurrence on or after the first session, repeated every seven days. The
 * result is in date order. [days] should include the weekday of [start]; the first session is
 * always [start] itself, which the caller checks. A duplicate or unsorted day is harmless.
 */
export function sessionWindows(start: Date, end: Date, days: string[], weeks: number): { start: Date; end: Date }[] {
  const from = WEEKDAYS.indexOf(campusParts(start).day);
  const offsets = [...new Set(days.map((d) => (WEEKDAYS.indexOf(d) - from + 7) % 7))].sort((a, b) => a - b);
  const windows: { start: Date; end: Date }[] = [];
  for (let week = 0; week < weeks; week++) {
    for (const offset of offsets) {
      const shift = (week * 7 + offset) * DAY_MS;
      windows.push({ start: new Date(start.getTime() + shift), end: new Date(end.getTime() + shift) });
    }
  }
  return windows;
}

/** The weekday name and minutes since midnight of an instant, in campus time. */
export function campusParts(at: Date): { day: string; minutes: number; date: string } {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: CAMPUS_TIME_ZONE,
    weekday: "long",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(at);
  const get = (type: string) => parts.find((p) => p.type === type)!.value;
  return {
    day: get("weekday").toUpperCase(),
    minutes: Number(get("hour")) * 60 + Number(get("minute")),
    date: `${get("year")}-${get("month")}-${get("day")}`,
  };
}

const minutesOf = (hhmm: string) => {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
};

/** Whether a session sits wholly inside one of the tutor's weekly windows, and ends on the day it starts. */
export function fitsAvailability(slots: IAvailabilitySlot[], start: Date, end: Date): boolean {
  const from = campusParts(start);
  const to = campusParts(end);
  if (from.date !== to.date) return false;
  return slots.some(
    (slot) => slot.day === from.day && minutesOf(slot.startTime) <= from.minutes && to.minutes <= minutesOf(slot.endTime)
  );
}
