// Modo mock do bloco 6b com as regras e os códigos de erro do backend: plano e playbook com versão (409) e validação (422 com
// details.errors), preparação, jogar a semana (422 DEPTH_CHART_INVALID), partida por descida com os modos de avanço, chamada de
// jogada com prazo de 30 s (DECISION_PENDING, PLAY_NOT_OFFERED, DECISION_CLOSED na chamada atrasada), ajustes só no intervalo
// (ADJUSTMENT_NOT_ALLOWED), play-by-play por cursor, relatório só no fim (MATCH_NOT_FINISHED) e a semana fechando ao fim do jogo.
// Tudo o que o mock devolve passa pelos mesmos `MATCH_SHAPES` dos goldens (o mock não inventa campo).
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { createMockApi } from "@/lib/api/mock";
import { ApiError } from "@/lib/api/problem";
import { MATCH_SHAPES } from "@/lib/api/live-match";
import { lastSeqOf, mergeEvents } from "@/lib/match/live";
import { playbookDraftFrom, playbookMeta, playbookWeights, setWeight } from "@/lib/match/plan";
import type { CareerSummary, MatchState, PlayEvent } from "@/lib/api/types";

const api = createMockApi();
const isCode = (code: string, status?: number) => (e: unknown) => e instanceof ApiError && e.code === code && (status == null || e.status === status);
let career: CareerSummary;

function known(o: object, shape: { fields: readonly string[] }, what: string) {
  const extra = Object.keys(o).filter((k) => !shape.fields.includes(k));
  assert.deepEqual(extra, [], `${what}: campo fora do contrato`);
}

const realNow = Date.now;
function at(offsetMs: number, fn: () => Promise<void>) {
  Date.now = () => realNow() + offsetMs;
  return fn().finally(() => {
    Date.now = realNow;
  });
}

test("carreira nova para o 6b", async () => {
  await api.auth.register({ email: `b6b-${randomUUID().slice(0, 8)}@scores.test`, password: randomUUID(), displayName: "Coach 6b" });
  const world = (await api.worlds.list())[0];
  const [league] = await api.worlds.leagues(world.id);
  const lc = await api.worlds.leagueClubs(world.id, league.id);
  const pav = lc.conferences.flatMap((c) => c.divisions.flatMap((d) => d.clubs)).find((c) => c.shortName === "PAV")!;
  career = await api.careers.create({ name: "Temporada 6b", clubId: pav.id, coachStyle: "OFFENSIVE", seed: 66 });
});

test("plano de jogo: nasce do estilo, 409 com versão errada, 422 com errors[] por campo, PUT sobe a versão e vira SAVED", async () => {
  const p = await api.gamePlan.get(career.id);
  known(p, MATCH_SHAPES.gamePlan, "game-plan");
  assert.equal(p.source, "COACH_STYLE");
  assert.equal(p.version, 0);
  const req = { version: p.version, passRatio: p.passRatio, fourthDownAggression: p.fourthDownAggression, tempo: p.tempo, defensiveFocus: p.defensiveFocus, blitzFrequency: p.blitzFrequency, baseDefense: p.baseDefense };
  await assert.rejects(api.gamePlan.save(career.id, { ...req, version: 7 }), (e) => isCode("GAME_PLAN_VERSION_CONFLICT", 409)(e) && (e as ApiError).problem.details.currentVersion === 0);
  await assert.rejects(api.gamePlan.save(career.id, { ...req, passRatio: 90 }), (e) => isCode("GAME_PLAN_INVALID", 422)(e) && JSON.stringify((e as ApiError).problem.details.errors).includes("passRatio"));
  const saved = await api.gamePlan.save(career.id, { ...req, passRatio: 62, tempo: "HURRY_UP" });
  assert.equal(saved.version, 1);
  assert.equal(saved.source, "SAVED");
  assert.equal(saved.passRatio, 62);
});

test("playbook: incompleto (PLAYBOOK_INCOMPLETE), peso fora (PLAYBOOK_INVALID), 409 e PUT com pesos ajustados", async () => {
  const pb = await api.gamePlan.playbook(career.id);
  known(pb, MATCH_SHAPES.playbook, "playbook");
  const meta = playbookMeta(pb);
  const d = playbookDraftFrom(pb);
  const noRun = setWeight(setWeight(d, "RED_ZONE.INSIDE_RUN", 0), "RED_ZONE.OUTSIDE_RUN", 0);
  await assert.rejects(api.gamePlan.savePlaybook(career.id, { version: 0, ...playbookWeights(noRun, meta) }), isCode("PLAYBOOK_INCOMPLETE", 422));
  await assert.rejects(api.gamePlan.savePlaybook(career.id, { version: 0, ...playbookWeights(setWeight(d, "EARLY_DOWN.DEEP_PASS", 250), meta) }), isCode("PLAYBOOK_INVALID", 422));
  await assert.rejects(api.gamePlan.savePlaybook(career.id, { version: 3, ...playbookWeights(d, meta) }), isCode("PLAYBOOK_VERSION_CONFLICT", 409));
  const saved = await api.gamePlan.savePlaybook(career.id, { version: 0, ...playbookWeights(setWeight(setWeight(d, "EARLY_DOWN.DEEP_PASS", 150), "coverages.COVER_0", 0), meta) });
  assert.equal(saved.version, 1);
  assert.equal(saved.customWeights, 2);
});

test("preparação: adversário, confrontos dos dois lados, prontidão pronta e o plano salvo", async () => {
  const p = await api.gamePlan.preparation(career.id);
  known(p, MATCH_SHAPES.preparation, "preparation");
  assert.equal(p.keyMatchups.length, 6);
  assert.ok(p.keyMatchups.every((m) => m.edge === m.ours - m.theirs));
  assert.equal(p.readiness.ready, true);
  assert.equal(p.gamePlan.passRatio, 62);
  assert.equal(p.playbookCustomWeights, 2);
  assert.equal(p.matchId, null);
});

let state: MatchState;
let events: PlayEvent[] = [];
async function sync() {
  const page = await api.matches.events(state.matchId, lastSeqOf(events), 1000);
  known(page, MATCH_SHAPES.matchEvents, "events");
  events = mergeEvents(events, page.events);
}

test("jogar a semana: cria a partida (NOT_STARTED, ajustável), a central mostra a partida em andamento; é idempotente", async () => {
  const board = await api.weeks.playCurrent(career.id);
  known(board, MATCH_SHAPES.weekBoard, "week");
  assert.ok(board.matchId);
  assert.equal((await api.weeks.playCurrent(career.id)).matchId, board.matchId, "idempotente");
  state = await api.matches.get(board.matchId!);
  known(state, MATCH_SHAPES.matchState, "match-state");
  known(state.live, MATCH_SHAPES.liveMatch, "match-state.live");
  assert.equal(state.status, "NOT_STARTED");
  assert.equal(state.adjustable, true);
  assert.equal(state.advanceModes.length, 5);
  const d = await api.careers.dashboard(career.id);
  assert.equal(d.live?.matchId, board.matchId);
  await assert.rejects(api.matches.report(board.matchId!), isCode("MATCH_NOT_FINISHED", 422));
});

test("ajuste antes do kickoff: versão obrigatória (409) e GAME_PLAN aplicado sobe a versão; modo inválido = 400", async () => {
  const plan = await api.gamePlan.get(career.id);
  const gp = { passRatio: 40, fourthDownAggression: plan.fourthDownAggression, tempo: plan.tempo, defensiveFocus: plan.defensiveFocus, blitzFrequency: plan.blitzFrequency, baseDefense: plan.baseDefense };
  await assert.rejects(api.matches.decisions(state.matchId, { version: state.live.version + 5, adjustments: [{ type: "GAME_PLAN", gamePlan: gp }] }), isCode("MATCH_VERSION_CONFLICT", 409));
  await assert.rejects(api.matches.decisions(state.matchId, { version: state.live.version, adjustments: [{ type: "GAME_PLAN", gamePlan: { ...gp, passRatio: 95 } }] }), isCode("GAME_PLAN_INVALID", 422));
  const next = await api.matches.decisions(state.matchId, { version: state.live.version, adjustments: [{ type: "GAME_PLAN", gamePlan: gp }] });
  assert.equal(next.live.version, state.live.version + 1);
  assert.equal(next.status, "NOT_STARTED", "ajuste não avança a partida");
  state = next;
  await assert.rejects(api.matches.advance(state.matchId, { mode: "WARP" as never }), isCode("INVALID_ADVANCE_MODE", 400));
  assert.equal((await api.gamePlan.get(career.id)).passRatio, 62, "o plano salvo da franquia não muda (D-60)");
});

test("partida por descida: PLAY dá o kickoff; decisão aberta bloqueia o avanço; opção fora = 422; chamada do manager vira USER", async () => {
  state = await api.matches.advance(state.matchId, { mode: "PLAY", version: state.live.version });
  await sync();
  assert.ok(events.length >= 2, "cara ou coroa + kickoff");
  assert.equal(events[0].textKey, "match:event.coinToss");
  await assert.rejects(api.matches.decisions(state.matchId, { version: state.live.version, adjustments: [{ type: "PLAYBOOK", playbook: { situations: [], coverages: [] } }] }), isCode("ADJUSTMENT_NOT_ALLOWED", 422), "com a bola em jogo, ajuste do playbook é recusado antes de validar o playbook");
  // avança até a primeira decisão do usuário (4ª descida)
  for (let i = 0; i < 40 && state.status !== "AWAITING_DECISION" && state.status !== "FINISHED"; i++) {
    state = await api.matches.advance(state.matchId, { mode: "TO_DECISION", version: state.live.version });
  }
  assert.equal(state.status, "AWAITING_DECISION", "uma 4ª descida envolvendo o usuário abre decisão");
  const dec = state.live.decision!;
  known(dec, MATCH_SHAPES.decision, "decision");
  assert.deepEqual(state.advanceModes, []);
  assert.ok(state.decisionDeadlineAt);
  await assert.rejects(api.matches.advance(state.matchId), isCode("DECISION_PENDING", 409));
  await assert.rejects(api.matches.playCall(state.matchId, dec.id, { optionCode: "HAIL_MARY", version: state.live.version }), isCode("PLAY_NOT_OFFERED", 422));
  await assert.rejects(api.matches.playCall(state.matchId, "dec-inexistente", { optionCode: dec.options[0].code, version: state.live.version }), isCode("DECISION_NOT_FOUND", 404));
  await assert.rejects(api.matches.playCall(state.matchId, dec.id, { optionCode: dec.options[0].code, version: state.live.version - 1 }), isCode("MATCH_VERSION_CONFLICT", 409));
  const pick = dec.options.find((o) => o.code !== dec.autoOptionCode) ?? dec.options[0];
  state = await api.matches.playCall(state.matchId, dec.id, { optionCode: pick.code, version: state.live.version });
  await sync();
  assert.ok(events.some((e) => e.callSource === "USER"), "o lance chamado pelo manager fica marcado");
  // a mesma decisão de novo: já resolvida
  await assert.rejects(api.matches.playCall(state.matchId, dec.id, { optionCode: pick.code, version: state.live.version }), isCode("DECISION_CLOSED", 409));
});

test("prazo vencido: a chamada atrasada responde 409 DECISION_CLOSED e a decisão automática fica gravada (AUTO)", async () => {
  for (let i = 0; i < 60 && state.status !== "AWAITING_DECISION" && state.status !== "FINISHED"; i++) {
    state = await api.matches.advance(state.matchId, { mode: "TO_DECISION", version: state.live.version });
  }
  assert.equal(state.status, "AWAITING_DECISION", "a semente da carreira tem outra 4ª descida do usuário");
  const dec = state.live.decision!;
  await at(31_000, async () => {
    await assert.rejects(api.matches.playCall(state.matchId, dec.id, { optionCode: dec.options[0].code, version: state.live.version }), (e) => isCode("DECISION_CLOSED", 409)(e) && (e as ApiError).problem.details.auto === true);
    state = await api.matches.get(state.matchId);
  });
  await sync();
  assert.ok(events.some((e) => e.callSource === "AUTO"), "a IA chamou no prazo vencido");
  assert.notEqual(state.live.decision?.id, dec.id);
});

test("até o fim: FULL para nas decisões; o cursor não duplica lances; relatório, semana fechada e central atualizada", async () => {
  for (let i = 0; i < 80 && state.status !== "FINISHED"; i++) {
    if (state.status === "AWAITING_DECISION") state = await api.matches.playCall(state.matchId, state.live.decision!.id, { optionCode: state.live.decision!.autoOptionCode, version: state.live.version });
    else state = await api.matches.advance(state.matchId, { mode: "FULL", version: state.live.version });
    await sync();
  }
  assert.equal(state.status, "FINISHED");
  assert.deepEqual(state.advanceModes, []);
  assert.equal(new Set(events.map((e) => e.seq)).size, events.length);
  assert.deepEqual(events.map((e) => e.seq), events.map((_, i) => i + 1), "todos os lances, em ordem, sem buraco");
  assert.equal(events[events.length - 1].textKey, "match:event.final");
  await assert.rejects(api.matches.advance(state.matchId), isCode("MATCH_FINISHED", 409));
  const r = await api.matches.report(state.matchId);
  known(r, MATCH_SHAPES.report, "report");
  assert.equal(r.home.score, r.home.quarters.reduce((a, q) => a + q, 0));
  assert.equal(r.away.score, r.away.quarters.reduce((a, q) => a + q, 0));
  assert.ok(r.scoringPlays.every((e) => e.points > 0));
  assert.ok(r.decisions.some((d) => d.optionCode === "PLAN_CHANGE"), "o ajuste do intervalo entra no log");
  assert.equal((await api.matches.report(r.fixtureId)).matchId, r.matchId, "o relatório aceita o id do jogo do calendário");
  const d = await api.careers.dashboard(career.id);
  assert.equal(d.live, null);
  assert.equal(d.currentWeek, 2, "a semana fechou (D-63)");
  assert.equal(d.record.wins + d.record.losses + d.record.ties, 1);
  const week1 = await api.weeks.get(career.id, 1);
  assert.ok(week1.completed, "os outros jogos da semana foram simulados");
  const cal = await api.careers.calendar(career.id, 1);
  assert.equal(cal.weeks[0].fixtures.find((f) => f.id === r.fixtureId)?.status, "FINISHED");
});

test("depth chart com erro bloqueia o kickoff: 422 DEPTH_CHART_INVALID com details.errors", async () => {
  const dc = await api.depthChart.get(career.id);
  const qb = dc.units[0].slots.find((s) => s.slot === "QB")!;
  // o PUT do depth chart recusa vaga sem titular (422): tira os QBs do depth chart mandando-os para inativos
  for (const e of qb.players) await api.roster.changeStatus(career.id, e.playerId, "INACTIVE");
  const p = await api.gamePlan.preparation(career.id);
  assert.equal(p.readiness.ready, false);
  await assert.rejects(api.weeks.playCurrent(career.id), (e) => isCode("DEPTH_CHART_INVALID", 422)(e) && Array.isArray((e as ApiError).problem.details.errors));
});
