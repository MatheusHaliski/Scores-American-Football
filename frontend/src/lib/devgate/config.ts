/**
 * DevAuthGate — leitura e validação da configuração da rota /api/dev-gate (Node). Todas as variáveis
 * obrigatórias são conferidas aqui: faltando ou malformada qualquer uma, a rota responde GATE_NOT_CONFIGURED
 * (falha fechada) em vez de fingir que o problema é o PIN ou a conta do usuário.
 */
import { usableSecret } from "./token";

export interface GateConfig {
  secret: string;
  clientId: string;
  /** SHA-256 do PIN (32 bytes) */
  pinSha256: Buffer;
  /** e-mails exatos ou domínios ("@velion.com.br"), em minúsculas */
  allowList: string[];
  /** Redis compartilhado do bloqueio; ausente só fora de produção (contador em memória) */
  redisUrl?: string;
}

export type GateConfigResult = { ok: true; config: GateConfig } | { ok: false; invalid: string[] };

export function parseAllowList(raw: string | undefined): string[] {
  return (raw ?? "").split(",").map((s) => s.trim().toLowerCase()).filter(Boolean);
}

/** Só nomes de variáveis em `invalid` (nunca valores): vai para o log de auditoria. */
export function readGateConfig(env: Record<string, string | undefined> = process.env): GateConfigResult {
  const invalid: string[] = [];
  const secret = env.DEV_GATE_SECRET;
  if (!usableSecret(secret)) invalid.push("DEV_GATE_SECRET");
  const clientId = env.NEXT_PUBLIC_GOOGLE_CLIENT_ID?.trim();
  if (!clientId) invalid.push("NEXT_PUBLIC_GOOGLE_CLIENT_ID");
  const pinHex = (env.DEV_GATE_PIN_SHA256 ?? "").trim().toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(pinHex)) invalid.push("DEV_GATE_PIN_SHA256");
  const allowList = parseAllowList(env.DEV_GATE_ALLOWED_EMAILS);
  if (allowList.length === 0) invalid.push("DEV_GATE_ALLOWED_EMAILS");
  const redisUrl = env.DEV_GATE_REDIS_URL?.trim() || undefined;
  if (redisUrl ? !/^rediss?:\/\//.test(redisUrl) : env.NODE_ENV === "production") invalid.push("DEV_GATE_REDIS_URL");

  if (invalid.length > 0 || !usableSecret(secret) || !clientId) return { ok: false, invalid };
  return { ok: true, config: { secret, clientId, pinSha256: Buffer.from(pinHex, "hex"), allowList, redisUrl } };
}
