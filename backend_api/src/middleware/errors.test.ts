import { test } from "node:test";
import assert from "node:assert/strict";
import mongoose from "mongoose";
import { Request, Response, NextFunction } from "express";
import { errorHandler } from "./errors";
import { Booking, LessonMode } from "../models";

/**
 * Every route looks its document up by whatever id the client sent, so this
 * handler is the only thing standing between a mistyped id and a 500 - and a
 * 500 pages whoever is on call. Nothing here needs a server or a database:
 * the handler is a plain function over an error.
 */

/** Just enough of an Express response to record what the handler decided. */
function recorder() {
  const captured: { status?: number; body?: any } = {};
  const res = {
    status(code: number) {
      captured.status = code;
      return this;
    },
    json(body: any) {
      captured.body = body;
      return this;
    },
  } as unknown as Response;
  return { res, captured };
}

function handle(err: unknown) {
  const { res, captured } = recorder();
  errorHandler(err, {} as Request, res, (() => {}) as NextFunction);
  return captured;
}

/**
 * The 500 branch logs the error, which is the point of it - but a stack trace
 * in a passing test run reads like a failure, so the two tests that go down
 * that branch swallow the log rather than the suite looking broken.
 */
function quietly<T>(run: () => T): T {
  const original = console.error;
  console.error = () => {};
  try {
    return run();
  } finally {
    console.error = original;
  }
}

test("a mistyped id is not found, not a server fault", () => {
  const captured = handle(new mongoose.Error.CastError("ObjectId", "not-an-id", "_id"));

  assert.equal(captured.status, 404);
  assert.deepEqual(captured.body, { error: { code: "NOT_FOUND", message: "Not found", field: null } });
});

test("the error a bad id really throws is the one the handler catches", async () => {
  // Pinning the shape the branch keys off, so a Mongoose upgrade that renames
  // `kind` fails here rather than quietly going back to 500s in production.
  const err: any = await Booking.findOne({ _id: "not-an-id" }).exec().then(
    () => null,
    (thrown) => thrown
  );

  assert.equal(err?.name, "CastError");
  assert.equal(err?.kind, "ObjectId");
  assert.equal(handle(err).status, 404);
});

test("a cast that is not an id is left alone, rather than called not found", () => {
  // "hours must be a number" is a different bug; answering 404 would hide it.
  const captured = quietly(() => handle(new mongoose.Error.CastError("Number", "abc", "hoursPerWeek")));

  assert.equal(captured.status, 500);
});

test("a model rejecting the client's input is still a 400 naming the field", () => {
  // Everything else valid, so the venue is the only thing the error can name.
  const booking = new Booking({
    institutionId: new mongoose.Types.ObjectId(),
    tutorId: new mongoose.Types.ObjectId(),
    studentId: new mongoose.Types.ObjectId(),
    subjectName: "Maths",
    startTime: new Date("2026-10-01T14:00:00.000Z"),
    endTime: new Date("2026-10-01T15:00:00.000Z"),
    mode: LessonMode.IN_PERSON,
    agreedRatePerHour: 250,
    location: "x".repeat(121),
  });
  const captured = handle(booking.validateSync());

  assert.equal(captured.status, 400);
  assert.equal(captured.body.error.code, "VALIDATION_ERROR");
  assert.equal(captured.body.error.field, "location");
});

test("anything else is still a 500 in the conventional body", () => {
  const captured = quietly(() => handle(new Error("kaboom")));

  assert.equal(captured.status, 500);
  assert.deepEqual(captured.body, {
    error: { code: "INTERNAL_ERROR", message: "Something went wrong", field: null },
  });
});
