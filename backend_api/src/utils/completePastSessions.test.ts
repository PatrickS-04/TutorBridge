// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import { completableFilter } from "./completePastSessions";
import { BookingStatus } from "../models";

/**
 * This filter decides what lands on a tutor's claim, so widening it costs
 * real money: sweeping REQUESTED pays for a session nobody ever confirmed,
 * and matching startTime closes one that is still being taught.
 */
test("only confirmed sessions that have already ended are swept", () => {
  const now = new Date("2026-09-20T12:00:00.000Z");
  const filter = completableFilter(now);

  assert.equal(filter.status, BookingStatus.CONFIRMED);
  assert.deepEqual(filter.endTime, { $lte: now });
});
