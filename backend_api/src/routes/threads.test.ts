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
import { User, UserRole, Thread, Message, Report, NotificationEvent, NotificationToken, UserSettings } from "../models";

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

test("nobody can send in a blocked conversation, but a retry still gets its stored message", async () => {
  const thread = stubThread();
  thread.blockedBy = [other];
  Message.findOne = (() => Promise.resolve(null)) as any;

  const refused = await call("POST", `/threads/${id}/messages`, { body: "hi", clientMessageId: "c2" });
  assert.equal(refused.status, 403);
  assert.equal(refused.body.error.code, "BLOCKED");
  assert.equal(thread.saves, 0);

  Message.findOne = (() => Promise.resolve({ _id: "stored", body: "before the block" })) as any;
  const retried = await call("POST", `/threads/${id}/messages`, { body: "before the block", clientMessageId: "c1" });
  assert.equal(retried.status, 201);
  assert.equal(retried.body._id, "stored");
});

test("blocking and unblocking report my own state, and only touch my own block", async () => {
  const updates: any[] = [];
  let blockedBy: unknown[] = [me];
  Thread.findOneAndUpdate = ((filter: any, update: any) => {
    updates.push({ filter, update });
    return Promise.resolve({ blockedBy });
  }) as any;

  const blocked = await call("POST", `/threads/${id}/block`);
  assert.equal(blocked.status, 200);
  assert.deepEqual(blocked.body, { blocked: true, blockedByMe: true });
  assert.equal(String(updates[0].filter.participants), String(me));
  assert.equal(String(updates[0].update.$addToSet.blockedBy), String(me));

  blockedBy = [other];
  const unblocked = await call("DELETE", `/threads/${id}/block`);
  assert.deepEqual(unblocked.body, { blocked: true, blockedByMe: false });
  assert.equal(String(updates[1].update.$pull.blockedBy), String(me));
});

test("someone outside the conversation cannot block or report it", async () => {
  Thread.findOneAndUpdate = (() => Promise.resolve(null)) as any;
  Thread.findOne = (() => Promise.resolve(null)) as any;
  assert.equal((await call("POST", `/threads/${id}/block`)).status, 404);
  assert.equal((await call("POST", `/threads/${id}/report`, { reason: "SPAM" })).status, 404);
});

test("a report names the other person and keeps a copy of the latest messages", async () => {
  stubThread();
  const sentAt = new Date("2026-11-05T10:00:00Z");
  Message.find = (() => ({
    sort: () => ({ limit: async () => [{ senderId: other, body: "Give me your address", sentAt, extra: "dropped" }] }),
  })) as any;
  let stored: any;
  Report.create = (async (doc: any) => {
    stored = doc;
    return { _id: "r1", status: "OPEN" };
  }) as any;

  const { status, body } = await call("POST", `/threads/${id}/report`, { reason: "UNSAFE", details: "  Felt unsafe  " });

  assert.equal(status, 201);
  assert.deepEqual(body, { id: "r1", status: "OPEN" });
  assert.equal(String(stored.reporterId), String(me));
  assert.equal(String(stored.reportedUserId), String(other));
  assert.equal(stored.details, "Felt unsafe");
  assert.deepEqual(stored.messages, [{ senderId: other, body: "Give me your address", sentAt }]);
});

test("a report needs a known reason and reasonable details", async () => {
  stubThread();
  assert.equal((await call("POST", `/threads/${id}/report`, { reason: "NOPE" })).status, 400);
  assert.equal((await call("POST", `/threads/${id}/report`, { reason: "SPAM", details: "x".repeat(1001) })).status, 400);
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
