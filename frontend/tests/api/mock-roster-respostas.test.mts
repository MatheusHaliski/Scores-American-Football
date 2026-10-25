// Respostas do PO (25/10/2026) no modo mock da carreira, com as regras do backend: dos 53 para o practice squad passa por waivers até
// a semana avançar (Q-23) e a dispensa segue a regra do 1º de junho (Q-24; o calendário do mock começa em setembro, então a parcela do
// ano entra no teto agora e o resto vai para a próxima temporada).
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { createMockApi } from "@/lib/api/mock";
import { ApiError } from "@/lib/api/problem";
import type { CareerSummary, MatchState } from "@/lib/api/types";

const api = createMockApi();
const isCode = (code: string, status?: number) => (e: unknown) => e instanceof ApiError && e.code === code && (status == null || e.status === status);
let career: CareerSummary;

test("carreira nova para as respostas Q-22..Q-28", async () => {
  await api.auth.register({ email: `q23-${randomUUID().slice(0, 8)}@scores.test`, password: randomUUID(), displayName: "Coach Q23" });
  const world = (await api.worlds.list())[0];
  const [league] = await api.worlds.leagues(world.id);
  const lc = await api.worlds.leagueClubs(world.id, league.id);
  const pav = lc.conferences.flatMap((c) => c.divisions.flatMap((d) => d.clubs)).find((c) => c.shortName === "PAV")!;
  career = await api.careers.create({ name: "Waivers e Teto", clubId: pav.id, seed: 23 });
});

test("Q-23: dos 53 para o practice squad fica em waivers (vaga reservada) e entra no practice squad quando a semana avança", async () => {
  const r = await api.roster.get(career.id);
  await api.roster.release(career.id, r.practiceSquad[0].id);
  const young = [...r.units.DEFENSE, ...r.units.OFFENSE].filter((c) => c.experienceYears <= 3);
  const moved = await api.roster.changeStatus(career.id, young[0].id, "PRACTICE_SQUAD");
  assert.equal(moved.player.rosterStatus, "PRACTICE_SQUAD_WAIVERS");
  assert.equal(moved.counts.waivers, 1);
  assert.equal(moved.counts.practiceSquad, 15);
  assert.equal(moved.counts.roster, 52);
  const during = await api.roster.get(career.id);
  assert.deepEqual(during.waivers.map((c) => c.id), [young[0].id]);
  assert.ok(!during.practiceSquad.some((c) => c.id === young[0].id));
  await assert.rejects(api.roster.changeStatus(career.id, young[1].id, "PRACTICE_SQUAD"), isCode("PRACTICE_SQUAD_FULL", 422));
  await assert.rejects(api.roster.changeStatus(career.id, young[0].id, "ACTIVE"), isCode("ROSTER_MOVE_NOT_ALLOWED", 422));

  const board = await api.weeks.playCurrent(career.id);
  let state: MatchState = await api.matches.get(board.matchId!);
  for (let i = 0; i < 200 && state.status !== "FINISHED"; i++) {
    if (state.status === "AWAITING_DECISION") state = await api.matches.playCall(state.matchId, state.live.decision!.id, { optionCode: state.live.decision!.autoOptionCode, version: state.live.version });
    else state = await api.matches.advance(state.matchId, { mode: "FULL", version: state.live.version });
  }
  assert.equal(state.status, "FINISHED");
  const after = await api.roster.get(career.id);
  assert.equal(after.currentWeek, 2);
  assert.equal(after.counts.waivers, 0);
  assert.equal(after.counts.practiceSquad, 16);
  assert.ok(after.practiceSquad.some((c) => c.id === young[0].id));
  const detail = await api.roster.player(career.id, young[0].id);
  assert.deepEqual(detail.history.map((h) => `${h.kind}:${h.toStatus}`), ["STATUS_CHANGE:PRACTICE_SQUAD_WAIVERS", "WAIVER_CLEARED:PRACTICE_SQUAD"]);
});

test("Q-24: a partir de 1º de junho, a parcela do ano entra no teto e o resto vai para a próxima temporada", async () => {
  const r = await api.roster.get(career.id);
  let best: { id: string; now: number; next: number } | null = null;
  for (const c of r.units.OFFENSE) {
    const d = await api.roster.player(career.id, c.id);
    if (!best || d.deadMoneyNextSeasonIfReleased > best.next) best = { id: c.id, now: d.deadMoneyIfReleased, next: d.deadMoneyNextSeasonIfReleased };
  }
  assert.ok(best && best.next > 0, "alguém com bônus rateado nos anos seguintes");
  const rel = await api.roster.release(career.id, best!.id);
  assert.equal(rel.deadMoney, best!.now);
  assert.equal(rel.deadMoneyNextSeason, best!.next);
  assert.equal(rel.cap.deadMoneyNextSeason, best!.next);
  assert.equal(rel.cap.capUsed, r.cap.capUsed - rel.capHitRemoved + rel.deadMoney);
});
