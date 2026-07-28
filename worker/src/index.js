/**
 * Appause Pro activation Worker (Plan B server side).
 *
 * Responsibilities:
 *  - Issue device-bound Pro license JWTs when a valid activation code + device
 *    fingerprint are presented (/api/redeem).
 *  - Enforce a per-code device limit (buyout model: e.g. 3 devices).
 *  - Allow unbinding a device (/api/unbind, self-service) or via admin
 *    (/admin/unbind) for lost devices.
 *  - Mint new activation codes (/admin/gencode).
 *
 * Security model:
 *  - The RSA signing PRIVATE KEY lives only in the Cloudflare secret
 *    APPAUSE_PRIVATE_KEY. The app ships only the matching PUBLIC key, so a fork
 *    of the open-source repo can verify tokens but cannot mint them.
 *  - Activation codes and their device bindings are stored in a KV namespace
 *    (APPAUSE_CODES). The server never sees the user's apps, usage, or identity.
 *
 * See worker/README.md for deploy + key-setup instructions.
 */

import { signJwt, importPrivateKeyPem } from "./jwt.mjs";

// Bindings (configured in wrangler.toml / via `wrangler secret put`):
//   env.APPAUSE_CODES    — KV namespace
//   env.APPAUSE_PRIVATE_KEY — PKCS#8 PEM RSA private key (secret)
//   env.ADMIN_KEY        — shared secret for the /admin/* endpoints (secret)

let privateKeyPromise = null;
function getPrivateKey(env) {
  if (!privateKeyPromise) {
    privateKeyPromise = importPrivateKeyPem(env.APPAUSE_PRIVATE_KEY);
  }
  return privateKeyPromise;
}

const DEFAULT_MAX_DEVICES = 3;
// Activation-code alphabet: no ambiguous characters (0/O, 1/I, etc.).
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "POST, OPTIONS",
      "access-control-allow-headers": "content-type, x-admin-key",
    },
  });
}

function randomCode() {
  const seg = (n) =>
    Array.from({ length: n }, () =>
      CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)]
    ).join("");
  return `APPAUSE-${seg(4)}-${seg(4)}`;
}

function isAdmin(req, env) {
  return req.headers.get("x-admin-key") === env.ADMIN_KEY;
}

/**
 * Redeem an activation code for a device.
 * Body: { code, device }
 *  - code  : activation code string
 *  - device: SHA-256(public key DER) hex, computed on-device by DeviceKeyStore
 * Returns: { token } on success, or an error object.
 */
async function handleRedeem(req, env) {
  const body = await req.json().catch(() => ({}));
  const code = (body.code || "").toString().trim().toUpperCase();
  const device = (body.device || "").toString().trim();
  if (!code || !device) {
    return json({ error: "bad_request" }, 400);
  }

  const record = await env.APPAUSE_CODES.get(code, { type: "json" });
  if (!record) {
    return json({ error: "invalid_code" }, 404);
  }
  const devices = Array.isArray(record.devices) ? record.devices : [];
  const maxDevices = record.maxDevices || DEFAULT_MAX_DEVICES;

  if (!devices.includes(device)) {
    if (devices.length >= maxDevices) {
      return json({ error: "device_limit_reached" }, 403);
    }
    devices.push(device);
  }

  const nowSec = Math.floor(Date.now() / 1000);
  const payload = { tier: "pro", iat: nowSec, jti: code, device };
  if (record.expiresInDays) {
    payload.exp = nowSec + record.expiresInDays * 86400;
  }

  const privateKey = await getPrivateKey(env);
  const token = await signJwt(payload, privateKey);

  record.devices = devices;
  record.status = "active";
  await env.APPAUSE_CODES.put(code, JSON.stringify(record));

  return json({ token });
}

/**
 * Self-service unbind: removes a device the code is already bound to.
 * Body: { code, device }
 * Use this before selling/giving away a device.
 */
async function handleUnbind(req, env) {
  const body = await req.json().catch(() => ({}));
  const code = (body.code || "").toString().trim().toUpperCase();
  const device = (body.device || "").toString().trim();
  if (!code || !device) {
    return json({ error: "bad_request" }, 400);
  }
  const record = await env.APPAUSE_CODES.get(code, { type: "json" });
  if (!record) {
    return json({ error: "invalid_code" }, 404);
  }
  const devices = Array.isArray(record.devices) ? record.devices : [];
  if (!devices.includes(device)) {
    return json({ error: "device_not_bound" }, 404);
  }
  record.devices = devices.filter((d) => d !== device);
  if (record.devices.length === 0) record.status = "unused";
  await env.APPAUSE_CODES.put(code, JSON.stringify(record));
  return json({ ok: true });
}

/** Admin: mint a new activation code. Requires x-admin-key header. */
async function handleGenCode(req, env) {
  if (!isAdmin(req, env)) return json({ error: "forbidden" }, 403);
  const body = await req.json().catch(() => ({}));
  const maxDevices = body.maxDevices || DEFAULT_MAX_DEVICES;
  const expiresInDays = body.expiresInDays ?? null;
  const code = randomCode();
  const record = {
    status: "unused",
    maxDevices,
    expiresInDays,
    devices: [],
    createdAt: Date.now(),
    notes: body.notes || "",
  };
  await env.APPAUSE_CODES.put(code, JSON.stringify(record));
  return json({ code });
}

/** Admin: unbind a device by code (for lost devices). Requires x-admin-key. */
async function handleAdminUnbind(req, env) {
  if (!isAdmin(req, env)) return json({ error: "forbidden" }, 403);
  const body = await req.json().catch(() => ({}));
  const code = (body.code || "").toString().trim().toUpperCase();
  const device = (body.device || "").toString().trim();
  if (!code || !device) return json({ error: "bad_request" }, 400);
  const record = await env.APPAUSE_CODES.get(code, { type: "json" });
  if (!record) return json({ error: "invalid_code" }, 404);
  const devices = Array.isArray(record.devices) ? record.devices : [];
  record.devices = devices.filter((d) => d !== device);
  if (record.devices.length === 0) record.status = "unused";
  await env.APPAUSE_CODES.put(code, JSON.stringify(record));
  return json({ ok: true });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // CORS preflight (harmless for the Android app, useful for web clients).
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "POST, OPTIONS",
          "access-control-allow-headers": "content-type, x-admin-key",
        },
      });
    }

    if (request.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405);
    }

    switch (url.pathname) {
      case "/api/redeem":
        return handleRedeem(request, env);
      case "/api/unbind":
        return handleUnbind(request, env);
      case "/admin/gencode":
        return handleGenCode(request, env);
      case "/admin/unbind":
        return handleAdminUnbind(request, env);
      default:
        return json({ error: "not_found" }, 404);
    }
  },
};
