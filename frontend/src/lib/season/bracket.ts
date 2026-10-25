/**
 * Chave dos playoffs na tela (RF66), sem React: monta, a partir do `GET /playoffs/bracket`, as colunas de cada conferência (uma por
 * rodada, com os jogos em ordem de semente e as folgas) e a final à parte; escolhe a rodada que abre no celular (a da semana atual) e
 * diz quem venceu cada jogo. As rodadas planejadas que ainda não existem aparecem vazias, com "a definir".
 */
import type { Bracket, BracketGame, BracketRound, BracketTeam } from "@/lib/api/season-types";
import { PLAYOFF_ROUNDS } from "@/lib/api/season-types";

/** Semana de playoff? (o tipo da semana do calendário: WILD_CARD…FINAL) */
export function isPlayoffKind(kind: string | null | undefined): boolean {
  return !!kind && (PLAYOFF_ROUNDS as readonly string[]).includes(kind);
}

export interface BracketColumn {
  round: BracketRound;
  games: BracketGame[];
  byes: BracketTeam[];
}

export interface ConferenceBracket {
  conference: string;
  columns: BracketColumn[];
}

export interface BracketView {
  conferences: ConferenceBracket[];
  /** a rodada final (jogo sem conferência), ou null se a liga não tem final entre conferências */
  final: BracketColumn | null;
  champion: Bracket["champion"];
}

const bestSeed = (g: BracketGame) => Math.min(g.home.seed || 99, g.away.seed || 99);

/** Conferência de um time da chave pela semente/franquia (folgas não têm jogo, então vêm pelo `club.conference`). */
const confOfTeam = (t: BracketTeam) => t.club.conference ?? "";

/**
 * Colunas por conferência: as conferências aparecem na ordem em que surgem nos jogos (a do servidor); cada coluna tem os jogos da
 * conferência naquela rodada, em ordem da melhor semente, e as folgas dela. A rodada FINAL (jogos sem conferência) vai para `final`.
 */
export function bracketView(b: Bracket): BracketView {
  const confs: string[] = [];
  const add = (c: string | null) => {
    if (c && !confs.includes(c)) confs.push(c);
  };
  for (const r of b.rounds) {
    for (const g of r.games) add(g.conference);
    for (const t of r.byes) add(confOfTeam(t));
  }
  confs.sort();
  const finalRound = b.rounds.find((r) => r.kind === "FINAL") ?? null;
  const confRounds = b.rounds.filter((r) => r !== finalRound);
  return {
    conferences: confs.map((conference) => ({
      conference,
      columns: confRounds.map((round) => ({
        round,
        games: round.games.filter((g) => g.conference === conference).sort((a, z) => bestSeed(a) - bestSeed(z)),
        byes: round.byes.filter((t) => confOfTeam(t) === conference).sort((a, z) => a.seed - z.seed),
      })),
    })),
    final: finalRound ? { round: finalRound, games: [...finalRound.games], byes: [] } : null,
    champion: b.champion,
  };
}

/** Rodada que abre no celular: a primeira ainda não encerrada que já tem jogos (a da semana atual); tudo encerrado, a última; nada, a primeira. */
export function initialRoundIndex(b: Bracket): number {
  const open = b.rounds.find((r) => r.status !== "COMPLETED" && r.games.length > 0);
  if (open) return open.index;
  const done = b.rounds.filter((r) => r.status === "COMPLETED");
  return (done[done.length - 1] ?? b.rounds[0])?.index ?? 0;
}

/** Lado vencedor de um jogo encerrado ("home" | "away"), ou null. */
export function winnerSide(g: BracketGame): "home" | "away" | null {
  if (!g.winnerClubId) return null;
  return g.winnerClubId === g.home.club.id ? "home" : g.winnerClubId === g.away.club.id ? "away" : null;
}

/** Link do relatório do jogo (rota do 6b): só para jogo do calendário já encerrado. */
export function reportHref(careerId: string, g: BracketGame): string | null {
  return g.fixtureId && g.homeScore != null && g.awayScore != null ? `/c/${careerId}/match/${g.fixtureId}/report` : null;
}
