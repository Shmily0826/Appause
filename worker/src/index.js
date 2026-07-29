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
 *  - Collect anonymous user feedback (/api/feedback, read via /admin/feedback).
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
  if (env.APPAUSE_PRIVATE_KEY == null) {
    // Surfaced as a clear error instead of an opaque Cloudflare 1101.
    return Promise.reject(new Error("APPAUSE_PRIVATE_KEY secret is not set"));
  }
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

  let token;
  try {
    const privateKey = await getPrivateKey(env);
    token = await signJwt(payload, privateKey);
  } catch (e) {
    // Never leak the raw secret, but return enough to diagnose (missing /
    // malformed key) instead of Cloudflare's opaque error code 1101.
    return json({ error: "signing_failed", detail: String((e && e.message) || e) }, 500);
  }

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

/**
 * Accept anonymous user feedback (bug report / suggestion).
 * Body: { type: "bug"|"suggestion", message, contact?, appVersion,
 *         androidVersion, deviceModel, language }
 * Stored in the same KV namespace under the "feedback:" prefix. The user is
 * never required to provide an email or account — this is user-initiated, not
 * telemetry. The developer reads these via /admin/feedback.
 */
async function handleFeedback(req, env) {
  let body;
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }
  const message = (body.message || "").toString().trim();
  const type = body.type === "suggestion" ? "suggestion" : "bug";
  if (message.length < 1) return json({ error: "empty_message" }, 400);
  if (message.length > 8000) return json({ error: "message_too_long" }, 413);

  const record = {
    receivedAt: Date.now(),
    type,
    message,
    contact: (body.contact || "").toString().trim() || null,
    appVersion: (body.appVersion || "").toString(),
    androidVersion: (body.androidVersion || "").toString(),
    deviceModel: (body.deviceModel || "").toString(),
    language: (body.language || "").toString(),
  };
  const key = `feedback:${Date.now()}:${Math.random().toString(36).slice(2, 10)}`;
  await env.APPAUSE_CODES.put(key, JSON.stringify(record));
  return json({ ok: true });
}

/** Admin: list stored feedback (newest first). Requires x-admin-key header. */
async function handleAdminFeedback(req, env) {
  if (!isAdmin(req, env)) return json({ error: "forbidden" }, 403);
  const { keys } = await env.APPAUSE_CODES.list({ prefix: "feedback:", limit: 200 });
  const items = [];
  for (const k of keys) {
    const raw = await env.APPAUSE_CODES.get(k.name);
    if (raw) {
      try {
        items.push(JSON.parse(raw));
      } catch {
        /* skip unparseable */
      }
    }
  }
  items.sort((a, b) => (b.receivedAt || 0) - (a.receivedAt || 0));
  return json({ count: items.length, items });
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

    const isPost = request.method === "POST";
    const isGet = request.method === "GET";
    if (!isPost && !isGet) {
      return json({ error: "method_not_allowed" }, 405);
    }

    switch (url.pathname) {
      case "/api/redeem":
        if (!isPost) return json({ error: "method_not_allowed" }, 405);
        return handleRedeem(request, env);
      case "/api/unbind":
        if (!isPost) return json({ error: "method_not_allowed" }, 405);
        return handleUnbind(request, env);
      case "/api/feedback":
        if (!isPost) return json({ error: "method_not_allowed" }, 405);
        return handleFeedback(request, env);
      case "/admin/gencode":
        if (!isPost) return json({ error: "method_not_allowed" }, 405);
        return handleGenCode(request, env);
      case "/admin/unbind":
        if (!isPost) return json({ error: "method_not_allowed" }, 405);
        return handleAdminUnbind(request, env);
      case "/admin/feedback":
        if (!isGet) return json({ error: "method_not_allowed" }, 405);
        return handleAdminFeedback(request, env);
      default:
        return json({ error: "not_found" }, 404);
    }
  },
};
