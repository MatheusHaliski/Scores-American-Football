/**
 * DevAuthGate — cookie de acesso assinado (HMAC-SHA256, Web Crypto) usado pelo proxy (Node) e pela
 * rota /api/dev-gate (Node). Sem dependências: só `crypto.subtle`, `TextEncoder` e base64url.
 *
 * Formato: base64url(JSON {sub, exp}) + "." + base64url(HMAC(payload)). `exp` em segundos (epoch).
 */

export const GATE_COOKIE = "scores_dev_gate";
export const GATE_PATH = "/gate";
export const GATE_API = "/api/dev-gate";

export interface GateClaims {
  /** e-mail Google verificado que passou pelo gate */
  sub: string;
  /** expiração (segundos desde epoch) */
  exp: number;
}

/**
 * Gate ligado? Em produção vem ligado por padrão (falha fechada): só `DEV_GATE_ENABLED=false` desliga.
 * Fora de produção (npm run dev, testes) só liga com `DEV_GATE_ENABLED=true`.
 */
export function gateEnabled(env: Record<string, string | undefined> = process.env): boolean {
  const flag = env.DEV_GATE_ENABLED?.trim().toLowerCase();
  if (flag === "true") return true;
  if (flag === "false") return false;
  return env.NODE_ENV === "production";
}

/** Duração da sessão do gate (horas); padrão 12 h, entre 1 h e 7 dias. */
export function gateTtlSeconds(env: Record<string, string | undefined> = process.env): number {
  const hours = Number(env.DEV_GATE_TTL_HOURS ?? 12);
  const safe = Number.isFinite(hours) ? Math.min(Math.max(hours, 1), 168) : 12;
  return Math.round(safe * 3600);
}

/** Só caminhos internos (evita redirecionamento aberto): "/x?y" ok; "//evil", "http://…", "/gate" viram "/". */
export function safeNext(raw: string | null | undefined): string {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/\\")) return "/";
  if (raw === GATE_PATH || raw.startsWith(`${GATE_PATH}?`) || raw.startsWith(GATE_API)) return "/";
  return raw;
}

const enc = new TextEncoder();

function b64url(bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromB64url(s: string): Uint8Array<ArrayBuffer> {
  const pad = s.length % 4 === 0 ? "" : "=".repeat(4 - (s.length % 4));
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/") + pad);
  const out = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}

/** Segredo mínimo de 32 caracteres; sem ele o gate não emite nem aceita cookies (falha fechada). */
export function usableSecret(secret: string | undefined): secret is string {
  return !!secret && secret.length >= 32;
}

export async function signGateToken(claims: GateClaims, secret: string): Promise<string> {
  const payload = b64url(enc.encode(JSON.stringify(claims)));
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", await hmacKey(secret), enc.encode(payload)));
  return `${payload}.${b64url(sig)}`;
}

/** Valida assinatura e validade; devolve as claims ou `null`. `crypto.subtle.verify` compara em tempo constante. */
export async function verifyGateToken(token: string | undefined, secret: string | undefined, nowSeconds = Math.floor(Date.now() / 1000)): Promise<GateClaims | null> {
  if (!token || !usableSecret(secret)) return null;
  const [payload, sig, extra] = token.split(".");
  if (!payload || !sig || extra !== undefined) return null;
  try {
    const ok = await crypto.subtle.verify("HMAC", await hmacKey(secret), fromB64url(sig), enc.encode(payload));
    if (!ok) return null;
    const claims = JSON.parse(new TextDecoder().decode(fromB64url(payload))) as Partial<GateClaims>;
    if (typeof claims.sub !== "string" || typeof claims.exp !== "number" || claims.exp <= nowSeconds) return null;
    return { sub: claims.sub, exp: claims.exp };
  } catch {
    return null;
  }
}
