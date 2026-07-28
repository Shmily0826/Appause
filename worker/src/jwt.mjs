/**
 * jwt.mjs — minimal RS256 (RSASSA-PKCS1-v1_5 + SHA-256) JWT signing.
 *
 * This module is SHARED between the Cloudflare Worker (src/index.js) and the
 * local test harness (test/sign-verify.mjs). Keeping it in one place guarantees
 * the test exercises the exact signing the Worker uses.
 *
 * Why RSASSA-PKCS1-v1_5 / SHA-256?
 * The Android client verifies with Java's `Signature.getInstance("SHA256withRSA")`,
 * which is precisely RSASSA-PKCS1-v1_5 with SHA-256. The base64url encoding here
 * (no padding) also matches the client's tolerant decoder.
 *
 * This file contains NO secret material — only signing logic. The private key
 * is supplied at runtime from a Cloudflare secret.
 */

const textEncoder = new TextEncoder();

/** base64url-encode raw bytes, without '=' padding (client tolerates this). */
export function b64urlFromBytes(bytes) {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

/** base64url-encode a UTF-8 string. */
export function b64urlFromString(str) {
  return b64urlFromBytes(textEncoder.encode(str));
}

/**
 * Import an RSA private key from a PKCS#8 PEM string into a Web Crypto CryptoKey
 * usable for signing. The Cloudflare secret APPAUSE_PRIVATE_KEY holds exactly
 * this PKCS#8 PEM (produced by `npm run genkeys`).
 */
export async function importPrivateKeyPem(pem) {
  const clean = pem
    .replace(/-----BEGIN[^-]*-----/g, "")
    .replace(/-----END[^-]*-----/g, "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(clean), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
}

/**
 * Sign a JWT.
 * @param payload object with at least { tier: "pro", device: <fingerprint> }.
 *   Optional: iat, exp (seconds since epoch), jti.
 * @param privateKey CryptoKey (signing) from importPrivateKeyPem.
 * @returns the compact JWT string "header.payload.signature".
 */
export async function signJwt(payload, privateKey) {
  const header = { alg: "RS256", typ: "JWT" };
  const signingInput =
    b64urlFromString(JSON.stringify(header)) +
    "." +
    b64urlFromString(JSON.stringify(payload));
  const signature = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    privateKey,
    textEncoder.encode(signingInput)
  );
  return signingInput + "." + b64urlFromBytes(new Uint8Array(signature));
}
