import { Request, Response, NextFunction } from "express";

/**
 * Error messages in the language of the app that asked. The routes keep writing
 * apiError(code, "English"), and this swaps the words on the way out, so a new route
 * cannot forget to translate. The app says which language it is in with Accept-Language.
 *
 * Only messages a person using the app can run into are listed. Anything else (a missing
 * field a working app always sends, a model's own validation text) gets a general
 * sentence for its code rather than English in the middle of a translated screen.
 */
type Language = "af" | "zu";

export const translatedErrors: Record<string, Record<Language, string>> = {
  "This booking can no longer be cancelled": {
    af: "Hierdie bespreking kan nie meer gekanselleer word nie",
    zu: "Lokhu kubhukha akusakwazi ukukhanselwa",
  },
  "Only a requested booking can be confirmed": {
    af: "Slegs 'n aangevraagde bespreking kan bevestig word",
    zu: "Ukubhukha okuceliwe kuphela okungaqinisekiswa",
  },
  "Only an upcoming session can be rescheduled": {
    af: "Slegs 'n komende sessie kan herskeduleer word",
    zu: "Iseshini ezayo kuphela engahlelwa kabusha",
  },
  "Only a confirmed session can be checked in to": {
    af: "Jy kan slegs vir 'n bevestigde sessie aanmeld",
    zu: "Ungabhalisa ukuba khona kuphela eseshini eqinisekisiwe",
  },
  "This series has no upcoming sessions left to cancel": {
    af: "Hierdie reeks het geen komende sessies oor om te kanselleer nie",
    zu: "Lolu chungechunge alusenazo izeshini ezizayo zokukhansela",
  },
  "This series has no sessions waiting to be confirmed": {
    af: "Hierdie reeks het geen sessies wat op bevestiging wag nie",
    zu: "Lolu chungechunge alunazo izeshini ezilinde ukuqinisekiswa",
  },
  "This request has already ended": {
    af: "Hierdie versoek is reeds beëindig",
    zu: "Lesi sicelo sesiphelile",
  },
  "Only an open request can be edited": {
    af: "Slegs 'n oop versoek kan gewysig word",
    zu: "Isicelo esivulekile kuphela esingahlelwa",
  },
  "endTime must be a valid time after startTime": {
    af: "Die eindtyd moet ná die begintyd wees",
    zu: "Isikhathi sokuphela kufanele sibe ngemva kwesikhathi sokuqala",
  },
  "A session can only be moved to a future time": {
    af: "'n Sessie kan slegs na 'n toekomstige tyd geskuif word",
    zu: "Iseshini ingahanjiswa kuphela esikhathini esizayo",
  },
  "A session must be booked for a future time": {
    af: "'n Sessie moet vir 'n toekomstige tyd bespreek word",
    zu: "Iseshini kufanele ibhukhelwe isikhathi esizayo",
  },
  "repeat.days must include the weekday of the first session": {
    af: "Die herhaaldae moet die weekdag van die eerste sessie insluit",
    zu: "Izinsuku zokuphinda kufanele zifake usuku lweseshini yokuqala",
  },
  "A note is too long to store": {
    af: "'n Nota is te lank om te stoor",
    zu: "Inothi lide kakhulu ukuthi lingagcinwa",
  },
  "newPassword must be at least 6 characters": {
    af: "Die nuwe wagwoord moet ten minste 6 karakters hê",
    zu: "Iphasiwedi entsha kufanele ibe nezinhlamvu okungenani eziyi-6",
  },
  "Choose a password other than the temporary one": {
    af: "Kies 'n ander wagwoord as die tydelike een",
    zu: "Khetha iphasiwedi ehlukile kweyesikhashana",
  },
  "Nothing to update": {
    af: "Niks om op te dateer nie",
    zu: "Akukho okuzobuyekezwa",
  },
  "Only the student who posted the request can schedule its session": {
    af: "Slegs die student wat die versoek geplaas het, kan die sessie skeduleer",
    zu: "Umfundi othumele isicelo kuphela ongahlela iseshini yaso",
  },
  "Only the request owner can decide on applications": {
    af: "Slegs die eienaar van die versoek kan oor aansoeke besluit",
    zu: "Umnikazi wesicelo kuphela ongakhetha ngezicelo",
  },
};

const generalByCode: Record<string, Record<Language, string>> = {
  NOT_FOUND: {
    af: "Daardie item kon nie gevind word nie. Dit is dalk verwyder.",
    zu: "Leyo nto ayitholakalanga. Kungenzeka isusiwe.",
  },
  FORBIDDEN: {
    af: "Jy mag dit nie doen nie",
    zu: "Awuvunyelwe ukwenza lokho",
  },
};

const generalFallback: Record<Language, string> = {
  af: "Dit kon nie gedoen word nie. Gaan asseblief die besonderhede na en probeer weer.",
  zu: "Lokho akwenzekanga. Sicela uhlole imininingwane bese uzama futhi.",
};

/** "af", "af-ZA" and "zu" are ours; a missing header or anything else means English. */
export function languageOf(header: string | undefined): Language | null {
  const tag = (header ?? "").slice(0, 2).toLowerCase();
  return tag === "af" || tag === "zu" ? tag : null;
}

export function translateError(language: Language, code: string | undefined, message: string): string {
  return translatedErrors[message]?.[language] ?? generalByCode[code ?? ""]?.[language] ?? generalFallback[language];
}

export function translateErrors(req: Request, res: Response, next: NextFunction) {
  const language = languageOf(req.headers["accept-language"]);
  if (language) {
    const send = res.json.bind(res);
    res.json = (body: any) =>
      send(
        typeof body?.error?.message === "string"
          ? { ...body, error: { ...body.error, message: translateError(language, body.error.code, body.error.message) } }
          : body
      );
  }
  next();
}
