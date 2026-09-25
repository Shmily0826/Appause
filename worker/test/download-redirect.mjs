import assert from "node:assert/strict";
import worker from "../src/index.js";

const values = new Map();
const env = {
  DOWNLOAD_TOKEN: "test-download-token",
  APPAUSE_CODES: {
    async get(key) { return values.get(key) ?? null; },
    async put(key, value) { values.set(key, String(value)); },
  },
};
const base = "https://worker.test/api/download";

async function download({ to, token = env.DOWNLOAD_TOKEN } = {}) {
  const url = new URL(base);
  if (to) url.searchParams.set("to", to);
  if (token) url.searchParams.set("t", token);
  return worker.fetch(new Request(url), env);
}

const missingToken = await download({ token: "" });
assert.equal(missingToken.status, 403);
assert.equal(values.has("downloads:total"), false);

const allowed = "https://shmily0826.lanzoup.com/b01eunt29a";
const mirror = await download({ to: allowed });
assert.equal(mirror.status, 302);
assert.equal(mirror.headers.get("location"), allowed);
assert.equal(values.get("downloads:total"), "1");

for (const to of [
  "https://attacker.example/collect",
  "http://shmily0826.lanzoup.com/b01eunt29a",
  `${allowed}/other-file`,
  `${allowed}?next=https://attacker.example/collect`,
]) {
  const response = await download({ to });
  assert.equal(response.status, 400);
  assert.equal(values.get("downloads:total"), "1");
}

const countOnly = await download();
assert.equal(countOnly.status, 200);
assert.deepEqual(await countOnly.json(), { downloads: 2 });

console.log("PASS download token gate, fixed mirror redirect, and rejected targets");
