import { test } from "node:test";
import assert from "node:assert/strict";
import { isCheckInOpen, newCheckInCode,
    hashCheckInCode, checkInCodeMatches,
    checkInQrPayload } from "./attendance";

const start = new Date("2026-11-02T10:00:00Z");
const end = new Date("2026-11-02T11:00:00Z");

test("check-in opens 15 minutes early and closes when the session ends", () => {
    assert.equal(isCheckInOpen(start, end, new Date("2026-11-02T09:44:00Z")), false);
    assert.equal(isCheckInOpen(start, end, new Date("2026-11-02T09:45:00Z")), true);
      assert.equal(isCheckInOpen(start, end, new Date("2026-11-02T10:30:00Z")), true);
      assert.equal(isCheckInOpen(start, end, new Date("2026-11-02T11:00:01Z")), false);
});

test("codes are always six digits", () => {
    for (let i = 0; i < 200; i++)
    {
        assert.match(newCheckInCode(), /^\d{6}$/);
    }
});

test("only the right code for the right booking matches", () => {
    const hash = hashCheckInCode("bookingA", "042917");
      assert.equal(checkInCodeMatches("bookingA", "042917", hash), true);
      assert.equal(checkInCodeMatches("bookingA", "042918", hash), false);
      assert.equal(checkInCodeMatches("bookingB", "042917", hash), false); // same code, other session
      assert.equal(checkInCodeMatches("bookingA", "42917", hash), false);  // not six digits
      assert.equal(checkInCodeMatches("bookingA", "042917", undefined), false); // no code issued
});

test("the QR payload carries the booking and the code", () => {
    assert.equal(checkInQrPayload("abc", "000123"), "tutorbridge://checkin?booking=abc&code=000123");
});