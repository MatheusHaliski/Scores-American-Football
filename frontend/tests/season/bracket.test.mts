// Chave dos playoffs na tela (RF66): colunas por conferência (rodadas em ordem, jogos pela melhor semente, folgas da conferência), a
// final à parte, a rodada que abre no celular, o vencedor de cada jogo e o link do relatório só para jogo encerrado.
import { test } from "node:test";
import assert from "node:assert/strict";
import { bracketFromDto } from "@/lib/api/live-season";
import type { Bracket, BracketRound } from "@/lib/api/season-types";
import { bracketView, initialRoundIndex, isPlayoffKind, reportHref, winnerSide } from "@/lib/season/bracket";
import { loadGolden } from "../golden.mts";

const b = bracketFromDto(loadGolden("playoffs-bracket.json"));

test("colunas por conferência: 3 rodadas cada, jogos da própria conferência pela melhor semente e a folga da semente 1", () => {
  const v = bracketView(b);
  assert.deepEqual(v.conferences.map((c) => c.conference), ["CAT", "CPA"]);
  for (const c of v.conferences) {
    assert.deepEqual(c.columns.map((col) => col.round.kind), ["WILD_CARD", "DIVISIONAL", "CONFERENCE"]);
    const wc = c.columns[0];
    assert.equal(wc.games.length, 3);
    assert.ok(wc.games.every((g) => g.conference === c.conference));
    assert.deepEqual(wc.games.map((g) => Math.min(g.home.seed, g.away.seed)), [2, 3, 4], "2×7, 3×6, 4×5");
    assert.deepEqual(wc.byes.map((t) => t.seed), [1]);
    assert.equal(c.columns[1].games.length, 2);
    assert.equal(c.columns[2].games.length, 1);
  }
  assert.equal(v.final?.round.kind, "FINAL");
  assert.equal(v.final?.games.length, 1);
  assert.equal(v.champion?.shortName, "RAM");
});

test("jogos da coluna em ordem da melhor semente mesmo quando o servidor manda embaralhado", () => {
  const shuffled: Bracket = { ...b, rounds: b.rounds.map((r) => ({ ...r, games: [...r.games].reverse() })) };
  assert.deepEqual(bracketView(shuffled).conferences[0].columns[0].games.map((g) => Math.min(g.home.seed, g.away.seed)), [2, 3, 4]);
});

test("chave projetada antes dos playoffs: só a primeira rodada tem jogos; as outras ficam a definir", () => {
  const projected: Bracket = { ...b, status: "NOT_STARTED", champion: null, rounds: b.rounds.map((r, i): BracketRound => (i === 0 ? { ...r, week: null, status: "PROJECTED", games: r.games.map((g) => ({ ...g, fixtureId: null, homeScore: null, awayScore: null, winnerClubId: null, status: "PROJECTED" })) } : { ...r, week: null, status: "PENDING", games: [], byes: [] })) };
  const v = bracketView(projected);
  assert.equal(v.conferences[0].columns[1].games.length, 0);
  assert.equal(v.final?.games.length, 0);
  assert.equal(initialRoundIndex(projected), 0);
  assert.ok(projected.rounds[0].games.every((g) => reportHref("c1", g) === null), "jogo projetado não tem relatório");
});

test("rodada que abre no celular: a atual; sem atual, a próxima com jogos depois da última encerrada; tudo encerrado, a final", () => {
  assert.equal(initialRoundIndex(b), 3, "chave encerrada abre na final");
  const live: Bracket = { ...b, rounds: b.rounds.map((r) => (r.index < 2 ? { ...r, status: "COMPLETED" } : r.index === 2 ? { ...r, status: "CURRENT" } : { ...r, status: "PENDING", games: [] })) };
  assert.equal(initialRoundIndex(live), 2);
  const between: Bracket = { ...b, rounds: b.rounds.map((r) => (r.index === 0 ? { ...r, status: "COMPLETED" } : { ...r, status: "PENDING", games: [] })) };
  assert.equal(initialRoundIndex(between), 0, "sem rodada aberta com jogos: a última encerrada");
});

test("vencedor e link do relatório (rota do 6b) só com placar", () => {
  const final = b.rounds[3].games[0];
  assert.equal(winnerSide(final), final.winnerClubId === final.home.club.id ? "home" : "away");
  assert.equal(reportHref("c1", final), `/c/c1/match/${final.fixtureId}/report`);
  assert.equal(winnerSide({ ...final, winnerClubId: null }), null);
  assert.equal(reportHref("c1", { ...final, homeScore: null, awayScore: null }), null);
});

test("tipos de semana de playoff", () => {
  for (const k of ["WILD_CARD", "DIVISIONAL", "CONFERENCE", "FINAL"]) assert.ok(isPlayoffKind(k));
  for (const k of ["REGULAR", "PRESEASON", null, undefined, ""]) assert.equal(isPlayoffKind(k), false);
});
