// Classificação na tela (RF16): marca de cada linha pela semente (folga, campeão de divisão, wild card, eliminado), linha de corte
// dos playoffs na tabela da conferência e ordenação pelas colunas com o desempate voltando para a ordem oficial do servidor.
import { test } from "node:test";
import assert from "node:assert/strict";
import { standingsFromDto } from "@/lib/api/live-season";
import type { StandingsRow } from "@/lib/api/season-types";
import { cutlineIndex, defaultDir, pctText, seedMark, sortRows } from "@/lib/season/standings";
import { loadGolden } from "../golden.mts";

const s = standingsFromDto(loadGolden("standings.json"));
const conf = s.conferences[0];

test("marca pela semente: 1 folga, 2–4 campeões de divisão, 5–7 wild card, sem semente eliminado só quando o servidor diz", () => {
  assert.equal(seedMark({ seed: 1, eliminated: false }, 4, 1), "BYE");
  assert.equal(seedMark({ seed: 2, eliminated: false }, 4, 1), "DIVISION");
  assert.equal(seedMark({ seed: 4, eliminated: false }, 4, 1), "DIVISION");
  assert.equal(seedMark({ seed: 5, eliminated: false }, 4, 1), "WILD_CARD");
  assert.equal(seedMark({ seed: 7, eliminated: false }, 4, 1), "WILD_CARD");
  assert.equal(seedMark({ seed: null, eliminated: false }, 4, 1), null);
  assert.equal(seedMark({ seed: null, eliminated: true }, 4, 1), "ELIMINATED");
  // liga com 2 folgas e 2 divisões (editor): a semente 2 também folga e a 3 já é wild card
  assert.equal(seedMark({ seed: 2, eliminated: false }, 2, 2), "BYE");
  assert.equal(seedMark({ seed: 3, eliminated: false }, 2, 2), "WILD_CARD");
  // no golden: 1 folga, 3 campeões de divisão e 3 wild cards por conferência
  const marks = conf.rows.map((r) => seedMark(r, conf.divisions.length, s.byeSeeds)).filter(Boolean);
  assert.deepEqual(marks, ["BYE", "DIVISION", "DIVISION", "DIVISION", "WILD_CARD", "WILD_CARD", "WILD_CARD"]);
});

test("linha de corte depois da última semente da zona (7ª), mesmo com a ordem da tabela", () => {
  assert.equal(cutlineIndex(conf.rows, s.seedsPerConference), 6);
  assert.equal(cutlineIndex([{ seed: null }, { seed: null }], 7), -1);
  assert.equal(cutlineIndex([{ seed: 1 }, { seed: null }, { seed: 2 }], 7), 2);
});

const row = (rank: number, over: Partial<StandingsRow>): StandingsRow => ({ ...conf.rows[0], rank, ...over });

test("ordenar por coluna: o melhor em cima e o empate na coluna volta para a ordem oficial (rank)", () => {
  const rows = [row(1, { pointDiff: 5, pointsAgainst: 20, streak: "W1", winPct: 1 }), row(2, { pointDiff: 12, pointsAgainst: 10, streak: "L2", winPct: 1 }), row(3, { pointDiff: 5, pointsAgainst: 30, streak: "W3", winPct: 0.5 })];
  assert.deepEqual(sortRows(rows, "diff").map((r) => r.rank), [2, 1, 3], "saldo desc, empate 1 antes de 3");
  assert.deepEqual(sortRows([...rows].reverse(), "diff").map((r) => r.rank), [2, 1, 3], "o empate na coluna segue o rank, não a ordem recebida");
  assert.deepEqual(sortRows(rows, "pa").map((r) => r.rank), [2, 1, 3], "pontos contra cresce");
  assert.deepEqual(sortRows(rows, "streak").map((r) => r.rank), [3, 1, 2], "W3 > W1 > L2");
  assert.deepEqual(sortRows(rows, "pct").map((r) => r.rank), [1, 2, 3]);
  assert.deepEqual(sortRows(rows, "diff", "asc").map((r) => r.rank), [1, 3, 2]);
  assert.deepEqual(sortRows([...rows].reverse(), "rank").map((r) => r.rank), [1, 2, 3]);
  assert.equal(defaultDir("rank"), "asc");
  assert.equal(defaultDir("pa"), "asc");
  assert.equal(defaultDir("pf"), "desc");
  const before = rows.map((r) => r.rank);
  sortRows(rows, "diff");
  assert.deepEqual(rows.map((r) => r.rank), before, "não muda a lista recebida");
});

test("ordenar a tabela da conferência do golden por campanha em casa mantém a ordem oficial entre os empatados", () => {
  const sorted = sortRows(conf.rows, "home");
  for (let i = 1; i < sorted.length; i++) {
    const p = (r: StandingsRow) => (r.home.wins + r.home.ties / 2) / Math.max(1, r.home.wins + r.home.losses + r.home.ties);
    assert.ok(p(sorted[i - 1]) > p(sorted[i]) || (p(sorted[i - 1]) === p(sorted[i]) && sorted[i - 1].rank < sorted[i].rank));
  }
});

test("percentual com três casas sem o zero à esquerda", () => {
  assert.equal(pctText(1), "1.000");
  assert.equal(pctText(0.5), ".500");
  assert.equal(pctText(0), ".000");
  assert.equal(pctText(0.6667), ".667");
});
