/**
 * Resumo do analista (D-104) no idioma da tela: o backend manda `summaryCode` e `summaryParams` (códigos das unidades, id do
 * adversário e a chance de vitória) e o front monta o texto por `season:analysis.summary.<CODE>`, com as unidades traduzidas e o nome
 * do adversário tirado dos próximos jogos do próprio relatório. Faltando algo (código novo, adversário fora da lista), vale o resumo
 * que o servidor já traduziu.
 */
import type { Locale } from "@/i18n/config";
import { rawMessage, translateIn } from "@/i18n/runtime";
import type { Analysis } from "@/lib/api/season-types";

export function unitLabel(unit: string, locale: Locale, pseudo = false): string {
  return rawMessage(locale, "season", `analysis.unit.${unit}`) === null ? unit : translateIn(locale, "season", `analysis.unit.${unit}`, undefined, pseudo);
}

export function analysisSummaryText(a: Pick<Analysis, "summaryCode" | "summaryParams" | "summary" | "upcoming">, locale: Locale, pseudo = false): string {
  const key = `analysis.summary.${a.summaryCode}`;
  if (rawMessage(locale, "season", key) === null) return a.summary;
  const p = a.summaryParams;
  const best = typeof p.bestUnit === "string" ? p.bestUnit : null;
  const worst = typeof p.worstUnit === "string" ? p.worstUnit : null;
  if (!best || !worst) return a.summary;
  const needsOpponent = a.summaryCode.startsWith("NEXT_GAME_");
  const opponent = needsOpponent ? a.upcoming.find((o) => o.opponent.id === p.opponentId)?.opponent.name : "";
  const chance = typeof p.winProbability === "number" ? p.winProbability : null;
  if (needsOpponent && (!opponent || chance == null)) return a.summary;
  return translateIn(locale, "season", key, { best: unitLabel(best, locale, pseudo), worst: unitLabel(worst, locale, pseudo), opponent: opponent ?? "", chance: chance ?? 0 }, pseudo);
}
