// W2 E2E against local workerd (wrangler dev) — full Pro chain on the REAL
// Cloudflare runtime (workerd WebCrypto), closing the "tests are Node-only"
// gap. Verifies every issued JWT with Node RSA-SHA256 == Android verification.
import { createVerify } from "crypto";
import { readFileSync } from "fs";

const BASE = "http://127.0.0.1:8787";
const PUB = readFileSync("local-pub.pem");
let pass = 0, fail = 0;
const ok = (name, cond, extra = "") => {
  console.log(`${cond ? "PASS" : "FAIL"}  ${name} ${extra}`);
  cond ? pass++ : fail++;
};

async function post(path, body, headers = {}) {
  const r = await fetch(BASE + path, {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let j = null;
  try { j = await r.json(); } catch { /* non-json */ }
  return { status: r.status, j };
}

function verifyJwt(token) {
  const [h, p, s] = token.split(".");
  const v = createVerify("RSA-SHA256");
  v.update(`${h}.${p}`);
  const sig = Buffer.from(s, "base64url");
  if (!v.verify(PUB, sig)) return null;
  return JSON.parse(Buffer.from(p, "base64url").toString());
}

// 0. server up
for (let i = 0; i < 30; i++) {
  try { await fetch(BASE + "/api/download-count"); break; } catch { await new Promise(r => setTimeout(r, 1000)); }
}

// 1. admin auth enforced
const noKey = await fetch(BASE + "/admin/stats");
ok("admin/stats without key rejected", noKey.status === 401 || noKey.status === 403, `(${noKey.status})`);

// 2. gencode (admin)
const gen = await post("/admin/gencode", {}, { "x-admin-key": "local-admin-123" });
const code = gen.j?.code;
ok("admin/gencode issues code", gen.status === 200 && /^APPAUSE-/.test(code || ""), code);

// 3. redeem on 3 distinct devices, 4th rejected
const fps = ["FP-A", "FP-B", "FP-C", "FP-D"].map(f => f + Math.random().toString(36).slice(2, 8));
const r1 = await post("/api/redeem", { code, device: fps[0] });
const t1 = r1.j?.token;
ok("redeem device1 -> lifetime token", r1.status === 200 && !!t1);
const c1 = t1 && verifyJwt(t1);
ok("workerd RS256 signature verifies with Node (Android-equivalent)", !!c1);
ok("claims: tier=pro, device bound", c1?.tier === "pro" && c1?.device === fps[0]);
const r2 = await post("/api/redeem", { code, device: fps[1] });
const r3 = await post("/api/redeem", { code, device: fps[2] });
ok("maxDevices=3 slots fill", r2.status === 200 && r3.status === 200);
const r4 = await post("/api/redeem", { code, device: fps[3] });
ok("4th device rejected (403)", r4.status === 403);

// 4. idempotent re-redeem same device
const r1b = await post("/api/redeem", { code, device: fps[0] });
const c1b = r1b.j?.token && verifyJwt(r1b.j.token);
ok("re-redeem idempotent (same exp window)", r1b.status === 200 && c1b?.exp === c1.exp);

// 5. unknown code -> 404
const bad = await post("/api/redeem", { code: "APPAUSE-NOPE-NOPE", device: "X" });
ok("unknown code 404", bad.status === 404);

// 6. trial: start once, repeat is idempotent with same 7-day window
const tfp = "TRIAL-" + Math.random().toString(36).slice(2, 10);
const tr1 = await post("/api/trial/start", { device: tfp });
const tc1 = tr1.j?.token && verifyJwt(tr1.j.token);
const win = tc1 ? tc1.exp - tc1.iat : 0;
ok("trial start -> 7-day pro token", tr1.status === 200 && tc1?.tier === "pro" && win === 604800, `window=${win}`);
const tr2 = await post("/api/trial/start", { device: tfp });
const tc2 = tr2.j?.token && verifyJwt(tr2.j.token);
ok("trial repeat idempotent (same expiresAt, iat may be newer)",
   tr2.status === 200 && tc2?.exp === tc1?.exp && tc2.iat >= tc1.iat);

// 7. unbind frees a slot
const un = await post("/api/unbind", { code, device: fps[1], token: r2.j.token });
ok("self-unbind accepted", un.status === 200, JSON.stringify(un.j ?? {}).slice(0, 80));
const r4b = await post("/api/redeem", { code, device: fps[3] });
ok("freed slot reusable by 4th device", r4b.status === 200 && !!verifyJwt(r4b.j?.token));

// 8. forged token (wrong key) must not verify — client-side fail-closed premise
const forged = t1.split(".")[0] + "." + t1.split(".")[1] + "." + "A".repeat(t1.split(".")[2].length);
ok("tampered signature rejected by Node verify", verifyJwt(forged) === null);

console.log(`\n=== W2 E2E: ${pass} passed, ${fail} failed ===`);
process.exit(fail ? 1 : 0);
