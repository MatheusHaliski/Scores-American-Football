// "Jogar" na semana de folga (revisão do bloco 6b, D-92): a preparação mostra o próximo jogo (semana N+1), mas
// `POST /weeks/current/play` age sobre a semana atual N, que é folga, e volta sem matchId. O "Jogar" encerra a folga e joga de novo,
// terminando na partida do jogo que a preparação mostrava, e conta a folga encerrada para a tela avisar. Contra o modo mock.
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { createMockApi } from "@/lib/api/mock";
import { playToMatch } from "@/lib/match/play";
import type { CareerSummary, MatchState } from "@/lib/api/types";

const api = createMockApi();

/** Joga a partida da semana atual até o fim (decisão aberta: a opção que a IA tomaria). */
async function playWeek(careerId: string) {
  const board = await api.weeks.playCurrent(careerId);
  let s: MatchState = await api.matches.get(board.matchId!);
  for (let i = 0; i < 200 && s.status !== "FINISHED"; i++) {
    s = s.status === "AWAITING_DECISION"
      ? await api.matches.playCall(s.matchId, s.live.decision!.id, { optionCode: s.live.decision!.autoOptionCode, version: s.live.version })
      : await api.matches.advance(s.matchId, { mode: "FULL", version: s.live.version });
  }
  assert.equal(s.status, "FINISHED");
}

test("semana de folga seguida de \"Jogar\": encerra a folga e termina na partida do próximo jogo", { timeout: 120_000 }, async () => {
  await api.auth.register({ email: `bye-${randomUUID().slice(0, 8)}@scores.test`, password: randomUUID(), displayName: "Coach folga" });
  const world = (await api.worlds.list())[0];
  const [league] = await api.worlds.leagues(world.id);
  const lc = await api.worlds.leagueClubs(world.id, league.id);
  const ram = lc.conferences.flatMap((c) => c.divisions.flatMap((d) => d.clubs)).find((c) => c.shortName === "RAM")!;
  const career: CareerSummary = await api.careers.create({ name: "Folga", clubId: ram.id, coachStyle: "OFFENSIVE", seed: 55 });
  const cal = await api.careers.calendar(career.id);
  const bye = cal.weeks.find((w) => w.userBye)!.number;
  assert.ok(bye <= 6, `folga cedo para o teste ser curto (semana ${bye})`);
  for (let w = 1; w < bye; w++) await playWeek(career.id);

  // a preparação já mostra o jogo da semana seguinte à folga
  const prep = await api.gamePlan.preparation(career.id);
  assert.equal((await api.careers.dashboard(career.id)).currentWeek, bye);
  assert.equal(prep.fixture.week, bye + 1);

  const out = await playToMatch(api.weeks, career.id);
  assert.deepEqual(out.byeWeeks, [bye], "a folga encerrada vem contada para o aviso");
  assert.equal(out.board.week, bye + 1);
  assert.ok(out.board.matchId, "terminou na partida");
  assert.equal(out.board.userFixtureId, prep.fixture.id, "a partida é a do jogo que a preparação mostrava");
  assert.equal((await api.matches.get(out.board.matchId!)).status, "NOT_STARTED");
});
