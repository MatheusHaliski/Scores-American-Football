// Adaptadores da carreira (bloco 6a) contra as respostas reais do backend (goldens do ApiGoldenTest, D-5): careers-list,
// career-dashboard, career-calendar, roster, roster-player, depth-chart, problem-409-depth-chart e world-editor-club. A API omite
// campos nulos (non_null), menos no DashboardDto, que manda os nulos explícitos: o golden pode não trazer um campo anulável, mas
// nunca traz campo que o front não conhece, e os obrigatórios estão sempre lá.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  SHAPES,
  calendarFromDto,
  careerListFromDto,
  clubOptionFromDto,
  dashboardFromDto,
  depthChartFromDto,
  leagueClubsFrom,
  playerDetailFromDto,
  rosterFromDto,
  type Raw,
} from "@/lib/api/live-career";
import { SEASON_SHAPES } from "@/lib/api/live-season";
import { ApiError, problemFromBody } from "@/lib/api/problem";
import { capExceededExcess, describeError } from "@/lib/api/errors";
import { setRuntimeLocale } from "@/i18n/runtime";
import { MESSAGES } from "@/i18n/messages";
import { FAMILY_OF, macros } from "@/lib/ratings/macros";
import { overallByPosition, type GroupedAttributes, type Position } from "@/lib/ratings/overall";
import { cardTier } from "@/lib/ratings/tier";
import { loadGolden } from "../golden.mts";

const list = loadGolden("careers-list.json") as Raw;
const dash = loadGolden("career-dashboard.json") as Raw;
const cal = loadGolden("career-calendar.json") as Raw;
const roster = loadGolden("roster.json") as Raw;
const player = loadGolden("roster-player.json") as Raw;
const depth = loadGolden("depth-chart.json") as Raw;
const conflict = loadGolden("problem-409-depth-chart.json") as Raw;
const capExceeded = loadGolden("problem-422-cap-exceeded.json") as Raw;
const editorClub = loadGolden("world-editor-club.json") as Raw;

type ShapeName = keyof typeof SHAPES | keyof typeof SEASON_SHAPES;
const asObj = (v: unknown) => v as Raw;
const each = (v: unknown) => (v as Raw[]) ?? [];

/** Todo campo do golden é conhecido do front e todo campo obrigatório (nunca nulo) está presente. */
function assertShape(o: unknown, name: ShapeName, what: string) {
  assert.ok(o && typeof o === "object" && !Array.isArray(o), `${what}: não é objeto`);
  const { fields, nullable } = ((SHAPES as Record<string, unknown>)[name] ?? (SEASON_SHAPES as Record<string, unknown>)[name]) as { fields: readonly string[]; nullable: readonly string[] };
  const present = Object.keys(o as object);
  assert.deepEqual(present.filter((k) => !fields.includes(k)), [], `${what}: campo que o front não conhece`);
  assert.deepEqual(fields.filter((k) => !nullable.includes(k) && !present.includes(k)), [], `${what}: campo obrigatório ausente`);
}

function assertCard(c: unknown, what: string) {
  assertShape(c, "card", what);
  const card = asObj(c);
  assert.equal(typeof card.macros, "object");
  assert.ok(Array.isArray(card.depthChart));
}

test("careers-list: lista, carreira e campanha só têm campos conhecidos", () => {
  assertShape(list, "careerList", "careers-list");
  assert.ok(each(list.careers).length > 0, "o golden traz ao menos uma carreira");
  for (const c of each(list.careers)) {
    assertShape(c, "career", "careers-list.careers[]");
    assertShape(c.record, "record", "careers-list.careers[].record");
  }
});

test("careers-list: vira a central de carreiras com slots livres e campos anuláveis como null", () => {
  const l = careerListFromDto(list);
  assert.equal(l.maxActiveSlots, list.maxActiveSlots);
  assert.equal(l.activeCount, list.activeCount);
  assert.deepEqual(l.freeSlots, list.freeSlots);
  const raw = each(list.careers)[0];
  const c = l.careers[0];
  assert.equal(c.id, raw.id);
  assert.equal(c.slot, raw.slot);
  assert.equal(c.clubName, raw.clubName);
  assert.equal(c.coachStyle, raw.coachStyle);
  assert.equal(c.record.summary, asObj(raw.record).summary);
  // carreira arquivada sem slot e desempregada sem franquia: os campos somem da API e viram null aqui
  const archived = careerListFromDto({ ...list, careers: [{ ...raw, status: "ARCHIVED", slot: undefined, clubId: undefined, clubName: undefined, seasonYear: undefined }] }).careers[0];
  assert.equal(archived.status, "ARCHIVED");
  assert.equal(archived.slot, null);
  assert.equal(archived.clubName, null);
  assert.equal(archived.seasonYear, null);
});

test("career-dashboard: todos os objetos aninhados só têm campos conhecidos", () => {
  assertShape(dash, "dashboard", "dashboard");
  assertShape(dash.career, "career", "dashboard.career");
  assertShape(dash.club, "clubRef", "dashboard.club");
  assertShape(dash.coach, "coach", "dashboard.coach");
  assertShape(dash.record, "record", "dashboard.record");
  assertShape(dash.cap, "cap", "dashboard.cap");
  assertShape(dash.depthChart, "depthChartAlert", "dashboard.depthChart");
  const next = asObj(dash.nextGame);
  assertShape(next, "nextGame", "dashboard.nextGame");
  assertShape(next.fixture, "fixture", "dashboard.nextGame.fixture");
  assertShape(next.opponent, "clubRef", "dashboard.nextGame.opponent");
  assertShape(next.opponentRecord, "record", "dashboard.nextGame.opponentRecord");
  for (const r of each(dash.division)) {
    assertShape(r, "standingRow", "dashboard.division[]");
    assertShape(r.club, "clubRef", "dashboard.division[].club");
  }
  for (const f of each(dash.upcomingDivisionGames)) assertShape(f, "fixture", "dashboard.upcomingDivisionGames[]");
  for (const h of each(dash.rosterHighlights)) assertShape(h, "highlight", "dashboard.rosterHighlights[]");
  for (const i of each(dash.injuries)) assertShape(i, "injury", "dashboard.injuries[]");
  // bloco 6c (D-106): moral, notícias e playoffs preenchidos
  assertShape(dash.morale, "moraleSummary", "dashboard.morale");
  assert.ok(Array.isArray(dash.news), "dashboard.news é lista");
  for (const n of each(dash.news)) assertShape(n, "news", "dashboard.news[]");
  assertShape(dash.playoffs, "userPlayoff", "dashboard.playoffs");
  if (dash.analysis !== null) assertShape(dash.analysis, "analysisSummary", "dashboard.analysis");
});

test("career-dashboard: o que ainda não existe chega nulo e continua nulo (a tela mostra —, nunca zero); moral e playoffs do 6c viram objetos", () => {
  for (const k of ["live", "analysis", "inbox", "unreadInbox", "scoresPoints"]) assert.ok(k in dash && dash[k] === null, `${k} nulo explícito`);
  const d = dashboardFromDto(dash);
  assert.equal(d.live, null);
  assert.equal(d.analysis, null, "sem relatório do analista ainda");
  const m = asObj(dash.morale);
  assert.deepEqual(d.morale, { average: m.average, label: m.label, trend: m.trend, low: m.low, tradeRequests: m.tradeRequests });
  assert.deepEqual(d.news, []);
  const p = asObj(dash.playoffs);
  assert.equal(d.playoffs?.clubId, p.clubId);
  assert.equal(d.playoffs?.seed, p.seed);
  assert.equal(d.playoffs?.status, p.status);
  assert.equal(d.playoffs?.round, null, "round ausente vira null");
  assert.equal(d.playoffs?.nextGame, null, "nextGame ausente vira null");
  // carreira sem temporada: moral e playoffs nulos, notícias lista vazia
  const bare = dashboardFromDto({ ...dash, morale: null, playoffs: null, news: undefined });
  assert.equal(bare.morale, null);
  assert.equal(bare.playoffs, null);
  assert.deepEqual(bare.news, []);
  assert.equal(d.inbox, null);
  assert.equal(d.unreadInbox, null);
  assert.equal(d.scoresPoints, null);
  assert.equal(d.nextGame?.opponent.shortName, asObj(asObj(dash.nextGame).opponent).shortName);
  assert.equal(d.nextGame?.fixture.homeScore, null, "placar ausente vira null");
  assert.equal(d.divisionRank, dash.divisionRank);
  assert.equal(d.cap?.capSpace, asObj(dash.cap).capSpace);
  assert.equal(d.cap!.salaryCap - d.cap!.capUsed, d.cap!.capSpace);
  assert.equal(d.division.filter((r) => r.userClub).length, 1);
  // sem próximo jogo (temporada encerrada) e sem divisão: nulos, não zero
  const empty = dashboardFromDto({ ...dash, nextGame: null, divisionRank: null, cap: null });
  assert.equal(empty.nextGame, null);
  assert.equal(empty.divisionRank, null);
  assert.equal(empty.cap, null);
});

test("career-calendar: semanas, jogos e franquias só têm campos conhecidos; 17 jogos e 1 bye por franquia; rodadas de playoff planejadas", () => {
  assertShape(cal, "calendar", "calendar");
  assert.ok(Array.isArray(cal.playoffWeeks), "playoffWeeks (D-105)");
  for (const p of each(cal.playoffWeeks)) assertShape(p, "playoffWeek", "calendar.playoffWeeks[]");
  for (const c of each(cal.clubs)) assertShape(c, "clubRef", "calendar.clubs[]");
  for (const w of each(cal.weeks)) {
    assertShape(w, "week", "calendar.weeks[]");
    for (const f of each(w.fixtures)) assertShape(f, "fixture", "calendar.weeks[].fixtures[]");
  }
  const c = calendarFromDto(cal);
  assert.equal(c.weeks.length, c.regularSeasonWeeks);
  const user = c.userClubId;
  const games = c.weeks.flatMap((w) => w.fixtures).filter((f) => f.homeClubId === user || f.awayClubId === user);
  assert.equal(games.length, c.gamesPerTeam);
  assert.ok(games.every((f) => f.userGame));
  assert.equal(c.weeks.filter((w) => w.userBye).length, 1);
  assert.ok(c.weeks.filter((w) => w.userBye).every((w) => w.byeClubIds.includes(user)));
  assert.deepEqual(c.playoffWeeks.map((p) => p.kind), ["WILD_CARD", "DIVISIONAL", "CONFERENCE", "FINAL"]);
  assert.deepEqual(c.playoffWeeks.map((p) => p.number), [c.regularSeasonWeeks + 1, c.regularSeasonWeeks + 2, c.regularSeasonWeeks + 3, c.regularSeasonWeeks + 4]);
  assert.ok(c.playoffWeeks.every((p) => p.status === "PENDING" && /^\d{4}-\d{2}-\d{2}$/.test(p.startsOn)));
  assert.deepEqual(calendarFromDto({ ...cal, playoffWeeks: undefined }).playoffWeeks, [], "calendário antigo sem playoffWeeks: lista vazia");
});

test("roster: elenco, regras, contadores, teto e cards só têm campos conhecidos", () => {
  assertShape(roster, "roster", "roster");
  assertShape(roster.rules, "rosterRules", "roster.rules");
  assertShape(roster.counts, "counts", "roster.counts");
  assertShape(roster.cap, "cap", "roster.cap");
  assert.deepEqual(Object.keys(asObj(roster.units)).sort(), ["DEFENSE", "OFFENSE", "SPECIAL_TEAMS"]);
  for (const cards of Object.values(asObj(roster.units))) for (const c of each(cards)) assertCard(c, "roster.units[][]");
  for (const c of each(roster.practiceSquad)) assertCard(c, "roster.practiceSquad[]");
  for (const c of each(roster.injuredReserve)) assertCard(c, "roster.injuredReserve[]");
  assert.ok(Array.isArray(roster.waivers), "lista dos expostos a waivers (Q-23)");
  for (const c of each(roster.waivers)) assertCard(c, "roster.waivers[]");
});

test("roster: vira 53 por unidade + practice squad; macros na família e nível pelo overall da lib de ratings", () => {
  const r = rosterFromDto(roster);
  const in53 = r.units.OFFENSE.length + r.units.DEFENSE.length + r.units.SPECIAL_TEAMS.length;
  assert.equal(in53, r.counts.roster);
  assert.ok(in53 <= r.rules.rosterMax);
  assert.equal(r.practiceSquad.length, r.counts.practiceSquad);
  assert.equal(r.waivers.length, r.counts.waivers);
  assert.equal(r.cap.deadMoneyNextSeason, 0, "carreira nova: nada lançado na próxima temporada");
  assert.ok(r.practiceSquad.length <= r.rules.practiceSquadMax);
  for (const c of [...r.units.OFFENSE, ...r.units.DEFENSE, ...r.units.SPECIAL_TEAMS, ...r.practiceSquad]) {
    assert.equal(c.family, FAMILY_OF[c.position], `família de ${c.position}`);
    assert.equal(c.tier, cardTier(c.overall), `nível de ${c.name}`);
    assert.ok(c.secondaryPosition === null || typeof c.secondaryPosition === "string", "secundária ausente vira null");
  }
});

test("roster-player: jogador, contrato e card só têm campos conhecidos", () => {
  assertShape(player, "playerDetail", "roster-player");
  assertCard(player.card, "roster-player.card");
  assertShape(player.contract, "contract", "roster-player.contract");
  for (const m of each(player.history)) assertShape(m, "rosterMove", "roster-player.history[]");
});

test("roster-player: overall por posição e macros batem com a lib de ratings (OVR v1)", () => {
  const p = playerDetailFromDto(player);
  const attrs = p.attributes as unknown as GroupedAttributes;
  const byPos = overallByPosition(attrs);
  for (const [pos, v] of Object.entries(p.overallByPosition)) assert.equal(byPos[pos as Position], v, `overall em ${pos}`);
  assert.equal(byPos[p.card.position], p.card.overall);
  assert.deepEqual(macros(attrs, FAMILY_OF[p.card.position]), p.card.macros);
  assert.equal(p.irPlacedWeek, null);
  assert.equal(p.contract?.capHitByYear.length, p.contract?.years);
  // a semana 1 começa em setembro: regra do 1º de junho (Q-24) — a parcela do ano agora, o resto (o corte no ano seguinte) depois
  assert.equal(p.deadMoneyIfReleased + p.deadMoneyNextSeasonIfReleased, p.contract?.deadMoneyIfCutByYear[0]);
  assert.equal(p.deadMoneyNextSeasonIfReleased, p.contract?.deadMoneyIfCutByYear[1] ?? 0);
  assert.ok(p.deadMoneyNextSeasonIfReleased > 0, "o QB do golden tem bônus rateado nos anos seguintes");
  assert.equal(playerDetailFromDto({ ...player, birthDate: "null" }).birthDate, null, "String.valueOf(null) do backend vira null");
});

test("depth-chart: vagas, jogadores e problemas só têm campos conhecidos", () => {
  assertShape(depth, "depthChart", "depth-chart");
  for (const u of each(depth.units)) {
    assertShape(u, "depthUnit", "depth-chart.units[]");
    for (const s of each(u.slots)) {
      assertShape(s, "depthSlot", "depth-chart.units[].slots[]");
      for (const e of each(s.players)) assertShape(e, "depthEntry", "depth-chart.units[].slots[].players[]");
    }
  }
  for (const i of [...each(depth.errors), ...each(depth.warnings)]) assertShape(i, "depthIssue", "depth-chart.issues[]");
});

test("depth-chart: papéis sem posição viram null; versão e limite de ativos do dia de jogo", () => {
  const d = depthChartFromDto(depth);
  assert.equal(d.version, depth.version);
  assert.deepEqual(d.units.map((u) => u.unit), ["OFFENSE", "DEFENSE", "SPECIAL_TEAMS"]);
  const slots = d.units.flatMap((u) => u.slots);
  for (const role of ["KR", "PR", "H", "THIRD_DOWN_BACK"]) {
    const s = slots.find((x) => x.slot === role);
    assert.ok(s, `vaga ${role}`);
    assert.equal(s!.role, true);
    assert.equal(s!.position, null);
  }
  assert.ok(d.uniquePlayers <= d.gameDayActives);
  for (const s of slots) s.players.forEach((e, i) => assert.equal(e.depth, i + 1, `profundidade em ${s.slot}`));
});

test("problem-409-depth-chart: conflito de versão com as duas versões e mensagem traduzida nos 3 idiomas", () => {
  for (const k of ["code", "message", "details", "correlationId", "locale"]) assert.ok(k in conflict, `falta ${k}`);
  const err = new ApiError(problemFromBody(409, conflict));
  assert.equal(err.status, 409);
  assert.equal(err.code, "DEPTH_CHART_VERSION_CONFLICT");
  assert.equal(typeof err.problem.details.currentVersion, "number");
  assert.equal(typeof err.problem.details.expectedVersion, "number");
  for (const loc of ["pt-BR", "en-US", "es-ES"] as const) {
    assert.ok(err.code in MESSAGES[loc].errors, `${loc} sem errors:${err.code}`);
  }
  // o catálogo usa {0}/{1}; sem argumentos posicionais no corpo, a tela mostra a mensagem que o servidor já traduziu
  setRuntimeLocale("pt-BR");
  assert.equal(describeError(err), conflict.message);
});

test("problem-422-cap-exceeded: dispensa recusada com teto, comprometido e excedente; a tela mostra o excedente (Q-24)", () => {
  const err = new ApiError(problemFromBody(422, capExceeded));
  assert.equal(err.status, 422);
  assert.equal(err.code, "CAP_EXCEEDED");
  const d = err.problem.details;
  for (const k of ["salaryCap", "capUsed", "excess"]) assert.equal(typeof d[k], "number", `details.${k}`);
  assert.equal(d.excess, (d.capUsed as number) - (d.salaryCap as number));
  assert.equal(capExceededExcess(err), d.excess);
  assert.equal(capExceededExcess(new ApiError(problemFromBody(409, conflict))), null, "outro erro não tem excedente");
  for (const loc of ["pt-BR", "en-US", "es-ES"] as const) {
    assert.ok(err.code in MESSAGES[loc].errors, `${loc} sem errors:${err.code}`);
    assert.match((MESSAGES[loc].roster as Record<string, string>)["release.capExceeded"], /\{excess\}/, `${loc}: a tela mostra o excedente`);
  }
  setRuntimeLocale("pt-BR");
  assert.equal(describeError(err), capExceeded.message, "sem argumentos posicionais no corpo, vale a mensagem do servidor");
  assert.match(String(capExceeded.message), /S\$ [\d.]+,\d{2}/);
});

test("world-editor-club: a nova carreira lê da franquia só campos que existem no ClubDto", () => {
  const known = SHAPES.clubOption.fields;
  for (const k of known.filter((f) => !SHAPES.clubOption.nullable.includes(f as never))) assert.ok(k in editorClub, `ClubDto sem ${k}`);
  const c = clubOptionFromDto(editorClub);
  assert.equal(c.id, editorClub.id);
  assert.equal(c.shortName, editorClub.shortName);
  assert.equal(c.expectedWins, editorClub.expectedWins);
  const grouped = leagueClubsFrom(String(editorClub.leagueId), { conferences: [{ id: editorClub.conferenceId, name: "Conferência", shortName: "CAT", divisions: [{ id: editorClub.divisionId, name: "Divisão", clubIds: [editorClub.id] }] }] }, [editorClub, { ...editorClub, id: "outro" }]);
  assert.equal(grouped.conferences[0].divisions[0].clubs[0].id, editorClub.id);
  assert.deepEqual(grouped.unassigned.map((x) => x.id), ["outro"]);
});
