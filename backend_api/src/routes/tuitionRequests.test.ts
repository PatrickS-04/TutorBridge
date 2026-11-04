// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test, after } from "node:test";
import assert from "node:assert/strict";
import { AddressInfo } from "node:net";
import express from "express";
import admin from "firebase-admin";
import mongoose from "mongoose";
import { routeMounts } from ".";
import { errorHandler } from "../middleware/errors";
import { User, UserRole, TuitionRequest, TuitionRequestStatus, Application } from "../models";

/**
 * A student's control over their own request: edit it while it is open, close
 * it or withdraw it while it is open or matched, and never touch anyone
 * else's. Firebase and the database are stubbed, as in tutorAccounts.test.ts.
 */

const institutionId = new mongoose.Types.ObjectId();
const studentId = new mongoose.Types.ObjectId();

function signedInAs(role: UserRole) {
  const caller = { _id: studentId, institutionId, role };
  admin.auth().verifyIdToken = (async () => ({ uid: `uid-${role}` })) as any;
  User.findOne = ((filter: any) => Promise.resolve(filter.firebaseUid ? caller : null)) as any;
}

/** A request document that records what was set on it and whether it saved. */
function stubRequest(status: TuitionRequestStatus) {
  const doc: any = {
    _id: new mongoose.Types.ObjectId(),
    status,
    saved: false,
    set(updates: object) {
      Object.assign(this, updates);
    },
    async save() {
      this.saved = true;
    },
  };
  // Ownership is part of the query, so an unowned id simply is not found.
  TuitionRequest.findOne = ((filter: any) =>
    Promise.resolve(String(filter.studentId) === String(studentId) ? doc : null)) as any;
  Application.find = (() => Promise.resolve([])) as any;
  return doc;
}

const app = express();
app.use(express.json());
for (const [path, router] of Object.entries(routeMounts)) app.use(path, router);
app.use(errorHandler);
const server = app.listen(0);
after(() => server.close());

function call(method: string, path: string, body?: object) {
  const { port } = server.address() as AddressInfo;
  return fetch(`http://127.0.0.1:${port}${path}`, {
    method,
    headers: { authorization: "Bearer stubbed", "content-type": "application/json" },
    body: body && JSON.stringify(body),
  }).then(async (res) => ({ status: res.status, body: await res.json().catch(() => null) }));
}

const id = "66f1a2b3c4d5e6f708192a3b";

test("an open request can be edited", async () => {
  signedInAs(UserRole.GUARDIAN_LEARNER);
  const doc = stubRequest(TuitionRequestStatus.OPEN);
  const { status } = await call("PUT", `/tuition-requests/${id}`, { gradeLevel: "Grade 12", description: "Calculus" });
  assert.equal(status, 200);
  assert.equal(doc.gradeLevel, "Grade 12");
  assert.equal(doc.saved, true);
});

test("fields a student must not change are ignored", async () => {
  signedInAs(UserRole.GUARDIAN_LEARNER);
  const doc = stubRequest(TuitionRequestStatus.OPEN);
  await call("PUT", `/tuition-requests/${id}`, { description: "x", status: "MATCHED", studentId: "someone-else" });
  assert.equal(doc.status, TuitionRequestStatus.OPEN);
  assert.equal(doc.studentId, undefined);
});

test("an edit with nothing editable in it is a 400", async () => {
  signedInAs(UserRole.GUARDIAN_LEARNER);
  stubRequest(TuitionRequestStatus.OPEN);
  const { status } = await call("PUT", `/tuition-requests/${id}`, { status: "CLOSED" });
  assert.equal(status, 400);
});

test("a matched request cannot be edited", async () => {
  signedInAs(UserRole.GUARDIAN_LEARNER);
  const doc = stubRequest(TuitionRequestStatus.MATCHED);
  const { status, body } = await call("PUT", `/tuition-requests/${id}`, { description: "changed" });
  assert.equal(status, 409);
  assert.equal(body?.error?.code, "INVALID_STATE");
  assert.equal(doc.saved, false);
});

test("closing and withdrawing leave different statuses", async () => {
  signedInAs(UserRole.GUARDIAN_LEARNER);
  stubRequest(TuitionRequestStatus.OPEN);
  const closed = await call("PUT", `/tuition-requests/${id}/close`);
  assert.equal(closed.status, 200);
  assert.equal(closed.body.status, TuitionRequestStatus.CLOSED);

  stubRequest(TuitionRequestStatus.MATCHED);
  const withdrawn = await call("PUT", `/tuition-requests/${id}/withdraw`);
  assert.equal(withdrawn.status, 200);
  assert.equal(withdrawn.body.status, TuitionRequestStatus.CANCELLED);
});

test("an ended request cannot be closed again", async () => {
  signedInAs(UserRole.GUARDIAN_LEARNER);
  stubRequest(TuitionRequestStatus.CANCELLED);
  const { status, body } = await call("PUT", `/tuition-requests/${id}/close`);
  assert.equal(status, 409);
  assert.equal(body?.error?.code, "INVALID_STATE");
});

test("someone else's request is not found", async () => {
  signedInAs(UserRole.GUARDIAN_LEARNER);
  stubRequest(TuitionRequestStatus.OPEN);
  TuitionRequest.findOne = (() => Promise.resolve(null)) as any;
  for (const [method, path] of [
    ["PUT", `/tuition-requests/${id}`],
    ["PUT", `/tuition-requests/${id}/close`],
    ["PUT", `/tuition-requests/${id}/withdraw`],
  ]) {
    const { status } = await call(method, path, { description: "x" });
    assert.equal(status, 404, `${method} ${path}`);
  }
});
