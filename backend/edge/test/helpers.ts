import type { Env } from "../src/env";
import { WhoopVault, type VaultStorage } from "../src/vault";
import { WHOOP_PROFILE_URL, WHOOP_REVOKE_URL, WHOOP_TOKEN_URL } from "../src/whoop";

/** A fixed test key (32 zero-to-31 bytes) — never used outside tests. */
export const TEST_KEY = btoa(String.fromCharCode(...Array.from({ length: 32 }, (_, i) => i)));

export class MemoryStorage implements VaultStorage {
  readonly map = new Map<string, unknown>();
  async get<T>(key: string): Promise<T | undefined> {
    const v = this.map.get(key);
    return v === undefined ? undefined : (structuredClone(v) as T);
  }
  async put<T>(key: string, value: T): Promise<void> {
    this.map.set(key, structuredClone(value));
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

export function makeEnv(overrides: Partial<Env> = {}, whoop = new FakeWhoop(), now: () => number = Date.now) {
  let env: Env;
  const ns = new FakeVaultNamespace(() => env, whoop.fetch, now);
  env = {
    LLM_MODELS: "claude-opus-5-5",
    WHOOP_CLIENT_ID: "test-client",
    WHOOP_CLIENT_SECRET: "test-secret",
    ALLOWED_WHOOP_USER_IDS: "4242",
    TOKEN_ENC_KEY: TEST_KEY,
    WHOOP_REDIRECT_URI: "com.healthofatitan.app://oauth/whoop",
    WHOOP_VAULT: ns as unknown as DurableObjectNamespace,
    ...overrides,
  };
  return { env, whoop, ns };
}

/** RFC 7636 verifier + S256 challenge. */
export async function pkcePair(): Promise<{ verifier: string; challenge: string }> {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  const verifier = btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  const challenge = btoa(String.fromCharCode(...digest)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return { verifier, challenge };
}
