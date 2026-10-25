/**
 * Classificação na tela (RF16), sem React: a marca de cada linha (folga, campeão de divisão, wild card, eliminado), a linha de corte
 * dos playoffs na tabela da conferência e a ordenação pelas colunas. A ordem oficial é a do servidor (`rank`, cadeia de desempate da
 * D-95); ordenar por outra coluna é só leitura, e o empate na coluna volta para a ordem oficial.
 */
import type { StandingsRow } from "@/lib/api/season-types";

/** Marca da linha: semente com folga, campeão de divisão (as primeiras sementes), wild card ou eliminado. */
export type SeedMark = "BYE" | "DIVISION" | "WILD_CARD" | "ELIMINATED" | null;

/**
 * As primeiras `divisions` sementes são os campeões de divisão (D-96: campeões primeiro, no máximo um por divisão); as `byeSeeds`
 * melhores folgam na primeira rodada; o resto da zona é wild card. Sem semente: eliminado quando o servidor diz, senão nada.
 */
export function seedMark(row: Pick<StandingsRow, "seed" | "eliminated">, divisions: number, byeSeeds: number): SeedMark {
  if (row.seed == null) return row.eliminated ? "ELIMINATED" : null;
  if (row.seed <= byeSeeds) return "BYE";
  if (row.seed <= divisions) return "DIVISION";
  return "WILD_CARD";
}

/** Índice da última linha dentro da zona de playoffs na tabela da conferência (a linha de corte vem depois dela); -1 sem zona. */
export function cutlineIndex(rows: Pick<StandingsRow, "seed">[], seedsPerConference: number): number {
  let last = -1;
  rows.forEach((r, i) => {
    if (r.seed != null && r.seed <= seedsPerConference) last = i;
  });
  return last;
}

export const SORT_KEYS = ["rank", "record", "pct", "pf", "pa", "diff", "streak", "home", "away", "division", "conference"] as const;
export type SortKey = (typeof SORT_KEYS)[number];
export type SortDir = "asc" | "desc";

const pctOf = (r: { wins: number; losses: number; ties: number }) => {
  const g = r.wins + r.losses + r.ties;
  return g ? (r.wins + r.ties / 2) / g : 0;
};

/** Sequência como número: vitórias positivas, derrotas negativas, empate e "—" no meio. */
function streakValue(s: string): number {
  const n = Number(s.slice(1)) || 0;
  return s[0] === "W" ? n : s[0] === "L" ? -n : 0;
}

/** Valor da coluna (maior = melhor, menos em `rank` e `pa`, em que menor é melhor). */
function valueOf(r: StandingsRow, key: SortKey): number {
  switch (key) {
    case "rank":
      return r.rank;
    case "record":
    case "pct":
      return r.winPct;
    case "pf":
      return r.pointsFor;
    case "pa":
      return r.pointsAgainst;
    case "diff":
      return r.pointDiff;
    case "streak":
      return streakValue(r.streak);
    case "home":
      return pctOf(r.home);
    case "away":
      return pctOf(r.away);
    case "division":
      return pctOf(r.division);
    case "conference":
      return pctOf(r.conference);
  }
}

/** Direção natural da coluna: posição e pontos contra crescem; o resto decresce (o melhor em cima). */
export function defaultDir(key: SortKey): SortDir {
  return key === "rank" || key === "pa" ? "asc" : "desc";
}

/** Ordena por uma coluna; empate na coluna fica na ordem oficial (`rank`). Não muda a lista recebida. */
export function sortRows(rows: StandingsRow[], key: SortKey, dir: SortDir = defaultDir(key)): StandingsRow[] {
  const sign = dir === "asc" ? 1 : -1;
  return [...rows].sort((a, b) => sign * (valueOf(a, key) - valueOf(b, key)) || a.rank - b.rank);
}

/** "0,750" → ".750" como nas tabelas da liga profissional (percentual com 3 casas, sem o zero à esquerda). */
export function pctText(winPct: number): string {
  const s = winPct.toFixed(3);
  return s.startsWith("0") ? s.slice(1) : s;
}
