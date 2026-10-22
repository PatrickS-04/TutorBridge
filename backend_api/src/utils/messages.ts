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

/**
 * Per-language catalogues. These are AI-drafted and have not been reviewed by a native
 * speaker; a reviewer's corrections go straight into the entries below.
 */
export const translations: Partial<Record<Locale, Partial<Catalogue>>> = {
  [Locale.AF]: {
    "application.accepted": {
      title: "Jou aansoek is aanvaar",
      body: "Die student het jou aanbod aanvaar - maak 'n tyd af om die bespreking vas te maak.",
    },
    "application.declined": {
      title: "Jou aansoek is van die hand gewys",
      body: "Die student het hierdie keer by 'n ander tutor uitgekom.",
    },
    "application.requestChanged": {
      title: "'n Versoek waarvoor jy aansoek gedoen het, het verander",
      body: "Die student het hul onderrigversoek gewysig - kyk of dit jou nog pas.",
    },
    "application.requestClosed": {
      title: "'n Versoek waarvoor jy aansoek gedoen het, is gesluit",
      body: "Die student soek nie meer 'n tutor vir hierdie versoek nie.",
    },
    "application.new": {
      title: "Nuwe tutor-aansoek",
      body: "{tutor} het aangebied om jou onderrigversoek te neem.",
    },
    "booking.scheduled": {
      title: "Sessie geskeduleer",
      body: "{subject} is bespreek vir {when}.",
    },
    "booking.scheduledAtVenue": {
      title: "Sessie geskeduleer",
      body: "{subject} is bespreek vir {when} by {venue}.",
    },
    "booking.new": {
      title: "Nuwe studentebespreking",
      body: "{subject} op {when} - bevestig of weier dit.",
    },
    "booking.confirmed": {
      title: "Sessie bevestig",
      body: "{subject} is bevestig vir {when}.",
    },
    "booking.cancelled": {
      title: "Sessie gekanselleer",
      body: "{subject} is gekanselleer.",
    },
    "booking.cancelledWithReason": {
      title: "Sessie gekanselleer",
      body: "{subject}: {reason}",
    },
    "booking.rescheduled": {
      title: "Sessie herskeduleer",
      body: "{subject} is verskuif na {when}.",
    },
    "booking.rescheduleRequested": {
      title: "Herskedulering versoek",
      body: "{subject} is verskuif na {when} - bevestig die nuwe tyd.",
    },
    "message.new": {
      title: "Nuwe boodskap",
      body: "{text}",
    },
  },
  [Locale.ZU]: {
    "application.accepted": {
      title: "Isicelo sakho samukelwe",
      body: "Umfundi wamukele isipho sakho - vumelanani ngesikhathi ukuze kuqiniswe ukubhukha.",
    },
    "application.declined": {
      title: "Isicelo sakho senqatshiwe",
      body: "Umfundi ukhethe omunye uthisha kulokhu.",
    },
    "application.requestChanged": {
      title: "Isicelo owufake ngaso siguquliwe",
      body: "Umfundi uhlele isicelo sakhe sokufundiswa - hlola ukuthi siyakufanela yini.",
    },
    "application.requestClosed": {
      title: "Isicelo owufake ngaso sivaliwe",
      body: "Umfundi akasafuni uthisha walesi sicelo.",
    },
    "application.new": {
      title: "Isicelo esisha sikathisha",
      body: "U-{tutor} unikeze ukuthatha isicelo sakho sokufundiswa.",
    },
    "booking.scheduled": {
      title: "Isifundo sihleliwe",
      body: "I-{subject} ibhukhelwe {when}.",
    },
    "booking.scheduledAtVenue": {
      title: "Isifundo sihleliwe",
      body: "I-{subject} ibhukhelwe {when} e-{venue}.",
    },
    "booking.new": {
      title: "Ukubhukha okusha komfundi",
      body: "I-{subject} ngo-{when} - qinisekisa noma unqabe.",
    },
    "booking.confirmed": {
      title: "Isifundo siqinisekisiwe",
      body: "I-{subject} iqinisekisiwe ngo-{when}.",
    },
    "booking.cancelled": {
      title: "Isifundo sikhanseliwe",
      body: "I-{subject} ikhanseliwe.",
    },
    "booking.cancelledWithReason": {
      title: "Isifundo sikhanseliwe",
      body: "{subject}: {reason}",
    },
    "booking.rescheduled": {
      title: "Isifundo sihlelwe kabusha",
      body: "I-{subject} ifuduselwe ku-{when}.",
    },
    "booking.rescheduleRequested": {
      title: "Kucelwe ukuhlela kabusha",
      body: "I-{subject} ifuduselwe ku-{when} - qinisekisa isikhathi esisha.",
    },
    "message.new": {
      title: "Umlayezo omusha",
      body: "{text}",
    },
  },
};

/** The text for one notification, in `locale` where it exists and in English where it does not. */
export function render(key: NotificationKey, locale: Locale, values: NotificationValues = {}): NotificationText {
  const entry = translations[locale]?.[key] ?? english[key];
  return { title: fill(entry.title, values), body: fill(entry.body, values) };
}

function fill(template: string, values: NotificationValues) {
  return template.replace(/\{(\w+)\}/g, (_match, name) => values[name] ?? "");
}
