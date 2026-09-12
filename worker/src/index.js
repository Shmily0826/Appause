/**
 * Appause Pro activation Worker (Plan B server side).
 *
 * Responsibilities:
 *  - Issue device-bound Pro license JWTs for lifetime activation codes
 *    (/api/redeem) and one-time self-service trials (/api/trial/start).
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
const INDEX_PREFIX = "pro:activation:";
const EVENT_PREFIX = "pro:event:";
// Activation-code alphabet: no ambiguous characters (0/O, 1/I, etc.).
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, POST, OPTIONS",
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

function sourceOf(value) {
  const source = (value || "unknown").toString().trim().slice(0, 64);
  return source || "unknown";
}

async function recordEvent(env, kind, source = "unknown") {
  const key = `${EVENT_PREFIX}${Date.now()}:${crypto.randomUUID()}`;
  await env.APPAUSE_CODES.put(key, JSON.stringify({ kind, source, at: Date.now() }));
}

async function listAll(env, prefix) {
  const items = [];
  let cursor;
  do {
    const page = await env.APPAUSE_CODES.list({ prefix, limit: 1000, ...(cursor ? { cursor } : {}) });
    items.push(...page.keys.map((key) => key.name));
    cursor = page.list_complete === false ? page.cursor : undefined;
  } while (cursor);
  return items;
}

async function putActivationIndex(env, code, record) {
  await env.APPAUSE_CODES.put(`${INDEX_PREFIX}${code}`, JSON.stringify(record));
}

async function updateActivationIndex(env, code, body) {
  const key = `${INDEX_PREFIX}${code}`;
  const current = await env.APPAUSE_CODES.get(key, { type: "json" });
  if (!current || !body.newlyBound) return;
  const updated = {
    ...current,
    activatedAt: body.activatedAt || current.activatedAt || Date.now(),
    expiresAt: body.expiresAt || current.expiresAt || null,
    status: "active",
  };
  await env.APPAUSE_CODES.put(key, JSON.stringify(updated));
  if (current.kind === "lifetime") {
    await recordEvent(env, "lifetime_activated", current.source);
  } else if (current.kind === "trial") {
    await recordEvent(env, "trial_activated", current.source);
  }
  if (current.kind === "lifetime" && current.linkedTrialCode) {
    const trialKey = `${INDEX_PREFIX}${current.linkedTrialCode}`;
    const trial = await env.APPAUSE_CODES.get(trialKey, { type: "json" });
    if (trial && !trial.lifetimeActivatedAt) {
      await env.APPAUSE_CODES.put(trialKey, JSON.stringify({
        ...trial,
        lifetimeActivatedAt: updated.activatedAt,
      }));
    }
  }
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

  const response = await dispatchCodeOperation(env, code, { action: "redeem", code, device });
  if (response.status < 200 || response.status >= 300) return response;
  const result = await response.json();
  await updateActivationIndex(env, code, result);
  return json(result, response.status);
}

async function trialObjectName(device) {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(device)
  );
  const hash = Array.from(new Uint8Array(digest), (byte) =>
    byte.toString(16).padStart(2, "0")
  ).join("");
  return `TRIAL-${hash}`;
}

/**
 * Start the one-time, device-bound seven-day trial.
 * Body: { device }
 */
async function handleTrialStart(req, env) {
  const body = await req.json().catch(() => ({}));
  const device = (body.device || "").toString().trim();
  if (!device || device.length > 256) return json({ error: "bad_request" }, 400);

  try {
    const code = await trialObjectName(device);
    return dispatchCodeOperation(env, code, { action: "start_trial", code, device });
  } catch {
    return json({ error: "trial_unavailable" }, 500);
  }
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
  const source = sourceOf(body.source);
  const code = randomCode();
  const kind = expiresInDays === 7 ? "trial" : expiresInDays == null ? "lifetime" : "expiring";
  const linkedTrialCode = kind === "lifetime"
    ? (body.trialCode || "").toString().trim().toUpperCase() || null
    : null;
  if (linkedTrialCode) {
    const trial = await env.APPAUSE_CODES.get(`${INDEX_PREFIX}${linkedTrialCode}`, { type: "json" });
    if (!trial || trial.kind !== "trial") return json({ error: "trial_not_found" }, 404);
    if (!trial.lifetimeRequestedAt || !trial.expiresAt || trial.expiresAt > Date.now()) {
      return json({ error: "trial_not_eligible" }, 409);
    }
  }
  const record = {
    status: "unused",
    maxDevices,
    expiresInDays,
    devices: [],
    createdAt: Date.now(),
    notes: body.notes || "",
  };
  await dispatchCodeOperation(env, code, { action: "initialize", code, record });
  await putActivationIndex(env, code, {
    code,
    kind,
    source,
    createdAt: record.createdAt,
    expiresInDays,
    activatedAt: null,
    expiresAt: null,
    status: "issued",
    linkedTrialCode,
  });
  if (kind === "lifetime") {
    await recordEvent(env, "lifetime_issued", source);
  } else if (kind === "trial") {
    await recordEvent(env, "trial_issued", source);
  }
  return json({ code });
}

/** Admin marks one eligible expired trial as requesting lifetime access. */
async function handleAdminLifetimeRequest(req, env) {
  if (!isAdmin(req, env)) return json({ error: "forbidden" }, 403);
  const body = await req.json().catch(() => ({}));
  const code = (body.code || "").toString().trim().toUpperCase();
  if (!code) return json({ error: "bad_request" }, 400);
  const key = `${INDEX_PREFIX}${code}`;
  const current = await env.APPAUSE_CODES.get(key, { type: "json" });
  if (!current || current.kind !== "trial") return json({ error: "trial_not_found" }, 404);
  if (!current.activatedAt || !current.expiresAt || current.expiresAt > Date.now()) {
    return json({ error: "trial_not_expired" }, 409);
  }
  if (current.lifetimeRequestedAt) {
    return json({ ok: true, lifetimeRequestedAt: current.lifetimeRequestedAt });
  }
  const lifetimeRequestedAt = Date.now();
  await env.APPAUSE_CODES.put(key, JSON.stringify({ ...current, lifetimeRequestedAt }));
  await recordEvent(env, "lifetime_requested", current.source);
  return json({ ok: true, lifetimeRequestedAt });
}

/** Minimal admin-only funnel JSON; no app usage or device identifiers. */
async function handleAdminStats(req, env) {
  if (!isAdmin(req, env)) return json({ error: "forbidden" }, 403);
  const eventKeys = await listAll(env, EVENT_PREFIX);
  const events = {};
  const bySource = {};
  for (const key of eventKeys) {
    const event = await env.APPAUSE_CODES.get(key, { type: "json" });
    if (!event) continue;
    events[event.kind] = (events[event.kind] || 0) + 1;
    bySource[event.source] = (bySource[event.source] || 0) + 1;
  }
  const activationKeys = await listAll(env, INDEX_PREFIX);
  const activations = [];
  for (const key of activationKeys) {
    const item = await env.APPAUSE_CODES.get(key, { type: "json" });
    if (item) activations.push(item);
  }
  const now = Date.now();
  const trials = activations.filter((item) => item.kind === "trial");
  const expiredTrials = trials.filter((item) => item.expiresAt != null && item.expiresAt <= now);
  const requestedTrials = expiredTrials.filter((item) => item.lifetimeRequestedAt != null);
  const convertedTrials = requestedTrials.filter((item) => item.lifetimeActivatedAt != null);
  return json({
    generatedAt: now,
    events,
    bySource,
    trial: {
      issued: trials.length,
      activated: trials.filter((item) => item.activatedAt != null).length,
      expired: expiredTrials.length,
      active: trials.filter((item) => item.expiresAt != null && item.expiresAt > now).length,
      lifetimeRequested: requestedTrials.length,
      lifetimeActivated: convertedTrials.length,
      requestConversion: expiredTrials.length === 0
        ? 0
        : requestedTrials.length / expiredTrials.length,
      lifetimeActivationConversion: requestedTrials.length === 0
        ? 0
        : convertedTrials.length / requestedTrials.length,
    },
    lifetime: {
      issued: activations.filter((item) => item.kind === "lifetime").length,
      activated: activations.filter((item) => item.kind === "lifetime" && item.activatedAt != null).length,
    },
  });
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
      case "/api/trial/start":
        if (!isPost) return json({ error: "method_not_allowed" }, 405);
        return handleTrialStart(request, env);
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
      case "/admin/lifetime-request":
        if (!isPost) return json({ error: "method_not_allowed" }, 405);
        return handleAdminLifetimeRequest(request, env);
      case "/admin/feedback":
        if (!isGet) return json({ error: "method_not_allowed" }, 405);
        return handleAdminFeedback(request, env);
      case "/admin/stats":
        if (!isGet) return json({ error: "method_not_allowed" }, 405);
        return handleAdminStats(request, env);
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
