/**
 * Que roteiro encena cada lance do play-by-play, onde a bola termina e qual lance encenar quando chegam vários, sem React
 * (testável em node).
 *
 * O roteiro sai da chave i18n do lance (`textKey`, as 48 chaves de `match-i18n-keys.json`, estáveis desde o bloco 5) e, para
 * uma chave que o front não conhece, do `type`/`result` do motor (`PlayType`/`PlayResult`). Lance sem roteiro conhecido cai no
 * roteiro genérico (snap, o corredor leva a bola até o ponto final). Os administrativos (cara ou coroa, tempo técnico, aviso
 * dos dois minutos, fim de período) não são encenados.
 *
 * A bola termina no ponto oficial do lance: linha de scrimmage + jardas do evento no sentido do ataque (corrida, passe, sack,
 * scramble, kneel, falta; passe incompleto e spike = a própria linha). Quando a posse muda (turnover, chutes) o ponto é a
 * próxima linha de scrimmage que o estado mostra (o lance seguinte ou `live`), porque as jardas do evento não dizem onde o
 * outro time pegou a bola. Touchdown termina na linha de gol, safety na própria linha de gol e o field goal/extra point bom
 * atrás das traves.
 */
import type { PlayEvent } from "@/lib/api/match-types";
import { FIELD } from "@/lib/field/geometry";
import type { AttackDir } from "@/lib/field/world";

export type ScriptKind =
  | "RUN" | "PASS_COMPLETE" | "PASS_INCOMPLETE" | "SACK" | "SCRAMBLE" | "INTERCEPTION" | "FUMBLE"
  | "PUNT" | "KICKOFF" | "FIELD_GOAL" | "PENALTY" | "KNEEL" | "SPIKE" | "TWO_POINT" | "GENERIC";

/** Chave do lance → roteiro (null = administrativo, sem encenação). Cobre todas as chaves do motor. */
export const KIND_BY_KEY: Record<string, ScriptKind | null> = {
  "match:event.coinToss": null,
  "match:event.kickoff.touchback": "KICKOFF",
  "match:event.kickoff.return": "KICKOFF",
  "match:event.kickoff.returnTouchdown": "KICKOFF",
  "match:event.kickoff.fumble": "KICKOFF",
  "match:event.onside.recovered": "KICKOFF",
  "match:event.onside.lost": "KICKOFF",
  "match:event.freeKick.return": "KICKOFF",
  "match:event.run.gain": "RUN",
  "match:event.run.touchdown": "RUN",
  "match:event.run.fumble": "FUMBLE",
  "match:event.run.safety": "RUN",
  "match:event.pass.complete": "PASS_COMPLETE",
  "match:event.pass.touchdown": "PASS_COMPLETE",
  "match:event.pass.incomplete": "PASS_INCOMPLETE",
  "match:event.pass.drop": "PASS_INCOMPLETE",
  "match:event.pass.defended": "PASS_INCOMPLETE",
  "match:event.pass.fumble": "FUMBLE",
  "match:event.pass.interception": "INTERCEPTION",
  "match:event.pass.interceptionTouchdown": "INTERCEPTION",
  "match:event.sack": "SACK",
  "match:event.sack.fumble": "FUMBLE",
  "match:event.sack.safety": "SACK",
  "match:event.scramble": "SCRAMBLE",
  "match:event.punt.return": "PUNT",
  "match:event.punt.returnTouchdown": "PUNT",
  "match:event.punt.fairCatch": "PUNT",
  "match:event.punt.touchback": "PUNT",
  "match:event.punt.downed": "PUNT",
  "match:event.punt.blocked": "PUNT",
  "match:event.punt.muffed": "PUNT",
  "match:event.fieldGoal.good": "FIELD_GOAL",
  "match:event.fieldGoal.missed": "FIELD_GOAL",
  "match:event.fieldGoal.blocked": "FIELD_GOAL",
  "match:event.extraPoint.good": "FIELD_GOAL",
  "match:event.extraPoint.missed": "FIELD_GOAL",
  "match:event.twoPoint.good": "TWO_POINT",
  "match:event.twoPoint.failed": "TWO_POINT",
  "match:event.kneel": "KNEEL",
  "match:event.spike": "SPIKE",
  "match:event.penalty": "PENALTY",
  "match:event.timeout": null,
  "match:event.twoMinuteWarning": null,
  "match:event.endOfQuarter": null,
  "match:event.endOfHalf": null,
  "match:event.endOfRegulation": null,
  "match:event.endOfOvertime": null,
  "match:event.final": null,
};

const ADMIN_TYPES = new Set(["COIN_TOSS", "TIMEOUT", "TWO_MINUTE_WARNING", "END_OF_PERIOD"]);

/** Pelo `type`/`result` do motor, para uma chave que o front não conhece. */
function kindByType(type: string | null, result: string | null): ScriptKind | null {
  if (type && ADMIN_TYPES.has(type)) return null;
  if (result === "PENALTY" || type === "NO_PLAY") return "PENALTY";
  switch (type) {
    case "KICKOFF":
    case "ONSIDE_KICK":
    case "FREE_KICK":
      return "KICKOFF";
    case "PUNT":
      return "PUNT";
    case "FIELD_GOAL":
    case "EXTRA_POINT":
      return "FIELD_GOAL";
    case "TWO_POINT":
      return "TWO_POINT";
    case "KNEEL":
      return "KNEEL";
    case "SPIKE":
      return "SPIKE";
    case "RUN":
      return result === "FUMBLE_LOST" ? "FUMBLE" : "RUN";
    case "PASS":
      switch (result) {
        case "COMPLETE":
        case "TOUCHDOWN":
          return "PASS_COMPLETE";
        case "INCOMPLETE":
          return "PASS_INCOMPLETE";
        case "SACK":
          return "SACK";
        case "SCRAMBLE":
          return "SCRAMBLE";
        case "INTERCEPTION":
          return "INTERCEPTION";
        case "FUMBLE_LOST":
          return "FUMBLE";
        default:
          return "GENERIC";
      }
    default:
      return "GENERIC";
  }
}

/** Roteiro do lance; null para os administrativos (não há o que encenar). */
export function kindOf(e: Pick<PlayEvent, "textKey" | "type" | "result">): ScriptKind | null {
  if (Object.prototype.hasOwnProperty.call(KIND_BY_KEY, e.textKey)) return KIND_BY_KEY[e.textKey];
  return kindByType(e.type, e.result);
}

/* ───────────────────────── situação seguinte ───────────────────────── */

/** O que o estado mostra depois do lance: a situação do lance seguinte (o motor grava a de antes do snap) ou a de `live`. */
export interface NextSituation {
  possessionClubId: string | null;
  ballOnAbsYd: number;
}

export function nextSituation(events: PlayEvent[], index: number, live: NextSituation | null): NextSituation | null {
  const after = events[index + 1];
  if (after) return { possessionClubId: after.possessionClubId, ballOnAbsYd: after.ballOnAbsYd };
  return live;
}

/* ───────────────────────── onde a bola termina ───────────────────────── */

const clampAbs = (v: number) => Math.max(0, Math.min(FIELD.playYd, v));
/** Linha de gol do adversário de quem ataca no sentido `dir`, e a própria. */
export const goalLineOf = (dir: AttackDir) => (dir === 1 ? FIELD.playYd : 0);
export const ownGoalLineOf = (dir: AttackDir) => (dir === 1 ? 0 : FIELD.playYd);
/** Field goal ou extra point bom: a bola cai 4 jardas atrás da linha de fundo, depois das traves. */
export const KICK_GOOD_PAST_END_LINE_YD = 4;

export interface SpotInput {
  event: Pick<PlayEvent, "textKey" | "type" | "result" | "ballOnAbsYd" | "yards" | "points" | "scoringClubId" | "possessionClubId" | "turnover" | "concept">;
  kind: ScriptKind;
  /** sentido do ataque de quem tem a bola no snap (nos chutes de saída, de quem chuta) */
  dir: AttackDir;
  /** linha da jogada (no kickoff, a linha do chute) */
  losAbsYd: number;
  /** clube que tem a bola no snap (no kickoff, quem chuta) */
  offenseClubId: string | null;
  next: NextSituation | null;
}

const has = (key: string, part: string) => key.includes(part);

/**
 * Jarda absoluta onde a bola fica no fim do lance (pode passar de 0–100 só no field goal bom, atrás das traves). É a regra que
 * a encenação segue e que os testes conferem contra a próxima linha de scrimmage.
 */
export function endSpot({ event: e, kind, dir, losAbsYd, offenseClubId, next }: SpotInput): number {
  const key = e.textKey;
  const byYards = clampAbs(losAbsYd + dir * e.yards);
  const offenseScored = e.points > 0 && (e.scoringClubId == null || e.scoringClubId === offenseClubId);
  const otherScored = e.points > 0 && e.scoringClubId != null && e.scoringClubId !== offenseClubId;
  switch (kind) {
    case "FIELD_GOAL": {
      const good = e.result === "FIELD_GOAL_GOOD" || e.result === "EXTRA_POINT_GOOD" || has(key, ".good");
      const endLine = dir === 1 ? FIELD.playYd + FIELD.endZoneYd : -FIELD.endZoneYd;
      if (good) return endLine + dir * KICK_GOOD_PAST_END_LINE_YD;
      // field goal errado ou bloqueado: a bola vai para o adversário onde o estado mostrar; extra point errado fica onde caiu
      const isExtraPoint = e.type === "EXTRA_POINT" || has(key, "extraPoint");
      if (!isExtraPoint && next && next.possessionClubId !== offenseClubId) return next.ballOnAbsYd;
      return has(key, "blocked") ? clampAbs(losAbsYd - dir * 5) : endLine;
    }
    case "KICKOFF":
    case "PUNT":
      // retorno para touchdown: na linha de gol de quem chutou
      if (otherScored || has(key, "returnTouchdown")) return ownGoalLineOf(dir);
      if (next) return next.ballOnAbsYd;
      return kind === "PUNT" ? clampAbs(losAbsYd + dir * Math.max(30, e.yards)) : clampAbs(losAbsYd + dir * 45);
    case "INTERCEPTION":
    case "FUMBLE":
      if (otherScored || has(key, "interceptionTouchdown")) return ownGoalLineOf(dir);
      if (next && next.possessionClubId !== offenseClubId) return next.ballOnAbsYd;
      return byYards;
    case "TWO_POINT":
      return e.result === "TWO_POINT_GOOD" || has(key, ".good") ? goalLineOf(dir) : byYards;
    case "PASS_INCOMPLETE":
    case "SPIKE":
      return clampAbs(losAbsYd);
    default:
      if (has(key, "safety") || e.result === "SAFETY") return ownGoalLineOf(dir);
      if (offenseScored && (e.result === "TOUCHDOWN" || has(key, "touchdown"))) return goalLineOf(dir);
      return byYards;
  }
}

/* ───────────────────────── qual lance encenar ───────────────────────── */

export interface StagePick {
  /** índice do lance encenado em `events` */
  index: number;
  /** lances novos que não serão encenados (avanço de vários de uma vez) */
  skipped: number;
}

/**
 * Lances novos desde o último encenado (`afterSeq`): encena só o último que tem roteiro; os outros entram no play-by-play
 * sem animação (avanço de drive ou de quarto inteiro não trava a tela). null quando não há lance novo encenável.
 */
export function pickStage(events: PlayEvent[], afterSeq: number): StagePick | null {
  let index = -1;
  let count = 0;
  for (let i = 0; i < events.length; i++) {
    const e = events[i];
    if (e.seq <= afterSeq || kindOf(e) == null) continue;
    count++;
    index = i;
  }
  return index < 0 ? null : { index, skipped: count - 1 };
}

/** Último lance encenável até agora (o quadro final que a tela mostra ao abrir, sem animação). */
export function lastStageable(events: PlayEvent[]): number {
  for (let i = events.length - 1; i >= 0; i--) if (kindOf(events[i]) != null) return i;
  return -1;
}
