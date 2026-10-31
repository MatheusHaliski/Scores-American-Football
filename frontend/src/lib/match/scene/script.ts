/**
 * Roteiro de um lance do play-by-play para o campo 3D, sem React (testável em node): formação pré-snap (`formation.ts`), snap,
 * a trajetória de cada um dos 22 atletas e da bola até o fim do lance e a duração (3 a 6 s). Tudo no referencial do ataque
 * (`lib/field/world`), então o mesmo roteiro serve para os dois sentidos de ataque: a conversão para o mundo espelha o campo.
 *
 * A bola termina exatamente no ponto oficial do lance (`endSpot` em `kinds.ts`). Quando o lance físico acaba noutro lugar
 * (passe incompleto, spike, touchback, field goal errado), a bola é "reposta" no ponto oficial num arco curto no fim do lance,
 * como o árbitro faz, em vez de pular de lugar. Ao fim, todos os atletas param.
 *
 * Não há aleatoriedade: o mesmo lance dá sempre o mesmo roteiro (o seq do lance só desempata o lado das corridas).
 */
import type { PlayEvent } from "@/lib/api/match-types";
import { FIELD, GOALPOST_M, YARD_M } from "@/lib/field/geometry";
import type { AttackDir } from "@/lib/field/world";
import { HALF_WIDTH_YD, offenseToWorld, worldToOffense } from "@/lib/field/world";
import { endSpot, kindOf, type NextSituation, type ScriptKind } from "./kinds";
import { formationFor, type FormationKind, type Role, type Slot } from "./formation";
import { headingOf, sampleBall, sampleTrack, type Action, type BallSeg, type Keyframe } from "./track";

/** Duração de um lance encenado (s). */
export const MIN_DURATION = 3;
export const MAX_DURATION = 6;
/** Momento do snap (s). */
export const SNAP_T = 0.5;
/** Pausa no fim, com todos parados, antes do roteiro acabar (s). */
const HOLD = 0.7;
/** Bola no chão: altura do centro (m). */
export const BALL_REST_H = 0.12;
const SPRINT = 8;
const RUN = 7;
const JOG = 4.5;
const LINE = 2.5;

export interface FlagThrow {
  t0: number;
  t1: number;
  from: [number, number, number];
  to: [number, number, number];
}

export interface PlayScript {
  seq: number;
  kind: ScriptKind;
  /** linha da jogada (jarda absoluta; no kickoff, a linha do chute) */
  los: number;
  /** sentido de quem tem a bola no snap (no kickoff, de quem chuta) */
  dir: AttackDir;
  offenseClubId: string;
  defenseClubId: string;
  slots: Slot[];
  tracks: Keyframe[][];
  ball: BallSeg[];
  flag: FlagThrow | null;
  duration: number;
  /** ponto oficial onde a bola termina (jarda absoluta) e o mesmo ponto no referencial do ataque */
  endAbsYd: number;
  endD: number;
  touchdown: boolean;
  /** vaga → índice em `event.playerIds` (o número certo no atleta que corre, passa, recebe, derruba, chuta) */
  named: Record<number, number>;
}

export interface ScriptInput {
  event: PlayEvent;
  next: NextSituation | null;
  homeClubId: string;
  awayClubId: string;
}

/* ───────────────────────── montagem ───────────────────────── */

const MOVING: Action[] = ["run", "backpedal"];

class Builder {
  readonly tracks: Keyframe[][];
  readonly ball: BallSeg[] = [];
  flag: FlagThrow | null = null;
  named: Record<number, number> = {};
  readonly slots: Slot[];
  constructor(slots: Slot[]) {
    this.slots = slots;
    this.tracks = slots.map((s) => [{ t: 0, d: s.d, w: s.w, act: s.stance, face: s.side === "O" ? 0 : Math.PI }]);
  }
  last(i: number): Keyframe {
    const k = this.tracks[i];
    return k[k.length - 1];
  }
  at(i: number, t: number) {
    return sampleTrack(this.tracks[i], t, this.slots[i].side === "O" ? 0 : Math.PI);
  }
  /** Muda a ação a partir de `t`, parado onde estiver. */
  act(i: number, t: number, act: Action, face?: number) {
    const k = this.tracks[i];
    const last = k[k.length - 1];
    if (Math.abs(last.t - t) < 1e-6) {
      last.act = act;
      last.face = face;
      return;
    }
    const tt = Math.max(t, last.t + 0.01);
    const s = this.at(i, tt);
    k.push({ t: tt, d: s.d, w: s.w, act, face });
  }
  /** Vai de onde estiver em `t0` até (d, w) em `t1`, fazendo `act` no caminho e `end` ao chegar. */
  move(i: number, t0: number, t1: number, d: number, w: number, act: Action = "run", end: Action = "up", face?: number, endFace?: number) {
    this.act(i, t0, act, face);
    const last = this.last(i);
    this.tracks[i].push({ t: Math.max(t1, last.t + 0.05), d, w, act: end, face: endFace });
  }
  /** Corre até (d, w) à velocidade `v` (jardas/s) a partir de `t0`; devolve a chegada. */
  runTo(i: number, t0: number, d: number, w: number, v: number, end: Action = "up", act: Action = "run", face?: number): number {
    const start = Math.max(t0, this.last(i).t);
    const s = this.at(i, start);
    const t1 = start + Math.max(0.25, Math.hypot(d - s.d, w - s.w) / v);
    this.move(i, t0, t1, d, w, act, end, face, face);
    return t1;
  }
  get ballEnd(): number {
    return this.ball.length ? this.ball[this.ball.length - 1].t1 : 0;
  }
  hold(actor: number, t1: number) {
    this.ball.push({ kind: "held", t0: this.ballEnd, t1, actor });
  }
  fly(to: [number, number, number], t1: number, apex: number, from?: [number, number, number]) {
    const t0 = this.ballEnd;
    const f = from ?? this.ballPos(t0);
    this.ball.push({ kind: "flight", t0, t1, from: f, to, apex });
  }
  rest(at: [number, number, number], t1: number) {
    this.ball.push({ kind: "rest", t0: this.ballEnd, t1, at });
  }
  ballPos(t: number): [number, number, number] {
    if (!this.ball.length) return [0, 0, BALL_REST_H];
    const b = sampleBall(this.ball, Math.min(t, this.ballEnd - 1e-6), (i, tt) => this.at(i, tt));
    return [b.d, b.w, b.h];
  }
  find(side: "O" | "D", ...roles: Role[]): number {
    for (const r of roles) {
      const i = this.slots.findIndex((s) => s.side === side && s.role === r);
      if (i >= 0) return i;
    }
    return -1;
  }
  all(side: "O" | "D", ...roles: Role[]): number[] {
    return this.slots.flatMap((s, i) => (s.side === side && (!roles.length || roles.includes(s.role)) ? [i] : []));
  }
  /** Os atletas de um lado mais perto de (d, w) no instante `t`, sem os da lista. */
  nearest(side: "O" | "D", d: number, w: number, t: number, exclude: number[] = []): number[] {
    return this.all(side)
      .filter((i) => !exclude.includes(i))
      .map((i) => {
        const s = this.at(i, t);
        return { i, dist: Math.hypot(s.d - d, s.w - w) };
      })
      .sort((a, b) => a.dist - b.dist)
      .map((x) => x.i);
  }
}

const clampW = (w: number) => Math.max(-(HALF_WIDTH_YD - 0.8), Math.min(HALF_WIDTH_YD - 0.8, w));

/** Ponto do corpo de quem leva a bola para a bola ficar em (d, w) indo no sentido `heading`. */
function rootBehind(d: number, w: number, heading: number): [number, number] {
  const back = 0.38;
  return [d - Math.cos(heading) * back, w - Math.sin(heading) * back];
}

/**
 * Bloqueios do início: a linha ofensiva e a defensiva se encontram na linha (corrida) ou a linha ofensiva recua protegendo
 * o passe e a defensiva avança (passe); tight ends bloqueiam junto.
 */
function linePlay(b: Builder, pass: boolean, until: number) {
  for (const i of b.all("O", "C", "LG", "RG", "LT", "RT", "LS")) {
    const s = b.slots[i];
    b.move(i, SNAP_T, SNAP_T + 0.5, pass ? s.d - 1.2 : s.d + 0.7, s.w, "block", "block", 0, 0);
    b.act(i, until, "up", 0);
  }
  for (const i of b.all("D", "DE", "DT")) {
    const s = b.slots[i];
    b.move(i, SNAP_T, SNAP_T + 0.5, pass ? s.d - 1.6 : s.d - 0.3, s.w * (pass ? 0.9 : 1), "block", "block", Math.PI, Math.PI);
    b.act(i, until, "up", Math.PI);
  }
}

/** Perseguição: cada um corre até perto do ponto do fim, em volta dele (ninguém se empilha), e para. */
function pursue(b: Builder, ids: number[], d: number, w: number, from: number, arrive: number, v = RUN) {
  ids.forEach((i, k) => {
    const s = b.at(i, from);
    const ang = headingOf(s.d - d, s.w - w);
    const r = 2 + k * 0.9;
    const td = d + Math.cos(ang) * r;
    const tw = clampW(w + Math.sin(ang) * r);
    const dist = Math.hypot(td - s.d, tw - s.w);
    // quem não alcança até pouco depois do fim para no caminho (o lance não espera ninguém)
    const start = Math.max(from, b.last(i).t);
    const limit = Math.max(start + 0.3, arrive + 0.6);
    const t1 = Math.max(start + dist / v, Math.min(arrive, start + dist / 2.5));
    const u = t1 > limit ? (limit - start) / (t1 - start) : 1;
    b.move(i, from, Math.min(t1, limit), s.d + (td - s.d) * u, s.w + (tw - s.w) * u, dist > 0.6 ? "run" : "up", "up");
  });
}

/** Derruba quem leva a bola: o defensor chega pela frente e mergulha; os dois ficam no chão; a bola fica no ponto exato. */
function tackle(b: Builder, carrier: number, tackler: number, tT: number, end: [number, number], heading: number) {
  const meet: [number, number] = [end[0] + Math.cos(heading) * 1.1, clampW(end[1] + Math.sin(heading) * 1.1 + 0.3)];
  const s = b.at(tackler, Math.max(SNAP_T, tT - 2.2));
  const dist = Math.hypot(meet[0] - s.d, meet[1] - s.w);
  const t0 = Math.max(SNAP_T, tT - 0.1 - dist / SPRINT);
  b.move(tackler, t0, tT - 0.1, meet[0], meet[1], "run", "dive", undefined, heading + Math.PI);
  b.move(tackler, tT + 0.05, tT + 0.45, end[0] + Math.cos(heading) * 0.55, end[1] + Math.sin(heading) * 0.55, "dive", "down", heading + Math.PI, heading + Math.PI);
  b.act(carrier, tT, "dive", heading);
  b.act(carrier, tT + 0.45, "down", heading);
  b.hold(carrier, tT + 0.2);
  b.fly([end[0], end[1], BALL_REST_H], tT + 0.45, 0.15);
}

/** Touchdown: quem marca encosta a bola no chão no ponto (spike) e comemora; os dois companheiros mais perto vêm junto. */
function celebrate(b: Builder, scorer: number, tG: number, end: [number, number], heading: number) {
  b.act(scorer, tG, "spike", heading);
  b.hold(scorer, tG + 0.25);
  b.fly([end[0], end[1], BALL_REST_H], tG + 0.45, 0.2);
  b.act(scorer, tG + 0.55, "celebrate", heading + Math.PI);
  b.act(scorer, tG + 1.65, "up", heading + Math.PI);
  const side = b.slots[scorer].side;
  for (const i of b.nearest(side, end[0], end[1], tG, [scorer]).slice(0, 2)) {
    const s = b.at(i, tG);
    if (Math.hypot(s.d - end[0], s.w - end[1]) > 14) continue;
    const t1 = b.runTo(i, tG, end[0] - Math.cos(heading) * 1.8, clampW(end[1] + (s.w > end[1] ? 1.6 : -1.6)), RUN, "celebrate");
    b.act(i, Math.max(t1 + 0.9, tG + 1.6), "up");
  }
}

/** Snap: a bola sai do chão na linha para o QB (sob o center, na mão; em shotgun, num passe curto). Devolve quando o QB tem a bola. */
function snapTo(b: Builder, qb: number): number {
  const s = b.slots[qb];
  b.rest([-0.15, 0, BALL_REST_H], SNAP_T);
  if (s.stance === "qbUnder") return SNAP_T;
  const tGet = SNAP_T + Math.min(0.75, 0.12 + Math.abs(s.d) * 0.05);
  b.fly([s.d + 0.4, s.w, 1.0], tGet, 0.15, [-0.4, 0, 0.35]);
  return tGet;
}

/** Repõe a bola no ponto oficial (num arco curto, como o árbitro) quando o lance físico acabou noutro lugar. */
function respot(b: Builder, t0: number, end: [number, number]) {
  const here = b.ballPos(b.ballEnd);
  const far = Math.hypot(here[0] - end[0], here[1] - end[1]);
  if (far < 0.05) return;
  b.rest(here, Math.max(t0, b.ballEnd));
  b.fly([end[0], end[1], BALL_REST_H], b.ballEnd + Math.min(1.1, 0.45 + far * 0.02), Math.min(6, 1.5 + far * 0.12));
}

/** Os que não têm papel no fim do lance correm em volta da bola (o ataque também, para bloquear ou acompanhar). */
function everyoneFollows(b: Builder, busy: number[], d: number, w: number, from: number, arrive: number) {
  const free = (side: "O" | "D") => b.nearest(side, d, w, from, busy);
  pursue(b, free("D"), d, w, from, arrive);
  pursue(b, free("O"), d, w, from, arrive, JOG + 1);
}

/* ───────────────────────── os roteiros ───────────────────────── */

interface Ctx {
  b: Builder;
  e: PlayEvent;
  endD: number;
  toGoal: number;
  toOwnGoal: number;
  touchdown: boolean;
}

function carrierOf(b: Builder, e: PlayEvent): number {
  if (e.concept === "QB_SNEAK" || e.type === "KNEEL") return b.find("O", "QB");
  const i = b.find("O", "RB", "FB");
  return i >= 0 ? i : b.find("O", "QB", "WR");
}

/** Corrida (também o genérico e a conversão de 2 pontos por terra). */
function runScript(c: Ctx) {
  const { b, e } = c;
  const qb = b.find("O", "QB");
  const carrier = carrierOf(b, e);
  b.named[carrier] = 0;
  const tGot = snapTo(b, qb);
  const sign = e.seq % 2 === 0 ? 1 : -1;
  let t: number;
  if (carrier !== qb) {
    const cs = b.slots[carrier];
    const hD = Math.min(cs.d + 2.2, -2.6);
    const qbAt = b.slots[qb];
    const tH = Math.max(tGot + 0.35, SNAP_T + 0.65);
    b.move(qb, tGot, tH, Math.min(qbAt.d, hD) - 0.3, -0.5 * sign, "backpedal", "handoff", 0, sign * 0.9);
    b.hold(qb, tH);
    b.move(carrier, SNAP_T, tH, hD, 0.4 * sign, "run", "run");
    b.move(qb, tH + 0.35, tH + 1.4, hD - 2.5, -3.5 * sign, "run", "up");
    t = tH;
  } else {
    t = tGot + 0.15;
  }
  // corredor: buraco por dentro ou contorno pelo lado, depois até o ponto final
  const outside = e.concept === "OUTSIDE_RUN";
  const start = b.at(carrier, t);
  const holeW = outside ? 7.5 * sign : 1.3 * sign;
  const holeD = outside ? Math.max(start.d + 1, -1.2) : 0.6;
  const endW = clampW(holeW + (outside ? 1.5 * sign : 0.4 * sign));
  const heading = headingOf(c.endD - holeD, endW - holeW);
  const [rd, rw] = rootBehind(c.endD, endW, heading);
  if (c.endD > holeD + 0.5) t = b.runTo(carrier, t, holeD, holeW, RUN, "run");
  const tEnd = b.runTo(carrier, t, rd, rw, SPRINT, c.touchdown ? "up" : "run");
  b.hold(carrier, tEnd);
  linePlay(b, false, tEnd);
  const fb = b.find("O", "FB");
  if (fb >= 0 && fb !== carrier) b.runTo(fb, SNAP_T, 1.4, holeW, RUN, "block");
  for (const i of b.all("O", "WR", "TE")) {
    const s = b.slots[i];
    b.runTo(i, SNAP_T, s.role === "TE" ? 0.4 : Math.min(8, c.toGoal), s.w * 0.85, s.role === "TE" ? LINE : RUN, "block");
  }
  if (c.touchdown) {
    celebrate(b, carrier, tEnd, [c.endD, endW], heading);
    pursue(b, b.all("D", "CB", "FS", "SS", "OLB", "MLB"), c.endD - 2, endW, SNAP_T + 0.4, tEnd + 0.3);
    return;
  }
  const tackler = b.nearest("D", c.endD, endW, SNAP_T, b.all("D", "DE", "DT"))[0];
  b.named[tackler] = 1;
  tackle(b, carrier, tackler, tEnd, [c.endD, endW], heading);
  pursue(b, b.all("D").filter((i) => i !== tackler && !["DE", "DT"].includes(b.slots[i].role)), c.endD, endW, SNAP_T + 0.3, tEnd + 0.4);
}

interface PassPlan {
  qb: number;
  receiver: number;
  tThrow: number;
  tCatch: number;
  catchD: number;
  catchW: number;
  dropD: number;
}

/** Recebedor pelo conceito: screen → running back; curto → slot ou tight end; fundo → o recebedor mais aberto. */
function receiverOf(b: Builder, concept: string | null): number {
  const wrs = b.all("O", "WR").sort((x, y) => Math.abs(b.slots[x].w) - Math.abs(b.slots[y].w));
  const te = b.find("O", "TE");
  if (concept === "SCREEN") return b.find("O", "RB", "FB", "WR");
  if (concept === "SHORT_PASS") return wrs.length > 2 ? wrs[0] : te >= 0 ? te : wrs[0];
  if (concept === "MEDIUM_PASS") return te >= 0 && wrs.length < 3 ? te : wrs[Math.min(1, wrs.length - 1)] ?? te;
  return wrs[wrs.length - 1] ?? te;
}

const DEPTH_BY_CONCEPT: Record<string, number> = { SCREEN: -1.5, SHORT_PASS: 6, MEDIUM_PASS: 13, DEEP_PASS: 28, PLAY_ACTION: 18 };

/** Dropback, rota e lançamento (até a bola chegar no ponto do passe). */
function passPlan(c: Ctx, catchDepth: number, keepCatchD = false): PassPlan {
  const { b, e } = c;
  const qb = b.find("O", "QB");
  b.named[qb] = 0;
  const receiver = receiverOf(b, e.concept);
  const tGot = snapTo(b, qb);
  const q0 = b.slots[qb];
  const dropD = Math.max(q0.stance === "qbUnder" ? -7 : q0.d - 2, -(c.toOwnGoal + 8));
  const tDrop = b.runTo(qb, tGot, dropD, 0, q0.stance === "qbUnder" ? 6.5 : 4.5, "up", "backpedal", 0);
  const r0 = b.slots[receiver];
  const catchD = keepCatchD ? catchDepth : Math.min(catchDepth, c.toGoal + 8);
  const inside = Math.abs(r0.w) < 10;
  const catchW = clampW(e.concept === "SCREEN" ? r0.w + Math.sign(r0.w || 1) * 4 : inside ? r0.w * 0.55 : r0.w * 0.85);
  // haste reta e quebra para dentro (ou para fora no fundo)
  const stemD = e.concept === "SCREEN" ? r0.d - 0.5 : Math.max(r0.d + 1, catchD * 0.65);
  const tStem = b.runTo(receiver, SNAP_T, stemD, r0.w, RUN, "run");
  let tCatch = b.runTo(receiver, tStem, catchD, catchW, RUN, "catch");
  const dist = Math.hypot(catchD - dropD, catchW);
  const flight = Math.min(1.5, Math.max(0.45, dist / 22));
  let tThrow = tCatch - flight;
  if (tThrow < tDrop + 0.3) {
    tThrow = tDrop + 0.3;
    const late = tThrow + flight;
    b.act(receiver, tCatch, "up");
    b.act(receiver, late - 0.25, "catch");
    tCatch = late;
  }
  b.act(qb, tThrow - 0.3, "throw", headingOf(catchD - dropD, catchW));
  b.act(qb, tThrow + 0.4, "up");
  b.hold(qb, tThrow);
  return { qb, receiver, tThrow, tCatch, catchD, catchW, dropD };
}

/** Defesa no passe: pressão da linha, linebackers recuando, secundária colada nos recebedores. */
function passDefense(b: Builder, p: PassPlan, skip: number[] = []) {
  for (const i of b.all("D", "OLB", "MLB")) {
    if (skip.includes(i)) continue;
    const s = b.slots[i];
    b.runTo(i, SNAP_T, s.d + 3, s.w * 0.8, JOG, "two", "backpedal");
  }
  const receivers = b.all("O", "WR", "TE", "RB", "FB");
  for (const i of b.all("D", "CB", "FS", "SS")) {
    if (skip.includes(i)) continue;
    const s = b.slots[i];
    const mark = receivers.map((r) => ({ r, dist: Math.hypot(b.slots[r].d - s.d, b.slots[r].w - s.w) })).sort((x, y) => x.dist - y.dist)[0];
    const r = mark ? b.at(mark.r, p.tCatch) : { d: s.d + 4, w: s.w };
    b.move(i, SNAP_T, p.tCatch, Math.max(r.d + 1.5, 1), clampW(r.w + (s.w > r.w ? 1 : -1)), "backpedal", "two");
  }
  for (const i of b.all("O", "WR", "TE")) {
    if (i === p.receiver) continue;
    const s = b.slots[i];
    b.runTo(i, SNAP_T, Math.max(5, Math.min(14, p.catchD)), clampW(s.w * 0.9), RUN, "up");
  }
}

function passComplete(c: Ctx, yards: number) {
  const { b, e } = c;
  const yac = e.concept === "SCREEN" ? yards + 1.5 : Math.max(0, Math.round(yards * 0.35));
  const depth = e.concept === "SCREEN" ? -1.5 : Math.max(1.5, c.endD - yac);
  const p = passPlan(c, depth);
  b.named[p.receiver] = 1;
  b.fly([p.catchD + 0.3, p.catchW, 1.3], p.tCatch, 1 + Math.hypot(p.catchD - p.dropD, p.catchW) * 0.09, [p.dropD + 0.4, -0.2, 2.1]);
  passDefense(b, p);
  linePlay(b, true, p.tCatch);
  const runs = c.endD - p.catchD > 0.5;
  const endW = runs ? clampW(p.catchW * 0.6) : p.catchW;
  const h = runs ? headingOf(c.endD - p.catchD, endW - p.catchW) : 0;
  const [rd, rw] = rootBehind(c.endD, endW, h);
  const tEnd = b.runTo(p.receiver, p.tCatch + 0.1, rd, rw, SPRINT, c.touchdown ? "up" : "run");
  b.hold(p.receiver, tEnd);
  if (c.touchdown) {
    celebrate(b, p.receiver, tEnd, [c.endD, endW], h);
    pursue(b, b.nearest("D", c.endD, endW, p.tCatch).slice(0, 6), c.endD - 2, endW, p.tCatch, tEnd + 0.4);
    return;
  }
  const tackler = b.nearest("D", c.endD, endW, p.tCatch)[0];
  b.named[tackler] = 2;
  tackle(b, p.receiver, tackler, tEnd, [c.endD, endW], h);
  pursue(b, b.nearest("D", c.endD, endW, p.tCatch, [tackler]).slice(0, 5), c.endD, endW, p.tCatch, tEnd + 0.4);
}

function passIncomplete(c: Ctx) {
  const { b, e } = c;
  const p = passPlan(c, DEPTH_BY_CONCEPT[e.concept ?? ""] ?? 10);
  b.named[p.receiver] = 1;
  const land: [number, number, number] = [p.catchD + 1.8, clampW(p.catchW + 0.9), BALL_REST_H];
  b.fly(land, p.tCatch + 0.12, 1 + Math.hypot(p.catchD - p.dropD, p.catchW) * 0.09, [p.dropD + 0.4, -0.2, 2.1]);
  b.fly([land[0] + 2, clampW(land[1] + 1.2), BALL_REST_H], b.ballEnd + 0.5, 0.6);
  passDefense(b, p);
  linePlay(b, true, p.tCatch);
  // o defensor mais perto mergulha na bola; o recebedor fica sem ela
  const db = b.nearest("D", p.catchD, p.catchW, p.tCatch)[0];
  b.move(db, p.tCatch - 0.25, p.tCatch + 0.3, p.catchD + 0.6, p.catchW + 0.5, "dive", "down");
  b.act(db, p.tCatch + 1.2, "up");
  b.act(p.receiver, p.tCatch + 0.3, "up");
  respot(b, p.tCatch + 1.5, [c.endD, 0]);
}

function interception(c: Ctx) {
  const { b, e } = c;
  const p = passPlan(c, DEPTH_BY_CONCEPT[e.concept ?? ""] ?? 12);
  const picker = b.nearest("D", p.catchD, p.catchW, SNAP_T, b.all("D", "DE", "DT"))[0];
  b.named[picker] = 1;
  b.move(picker, SNAP_T, p.tCatch, p.catchD + 0.5, p.catchW, "run", "catch", undefined, Math.PI);
  passDefense(b, p, [picker]);
  linePlay(b, true, p.tCatch);
  b.fly([p.catchD + 0.1, p.catchW, 1.4], p.tCatch, 1 + Math.hypot(p.catchD - p.dropD, p.catchW) * 0.09, [p.dropD + 0.4, -0.2, 2.1]);
  const endW = clampW(p.catchW * 0.5);
  const h = headingOf(c.endD - p.catchD, endW - p.catchW);
  const [rd, rw] = rootBehind(c.endD, endW, h);
  const tEnd = b.runTo(picker, p.tCatch + 0.15, rd, rw, SPRINT, "run");
  b.hold(picker, tEnd);
  if (c.endD <= -c.toOwnGoal + 0.01) {
    celebrate(b, picker, tEnd, [c.endD, endW], h);
    return;
  }
  const tackler = b.nearest("O", c.endD, endW, p.tCatch, [p.qb])[0] ?? p.receiver;
  tackle(b, picker, tackler, tEnd, [c.endD, endW], h);
  pursue(b, b.nearest("O", c.endD, endW, p.tCatch, [tackler]).slice(0, 4), c.endD, endW, p.tCatch + 0.2, tEnd + 0.4);
}

function sack(c: Ctx, fumble: boolean) {
  const { b } = c;
  const qb = b.find("O", "QB");
  b.named[qb] = 0;
  const tGot = snapTo(b, qb);
  const q0 = b.slots[qb];
  const dropD = Math.max(q0.stance === "qbUnder" ? -7 : q0.d - 2, -(c.toOwnGoal + 8));
  const tDrop = b.runTo(qb, tGot, dropD, 0, 5, "up", "backpedal", 0);
  const spotD = fumble ? Math.min(c.endD, dropD) : c.endD;
  const [rd, rw] = rootBehind(spotD, 0, 0);
  const tS = Math.max(tDrop + 0.6, SNAP_T + 2.1);
  b.move(qb, tDrop + 0.1, tS - 0.05, rd, rw, Math.abs(rd - dropD) > 0.4 ? "run" : "up", "up", 0, 0);
  linePlay(b, true, tS);
  const sacker = b.find("D", "DE", "OLB", "DT");
  b.named[sacker] = 1;
  const s = b.slots[sacker];
  b.move(sacker, SNAP_T, tS - 0.45, rd + 1.6, s.w > 0 ? 1.6 : -1.6, "run", "run");
  b.move(sacker, tS - 0.45, tS + 0.35, rd + 0.5, 0.3 * Math.sign(s.w || 1), "dive", "down", Math.PI, Math.PI);
  b.act(qb, tS, "dive", Math.PI);
  b.act(qb, tS + 0.4, "down", Math.PI);
  b.hold(qb, tS + 0.1);
  if (fumble) {
    // a bola escapa e alguém da defesa cai em cima dela no ponto oficial
    b.fly([c.endD, 1.5, BALL_REST_H], tS + 0.8, 1.1);
    const rec = b.nearest("D", c.endD, 1.5, tS, [sacker])[0];
    b.move(rec, tS - 0.1, tS + 0.75, c.endD + 0.9, 1.6, "run", "dive");
    b.act(rec, tS + 1.1, "down");
  } else {
    b.fly([c.endD, 0, BALL_REST_H], tS + 0.4, 0.1);
  }
  passDefenseLight(b, tS);
}

/** Secundária e linebackers recuam e voltam para a bola num sack ou scramble. */
function passDefenseLight(b: Builder, until: number) {
  for (const i of b.all("D", "OLB", "MLB", "CB", "FS", "SS")) {
    const s = b.slots[i];
    b.runTo(i, SNAP_T, s.d + 2.5, s.w * 0.9, JOG, "up", "backpedal");
  }
  for (const i of b.all("O", "WR", "TE")) {
    const s = b.slots[i];
    b.runTo(i, SNAP_T, Math.min(12, s.d + 10), s.w * 0.9, RUN, "up");
  }
  void until;
}

function scramble(c: Ctx) {
  const { b } = c;
  const qb = b.find("O", "QB");
  b.named[qb] = 0;
  const tGot = snapTo(b, qb);
  const q0 = b.slots[qb];
  const dropD = Math.max(q0.stance === "qbUnder" ? -7 : q0.d - 2, -(c.toOwnGoal + 8));
  const tDrop = b.runTo(qb, tGot, dropD, 0, 5, "up", "backpedal", 0);
  const side = c.e.seq % 2 === 0 ? 1 : -1;
  const t1 = b.runTo(qb, tDrop + 0.5, dropD + 1, 4 * side, RUN, "run");
  const endW = clampW(6 * side);
  const h = headingOf(c.endD - (dropD + 1), endW - 4 * side);
  const [rd, rw] = rootBehind(c.endD, endW, h);
  const tEnd = b.runTo(qb, t1, rd, rw, SPRINT, c.touchdown ? "up" : "run");
  b.hold(qb, tEnd);
  linePlay(b, true, tEnd);
  passDefenseLight(b, tEnd);
  if (c.touchdown) {
    celebrate(b, qb, tEnd, [c.endD, endW], h);
    return;
  }
  const tackler = b.nearest("D", c.endD, endW, tDrop)[0];
  tackle(b, qb, tackler, tEnd, [c.endD, endW], h);
}

/** Fumble depois da corrida ou da recepção: a bola escapa e a defesa recupera no ponto oficial. */
function fumble(c: Ctx) {
  const { b, e } = c;
  if (e.textKey.includes("sack")) return sack(c, true);
  const fromPass = e.type === "PASS" || e.textKey.includes("pass");
  let carrier: number;
  let tFrom: number;
  if (fromPass) {
    const p = passPlan(c, Math.max(4, Math.min(12, e.yards || 8)));
    carrier = p.receiver;
    b.fly([p.catchD + 0.3, p.catchW, 1.3], p.tCatch, 1 + Math.hypot(p.catchD - p.dropD, p.catchW) * 0.09, [p.dropD + 0.4, -0.2, 2.1]);
    passDefense(b, p);
    linePlay(b, true, p.tCatch);
    tFrom = p.tCatch + 0.1;
  } else {
    const qb = b.find("O", "QB");
    carrier = carrierOf(b, e);
    const tGot = snapTo(b, qb);
    if (carrier !== qb) {
      const tH = Math.max(tGot + 0.35, SNAP_T + 0.65);
      const cs = b.slots[carrier];
      b.move(carrier, SNAP_T, tH, Math.min(cs.d + 2.2, -2.6), 0.4, "run", "run");
      b.hold(qb, tH);
      tFrom = tH;
    } else tFrom = tGot;
    linePlay(b, false, tFrom + 1.5);
  }
  b.named[carrier] = 0;
  const hitD = Math.max(1, Math.min(c.toGoal - 1, fromPass ? b.at(carrier, tFrom).d + 3 : Math.max(1.5, e.yards)));
  const cw = b.at(carrier, tFrom).w;
  const tHit = b.runTo(carrier, tFrom, hitD, cw, SPRINT, "dive");
  b.hold(carrier, tHit);
  b.act(carrier, tHit + 0.4, "down");
  const hitter = b.nearest("D", hitD, cw, tFrom)[0];
  b.move(hitter, tFrom, tHit, hitD + 1, cw + 0.3, "run", "dive", undefined, Math.PI);
  b.act(hitter, tHit + 0.4, "down", Math.PI);
  const recW = clampW(cw + 1.5);
  b.fly([c.endD, recW, BALL_REST_H], tHit + 0.7, 1.2);
  const rec = b.nearest("D", c.endD, recW, tHit, [hitter])[0];
  b.named[rec] = 1;
  b.move(rec, Math.max(SNAP_T, tHit - 1.2), tHit + 0.6, c.endD + 0.8, recW + 0.2, "run", "dive");
  b.act(rec, tHit + 1, "down");
  pursue(b, b.nearest("D", c.endD, recW, tFrom, [hitter, rec]).slice(0, 4), c.endD, recW, tFrom, tHit + 0.6);
}

function kneel(c: Ctx) {
  const { b } = c;
  const qb = b.find("O", "QB");
  b.named[qb] = 0;
  snapTo(b, qb);
  const [rd] = rootBehind(c.endD, 0, 0);
  b.move(qb, SNAP_T, SNAP_T + 0.35, rd, 0, "backpedal", "kneel", 0, 0);
  b.hold(qb, SNAP_T + 0.45);
  b.fly([c.endD, 0, BALL_REST_H], SNAP_T + 0.7, 0.05);
  for (let i = 0; i < b.slots.length; i++) if (i !== qb) b.act(i, SNAP_T + 0.4, "up");
}

function spike(c: Ctx) {
  const { b } = c;
  const qb = b.find("O", "QB");
  b.named[qb] = 0;
  const tGot = snapTo(b, qb);
  b.move(qb, tGot, tGot + 0.3, b.slots[qb].d - 0.6, 0, "backpedal", "spike", 0, 0);
  b.hold(qb, tGot + 0.45);
  const land: [number, number, number] = [b.slots[qb].d + 0.2, 0.4, BALL_REST_H];
  b.fly(land, tGot + 0.6, 0);
  b.fly([land[0] + 1.2, 1.4, BALL_REST_H], tGot + 1.1, 0.9);
  b.act(qb, tGot + 0.9, "up");
  for (let i = 0; i < b.slots.length; i++) if (i !== qb) b.act(i, SNAP_T + 0.5, "up");
  respot(b, tGot + 1.6, [c.endD, 0]);
}

/** Falta: jogada parada, quem cometeu se mexe antes do snap, a flag voa e a bola é reposta com as jardas da falta. */
function penalty(c: Ctx) {
  const { b, e } = c;
  const byOffense = !!e.penalty && e.penalty.againstClubId === e.possessionClubId;
  const who = byOffense ? b.find("O", "RT", "RG", "TE", "WR") : b.find("D", "DE", "DT", "OLB");
  const s = b.slots[who];
  b.rest([-0.15, 0, BALL_REST_H], 2);
  b.move(who, 0.35, 0.65, s.side === "O" ? s.d + 0.9 : -0.4, s.w, "run", "up", s.side === "O" ? 0 : Math.PI, s.side === "O" ? 0 : Math.PI);
  for (let i = 0; i < b.slots.length; i++) if (i !== who) b.act(i, 1.2 + (i % 5) * 0.08, "up");
  const farSide = -(HALF_WIDTH_YD + 1.5);
  b.flag = { t0: 0.7, t1: 1.45, from: [-3, farSide, 2.2], to: [0.6, s.w, 0.03] };
  if (Math.abs(c.endD) > 0.05) b.fly([c.endD, 0, BALL_REST_H], 2 + Math.min(1.1, 0.5 + Math.abs(c.endD) * 0.04), 1.6 + Math.abs(c.endD) * 0.1);
}

/** Punt: snap longo, chute em arco, retorno (ou fair catch, touchback, bola morta, bloqueio, muff) e o tackle. */
function punt(c: Ctx) {
  const { b, e } = c;
  const key = e.textKey;
  const p = b.find("O", "P");
  const ls = b.find("O", "LS");
  const kr = b.find("D", "KR");
  b.named[p] = 0;
  b.named[kr] = 1;
  const p0 = b.slots[p];
  b.rest([-0.15, 0, BALL_REST_H], SNAP_T);
  const tRec = SNAP_T + 0.7;
  b.fly([p0.d + 0.4, 0, 1.0], tRec, 0.3, [-0.5, 0, 0.35]);
  const tK = tRec + 0.75;
  b.move(p, tRec, tK - 0.25, p0.d + 1.4, 0, "run", "kick", 0, 0);
  b.act(p, tK + 0.35, "up", 0);
  b.hold(p, tK);
  linePlay(b, false, tK);
  for (const i of b.all("O")) if (i !== p && i !== ls && !["LS"].includes(b.slots[i].role)) b.act(i, tK - 0.2, "up");
  const blocked = key.includes("blocked") || e.result === "PUNT_BLOCKED";
  if (blocked) {
    const rusher = b.find("D", "ST");
    b.move(rusher, SNAP_T, tK - 0.05, p0.d + 2, 0.4, "run", "dive", undefined, Math.PI);
    b.act(rusher, tK + 0.4, "down", Math.PI);
    b.fly([p0.d + 3.5, 1.2, BALL_REST_H], tK + 0.45, 1.4, [p0.d + 1.8, 0, 0.6]);
    b.fly([c.endD, 2, BALL_REST_H], b.ballEnd + 0.9, 0.9);
    const rec = b.nearest("D", c.endD, 2, tK, [rusher])[0];
    b.move(rec, tK, b.ballEnd - 0.1, c.endD + 0.8, 2.2, "run", "dive");
    b.act(rec, b.ballEnd + 0.4, "down");
    return;
  }
  const touchback = key.includes("touchback") || e.result === "TOUCHBACK";
  const downed = key.includes("downed") || e.result === "PUNT_DOWNED";
  const fair = key.includes("fairCatch") || e.result === "FAIR_CATCH";
  const muffed = key.includes("muffed") || e.result === "MUFFED";
  const returnTd = key.includes("returnTouchdown");
  const goal = c.toGoal;
  let landD: number;
  if (touchback) landD = goal + 4;
  else if (downed) landD = Math.min(goal - 1, c.endD - 5);
  else if (fair) landD = c.endD;
  else if (returnTd) landD = Math.min(goal - 4, 42);
  else landD = Math.min(goal - 1, Math.max(c.endD + 6, c.endD));
  const hang = Math.min(3.4, Math.max(2.3, Math.abs(landD - p0.d) / 15));
  const tLand = tK + hang;
  const catchable = !touchback && !downed;
  b.fly([landD, 0, catchable ? 1.3 : BALL_REST_H], tLand, Math.min(26, 12 + Math.abs(landD - p0.d) * 0.25), [p0.d + 1.6, 0, 0.9]);
  // cobertura: os gunners chegam no retornador, o resto desce em leque
  const cover = b.all("O").filter((i) => i !== p);
  cover.forEach((i, k) => {
    const s = b.at(i, tK);
    const tw = clampW(s.w * 0.35 + ((k % 5) - 2) * 2);
    b.move(i, tK - 0.1, tLand + 0.3, landD - 2 - (Math.abs(s.w) > 15 ? 0 : 6 + (k % 3) * 2), tw, "run", "up");
  });
  const blockers = b.all("D").filter((i) => i !== kr);
  blockers.forEach((i, k) => {
    const s = b.at(i, tK);
    b.move(i, tK, tLand, Math.min(goal + 5, landD - 12 + (k % 3) * 2), clampW(s.w * 0.5), "run", "up", undefined, Math.PI);
  });
  b.move(kr, SNAP_T, tLand - 0.3, landD + 0.4, 0, "run", catchable ? "catch" : "up", Math.PI, Math.PI);
  if (touchback) {
    b.fly([goal + 7, 1.5, BALL_REST_H], tLand + 0.6, 0.7);
    b.act(kr, tLand, "up", Math.PI);
    respot(b, tLand + 1.1, [c.endD, 0]);
    return;
  }
  if (downed) {
    b.fly([c.endD, 0.8, BALL_REST_H], tLand + 0.9, 0.5);
    const gunner = b.nearest("O", c.endD, 0.8, tLand)[0];
    b.move(gunner, tLand, tLand + 1, c.endD - 0.8, 0.6, "run", "up");
    return;
  }
  if (fair) {
    b.hold(kr, tLand + 0.8);
    b.act(kr, tLand + 0.3, "up", Math.PI);
    b.fly([c.endD, 0, BALL_REST_H], tLand + 1.1, 0.3);
    return;
  }
  if (muffed) {
    b.act(kr, tLand + 0.1, "dive", Math.PI);
    b.act(kr, tLand + 0.6, "down", Math.PI);
    b.fly([c.endD, 1.5, BALL_REST_H], tLand + 0.9, 0.8);
    const rec = b.nearest("O", c.endD, 1.5, tLand)[0];
    b.move(rec, tLand, tLand + 0.8, c.endD - 0.8, 1.6, "run", "dive");
    b.act(rec, tLand + 1.1, "down");
    return;
  }
  // retorno: o retornador volta no sentido de quem chutou (−d)
  b.hold(kr, tLand);
  const endW = clampW((e.seq % 2 ? 4 : -4));
  const h = headingOf(c.endD - landD, endW);
  const [rd, rw] = rootBehind(c.endD, endW, h);
  const tEnd = b.runTo(kr, tLand + 0.1, rd, rw, SPRINT, returnTd ? "up" : "run");
  b.hold(kr, tEnd);
  if (returnTd) {
    celebrate(b, kr, tEnd, [c.endD, endW], h);
    return;
  }
  const tackler = b.nearest("O", c.endD, endW, tLand, [p])[0];
  tackle(b, kr, tackler, tEnd, [c.endD, endW], h);
}

/** Kickoff (também onside e free kick): corrida do kicker, chute do tee, retorno ou touchback. */
function kickoff(c: Ctx) {
  const { b, e } = c;
  const key = e.textKey;
  const k = b.find("O", "K");
  b.named[k] = 0;
  const tK = 1.25;
  b.rest([0, 0, 0.25], tK);
  b.move(k, 0.2, tK - 0.2, -0.6, 0, "run", "kick", 0, 0);
  b.act(k, tK + 0.35, "up", 0);
  const cover = b.all("O").filter((i) => i !== k);
  const onside = key.includes("onside") || e.type === "ONSIDE_KICK";
  if (onside) {
    const recovered = key.includes("recovered") || e.result === "ONSIDE_RECOVERED";
    b.fly([7, 3, BALL_REST_H], tK + 0.45, 0.5, [0, 0, 0.25]);
    b.fly([c.endD, 4, BALL_REST_H], tK + 1.2, 1.4);
    const side = recovered ? "O" : "D";
    const rec = b.nearest(side, c.endD, 4, tK)[0];
    b.named[rec] = 1;
    b.move(rec, tK - 0.2, tK + 1.1, c.endD + (side === "O" ? -0.8 : 0.8), 4.2, "run", "dive");
    b.act(rec, tK + 1.5, "down");
    cover.filter((i) => i !== rec).forEach((i) => {
      const s = b.at(i, tK);
      b.runTo(i, tK - 0.3, Math.min(11, c.endD), clampW(s.w * 0.6), RUN, "up");
    });
    for (const i of b.all("D").filter((x) => x !== rec)) {
      const s = b.at(i, tK);
      b.runTo(i, tK + 0.2, Math.max(9, s.d - 2), clampW(s.w * 0.7), RUN, "up");
    }
    return;
  }
  const touchback = key.includes("touchback") || e.result === "TOUCHBACK";
  const returnTd = key.includes("returnTouchdown");
  const goal = c.toGoal;
  const krs = b.all("D", "KR");
  const kr = krs[e.seq % krs.length] ?? krs[0];
  b.named[kr] = 1;
  const landD = touchback ? goal + 6 : Math.min(goal + 2, Math.max(c.endD + 8, goal - 6));
  const hang = Math.min(3.3, Math.max(2.4, landD / 22));
  const tLand = tK + hang;
  b.fly([landD, 0, touchback ? BALL_REST_H : 1.3], tLand, 20, [0, 0, 0.25]);
  cover.forEach((i, n) => {
    const s = b.at(i, tK);
    b.move(i, tK - 0.5, tLand + 0.4, Math.min(landD - 10 - (n % 3) * 3, goal - 4), clampW(s.w * 0.45), "run", "up");
  });
  for (const i of b.all("D").filter((x) => !krs.includes(x))) {
    const s = b.at(i, tK);
    b.move(i, tK + 0.2, tLand, Math.min(goal - 2, landD - 14 + (i % 3) * 2), clampW(s.w * 0.6), "run", "up", undefined, Math.PI);
  }
  for (const other of krs.filter((x) => x !== kr)) b.runTo(other, tK + 0.3, landD - 6, b.slots[other].w * 1.5, JOG, "up");
  b.move(kr, tK + 0.2, tLand - 0.3, landD + 0.4, 0, "run", touchback ? "kneel" : "catch", Math.PI, Math.PI);
  if (touchback) {
    b.fly([goal + 7.5, 1, BALL_REST_H], tLand + 0.5, 0.6);
    respot(b, tLand + 1.0, [c.endD, 0]);
    return;
  }
  b.hold(kr, tLand);
  const endW = clampW(e.seq % 2 ? 5 : -5);
  const h = headingOf(c.endD - landD, endW);
  const [rd, rw] = rootBehind(c.endD, endW, h);
  const tEnd = b.runTo(kr, tLand + 0.1, rd, rw, SPRINT, returnTd ? "up" : "run");
  b.hold(kr, tEnd);
  if (returnTd) {
    celebrate(b, kr, tEnd, [c.endD, endW], h);
    return;
  }
  const tackler = b.nearest("O", c.endD, endW, tLand, [k])[0];
  tackle(b, kr, tackler, tEnd, [c.endD, endW], h);
}

/** Field goal e extra point: snap para o holder, chute e a bola passando (ou não) pelas traves. */
function fieldGoal(c: Ctx) {
  const { b, e } = c;
  const key = e.textKey;
  const h = b.find("O", "H");
  const k = b.find("O", "K");
  b.named[k] = 0;
  const hold: [number, number, number] = [b.slots[h].d + 0.3, 0, 0.22];
  b.rest([-0.15, 0, BALL_REST_H], SNAP_T);
  b.fly(hold, SNAP_T + 0.45, 0.2, [-0.5, 0, 0.35]);
  const tK = SNAP_T + 1.25;
  b.rest(hold, tK);
  b.move(k, SNAP_T + 0.3, tK - 0.2, hold[0] - 0.7, -0.45, "run", "kick", 0.3, 0.1);
  b.act(k, tK + 0.4, "up", 0);
  linePlay(b, false, tK + 0.6);
  for (const i of b.all("O", "TE", "ST")) b.act(i, SNAP_T, "block", 0);
  for (const i of b.all("D", "ST")) {
    const s = b.slots[i];
    b.move(i, SNAP_T, SNAP_T + 0.6, s.d - 0.4, s.w, "block", "block", Math.PI, Math.PI);
    b.act(i, tK + 0.6, "up", Math.PI);
  }
  const blocked = key.includes("blocked") || e.result === "FIELD_GOAL_BLOCKED";
  if (blocked) {
    const rusher = b.find("D", "ST");
    b.move(rusher, SNAP_T, tK - 0.05, hold[0] + 2, 0.4, "run", "dive", undefined, Math.PI);
    b.act(rusher, tK + 0.4, "down", Math.PI);
    b.fly([hold[0] + 2.5, 1, BALL_REST_H], tK + 0.4, 1.2, [hold[0] + 0.5, 0, 0.5]);
    respot(b, tK + 0.9, [c.endD, 0]);
    return;
  }
  const good = e.result === "FIELD_GOAL_GOOD" || e.result === "EXTRA_POINT_GOOD" || key.includes(".good");
  // as traves ficam na linha de fundo: a bola passa por cima do travessão e cai do outro lado
  const postD = c.toGoal + FIELD.endZoneYd;
  const landD = postD + 4;
  const halfPosts = GOALPOST_M.width / 2 / YARD_M;
  const wide = good ? 0 : (e.seq % 2 ? 1 : -1) * (halfPosts + 2.2);
  const from = hold;
  const to: [number, number, number] = [good ? c.endD : landD, wide, BALL_REST_H];
  const u = (postD - from[0]) / (to[0] - from[0]);
  const need = GOALPOST_M.crossbar + 2.2;
  const apex = Math.max(6, (need - (from[2] + (to[2] - from[2]) * u)) / Math.max(0.05, 4 * u * (1 - u)));
  b.fly(to, tK + Math.min(2.4, Math.max(1.2, (to[0] - from[0]) / 24)), apex, from);
  if (!good) respot(b, b.ballEnd + 0.4, [c.endD, 0]);
}

/** Conversão de 2 pontos: passe curto para a end zone ou corrida, pelo conceito do lance. */
function twoPoint(c: Ctx) {
  const pass = c.e.concept != null && ["SHORT_PASS", "MEDIUM_PASS", "DEEP_PASS", "SCREEN", "PLAY_ACTION"].includes(c.e.concept);
  const good = c.e.result === "TWO_POINT_GOOD" || c.e.textKey.includes(".good");
  if (pass) {
    if (!good) return passIncomplete(c);
    return passComplete(c, c.toGoal);
  }
  return runScript(c);
}

/* ───────────────────────── entrada ───────────────────────── */

const other = (club: string | null, home: string, away: string) => (club === home ? away : home);

/** Quem chuta no kickoff: o motor grava a posse de quem chuta; o mock, de quem recebe. A posse seguinte desempata. */
function kickingClub(e: PlayEvent, next: NextSituation | null, home: string, away: string): string {
  const keeps = e.textKey.includes("onside.recovered") || e.textKey.includes("kickoff.fumble") || e.result === "ONSIDE_RECOVERED";
  if (next?.possessionClubId) return keeps ? next.possessionClubId : other(next.possessionClubId, home, away);
  return e.possessionClubId ?? home;
}

function formationKind(kind: ScriptKind, e: PlayEvent): FormationKind {
  if (kind === "KICKOFF") return e.textKey.includes("onside") || e.type === "ONSIDE_KICK" ? "ONSIDE" : "KICKOFF";
  if (kind === "PUNT") return "PUNT";
  if (kind === "FIELD_GOAL") return "FIELD_GOAL";
  if (kind === "KNEEL") return "KNEEL";
  return "SCRIMMAGE";
}

/** Roteiro do lance; null para os administrativos. */
export function buildScript({ event: e, next, homeClubId, awayClubId }: ScriptInput): PlayScript | null {
  const kind = kindOf(e);
  if (kind == null) return null;
  let offense: string;
  let los: number;
  if (kind === "KICKOFF") {
    offense = kickingClub(e, next, homeClubId, awayClubId);
    const own = e.type === "FREE_KICK" || e.textKey.includes("freeKick") ? 20 : 35;
    los = e.possessionClubId === offense ? e.ballOnAbsYd : offense === homeClubId ? own : FIELD.playYd - own;
  } else {
    offense = e.possessionClubId ?? homeClubId;
    los = e.ballOnAbsYd;
  }
  const dir: AttackDir = offense === homeClubId ? 1 : -1;
  const toGoal = dir === 1 ? FIELD.playYd - los : los;
  const toOwnGoal = FIELD.playYd - toGoal;
  const slots = formationFor({
    kind: formationKind(kind, e),
    formation: kind === "TWO_POINT" && !e.formation ? (e.concept && e.concept.includes("PASS") ? "SHOTGUN" : "GOAL_LINE") : e.formation,
    concept: e.concept,
    type: e.type,
    defensePackage: kind === "TWO_POINT" && !e.defensePackage ? "GOAL_LINE" : e.defensePackage,
    coverage: e.coverage,
    blitz: e.blitz,
    toGoal,
    toOwnGoal,
  });
  const endAbsYd = endSpot({ event: e, kind, dir, losAbsYd: los, offenseClubId: offense, next });
  const endD = (endAbsYd - los) * dir;
  const goalD = toGoal;
  const scoredByOffense = e.points >= 6 && (e.scoringClubId == null || e.scoringClubId === offense);
  const touchdown = (scoredByOffense && Math.abs(endD - goalD) < 0.01) || (kind === "TWO_POINT" && Math.abs(endD - goalD) < 0.01);
  const b = new Builder(slots);
  const c: Ctx = { b, e, endD, toGoal, toOwnGoal, touchdown };
  switch (kind) {
    case "RUN":
    case "GENERIC":
      runScript(c);
      break;
    case "PASS_COMPLETE":
      passComplete(c, endD);
      break;
    case "PASS_INCOMPLETE":
      passIncomplete(c);
      break;
    case "INTERCEPTION":
      interception(c);
      break;
    case "SACK":
      sack(c, false);
      break;
    case "SCRAMBLE":
      scramble(c);
      break;
    case "FUMBLE":
      fumble(c);
      break;
    case "KNEEL":
      kneel(c);
      break;
    case "SPIKE":
      spike(c);
      break;
    case "PENALTY":
      penalty(c);
      break;
    case "PUNT":
      punt(c);
      break;
    case "KICKOFF":
      kickoff(c);
      break;
    case "FIELD_GOAL":
      fieldGoal(c);
      break;
    case "TWO_POINT":
      twoPoint(c);
      break;
  }
  return finish(b, {
    seq: e.seq,
    kind,
    los,
    dir,
    offenseClubId: offense,
    defenseClubId: other(offense, homeClubId, awayClubId),
    endAbsYd,
    endD,
    touchdown: touchdown || (e.points >= 6 && Math.abs(endD + toOwnGoal) < 0.01),
  });
}

/**
 * Fecha o roteiro: a bola termina parada no ponto oficial, todos param, e a duração fica entre 3 e 6 s (lances longos são
 * acelerados por igual; curtos ganham pausa no fim).
 */
function finish(b: Builder, base: Omit<PlayScript, "slots" | "tracks" | "ball" | "flag" | "duration" | "named">): PlayScript {
  // ponto final exato (sem depender do arredondamento das trajetórias)
  const last = b.ball[b.ball.length - 1];
  const end: [number, number, number] = [base.endD, last ? (last.kind === "rest" ? last.at[1] : last.kind === "flight" ? last.to[1] : 0) : 0, BALL_REST_H];
  if (!last || last.kind === "held") b.fly(end, b.ballEnd + 0.3, 0.1);
  else if (last.kind === "flight") last.to = end;
  else last.at = end;
  let natural = b.ballEnd;
  for (const k of b.tracks) natural = Math.max(natural, k[k.length - 1].t);
  if (b.flag) natural = Math.max(natural, b.flag.t1);
  natural += HOLD;
  const duration = Math.min(MAX_DURATION, Math.max(MIN_DURATION, natural));
  const scale = natural > MAX_DURATION ? MAX_DURATION / natural : 1;
  const tracks = b.tracks.map((kfs) => {
    const out = kfs.map((k) => ({ ...k, t: k.t * scale }));
    const lastK = out[out.length - 1];
    if (MOVING.includes(lastK.act)) lastK.act = "up";
    // um quadro no fim: parados até o roteiro acabar
    if (lastK.t < duration) out.push({ ...lastK, t: duration });
    return out;
  });
  const ball: BallSeg[] = b.ball.map((s) => ({ ...s, t0: s.t0 * scale, t1: s.t1 * scale }));
  ball.push({ kind: "rest", t0: ball[ball.length - 1].t1, t1: Number.POSITIVE_INFINITY, at: end });
  const flag = b.flag ? { ...b.flag, t0: b.flag.t0 * scale, t1: b.flag.t1 * scale } : null;
  return { ...base, slots: b.slots, tracks, ball, flag, duration, named: b.named };
}

/** Pré-snap do próximo lance (sem encenação): a formação na linha atual, todos parados, a bola no chão. */
export function presnapScript(input: { possessionClubId: string; ballOnAbsYd: number; homeClubId: string; awayClubId: string }): PlayScript {
  const dir: AttackDir = input.possessionClubId === input.homeClubId ? 1 : -1;
  const toGoal = dir === 1 ? FIELD.playYd - input.ballOnAbsYd : input.ballOnAbsYd;
  const slots = formationFor({ kind: "SCRIMMAGE", formation: "SINGLEBACK", defensePackage: "BASE_4_3", toGoal, toOwnGoal: FIELD.playYd - toGoal });
  const b = new Builder(slots);
  return {
    seq: 0,
    kind: "GENERIC",
    los: input.ballOnAbsYd,
    dir,
    offenseClubId: input.possessionClubId,
    defenseClubId: other(input.possessionClubId, input.homeClubId, input.awayClubId),
    slots,
    tracks: b.tracks.map((k) => [...k, { ...k[0], t: MIN_DURATION }]),
    ball: [{ kind: "rest", t0: 0, t1: Number.POSITIVE_INFINITY, at: [-0.15, 0, BALL_REST_H] }],
    flag: null,
    duration: 0,
    endAbsYd: input.ballOnAbsYd,
    endD: 0,
    touchdown: false,
    named: {},
  };
}

/* ───────────────────────── entre um lance e outro ───────────────────────── */

export interface WorldActor {
  clubId: string;
  x: number;
  z: number;
}

/** Clube de cada vaga do roteiro. */
export function clubOfSlot(s: PlayScript, i: number): string {
  return s.slots[i].side === "O" ? s.offenseClubId : s.defenseClubId;
}

/** Onde cada atleta e a bola estão no mundo no instante `t` do roteiro (o quadro que está na tela). */
export function worldSnapshot(s: PlayScript, t: number): { actors: WorldActor[]; ball: [number, number, number] } {
  const actors = s.tracks.map((kfs, i) => {
    const p = sampleTrack(kfs, t);
    const [x, z] = offenseToWorld(s.los, s.dir, p.d, p.w);
    return { clubId: clubOfSlot(s, i), x, z };
  });
  const b = sampleBall(s.ball, t, (i, tt) => sampleTrack(s.tracks[i], tt));
  const [bx, bz] = offenseToWorld(s.los, s.dir, b.d, b.w);
  return { actors, ball: [bx, b.h, bz] };
}

/** Velocidade de quem volta para a formação (jardas/s). */
const RESET_SPEED = 9;
export const MIN_RESET = 0.6;
export const MAX_RESET = 3;

/**
 * Transição sem teleporte para a formação de `to`: cada atleta sai de onde está na tela e trota até a vaga do mesmo time em
 * `to` (o k-ésimo de um time vai para a k-ésima vaga desse time), terminando na postura do pré-snap; a bola vai do ponto onde
 * ficou até a linha nova num arco curto. A duração cresce com a maior distância (de 0,6 a 3 s).
 */
export function transitionScript(from: { actors: WorldActor[]; ball: [number, number, number] }, to: PlayScript): PlayScript {
  const toInv = (x: number, z: number) => worldToOffense(to.los, to.dir, x, z);
  const pools = new Map<string, WorldActor[]>();
  for (const a of from.actors) pools.set(a.clubId, [...(pools.get(a.clubId) ?? []), a]);
  const starts = to.slots.map((s, i) => {
    const pool = pools.get(clubOfSlot(to, i));
    const a = pool?.shift();
    return a ? toInv(a.x, a.z) : ([s.d, s.w] as [number, number]);
  });
  const far = Math.max(0, ...starts.map(([d, w], i) => Math.hypot(d - to.slots[i].d, w - to.slots[i].w)));
  const duration = Math.min(MAX_RESET, Math.max(MIN_RESET, far / RESET_SPEED + 0.4));
  const tracks: Keyframe[][] = to.slots.map((s, i) => {
    const [d0, w0] = starts[i];
    const moving = Math.hypot(d0 - s.d, w0 - s.w) > 0.3;
    const face = s.side === "O" ? 0 : Math.PI;
    return [
      { t: 0, d: d0, w: w0, act: moving ? "run" : "up" },
      { t: Math.max(0.2, duration - 0.35), d: s.d, w: s.w, act: s.stance, face },
      { t: duration, d: s.d, w: s.w, act: s.stance, face },
    ];
  });
  const [bd, bw] = toInv(from.ball[0], from.ball[2]);
  const start = sampleBall(to.ball, 0, (i, tt) => sampleTrack(to.tracks[i], tt));
  const ballMoves = Math.hypot(bd - start.d, bw - start.w) > 0.05;
  const ball: BallSeg[] = ballMoves
    ? [
        { kind: "flight", t0: 0, t1: Math.max(0.3, duration - 0.2), from: [bd, bw, Math.max(BALL_REST_H, from.ball[1])], to: [start.d, start.w, start.h], apex: Math.min(5, 0.6 + Math.hypot(bd - start.d, bw - start.w) * 0.1) },
        { kind: "rest", t0: Math.max(0.3, duration - 0.2), t1: Number.POSITIVE_INFINITY, at: [start.d, start.w, start.h] },
      ]
    : [{ kind: "rest", t0: 0, t1: Number.POSITIVE_INFINITY, at: [start.d, start.w, start.h] }];
  return { ...to, tracks, ball, flag: null, duration, touchdown: false };
}
