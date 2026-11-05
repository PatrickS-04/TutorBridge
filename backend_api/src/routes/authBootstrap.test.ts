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
import { User, UserRole } from "../models";

/** The age declaration is asked of new accounts only. Firebase and the database are stubbed. */

const institutionId = new mongoose.Types.ObjectId();

admin.auth().verifyIdToken = (async () => ({ uid: "uid-new", email: "new@example.com", firebase: {} })) as any;

const app = express();
app.use(express.json());
for (const [path, router] of Object.entries(routeMounts)) app.use(path, router);
app.use(errorHandler);
const server = app.listen(0);
after(() => server.close());

function bootstrap(body: object) {
  const { port } = server.address() as AddressInfo;
  return fetch(`http://127.0.0.1:${port}/auth/bootstrap`, {
    method: "POST",
    headers: { authorization: "Bearer stubbed", "content-type": "application/json" },
    body: JSON.stringify(body),
  }).then(async (res) => ({ status: res.status, body: await res.json() }));
}

function stubUsers(existing: object | null = null) {
  const created: any[] = [];
  User.findOne = (() => Promise.resolve(existing)) as any;
  User.create = (async (doc: any) => {
    created.push(doc);
    return { ...doc, _id: new mongoose.Types.ObjectId() };
  }) as any;
  return created;
}

test("a new account is refused until the age declaration is made", async () => {
  const created = stubUsers();
  const { status, body } = await bootstrap({ institutionId: String(institutionId) });
  assert.equal(status, 400);
  assert.equal(body.error.message, "Please confirm your age on the create account screen");
  assert.equal(created.length, 0);
});

test("a new account records when the declaration was made", async () => {
  const created = stubUsers();
  const { status } = await bootstrap({ institutionId: String(institutionId), ageConfirmed: true });
  assert.equal(status, 201);
  assert.ok(created[0].ageConfirmedAt instanceof Date);
});

test("a returning user is not asked again", async () => {
  stubUsers({ _id: new mongoose.Types.ObjectId(), institutionId, role: UserRole.GUARDIAN_LEARNER, fullName: "Old User" });
  const { status } = await bootstrap({});
  assert.equal(status, 200);
});
