/**
 * "Jogar" da preparação (RF27/RF7) sem React: `POST /weeks/current/play` age sobre a semana atual, mas a preparação mostra o
 * próximo jogo do usuário. Na semana de folga, a primeira chamada só encerra a folga (sem `matchId`); o "Jogar" chama de novo e
 * termina na partida do jogo que a preparação mostrava, devolvendo a folga encerrada para a tela avisar (D-92). Puro, recebe a rota
 * de jogar a semana.
 */
import type { ScoresApi } from "@/lib/api/contract";
import type { WeekBoard } from "@/lib/api/match-types";

export interface PlayOutcome {
  /** painel da última semana jogada */
  board: WeekBoard;
  /** semanas de folga do usuário que este "Jogar" encerrou no caminho */
  byeWeeks: number[];
}

/** Folgas seguidas que um "Jogar" atravessa no máximo (o calendário tem uma por franquia; o limite só evita laço). */
const MAX_BYES = 2;

/** `onBye` avisa cada folga encerrada na hora, para a tela contar mesmo que a chamada seguinte falhe (ex.: 422 DEPTH_CHART_INVALID). */
export async function playToMatch(weeks: Pick<ScoresApi["weeks"], "playCurrent">, careerId: string, onBye?: (week: number) => void): Promise<PlayOutcome> {
  const byeWeeks: number[] = [];
  let board = await weeks.playCurrent(careerId);
  // folga encerrada e a semana avançou: joga a seguinte (a da preparação)
  while (!board.matchId && board.userBye && board.currentWeek > board.week && byeWeeks.length < MAX_BYES) {
    byeWeeks.push(board.week);
    onBye?.(board.week);
    board = await weeks.playCurrent(careerId);
  }
  return { board, byeWeeks };
}
