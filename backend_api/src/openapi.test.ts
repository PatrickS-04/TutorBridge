// Reference: Node.js. 2026. Test runner (Node.js v22.23.2 documentation). [Online].
//   Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
import { test } from "node:test";
import assert from "node:assert/strict";
import { routeMounts } from "./routes";
import { openapiSpec } from "./openapi";

/**
 * A hand-written spec is only worth having while it still matches the code,
 * so this reads the real Express route surface back and diffs it against the
 * spec in both directions. Adding a route without documenting it, or leaving
 * docs behind for one that was renamed, fails the build.
 */

const HTTP_METHODS = ["get", "post", "put", "patch", "delete", "head", "options"];

/** "/bookings" + "/:id/confirm" -> "put /bookings/{id}/confirm" */
function specPath(mount: string, routePath: string) {
  const joined = (mount + routePath).replace(/\/+$/, "") || "/";
  return joined.replace(/:([A-Za-z0-9_]+)/g, "{$1}");
}

function registeredOperations(): string[] {
  const operations: string[] = [];
  for (const [mount, router] of Object.entries(routeMounts)) {
    for (const layer of (router as any).stack) {
      if (!layer.route) continue;
      for (const method of Object.keys(layer.route.methods)) {
        if (!HTTP_METHODS.includes(method)) continue; // skip Express's _all
        operations.push(`${method} ${specPath(mount, layer.route.path)}`);
      }
    }
  }
  return operations.sort();
}

function documentedOperations(): string[] {
  return Object.entries(openapiSpec.paths)
    .flatMap(([path, item]) =>
      Object.keys(item)
        .filter((key) => HTTP_METHODS.includes(key))
        .map((method) => `${method} ${path}`)
    )
    .sort();
}

test("every registered route is documented", () => {
  const documented = new Set(documentedOperations());
  const undocumented = registeredOperations().filter((op) => !documented.has(op));
  assert.deepEqual(undocumented, [], `add these to openapi.ts: ${undocumented.join(", ")}`);
});

test("the spec documents no route that no longer exists", () => {
  const registered = new Set(registeredOperations());
  const stale = documentedOperations().filter((op) => !registered.has(op));
  assert.deepEqual(stale, [], `remove these from openapi.ts: ${stale.join(", ")}`);
});

test("every $ref resolves", () => {
  // A typo'd $ref is not a type error - it just renders as an empty schema.
  const pointers: string[] = [];
  const collect = (node: unknown) => {
    if (Array.isArray(node)) return node.forEach(collect);
    if (!node || typeof node !== "object") return;
    for (const [key, value] of Object.entries(node)) {
      if (key === "$ref" && typeof value === "string") pointers.push(value);
      else collect(value);
    }
  };
  collect(openapiSpec);

  const broken = pointers.filter(
    (pointer) =>
      pointer
        .slice(2)
        .split("/")
        .reduce<any>((node, key) => node?.[key], openapiSpec) === undefined
  );
  assert.deepEqual([...new Set(broken)], []);
});
