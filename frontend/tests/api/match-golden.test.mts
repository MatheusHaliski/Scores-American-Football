// Adaptadores do bloco 6b contra as respostas reais do backend (goldens do ApiGoldenTest, D-5): game-plan, playbook,
// problem-409-game-plan, preparation, week, match-state, match-events, match-report e live-match (o mesmo LiveMatchDto dentro de
// match-state.live). A API omite campos nulos (non_null): o golden pode não trazer um campo anulável, mas nunca traz campo que o
// front não conhece, e os obrigatórios estão sempre lá. Os `params` dos lances são um mapa livre do motor e ficam de fora.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  MATCH_SHAPES,
  gamePlanFromDto,
  liveMatchFromDto,
  matchEventsFromDto,
  matchReportFromDto,
  matchStateFromDto,
  planIssuesFromDetails,
  playbookFromDto,
  preparationFromDto,
  weekBoardFromDto,
} from "@/lib/api/live-match";
import { SHAPES, dashboardFromDto, type Raw } from "@/lib/api/live-career";
import { ApiError, problemFromBody } from "@/lib/api/problem";
import { describeError } from "@/lib/api/errors";
import { setRuntimeLocale } from "@/i18n/runtime";
import { MESSAGES } from "@/i18n/messages";
import { LOCALES } from "@/i18n/config";
import { rawMessage, splitRefKey, translateIn } from "@/i18n/runtime";
import { MATCH_GOLDENS, loadGolden } from "../golden.mts";

const plan = loadGolden("game-plan.json") as Raw;
const book = loadGolden("playbook.json") as Raw;
const conflict = loadGolden("problem-409-game-plan.json") as Raw;
const prep = loadGolden("preparation.json") as Raw;
const week = loadGolden("week.json") as Raw;
const state = loadGolden("match-state.json") as Raw;
const events = loadGolden("match-events.json") as Raw;
const report = loadGolden("match-report.json") as Raw;
const live = loadGolden("live-match.json") as Raw;
const dash = loadGolden("career-dashboard.json") as Raw;

type MatchShape = keyof typeof MATCH_SHAPES;
const asObj = (v: unknown) => v as Raw;
const each = (v: unknown) => (v as Raw[]) ?? [];

function check(o: unknown, s: { fields: readonly string[]; nullable: readonly string[] }, what: string) {
  assert.ok(o && typeof o === "object" && !Array.isArray(o), `${what}: não é objeto`);
  const present = Object.keys(o as object);
  assert.deepEqual(present.filter((k) => !s.fields.includes(k)), [], `${what}: campo que o front não conhece`);
  assert.deepEqual(s.fields.filter((k) => !s.nullable.includes(k) && !present.includes(k)), [], `${what}: campo obrigatório ausente`);
}
const assertShape = (o: unknown, name: MatchShape, what: string) => check(o, MATCH_SHAPES[name], what);
const assertCareerShape = (o: unknown, name: keyof typeof SHAPES, what: string) => check(o, SHAPES[name] as { fields: readonly string[]; nullable: readonly string[] }, what);

function assertEvent(e: unknown, what: string) {
  assertShape(e, "event", what);
  const ev = asObj(e);
  if (ev.penalty) assertShape(ev.penalty, "penalty", `${what}.penalty`);
  assert.equal(typeof ev.params, "object", `${what}.params`);
}

function assertPlayer(p: unknown, what: string) {
  assertShape(p, "playerBox", what);
  const r = asObj(p);
  for (const g of ["passing", "rushing", "receiving", "defense", "kicking", "punting", "returns"] as const) if (r[g]) assertShape(r[g], g, `${what}.${g}`);
}

function assertLive(l: unknown, what: string) {
  assertShape(l, "liveMatch", what);
  const r = asObj(l);
  for (const side of ["home", "away"] as const) {
    const t = asObj(r[side]);
    assertShape(t, "teamLine", `${what}.${side}`);
    assertShape(t.stats, "teamBox", `${what}.${side}.stats`);
    each(t.players).forEach((p, i) => assertPlayer(p, `${what}.${side}.players[${i}]`));
  }
  if (r.decision) {
    assertShape(r.decision, "decision", `${what}.decision`);
    for (const o of each(asObj(r.decision).options)) assertShape(o, "playOption", `${what}.decision.options[]`);
  }
  each(r.lastEvents).forEach((e, i) => assertEvent(e, `${what}.lastEvents[${i}]`));
  if (r.lastDrive) assertShape(r.lastDrive, "drive", `${what}.lastDrive`);
}

test("os goldens do 6b existem e são lidos pelo teste", () => {
  for (const f of MATCH_GOLDENS) assert.ok(loadGolden(f), f);
});

test("game-plan: plano e opções só têm campos conhecidos; vira o tipo do front com as faixas do servidor", () => {
  assertShape(plan, "gamePlan", "game-plan");
  assertShape(plan.options, "gamePlanOptions", "game-plan.options");
  const p = gamePlanFromDto(plan);
  assert.equal(p.version, plan.version);
  assert.equal(p.passRatio, plan.passRatio);
  assert.equal(p.source, "COACH_STYLE");
  assert.equal(p.options.minPassRatio, 20);
  assert.equal(p.options.maxPassRatio, 80);
  assert.deepEqual(p.options.baseDefense, ["BASE_4_3", "BASE_3_4"]);
  // sem head coach e sem data: os anuláveis somem da API e viram null
  const bare = gamePlanFromDto({ ...plan, coachStyle: undefined, updatedAt: undefined });
  assert.equal(bare.coachStyle, null);
  assert.equal(bare.updatedAt, null);
});

test("playbook: situações, pesos e coberturas só têm campos conhecidos; 5 situações × 8 conceitos, 6 coberturas, tudo em 100", () => {
  assertShape(book, "playbook", "playbook");
  for (const s of each(book.situations)) {
    assertShape(s, "playbookSituation", "playbook.situations[]");
    for (const c of each(s.concepts)) assertShape(c, "weight", "playbook.situations[].concepts[]");
  }
  for (const c of each(book.coverages)) assertShape(c, "weight", "playbook.coverages[]");
  const pb = playbookFromDto(book);
  assert.equal(pb.situations.length, 5);
  assert.ok(pb.situations.every((s) => s.concepts.length === pb.runConcepts.length + pb.passConcepts.length));
  assert.equal(pb.coverages.length, 6);
  assert.ok([...pb.situations.flatMap((s) => s.concepts), ...pb.coverages].every((w) => w.weight === pb.neutralWeight));
  assert.equal(pb.customWeights, 0);
});

test("problem-409-game-plan: conflito com as duas versões e mensagem do servidor; o código tem texto nos 3 idiomas", () => {
  const err = new ApiError(problemFromBody(409, conflict));
  assert.equal(err.code, "GAME_PLAN_VERSION_CONFLICT");
  assert.equal(typeof err.problem.details.currentVersion, "number");
  assert.equal(typeof err.problem.details.expectedVersion, "number");
  for (const loc of ["pt-BR", "en-US", "es-ES"] as const) assert.ok(err.code in MESSAGES[loc].errors, `${loc} sem errors:${err.code}`);
  setRuntimeLocale("pt-BR");
  assert.equal(describeError(err), conflict.message, "o catálogo usa {0}/{1}: sem argumentos, vale a mensagem que o servidor traduziu");
});

test("422 do plano/playbook: details.errors vira a lista de problemas por campo", () => {
  const body = { code: "PLAYBOOK_INCOMPLETE", message: "x", details: { errors: [{ code: "NO_RUN_CONCEPT", field: "EARLY_DOWN" }, { code: "WEIGHT_OUT_OF_RANGE", field: "RED_ZONE.DEEP_PASS", value: "250" }] } };
  const issues = planIssuesFromDetails(new ApiError(problemFromBody(422, body)).problem.details);
  for (const i of body.details.errors) assertShape(i, "planIssue", "details.errors[]");
  assert.deepEqual(issues, [
    { code: "NO_RUN_CONCEPT", field: "EARLY_DOWN", value: null },
    { code: "WEIGHT_OUT_OF_RANGE", field: "RED_ZONE.DEEP_PASS", value: "250" },
  ]);
});

test("preparation: adversário, clima, unidades, confrontos, jogadores-chave, prontidão e plano só têm campos conhecidos", () => {
  assertShape(prep, "preparation", "preparation");
  assertCareerShape(prep.fixture, "fixture", "preparation.fixture");
  assertCareerShape(prep.club, "clubRef", "preparation.club");
  assertCareerShape(prep.opponent, "clubRef", "preparation.opponent");
  assertCareerShape(prep.record, "record", "preparation.record");
  assertCareerShape(prep.opponentRecord, "record", "preparation.opponentRecord");
  assertShape(prep.weather, "weather", "preparation.weather");
  for (const u of each(prep.units)) assertShape(u, "unitComparison", "preparation.units[]");
  for (const m of each(prep.keyMatchups)) {
    assertShape(m, "matchup", "preparation.keyMatchups[]");
    for (const p of [...each(m.ourPlayers), ...each(m.theirPlayers)]) assertShape(p, "playerRef", "preparation.keyMatchups[].players[]");
  }
  for (const p of [...each(prep.keyPlayers), ...each(prep.opponentKeyPlayers)]) assertShape(p, "playerRef", "preparation.keyPlayers[]");
  assertShape(prep.readiness, "readiness", "preparation.readiness");
  for (const i of [...each(asObj(prep.readiness).errors), ...each(asObj(prep.readiness).warnings)]) assertCareerShape(i, "depthIssue", "preparation.readiness.issues[]");
  assertShape(prep.gamePlan, "gamePlan", "preparation.gamePlan");
  const p = preparationFromDto(prep);
  assert.equal(p.matchId, null, "partida ainda não iniciada: matchId ausente vira null");
  assert.deepEqual([...new Set(p.keyMatchups.map((m) => m.key))].sort(), ["OL_VS_DL", "QB_VS_PASS_RUSH", "WR_VS_CB"]);
  assert.deepEqual([...new Set(p.keyMatchups.map((m) => m.side))].sort(), ["OUR_OFFENSE", "THEIR_OFFENSE"]);
  for (const m of p.keyMatchups) assert.equal(m.edge, m.ours - m.theirs, `${m.key}/${m.side}: edge = nosso − deles`);
  assert.equal(p.readiness.ready, true);
  assert.equal(p.gamePlan.passRatio, asObj(prep.gamePlan).passRatio);
  assert.equal(p.club.shortName, "PAV");
  assert.equal(p.home, false);
});

test("week: painel, jogos e situação ao vivo só têm campos conhecidos; a partida do usuário tem matchId", () => {
  assertShape(week, "weekBoard", "week");
  for (const g of each(week.games)) {
    assertShape(g, "weekGame", "week.games[]");
    assertCareerShape(g.fixture, "fixture", "week.games[].fixture");
    assertCareerShape(g.home, "clubRef", "week.games[].home");
    assertCareerShape(g.away, "clubRef", "week.games[].away");
    if (g.live) assertShape(g.live, "liveLine", "week.games[].live");
  }
  const w = weekBoardFromDto(week);
  assert.equal(w.matchId, week.matchId);
  const mine = w.games.find((g) => g.fixture.id === w.userFixtureId)!;
  assert.ok(mine.fixture.userGame);
  assert.equal(mine.live?.matchId, w.matchId);
  assert.equal(mine.live?.status, "NOT_STARTED");
  assert.equal(mine.live?.possessionClubId, null, "sem posse antes do kickoff");
  assert.ok(w.games.filter((g) => g !== mine).every((g) => g.live === null), "só a partida do usuário tem sessão ao vivo (D-63)");
});

test("match-state: sessão e estado do motor só têm campos conhecidos; decisão aberta com prazo e sem modos de avanço", () => {
  assertShape(state, "matchState", "match-state");
  assertLive(state.live, "match-state.live");
  const s = matchStateFromDto(state);
  assert.equal(s.status, "AWAITING_DECISION");
  assert.deepEqual(s.advanceModes, [], "decisão aberta no prazo: nenhum modo de avanço");
  assert.equal(s.decisionTimeoutSeconds, 30);
  assert.ok(s.decisionDeadlineAt);
  assert.equal(s.adjustable, false);
  assert.ok(s.live.decision);
  assert.ok(s.live.decision!.options.some((o) => o.code === s.live.decision!.autoOptionCode));
  assert.equal(s.live.decision!.side, "DEFENSE");
  assert.equal(s.live.decision!.options[0].concept, null, "opção de defesa sem conceito: ausente vira null");
  assert.equal(s.live.version, asObj(state.live).version);
  assert.equal(s.userHome, false);
  assert.equal(s.live.away.clubId, s.userClubId);
  // o motor não gera tackles assistidos nem QB hits (D-35): ausentes viram null, nunca zero
  const tackler = s.live.home.players.find((p) => p.defense)!;
  assert.equal(tackler.defense!.assistedTackles, null);
  assert.equal(tackler.defense!.qbHits, null);
});

test("live-match (bloco 5): o mesmo LiveMatchDto que vem em match-state.live passa pelo mesmo adaptador", () => {
  assertLive(live, "live-match");
  const l = liveMatchFromDto(live);
  assert.equal(l.status, "AWAITING_DECISION");
  assert.equal(l.lastEvents.length, each(live.lastEvents).length);
  const pen = l.lastEvents.find((e) => e.penalty);
  if (pen) assert.equal(typeof pen.penalty!.accepted, "boolean");
});

test("match-events: cursor e lances só têm campos conhecidos; seq crescente e depois de `after`", () => {
  assertShape(events, "matchEvents", "match-events");
  each(events.events).forEach((e, i) => assertEvent(e, `match-events.events[${i}]`));
  const ev = matchEventsFromDto(events);
  assert.ok(ev.events.length > 0);
  assert.ok(ev.events.every((e, i, a) => e.seq > ev.after && (i === 0 || e.seq > a[i - 1].seq)));
  assert.ok(ev.lastSeq >= ev.events[ev.events.length - 1].seq);
  // todo lance tem texto nos três idiomas, sem sobrar {…}
  for (const loc of LOCALES) {
    for (const e of ev.events) {
      const { ns, key } = splitRefKey(e.textKey);
      assert.equal(ns, "match");
      assert.ok(rawMessage(loc, "match", key), `${loc}: ${e.textKey}`);
      const out = translateIn(loc, "match", key, e.params);
      assert.ok(!out.includes("{"), `${loc} ${e.textKey}: ${out}`);
    }
  }
});

test("match-report: lados, box score, líderes, drives, jogadas de pontuação e decisões só têm campos conhecidos", () => {
  assertShape(report, "report", "match-report");
  for (const side of ["home", "away"] as const) {
    const r = asObj(report[side]);
    assertShape(r, "reportSide", `match-report.${side}`);
    assertCareerShape(r.club, "clubRef", `match-report.${side}.club`);
    assertShape(r.stats, "teamBox", `match-report.${side}.stats`);
    each(r.players).forEach((p, i) => assertPlayer(p, `match-report.${side}.players[${i}]`));
  }
  for (const l of each(report.leaders)) assertShape(l, "leader", "match-report.leaders[]");
  for (const d of each(report.drives)) assertShape(d, "drive", "match-report.drives[]");
  each(report.scoringPlays).forEach((e, i) => assertEvent(e, `match-report.scoringPlays[${i}]`));
  for (const d of each(report.decisions)) assertShape(d, "decisionRecord", "match-report.decisions[]");
  const r = matchReportFromDto(report);
  assert.equal(r.home.score, r.home.quarters.reduce((a, q) => a + q, 0), "placar = soma dos quartos (casa)");
  assert.equal(r.away.score, r.away.quarters.reduce((a, q) => a + q, 0), "placar = soma dos quartos (visitante)");
  assert.equal(r.home.stats.points, r.home.score);
  const winner = r.home.score > r.away.score ? r.home.club.id : r.away.score > r.home.score ? r.away.club.id : null;
  assert.equal(r.winnerClubId, winner);
  assert.ok(r.scoringPlays.every((e) => e.points > 0));
  assert.ok(r.leaders.length > 0);
  // sem partida ao vivo (jogo da IA) e com empate: matchId e vencedor somem e viram null
  const ai = matchReportFromDto({ ...report, matchId: undefined, winnerClubId: undefined });
  assert.equal(ai.matchId, null);
  assert.equal(ai.winnerClubId, null);
});

test("career-dashboard: `live` nulo continua nulo; preenchido segue o LiveMatchRefDto do 6b", () => {
  assert.equal(dashboardFromDto(dash).live, null);
  const ref = { matchId: "m-1", fixtureId: "f-1", week: 3, status: "HALFTIME", homeScore: 10, awayScore: 7 };
  assertCareerShape(ref, "liveRef", "dashboard.live");
  const d = dashboardFromDto({ ...dash, live: ref });
  assert.deepEqual(d.live, ref);
});

test("códigos de erro das rotas do 6b têm texto nos três idiomas", () => {
  const codes = ["GAME_PLAN_VERSION_CONFLICT", "PLAYBOOK_VERSION_CONFLICT", "GAME_PLAN_INVALID", "PLAYBOOK_INVALID", "PLAYBOOK_INCOMPLETE", "DEPTH_CHART_INVALID", "NO_NEXT_GAME", "DECISION_PENDING", "MATCH_VERSION_CONFLICT", "INVALID_ADVANCE_MODE", "MATCH_FINISHED", "ADJUSTMENT_NOT_ALLOWED", "TIMEOUT_NOT_ALLOWED", "DECISION_NOT_FOUND", "DECISION_CLOSED", "PLAY_NOT_OFFERED", "MATCH_NOT_FINISHED", "INVALID_ADJUSTMENT"];
  for (const loc of ["pt-BR", "en-US", "es-ES"] as const) for (const c of codes) assert.ok(c in MESSAGES[loc].errors, `${loc}: errors:${c}`);
});
