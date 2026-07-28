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
| Activation codes + device bindings | KV namespace `APPAUSE_CODES` | n/a |

A fork of the open-source app gets the *verifier*, not the *printer*. Device
binding (`device` claim = SHA-256 of the device's Android Keystore public key)
stops a user from copying one token across phones. Daily app use stays fully
offline — the network is touched only for the one-time activation handshake.

## Files

- `src/jwt.mjs` — shared RS256 signing (imported by both the worker and the test)
- `src/index.js` — the worker (`/api/redeem`, `/api/unbind`, `/admin/gencode`, `/admin/unbind`)
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
npm test              # signs a token with the shared code and verifies it (RSA-SHA256)
wrangler dev          # run the worker locally; pair with the app pointed at http://localhost:8787
```

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
- Replace the DEV key pair (in `C:/Users/Shmily/Appause_Keys/`, outside this repo)
  with a fresh production pair before any public release.

## Payment / storefront (out of scope here)

This worker only *issues* tokens. To sell codes, add a storefront
(Stripe / WeChat Pay / Alipay / 酷安内购) that, on successful payment, calls
`/admin/gencode` and emails the code to the buyer. Keep the payment surface
separate from this worker.
