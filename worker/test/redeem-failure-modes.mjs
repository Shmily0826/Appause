/**
 * redeem-failure-modes.mjs — exercises the Appause Pro Worker's /api/redeem
 * and /admin/* failure paths WITHOUT a live Cloudflare deployment.
 *
 * Strategy: import the REAL worker entry (../src/index.js) and drive its
 * `fetch` handler with the Node 22 global `Request`/`fetch`. We supply an
 * in-memory KV (no real Cloudflare KV) and inject an ephemeral RSA private key
 * as the APPAUSE_PRIVATE_KEY secret, so signJwt() works exactly as in prod.
 *
 * This covers the money-correctness cases that the client-side tests cannot
 * reach:
 *  - activation code does not exist        -> 404 invalid_code
 *  - device limit reached (buyout, 3 max)  -> 403 device_limit_reached (and the
 *                                              new device MUST NOT be persisted)
 *  - wrong / missing admin key on /admin/*  -> 403 forbidden
 *  - happy path redeem                      -> 200 { token }
 *  - CONCURRENCY RACE: two simultaneous redeem calls for a fresh code both
 *    read devices=[] and write back, so the non-atomic read-modify-write can
 *    lose a device binding. This test PROVES the race exists (it is a known
 *    audit finding, not something we "fix" here — the worker should move to a
 *    atomic KV op or single-flight lock before charging goes live).
 *
 * Run: node test/redeem-failure-modes.mjs
 */

import { generateKeyPairSync } from "node:crypto";
import worker, { ActivationCodeDurableObject } from "../src/index.js";

let passed = 0;
let failed = 0;
const failures = [];

function check(name, cond) {
  if (cond) {
    passed++;
    console.log(`  PASS ${name}`);
  } else {
    failed++;
    failures.push(name);
    console.log(`  FAIL ${name}`);
  }
}

// ---------------------------------------------------------------------------
// In-memory KV that mirrors the Cloudflare Workers KV surface the worker uses:
//   get(key, { type: "json" }) -> object | null
//   put(key, string)
//   list({ prefix, limit })    -> { keys: [{ name }] }
// The KV fake is deliberately not a coordination mechanism. The fake Durable
// Object namespace below serializes each object's requests, matching the
// production coordination boundary while still exercising the real DO class.
// ---------------------------------------------------------------------------
function makeMemoryKv() {
  const store = new Map();
  return {
    async get(key, opts) {
      const raw = store.get(key);
      if (raw == null) return null;
      return opts && opts.type === "json" ? JSON.parse(raw) : raw;
    },
    async put(key, value) {
      store.set(key, String(value));
    },
    async list({ prefix = "", limit = 1000 } = {}) {
      const keys = [];
      for (const k of store.keys()) {
        if (k.startsWith(prefix)) keys.push({ name: k });
      }
      return { keys: keys.slice(0, limit) };
    },
    // test helper
    _raw: store,
  };
}

function makeDurableObjectNamespace(env, { concurrencyGuard = true, signer = null } = {}) {
  const objects = new Map();
  return {
    idFromName(name) {
      return name;
    },
    get(id) {
      let entry = objects.get(id);
      if (!entry) {
        const storage = new Map();
        let guardTail = Promise.resolve();
        const state = {
          storage: {
            async get(key) { return storage.get(key); },
            async put(key, value) { storage.set(key, structuredClone(value)); },
          },
          blockConcurrencyWhile(callback) {
            if (!concurrencyGuard) return callback();
            const run = guardTail.then(callback);
            guardTail = run.catch(() => {});
            return run;
          },
        };
        const object = new ActivationCodeDurableObject(state, env);
        if (signer) object.signToken = signer;
        entry = { object, tail: Promise.resolve(), storage };
        objects.set(id, entry);
      }
      return {
        fetch: (url, init) => {
          const request = new Request(url, init);
          // Deliberately do not queue the whole request here. The production
          // DO's blockConcurrencyWhile is the only intended guard.
          return entry.object.fetch(request);
        },
        _storage: entry.storage,
      };
    },
    _objects: objects,
  };
}

function makeEnv(kv, options = {}) {
  const { privateKey: nodePrivate } = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    publicKeyEncoding: { type: "spki", format: "pem" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
  });
  const env = {
    APPAUSE_CODES: kv,
    APPAUSE_PRIVATE_KEY: nodePrivate, // ephemeral; never the production key
    ADMIN_KEY: "test-admin-secret",
    DOWNLOAD_TOKEN: "test-download-token",
  };
  env.ACTIVATION_CODES = makeDurableObjectNamespace(env, options);
  return env;
}

const BASE = "https://worker.test";

async function redeem(env, code, device) {
  return worker.fetch(
    new Request(`${BASE}/api/redeem`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code, device }),
    }),
    env
  );
}

function codeRecord(overrides = {}) {
  return {
    status: "unused",
    maxDevices: 3,
    expiresInDays: null,
    devices: [],
    ...overrides,
  };
}

function deferred() {
  let resolve;
  const promise = new Promise((r) => { resolve = r; });
  return { promise, resolve };
}

async function main() {
  // --- 1) code does not exist -> 404 invalid_code -------------------------
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    const res = await redeem(env, "APPAUSE-NOPE-NOPE", "devA");
    const body = await res.json();
    check("redeem unknown code -> 404", res.status === 404);
    check("redeem unknown code -> invalid_code", body.error === "invalid_code");
  }

  // --- 2) device limit reached -> 403, and new device NOT persisted -------
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    const code = "APPAUSE-FULL-FULL";
    // Pre-bind the maximum (3) devices.
    kv._raw.set(
      code,
      JSON.stringify(codeRecord({ status: "active", devices: ["d1", "d2", "d3"] }))
    );
    const res = await redeem(env, code, "d4");
    const body = await res.json();
    check("redeem beyond limit -> 403", res.status === 403);
    check("redeem beyond limit -> device_limit_reached", body.error === "device_limit_reached");
    const stored = await env.ACTIVATION_CODES.get(code)._storage.get("record");
    check("device-limit: 4th device NOT persisted", !stored.devices.includes("d4"));
    check("device-limit: still exactly 3 devices", stored.devices.length === 3);
  }

  // --- 3) happy path -> 200 { token } and device persisted ----------------
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    const code = "APPAUSE-OKAY-OKAY";
    kv._raw.set(code, JSON.stringify(codeRecord()));
    const res = await redeem(env, code, "devX");
    const body = await res.json();
    check("redeem valid code -> 200", res.status === 200);
    check("redeem valid code -> has token", typeof body.token === "string" && body.token.split(".").length === 3);
    const stored = await env.ACTIVATION_CODES.get(code)._storage.get("record");
    check("redeem valid code -> device persisted", stored.devices.includes("devX"));
    check("redeem valid code -> status active", stored.status === "active");
  }

  // --- 4) re-redeem same device is idempotent (still 200, no duplicate) ---
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    const code = "APPAUSE-DUPL-DUPL";
    kv._raw.set(code, JSON.stringify(codeRecord({ devices: ["devX"] })));
    const res = await redeem(env, code, "devX");
    const body = await res.json();
    check("re-redeem same device -> 200", res.status === 200);
    const stored = await env.ACTIVATION_CODES.get(code)._storage.get("record");
    check("re-redeem same device -> no duplicate", stored.devices.filter((d) => d === "devX").length === 1);
  }

  // --- 5) admin key wrong -> 403 forbidden on /admin/gencode -------------
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    const res = await worker.fetch(
      new Request(`${BASE}/admin/gencode`, {
        method: "POST",
        headers: { "content-type": "application/json", "x-admin-key": "wrong-key" },
        body: JSON.stringify({ maxDevices: 3 }),
      }),
      env
    );
    const body = await res.json();
    check("admin gencode wrong key -> 403", res.status === 403);
    check("admin gencode wrong key -> forbidden", body.error === "forbidden");
  }

  // --- 6) admin key missing -> 403 forbidden ------------------------------
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    const res = await worker.fetch(
      new Request(`${BASE}/admin/gencode`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ maxDevices: 3 }),
      }),
      env
    );
    check("admin gencode missing key -> 403", res.status === 403);
  }

  // --- 7) admin key correct -> 200 and mints a code -----------------------
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    const res = await worker.fetch(
      new Request(`${BASE}/admin/gencode`, {
        method: "POST",
        headers: { "content-type": "application/json", "x-admin-key": "test-admin-secret" },
        body: JSON.stringify({ maxDevices: 3 }),
      }),
      env
    );
    const body = await res.json();
    check("admin gencode correct key -> 200", res.status === 200);
    check("admin gencode correct key -> returns code", typeof body.code === "string");
  }

  // --- 8) concurrent new devices with capacity ----------------------------
  {
    const kv = makeMemoryKv();
    const gate = deferred();
    let signerStarted = 0;
    const env = makeEnv(kv, {
      signer: async (_record, _code, device) => {
        signerStarted++;
        await gate.promise;
        return `test-token-${device}`;
      },
    });
    const code = "APPAUSE-RACE-RACE";
    kv._raw.set(code, JSON.stringify(codeRecord()));
    const first = redeem(env, code, "devA");
    while (signerStarted === 0) await Promise.resolve();
    const second = redeem(env, code, "devB");
    await Promise.resolve();
    check("delayed signer: second request held by DO guard", signerStarted === 1);
    gate.resolve();
    const [r1, r2] = await Promise.all([first, second]);
    await r1.json(); await r2.json();
    const stored = await env.ACTIVATION_CODES.get(code)._storage.get("record");
    check("race: both calls returned 200", r1.status === 200 && r2.status === 200);
    check("race: both bindings persist", stored.devices.length === 2 && stored.devices.includes("devA") && stored.devices.includes("devB"));
  }

  // --- 9) concurrent requests competing for the final slot ----------------
  {
    const kv = makeMemoryKv();
    const gate = deferred();
    const env = makeEnv(kv, {
      signer: async () => {
        await gate.promise;
        return "test-token-final-slot";
      },
    });
    const code = "APPAUSE-LIMIT-LIMIT";
    kv._raw.set(code, JSON.stringify(codeRecord({ devices: ["d1", "d2"] })));
    const first = redeem(env, code, "devA");
    const second = redeem(env, code, "devB");
    await Promise.resolve();
    gate.resolve();
    const [r1, r2] = await Promise.all([first, second]);
    const statuses = [r1.status, r2.status].sort();
    const stored = await env.ACTIVATION_CODES.get(code)._storage.get("record");
    check("final slot: exactly one success", statuses[0] === 200 && statuses[1] === 403);
    check("final slot: no oversubscription", stored.devices.length === 3);
  }

  // --- 10) concurrent same-device redemption is idempotent ----------------
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    const code = "APPAUSE-SAME-SAME";
    kv._raw.set(code, JSON.stringify(codeRecord()));
    const [r1, r2] = await Promise.all([redeem(env, code, "devSame"), redeem(env, code, "devSame")]);
    const stored = await env.ACTIVATION_CODES.get(code)._storage.get("record");
    check("same device: both succeed", r1.status === 200 && r2.status === 200);
    check("same device: one slot", stored.devices.filter((d) => d === "devSame").length === 1);
  }

  // --- 11) legacy KV record bootstraps into the DO -------------------------
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    const code = "APPAUSE-OLDK-OLDK";
    kv._raw.set(code, JSON.stringify(codeRecord({ maxDevices: 2, expiresInDays: 30, devices: ["oldDev"] })));
    const [res, second] = await Promise.all([redeem(env, code, "newDev"), redeem(env, code, "newDev2")]);
    const stored = await env.ACTIVATION_CODES.get(code)._storage.get("record");
    check("legacy KV: concurrent redeems respect limit", [res.status, second.status].sort().join(",") === "200,403");
    check("legacy KV: bootstrap preserves bindings and limit", stored.devices.length === 2 && stored.devices.includes("oldDev") && stored.maxDevices === 2);
    check("legacy KV: expiry metadata preserved", stored.expiresInDays === 30);
  }

  // --- 12) signing failure does not consume a new slot --------------------
  {
    const kv = makeMemoryKv();
    const env = makeEnv(kv);
    env.APPAUSE_PRIVATE_KEY = "not-a-private-key";
    const code = "APPAUSE-SIGN-SIGN";
    kv._raw.set(code, JSON.stringify(codeRecord()));
    const res = await redeem(env, code, "neverBound");
    const stored = await env.ACTIVATION_CODES.get(code)._storage.get("record");
    check("signing failure: returns 500", res.status === 500);
    check("signing failure: no slot consumed", stored.devices.length === 0);
  }

  // --- 13) self/admin unbind use the same canonical DO state --------------
  {
    const kv = makeMemoryKv();
    const gate = deferred();
    let signerStarted = 0;
    const env = makeEnv(kv, {
      signer: async (_record, _code, device) => {
        signerStarted++;
        await gate.promise;
        return `test-token-${device}`;
      },
    });
    const code = "APPAUSE-UNBD-UNBD";
    kv._raw.set(code, JSON.stringify(codeRecord({ devices: ["keep", "remove"] })));
    const redeemRequest = redeem(env, code, "newDevice");
    while (signerStarted === 0) await Promise.resolve();
    const selfRequest = worker.fetch(new Request(`${BASE}/api/unbind`, { method: "POST", body: JSON.stringify({ code, device: "remove" }) }), env);
    gate.resolve();
    const [, self] = await Promise.all([redeemRequest, selfRequest]);
    const stored = await env.ACTIVATION_CODES.get(code)._storage.get("record");
    check("unbind: redeem + self-unbind are serializable", self.status === 200 && stored.devices.length === 2 && stored.devices.includes("keep") && stored.devices.includes("newDevice"));

    const adminKv = makeMemoryKv();
    const adminGate = deferred();
    let adminSignerStarted = 0;
    const adminEnv = makeEnv(adminKv, {
      signer: async (_record, _code, device) => {
        adminSignerStarted++;
        await adminGate.promise;
        return `test-token-${device}`;
      },
    });
    const adminCode = "APPAUSE-ADMN-ADMN";
    adminKv._raw.set(adminCode, JSON.stringify(codeRecord({ devices: ["keep", "remove"] })));
    const adminRedeem = redeem(adminEnv, adminCode, "newDevice");
    while (adminSignerStarted === 0) await Promise.resolve();
    const adminRequest = worker.fetch(new Request(`${BASE}/admin/unbind`, { method: "POST", headers: { "x-admin-key": "test-admin-secret" }, body: JSON.stringify({ code: adminCode, device: "remove" }) }), adminEnv);
    adminGate.resolve();
    const [, admin] = await Promise.all([adminRedeem, adminRequest]);
    const adminStored = await adminEnv.ACTIVATION_CODES.get(adminCode)._storage.get("record");
    check("unbind: redeem + admin-unbind are serializable", admin.status === 200 && adminStored.devices.length === 2 && adminStored.devices.includes("keep") && adminStored.devices.includes("newDevice"));
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) {
    console.error("FAILURES:", failures.join(", "));
    process.exit(1);
  }
}

main().catch((err) => {
  console.error("TEST CRASHED:", err);
  process.exit(1);
});
