/**
 * Estado ao vivo por descida (RF27/RF62) no front, sem React: mescla incremental do play-by-play (cursor `after`/`lastSeq`, sem
 * duplicar), contagem regressiva da chamada de jogada até `decisionDeadlineAt`, linha de scrimmage e de primeira descida na
 * geometria do campo (`lib/field`) para os dois sentidos de ataque, drive atual e a reação da tela a cada código de erro das rotas
 * de partida (recarregar o estado, avisar que a IA chamou, etc.).
 */
import { ApiError } from "@/lib/api/problem";
import type { DecisionRecord, LiveMatch, MatchSessionStatus, MatchState, PlayEvent } from "@/lib/api/match-types";
import { downMarkers, fieldPosition, FIELD, YARD_M, type FieldPosition } from "@/lib/field/geometry";

/* ───────────────────────── play-by-play ───────────────────────── */

/** Lances guardados na tela (os mais antigos saem primeiro). */
export const MAX_FEED_EVENTS = 1000;

/**
 * Junta lances novos aos que a tela já tem: ordena por `seq`, ignora o que já chegou (o mesmo lance pode vir de `/events` e de
 * `live.lastEvents`) e guarda no máximo `max`. Devolve o mesmo array quando nada muda (evita render à toa).
 */
export function mergeEvents(existing: PlayEvent[], incoming: PlayEvent[], max = MAX_FEED_EVENTS): PlayEvent[] {
  if (!incoming.length) return existing;
  const bySeq = new Map<number, PlayEvent>();
  for (const e of existing) bySeq.set(e.seq, e);
  let changed = false;
  for (const e of incoming) {
    if (!bySeq.has(e.seq)) {
      bySeq.set(e.seq, e);
      changed = true;
    }
  }
  if (!changed) return existing;
  const all = [...bySeq.values()].sort((a, b) => a.seq - b.seq);
  return all.length > max ? all.slice(all.length - max) : all;
}

/** Cursor para o próximo `/events?after=`: o maior `seq` já recebido (0 sem lances). */
export function lastSeqOf(events: PlayEvent[]): number {
  return events.reduce((m, e) => (e.seq > m ? e.seq : m), 0);
}

/**
 * Um estado que chega do servidor ainda vale para a tela? Não quando é de outra partida (a tela já trocou) nem quando a versão do
 * snapshot (`live.version`) é menor que a da tela: uma resposta atrasada (o refresh do fim do prazo que volta depois do avanço)
 * não pode voltar o placar, o relógio e a decisão (D-93). A mesma versão vale (o prazo e os modos de avanço podem mudar sem lance).
 */
export function isFresh(screenMatchId: string | null, current: MatchState | null, next: MatchState): boolean {
  if (screenMatchId != null && next.matchId !== screenMatchId) return false;
  if (current && current.matchId === next.matchId && next.live.version < current.live.version) return false;
  return true;
}

/* ───────────────────────── chamada de jogada ───────────────────────── */

/** Segundos inteiros até o prazo (arredonda para cima; 0 quando venceu; null sem prazo ou com data ilegível). */
export function secondsLeft(deadlineIso: string | null, nowMs: number): number | null {
  if (!deadlineIso) return null;
  const t = Date.parse(deadlineIso);
  if (Number.isNaN(t)) return null;
  return Math.max(0, Math.ceil((t - nowMs) / 1000));
}

/** Tom da contagem: normal, atenção (≤ 10 s) e urgente (≤ 5 s). */
export function countdownTone(left: number): "ok" | "warn" | "urgent" {
  return left <= 5 ? "urgent" : left <= 10 ? "warn" : "ok";
}

/** A decisão aberta é do usuário e ainda está no prazo? */
export function decisionOpen(state: MatchState, nowMs: number): boolean {
  if (state.status !== "AWAITING_DECISION" || !state.live.decision) return false;
  const left = secondsLeft(state.decisionDeadlineAt, nowMs);
  return left == null || left > 0;
}

/** Dá para avançar agora? Sem modos aceitos, só quando a decisão aberta venceu (a IA chama no próximo avanço, D-61). */
export function canAdvance(state: MatchState, nowMs: number): boolean {
  if (state.status === "FINISHED") return false;
  if (state.advanceModes.length > 0) return true;
  return state.status === "AWAITING_DECISION" && !decisionOpen(state, nowMs);
}

/** Situações em que o painel do intervalo (playbook) aparece. */
export function isBreak(status: MatchSessionStatus): boolean {
  return status === "NOT_STARTED" || status === "BREAK" || status === "HALFTIME";
}

/* ───────────────────────── campo ───────────────────────── */

/** O time com a bola ataca rumo à jarda 100 (a casa) ou rumo à 0 (o visitante)? null sem posse. */
export function attacksUp(live: Pick<LiveMatch, "possessionClubId" | "home" | "away">): boolean | null {
  if (!live.possessionClubId) return null;
  if (live.possessionClubId === live.home.clubId) return true;
  if (live.possessionClubId === live.away.clubId) return false;
  return null;
}

export interface Markers {
  /** jarda absoluta da linha de scrimmage */
  scrimmageAbsYd: number;
  /** jarda absoluta da primeira descida (a linha de gol quando é "and goal") */
  firstDownAbsYd: number;
  goalToGo: boolean;
  /** +1 quando o ataque vai para a jarda 100, −1 quando vai para a 0 */
  direction: 1 | -1;
}

/**
 * Linhas de scrimmage e de primeira descida em jarda absoluta, pelos dois sentidos de ataque. Usa `downMarkers` da geometria do
 * campo (que assume ataque para +x) espelhando o campo quando o visitante ataca.
 */
export function scrimmageMarkers(ballOnAbsYd: number, distance: number, up: boolean): Markers {
  const ball = up ? ballOnAbsYd : FIELD.playYd - ballOnAbsYd;
  const m = downMarkers(ball, distance);
  const toAbs = (x: number) => x / YARD_M + FIELD.playYd / 2;
  const scrimmage = toAbs(m.scrimmageX);
  const first = toAbs(m.firstDownX);
  const round = (v: number) => Math.round(v * 100) / 100;
  return up
    ? { scrimmageAbsYd: round(scrimmage), firstDownAbsYd: round(first), goalToGo: m.goalToGo, direction: 1 }
    : { scrimmageAbsYd: round(FIELD.playYd - scrimmage), firstDownAbsYd: round(FIELD.playYd - first), goalToGo: m.goalToGo, direction: -1 };
}

/** Posição de transmissão do ponto de vista de quem ataca ("own 25", "opp 40", "50"). */
export function spotFor(ballOnAbsYd: number, up: boolean): FieldPosition {
  return fieldPosition(up ? ballOnAbsYd : FIELD.playYd - ballOnAbsYd);
}

/** "12:34" para o relógio do quarto. */
export function clockText(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

export interface CurrentDrive {
  number: number;
  clubId: string;
  plays: number;
  yards: number;
  startAbsYd: number;
}

const SCRIMMAGE = new Set(["RUN", "PASS", "KNEEL", "SPIKE"]);

/**
 * Drive em andamento a partir dos lances: o maior número de drive entre os lances do time com a bola, contando as jogadas de
 * scrimmage e as jardas. Sem posse (intervalo, fim) ou sem lance do drive, null.
 */
export function currentDrive(live: LiveMatch, events: PlayEvent[]): CurrentDrive | null {
  const club = live.possessionClubId;
  if (!club || live.status === "FINISHED") return null;
  const mine = events.filter((e) => e.possessionClubId === club && e.drive > 0);
  if (!mine.length) return null;
  const number = Math.max(...mine.map((e) => e.drive));
  const plays = mine.filter((e) => e.drive === number);
  if (plays.some((e) => e.driveEnded)) return null;
  const snaps = plays.filter((e) => e.type != null && SCRIMMAGE.has(e.type));
  const first = snaps[0] ?? plays[0];
  return { number, clubId: club, plays: snaps.length, yards: snaps.reduce((a, e) => a + e.yards, 0), startAbsYd: first.ballOnAbsYd };
}

/* ───────────────────────── erros das rotas de partida ───────────────────────── */

export type MatchNotice = "aiCalled" | "stale" | "decisionOpen" | "finished" | "notAdjustable" | "noTimeout" | "notOffered" | null;

export interface ErrorReaction {
  /** reler `GET /matches/{mid}` (o estado na tela ficou velho) */
  reload: boolean;
  /** aviso traduzido em `match:live.notice.<notice>` */
  notice: MatchNotice;
  /** mostrar também a mensagem do erro (o servidor explica o motivo) */
  showError: boolean;
}

/**
 * O que a tela faz com cada erro das rotas de partida. A chamada atrasada (DECISION_CLOSED) é o caso esperado do prazo de 30 s:
 * a IA já chamou, então a tela avisa e recarrega, sem tratar como falha.
 */
export function reactionTo(err: unknown): ErrorReaction {
  const code = err instanceof ApiError ? err.code : null;
  switch (code) {
    case "DECISION_CLOSED":
      return { reload: true, notice: "aiCalled", showError: false };
    case "MATCH_VERSION_CONFLICT":
    // outra escrita na mesma partida ganhou a corrida (trava ou chave única no servidor, D-91): o estado na tela ficou velho
    case "CONCURRENT_UPDATE":
      return { reload: true, notice: "stale", showError: false };
    case "DECISION_PENDING":
      return { reload: true, notice: "decisionOpen", showError: false };
    case "MATCH_FINISHED":
      return { reload: true, notice: "finished", showError: false };
    case "DECISION_NOT_FOUND":
      return { reload: true, notice: "stale", showError: false };
    case "PLAY_NOT_OFFERED":
      return { reload: true, notice: "notOffered", showError: true };
    case "ADJUSTMENT_NOT_ALLOWED":
      return { reload: true, notice: "notAdjustable", showError: true };
    case "TIMEOUT_NOT_ALLOWED":
      return { reload: true, notice: "noTimeout", showError: true };
    default:
      return { reload: false, notice: null, showError: true };
  }
}

/* ───────────────────────── relatório ───────────────────────── */

/**
 * Chaves de render das linhas do log de decisões do relatório. `TIMEOUT` e `PLAN_CHANGE` não têm `decisionId` e dois deles podem
 * cair no mesmo lance (`atSeq`): a posição na lista entra na chave para ela nunca se repetir (D-94).
 */
export function decisionKeys(decisions: DecisionRecord[]): string[] {
  return decisions.map((d, i) => `${i}-${d.decisionId ?? d.optionCode}-${d.atSeq}`);
}
