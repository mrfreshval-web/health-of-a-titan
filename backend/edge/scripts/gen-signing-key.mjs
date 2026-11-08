#!/usr/bin/env node
// Generates AUTH_SIGNING_KEY for hot-edge (A65 §3.1, §8): an Ed25519 private JWK with a kid.
// The key is printed to stdout only. Pipe it straight into a secret, never into a file in git:
//
//   node scripts/gen-signing-key.mjs | gh secret set AUTH_SIGNING_KEY
//   # or: node scripts/gen-signing-key.mjs | npx wrangler secret put AUTH_SIGNING_KEY
//
// Rotation: move the old value to AUTH_SIGNING_KEY_PREVIOUS, set a new AUTH_SIGNING_KEY, and
// remove AUTH_SIGNING_KEY_PREVIOUS after 15 minutes (the lifetime of an access token).

const { privateKey } = await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"]);
const jwk = await crypto.subtle.exportKey("jwk", privateKey);
const kid = `k${new Date().toISOString().slice(0, 10).replace(/-/g, "")}`;
process.stdout.write(JSON.stringify({ kty: "OKP", crv: "Ed25519", d: jwk.d, x: jwk.x, kid }));
