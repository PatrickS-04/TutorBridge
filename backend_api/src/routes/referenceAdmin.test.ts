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
import { User, UserRole, Subject, TuitionRequest } from "../models";

/**
 * Subjects, provinces and areas are shared by every institution, so only a
 * platform admin may change them - an institution admin editing one would be
 * editing it for institutions that are not theirs. Firebase and the database
 * are stubbed, as in tutorAccounts.test.ts.
 */

const institutionId = new mongoose.Types.ObjectId();

function signedInAs(role: UserRole) {
  const caller = { _id: new mongoose.Types.ObjectId(), institutionId, role };
  admin.auth().verifyIdToken = (async () => ({ uid: `uid-${role}` })) as any;
  User.findOne = ((filter: any) => Promise.resolve(filter.firebaseUid ? caller : null)) as any;
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
const writes: [string, string][] = ["subjects", "provinces", "areas"].flatMap((resource) => [
  ["POST", `/admin/${resource}`],
  ["PUT", `/admin/${resource}/${id}`],
  ["DELETE", `/admin/${resource}/${id}`],
]);

for (const role of [UserRole.INSTITUTION_ADMIN, UserRole.TUTOR, UserRole.GUARDIAN_LEARNER]) {
  test(`a ${role} cannot change the shared reference lists`, async () => {
    signedInAs(role);
    for (const [method, path] of writes) {
      const { status, body } = await call(method, path, { name: "x" });
      assert.equal(status, 403, `${method} ${path}`);
      assert.equal(body?.error?.code, "FORBIDDEN", `${method} ${path}`);
    }
  });
}

test("a platform admin gets past the role check", async () => {
  signedInAs(UserRole.PLATFORM_ADMIN);
  Subject.findByIdAndUpdate = (() => Promise.resolve(null)) as any;
  // Past the role check the stubbed lookup finds nothing: 404, not 403.
  const { status } = await call("PUT", `/admin/subjects/${id}`, { name: "x" });
  assert.equal(status, 404);
});

test("any signed-in user can still read the lists", async () => {
  signedInAs(UserRole.GUARDIAN_LEARNER);
  Subject.findById = (() => Promise.resolve(null)) as any;
  const { status } = await call("GET", `/admin/subjects/${id}`);
  assert.equal(status, 404); // reached the handler; a 403 would mean reads were locked too
});

test("a subject that is still in use is not deleted", async () => {
  signedInAs(UserRole.PLATFORM_ADMIN);
  let deleted = false;
  Subject.findByIdAndDelete = (() => {
    deleted = true;
    return Promise.resolve(null);
  }) as any;
  User.exists = (() => Promise.resolve(null)) as any;
  TuitionRequest.exists = (() => Promise.resolve({ _id: id })) as any;

  const { status, body } = await call("DELETE", `/admin/subjects/${id}`);
  assert.equal(status, 409);
  assert.equal(body?.error?.code, "IN_USE");
  assert.equal(deleted, false);
});

test("an unused subject is deleted", async () => {
  signedInAs(UserRole.PLATFORM_ADMIN);
  Subject.findByIdAndDelete = (() => Promise.resolve(null)) as any;
  User.exists = (() => Promise.resolve(null)) as any;
  TuitionRequest.exists = (() => Promise.resolve(null)) as any;

  const { status } = await call("DELETE", `/admin/subjects/${id}`);
  assert.equal(status, 204);
});
