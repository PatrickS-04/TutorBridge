// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import { Locale, UserSettings, User } from "../models";
import { english, translations, render, NotificationKey } from "./messages";
import { localeOf } from "./notify";

const keys = Object.keys(english) as NotificationKey[];
const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();

test("English text fills its values in", () => {
  const { title, body } = render("booking.confirmed", Locale.EN, { subject: "Maths", when: "2026-10-22 14:00" });
  assert.equal(title, "Session confirmed");
  assert.equal(body, "Maths is confirmed for 2026-10-22 14:00.");
});

test("a value left out reads as nothing rather than as the word undefined", () => {
  assert.equal(render("booking.cancelledWithReason", Locale.EN, { subject: "Maths" }).body, "Maths: ");
});

test("a language with no entry for a notification reads it in English", () => {
  const saved = translations[Locale.ZU];
  translations[Locale.ZU] = {};
  try {
    assert.deepEqual(render("application.accepted", Locale.ZU), render("application.accepted", Locale.EN));
  } finally {
    translations[Locale.ZU] = saved;
  }
});

test("Afrikaans and isiZulu cover every notification", () => {
  for (const locale of [Locale.AF, Locale.ZU]) {
    assert.deepEqual(Object.keys(translations[locale] ?? {}).sort(), keys.slice().sort(), locale);
  }
});

test("a translated entry is used, with the same values filled in", () => {
  translations[Locale.AF] = { "booking.cancelled": { title: "T", body: "{subject}!" } };
  try {
    assert.deepEqual(render("booking.cancelled", Locale.AF, { subject: "Maths" }), { title: "T", body: "Maths!" });
  } finally {
    delete translations[Locale.AF];
  }
});

test("every translation asks for the same values as the English it replaces", () => {
  for (const [locale, catalogue] of Object.entries(translations)) {
    for (const [key, entry] of Object.entries(catalogue ?? {})) {
      const base = english[key as NotificationKey];
      assert.deepEqual(placeholders(entry!.title), placeholders(base.title), `${locale} ${key} title`);
      assert.deepEqual(placeholders(entry!.body), placeholders(base.body), `${locale} ${key} body`);
    }
  }
});

test("every English notification has a title and a body", () => {
  for (const key of keys) {
    assert.ok(english[key].title.length > 0 && english[key].body.length > 0, key);
  }
});

function stubLookups(settings: any, user: any) {
  UserSettings.findOne = (() => ({ select: () => ({ lean: async () => settings }) })) as any;
  User.findById = (() => ({ select: () => ({ lean: async () => user }) })) as any;
}

test("the language chosen in Settings wins", async () => {
  stubLookups({ language: Locale.ZU }, { locale: Locale.AF });
  assert.equal(await localeOf("u1"), Locale.ZU);
});

test("with nothing chosen the account locale is used, then English", async () => {
  stubLookups(null, { locale: Locale.AF });
  assert.equal(await localeOf("u1"), Locale.AF);
  stubLookups(null, null);
  assert.equal(await localeOf("u1"), Locale.EN);
});

test("a failed lookup falls back to English instead of losing the notification", async () => {
  UserSettings.findOne = (() => { throw new Error("db down"); }) as any;
  const logged = console.error;
  console.error = () => {};
  try {
    assert.equal(await localeOf("u1"), Locale.EN);
  } finally {
    console.error = logged;
  }
});

test("the languages the server stores are the ones the app can send", () => {
  // android_app .../data/model/Language.kt: serverCode EN, ZU, AF. Keep the two in step.
  assert.deepEqual(Object.values(Locale).sort(), ["AF", "EN", "ZU"]);
});
