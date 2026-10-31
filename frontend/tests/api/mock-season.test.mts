// Modo mock do bloco 6c com as regras e os códigos de erro do backend: classificação projetada até a última semana regular, sementes
// congeladas e a primeira rodada criada no fim dela, playoffs com folga da semente 1 e re-seed, semana de playoff que fecha na hora
// quando o usuário folga ou não joga, 409 NO_NEXT_GAME depois da final, avanço só depois da final (422 SEASON_NOT_FINISHED), 409
// SEASON_ALREADY_ADVANCED com a temporada já fechada, histórico por número, troféus, moral e analista (404 antes do primeiro
// relatório, 429 no 7º pedido do minuto). Tudo o que o mock devolve passa pelos `SEASON_SHAPES` dos goldens (o mock não inventa campo).
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { createMockApi } from "@/lib/api/mock";
import { mockCareerAccess, mockCareers } from "@/lib/api/mock/career";
import { mockMatches } from "@/lib/api/mock/match";
import { mockSeason } from "@/lib/api/mock/season";
import { getUser } from "@/lib/api/mock/state";
import { SEASON_SHAPES } from "@/lib/api/live-season";
import { ApiError } from "@/lib/api/problem";
import { newsText } from "@/lib/season/news";
import { weekSituation } from "@/lib/season/week";
import type { CareerSummary, UserAccount } from "@/lib/api/types";

const api = createMockApi();
const isCode = (code: string, status?: number) => (e: unknown) => e instanceof ApiError && e.code === code && (status == null || e.status === status);
let user: UserAccount;
let career: CareerSummary;
const careers = () => mockCareers(user);
const matches = () => mockMatches(user);
const season = () => mockSeason(user);

function known(o: unknown, name: keyof typeof SEASON_SHAPES, what: string) {
  const s = SEASON_SHAPES[name] as { fields: readonly string[]; nullable: readonly string[] };
  const present = Object.keys(o as object);
  assert.deepEqual(present.filter((k) => !s.fields.includes(k)), [], `${what}: campo fora do contrato`);
  assert.deepEqual(s.fields.filter((k) => !s.nullable.includes(k) && !present.includes(k)), [], `${what}: campo obrigatório ausente`);
}

/** Joga a semana atual: partida do usuário até o fim (chamando a primeira opção nas decisões) ou fecha a semana sem jogo. */
function playWeek(): { matchId: string | null; userBye: boolean; week: number } {
  const board = matches().playCurrent(career.id);
  if (board.matchId) {
    let st = matches().get(board.matchId);
    for (let i = 0; i < 2000 && st.status !== "FINISHED"; i++) {
      st = st.status === "AWAITING_DECISION" && st.live.decision ? matches().playCall(board.matchId, st.live.decision.id, { optionCode: st.live.decision.options[0].code, version: st.live.version }) : matches().advance(board.matchId, { mode: "FULL", version: st.live.version });
    }
    assert.equal(st.status, "FINISHED");
  }
  return { matchId: board.matchId, userBye: board.userBye, week: board.week };
}

test("carreira nova para o 6c", async () => {
  await api.auth.register({ email: `b6c-${randomUUID().slice(0, 8)}@scores.test`, password: randomUUID(), displayName: "Coach 6c" });
  user = getUser()!;
  const world = (await api.worlds.list())[0];
  const [league] = await api.worlds.leagues(world.id);
  const lc = await api.worlds.leagueClubs(world.id, league.id);
  const pav = lc.conferences.flatMap((c) => c.divisions.flatMap((d) => d.clubs)).find((c) => c.shortName === "PAV")!;
  career = await api.careers.create({ name: "Temporada 6c", clubId: pav.id, coachStyle: "OFFENSIVE", seed: 606 });
});

test("antes do primeiro jogo: projeção, chave projetada, sem analista e avanço recusado com 422 SEASON_NOT_FINISHED", async () => {
  const s = await api.season.standings(career.id);
  known(s, "standings", "standings");
  for (const c of s.conferences) {
    known(c, "conferenceTable", "conference");
    for (const r of c.rows) known(r, "standingsRow", "row");
    assert.deepEqual(c.rows.filter((r) => r.seed != null).map((r) => r.seed), [1, 2, 3, 4, 5, 6, 7]);
    assert.ok(c.rows.slice(0, 4).every((r) => r.tiebreaker === "PROJECTION" || r.tiebreaker === null), "sem jogos, o desempate é a projeção");
  }
  assert.equal(s.official, false);
  assert.equal(s.throughWeek, 0);
  const b = await api.season.bracket(career.id);
  known(b, "bracket", "bracket");
  assert.equal(b.status, "NOT_STARTED");
  assert.equal(b.rounds[0].status, "PROJECTED");
  assert.equal(b.rounds[0].games.length, 6);
  assert.equal(b.rounds[0].byes.length, 2);
  assert.ok(b.rounds[0].games.every((g) => g.fixtureId === null && g.status === "PROJECTED"));
  assert.deepEqual(b.rounds.slice(1).map((r) => r.status), ["PENDING", "PENDING", "PENDING"]);
  const p = await api.season.playoffs(career.id);
  known(p, "playoffs", "playoffs");
  assert.equal(p.projection, true);
  assert.match(String(p.user?.status), /^PROJECTED_(IN|OUT)$/);
  await assert.rejects(api.analysis.get(career.id), isCode("ANALYSIS_NOT_FOUND", 404));
  await assert.rejects(api.season.advance(career.id, { seasonId: career.seasonId }), (e) => isCode("SEASON_NOT_FINISHED", 422)(e) && (e as ApiError).problem.details.phase === "REGULAR" && (e as ApiError).problem.details.currentWeek === 1);
  const d = await api.careers.dashboard(career.id);
  assert.ok(d.morale && d.morale.average > 0);
  assert.deepEqual(d.news, []);
  assert.match(String(d.playoffs?.status), /^PROJECTED_/);
  assert.equal(d.analysis, null);
});

test("temporada regular: notícias por textKey a cada semana e, no fim, sementes congeladas e o Wild Card criado (D-96)", () => {
  playWeek();
  const n = season().weekNews(career.id, 1);
  known(n, "weekNews", "week-news");
  for (const i of n.items) known(i, "news", "news");
  const mine = n.items.find((i) => i.kind.startsWith("USER_"))!;
  assert.ok(mine, "notícia do jogo do usuário");
  assert.deepEqual(Object.keys(mine.params).sort(), ["opponent", "overtime", "pointsAgainst", "pointsFor", "record", "team"]);
  assert.equal(newsText(mine, "en-US").translated, true);
  assert.equal(mine.headline, newsText(mine, "pt-BR").headline, "o mock gera o texto pelo mesmo catálogo");
  assert.throws(() => season().weekNews(career.id, 30), isCode("INVALID_WEEK", 400));
  for (let w = 2; w <= 18; w++) playWeek();
  const c = careers().get(career.id);
  assert.equal(c.seasonPhase, "PLAYOFFS");
  assert.equal(c.currentWeek, 19);
  const s = season().standings(career.id);
  assert.equal(s.official, true);
  assert.equal(s.throughWeek, 18);
  for (const conf of s.conferences) {
    assert.ok(conf.rows.slice(0, 7).every((r, i) => r.seed === i + 1 && r.clinch != null));
    assert.ok(conf.rows.slice(7).every((r) => r.seed === null && r.eliminated));
  }
  const cal = careers().calendar(career.id);
  assert.equal(cal.weeks.length, 19);
  assert.equal(cal.weeks[18].kind, "WILD_CARD");
  assert.deepEqual(cal.playoffWeeks.map((p) => p.status), ["CURRENT", "PENDING", "PENDING", "PENDING"]);
  const last = season().weekNews(career.id, 18).items.map((i) => i.kind);
  assert.ok(last.includes("USER_SEED") || last.includes("USER_MISSED"));
  assert.equal(last.filter((k) => k === "TOP_SEED").length, 2);
  const byes = cal.weeks[18].byeClubIds;
  assert.equal(byes.length, 2, "só as sementes 1 folgam na semana de playoff (D-105)");
});

test("playoffs: folga da semente fecha a semana, quem não joga também; re-seed; depois da final 409 NO_NEXT_GAME", () => {
  const seen: string[] = [];
  for (let i = 0; i < 4; i++) {
    const before = careers().dashboard(career.id);
    const extras = season().dashboardExtras(career.id);
    const situation = weekSituation({ ...before, ...extras, nextGame: before.nextGame });
    const r = playWeek();
    seen.push(situation);
    if (situation === "playoffGame") assert.ok(r.matchId, "jogo do usuário vira partida ao vivo");
    if (situation === "playoffBye") assert.ok(!r.matchId && r.userBye, "semente com folga: a semana fecha na hora");
    if (situation === "playoffOut") assert.ok(!r.matchId && !r.userBye, "eliminado ou fora: fecha a semana sem folga");
  }
  // a carreira é nova a cada execução: a situação do usuário varia; as três situações têm teste próprio logo abaixo, com campanha forçada
  assert.equal(seen.length, 4);
  const b = season().bracket(career.id);
  assert.equal(b.status, "FINISHED");
  assert.deepEqual(b.rounds.map((r) => [r.kind, r.status, r.games.length]), [["WILD_CARD", "COMPLETED", 6], ["DIVISIONAL", "COMPLETED", 4], ["CONFERENCE", "COMPLETED", 2], ["FINAL", "COMPLETED", 1]]);
  for (const r of b.rounds) for (const g of r.games) {
    known(g, "bracketGame", "game");
    assert.ok(g.winnerClubId && g.homeScore !== g.awayScore, "playoff sempre tem vencedor");
  }
  // re-seed: no Divisional, a semente 1 recebe a pior semente viva
  for (const conf of ["CAT", "CPA"]) {
    const div = b.rounds[1].games.filter((g) => g.conference === conf);
    const top = div.find((g) => g.home.seed === 1)!;
    assert.equal(top.away.seed, Math.max(...div.flatMap((g) => [g.home.seed, g.away.seed])));
  }
  const final = b.rounds[3].games[0];
  assert.equal(final.conference, null);
  assert.equal(final.neutralSite, true);
  assert.equal(b.champion?.id, final.winnerClubId);
  assert.throws(() => matches().playCurrent(career.id), isCode("NO_NEXT_GAME", 409));
  assert.throws(() => matches().preparation(career.id), isCode("NO_NEXT_GAME", 409));
  const p = season().playoffs(career.id);
  assert.equal(p.status, "FINISHED");
  assert.equal(p.champion?.id, final.winnerClubId);
  assert.match(String(p.user?.status), /^(CHAMPION|RUNNER_UP|ELIMINATED|MISSED)$/);
  assert.deepEqual(season().weekNews(career.id, 22).items.map((i) => i.kind), ["CHAMPION"]);
  assert.equal(careers().get(career.id).seasonPhase, "OFFSEASON");
});

test("resumo: prêmios oficiais, 9 categorias de líderes e a entressafra liberada", () => {
  const s = season().summary(career.id);
  known(s, "summary", "summary");
  for (const a of s.awards) known(a, "award", "award");
  assert.equal(s.completed, true);
  assert.equal(s.provisionalAwards, false);
  assert.ok(s.awards.some((a) => a.type === "MVP") && s.awards.some((a) => a.type === "COACH"));
  assert.ok(s.awards.every((a) => a.name.length > 1 && !a.name.includes("undefined")), "nome de todo premiado");
  assert.equal(Object.keys(s.leaders).length, 9);
  assert.ok(Object.values(s.leaders).every((l) => l.length <= 5));
  assert.equal(s.offseason?.canAdvance, true);
  assert.notEqual(s.campaign?.playoffResult, "IN_PROGRESS");
});

test("avanço: temporada 2, contratos encerrados, IA renova (D-120); repetir com a temporada fechada dá 409, sem corpo dá 422", () => {
  const before = season().summary(career.id);
  const r = season().advance(career.id, { seasonId: before.seasonId });
  known(r, "advance", "advance");
  assert.equal(r.seasonNumber, 2);
  assert.equal(r.seasonYear, 2027);
  assert.equal(r.currentWeek, 1);
  assert.equal(r.previousSeasonId, before.seasonId);
  assert.ok(r.aiRenewals > 0, "as franquias da IA renovam sozinhas");
  assert.deepEqual(r.userExpiredContracts.map((x) => x.playerId).sort(), (before.offseason?.expiringContracts ?? []).map((x) => x.playerId).sort());
  assert.equal(r.career.currentWeek, 1);
  assert.equal(r.career.seasonPhase, "REGULAR");
  assert.equal(r.career.seasonId, r.seasonId);
  assert.throws(() => season().advance(career.id, { seasonId: before.seasonId }), (e) => isCode("SEASON_ALREADY_ADVANCED", 409)(e) && (e as ApiError).problem.details.currentSeasonNumber === 2);
  assert.throws(() => season().advance(career.id), isCode("SEASON_NOT_FINISHED", 422));
  // histórico e troféus
  const old = season().summary(career.id, 1);
  assert.equal(old.completed, true);
  assert.equal(old.seasonId, before.seasonId);
  assert.throws(() => season().summary(career.id, 9), isCode("notFound.season", 404));
  const t = season().trophies(career.id);
  known(t, "trophies", "trophies");
  assert.deepEqual(t.seasons.map((x) => x.number), [2, 1]);
  assert.ok(t.trophies.some((x) => x.kind === "CHAMPIONSHIP"));
  assert.equal(t.trophies.filter((x) => x.kind === "DIVISION").length, 8);
  // temporada nova zerada, com calendário novo
  const s = season().standings(career.id);
  assert.equal(s.seasonYear, 2027);
  assert.ok(s.conferences.every((c) => c.rows.every((x) => x.record.wins + x.record.losses + x.record.ties === 0)));
  assert.equal(careers().calendar(career.id).weeks.length, 18);
});

test("moral: elenco e jogadores no formato do golden; distribuição soma os 53", () => {
  const m = season().morale(career.id);
  known(m, "squadMorale", "morale");
  for (const p of m.players) known(p, "playerMorale", "player");
  const in53 = m.players.filter((p) => p.rosterStatus === "ACTIVE" || p.rosterStatus === "INACTIVE").length;
  assert.equal(Object.values(m.distribution).reduce((a, n) => a + n, 0), in53);
  assert.ok(m.players.every((p) => p.morale >= 0 && p.morale <= 100));
  assert.ok(m.players.filter((p) => p.role === "RESERVE").every((p) => p.expectedSnapShare === null));
});

test("analista: gera o relatório, 6 pedidos por minuto e o 7º dá 429 com retryAfterSeconds; passada a janela, volta", async () => {
  const realNow = Date.now;
  try {
    const first = season().refreshAnalysis(career.id);
    known(first, "analysis", "analysis");
    for (const o of first.upcoming) known(o, "outlook", "outlook");
    assert.equal(first.units.length, 8);
    assert.equal(first.summaryCode, `NEXT_GAME_${first.upcoming[0].verdict}`);
    assert.deepEqual(season().analysis(career.id), first);
    for (let i = 0; i < 5; i++) season().refreshAnalysis(career.id);
    assert.throws(() => season().refreshAnalysis(career.id), (e) => isCode("RATE_LIMITED", 429)(e) && ((e as ApiError).retryAfterSeconds ?? 0) > 0);
    const t0 = realNow();
    Date.now = () => t0 + 61_000;
    assert.equal(season().refreshAnalysis(career.id).summaryCode.length > 0, true);
    const d = season().dashboardExtras(career.id);
    assert.equal(d.analysis?.nextWinProbability, season().analysis(career.id).upcoming[0].winProbability);
  } finally {
    Date.now = realNow;
  }
});

/** Carreira nova em que a franquia vence (ou perde) todos os jogos da temporada regular, parada antes da última semana. */
async function forcedCareer(shortName: string, name: string, win: boolean): Promise<CareerSummary> {
  const world = (await api.worlds.list())[0];
  const league = (await api.worlds.leagues(world.id))[0];
  const club = (await api.worlds.leagueClubs(world.id, league.id)).conferences.flatMap((c) => c.divisions.flatMap((d) => d.clubs)).find((c) => c.shortName === shortName)!;
  const other = await api.careers.create({ name, clubId: club.id, seed: 1 });
  // placares gravados direto, como o fechamento das semanas faria; a semana 18 fica para o "jogar"
  const acc = mockCareerAccess(user);
  const c = acc.find(other.id);
  c.results = {};
  const [us, them] = win ? [31, 3] : [3, 31];
  for (const f of acc.fixtures(c)) if (f.week < 18) c.results[f.id] = f.homeClubId === club.id ? [us, them] : f.awayClubId === club.id ? [them, us] : [20 + (f.week % 7), 17];
  c.summary.currentWeek = 18;
  acc.commit();
  return other;
}

test("semente 1 invicta: o Wild Card é folga (fecha a semana na hora, userBye) e o Divisional é jogo; central e preparação acompanham", async () => {
  const other = await forcedCareer("RAM", "Semente 1", true);
  const save = career;
  career = other;
  try {
    assert.ok(playWeek().matchId, "a semana 18 é jogo do usuário");
    const p = season().playoffs(other.id);
    assert.equal(p.user?.seed, 1);
    assert.equal(p.user?.status, "BYE");
    const d = { ...careers().dashboard(other.id), ...season().dashboardExtras(other.id) };
    assert.equal(d.byeWeek, true, "semana de playoff em que a semente folga");
    assert.equal(weekSituation(d), "playoffBye");
    assert.throws(() => matches().preparation(other.id), isCode("NO_NEXT_GAME", 409), "a próxima rodada ainda não existe");
    const bye = playWeek();
    assert.deepEqual([bye.week, bye.matchId, bye.userBye], [19, null, true]);
    const d2 = { ...careers().dashboard(other.id), ...season().dashboardExtras(other.id) };
    assert.equal(weekSituation(d2), "playoffGame");
    assert.equal(d2.playoffs?.round, "DIVISIONAL");
    assert.equal(d2.nextGame?.fixture.week, 20);
    assert.equal(matches().week(other.id, 20).kind, "DIVISIONAL");
    assert.equal(matches().preparation(other.id).fixture.week, 20);
    assert.ok(playWeek().matchId, "o Divisional do usuário é partida ao vivo");
  } finally {
    career = save;
  }
});

test("fora dos playoffs (0-17): cada rodada fecha na hora sem folga, até a final; depois, 409 NO_NEXT_GAME", async () => {
  const other = await forcedCareer("VAU", "Sem vaga", false);
  const save = career;
  career = other;
  try {
    playWeek();
    assert.equal(season().playoffs(other.id).user?.status, "MISSED");
    // jogo de playoff empatado no fim do tempo normal: o mock dá a vitória ao mandante "na prorrogação" (D-110), nunca empate
    const acc = mockCareerAccess(user);
    const tie = season().bracket(other.id).rounds[0].games[0];
    acc.closeWeek(acc.find(other.id), 19, { [tie.fixtureId!]: [17, 17] }, () => 0.5);
    const decided = season().bracket(other.id).rounds[0].games[0];
    assert.deepEqual([decided.homeScore, decided.awayScore, decided.overtime, decided.winnerClubId], [20, 17, true, decided.home.club.id]);
    assert.equal(season().weekNews(other.id, 19).items.find((i) => i.fixtureId === tie.fixtureId)?.params.overtime, 1);
    for (const week of [20, 21, 22]) {
      const d = { ...careers().dashboard(other.id), ...season().dashboardExtras(other.id) };
      assert.equal(weekSituation(d), "playoffOut");
      assert.equal(d.byeWeek, false, "eliminado não folga (D-105)");
      const r = playWeek();
      assert.deepEqual([r.week, r.matchId, r.userBye], [week, null, false]);
    }
    const d = { ...careers().dashboard(other.id), ...season().dashboardExtras(other.id) };
    assert.equal(weekSituation(d), "seasonOver");
    assert.throws(() => matches().playCurrent(other.id), isCode("NO_NEXT_GAME", 409));
  } finally {
    career = save;
  }
});
