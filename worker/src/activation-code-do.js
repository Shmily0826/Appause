import { signJwt, importPrivateKeyPem } from "./jwt.mjs";

const DEFAULT_MAX_DEVICES = 3;
const DAY_MS = 24 * 60 * 60 * 1000;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

async function readBody(request) {
  return request.json().catch(() => ({}));
}

/**
 * One SQLite-backed Durable Object is the serialization boundary for one
 * activation code. The KV record is read only during first-use bootstrap;
 * after that, this object's storage is the canonical mutable state.
 */
export class ActivationCodeDurableObject {
  constructor(state, env) {
    this.state = state;
    this.env = env;
  }

  async loadRecord(code) {
    let record = await this.state.storage.get("record");
    if (record) return record;

    record = await this.env.APPAUSE_CODES.get(code, { type: "json" });
    if (!record) return null;

    const imported = {
      ...record,
      devices: Array.isArray(record.devices) ? [...new Set(record.devices)] : [],
      maxDevices: record.maxDevices || DEFAULT_MAX_DEVICES,
      expiresInDays: record.expiresInDays ?? null,
      status: record.status || "unused",
    };
    await this.state.storage.put("record", imported);
    return imported;
  }

  async saveRecord(record) {
    await this.state.storage.put("record", record);
  }

  async signToken(record, code, device) {
    if (this.env.APPAUSE_PRIVATE_KEY == null) {
      throw new Error("APPAUSE_PRIVATE_KEY secret is not set");
    }
    const privateKey = await importPrivateKeyPem(this.env.APPAUSE_PRIVATE_KEY);
    const nowSec = Math.floor(Date.now() / 1000);
    const payload = { tier: "pro", iat: nowSec, jti: code, device };
    if (record.expiresInDays) {
      if (Number(record.expiresInDays) === 7) payload.trial = true;
      const expiresAt = record.expiresAt ||
        ((record.activatedAt || Date.now()) + record.expiresInDays * DAY_MS);
      payload.exp = Math.floor(expiresAt / 1000);
    }
    return signJwt(payload, privateKey);
  }

  async redeem(code, device) {
    const record = await this.loadRecord(code);
    if (!record) return json({ error: "invalid_code" }, 404);

    const devices = Array.isArray(record.devices) ? [...record.devices] : [];
    const alreadyBound = devices.includes(device);
    if (!alreadyBound && devices.length >= (record.maxDevices || DEFAULT_MAX_DEVICES)) {
      return json({ error: "device_limit_reached" }, 403);
    }

    const expiring = Number(record.expiresInDays) > 0;
    const now = Date.now();
    if (expiring && record.expiresAt && now >= record.expiresAt) {
      return json({ error: "trial_expired" }, 410);
    }

    // New expiring codes anchor their lifetime at the first successful bind.
    // Legacy records that already have devices but no anchor retain their old
    // shape; there is no truthful activation timestamp to reconstruct.
    const activation = expiring && !alreadyBound && !record.activatedAt
      ? { activatedAt: now, expiresAt: now + record.expiresInDays * DAY_MS }
      : null;
    const nextRecord = activation ? { ...record, ...activation } : record;

    // Sign before committing a new device, so signing failure cannot consume
    // a slot. The DO serializes competing requests for this code.
    let token;
    try {
      token = await this.signToken(nextRecord, code, device);
    } catch (e) {
      return json({ error: "signing_failed", detail: String((e && e.message) || e) }, 500);
    }

    if (!alreadyBound) {
      devices.push(device);
      nextRecord.devices = devices;
      nextRecord.status = "active";
      await this.saveRecord(nextRecord);
    } else if (record.status !== "active") {
      record.status = "active";
      await this.saveRecord(record);
    }
    return json({
      token,
      newlyBound: !alreadyBound,
      lifetime: !expiring,
      activatedAt: nextRecord.activatedAt || null,
      expiresAt: nextRecord.expiresAt || null,
    });
  }

  async startTrial(code, device) {
    const current = await this.state.storage.get("record");
    const now = Date.now();
    if (!current) {
      const record = {
        kind: "trial",
        device,
        status: "active",
        maxDevices: 1,
        expiresInDays: 7,
        devices: [device],
        createdAt: now,
        activatedAt: now,
        expiresAt: now + 7 * DAY_MS,
      };
      try {
        const token = await this.signToken(record, code, device);
        await this.saveRecord(record);
        return json({ token, newlyStarted: true, alreadyStarted: false, activatedAt: now, expiresAt: record.expiresAt });
      } catch (e) {
        return json({ error: "trial_unavailable" }, 500);
      }
    }

    // A trial object is intentionally strict: malformed state must never
    // silently restart a device's one-time entitlement.
    if (
      current.kind !== "trial" ||
      current.device !== device ||
      !Array.isArray(current.devices) ||
      current.devices.length !== 1 ||
      current.devices[0] !== device ||
      current.maxDevices !== 1 ||
      current.expiresInDays !== 7 ||
      !current.activatedAt ||
      !current.expiresAt
    ) {
      return json({ error: "trial_state_invalid" }, 500);
    }
    if (now >= current.expiresAt) return json({ error: "trial_expired" }, 410);

    try {
      const token = await this.signToken(current, code, device);
      return json({
        token,
        newlyStarted: false,
        alreadyStarted: true,
        activatedAt: current.activatedAt,
        expiresAt: current.expiresAt,
      });
    } catch (e) {
      return json({ error: "trial_unavailable" }, 500);
    }
  }

  async unbind(code, device) {
    const record = await this.loadRecord(code);
    if (!record) return json({ error: "invalid_code" }, 404);
    const devices = Array.isArray(record.devices) ? record.devices : [];
    if (!devices.includes(device)) return json({ error: "device_not_bound" }, 404);
    record.devices = devices.filter((item) => item !== device);
    if (record.devices.length === 0) record.status = "unused";
    await this.saveRecord(record);
    return json({ ok: true });
  }

  async initialize(code, record) {
    const existing = await this.state.storage.get("record");
    if (!existing) await this.saveRecord({ ...record, devices: [] });
    return json({ ok: true });
  }

  async fetch(request) {
    const body = await readBody(request);
    const code = (body.code || "").toString().trim().toUpperCase();
    const device = (body.device || "").toString().trim();
    const action = body.action;

    // DO requests can interleave at ordinary await points. Keep the complete
    // state transition together, including legacy KV bootstrap and signing,
    // so no request can make a decision from stale canonical state. The guard
    // is scoped to this object's code identity; other codes remain independent.
    return this.state.blockConcurrencyWhile(async () => {
      if (action === "initialize") return this.initialize(code, body.record);
      if (!code || !device) return json({ error: "bad_request" }, 400);
      if (action === "start_trial") return this.startTrial(code, device);
      if (action === "redeem") return this.redeem(code, device);
      if (action === "unbind") return this.unbind(code, device);
      return json({ error: "bad_request" }, 400);
    });
  }
}
