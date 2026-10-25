// DevAuthGate — configuração obrigatória e bloqueio de tentativas compartilhado entre instâncias.
// O bloco Redis roda contra um servidor real quando DEV_GATE_TEST_REDIS_URL está definido (senão é pulado).
// Segredo, PIN e e-mails são gerados na execução: nada de valor fixo que pareça credencial.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash, randomBytes, randomInt } from "node:crypto";
import { readFileSync } from "node:fs";
import { readGateConfig } from "../../src/lib/devgate/config.ts";
import { closeLockoutConnections, LOCK_MS, MAX_FAILURES, memoryLockoutStore, redisLockoutStore, type LockoutStore } from "../../src/lib/devgate/lockout.ts";
import { GATE_MATCHER, gateApplies } from "../../src/lib/devgate/matcher.ts";
import { gateEnabled, gateTtlSeconds, safeNext, signGateToken, verifyGateToken } from "../../src/lib/devgate/token.ts";

const PIN = String(randomInt(100000, 999999));
const PIN_SHA = createHash("sha256").update(PIN, "utf8").digest("hex");
const SECRET = randomBytes(32).toString("hex");
const valid = {
  NODE_ENV: "production",
  DEV_GATE_SECRET: SECRET,
  NEXT_PUBLIC_GOOGLE_CLIENT_ID: "client.apps.googleusercontent.com",
  DEV_GATE_PIN_SHA256: PIN_SHA,
  DEV_GATE_ALLOWED_EMAILS: "ana@velion.com.br, @velion.com.br",
  DEV_GATE_REDIS_URL: "rediss://redis.example:6380",
};

test("config completa é aceita", () => {
  const r = readGateConfig(valid);
  assert.ok(r.ok);
  assert.deepEqual(r.config.allowList, ["ana@velion.com.br", "@velion.com.br"]);
  assert.equal(r.config.pinSha256.toString("hex"), PIN_SHA);
});

for (const [name, patch] of [
  ["DEV_GATE_PIN_SHA256", { DEV_GATE_PIN_SHA256: undefined }],
  ["DEV_GATE_PIN_SHA256", { DEV_GATE_PIN_SHA256: PIN }],
  ["DEV_GATE_PIN_SHA256", { DEV_GATE_PIN_SHA256: "z".repeat(64) }],
  ["DEV_GATE_ALLOWED_EMAILS", { DEV_GATE_ALLOWED_EMAILS: undefined }],
  ["DEV_GATE_ALLOWED_EMAILS", { DEV_GATE_ALLOWED_EMAILS: " , ," }],
  ["DEV_GATE_SECRET", { DEV_GATE_SECRET: SECRET.slice(0, 16) }],
  ["NEXT_PUBLIC_GOOGLE_CLIENT_ID", { NEXT_PUBLIC_GOOGLE_CLIENT_ID: "" }],
  ["DEV_GATE_REDIS_URL", { DEV_GATE_REDIS_URL: undefined }],
  ["DEV_GATE_REDIS_URL", { DEV_GATE_REDIS_URL: "http://redis.example" }],
] as const) {
  test(`config inválida → GATE_NOT_CONFIGURED (${name}=${JSON.stringify(Object.values(patch)[0])})`, () => {
    const r = readGateConfig({ ...valid, ...patch });
    assert.equal(r.ok, false);
    assert.deepEqual(!r.ok && r.invalid, [name]);
  });
}

test("fora de produção o Redis é opcional (contador em memória)", () => {
  const r = readGateConfig({ ...valid, NODE_ENV: "development", DEV_GATE_REDIS_URL: undefined });
  assert.ok(r.ok);
  assert.equal(r.config.redisUrl, undefined);
});

async function lockoutContract(make: () => LockoutStore, tag: string) {
  // duas "instâncias" do Next.js: objetos separados sobre o mesmo armazenamento
  const a = make();
  const b = make();
  const acct = `acct:${tag}@velion.com.br`;
  const ip = `ip:${tag}`;
  for (let i = 0; i < MAX_FAILURES; i++) assert.equal(await (i % 2 ? a : b).reserve([acct, ip]), true, `tentativa ${i + 1}`);
  assert.equal(await a.reserve([acct, ip]), false, "6ª tentativa barrada em qualquer instância");
  assert.equal(await b.locked(ip), true);

  // PIN certo libera a conta e devolve a tentativa do IP
  const acct2 = `acct:${tag}-ok@velion.com.br`;
  const ip2 = `ip:${tag}-ok`;
  await a.fail(ip2);
  assert.equal(await a.reserve([acct2, ip2]), true);
  await b.release(acct2, ip2);
  for (let i = 0; i < MAX_FAILURES - 1; i++) assert.equal(await a.reserve([acct2, ip2]), true);
  assert.equal(await b.reserve([acct2, ip2]), false);

  // reservas paralelas não furam o limite
  const acct3 = `acct:${tag}-par@velion.com.br`;
  const results = await Promise.all(Array.from({ length: 20 }, (_, i) => (i % 2 ? a : b).reserve([acct3, `ip:${tag}-par-${i}`])));
  assert.equal(results.filter(Boolean).length, MAX_FAILURES);
}

test("memória: limite compartilhado, liberação e expiração", async () => {
  let now = 1_000_000;
  const store = memoryLockoutStore(() => now);
  await lockoutContract(() => store, "mem");
  now += LOCK_MS + 1;
  assert.equal(await store.locked("ip:mem"), false, "expira depois de LOCK_MS");
});

const redisUrl = process.env.DEV_GATE_TEST_REDIS_URL;
test("Redis: limite vale entre instâncias e expira junto com o contador", { skip: !redisUrl && "DEV_GATE_TEST_REDIS_URL não definido" }, async () => {
  const tag = `t${Date.now()}`;
  try {
    await lockoutContract(() => redisLockoutStore(redisUrl!), tag);
    const { createClient } = await import("redis");
    const c = await createClient({ url: redisUrl }).connect();
    const keys = await c.keys("scores:{devgate}:fail:*");
    assert.ok(keys.length > 0);
    for (const k of keys) {
      const ttl = await c.pTTL(k);
      assert.ok(ttl > 0 && ttl <= LOCK_MS, `${k} sem expiração (${ttl})`);
      assert.ok(!k.includes("@") && !k.includes(tag), "identificador vai com hash");
    }
    await c.del(keys);
    await c.close();
  } finally {
    await closeLockoutConnections();
  }
});

test("cookie do gate: assinatura confere, expira e não aceita outro segredo nem token adulterado", async () => {
  const now = Math.floor(Date.now() / 1000);
  const token = await signGateToken({ sub: "ana@velion.com.br", exp: now + 60 }, SECRET);
  assert.deepEqual(await verifyGateToken(token, SECRET, now), { sub: "ana@velion.com.br", exp: now + 60 });
  assert.equal(await verifyGateToken(token, SECRET, now + 61), null, "expirado");
  assert.equal(await verifyGateToken(token, randomBytes(32).toString("hex"), now), null, "outro segredo");
  assert.equal(await verifyGateToken(`${token}x`, SECRET, now), null, "assinatura adulterada");
  assert.equal(await verifyGateToken(token, SECRET.slice(0, 16), now), null, "segredo curto nunca vale");
});

test("gate liga por padrão só em produção; TTL fica entre 1 h e 7 dias", () => {
  assert.equal(gateEnabled({ NODE_ENV: "production" }), true);
  assert.equal(gateEnabled({ NODE_ENV: "production", DEV_GATE_ENABLED: "false" }), false);
  assert.equal(gateEnabled({ NODE_ENV: "development" }), false);
  assert.equal(gateEnabled({ NODE_ENV: "development", DEV_GATE_ENABLED: "true" }), true);
  assert.equal(gateTtlSeconds({}), 12 * 3600);
  assert.equal(gateTtlSeconds({ DEV_GATE_TTL_HOURS: "0" }), 3600);
  assert.equal(gateTtlSeconds({ DEV_GATE_TTL_HOURS: "9999" }), 168 * 3600);
});

test("next só aceita caminho interno (sem redirecionamento aberto)", () => {
  assert.equal(safeNext("/settings?s=privacy"), "/settings?s=privacy");
  for (const bad of ["//evil.example", "https://evil.example", "/\\evil", "/gate", "/gate?next=/x", "/api/dev-gate", null, ""]) assert.equal(safeNext(bad), "/");
});

test("o matcher do proxy é a cópia testada e deixa de fora gate, rota do gate e estáticos", () => {
  // compara o texto-fonte dos dois literais (o Next exige o literal em proxy.ts; a cópia em matcher.ts é a testada)
  const literal = (file: string, re: RegExp) => readFileSync(new URL(file, import.meta.url), "utf8").match(re)?.[1];
  const noProxy = literal("../../src/proxy.ts", /matcher: \["([^"]+)"\]/);
  const naCopia = literal("../../src/lib/devgate/matcher.ts", /GATE_MATCHER =\s*"([^"]+)"/);
  assert.ok(noProxy && naCopia);
  assert.equal(noProxy, naCopia, "proxy.ts e lib/devgate/matcher.ts precisam ter o mesmo padrão");
  assert.ok(GATE_MATCHER.length > 0);
  for (const p of ["/", "/auth", "/auth/reset", "/settings"]) assert.equal(gateApplies(p), true, p);
  for (const p of ["/gate", "/api/dev-gate", "/_next/static/chunk.js", "/favicon.ico", "/fonte.woff2"]) assert.equal(gateApplies(p), false, p);
});
