// Edição do depth chart no navegador: o rascunho sai do golden (depth-chart.json), a validação local concorda com a do backend
// (golden válido e sem avisos) e as operações de mover, tirar e acrescentar geram os problemas certos. Os goldens normalizam os
// ids (00000000-…-0001, -0002…), então depth-chart.json e roster.json não se cruzam por id: os jogadores saem do próprio depth chart.
import { test } from "node:test";
import assert from "node:assert/strict";
import { depthChartFromDto, rosterFromDto } from "@/lib/api/live-career";
import { addToSlot, changedSlots, draftFrom, isOutOfPosition, knownAtSlot, moveInSlot, removeFromSlot, reorder, sameDraft, slotsOf, toRequest, validateDraft, type DepthPlayerInfo } from "@/lib/career/depth";
import { loadGolden } from "../golden.mts";

const dc = depthChartFromDto(loadGolden("depth-chart.json"));
const roster = rosterFromDto(loadGolden("roster.json"));
const slots = slotsOf(dc);
const known = knownAtSlot(dc);
const players = new Map<string, DepthPlayerInfo>();
for (const s of slots) for (const e of s.players) players.set(e.playerId, { id: e.playerId, name: e.name, jerseyNumber: e.jerseyNumber, position: e.position, secondaryPosition: null, overall: e.overall, tier: e.tier, rosterStatus: e.rosterStatus, suspensionWeeks: 0 });
// practice squad do golden de elenco com ids próprios (não colidem com os do depth chart)
const practice = roster.practiceSquad.map((c, i) => ({ ...c, id: `ps-${i}` }));
for (const c of practice) players.set(c.id, c);

test("o rascunho do golden valida como o backend: sem erros, avisos iguais e o mesmo total de jogadores", () => {
  const draft = draftFrom(dc);
  const check = validateDraft(slots, draft, players, known, dc.gameDayActives);
  assert.deepEqual(check.errors, dc.errors);
  assert.equal(check.warnings.length, dc.warnings.length);
  assert.equal(check.uniquePlayers, dc.uniquePlayers);
  assert.equal(toRequest(slots, draft, dc.version).slots.length, slots.length);
});

test("subir, descer e arrastar mudam a ordem sem perder ninguém", () => {
  const draft = draftFrom(dc);
  const qb = draft.QB;
  const moved = moveInSlot(draft, "QB", 1, -1);
  assert.deepEqual(moved.QB, [qb[1], qb[0], ...qb.slice(2)]);
  assert.equal(moveInSlot(draft, "QB", 0, -1), draft, "o primeiro não sobe");
  const dragged = reorder(draft, "WR", 0, 3);
  assert.equal(dragged.WR[3], draft.WR[0]);
  assert.equal(dragged.WR.length, draft.WR.length);
  assert.ok(!sameDraft(draft, dragged));
  assert.deepEqual(changedSlots(draft, dragged), ["WR"]);
});

test("tirar o titular único deixa a vaga incompleta; acrescentar repetido não duplica", () => {
  const draft = draftFrom(dc);
  const noK = removeFromSlot(draft, "K", draft.K[0]);
  const check = validateDraft(slots, noK, players, known, dc.gameDayActives);
  assert.ok(check.errors.some((e) => e.code === "DEPTH_CHART_INCOMPLETE" && e.slot === "K"));
  assert.equal(addToSlot(draft, "QB", draft.QB[0], 10), draft);
});

test("fora de posição: papel pela posição natural; vaga de posição pela secundária e pela margem de 5", () => {
  const kr = slots.find((s) => s.slot === "KR")!;
  const qbSlot = slots.find((s) => s.slot === "QB")!;
  const base: DepthPlayerInfo = { id: "x", name: "X", jerseyNumber: 1, position: "DT", secondaryPosition: null, overall: 80, tier: "GOLD", rosterStatus: "ACTIVE", suspensionWeeks: 0 };
  assert.equal(isOutOfPosition(base, kr, known), true, "DT não faz o papel de retornador");
  assert.equal(isOutOfPosition({ ...base, position: "CB" }, kr, known), false);
  assert.equal(isOutOfPosition({ ...base, position: "RB", secondaryPosition: "QB" }, qbSlot, known), false, "secundária nunca é fora de posição");
  assert.equal(isOutOfPosition({ ...base, position: "RB", byPosition: { QB: 76 } }, qbSlot, known), false, "até 5 abaixo do natural não avisa");
  assert.equal(isOutOfPosition({ ...base, position: "RB", byPosition: { QB: 74 } }, qbSlot, known), true);
  const many = { ...draftFrom(dc), FB: practice.map((p) => p.id) };
  const check = validateDraft(slots, many, players, known, dc.gameDayActives);
  assert.ok(check.errors.some((e) => e.code === "DEPTH_CHART_PLAYER_UNAVAILABLE" && e.slot === "FB"), "practice squad não entra no depth chart");
});
