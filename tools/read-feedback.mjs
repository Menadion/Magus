// Prints the newest notes from Settings > Send feedback, newest first.
//
//   node tools/read-feedback.mjs        the last 20
//   node tools/read-feedback.mjs 50     the last 50
//
// Needs Firebase's admin key: Firebase console > Project settings > Service accounts >
// Generate new private key, saved in C:\Users\Menadion\.keys (never in this repo). The key can
// read the whole project, locations included; this script only ever asks for "feedback".
// Nothing to install: Node's own crypto signs the login and its fetch does the asking.

import { createSign } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

const KEYS = join(homedir(), ".keys");
const count = Number(process.argv[2]) || 20;

// Firebase names the file "<project>-firebase-adminsdk-<id>.json"; any such file in .keys is it.
const keyFile = readdirSync(KEYS).find((f) => /firebase-adminsdk.*\.json$/.test(f));
if (!keyFile) {
  console.error(`No Firebase admin key in ${KEYS}. See the top of this file for where to get one.`);
  process.exit(1);
}
const key = JSON.parse(readFileSync(join(KEYS, keyFile), "utf8"));

// Google's service-account login: a signed note saying who we are and what we want, traded for a token.
async function token() {
  const now = Math.floor(Date.now() / 1000);
  const part = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const unsigned = part({ alg: "RS256", typ: "JWT" }) + "." + part({
    iss: key.client_email,
    scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 600,
  });
  const signature = createSign("RSA-SHA256").update(unsigned).sign(key.private_key, "base64url");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${signature}`,
    }),
  });
  if (!res.ok) throw new Error(`Login failed: ${res.status} ${await res.text()}`);
  return (await res.json()).access_token;
}

// Firestore's REST answers wrap every value in its type ({stringValue: "..."}); this unwraps them.
function plain(value) {
  if (value == null || "nullValue" in value) return null;
  if ("mapValue" in value) {
    return Object.fromEntries(Object.entries(value.mapValue.fields ?? {}).map(([k, v]) => [k, plain(v)]));
  }
  if ("arrayValue" in value) return (value.arrayValue.values ?? []).map(plain);
  if ("integerValue" in value) return Number(value.integerValue);
  return Object.values(value)[0];
}

const res = await fetch(
  `https://firestore.googleapis.com/v1/projects/${key.project_id}/databases/(default)/documents:runQuery`,
  {
    method: "POST",
    headers: { Authorization: `Bearer ${await token()}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "feedback" }],
        orderBy: [{ field: { fieldPath: "createdAt" }, direction: "DESCENDING" }],
        limit: count,
      },
    }),
  },
);
if (!res.ok) throw new Error(`Reading feedback failed: ${res.status} ${await res.text()}`);

const notes = (await res.json()).filter((row) => row.document).map((row) => plain({ mapValue: row.document }));
if (notes.length === 0) console.log("No feedback yet.");

for (const note of notes) {
  const d = note.diag ?? {};
  const when = note.createdAt ? new Date(note.createdAt).toLocaleString("en-PH") : "time not set yet";
  const phone = [d.brand, d.model].filter(Boolean).join(" ");
  const steps = d.unrestricted === false ? "battery restricted"
    : d.brandStepDone === false ? "brand step not done" : "steps done";
  console.log(`\n${when}  ${note.name ?? "?"} (family ${note.familyCode ?? "?"}, language ${note.language ?? "?"})`);
  console.log(`  ${phone} · Android ${d.android ?? "?"} · Mogar ${d.app ?? "?"} · ${steps}`);
  if (note.settingsWorked) console.log(`  Buttons took them to the right page: ${note.settingsWorked}`);
  if (note.landedOn) console.log(`  Ended up at: ${note.landedOn}`);
  if (note.suggestion) console.log(`  Fix or add: ${note.suggestion}`);
  if (d.lastCrash) console.log(`  Last crash: ${d.lastCrash}`);
}
