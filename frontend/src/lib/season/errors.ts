/**
 * O que a tela da temporada e o analista fazem com cada erro (bloco 6c), sem React. O avanço da temporada pode chegar cedo demais
 * (422 SEASON_NOT_FINISHED, com `phase` e `currentWeek`) ou tarde demais, de outra aba ou de um clique duplo (409
 * SEASON_ALREADY_ADVANCED, com a temporada atual): nos dois casos a tela recarrega e explica, sem tratar como falha. O analista tem
 * limite de pedidos (429 RATE_LIMITED, `retryAfterSeconds`): a tela pede para aguardar e trava o botão até lá; antes do primeiro
 * relatório, 404 ANALYSIS_NOT_FOUND vira o estado vazio com o botão de gerar.
 */
import { ApiError } from "@/lib/api/problem";

export type SeasonReaction =
  | { kind: "notFinished"; reload: true; phase: string | null; week: number | null }
  | { kind: "alreadyAdvanced"; reload: true; seasonNumber: number | null }
  | { kind: "rateLimited"; reload: false; retryAfterSeconds: number }
  | { kind: "noAnalysis"; reload: false }
  | { kind: "error"; reload: false };

/** Espera padrão quando o 429 não diz quanto (a janela do limite do analista é de 1 minuto). */
export const DEFAULT_RETRY_SECONDS = 60;

const numOrNull = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
const strOrNull = (v: unknown): string | null => (typeof v === "string" && v.length > 0 ? v : null);

export function seasonReaction(err: unknown): SeasonReaction {
  if (!(err instanceof ApiError)) return { kind: "error", reload: false };
  const d = err.problem.details;
  if (err.code === "SEASON_NOT_FINISHED") return { kind: "notFinished", reload: true, phase: strOrNull(d.phase), week: numOrNull(d.currentWeek) };
  if (err.code === "SEASON_ALREADY_ADVANCED") return { kind: "alreadyAdvanced", reload: true, seasonNumber: numOrNull(d.currentSeasonNumber) };
  if (err.status === 429 || err.code === "RATE_LIMITED") return { kind: "rateLimited", reload: false, retryAfterSeconds: Math.max(1, Math.ceil(err.retryAfterSeconds ?? DEFAULT_RETRY_SECONDS)) };
  if (err.code === "ANALYSIS_NOT_FOUND") return { kind: "noAnalysis", reload: false };
  return { kind: "error", reload: false };
}
