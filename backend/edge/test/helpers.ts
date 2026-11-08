import { AccountVault, type KvStorage } from "../src/account-store";
import type { Env } from "../src/env";
import { WhoopVault, type VaultStorage } from "../src/vault";
import { WHOOP_PROFILE_URL, WHOOP_REVOKE_URL, WHOOP_TOKEN_URL } from "../src/whoop";

/** A fixed test key (32 zero-to-31 bytes) — never used outside tests. */
export const TEST_KEY = btoa(String.fromCharCode(...Array.from({ length: 32 }, (_, i) => i)));

export class MemoryStorage implements VaultStorage, KvStorage {
  readonly map = new Map<string, unknown>();
  async get<T>(key: string): Promise<T | undefined> {
    const v = this.map.get(key);
    return v === undefined ? undefined : (structuredClone(v) as T);
  }
  async put<T>(key: string, value: T): Promise<void> {
    this.map.set(key, structuredClone(value));
  }
  async delete(key: string): Promise<boolean> {
    return this.map.delete(key);
  }
  async list<T>(options: { prefix: string; limit?: number }): Promise<Map<string, T>> {
    const keys = [...this.map.keys()].filter((k) => k.startsWith(options.prefix)).sort();
    const out = new Map<string, T>();
    for (const k of keys.slice(0, options.limit ?? keys.length)) out.set(k, structuredClone(this.map.get(k)) as T);
    return out;
  }
  async deleteAll(): Promise<void> {
    this.map.clear();
  }
  dump(): string {
    return JSON.stringify([...this.map.entries()]);
  }
}

/**
 * A fake WHOOP: issues rotating tokens, knows one user, records every call. Responses can be
 * delayed to make concurrent refreshes overlap.
 */
export class FakeWhoop {
  userId = "4242";
  tokenCalls: URLSearchParams[] = [];
  revoked: string[] = [];
  profileCalls = 0;
  delayMs = 0;
  /** Next token response override: "invalid_grant", "invalid_client", "down". */
  failNext: "invalid_grant" | "invalid_client" | "down" | null = null;
  private counter = 0;
  validRefresh = new Set<string>();
  validAccess = new Set<string>();

  readonly fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
    if (this.delayMs) await new Promise((r) => setTimeout(r, this.delayMs));
    if (url === WHOOP_TOKEN_URL) {
      const params = new URLSearchParams(String(init?.body ?? ""));
      this.tokenCalls.push(params);
      const fail = this.failNext;
      this.failNext = null;
      if (fail === "down") return new Response("oops", { status: 503 });
      if (fail === "invalid_client") return Response.json({ error: "invalid_client" }, { status: 401 });
      if (params.get("client_secret") !== "test-secret") return Response.json({ error: "invalid_client" }, { status: 401 });
      if (params.get("grant_type") === "refresh_token") {
        const rt = params.get("refresh_token") ?? "";
        if (fail === "invalid_grant" || !this.validRefresh.has(rt)) return Response.json({ error: "invalid_grant" }, { status: 400 });
        this.validRefresh.delete(rt); // rotation
      } else if (params.get("grant_type") === "authorization_code") {
        if (fail === "invalid_grant" || params.get("code") !== "good-code") return Response.json({ error: "invalid_grant" }, { status: 400 });
      }
      const n = ++this.counter;
      const access = `whoop-access-${n}`;
      const refresh = `whoop-refresh-${n}`;
      this.validAccess.add(access);
      this.validRefresh.add(refresh);
      return Response.json({ access_token: access, refresh_token: refresh, expires_in: 3600, scope: "offline read:recovery", token_type: "bearer" });
    }
    const auth = new Headers(init?.headers).get("Authorization") ?? "";
    const token = auth.replace(/^Bearer /, "");
    if (url === WHOOP_PROFILE_URL) {
      this.profileCalls++;
      if (!this.validAccess.has(token)) return new Response(null, { status: 401 });
      return Response.json({ user_id: Number(this.userId), email: "private@example.com", first_name: "Private", last_name: "Person" });
    }
    if (url === WHOOP_REVOKE_URL && init?.method === "DELETE") {
      this.revoked.push(token);
      return new Response(null, { status: 204 });
    }
    return new Response("not found", { status: 404 });
  };
}

/** A fake Durable Object namespace that keeps one WhoopVault (and storage) per name. */
export class FakeVaultNamespace {
  readonly vaults = new Map<string, { vault: WhoopVault; storage: MemoryStorage }>();
  constructor(
    private readonly envRef: () => Env,
    private readonly http: typeof fetch,
    private readonly now: () => number = Date.now,
  ) {}

  idFromName(name: string) {
    return { name, toString: () => name };
  }

  entry(name: string) {
    let e = this.vaults.get(name);
    if (!e) {
      const storage = new MemoryStorage();
      e = { storage, vault: new WhoopVault({ storage }, this.envRef(), { fetch: this.http, now: this.now }) };
      this.vaults.set(name, e);
    }
    return e;
  }

  get(id: { name: string }) {
    return {
      fetch: (input: RequestInfo | URL, init?: RequestInit) => this.entry(id.name).vault.fetch(new Request(input, init)),
    };
  }
}

/** A fake namespace for the single AccountVault object (A65 §4.1). */
export class FakeAccountNamespace {
  readonly storage = new MemoryStorage();
  readonly vault = new AccountVault({ storage: this.storage });
  calls = 0;
  idFromName(name: string) {
    return { name, toString: () => name };
  }
  get(_id: { name: string }) {
    return {
      fetch: (input: RequestInfo | URL, init?: RequestInit) => {
        this.calls++;
        return this.vault.fetch(new Request(input, init));
      },
    };
  }
}

const b64url = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

/** A fresh Ed25519 JWK for AUTH_SIGNING_KEY, generated per test run (no key material in code). */
export async function newSigningJwk(kid: string): Promise<string> {
  const pair = (await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"])) as CryptoKeyPair;
  const jwk = (await crypto.subtle.exportKey("jwk", pair.privateKey)) as JsonWebKey;
  return JSON.stringify({ kty: "OKP", crv: "Ed25519", d: jwk.d, x: jwk.x, kid });
}

export const TEST_SIGNING_KEY = await newSigningJwk("test-1");
export const TEST_GOOGLE_AUD = "test-web-client.apps.googleusercontent.com";

/** The phone's Keystore key (A65 §3.3): EC P-256; signatures in DER like Android's SHA256withECDSA. */
export class FakeDevice {
  private constructor(
    private readonly pair: CryptoKeyPair,
    readonly pub: string,
  ) {}

  static async create(): Promise<FakeDevice> {
    const pair = (await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"])) as CryptoKeyPair;
    const spki = new Uint8Array((await crypto.subtle.exportKey("spki", pair.publicKey)) as ArrayBuffer);
    return new FakeDevice(pair, b64url(spki));
  }

  async sign(message: string, der = true): Promise<string> {
    const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, this.pair.privateKey, new TextEncoder().encode(message)));
    return b64url(der ? rawToDer(raw) : raw);
  }

  async login(binding: string): Promise<{ device_pub: string; device_sig: string; device_name: string }> {
    return { device_pub: this.pub, device_sig: await this.sign(`hot-login|v1|${binding}`), device_name: "Pixel 9 Pro XL" };
  }

  async refresh(refresh: string, ts: number): Promise<{ refresh: string; ts: number; sig: string }> {
    const digest = b64url(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(refresh))));
    return { refresh, ts, sig: await this.sign(`hot-refresh|v1|${digest}|${ts}`) };
  }
}

/** r ‖ s → ASN.1 DER SEQUENCE of two INTEGERs (what Android produces). */
export function rawToDer(raw: Uint8Array): Uint8Array {
  const int = (b: Uint8Array) => {
    let i = 0;
    while (i < b.length - 1 && b[i] === 0) i++;
    let v = b.slice(i);
    if (v[0]! & 0x80) v = Uint8Array.of(0, ...v);
    return Uint8Array.of(0x02, v.length, ...v);
  };
  const r = int(raw.slice(0, 32));
  const s = int(raw.slice(32));
  return Uint8Array.of(0x30, r.length + s.length, ...r, ...s);
}

/** A fake Google: its own RSA key, a JWKS endpoint and ID tokens with chosen claims. */
export class FakeGoogle {
  jwksCalls = 0;
  private constructor(
    private readonly pair: CryptoKeyPair,
    private readonly jwk: JsonWebKey,
    readonly kid: string,
  ) {}

  static async create(kid = "g-1"): Promise<FakeGoogle> {
    const pair = (await crypto.subtle.generateKey(
      { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
      true,
      ["sign", "verify"],
    )) as CryptoKeyPair;
    return new FakeGoogle(pair, (await crypto.subtle.exportKey("jwk", pair.publicKey)) as JsonWebKey, kid);
  }

  readonly fetch = async (input: RequestInfo | URL): Promise<Response> => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
    if (url !== "https://www.googleapis.com/oauth2/v3/certs") return new Response("nope", { status: 404 });
    this.jwksCalls++;
    return Response.json(
      { keys: [{ kty: "RSA", alg: "RS256", use: "sig", kid: this.kid, n: this.jwk.n, e: this.jwk.e }] },
      { headers: { "Cache-Control": "public, max-age=3600" } },
    );
  };

  async idToken(claims: Record<string, unknown>, header: Record<string, unknown> = {}): Promise<string> {
    const enc = new TextEncoder();
    const h = b64url(enc.encode(JSON.stringify({ alg: "RS256", kid: this.kid, typ: "JWT", ...header })));
    const p = b64url(enc.encode(JSON.stringify(claims)));
    const sig = new Uint8Array(await crypto.subtle.sign({ name: "RSASSA-PKCS1-v1_5" }, this.pair.privateKey, enc.encode(`${h}.${p}`)));
    return `${h}.${p}.${b64url(sig)}`;
  }

  /** Claims of a valid token for [nonce] at [nowSeconds]. E-mail is there, as at Google, and must be ignored. */
  claims(nonce: string, nowSeconds: number, sub = "109876543210"): Record<string, unknown> {
    return {
      iss: "https://accounts.google.com",
      aud: TEST_GOOGLE_AUD,
      azp: "android-client.apps.googleusercontent.com",
      sub,
      email: "private@example.com",
      name: "Private Person",
      iat: nowSeconds - 5,
      exp: nowSeconds + 3600,
      nonce,
    };
  }
}

export function makeEnv(overrides: Partial<Env> = {}, whoop = new FakeWhoop(), now: () => number = Date.now) {
  let env: Env;
  const ns = new FakeVaultNamespace(() => env, whoop.fetch, now);
  const accounts = new FakeAccountNamespace();
  env = {
    LLM_MODELS: "claude-opus-5-5",
    WHOOP_CLIENT_ID: "test-client",
    WHOOP_CLIENT_SECRET: "test-secret",
    ALLOWED_WHOOP_USER_IDS: "4242",
    TOKEN_ENC_KEY: TEST_KEY,
    AUTH_SIGNING_KEY: TEST_SIGNING_KEY,
    GOOGLE_WEB_CLIENT_ID: TEST_GOOGLE_AUD,
    WHOOP_REDIRECT_URI: "com.healthofatitan.app://oauth/whoop",
    WHOOP_VAULT: ns as unknown as DurableObjectNamespace,
    ACCOUNT_VAULT: accounts as unknown as DurableObjectNamespace,
    ...overrides,
  };
  return { env, whoop, ns, accounts };
}

/** RFC 7636 verifier + S256 challenge. */
export async function pkcePair(): Promise<{ verifier: string; challenge: string }> {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  const verifier = btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  const challenge = btoa(String.fromCharCode(...digest)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return { verifier, challenge };
}
