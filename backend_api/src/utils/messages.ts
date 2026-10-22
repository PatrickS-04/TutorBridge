import { Locale } from "../models";

/**
 * Every notification the server sends, by key. A notification is stored and pushed
 * in its recipient's language, which is not the language of whoever caused it - a
 * tutor accepting a booking writes to the student - so the sender passes a key and
 * values, never finished English.
 *
 * English is the only language that has to be complete. Any other locale may fall
 * short of it, and a missing entry reads in English rather than not at all.
 */
export type NotificationKey =
  | "application.accepted"
  | "application.declined"
  | "application.requestChanged"
  | "application.requestClosed"
  | "application.new"
  | "booking.scheduled"
  | "booking.scheduledAtVenue"
  | "booking.new"
  | "booking.confirmed"
  | "booking.cancelled"
  | "booking.cancelledWithReason"
  | "booking.rescheduled"
  | "booking.rescheduleRequested"
  | "message.new";

export interface NotificationText {
  title: string;
  body: string;
}

export type NotificationValues = Record<string, string>;

type Catalogue = Record<NotificationKey, NotificationText>;

export const english: Catalogue = {
  "application.accepted": {
    title: "Your application was accepted",
    body: "The student accepted your offer - agree a time to lock in the booking.",
  },
  "application.declined": {
    title: "Your application was declined",
    body: "The student went with another tutor this time.",
  },
  "application.requestChanged": {
    title: "A request you applied to changed",
    body: "The student edited their tuition request - check it still suits you.",
  },
  "application.requestClosed": {
    title: "A request you applied to is closed",
    body: "The student is no longer looking for a tutor for this request.",
  },
  "application.new": {
    title: "New tutor application",
    body: "{tutor} offered to take your tuition request.",
  },
  "booking.scheduled": {
    title: "Session scheduled",
    body: "{subject} is booked for {when}.",
  },
  //The venue is in the body because this flow confirms outright: the tutor
  //never taps accept, so this is the first they hear of where they are going.
  "booking.scheduledAtVenue": {
    title: "Session scheduled",
    body: "{subject} is booked for {when} at {venue}.",
  },
  "booking.new": {
    title: "New student booking",
    body: "{subject} on {when} - confirm or decline it.",
  },
  "booking.confirmed": {
    title: "Session confirmed",
    body: "{subject} is confirmed for {when}.",
  },
  "booking.cancelled": {
    title: "Session cancelled",
    body: "{subject} was cancelled.",
  },
  "booking.cancelledWithReason": {
    title: "Session cancelled",
    body: "{subject}: {reason}",
  },
  "booking.rescheduled": {
    title: "Session rescheduled",
    body: "{subject} was moved to {when}.",
  },
  "booking.rescheduleRequested": {
    title: "Reschedule requested",
    body: "{subject} was moved to {when} - confirm the new time.",
  },
  "message.new": {
    title: "New message",
    body: "{text}",
  },
};

/** Per-language catalogues. Filled in as each language is translated and reviewed. */
export const translations: Partial<Record<Locale, Partial<Catalogue>>> = {};

/** The text for one notification, in `locale` where it exists and in English where it does not. */
export function render(key: NotificationKey, locale: Locale, values: NotificationValues = {}): NotificationText {
  const entry = translations[locale]?.[key] ?? english[key];
  return { title: fill(entry.title, values), body: fill(entry.body, values) };
}

function fill(template: string, values: NotificationValues) {
  return template.replace(/\{(\w+)\}/g, (_match, name) => values[name] ?? "");
}
