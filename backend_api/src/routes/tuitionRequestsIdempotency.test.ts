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
import { User, UserRole, TuitionRequest } from "../models";

/**
 * Posting a tuition request is safe to repeat. The app replays requests made
 * offline, and retries after a lost reply, so the same post can reach the
 * server more than once - it must end up as one request. Firebase and the
 * database are stubbed, as in tutorAccounts.test.ts.
 */

const institutionId = new mongoose.Types.ObjectId();
const student = new mongoose.Types.ObjectId();

admin.auth().verifyIdToken = (async () => ({ uid: "uid-student" })) as any;
User.findOne = ((filter: any) =>
  Promise.resolve(filter.firebaseUid ? { _id: student, institutionId, role: UserRole.GUARDIAN_LEARNER } : null)) as any;

/** A stand-in for the collection: remembers what was created and enforces the unique index. */
function fakeCollection() {
  const stored: any[] = [];
  const state = { creates: 0, raceOnce: false };

  TuitionRequest.findOne = ((filter: any) => {
    // A race loser looks again after losing, and finds the winner.
    const hit = stored.find(
      (doc) => String(doc.studentId) === String(filter.studentId) && doc.clientRequestId === filter.clientRequestId
    );
    return Promise.resolve(hit ?? null);
  }) as any;

  TuitionRequest.create = (async (doc: any) => {
    state.creates++;
    if (state.raceOnce) {
      // Another copy of this post got in first, between the lookup and the insert.
      state.raceOnce = false;
      stored.push({ ...doc, _id: new mongoose.Types.ObjectId(), status: "OPEN", createdAt: new Date() });
      throw Object.assign(new Error("E11000 duplicate key"), { code: 11000 });
    }
    const created = { ...doc, _id: new mongoose.Types.ObjectId(), createdAt: new Date() };
    stored.push(created);
    return created;
  }) as any;

  return { stored, state };
}

const app = express();
app.use(express.json());
for (const [path, router] of Object.entries(routeMounts)) app.use(path, router);
app.use(errorHandler);
const server = app.listen(0);
after(() => server.close());

function post(body: object) {
  const { port } = server.address() as AddressInfo;
  return fetch(`http://127.0.0.1:${port}/tuition-requests`, {
    method: "POST",
    headers: { authorization: "Bearer stubbed", "content-type": "application/json" },
    body: JSON.stringify(body),
  }).then(async (res) => ({ status: res.status, body: await res.json().catch(() => null) }));
}

const request = {
  subjectId: "66f1a2b3c4d5e6f708192a3b",
  areaId: "66f1a2b3c4d5e6f708192a3c",
  mode: "IN_PERSON",
  description: "Limits",
};

test("a post with a clientRequestId is created once", async () => {
  const { stored } = fakeCollection();
  const first = await post({ ...request, clientRequestId: "c-1" });
  assert.equal(first.status, 201);
  assert.equal(stored.length, 1);
  assert.equal(stored[0].clientRequestId, "c-1");
});

test("the same post again answers with the request already made", async () => {
  const { stored, state } = fakeCollection();
  const first = await post({ ...request, clientRequestId: "c-1" });
  const replay = await post({ ...request, clientRequestId: "c-1" });

  assert.equal(replay.status, 200);
  assert.equal(replay.body.id, first.body.id);
  assert.equal(stored.length, 1);
  assert.equal(state.creates, 1, "the replay must not even try to insert");
});

test("a different clientRequestId is a different request", async () => {
  const { stored } = fakeCollection();
  await post({ ...request, clientRequestId: "c-1" });
  const second = await post({ ...request, clientRequestId: "c-2" });
  assert.equal(second.status, 201);
  assert.equal(stored.length, 2);
});

test("a post without a clientRequestId behaves as before", async () => {
  const { stored } = fakeCollection();
  assert.equal((await post(request)).status, 201);
  assert.equal((await post(request)).status, 201);
  assert.equal(stored.length, 2);
});

test("two copies arriving together still make one request", async () => {
  const { stored, state } = fakeCollection();
  state.raceOnce = true;
  const { status, body } = await post({ ...request, clientRequestId: "c-race" });

  assert.equal(status, 200, "the loser is answered with the winner");
  assert.equal(stored.length, 1);
  assert.equal(String(body.id), String(stored[0]._id));
});

test("a clientRequestId that is not a short string is a 400", async () => {
  fakeCollection();
  const tooLong = await post({ ...request, clientRequestId: "x".repeat(101) });
  assert.equal(tooLong.status, 400);
  const wrongType = await post({ ...request, clientRequestId: 42 });
  assert.equal(wrongType.status, 400);
  assert.equal(wrongType.body?.error?.field, "clientRequestId");
});
