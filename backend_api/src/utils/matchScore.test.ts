// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import { scoreTutor, signalsFromRequests } from "./matchScore";

const signals = signalsFromRequests([
  { subjectId: "maths", areaId: "bellville", status: "OPEN" },
  { subjectId: "english", areaId: "sandton", status: "CLOSED" },
]);

test("a tutor for a live subject in the same area scores subject + area", () => {
  assert.equal(scoreTutor({ subjectIds: ["maths"], areaId: "bellville", completedSessions: 0 }, signals), 80);
});

test("a past subject counts for less than a live one", () => {
  const past = scoreTutor({ subjectIds: ["english"], completedSessions: 0 }, signals);
  const live = scoreTutor({ subjectIds: ["maths"], completedSessions: 0 }, signals);
  assert.equal(past, 40);
  assert.ok(live > past);
});

test("completed sessions add up to a cap", () => {
  const few = scoreTutor({ subjectIds: ["maths"], completedSessions: 3 }, signals);
  const many = scoreTutor({ subjectIds: ["maths"], completedSessions: 500 }, signals);
  assert.equal(few, 55 + 6);
  assert.equal(many, 55 + 20);
});

test("a perfect fit never goes over 100", () => {
  assert.equal(scoreTutor({ subjectIds: ["maths"], areaId: "bellville", completedSessions: 500 }, signals), 100);
});

test("experience alone does not make a tutor a match", () => {
  assert.equal(scoreTutor({ subjectIds: ["physics"], areaId: "durban", completedSessions: 500 }, signals), 0);
});

test("a student with no requests matches nobody", () => {
  const none = signalsFromRequests([]);
  assert.equal(scoreTutor({ subjectIds: ["maths"], areaId: "bellville", completedSessions: 9 }, none), 0);
});

test("a shared area alone is a partial match", () => {
  assert.equal(scoreTutor({ subjectIds: ["physics"], areaId: "sandton", completedSessions: 0 }, signals), 25);
});
