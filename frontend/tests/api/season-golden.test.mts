// Adaptadores da temporada (bloco 6c) contra as respostas reais do backend (goldens do ApiGoldenTest, D-5): standings, week-news,
// playoffs, playoffs-bracket, season-summary, season-advance, trophies, morale, analysis e os problemas 422/409 do avanço. A API omite
// campos nulos (non_null): o golden pode não trazer um campo anulável, mas nunca traz campo que o front não conhece, e os obrigatórios
// estão sempre lá. Mapas livres (params, statLine, factors, leaders, distribution, summaryParams) ficam fora da conferência de campos.
import { test } from "node:test";
import assert from "node:assert/strict";
import { SEASON_SHAPES, analysisFromDto, bracketFromDto, playoffsFromDto, seasonAdvanceFromDto, seasonSummaryFromDto, squadMoraleFromDto, standingsFromDto, trophiesFromDto, weekNewsFromDto } from "@/lib/api/live-season";
import { SHAPES, type Raw } from "@/lib/api/live-career";
import { ApiError, problemFromBody } from "@/lib/api/problem";
import { describeError } from "@/lib/api/errors";
import { setRuntimeLocale } from "@/i18n/runtime";
import { MESSAGES } from "@/i18n/messages";
import { SEASON_GOLDENS, loadGolden } from "../golden.mts";

const g = (f: (typeof SEASON_GOLDENS)[number]) => loadGolden(f) as Raw;
const each = (v: unknown) => (v as Raw[]) ?? [];
const asObj = (v: unknown) => v as Raw;
type Name = keyof typeof SEASON_SHAPES | "clubRef" | "record" | "fixture" | "career";

function assertShape(o: unknown, name: Name, what: string) {
  assert.ok(o && typeof o === "object" && !Array.isArray(o), `${what}: não é objeto`);
  const s = ((SEASON_SHAPES as Record<string, unknown>)[name] ?? (SHAPES as Record<string, unknown>)[name]) as { fields: readonly string[]; nullable: readonly string[] };
  const present = Object.keys(o as object);
  assert.deepEqual(present.filter((k) => !s.fields.includes(k)), [], `${what}: campo que o front não conhece`);
  assert.deepEqual(s.fields.filter((k) => !s.nullable.includes(k) && !present.includes(k)), [], `${what}: campo obrigatório ausente`);
}

function assertRow(r: Raw, what: string) {
  assertShape(r, "standingsRow", what);
  assertShape(r.club, "clubRef", `${what}.club`);
  for (const k of ["record", "home", "away", "division", "conference"]) assertShape(r[k], "record", `${what}.${k}`);
}

test("standings: tabela, conferências, divisões e linhas só têm campos conhecidos", () => {
  const s = g("standings.json");
  assertShape(s, "standings", "standings");
  for (const c of each(s.conferences)) {
    assertShape(c, "conferenceTable", "conferences[]");
    for (const d of each(c.divisions)) {
      assertShape(d, "divisionTable", "divisions[]");
      for (const r of each(d.rows)) assertRow(r, "divisions[].rows[]");
    }
    for (const r of each(c.rows)) assertRow(r, "conferences[].rows[]");
  }
});

test("standings: vira a classificação com semente, desempate e franquia do usuário; anuláveis ausentes viram null", () => {
  const raw = g("standings.json");
  const s = standingsFromDto(raw);
  assert.equal(s.official, false, "o golden é projeção (semana 1)");
  assert.deepEqual(s.divisionTiebreakers, raw.divisionTiebreakers);
  assert.equal(s.conferences.length, 2);
  const rows = s.conferences.flatMap((c) => c.rows);
  assert.equal(rows.length, 32);
  assert.equal(rows.filter((r) => r.userClub).length, 1);
  assert.equal(rows.find((r) => r.userClub)?.club.id, s.userClubId);
  for (const c of s.conferences) {
    const seeds = c.rows.filter((r) => r.seed != null).map((r) => r.seed);
    assert.deepEqual(seeds, Array.from({ length: s.seedsPerConference }, (_, i) => i + 1), `${c.shortName}: sementes 1..7 na ordem`);
    for (const d of c.divisions) d.rows.forEach((r, i) => assert.equal(r.rank, i + 1));
  }
  const noSeed = rows.find((r) => r.seed == null)!;
  assert.equal(noSeed.clinch, null);
  assert.ok(rows.every((r) => r.pointDiff === r.pointsFor - r.pointsAgainst));
  assert.ok(rows.some((r) => r.tiebreaker === "POINT_DIFFERENTIAL") && rows.some((r) => r.tiebreaker === null));
});

test("week-news: semana e notícias só têm campos conhecidos; params ficam como vieram (texto e número)", () => {
  const n = g("week-news.json");
  assertShape(n, "weekNews", "week-news");
  for (const i of each(n.items)) assertShape(i, "news", "items[]");
  const w = weekNewsFromDto(n);
  assert.equal(w.items.length, each(n.items).length);
  const first = w.items[0];
  assert.equal(first.textKey, "news.USER_WIN");
  assert.deepEqual(first.params, asObj(each(n.items)[0].params));
  assert.equal(first.playerId, null, "playerId ausente vira null");
  assert.ok(w.items.some((i) => i.playerId), "destaque individual traz o jogador");
});

test("playoffs: situação, sementes e o usuário só têm campos conhecidos", () => {
  const p = g("playoffs.json");
  assertShape(p, "playoffs", "playoffs");
  for (const c of each(p.conferences)) {
    assertShape(c, "conferenceSeeds", "conferences[]");
    for (const s of each(c.seeds)) {
      assertShape(s, "seed", "seeds[]");
      assertShape(s.club, "clubRef", "seeds[].club");
    }
  }
  assertShape(p.user, "userPlayoff", "user");
  assertShape(asObj(p.user).nextGame, "fixture", "user.nextGame");
  const s = playoffsFromDto(p);
  assert.equal(s.status, "IN_PROGRESS");
  assert.equal(s.champion, null, "campeã ausente vira null");
  assert.equal(s.user?.status, "ALIVE");
  assert.equal(s.user?.nextGame?.week, asObj(asObj(p.user).nextGame).week);
  for (const c of s.conferences) assert.deepEqual(c.seeds.filter((x) => x.bye).map((x) => x.seed), [1], "a semente 1 folga");
});

test("playoffs-bracket: rodadas, jogos e folgas só têm campos conhecidos; a final não tem conferência", () => {
  const b = g("playoffs-bracket.json");
  assertShape(b, "bracket", "bracket");
  assertShape(b.champion, "clubRef", "champion");
  for (const r of each(b.rounds)) {
    assertShape(r, "bracketRound", "rounds[]");
    for (const t of each(r.byes)) assertShape(t, "bracketTeam", "rounds[].byes[]");
    for (const x of each(r.games)) {
      assertShape(x, "bracketGame", "rounds[].games[]");
      assertShape(x.home, "bracketTeam", "games[].home");
      assertShape(x.away, "bracketTeam", "games[].away");
    }
  }
  const br = bracketFromDto(b);
  assert.deepEqual(br.rounds.map((r) => r.kind), ["WILD_CARD", "DIVISIONAL", "CONFERENCE", "FINAL"]);
  const final = br.rounds[3].games[0];
  assert.equal(final.conference, null);
  assert.equal(final.neutralSite, true);
  assert.equal(final.winnerClubId, br.champion?.id);
  assert.ok(br.rounds[0].games.some((x) => x.overtime));
});

test("season-summary: resumo, campanha, prêmios, líderes e entressafra só têm campos conhecidos", () => {
  const s = g("season-summary.json");
  assertShape(s, "summary", "summary");
  assertShape(s.campaign, "campaign", "campaign");
  for (const a of each(s.awards)) assertShape(a, "award", "awards[]");
  for (const list of Object.values(asObj(s.leaders))) for (const l of each(list)) assertShape(l, "leader", "leaders[][]");
  assertShape(s.offseason, "offseason", "offseason");
  for (const st of each(asObj(s.offseason).steps)) assertShape(st, "step", "offseason.steps[]");
  for (const x of each(asObj(s.offseason).expiringContracts)) assertShape(x, "expiring", "expiringContracts[]");
  const r = seasonSummaryFromDto(s);
  assert.equal(r.completed, true);
  assert.equal(r.provisionalAwards, false);
  const coach = r.awards.find((a) => a.type === "COACH")!;
  assert.equal(coach.playerId, null, "head coach sem playerId");
  assert.equal(coach.position, null);
  assert.equal(r.campaign?.playoffResult, "CONFERENCE");
  assert.equal(Object.keys(r.leaders).length, 9);
});

test("season-advance: resultado do avanço com a renovação da IA (D-120) só tem campos conhecidos", () => {
  const a = g("season-advance.json");
  assertShape(a, "advance", "advance");
  assertShape(a.career, "career", "advance.career");
  for (const st of each(a.steps)) assertShape(st, "step", "steps[]");
  for (const x of each(a.userExpiredContracts)) assertShape(x, "expiring", "userExpiredContracts[]");
  const r = seasonAdvanceFromDto(a);
  assert.equal(r.seasonNumber, 2);
  assert.equal(r.aiRenewals, a.aiRenewals);
  assert.equal(r.expiredContracts, a.expiredContracts);
  assert.ok(r.expiredContracts >= r.userExpiredContracts.length, "agentes livres incluem os do usuário");
  assert.equal(r.career.seasonId, r.seasonId);
  assert.deepEqual(r.steps.map((s) => s.status), ["DONE", "SKIPPED", "SKIPPED", "DONE"]);
});

test("trophies: contagem, títulos e temporadas só têm campos conhecidos", () => {
  const t = g("trophies.json");
  assertShape(t, "trophies", "trophies");
  for (const x of each(t.trophies)) assertShape(x, "trophy", "trophies[]");
  for (const s of each(t.seasons)) {
    assertShape(s, "seasonHistory", "seasons[]");
    if (s.mvp != null) assertShape(s.mvp, "award", "seasons[].mvp");
  }
  const r = trophiesFromDto(t);
  assert.equal(r.divisionTitles, r.trophies.filter((x) => x.userClub && x.kind === "DIVISION").length);
  assert.equal(r.seasons[0].userResult, "CONFERENCE");
});

test("morale: elenco e jogadores só têm campos conhecidos; snaps ausentes viram null (reserva e especialista)", () => {
  const m = g("morale.json");
  assertShape(m, "squadMorale", "morale");
  assertShape(m.record, "record", "morale.record");
  for (const p of each(m.players)) assertShape(p, "playerMorale", "players[]");
  const r = squadMoraleFromDto(m);
  assert.equal(Object.values(r.distribution).reduce((a, n) => a + n, 0), 53, "distribuição dos 53");
  const reserve = r.players.find((p) => p.role === "RESERVE")!;
  assert.equal(reserve.expectedSnapShare, null);
  assert.ok(r.players.some((p) => p.role === "STARTER" && p.expectedSnapShare != null));
  assert.ok(r.players.every((p) => p.morale >= 0 && p.morale <= 100));
});

test("analysis: relatório, unidades, próximos jogos e ajustes só têm campos conhecidos", () => {
  const a = g("analysis.json");
  assertShape(a, "analysis", "analysis");
  for (const k of ["units", "strengths", "weaknesses"]) for (const u of each(a[k])) assertShape(u, "unit", `${k}[]`);
  for (const o of each(a.upcoming)) {
    assertShape(o, "outlook", "upcoming[]");
    assertShape(o.opponent, "clubRef", "upcoming[].opponent");
    for (const m of each(o.matchups)) assertShape(m, "analystMatchup", "upcoming[].matchups[]");
  }
  for (const d of each(a.depthChart)) assertShape(d, "depthChange", "depthChart[]");
  for (const p of each(a.gamePlan)) assertShape(p, "planAdvice", "gamePlan[]");
  const r = analysisFromDto(a);
  assert.equal(r.units.length, 8);
  assert.equal(r.summaryParams.bestUnit, "OFFENSIVE_LINE");
  assert.equal(r.upcoming[0].opponent.id, r.summaryParams.opponentId);
});

test("problem-422-season-not-finished e problem-409-season-advance: códigos, detalhes e mensagem traduzida nos 3 idiomas", () => {
  const e422 = new ApiError(problemFromBody(422, g("problem-422-season-not-finished.json")));
  assert.equal(e422.code, "SEASON_NOT_FINISHED");
  assert.equal(e422.problem.details.phase, "PLAYOFFS");
  assert.equal(typeof e422.problem.details.currentWeek, "number");
  const e409 = new ApiError(problemFromBody(409, g("problem-409-season-advance.json")));
  assert.equal(e409.code, "SEASON_ALREADY_ADVANCED");
  assert.equal(e409.problem.details.currentSeasonNumber, 2);
  for (const loc of ["pt-BR", "en-US", "es-ES"] as const) for (const code of ["SEASON_NOT_FINISHED", "SEASON_ALREADY_ADVANCED", "ANALYSIS_NOT_FOUND"]) assert.ok(code in MESSAGES[loc].errors, `${loc} sem errors:${code}`);
  setRuntimeLocale("pt-BR");
  // o catálogo usa {0}; sem argumentos posicionais no corpo, vale a mensagem que o servidor já traduziu
  assert.equal(describeError(e422), g("problem-422-season-not-finished.json").message);
  assert.equal(describeError(e409), g("problem-409-season-advance.json").message);
});
