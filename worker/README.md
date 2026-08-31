# Appause Pro Activation Worker (Plan B)

A tiny [Cloudflare Worker](https://workers.cloudflare.com/) that issues
**device-bound** Appause Pro license tokens (JWT, RS256). It is the server half
of the "open source but paid" model: the app is MIT-licensed and forkable, but
Pro can only be unlocked with a token signed by **this** worker's private key —
which never leaves Cloudflare.

## How the trust model works

| Asset | Where it lives | Can it mint tokens? |
|-------|----------------|---------------------|
| Signing **private** key | Cloudflare secret `APPAUSE_PRIVATE_KEY` | ✅ yes (server only) |
| Verification **public** key | embedded in the app (`ServerKeys.kt`) | ❌ verify only |
| Activation-code binding state | One SQLite-backed Durable Object per code | n/a |
| Legacy activation-code records | KV namespace `APPAUSE_CODES` (lazy bootstrap only) | n/a |

A fork of the open-source app gets the *verifier*, not the *printer*. Device
binding (`device` claim = SHA-256 of the device's Android Keystore public key)
stops a user from copying one token across phones. The app verifies the token
locally and does not perform automatic license checks. The activation record
may include an expiry; the app accepts a token without `exp` and checks the
claim locally when present. If a token expires, the user must explicitly redeem
the code again or import another valid token.

### Why a Durable Object owns activation state

Workers KV is eventually consistent, so a KV read-modify-write can lose a
device binding when two devices redeem the same code concurrently. The Worker
routes every activation mutation (`redeem`, self-unbind, and admin-unbind) to
one SQLite-backed Durable Object whose deterministic identity is derived from
the normalized activation code. Its storage is the single authoritative copy
of `devices`, `maxDevices`, `status`, and expiry metadata. A legacy KV record is
imported lazily on first access; it is not rewritten or treated as a competing
mutable source afterwards.

The guard covers the full state transition, including async key import/signing
and the first legacy-KV bootstrap. This matters because Durable Object handlers
may yield at `await` points; object identity alone does not make an entire
handler atomic. Different activation-code objects can still execute
independently.

## Files

- `src/jwt.mjs` — shared RS256 signing (imported by both the worker and the test)
- `src/index.js` — the worker (`/api/redeem`, `/api/unbind`, `/admin/gencode`, `/admin/unbind`)
- `src/activation-code-do.js` — per-code SQLite Durable Object and state transitions
- `scripts/genkeys.mjs` — generates the production key pair
- `test/sign-verify.mjs` — proves tokens verify against the Android client's verifier

## Endpoints

| Method & path | Body | Purpose |
|---------------|------|---------|
| `POST /api/redeem` | `{ "code": "...", "device": "<fingerprint>" }` | Activate Pro on a device. Returns `{ "token": "..." }`. |
| `POST /api/unbind` | `{ "code": "...", "device": "<fingerprint>" }` | Self-service unbind (before selling a device). |
| `POST /admin/gencode` | header `x-admin-key`; body `{ "maxDevices"? , "expiresInDays"? , "notes"? }` | Mint a new activation code. |
| `POST /admin/unbind` | header `x-admin-key`; body `{ "code", "device" }` | Admin unbind (for lost devices). |

`/api/redeem` enforces `maxDevices` per code (default 3). A device already bound
to a code is re-issued a token idempotently (handles reinstalls on the same
device).

## Deploy

### 1. Install deps
```bash
cd worker
npm install            # installs wrangler
```

### 2. Generate the production key pair
```bash
npm run genkeys
```
Copy the **PRIVATE** key into a secret, and the **PUBLIC** key into the app.

### 3. Set secrets (never commit these)
```bash
wrangler secret put APPAUSE_PRIVATE_KEY   # paste the PKCS#8 private PEM
wrangler secret put ADMIN_KEY             # any long random string
```

### 4. Create the KV namespace
```bash
wrangler kv namespace create APPAUSE_CODES
```
Copy the returned `id` into `wrangler.toml` (`[[kv_namespaces]]` → `id`).

The Durable Object class and its SQLite-backed migration are declared in
`wrangler.toml`. New codes are initialized directly in the Durable Object;
existing KV-only codes remain valid and need no manual migration.

### 5. Deploy
```bash
wrangler deploy
```
Note the worker URL (e.g. `https://appause-pro.<subdomain>.workers.dev`).

### 6. Wire the app to the worker
- In `app/.../data/pro/ServerKeys.kt`, replace `SERVER_PUBLIC_KEY_PEM` with the
  generated **PUBLIC** key, and set `IS_PRODUCTION_KEY = true`.
- In `app/.../data/pro/ProConfig.kt`, set `WORKER_BASE_URL` to your worker URL.
- Rebuild + release.

## Local testing

```bash
npm test              # JWT interop + redeem/unbind concurrency tests
wrangler dev          # run the worker locally; pair with the app pointed at http://localhost:8787
```

The tests use the real Worker handler and Durable Object class with an
in-memory harness. They do not access production KV, secrets, or a deployed
Worker. Run `wrangler deploy --dry-run` before a later deployment to validate
the local bindings and bundle; this task does not deploy anything.

To mint a test code against a local/dev worker:
```bash
curl -X POST https://<your-worker>/admin/gencode \
  -H "x-admin-key: $ADMIN_KEY" \
  -H "content-type: application/json" \
  -d '{"maxDevices":3}'
```

## ⚠️ Security red lines

- **Never** commit the private key or `ADMIN_KEY`. They are Cloudflare secrets.
- **Never** ship the DEV public key (`ServerKeys.IS_PRODUCTION_KEY = false`) in a
  public release — the repo's `dev_token.txt` would then unlock Pro for anyone.
- The server stores only `(code → device fingerprints)`. It never sees the user's
  apps, usage, or identity. Keep it that way.

The v0.5.39 public Release is built with the production verification-key path;
debug-only Pro controls are isolated from Release. Any future release must
repeat that check before publishing. Keep all key material and deployment
configuration outside Git.

## Payment / storefront (out of scope here)

This worker only *issues* tokens. To sell codes, add a storefront
(Stripe / WeChat Pay / Alipay / 酷安内购) that, on successful payment, calls
`/admin/gencode` and emails the code to the buyer. Keep the payment surface
separate from this worker.
