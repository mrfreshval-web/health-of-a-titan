import { describe, expect, it } from "vitest";
import { b64urlDecode, b64urlEncode, ConfigError, deriveKeys, open, seal, timingSafeEqual } from "../src/crypto";
import { issueState, STATE_TTL_SECONDS, verifyState } from "../src/oauth-state";
import { newSessionToken, sessionUserId } from "../src/session";
import { pkcePair, TEST_KEY } from "./helpers";

const OTHER_KEY = btoa(String.fromCharCode(...Array.from({ length: 32 }, (_, i) => 255 - i)));

describe("TOKEN_ENC_KEY", () => {
  it("fails closed when missing, not base64 or not 32 bytes", async () => {
    await expect(deriveKeys(undefined)).rejects.toBeInstanceOf(ConfigError);
    await expect(deriveKeys("%%%")).rejects.toBeInstanceOf(ConfigError);
    await expect(deriveKeys(btoa("too short"))).rejects.toBeInstanceOf(ConfigError);
  });

  it("derives separate keys per purpose", async () => {
    const keys = await deriveKeys(TEST_KEY);
    expect(keys.tokenAead.algorithm.name).toBe("AES-GCM");
    expect(keys.stateMac.algorithm.name).toBe("HMAC");
    expect(keys.tokenAead.extractable).toBe(false);
  });
});

describe("AES-GCM sealing", () => {
  it("roundtrips and never contains the plaintext", async () => {
    const { tokenAead } = await deriveKeys(TEST_KEY);
    const sealed = await seal(tokenAead, "refresh-token-value", "aad");
    expect(sealed.startsWith("v1.")).toBe(true);
    expect(sealed).not.toContain("refresh-token-value");
    expect(await open(tokenAead, sealed, "aad")).toBe("refresh-token-value");
  });

  it("uses a fresh IV every time", async () => {
    const { tokenAead } = await deriveKeys(TEST_KEY);
    const a = await seal(tokenAead, "same", "aad");
    const b = await seal(tokenAead, "same", "aad");
    expect(a).not.toBe(b);
    expect(a.split(".")[1]).not.toBe(b.split(".")[1]);
  });

  it("rejects a wrong AAD, a wrong key and tampering", async () => {
    const { tokenAead } = await deriveKeys(TEST_KEY);
    const other = (await deriveKeys(OTHER_KEY)).tokenAead;
    const sealed = await seal(tokenAead, "secret", "user:1|refresh");
    await expect(open(tokenAead, sealed, "user:2|refresh")).rejects.toThrow();
    await expect(open(other, sealed, "user:1|refresh")).rejects.toThrow();
    const [v, iv, ct] = sealed.split(".") as [string, string, string];
    const bytes = b64urlDecode(ct);
    bytes[0] = bytes[0]! ^ 1;
    await expect(open(tokenAead, `${v}.${iv}.${b64urlEncode(bytes)}`, "user:1|refresh")).rejects.toThrow();
    await expect(open(tokenAead, `v2.${iv}.${ct}`, "user:1|refresh")).rejects.toThrow();
  });
});

describe("helpers", () => {
  it("base64url roundtrip", () => {
    const bytes = new Uint8Array([0, 255, 62, 63, 1, 2, 3]);
    expect(b64urlDecode(b64urlEncode(bytes))).toEqual(bytes);
    expect(b64urlEncode(bytes)).not.toMatch(/[+/=]/);
  });

  it("timingSafeEqual", () => {
    expect(timingSafeEqual("abc", "abc")).toBe(true);
    expect(timingSafeEqual("abc", "abd")).toBe(false);
    expect(timingSafeEqual("abc", "abcd")).toBe(false);
  });

  it("session tokens route by user id and are unguessable", () => {
    const t = newSessionToken("4242");
    expect(sessionUserId(t)).toBe("4242");
    expect(newSessionToken("4242")).not.toBe(t);
    expect(sessionUserId("hs1.4242.short")).toBeNull();
    expect(sessionUserId("whoop-access-token")).toBeNull();
    expect(sessionUserId(null)).toBeNull();
  });
});

describe("signed OAuth state (Worker-side PKCE)", () => {
  it("accepts the right verifier within 10 minutes", async () => {
    const { stateMac } = await deriveKeys(TEST_KEY);
    const { verifier, challenge } = await pkcePair();
    const state = await issueState(stateMac, challenge, 1_000);
    expect(state.length).toBeGreaterThanOrEqual(8);
    expect(await verifyState(stateMac, state, verifier, 1_000 + STATE_TTL_SECONDS)).toBe("ok");
  });

  it("rejects another verifier, an expired state, tampering and garbage", async () => {
    const { stateMac } = await deriveKeys(TEST_KEY);
    const { challenge } = await pkcePair();
    const other = await pkcePair();
    const state = await issueState(stateMac, challenge, 1_000);
    expect(await verifyState(stateMac, state, other.verifier, 1_000)).toBe("bad_signature");
    const own = await pkcePair();
    const s2 = await issueState(stateMac, own.challenge, 1_000);
    expect(await verifyState(stateMac, s2, own.verifier, 1_000 + STATE_TTL_SECONDS + 1)).toBe("expired");
    const [payload, mac] = s2.split(".") as [string, string];
    const p = b64urlDecode(payload);
    p[0] = p[0]! ^ 0xff; // try to push the expiry
    expect(await verifyState(stateMac, `${b64urlEncode(p)}.${mac}`, own.verifier, 1_000)).toBe("bad_signature");
    expect(await verifyState(stateMac, "nope", own.verifier, 1_000)).toBe("malformed");
    expect(await verifyState(stateMac, s2, "short", 1_000)).toBe("malformed");
    const otherKey = (await deriveKeys(OTHER_KEY)).stateMac;
    expect(await verifyState(otherKey, s2, own.verifier, 1_000)).toBe("bad_signature");
  });
});
