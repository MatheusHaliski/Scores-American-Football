// Encenação dos lances no campo 3D (D-140..): formação por tipo de jogada (11 × 11, lados certos, ninguém em impedimento),
// roteiro de cada tipo de evento do motor (as 48 chaves de match-i18n-keys.json; tipo desconhecido cai no genérico), bola
// terminando no ponto oficial (jardas do evento a partir da linha, igual à próxima linha de scrimmage, nos dois sentidos),
// escolha do lance a encenar quando chegam vários, quem veste cada vaga e a pose dos atletas apoiada no chão.
// Usa os goldens (match-state, match-events, match-i18n-keys) e uma partida inteira do mock.
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { matchEventsFromDto, matchStateFromDto } from "@/lib/api/live-match";
import type { MatchState, PlayEvent, PlayerBoxScore } from "@/lib/api/match-types";
import { createMockApi } from "@/lib/api/mock";
import { lastSeqOf, mergeEvents } from "@/lib/match/live";
import { DEFENSE_PACKAGES, formationFor, KICK_RECEIVE_YD, NEUTRAL_YD, OFFENSE_FORMATIONS, type FormationKind } from "@/lib/match/scene/formation";
import { endSpot, KIND_BY_KEY, kindOf, lastStageable, nextSituation, pickStage } from "@/lib/match/scene/kinds";
import { buildScript, MAX_DURATION, MIN_DURATION, presnapScript, type PlayScript } from "@/lib/match/scene/script";
import { sampleBall, sampleTrack } from "@/lib/match/scene/track";
import { lineupFor } from "@/lib/match/scene/lineup";
import { actionPose, poseOf, skeleton } from "@/lib/match/scene/athlete";
import { loadGolden } from "../golden.mts";

const HOME = "00000000-0000-4000-8000-0000000000aa";
const AWAY = "00000000-0000-4000-8000-0000000000bb";
const keys = (loadGolden("match-i18n-keys.json") as { events: { textKey: string; params: Record<string, string> }[] }).events;
const state = matchStateFromDto(loadGolden("match-state.json"));
const page = matchEventsFromDto(loadGolden("match-events.json"));

function ev(over: Partial<PlayEvent>): PlayEvent {
  return {
    seq: 10, drive: 2, quarter: 1, clockSeconds: 600, possessionClubId: HOME, down: 1, distance: 10, ballOnAbsYd: 30, type: "RUN", concept: "INSIDE_RUN",
    formation: "I_FORM", defensePackage: "BASE_4_3", coverage: "COVER_3", blitz: false, yards: 4, result: "GAIN", firstDown: false, turnover: false, points: 0,
    scoringClubId: null, penalty: null, playerIds: [], textKey: "match:event.run.gain", params: {}, homeScore: 0, awayScore: 0, clockStopped: false, driveEnded: null, callSource: "AI",
    ...over,
  };
}

/* ───────────────────────── formação ───────────────────────── */

test("formação: 11 × 11 em toda formação e pacote do motor, ataque atrás da linha e defesa à frente (ninguém em impedimento)", () => {
  for (const formation of OFFENSE_FORMATIONS) {
    for (const defensePackage of DEFENSE_PACKAGES) {
      for (const toGoal of [99, 50, 3]) {
        const slots = formationFor({ kind: "SCRIMMAGE", formation, defensePackage, coverage: "COVER_2", blitz: true, toGoal, toOwnGoal: 100 - toGoal });
        const o = slots.filter((s) => s.side === "O");
        const d = slots.filter((s) => s.side === "D");
        assert.equal(o.length, 11, `${formation}: 11 no ataque`);
        assert.equal(d.length, 11, `${defensePackage}: 11 na defesa`);
        assert.ok(o.every((s) => s.d <= -NEUTRAL_YD), `${formation}: ataque atrás da linha`);
        assert.ok(d.every((s) => s.d >= NEUTRAL_YD), `${defensePackage}: defesa à frente da linha`);
        assert.equal(o.filter((s) => s.role === "QB").length, 1);
        assert.ok(slots.every((s) => s.d <= toGoal + 10 && s.d >= -(100 - toGoal) - 10), "dentro do gramado (end zones inclusive)");
      }
    }
  }
  // o pessoal muda: I com fullback, empty sem running back, dime com 4 corners e 1 linebacker
  const i = formationFor({ kind: "SCRIMMAGE", formation: "I_FORM", toGoal: 70, toOwnGoal: 30 });
  assert.ok(i.some((s) => s.role === "FB"));
  const empty = formationFor({ kind: "SCRIMMAGE", formation: "EMPTY", toGoal: 70, toOwnGoal: 30 });
  assert.equal(empty.filter((s) => s.role === "RB" || s.role === "FB").length, 0);
  const dime = formationFor({ kind: "SCRIMMAGE", formation: "SHOTGUN", defensePackage: "DIME", toGoal: 70, toOwnGoal: 30 });
  assert.equal(dime.filter((s) => s.role === "CB").length, 4);
  assert.equal(dime.filter((s) => s.role === "MLB" || s.role === "OLB").length, 1);
});

test("formação dos times especiais: kickoff (quem recebe a 10+ jardas), punt, field goal e kneel com 11 × 11", () => {
  for (const kind of ["KICKOFF", "ONSIDE", "PUNT", "FIELD_GOAL", "KNEEL"] as FormationKind[]) {
    const slots = formationFor({ kind, toGoal: 65, toOwnGoal: 35 });
    assert.equal(slots.filter((s) => s.side === "O").length, 11, kind);
    assert.equal(slots.filter((s) => s.side === "D").length, 11, kind);
    assert.ok(slots.filter((s) => s.side === "O").every((s) => s.d < 0), `${kind}: quem chuta atrás da linha`);
    if (kind === "KICKOFF" || kind === "ONSIDE") assert.ok(slots.filter((s) => s.side === "D").every((s) => s.d >= KICK_RECEIVE_YD), `${kind}: quem recebe a 10 jardas`);
    else assert.ok(slots.filter((s) => s.side === "D").every((s) => s.d > 0), kind);
  }
  const punt = formationFor({ kind: "PUNT", toGoal: 60, toOwnGoal: 40 });
  assert.ok(punt.some((s) => s.role === "P" && s.d <= -13), "punter 14 jardas atrás");
  const fg = formationFor({ kind: "FIELD_GOAL", toGoal: 20, toOwnGoal: 80 });
  assert.ok(fg.some((s) => s.role === "H") && fg.some((s) => s.role === "K"));
});

/* ───────────────────────── tipo de evento → roteiro ───────────────────────── */

test("cada chave do motor tem roteiro (ou é administrativa); chave e tipo desconhecidos caem no genérico", () => {
  const golden = new Set(keys.map((k) => k.textKey));
  assert.equal(golden.size, 48);
  for (const key of golden) assert.ok(Object.prototype.hasOwnProperty.call(KIND_BY_KEY, key), `${key} sem roteiro definido`);
  const admin = [...golden].filter((k) => KIND_BY_KEY[k] == null);
  assert.deepEqual(admin.sort(), ["match:event.coinToss", "match:event.endOfHalf", "match:event.endOfOvertime", "match:event.endOfQuarter", "match:event.endOfRegulation", "match:event.final", "match:event.timeout", "match:event.twoMinuteWarning"].sort());
  assert.equal(kindOf({ textKey: "match:event.pass.interception", type: "PASS", result: "INTERCEPTION" }), "INTERCEPTION");
  assert.equal(kindOf({ textKey: "match:event.kickoff.return", type: "KICKOFF", result: "KICK_RETURNED" }), "KICKOFF");
  // chave nova do motor que o front não conhece: vai pelo tipo/resultado
  assert.equal(kindOf({ textKey: "match:event.run.newThing", type: "RUN", result: "GAIN" }), "RUN");
  assert.equal(kindOf({ textKey: "match:event.pass.newThing", type: "PASS", result: "SACK" }), "SACK");
  assert.equal(kindOf({ textKey: "match:event.x", type: "NO_PLAY", result: "PENALTY" }), "PENALTY");
  assert.equal(kindOf({ textKey: "match:event.x", type: "TIMEOUT", result: "TIMEOUT" }), null);
  assert.equal(kindOf({ textKey: "match:event.x", type: "HOOK_AND_LADDER", result: "WHATEVER" }), "GENERIC");
  assert.equal(kindOf({ textKey: "match:event.x", type: null, result: null }), "GENERIC");
});

/** Roteiro de um evento sintético para uma chave do golden, com o resultado e o tipo coerentes. */
function eventForKey(textKey: string): PlayEvent {
  const tail = textKey.replace("match:event.", "");
  const base: Partial<PlayEvent> = { textKey, playerIds: ["p1", "p2", "p3"] };
  const table: Record<string, Partial<PlayEvent>> = {
    "kickoff.touchback": { type: "KICKOFF", result: "TOUCHBACK", ballOnAbsYd: 35, down: 0, distance: 0 },
    "kickoff.return": { type: "KICKOFF", result: "KICK_RETURNED", ballOnAbsYd: 35, yards: 24 },
    "kickoff.returnTouchdown": { type: "KICKOFF", result: "TOUCHDOWN", ballOnAbsYd: 35, points: 6, scoringClubId: AWAY },
    "kickoff.fumble": { type: "KICKOFF", result: "FUMBLE_LOST", ballOnAbsYd: 35, turnover: true },
    "onside.recovered": { type: "ONSIDE_KICK", result: "ONSIDE_RECOVERED", ballOnAbsYd: 35, turnover: true },
    "onside.lost": { type: "ONSIDE_KICK", result: "ONSIDE_LOST", ballOnAbsYd: 35 },
    "freeKick.return": { type: "FREE_KICK", result: "KICK_RETURNED", ballOnAbsYd: 20 },
    "run.gain": { type: "RUN", result: "GAIN", yards: 6 },
    "run.touchdown": { type: "RUN", result: "TOUCHDOWN", ballOnAbsYd: 92, yards: 8, points: 6, scoringClubId: HOME },
    "run.fumble": { type: "RUN", result: "FUMBLE_LOST", yards: 3, turnover: true },
    "run.safety": { type: "RUN", result: "SAFETY", ballOnAbsYd: 2, yards: -2, points: 2, scoringClubId: AWAY },
    "pass.complete": { type: "PASS", result: "COMPLETE", concept: "MEDIUM_PASS", formation: "SHOTGUN", yards: 14 },
    "pass.touchdown": { type: "PASS", result: "TOUCHDOWN", concept: "DEEP_PASS", ballOnAbsYd: 70, yards: 30, points: 6, scoringClubId: HOME },
    "pass.incomplete": { type: "PASS", result: "INCOMPLETE", concept: "SHORT_PASS", yards: 0 },
    "pass.drop": { type: "PASS", result: "INCOMPLETE", concept: "SHORT_PASS", yards: 0 },
    "pass.defended": { type: "PASS", result: "INCOMPLETE", concept: "DEEP_PASS", yards: 0 },
    "pass.fumble": { type: "PASS", result: "FUMBLE_LOST", concept: "SHORT_PASS", yards: 7, turnover: true },
    "pass.interception": { type: "PASS", result: "INTERCEPTION", concept: "MEDIUM_PASS", turnover: true },
    "pass.interceptionTouchdown": { type: "PASS", result: "INTERCEPTION", concept: "SHORT_PASS", turnover: true, points: 6, scoringClubId: AWAY },
    sack: { type: "PASS", result: "SACK", concept: "DEEP_PASS", yards: -7 },
    "sack.fumble": { type: "PASS", result: "FUMBLE_LOST", yards: -6, turnover: true },
    "sack.safety": { type: "PASS", result: "SAFETY", ballOnAbsYd: 4, yards: -4, points: 2, scoringClubId: AWAY },
    scramble: { type: "PASS", result: "SCRAMBLE", yards: 9 },
    "punt.return": { type: "PUNT", result: "KICK_RETURNED", concept: "PUNT", yards: 44 },
    "punt.returnTouchdown": { type: "PUNT", result: "TOUCHDOWN", concept: "PUNT", yards: 44, points: 6, scoringClubId: AWAY },
    "punt.fairCatch": { type: "PUNT", result: "FAIR_CATCH", concept: "PUNT", yards: 42 },
    "punt.touchback": { type: "PUNT", result: "TOUCHBACK", concept: "PUNT", ballOnAbsYd: 55, yards: 45 },
    "punt.downed": { type: "PUNT", result: "PUNT_DOWNED", concept: "PUNT", ballOnAbsYd: 50, yards: 42 },
    "punt.blocked": { type: "PUNT", result: "PUNT_BLOCKED", concept: "PUNT", yards: -8, turnover: true },
    "punt.muffed": { type: "PUNT", result: "MUFFED", concept: "PUNT", yards: 40, turnover: true },
    "fieldGoal.good": { type: "FIELD_GOAL", result: "FIELD_GOAL_GOOD", concept: "FIELD_GOAL", ballOnAbsYd: 75, points: 3, scoringClubId: HOME },
    "fieldGoal.missed": { type: "FIELD_GOAL", result: "FIELD_GOAL_MISSED", concept: "FIELD_GOAL", ballOnAbsYd: 70 },
    "fieldGoal.blocked": { type: "FIELD_GOAL", result: "FIELD_GOAL_BLOCKED", concept: "FIELD_GOAL", ballOnAbsYd: 70 },
    "extraPoint.good": { type: "EXTRA_POINT", result: "EXTRA_POINT_GOOD", ballOnAbsYd: 85, points: 1, scoringClubId: HOME, down: 0 },
    "extraPoint.missed": { type: "EXTRA_POINT", result: "EXTRA_POINT_MISSED", ballOnAbsYd: 85, down: 0 },
    "twoPoint.good": { type: "TWO_POINT", result: "TWO_POINT_GOOD", concept: "SHORT_PASS", ballOnAbsYd: 98, points: 2, scoringClubId: HOME },
    "twoPoint.failed": { type: "TWO_POINT", result: "TWO_POINT_FAILED", concept: "INSIDE_RUN", ballOnAbsYd: 98, yards: 1 },
    kneel: { type: "KNEEL", result: "KNEEL", concept: "KNEEL", yards: -1 },
    spike: { type: "SPIKE", result: "SPIKE", concept: "SPIKE", yards: 0 },
    penalty: { type: "NO_PLAY", result: "PENALTY", yards: -5, penalty: { code: "FALSE_START", againstClubId: HOME, playerId: null, yards: 5, accepted: true, automaticFirstDown: false, lossOfDown: false, preSnap: true } },
  };
  return ev({ ...base, ...(table[tail] ?? { type: "TIMEOUT", result: "TIMEOUT" }) });
}

/** Próxima situação coerente com o evento sintético (onde o outro time pega a bola nos chutes e turnovers). */
function nextFor(e: PlayEvent): { possessionClubId: string; ballOnAbsYd: number } {
  const key = e.textKey;
  if (key.includes("kickoff.touchback")) return { possessionClubId: AWAY, ballOnAbsYd: 75 };
  if (key.includes("kickoff") || key.includes("freeKick")) return { possessionClubId: key.includes("fumble") ? HOME : AWAY, ballOnAbsYd: 70 };
  if (key.includes("onside.recovered")) return { possessionClubId: HOME, ballOnAbsYd: 46 };
  if (key.includes("onside.lost")) return { possessionClubId: AWAY, ballOnAbsYd: 47 };
  if (key.includes("punt.touchback")) return { possessionClubId: AWAY, ballOnAbsYd: 80 };
  if (key.includes("punt")) return { possessionClubId: key.includes("muffed") ? HOME : AWAY, ballOnAbsYd: key.includes("blocked") ? 22 : 72 };
  if (key.includes("fieldGoal.missed") || key.includes("fieldGoal.blocked")) return { possessionClubId: AWAY, ballOnAbsYd: 63 };
  if (e.turnover) return { possessionClubId: AWAY, ballOnAbsYd: e.ballOnAbsYd + Math.max(0, e.yards) };
  return { possessionClubId: HOME, ballOnAbsYd: e.ballOnAbsYd + e.yards };
}

/** Confere um roteiro: 22 atletas, duração de 3 a 6 s, tempo sempre andando, todos parados no fim e a bola no ponto oficial. */
function checkScript(s: PlayScript, what: string) {
  assert.equal(s.slots.length, 22, what);
  assert.equal(s.tracks.length, 22, what);
  assert.ok(s.duration >= MIN_DURATION - 1e-9 && s.duration <= MAX_DURATION + 1e-9, `${what}: duração ${s.duration}`);
  for (const [i, kfs] of s.tracks.entries()) {
    for (let k = 1; k < kfs.length; k++) assert.ok(kfs[k].t >= kfs[k - 1].t, `${what}: atleta ${i} volta no tempo`);
    for (const k of kfs) assert.ok(Number.isFinite(k.d) && Number.isFinite(k.w), `${what}: posição inválida`);
    const end = sampleTrack(kfs, s.duration);
    assert.ok(end.speed < 1e-6, `${what}: atleta ${i} ainda andando no fim`);
    assert.ok(!["run", "backpedal"].includes(end.act), `${what}: atleta ${i} correndo parado`);
    // ninguém sai do gramado (com folga para a beira)
    for (let t = 0; t <= s.duration; t += 0.25) {
      const p = sampleTrack(kfs, t);
      const abs = s.los + s.dir * p.d;
      assert.ok(abs >= -12 && abs <= 112 && Math.abs(p.w) <= 28.5, `${what}: atleta ${i} fora do campo em t=${t} (${abs}, ${p.w})`);
    }
  }
  for (let k = 1; k < s.ball.length; k++) assert.ok(Math.abs(s.ball[k].t0 - s.ball[k - 1].t1) < 1e-9, `${what}: bola com buraco no tempo`);
  const actorAt = (i: number, t: number) => sampleTrack(s.tracks[i], t);
  const b = sampleBall(s.ball, s.duration, actorAt);
  assert.ok(Math.abs(s.los + s.dir * b.d - s.endAbsYd) < 1e-6, `${what}: bola termina em ${s.los + s.dir * b.d}, ponto oficial ${s.endAbsYd}`);
  assert.ok(b.h < 0.2 && !b.flying, `${what}: bola parada no chão`);
  // sem teleporte: a bola anda no máximo ~40 m/s entre quadros
  let prev = sampleBall(s.ball, 0, actorAt);
  for (let t = 0.05; t <= s.duration; t += 0.05) {
    const cur = sampleBall(s.ball, t, actorAt);
    const jump = Math.hypot(cur.d - prev.d, cur.w - prev.w) * 0.9144;
    assert.ok(jump < 3.5, `${what}: bola pulou ${jump.toFixed(2)} m em t=${t.toFixed(2)}`);
    prev = cur;
  }
}

test("roteiro para cada chave do motor, com o ataque da casa e o do visitante (campo espelhado)", () => {
  for (const k of keys) {
    const e = eventForKey(k.textKey);
    if (kindOf(e) == null) {
      assert.equal(buildScript({ event: e, next: null, homeClubId: HOME, awayClubId: AWAY }), null, `${k.textKey}: administrativo sem roteiro`);
      continue;
    }
    const s = buildScript({ event: e, next: nextFor(e), homeClubId: HOME, awayClubId: AWAY })!;
    checkScript(s, k.textKey);
    // o mesmo lance com o visitante atacando: tudo espelhado
    const flip = (club: string | null) => (club === HOME ? AWAY : club === AWAY ? HOME : club);
    const n = nextFor(e);
    const em = { ...e, possessionClubId: flip(e.possessionClubId), scoringClubId: flip(e.scoringClubId), ballOnAbsYd: 100 - e.ballOnAbsYd, penalty: e.penalty ? { ...e.penalty, againstClubId: flip(e.penalty.againstClubId)! } : null };
    const sm = buildScript({ event: em, next: { possessionClubId: flip(n.possessionClubId)!, ballOnAbsYd: 100 - n.ballOnAbsYd }, homeClubId: HOME, awayClubId: AWAY })!;
    checkScript(sm, `${k.textKey} (visitante)`);
    assert.equal(sm.dir, -s.dir, `${k.textKey}: sentido espelhado`);
    assert.ok(Math.abs(sm.endAbsYd - (100 - s.endAbsYd)) < 1e-6, `${k.textKey}: ponto final espelhado`);
    assert.ok(Math.abs(sm.endD - s.endD) < 1e-6, `${k.textKey}: mesmas jardas no referencial do ataque`);
  }
});

test("bola no fim: jardas do evento a partir da linha (corrida, passe, sack, kneel), linha de gol no TD, a linha no incompleto", () => {
  const cases: [Partial<PlayEvent>, number][] = [
    [{ ballOnAbsYd: 30, yards: 7 }, 37],
    [{ ballOnAbsYd: 30, yards: -3, textKey: "match:event.sack", type: "PASS", result: "SACK" }, 27],
    [{ ballOnAbsYd: 92, yards: 8, result: "TOUCHDOWN", textKey: "match:event.run.touchdown", points: 6, scoringClubId: HOME }, 100],
    [{ ballOnAbsYd: 45, yards: 0, textKey: "match:event.pass.incomplete", type: "PASS", result: "INCOMPLETE" }, 45],
    [{ ballOnAbsYd: 40, yards: 15, textKey: "match:event.pass.complete", type: "PASS", result: "COMPLETE", concept: "MEDIUM_PASS" }, 55],
  ];
  for (const [over, abs] of cases) {
    for (const away of [false, true]) {
      const e = ev({ ...over, possessionClubId: away ? AWAY : HOME, scoringClubId: over.scoringClubId ? (away ? AWAY : HOME) : null, ballOnAbsYd: away ? 100 - over.ballOnAbsYd! : over.ballOnAbsYd! });
      const s = buildScript({ event: e, next: null, homeClubId: HOME, awayClubId: AWAY })!;
      assert.equal(s.endAbsYd, away ? 100 - abs : abs, `${e.textKey} ${away ? "visitante" : "casa"}`);
    }
  }
});

test("coerência com o golden: o fim de cada jogada de scrimmage é a linha do lance seguinte (match-events e match-state)", () => {
  for (const events of [page.events, state.live.lastEvents]) {
    const home = events === page.events ? events.find((e) => e.type === "RUN")!.possessionClubId! : state.live.home.clubId;
    let checked = 0;
    for (let i = 0; i + 1 < events.length; i++) {
      const e = events[i];
      if (e.type !== "RUN" && e.type !== "PASS") continue;
      if (e.points > 0) continue;
      const next = nextSituation(events, i, null);
      const s = buildScript({ event: e, next, homeClubId: home, awayClubId: "outro" })!;
      assert.equal(s.endAbsYd, events[i + 1].ballOnAbsYd, `seq ${e.seq}`);
      checked++;
    }
    assert.ok(checked >= 3);
  }
});

// partida inteira do mock (os dois times atacam, todos os tipos de lance que o mock gera)
let full: { state: MatchState; events: PlayEvent[] } | null = null;
async function fullMatch() {
  if (full) return full;
  const api = createMockApi();
  await api.auth.register({ email: `campo3d-${randomUUID().slice(0, 8)}@scores.test`, password: randomUUID(), displayName: "Coach 3D" });
  const world = (await api.worlds.list())[0];
  const [league] = await api.worlds.leagues(world.id);
  const lc = await api.worlds.leagueClubs(world.id, league.id);
  const club = lc.conferences.flatMap((c) => c.divisions.flatMap((d) => d.clubs))[3];
  const career = await api.careers.create({ name: "Campo 3D", clubId: club.id, coachStyle: "OFFENSIVE", seed: 140 });
  const board = await api.weeks.playCurrent(career.id);
  let st = await api.matches.get(board.matchId!);
  let events: PlayEvent[] = [];
  for (let i = 0; i < 200 && st.status !== "FINISHED"; i++) {
    if (st.status === "AWAITING_DECISION") st = await api.matches.playCall(st.matchId, st.live.decision!.id, { optionCode: st.live.decision!.autoOptionCode, version: st.live.version });
    else st = await api.matches.advance(st.matchId, { mode: "FULL", version: st.live.version });
    const pg = await api.matches.events(st.matchId, lastSeqOf(events), 1000);
    events = mergeEvents(events, pg.events);
  }
  full = { state: st, events };
  return full;
}

test("partida inteira do mock: todo lance encenável vira roteiro válido e cada jogada termina na linha seguinte, nos dois sentidos", async () => {
  const { state: st, events } = await fullMatch();
  const home = st.live.home.clubId;
  const away = st.live.away.clubId;
  let coherent = 0;
  const dirs = new Set<number>();
  for (let i = 0; i < events.length; i++) {
    const e = events[i];
    const next = nextSituation(events, i, { possessionClubId: st.live.possessionClubId, ballOnAbsYd: st.live.ballOnAbsYd });
    const s = buildScript({ event: e, next, homeClubId: home, awayClubId: away });
    if (kindOf(e) == null) {
      assert.equal(s, null);
      continue;
    }
    checkScript(s!, `seq ${e.seq} ${e.textKey}`);
    const nextEv = events[i + 1];
    if (nextEv && !e.points && nextEv.possessionClubId != null && e.type !== "EXTRA_POINT" && e.type !== "TWO_POINT" && !(e.type === "FIELD_GOAL" && e.result === "FIELD_GOAL_GOOD")) {
      assert.equal(s!.endAbsYd, nextEv.ballOnAbsYd, `seq ${e.seq} ${e.textKey}: a bola termina na próxima linha de scrimmage`);
      coherent++;
      dirs.add(s!.dir);
    }
  }
  assert.ok(coherent > 60, `lances conferidos: ${coherent}`);
  assert.deepEqual([...dirs].sort(), [-1, 1], "a casa e o visitante atacando");
});

test("endSpot: turnover e chutes usam a próxima linha; field goal bom atrás das traves; retorno para TD na linha de gol de quem chutou", () => {
  const base = { dir: 1 as const, losAbsYd: 40, offenseClubId: HOME };
  const int = ev({ textKey: "match:event.pass.interception", type: "PASS", result: "INTERCEPTION", turnover: true, ballOnAbsYd: 40, yards: 0 });
  assert.equal(endSpot({ ...base, event: int, kind: "INTERCEPTION", next: { possessionClubId: AWAY, ballOnAbsYd: 58 } }), 58);
  assert.equal(endSpot({ ...base, event: int, kind: "INTERCEPTION", next: null }), 40, "sem próxima linha: as jardas do evento");
  const fg = ev({ textKey: "match:event.fieldGoal.good", type: "FIELD_GOAL", result: "FIELD_GOAL_GOOD", points: 3, scoringClubId: HOME, ballOnAbsYd: 75 });
  assert.ok(endSpot({ ...base, losAbsYd: 75, event: fg, kind: "FIELD_GOAL", next: null }) > 110, "atrás da linha de fundo");
  const ptd = ev({ textKey: "match:event.punt.returnTouchdown", type: "PUNT", result: "TOUCHDOWN", points: 6, scoringClubId: AWAY });
  assert.equal(endSpot({ ...base, event: ptd, kind: "PUNT", next: { possessionClubId: HOME, ballOnAbsYd: 35 } }), 0);
  const safety = ev({ textKey: "match:event.run.safety", result: "SAFETY", ballOnAbsYd: 2, yards: -3, points: 2, scoringClubId: AWAY });
  assert.equal(endSpot({ ...base, losAbsYd: 2, event: safety, kind: "RUN", next: null }), 0);
});

test("kickoff: quem chuta sai da posse seguinte (o motor grava a de quem chuta, o mock a de quem recebe) e chuta da própria 35", () => {
  const engine = ev({ textKey: "match:event.kickoff.touchback", type: "KICKOFF", result: "TOUCHBACK", possessionClubId: AWAY, ballOnAbsYd: 65, down: 0, distance: 0 });
  const s1 = buildScript({ event: engine, next: { possessionClubId: HOME, ballOnAbsYd: 25 }, homeClubId: HOME, awayClubId: AWAY })!;
  assert.equal(s1.offenseClubId, AWAY);
  assert.equal(s1.los, 65);
  assert.equal(s1.dir, -1);
  assert.equal(s1.endAbsYd, 25);
  const mock = ev({ textKey: "match:event.kickoff.touchback", type: "KICKOFF", result: "TOUCHBACK", possessionClubId: HOME, ballOnAbsYd: 25 });
  const s2 = buildScript({ event: mock, next: { possessionClubId: HOME, ballOnAbsYd: 25 }, homeClubId: HOME, awayClubId: AWAY })!;
  assert.equal(s2.offenseClubId, AWAY, "a casa recebe: quem chuta é o visitante");
  assert.equal(s2.los, 65);
  const onside = ev({ textKey: "match:event.onside.recovered", type: "ONSIDE_KICK", result: "ONSIDE_RECOVERED", possessionClubId: HOME, ballOnAbsYd: 35 });
  assert.equal(buildScript({ event: onside, next: { possessionClubId: HOME, ballOnAbsYd: 46 }, homeClubId: HOME, awayClubId: AWAY })!.offenseClubId, HOME, "onside recuperado: quem chuta fica com a bola");
});

/* ───────────────────────── qual lance encenar ───────────────────────── */

test("vários lances de uma vez (DRIVE/QUARTER): encena só o último encenável; administrativos não contam", () => {
  const list = [ev({ seq: 1 }), ev({ seq: 2, textKey: "match:event.pass.complete", type: "PASS", result: "COMPLETE" }), ev({ seq: 3 }), ev({ seq: 4, textKey: "match:event.endOfQuarter", type: "END_OF_PERIOD", result: "END_OF_QUARTER" })];
  assert.deepEqual(pickStage(list, 0), { index: 2, skipped: 2 });
  assert.deepEqual(pickStage(list, 2), { index: 2, skipped: 0 });
  assert.equal(pickStage(list, 3), null, "só o fim do quarto: nada a encenar");
  assert.equal(pickStage([], 0), null);
  assert.equal(lastStageable(list), 2);
  // a situação seguinte do último encenado é a do fim do quarto (o motor grava a situação em todo lance) ou a de live
  assert.deepEqual(nextSituation(list, 2, null), { possessionClubId: HOME, ballOnAbsYd: 30 });
  assert.deepEqual(nextSituation(list, 3, { possessionClubId: AWAY, ballOnAbsYd: 70 }), { possessionClubId: AWAY, ballOnAbsYd: 70 });
});

test("pré-snap do próximo lance: formação parada na linha atual, bola no chão", () => {
  const s = presnapScript({ possessionClubId: AWAY, ballOnAbsYd: 60, homeClubId: HOME, awayClubId: AWAY });
  assert.equal(s.dir, -1);
  assert.equal(s.slots.length, 22);
  const b = sampleBall(s.ball, 1, (i, t) => sampleTrack(s.tracks[i], t));
  assert.ok(Math.abs(s.los + s.dir * b.d - 60) < 0.2);
});

/* ───────────────────────── elenco ───────────────────────── */

function player(id: string, position: string, jerseyNumber: number, snaps: number): PlayerBoxScore {
  return { playerId: id, clubId: HOME, name: id, position: position as PlayerBoxScore["position"], jerseyNumber, snaps, passing: null, rushing: null, receiving: null, defense: null, kicking: null, punting: null, returns: null };
}

test("quem veste cada vaga: o da posição com mais snaps, os nomeados no lance primeiro, sem inventar jogador", () => {
  const players = [player("qb1", "QB", 12, 40), player("qb2", "QB", 7, 2), player("rb1", "RB", 22, 30), player("rb2", "RB", 33, 5), player("wr1", "WR", 81, 30), player("wr2", "WR", 11, 20)];
  const slots = formationFor({ kind: "SCRIMMAGE", formation: "SINGLEBACK", toGoal: 70, toOwnGoal: 30 }).filter((s) => s.side === "O");
  const qb = slots.findIndex((s) => s.role === "QB");
  const rb = slots.findIndex((s) => s.role === "RB");
  const c = slots.findIndex((s) => s.role === "C");
  const wrs = slots.flatMap((s, i) => (s.role === "WR" ? [i] : []));
  const base = lineupFor(players, slots);
  assert.equal(base[qb].number, 12);
  assert.equal(base[rb].number, 22);
  assert.deepEqual(wrs.map((i) => base[i].number).sort(), [11, 81, null].sort(), "o terceiro WR fica sem número");
  assert.equal(base[c].number, null, "sem center no box score: camisa lisa");
  const named = lineupFor(players, slots, new Map([[rb, "rb2"]]));
  assert.equal(named[rb].number, 33, "o corredor nomeado no lance");
  // o golden de match-state: os titulares do box score
  const live = lineupFor(state.live.home.players, slots);
  assert.equal(live[qb].number, 12);
  assert.ok(live.filter((w) => w.number != null).length >= 9);
});

/* ───────────────────────── pose ───────────────────────── */

test("pose: em pé, correndo, na linha ou chutando o pé de apoio fica no chão; deitado ou ajoelhado o corpo encosta sem afundar", () => {
  const feetActs = ["three", "two", "up", "qbUnder", "run", "backpedal", "block", "throw", "catch", "handoff", "kick", "spike", "snap"] as const;
  for (const act of feetActs) {
    for (const p of [0, 0.3, 0.6, 1]) {
      for (const phase of [0, 1, 2.5, 4]) {
        const sk = skeleton(actionPose(act, p, phase, 7));
        const minFoot = Math.min(sk.toes[0].y, sk.toes[1].y) + sk.pelvisY;
        assert.ok(Math.abs(minFoot) < 1e-6 || minFoot > -1e-6, `${act}: pé ${minFoot}`);
        assert.ok(minFoot < 0.12, `${act}: flutuando (${minFoot.toFixed(3)} m)`);
        assert.ok(sk.head.y + sk.pelvisY > 0.6, `${act}: cabeça no chão`);
      }
    }
  }
  for (const act of ["down", "dive", "kneel"] as const) {
    const sk = skeleton(actionPose(act, 1, 0, 0));
    const ys = [...sk.toes, ...sk.knees, ...sk.hands].map((v) => v.y + sk.pelvisY);
    assert.ok(Math.min(...ys) > -1e-6, `${act}: afundou`);
    assert.ok(Math.min(...ys) < 0.15, `${act}: flutuando`);
  }
  // em pé: atleta de ~1,9 m com capacete
  const up = skeleton(actionPose("up", 0, 0, 0));
  const top = up.head.y + up.pelvisY + 0.165;
  assert.ok(top > 1.75 && top < 2.05, `altura ${top}`);
  // a troca de ação mistura as poses (sem tranco)
  const mid = poseOf({ d: 0, w: 0, act: "run", p: 0, face: 0, speed: 7, dist: 3, prevAct: "three", since: 0.05 });
  const three = actionPose("three", 1, 0, 0);
  assert.ok(Math.abs(mid.lean - three.lean) < 0.6, "começa perto da pose anterior");
});
