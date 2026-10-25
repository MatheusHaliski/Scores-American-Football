import { createHash, timingSafeEqual } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { readGateConfig } from "@/lib/devgate/config";
import { lockoutStore } from "@/lib/devgate/lockout";
import { GATE_COOKIE, gateEnabled, gateTtlSeconds, safeNext, signGateToken } from "@/lib/devgate/token";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// Tentativas de PIN por conta Google verificada (e por IP como segunda chave): 5 erros → 15 min bloqueado.
// O contador fica fora do processo (Redis em produção, ver lib/devgate/lockout.ts): vale para todas as instâncias.

/** Lista de acesso: e-mails exatos ou domínios ("@velion.com.br"). */
function allowed(email: string, list: string[]): boolean {
  const e = email.toLowerCase();
  return list.some((item) => (item.startsWith("@") ? e.endsWith(item) : e === item));
}

function pinMatches(pin: string, expected: Buffer): boolean {
  const got = createHash("sha256").update(pin, "utf8").digest();
  return timingSafeEqual(got, expected);
}

/** Valida o ID token do Google Identity Services (assinatura conferida pelo endpoint tokeninfo do Google). */
async function verifyGoogle(credential: string, clientId: string): Promise<{ email: string } | null> {
  const res = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(credential)}`, { cache: "no-store" });
  if (!res.ok) return null;
  const t = (await res.json()) as { aud?: string; iss?: string; email?: string; email_verified?: string | boolean; exp?: string };
  const issOk = t.iss === "accounts.google.com" || t.iss === "https://accounts.google.com";
  const verified = t.email_verified === true || t.email_verified === "true";
  const fresh = Number(t.exp ?? 0) * 1000 > Date.now();
  return t.aud === clientId && issOk && verified && fresh && t.email ? { email: t.email } : null;
}

function clientIp(req: NextRequest): string {
  // atrás do proxy da hospedagem: o primeiro endereço é o do cliente (a hospedagem sobrescreve o cabeçalho)
  return req.headers.get("x-real-ip") ?? req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "unknown";
}

function audit(outcome: string, email?: string, extra?: Record<string, unknown>): void {
  // sem PIN nem e-mail completo no log: só o domínio
  console.info(JSON.stringify({ event: "dev_gate", outcome, domain: email?.split("@")[1] ?? null, ...extra, at: new Date().toISOString() }));
}

export async function POST(req: NextRequest) {
  if (!gateEnabled()) return NextResponse.json({ code: "GATE_DISABLED" }, { status: 404 });
  // NEXT_PUBLIC_* lido literalmente: o Next embute o valor do build mesmo sem a variável no runtime
  const cfg = readGateConfig({ ...process.env, NEXT_PUBLIC_GOOGLE_CLIENT_ID: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID });
  if (!cfg.ok) {
    audit("not_configured", undefined, { invalid: cfg.invalid });
    return NextResponse.json({ code: "GATE_NOT_CONFIGURED" }, { status: 503 });
  }
  const { secret, clientId, pinSha256, allowList, redisUrl } = cfg.config;
  let body: { credential?: unknown; pin?: unknown; next?: unknown };
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ code: "BAD_REQUEST" }, { status: 400 });
  }
  const credential = typeof body.credential === "string" ? body.credential : "";
  const pin = typeof body.pin === "string" ? body.pin : "";
  if (!credential) return NextResponse.json({ code: "GOOGLE_REQUIRED" }, { status: 400 });
  if (!/^\d{4,12}$/.test(pin)) return NextResponse.json({ code: "PIN_FORMAT" }, { status: 400 });

  const store = lockoutStore(redisUrl);
  const ipKey = `ip:${clientIp(req)}`;
  try {
    if (await store.locked(ipKey)) return NextResponse.json({ code: "LOCKED" }, { status: 429 });

    const google = await verifyGoogle(credential, clientId).catch(() => null);
    if (!google) {
      await store.fail(ipKey);
      audit("google_invalid");
      return NextResponse.json({ code: "GOOGLE_INVALID" }, { status: 401 });
    }
    const accountKey = `acct:${google.email.toLowerCase()}`;
    if (!allowed(google.email, allowList)) {
      await store.fail(ipKey);
      audit("not_allowed", google.email);
      return NextResponse.json({ code: "NOT_ALLOWED" }, { status: 403 });
    }
    // confere o limite e já consome a tentativa numa operação só (paralelas não furam o limite)
    if (!(await store.reserve([accountKey, ipKey]))) return NextResponse.json({ code: "LOCKED" }, { status: 429 });
    if (!pinMatches(pin, pinSha256)) {
      audit("pin_invalid", google.email);
      return NextResponse.json({ code: "PIN_INVALID" }, { status: 401 });
    }
    await store.release(accountKey, ipKey);

    const now = Date.now();
    const ttl = gateTtlSeconds();
    const token = await signGateToken({ sub: google.email, exp: Math.floor(now / 1000) + ttl }, secret);
    const res = NextResponse.json({ ok: true, next: safeNext(typeof body.next === "string" ? body.next : null) });
    res.cookies.set(GATE_COOKIE, token, { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge: ttl });
    audit("granted", google.email);
    return res;
  } catch (err) {
    // contador indisponível: falha fechada (sem ele não há limite de tentativas)
    audit("lockout_store_unavailable", undefined, { error: String(err) });
    return NextResponse.json({ code: "GATE_UNAVAILABLE" }, { status: 503 });
  }
}

/** Sai do gate (apaga o cookie). */
export async function DELETE() {
  const res = NextResponse.json({ ok: true });
  res.cookies.set(GATE_COOKIE, "", { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge: 0 });
  return res;
}
