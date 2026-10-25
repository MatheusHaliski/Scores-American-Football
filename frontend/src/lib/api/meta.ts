/**
 * Identidade do jogo ({@code GET /api/v1/meta}, público). O front confere que fala com a API do futebol americano e mostra a
 * versão da build e as regras padrão das ligas novas (D-4). O adaptador é testado contra a amostra gravada pela API
 * (docs/validation/golden/meta.json, D-5).
 */
import { API_MODE, API_URL, type ApiMode } from "./config";
import { ApiError, NETWORK, problemFromBody } from "./problem";

export const RULESETS = ["PRO", "COLLEGE"] as const;
export type Ruleset = (typeof RULESETS)[number];

export const SPORT = "AMERICAN_FOOTBALL";

/** Resposta crua do backend (MetaController.MetaDto). */
export interface MetaDto {
  name: string;
  sport: string;
  version: string;
  defaultRuleset: string;
  apiVersion: string;
}

export interface GameMeta {
  name: string;
  version: string;
  ruleset: Ruleset;
  apiVersion: string;
}

/** Resposta fora do contrato: outro jogo, outra versão da API ou JSON quebrado. */
export class MetaContractError extends Error {
  constructor(reason: string) {
    super(`meta fora do contrato: ${reason}`);
    this.name = "MetaContractError";
  }
}

const isRuleset = (v: unknown): v is Ruleset => typeof v === "string" && (RULESETS as readonly string[]).includes(v);

export function metaFromDto(raw: unknown): GameMeta {
  if (typeof raw !== "object" || raw === null) throw new MetaContractError("corpo vazio");
  const dto = raw as Partial<MetaDto>;
  if (dto.sport !== SPORT) throw new MetaContractError(`esporte ${String(dto.sport)}`);
  if (dto.apiVersion !== "v1") throw new MetaContractError(`versão da API ${String(dto.apiVersion)}`);
  if (!isRuleset(dto.defaultRuleset)) throw new MetaContractError(`regras ${String(dto.defaultRuleset)}`);
  if (typeof dto.name !== "string" || typeof dto.version !== "string") throw new MetaContractError("nome ou versão ausente");
  return { name: dto.name, version: dto.version, ruleset: dto.defaultRuleset, apiVersion: dto.apiVersion };
}

/** Meta do modo demonstração (sem servidor). */
export const MOCK_META: GameMeta = { name: "Scores: American Football", version: "mock", ruleset: "PRO", apiVersion: "v1" };

export interface FetchMetaOptions {
  mode?: ApiMode;
  baseUrl?: string;
  fetchImpl?: typeof fetch;
  signal?: AbortSignal;
  locale?: string;
}

export async function fetchMeta({ mode = API_MODE, baseUrl = API_URL, fetchImpl = fetch, signal, locale }: FetchMetaOptions = {}): Promise<GameMeta> {
  if (mode === "mock") return MOCK_META;
  let res: Response;
  try {
    res = await fetchImpl(`${baseUrl}/meta`, { signal, headers: { Accept: "application/json", ...(locale ? { "Accept-Language": locale } : {}) } });
  } catch (e) {
    if ((e as Error)?.name === "AbortError") throw e;
    throw new ApiError({ status: 0, code: NETWORK, message: "", details: {}, correlationId: null, locale: null });
  }
  const body: unknown = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(problemFromBody(res.status, body));
  return metaFromDto(body);
}
