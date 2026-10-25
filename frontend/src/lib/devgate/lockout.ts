/**
 * DevAuthGate — contador de tentativas erradas por conta Google e por IP, compartilhado entre instâncias.
 *
 * Em produção vive no Redis (DEV_GATE_REDIS_URL): cada réplica do Next.js e cada reinício enxergam o mesmo
 * contador, então são 5 erros por conta/IP no total, não 5 por processo. Incremento e expiração andam juntos
 * num script Lua (atômico). Fora de produção, sem Redis, cai num Map em memória (um processo só).
 *
 * Fluxo da rota: `locked(ip)` barra cedo; `fail(ip)` conta Google/lista inválidos; antes de comparar o PIN,
 * `reserve([conta, ip])` confere e já consome uma tentativa de uma vez só — requisições paralelas não passam
 * todas pela checagem antes de o contador subir; PIN certo → `release` devolve a tentativa.
 */
import { createHash } from "node:crypto";
import { createClient } from "redis";

export const MAX_FAILURES = 5;
export const LOCK_MS = 15 * 60 * 1000;

export interface LockoutStore {
  /** chave já no limite? */
  locked(key: string): Promise<boolean>;
  /** conta um erro (janela de LOCK_MS renovada a cada erro) */
  fail(key: string): Promise<void>;
  /** atômico: se nenhuma chave está no limite, conta uma tentativa em todas e devolve true; senão, false */
  reserve(keys: string[]): Promise<boolean>;
  /** PIN certo: zera a conta e devolve a tentativa reservada do IP */
  release(accountKey: string, ipKey: string): Promise<void>;
}

// ───────────────────────────── Redis (produção) ─────────────────────────────

// "{devgate}" é hash tag: todas as chaves no mesmo slot, para o script de várias chaves funcionar em cluster.
// O identificador (e-mail/IP) entra com hash: nada de dado pessoal em texto no Redis.
const redisKey = (key: string) => `scores:{devgate}:fail:${createHash("sha256").update(key).digest("hex")}`;

const FAIL_LUA = `
local c = redis.call('INCR', KEYS[1])
redis.call('PEXPIRE', KEYS[1], ARGV[1])
return c`;

const RESERVE_LUA = `
for i = 1, #KEYS do
  if tonumber(redis.call('GET', KEYS[i]) or '0') >= tonumber(ARGV[2]) then return 0 end
end
for i = 1, #KEYS do
  redis.call('INCR', KEYS[i])
  redis.call('PEXPIRE', KEYS[i], ARGV[1])
end
return 1`;

const RELEASE_LUA = `
redis.call('DEL', KEYS[1])
if redis.call('DECR', KEYS[2]) <= 0 then redis.call('DEL', KEYS[2]) end
return 1`;

function newClient(url: string) {
  const client = createClient({ url, disableOfflineQueue: true, socket: { connectTimeout: 3000 } });
  // sem ouvinte de "error" o node derruba o processo; a falha aparece na chamada (rota responde 503)
  client.on("error", (err: unknown) => console.warn(JSON.stringify({ event: "dev_gate_redis", error: String(err) })));
  return client;
}

type Client = ReturnType<typeof newClient>;
const clients = new Map<string, Promise<Client>>();

/** Uma conexão por URL, reaproveitada entre requisições; conexão que falhou é descartada e refeita na próxima. */
function connect(url: string): Promise<Client> {
  const existing = clients.get(url);
  if (existing) return existing;
  const pending = newClient(url)
    .connect()
    .catch((err: unknown) => {
      clients.delete(url);
      throw err;
    });
  clients.set(url, pending);
  return pending;
}

export function redisLockoutStore(url: string): LockoutStore {
  const ms = String(LOCK_MS);
  return {
    async locked(key) {
      const v = await (await connect(url)).get(redisKey(key));
      return Number(v ?? 0) >= MAX_FAILURES;
    },
    async fail(key) {
      await (await connect(url)).eval(FAIL_LUA, { keys: [redisKey(key)], arguments: [ms] });
    },
    async reserve(keys) {
      const r = await (await connect(url)).eval(RESERVE_LUA, { keys: keys.map(redisKey), arguments: [ms, String(MAX_FAILURES)] });
      return Number(r) === 1;
    },
    async release(accountKey, ipKey) {
      await (await connect(url)).eval(RELEASE_LUA, { keys: [redisKey(accountKey), redisKey(ipKey)] });
    },
  };
}

// ───────────────────────────── memória (dev/testes) ─────────────────────────────

export function memoryLockoutStore(now: () => number = Date.now): LockoutStore {
  const entries = new Map<string, { count: number; until: number }>();
  const count = (key: string) => {
    const e = entries.get(key);
    return e && e.until > now() ? e.count : 0;
  };
  const bump = (key: string) => entries.set(key, { count: count(key) + 1, until: now() + LOCK_MS });
  return {
    async locked(key) {
      return count(key) >= MAX_FAILURES;
    },
    async fail(key) {
      bump(key);
    },
    async reserve(keys) {
      if (keys.some((k) => count(k) >= MAX_FAILURES)) return false;
      keys.forEach(bump);
      return true;
    },
    async release(accountKey, ipKey) {
      entries.delete(accountKey);
      const e = entries.get(ipKey);
      if (e && e.until > now() && e.count > 1) entries.set(ipKey, { ...e, count: e.count - 1 });
      else entries.delete(ipKey);
    },
  };
}

let memory: LockoutStore | null = null;

/** Redis quando há URL; sem URL, memória (readGateConfig já recusa produção sem DEV_GATE_REDIS_URL). */
export function lockoutStore(redisUrl: string | undefined): LockoutStore {
  if (redisUrl) return redisLockoutStore(redisUrl);
  return (memory ??= memoryLockoutStore());
}

/** Fecha as conexões abertas (testes / encerramento). */
export async function closeLockoutConnections(): Promise<void> {
  const pending = [...clients.values()];
  clients.clear();
  await Promise.all(pending.map((p) => p.then((c) => c.close()).catch(() => undefined)));
}
