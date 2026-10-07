// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import { csvCell } from "./timesheets";

test("plain values pass through untouched", () => {
  assert.equal(csvCell("Mathematics"), "Mathematics");
  assert.equal(csvCell(450), "450");
});

test("a comma would split the column, so the value is quoted", () => {
  assert.equal(csvCell("Dlamini, Naledi"), '"Dlamini, Naledi"');
});

test("quotes are doubled inside a quoted value", () => {
  assert.equal(csvCell('Maths "Extension"'), '"Maths ""Extension"""');
});

test("a line break stays inside its cell", () => {
  assert.equal(csvCell("a\nb"), '"a\nb"');
});

test("missing values become empty cells", () => {
  assert.equal(csvCell(undefined), "");
  assert.equal(csvCell(null), "");
});
