// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { languageOf, translateError, translatedErrors, translateErrors } from "./errorMessages";

test("only Afrikaans and isiZulu are translated; anything else stays English", () => {
  assert.equal(languageOf("af"), "af");
  assert.equal(languageOf("zu-ZA"), "zu");
  assert.equal(languageOf("en"), null);
  assert.equal(languageOf(undefined), null);
});

test("a listed message is translated, and anything else gets a sentence for its code", () => {
  assert.equal(translateError("af", "INVALID_STATE", "This request has already ended"), "Hierdie versoek is reeds beëindig");
  assert.equal(translateError("zu", "NOT_FOUND", "Booking not found"), "Leyo nto ayitholakalanga. Kungenzeka isusiwe.");
  assert.match(translateError("af", "VALIDATION_ERROR", "items must be an array"), /^Dit kon nie/);
});

test("every translated message is one the routes really send, so a reworded error cannot lose its translation", () => {
  const routes = join(__dirname, "../routes");
  const source = readdirSync(routes)
    .filter((file) => file.endsWith(".ts") && !file.endsWith(".test.ts"))
    .map((file) => readFileSync(join(routes, file), "utf8"))
    .join("\n");
  for (const message of Object.keys(translatedErrors)) {
    assert.ok(source.includes(message), `no route sends: ${message}`);
  }
});

function respond(language: string | undefined, body: unknown) {
  let sent: unknown;
  const res: any = { json: (value: unknown) => { sent = value; return res; } };
  translateErrors({ headers: { "accept-language": language } } as any, res, () => {});
  res.json(body);
  return sent;
}

test("the middleware changes only the words of an error, and only for a translated language", () => {
  const error = { error: { code: "NOT_FOUND", message: "Booking not found", field: "id" } };
  assert.deepEqual(respond("en", error), error);
  assert.deepEqual(respond("zu", error), {
    error: { code: "NOT_FOUND", message: "Leyo nto ayitholakalanga. Kungenzeka isusiwe.", field: "id" },
  });
  assert.deepEqual(respond("zu", { id: 1, message: "not an error" }), { id: 1, message: "not an error" });
});
