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
import { User, UserRole, Thread, Message, NotificationEvent, NotificationToken, UserSettings } from "../models";

/**
 * The chat the app's Messages screens sit on: sending stores the message and
 * tells the other person, opening a conversation clears what was waiting, and
 * nobody outside it can read or write. Firebase and the database are stubbed,
 * as in tutorAccounts.test.ts.
 */

const institutionId = new mongoose.Types.ObjectId();
const me = new mongoose.Types.ObjectId();
const other = new mongoose.Types.ObjectId();

admin.auth().verifyIdToken = (async () => ({ uid: "uid-me" })) as any;
User.findOne = ((filter: any) =>
  Promise.resolve(filter.firebaseUid ? { _id: me, institutionId, role: UserRole.GUARDIAN_LEARNER } : null)) as any;

// notify() runs after the response and writes through these; keep it off the database.
const notifications: any[] = [];
NotificationEvent.create = (async (event: any) => {
  notifications.push(event);
  return { ...event, _id: new mongoose.Types.ObjectId() };
}) as any;
NotificationToken.find = (() => ({ select: () => ({ lean: async () => [] }) })) as any;
// The recipient's language is looked up before a notification is written: none chosen, no account locale.
UserSettings.findOne = (() => ({ select: () => ({ lean: async () => null }) })) as any;
User.findById = (() => ({ select: () => ({ lean: async () => null }) })) as any;

/** A thread between me and the other person that remembers having been saved. */
function stubThread(unread: Record<string, number> = {}) {
  const thread: any = {
    _id: new mongoose.Types.ObjectId(),
    participants: [me, other],
    unreadCount: new Map(Object.entries(unread)),
    saves: 0,
    async save() {
      this.saves++;
    },
  };
  // Membership is part of the query, so a thread I am not in is simply not found.
  Thread.findOne = ((filter: any) =>
    Promise.resolve(String(filter.participants) === String(me) ? thread : null)) as any;
  return thread;
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

test("sending stores the message, tells the other person and counts it as unread for them", async () => {
  const thread = stubThread();
  Message.findOne = (() => Promise.resolve(null)) as any;
  Message.create = (async (doc: any) => ({ ...doc, _id: new mongoose.Types.ObjectId() })) as any;

  const { status, body } = await call("POST", `/threads/${id}/messages`, {
    body: "Can we move to 3pm?",
    clientMessageId: "c1",
  });

  assert.equal(status, 201);
  assert.equal(body.body, "Can we move to 3pm?");
  // The whole message comes back with `_id`, whether new or a retry's stored one.
  assert.ok(body._id);
  assert.equal(thread.unreadCount.get(String(other)), 1);
  assert.equal(thread.unreadCount.get(String(me)), undefined);
  assert.equal(thread.lastMessagePreview, "Can we move to 3pm?");

  await new Promise((resolve) => setTimeout(resolve, 20)); // notify() is fire-and-forget
  assert.equal(String(notifications.at(-1)?.userId), String(other));
});

test("a message needs a body and a clientMessageId", async () => {
  stubThread();
  const { status } = await call("POST", `/threads/${id}/messages`, { body: "hi" });
  assert.equal(status, 400);
});

test("someone outside the conversation cannot read or write it", async () => {
  Thread.findOne = (() => Promise.resolve(null)) as any;
  assert.equal((await call("GET", `/threads/${id}/messages`)).status, 404);
  assert.equal((await call("POST", `/threads/${id}/messages`, { body: "x", clientMessageId: "c" })).status, 404);
});

test("opening a conversation clears what was waiting for me", async () => {
  const thread = stubThread({ [String(me)]: 3 });
  let readReceipts: any;
  Message.updateMany = (async (filter: any, update: any) => {
    readReceipts = { filter, update };
  }) as any;
  // Only paginate's query chain is needed; an empty page is enough.
  Message.find = (() => {
    const chain: any = { sort: () => chain, skip: () => chain, limit: () => chain, populate: () => chain, select: () => chain, exec: async () => [] };
    return chain;
  }) as any;
  Message.countDocuments = (async () => 0) as any;

  const { status } = await call("GET", `/threads/${id}/messages`);

  assert.equal(status, 200);
  assert.equal(thread.unreadCount.get(String(me)), 0);
  assert.equal(String(readReceipts.filter.senderId.$ne), String(me));
  assert.equal(readReceipts.update.$set.status, "READ");
});
