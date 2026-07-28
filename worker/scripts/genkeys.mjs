/**
 * genkeys.mjs — generate the Appause Pro production RSA key pair.
 *
 * Produces, in memory and printed to stdout:
 *   - a PKCS#8 PEM PRIVATE key  -> set as the Cloudflare secret APPAUSE_PRIVATE_KEY
 *   - a SPKI PEM PUBLIC  key     -> paste into ServerKeys.SERVER_PUBLIC_KEY_PEM
 *
 * The private key is shown ONCE. Copy it into `wrangler secret put
 * APPAUSE_PRIVATE_KEY` immediately and never commit it or store it in the repo.
 *
 * Run: npm run genkeys
 */

import { generateKeyPairSync } from "node:crypto";

const { privateKey, publicKey } = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  publicKeyEncoding: { type: "spki", format: "pem" },
  privateKeyEncoding: { type: "pkcs8", format: "pem" },
});

console.log(
  "\n=== PRIVATE KEY (set as Cloudflare secret: wrangler secret put APPAUSE_PRIVATE_KEY) ===\n"
);
console.log(privateKey);
console.log(
  "=== PUBLIC KEY (paste into app: ServerKeys.SERVER_PUBLIC_KEY_PEM) ===\n"
);
console.log(publicKey);
console.log(
  "\nReminder: after pasting the public key, set ServerKeys.IS_PRODUCTION_KEY = true\n" +
    "and rebuild/release. Never ship the DEV key in a public release.\n"
);
