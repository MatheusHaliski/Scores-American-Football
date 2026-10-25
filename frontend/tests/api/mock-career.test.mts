// Modo mock da carreira (bloco 6a): uma carreira jogável com as regras do backend — slots, arquivar/excluir por nome, calendário
// do golden, elenco de 53 + practice squad + IR coerente com a lib de ratings, movimentos, dispensa com dead money no teto e
// depth chart com versão (409) e validação (422). Em Node não há localStorage: o mock fica só em memória.
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { createMockApi } from "@/lib/api/mock";
import { ApiError } from "@/lib/api/problem";
import { describeError } from "@/lib/api/errors";
import { cardTier } from "@/lib/ratings/tier";
import type { CareerSummary } from "@/lib/api/types";

const api = createMockApi();
const isCode = (code: string, status?: number) => (e: unknown) => e instanceof ApiError && e.code === code && (status == null || e.status === status);
let career: CareerSummary;

test("nova carreira: mundo padrão → liga → franquias agrupadas por conferência e divisão", async () => {
  await api.auth.register({ email: `coach-${randomUUID().slice(0, 8)}@scores.test`, password: randomUUID(), displayName: "Treinadora Mock" });
  const worlds = await api.worlds.list();
  const world = worlds.find((w) => w.defaultTemplate)!;
  assert.ok(world);
  const [league] = await api.worlds.leagues(world.id);
  assert.equal(league.teamCount, 32);
  const lc = await api.worlds.leagueClubs(world.id, league.id);
  assert.equal(lc.conferences.length, 2);
  assert.ok(lc.conferences.every((c) => c.divisions.length === 4 && c.divisions.every((d) => d.clubs.length === 4)));
  const pav = lc.conferences.flatMap((c) => c.divisions.flatMap((d) => d.clubs)).find((c) => c.shortName === "PAV")!;
  await assert.rejects(api.careers.create({ name: "ab", clubId: pav.id }), isCode("CAREER_NAME_INVALID", 422));
  career = await api.careers.create({ name: "Dinastia Dourada", worldId: world.id, leagueId: league.id, clubId: pav.id, coachStyle: "DEVELOPER", difficulty: "HARD", seed: 2026 });
  assert.equal(career.slot, 1);
  assert.equal(career.coachName, "Treinadora Mock", "sem nome, o head coach é o usuário");
  assert.equal(career.coachStyle, "DEVELOPER");
  assert.equal(career.clubShortName, "PAV");
  await assert.rejects(api.careers.create({ name: "dinastia dourada", clubId: pav.id }), isCode("CAREER_NAME_TAKEN", 409));
});

test("carreiras salvas: 5 slots, arquivar libera o slot e excluir exige o nome exato", async () => {
  const lc = await api.worlds.leagueClubs((await api.worlds.list())[0].id, career.leagueId!);
  const clubId = lc.conferences[1].divisions[0].clubs[0].id;
  for (let i = 2; i <= 5; i++) await api.careers.create({ name: `Carreira ${i}`, clubId, seed: i });
  await assert.rejects(api.careers.create({ name: "Sexta", clubId }), isCode("CAREER_SLOTS_FULL", 422));
  let list = await api.careers.list();
  assert.equal(list.activeCount, 5);
  assert.deepEqual(list.freeSlots, []);
  const fifth = list.careers.find((c) => c.slot === 5)!;
  const archived = await api.careers.update(fifth.id, { status: "ARCHIVED" });
  assert.equal(archived.slot, null);
  list = await api.careers.list();
  assert.deepEqual(list.freeSlots, [5]);
  assert.equal(list.careers.at(-1)?.status, "ARCHIVED");
  await assert.rejects(api.careers.update(fifth.id, { name: "Outro nome" }), isCode("CAREER_ARCHIVED", 422));
  await assert.rejects(api.careers.remove(fifth.id, "carreira 5"), isCode("CAREER_CONFIRMATION_REQUIRED", 422));
  await api.careers.remove(fifth.id, "Carreira 5");
  for (const c of (await api.careers.list()).careers.filter((c) => c.name.startsWith("Carreira "))) await api.careers.remove(c.id, c.name);
  assert.equal((await api.careers.list()).activeCount, 1);
});

test("central da franquia: próximo jogo, divisão, destaques, teto e nada inventado para o que ainda não existe", async () => {
  const d = await api.careers.dashboard(career.id);
  assert.equal(d.currentWeek, 1);
  assert.ok(d.nextGame, "semana 1 tem jogo");
  assert.equal(d.nextGame!.fixture.week, 1);
  assert.equal(d.division.length, 4);
  assert.equal(d.division.filter((r) => r.userClub).length, 1);
  assert.equal(d.rosterHighlights.length, 5);
  assert.ok(d.injuries.length >= 1, "o mock traz um lesionado para a tela");
  assert.ok(d.cap && d.cap.capSpace > 0 && d.cap.capUsed <= d.cap.salaryCap);
  assert.equal(d.depthChart?.valid, true);
  for (const k of ["live", "analysis", "inbox", "unreadInbox", "scoresPoints"] as const) assert.equal(d[k], null, `${k} nulo`);
  // bloco 6c (D-106): moral do elenco, notícias (nenhuma antes da 1ª semana) e a projeção dos playoffs
  assert.ok(d.morale && d.morale.average >= 0 && d.morale.average <= 100);
  assert.deepEqual(d.news, []);
  assert.match(String(d.playoffs?.status), /^PROJECTED_(IN|OUT)$/);
});

test("calendário: 18 semanas, 17 jogos e um bye da franquia", async () => {
  const c = await api.careers.calendar(career.id);
  assert.equal(c.weeks.length, 18);
  const mine = c.weeks.flatMap((w) => w.fixtures).filter((f) => f.userGame);
  assert.equal(mine.length, 17);
  assert.equal(c.weeks.filter((w) => w.userBye).length, 1);
  assert.equal((await api.careers.calendar(career.id, 3)).weeks.length, 1);
  await assert.rejects(api.careers.calendar(career.id, 40), isCode("INVALID_WEEK"));
});

test("elenco: 53 por unidade, practice squad de 16, IR; cards coerentes com a lib de ratings", async () => {
  const r = await api.roster.get(career.id);
  const units = [...r.units.OFFENSE, ...r.units.DEFENSE, ...r.units.SPECIAL_TEAMS];
  assert.equal(units.length, 53);
  assert.equal(r.counts.roster, 53);
  assert.equal(r.practiceSquad.length, 16);
  assert.equal(r.injuredReserve.length, 1);
  for (const c of units) assert.equal(c.tier, cardTier(c.overall));
  assert.ok(r.cap.capUsed <= r.cap.salaryCap);
  const qb = r.units.OFFENSE.find((c) => c.depthChart.includes("QB1"))!;
  const detail = await api.roster.player(career.id, qb.id);
  assert.equal(detail.overallByPosition.QB, qb.overall);
  assert.equal(detail.card.overall, qb.overall);
  assert.ok(detail.contract && detail.deadMoneyIfReleased >= 0);
});

test("movimentos: regras da liga, saída do depth chart e dispensa com dead money no teto", async () => {
  const r = await api.roster.get(career.id);
  const ps = r.practiceSquad[0];
  await assert.rejects(api.roster.changeStatus(career.id, ps.id, "ACTIVE"), isCode("ROSTER_FULL", 422));
  await assert.rejects(api.roster.changeStatus(career.id, ps.id, "IR"), isCode("ROSTER_MOVE_NOT_ALLOWED", 422));
  const healthy = r.units.DEFENSE.find((c) => c.injuryWeeksOut === 0)!;
  await assert.rejects(api.roster.changeStatus(career.id, healthy.id, "IR"), (e: unknown) => isCode("IR_NOT_ALLOWED", 422)(e) && /4/.test(describeError(e)));

  const starter = r.units.DEFENSE.find((c) => c.depthChart.length > 0)!;
  const before = await api.depthChart.get(career.id);
  const moved = await api.roster.changeStatus(career.id, starter.id, "INACTIVE");
  assert.equal(moved.player.rosterStatus, "INACTIVE");
  assert.deepEqual(moved.player.depthChart, [], "inativo sai do depth chart");
  assert.equal(moved.counts.roster, 53, "inativo continua nos 53");
  assert.ok((await api.depthChart.get(career.id)).version > before.version);

  const detail = await api.roster.player(career.id, starter.id);
  const rel = await api.roster.release(career.id, starter.id);
  assert.equal(rel.rosterStatus, "WAIVED");
  assert.equal(rel.deadMoney, detail.deadMoneyIfReleased);
  assert.equal(rel.counts.roster, 52);
  assert.equal(rel.cap.deadMoney, detail.deadMoneyIfReleased);
  assert.equal(rel.cap.capUsed, r.cap.capUsed - rel.capHitRemoved + rel.deadMoney);
  await assert.rejects(api.roster.release(career.id, starter.id), isCode("PLAYER_NOT_IN_ROSTER"));
  const back = await api.roster.changeStatus(career.id, ps.id, "ACTIVE");
  assert.equal(back.counts.roster, 53, "com vaga, o practice squad sobe");
});

test("depth chart: salvar com versão, 409 com as duas versões e 422 com a lista de erros", async () => {
  const d = await api.depthChart.get(career.id);
  const slots = d.units.flatMap((u) => u.slots).map((s) => ({ slot: s.slot, playerIds: s.players.map((p) => p.playerId) }));
  const wr = slots.find((s) => s.slot === "WR")!;
  wr.playerIds = [wr.playerIds[1], wr.playerIds[0], ...wr.playerIds.slice(2)];
  const saved = await api.depthChart.save(career.id, { version: d.version, slots });
  assert.equal(saved.version, d.version + 1);
  assert.equal(saved.units[0].slots.find((s) => s.slot === "WR")!.players[0].playerId, wr.playerIds[0]);

  await assert.rejects(api.depthChart.save(career.id, { version: d.version, slots }), (e: unknown) => {
    if (!isCode("DEPTH_CHART_VERSION_CONFLICT", 409)(e)) return false;
    const err = e as ApiError;
    return err.problem.details.currentVersion === saved.version && err.problem.details.expectedVersion === d.version && describeError(err).includes(String(saved.version));
  });

  const incomplete = slots.map((s) => (s.slot === "QB" ? { slot: "QB", playerIds: [] } : s));
  await assert.rejects(api.depthChart.save(career.id, { version: saved.version, slots: incomplete }), (e: unknown) => {
    if (!isCode("DEPTH_CHART_INCOMPLETE", 422)(e)) return false;
    const errors = (e as ApiError).problem.details.errors as { slot: string }[];
    return errors.some((x) => x.slot === "QB") && /QB/.test(describeError(e));
  });
  await assert.rejects(api.depthChart.save(career.id, { version: saved.version, slots: [...slots, { slot: "XX", playerIds: [] }] }), isCode("DEPTH_CHART_UNKNOWN_SLOT", 400));

  const r = await api.roster.get(career.id);
  const cb = r.units.DEFENSE.find((c) => c.position === "CB" && !c.depthChart.some((l) => l.startsWith("KR")))!;
  const withRole = await api.depthChart.setRole(career.id, cb.id, { role: "KR", depth: 1, version: saved.version });
  assert.equal(withRole.units[2].slots.find((s) => s.slot === "KR")!.players[0].playerId, cb.id);
  const dlineman = r.units.DEFENSE.find((c) => c.position === "DT")!;
  const oop = await api.depthChart.setRole(career.id, dlineman.id, { role: "PR", depth: 2 });
  assert.ok(oop.warnings.some((w) => w.code === "OUT_OF_POSITION" && w.slot === "PR" && w.playerId === dlineman.id), "DT de retornador gera aviso de fora de posição");
});

test("carreira de outro usuário não aparece (404)", async () => {
  await api.auth.register({ email: `outro-${randomUUID().slice(0, 8)}@scores.test`, password: randomUUID(), displayName: "Outro" });
  await assert.rejects(api.careers.dashboard(career.id), isCode("notFound.career", 404));
  assert.equal((await api.careers.list()).careers.length, 0);
});
