import { IAvailabilitySlot } from "../models";

/** A weekly series is 2 to 8 sessions; 4 is "a month". */
export const SERIES_MIN = 2;
export const SERIES_MAX = 8;

/** Availability windows are written in campus time, which is South African time and has no daylight saving. */
export const CAMPUS_TIME_ZONE = "Africa/Johannesburg";

const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

export interface SeriesRepeat {
  count: number;
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
  const { frequency, count } = raw as { frequency?: unknown; count?: unknown };
  if (frequency !== "WEEKLY") {
    return { error: "repeat.frequency must be WEEKLY", field: "repeat.frequency" };
  }
  if (typeof count !== "number" || !Number.isInteger(count) || count < SERIES_MIN || count > SERIES_MAX) {
    return { error: `repeat.count must be a whole number from ${SERIES_MIN} to ${SERIES_MAX}`, field: "repeat.count" };
  }
  return { repeat: { count } };
}

/** The windows of a weekly series: the first session, then the same time each week after it. */
export function seriesWindows(start: Date, end: Date, count: number): { start: Date; end: Date }[] {
  return Array.from({ length: count }, (_, i) => ({
    start: new Date(start.getTime() + i * WEEK_MS),
    end: new Date(end.getTime() + i * WEEK_MS),
  }));
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
