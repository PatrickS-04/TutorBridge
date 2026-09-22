// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test, after } from "node:test";
import assert from "node:assert/strict";
import { AddressInfo } from "node:net";
import express from "express";
import admin from "firebase-admin";
import bcrypt from "bcryptjs";
import mongoose from "mongoose";
import { routeMounts } from ".";
import { errorHandler } from "../middleware/errors";
import { User, UserRole } from "../models";

/**
 * A tutor account's life: an admin provisions it, the tutor swaps the
 * temporary password for their own, and nobody but an admin gets near the
 * admin routes. The real routers run behind a real server; only Firebase and
 * the database are stubbed, so no test here needs either.
 *
 * Each test file runs in its own process, so the stubs cannot leak.
 */

const institutionId = new mongoose.Types.ObjectId();

/** The profile the bearer token resolves to - authenticate() looks it up by uid. */
function signedInAs(role: UserRole) {
  const caller = { _id: new mongoose.Types.ObjectId(), institutionId, role };
  admin.auth().verifyIdToken = (async () => ({ uid: `uid-${role}` })) as any;
  User.findOne = ((filter: any) => Promise.resolve(filter.firebaseUid ? caller : null)) as any;
  return caller;
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

const adminOnly: [string, string][] = [
  ["PUT", "/institutions/me"],
  ["GET", "/institutions/me/tutors"],
  ["POST", "/institutions/me/tutors"],
  ["DELETE", "/institutions/me/tutors/66f1a2b3c4d5e6f708192a3b"],
  ["GET", "/institutions/me/verification-queue"],
  ["PUT", "/institutions/verification/66f1a2b3c4d5e6f708192a3b"],
];

for (const role of [UserRole.TUTOR, UserRole.GUARDIAN_LEARNER]) {
  test(`a ${role} token is turned away from every admin route`, async () => {
    signedInAs(role);
    for (const [method, path] of adminOnly) {
      const { status, body } = await call(method, path);
      assert.equal(status, 403, `${method} ${path}`);
      assert.equal(body?.error?.code, "FORBIDDEN", `${method} ${path}`);
    }
  });
}

test("an admin token gets past the role check", async () => {
  // Proves the stubs above are what let the 403s through, not a broken
  // sign-in: a malformed id fails at the query, after the role check.
  signedInAs(UserRole.INSTITUTION_ADMIN);
  const { status } = await call("DELETE", "/institutions/me/tutors/not-an-id");
  assert.equal(status, 404);
});

/** Provisioning with Firebase and the save stubbed; returns what each was handed. */
function provisioning(save: () => Promise<unknown>) {
  const seen: { firebase?: any; saved?: any; deletedUid?: string } = {};
  signedInAs(UserRole.INSTITUTION_ADMIN);
  User.exists = (() => Promise.resolve(null)) as any;
  admin.auth().createUser = (async (props: any) => {
    seen.firebase = props;
    return { uid: "new-tutor-uid" };
  }) as any;
  admin.auth().deleteUser = (async (uid: string) => {
    seen.deletedUid = uid;
  }) as any;
  User.prototype.save = async function (this: any) {
    seen.saved = this;
    return save();
  } as any;
  return seen;
}

test("a provisioned tutor can sign in with the password the admin is shown", async () => {
  // The bug this pins: the password used to go into a bcrypt hash and nowhere
  // the app could sign in against.
  const seen = provisioning(async () => {});
  const { status, body } = await call("POST", "/institutions/me/tutors", {
    fullName: "Naledi Dlamini",
    email: " Naledi@Uni.ac.za ",
  });

  assert.equal(status, 201);
  assert.equal(seen.firebase.password, body.temporaryPassword);
  assert.equal(seen.firebase.email, "naledi@uni.ac.za");
  assert.equal(seen.saved.firebaseUid, "new-tutor-uid");
  assert.equal(seen.saved.mustChangePassword, true);
});

test("a profile that fails to save takes its Firebase login with it", async () => {
  const seen = provisioning(() => Promise.reject(new Error("write failed")));
  const original = console.error;
  console.error = () => {}; // the 500 branch logs; expected here
  const { status } = await call("POST", "/institutions/me/tutors", { fullName: "Naledi Dlamini", email: "n@uni.ac.za" });
  console.error = original;

  assert.equal(status, 500);
  assert.equal(seen.deletedUid, "new-tutor-uid");
});

test("the temporary password can only be swapped for a new one, once", async () => {
  const caller = signedInAs(UserRole.TUTOR);
  const profile: any = {
    ...caller,
    mustChangePassword: true,
    passwordHash: await bcrypt.hash("Temp12345", 4),
    save: async () => {},
  };
  User.findById = (() => ({ select: () => Promise.resolve(profile) })) as any;
  let firebasePassword: string | undefined;
  admin.auth().updateUser = (async (_uid: string, props: any) => {
    firebasePassword = props.password;
  }) as any;
  const change = (newPassword: unknown) => call("POST", "/auth/change-password", { newPassword });

  assert.equal((await change("short")).status, 400);
  assert.equal((await change("Temp12345")).status, 400, "the temporary password again is no change");
  assert.equal(firebasePassword, undefined);

  assert.equal((await change("MyOwnPass1")).status, 204);
  assert.equal(firebasePassword, "MyOwnPass1");
  assert.equal(profile.mustChangePassword, false);
  assert.equal(profile.passwordHash, undefined);

  // Flag cleared: from here a bearer token alone must not change the password.
  assert.equal((await change("Another1")).status, 409);
});
