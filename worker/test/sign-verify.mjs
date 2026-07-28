/**
 * sign-verify.mjs — proves the Worker's RS256 signing interoperates with the
 * Android client's LicenseVerifier (which uses Java SHA256withRSA / PKCS1 v1.5).
 *
 * It imports the REAL signing code from ../src/jwt.mjs, signs a device-bound
 * token, then verifies the signature with Node's crypto (RSA-SHA256) — the same
 * primitive the app uses. If this passes, the token the Worker issues will be
 * accepted by the app (provided ServerKeys holds the matching public key).
 *
 * Run: npm test
 */

import { generateKeyPairSync, createPublicKey, verify } from "node:crypto";
import { signJwt, importPrivateKeyPem } from "../src/jwt.mjs";

// Node 22 already exposes Web Crypto as the global `crypto`, so jwt.mjs can use
// crypto.subtle directly — same API surface as the Cloudflare Workers runtime.

function b64urlDecode(str) {
  let s = str.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  return Buffer.from(s, "base64");
}

async function main() {
  // 1) Generate an ephemeral key pair (stands in for the production keypair).
  const { privateKey: nodePrivate, publicKey: nodePublic } = generateKeyPairSync(
    "rsa",
    {
      modulusLength: 2048,
      publicKeyEncoding: { type: "spki", format: "pem" },
      privateKeyEncoding: { type: "pkcs8", format: "pem" },
    }
  );

  const cryptoPrivate = await importPrivateKeyPem(nodePrivate);

  // 2) Sign a device-bound Pro token exactly as the Worker would.
  const nowSec = Math.floor(Date.now() / 1000);
  const deviceFingerprint =
    "a1b2c3d4e5f60718293a4b5c6d7e8f90" +
    "1234567890abcdef1234567890abcdef"; // 64 hex chars, like DeviceKeyStore output
  const payload = {
    tier: "pro",
    iat: nowSec,
    jti: "APPAUSE-TEST-TEST",
    device: deviceFingerprint,
  };
  const token = await signJwt(payload, cryptoPrivate);

  // 3) Verify the signature with Node crypto (RSA-SHA256 == SHA256withRSA).
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("token does not have 3 parts");
  const header = JSON.parse(b64urlDecode(parts[0]).toString("utf8"));
  if (header.alg !== "RS256") throw new Error("unexpected alg: " + header.alg);
  const signedPayload = Buffer.from(`${parts[0]}.${parts[1]}`, "utf8");
  const signature = b64urlDecode(parts[2]);
  const pub = createPublicKey(nodePublic);
  const ok = verify("RSA-SHA256", signedPayload, pub, signature);

  if (!ok) throw new Error("signature verification FAILED");

  // 4) Sanity-check the payload claims match what the app expects.
  const decoded = JSON.parse(b64urlDecode(parts[1]).toString("utf8"));
  if (decoded.tier !== "pro") throw new Error("tier claim wrong");
  if (decoded.device !== deviceFingerprint)
    throw new Error("device claim mismatch");

  console.log("OK: RS256 token signed and verified (interop with Android client).");
  console.log("header:", JSON.stringify(header));
  console.log("payload:", JSON.stringify(decoded));
  console.log("\nsample token (would verify against the matching public key):\n");
  console.log(token);
  console.log(
    "\nNOTE: this token was signed by an ephemeral test key, not your production key."
  );
}

main().catch((err) => {
  console.error("TEST FAILED:", err.message);
  process.exit(1);
});
