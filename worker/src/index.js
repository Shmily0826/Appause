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
 *  - Maintain an aggregate, PII-free download counter (/api/download,
 *    /api/download-count) that totals installs across all channels.
 *
 * Security model:
 *  - The RSA signing PRIVATE KEY lives only in the Cloudflare secret
 *    APPAUSE_PRIVATE_KEY. The app ships only the matching PUBLIC key, so a fork
 *    of the open-source repo can verify tokens but cannot mint them.
 *  - A SQLite-backed Durable Object is the authoritative store for each
 *    activation code. APPAUSE_CODES bootstraps legacy records only.
 *    The server never sees the user's apps, usage, or identity.
 *
 * See worker/README.md for deploy + key-setup instructions.
 */

import { ActivationCodeDurableObject } from "./activation-code-do.js";

export { ActivationCodeDurableObject };

// Bindings (configured in wrangler.toml / via `wrangler secret put`):
//   env.APPAUSE_CODES    — legacy KV namespace and unrelated Worker data
//   env.ACTIVATION_CODES — one SQLite-backed Durable Object per activation code
//   env.APPAUSE_PRIVATE_KEY — PKCS#8 PEM RSA private key (secret)
//   env.ADMIN_KEY        — shared secret for the /admin/* endpoints (secret)
//   env.DOWNLOAD_TOKEN   — shared token gating /api/download increments (secret)

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

  return dispatchCodeOperation(env, code, { action: "redeem", code, device });
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
  return dispatchCodeOperation(env, code, { action: "unbind", code, device });
}

function getCodeObject(env, code) {
  const id = env.ACTIVATION_CODES.idFromName(code);
  return env.ACTIVATION_CODES.get(id);
}

async function dispatchCodeOperation(env, code, body) {
  return getCodeObject(env, code).fetch("https://activation-code.internal/", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
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
  await dispatchCodeOperation(env, code, { action: "initialize", code, record });
  return json({ code });
}

/** Admin: unbind a device by code (for lost devices). Requires x-admin-key. */
async function handleAdminUnbind(req, env) {
  if (!isAdmin(req, env)) return json({ error: "forbidden" }, 403);
  const body = await req.json().catch(() => ({}));
  const code = (body.code || "").toString().trim().toUpperCase();
  const device = (body.device || "").toString().trim();
  if (!code || !device) return json({ error: "bad_request" }, 400);
  return dispatchCodeOperation(env, code, { action: "unbind", code, device });
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

/**
 * Aggregate, anonymous download counter (no PII — only a single number).
 *
 * The canonical "Download" CTA points here:
 *   https://<worker>/api/download?to=<apk-url>&t=<token>
 * We increment one KV counter and 302-redirect to the real APK, so downloads
 * from any channel (GitHub Releases, 蓝奏云, Coolapk) that flow through the
 * official link are counted in a single real total. The increment is gated
 * behind a shared DOWNLOAD_TOKEN so the count stays trustworthy.
 */
async function bumpDownload(env) {
  const raw = await env.APPAUSE_CODES.get("downloads:total");
  const n = Math.max(0, parseInt(raw || "0", 10) || 0) + 1;
  await env.APPAUSE_CODES.put("downloads:total", String(n));
  return n;
}

async function handleDownload(req, env) {
  const url = new URL(req.url);
  const token =
    url.searchParams.get("t") || req.headers.get("x-download-token");
  if (token !== env.DOWNLOAD_TOKEN) {
    return json({ error: "forbidden" }, 403);
  }
  const n = await bumpDownload(env);
  const to = url.searchParams.get("to");
  if (to) {
    try {
      const u = new URL(to);
      if (u.protocol === "https:" || u.protocol === "http:") {
        return Response.redirect(to, 302);
      }
    } catch {
      /* fall through to JSON */
    }
  }
  return json({ downloads: n });
}

/** Public read of the aggregate download total (no auth, no PII). */
async function handleDownloadCount(req, env) {
  const raw = await env.APPAUSE_CODES.get("downloads:total");
  const n = Math.max(0, parseInt(raw || "0", 10) || 0);
  return json({ downloads: n });
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
      case "/api/download":
        if (!isGet) return json({ error: "method_not_allowed" }, 405);
        return handleDownload(request, env);
      case "/api/download-count":
        if (!isGet) return json({ error: "method_not_allowed" }, 405);
        return handleDownloadCount(request, env);
      default:
        return json({ error: "not_found" }, 404);
    }
  },
};
