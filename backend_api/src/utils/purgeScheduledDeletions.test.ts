// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import mongoose from "mongoose";
import "../models";
import { keptCollections, purgedCollections } from "./purgeScheduledDeletions";

/**
 * Adding a collection that points at a User without deciding what happens to
 * it on erasure is how personal data gets left behind. This fails until the
 * new model is either purged or listed as deliberately kept.
 */
test("every collection referencing a User is purged or deliberately kept", () => {
  const referencesUser = (model: mongoose.Model<unknown>) =>
    Object.values(model.schema.paths).some(
      (path: any) => path.options?.ref === "User" || path.caster?.options?.ref === "User"
    );

  const referencing = Object.entries(mongoose.models)
    .filter(([, model]) => referencesUser(model as mongoose.Model<unknown>))
    .map(([name]) => name);

  assert.ok(referencing.length > 0, "no models registered - the import above is not doing its job");

  const accounted = new Set([...purgedCollections, ...keptCollections]);
  assert.deepEqual(
    referencing.filter((name) => !accounted.has(name)),
    []
  );
});
