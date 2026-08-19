/**
 * gencodes-batch.mjs — batch-mint Appause Pro activation codes for the
 * Afdian (domestic / China) 卡密 route.
 *
 * The codes produced here are EXACTLY the format the Worker's /api/redeem
 * expects (see worker/src/index.js `randomCode`): `APPAUSE-XXXX-XXXX` using the
 * unambiguous alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`. No secret material is
 * touched — these are just random codes; the Worker mints the real signed JWT
 * at redeem time using the private key that lives only in the Cloudflare secret.
 *
 * Outputs:
 *   1. codes.batch.json  — Cloudflare KV bulk-import format:
 *        [ { "key": "APPAUSE-XXXX-XXXX",
 *            "value": "{\"status\":\"unused\",\"maxDevices\":3,...}" }, ... ]
 *      Seed it with:  wrangler kv:key bulk put codes.batch.json --binding=APPAUSE_CODES
 *   2. codes.txt         — one code per line, for pasting into Afdian 卡密 pool.
 *
 * Run:  node scripts/gencodes-batch.mjs [count] [notes]
 *   count : how many codes to mint (default 50)
 *   notes : free-text tag stored on each code (default "afdian")
 */

import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
const DEFAULT_MAX_DEVICES = 3;

function seg(n) {
  return Array.from(
    { length: n },
    () => CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)]
  ).join("");
}
function randomCode() {
  return `APPAUSE-${seg(4)}-${seg(4)}`;
}

const count = Math.max(1, parseInt(process.argv[2] || "50", 10) || 50);
const notes = process.argv[3] || "afdian";

const batch = [];
const plain = [];
const seen = new Set();
while (batch.length < count) {
  const code = randomCode();
  if (seen.has(code)) continue; // avoid (astronomically unlikely) dup
  seen.add(code);
  const record = {
    status: "unused",
    maxDevices: DEFAULT_MAX_DEVICES,
    expiresInDays: null, // buyout: never expires
    devices: [],
    createdAt: Date.now(),
    notes,
  };
  batch.push({ key: code, value: JSON.stringify(record) });
  plain.push(code);
}

const __dirname = dirname(fileURLToPath(import.meta.url));
const batchPath = join(__dirname, "..", "codes.batch.json");
const plainPath = join(__dirname, "..", "codes.txt");
writeFileSync(batchPath, JSON.stringify(batch, null, 2));
writeFileSync(plainPath, plain.join("\n") + "\n");

console.log(`Minted ${batch.length} codes (notes="${notes}").`);
console.log(`  KV bulk file : ${batchPath}`);
console.log(`  Afdian 卡密   : ${plainPath}`);
console.log("");
console.log("First 5 codes:");
console.log(plain.slice(0, 5).join("\n"));
console.log("");
console.log("Next steps:");
console.log("  1. Seed KV : wrangler kv:key bulk put codes.batch.json --binding=APPAUSE_CODES");
console.log("  2. Upload codes.txt to Afdian 卡密 pool (售卖 -> 卡密商品).");
console.log("  3. User buys -> Afdian reveals code -> App 设置/Pro -> 输入激活码 -> 联网激活。");
