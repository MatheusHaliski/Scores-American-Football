// Regras do port de ratings que não dependem do vetor de ouro: pesos somam 1000, níveis nas fronteiras, macros por família.
import { test } from "node:test";
import assert from "node:assert/strict";
import { ATTRIBUTE_GROUPS, ATTRIBUTE_ORDER, OVERALL_WEIGHTS_V1, POSITIONS, WEIGHT_SCALE, overall, type GroupedAttributes } from "@/lib/ratings/overall";
import { FAMILY_OF, MACRO_DEFINITIONS, macrosForPosition } from "@/lib/ratings/macros";
import { cardTier } from "@/lib/ratings/tier";

function uniform(v: number): GroupedAttributes {
  return Object.fromEntries(
    Object.entries(ATTRIBUTE_GROUPS).map(([g, keys]) => [g, Object.fromEntries(keys.map((k) => [k, v]))]),
  ) as unknown as GroupedAttributes;
}

test("49 subatributos e pesos de cada posição somam 1000 (= 1,0)", () => {
  assert.equal(ATTRIBUTE_ORDER.length, 49);
  assert.equal(POSITIONS.length, 20);
  for (const p of POSITIONS) {
    const sum = Object.values(OVERALL_WEIGHTS_V1[p]).reduce((a, b) => a + (b ?? 0), 0);
    assert.equal(sum, WEIGHT_SCALE, p);
  }
  for (const defs of Object.values(MACRO_DEFINITIONS))
    for (const [code, w] of defs) assert.equal(Object.values(w).reduce((a, b) => a + (b ?? 0), 0), WEIGHT_SCALE, code);
});

test("atributos iguais dão o mesmo overall em qualquer posição", () => {
  for (const v of [0, 64, 65, 90, 100]) for (const p of POSITIONS) assert.equal(overall(uniform(v), p), v);
});

test("níveis do card nas fronteiras 64/65, 74/75, 84/85, 89/90", () => {
  const cases: [number, string][] = [[0, "BRONZE"], [64, "BRONZE"], [65, "SILVER"], [74, "SILVER"], [75, "GOLD"], [84, "GOLD"], [85, "ICON"], [89, "ICON"], [90, "ELITE"], [100, "ELITE"]];
  for (const [ovr, tier] of cases) assert.equal(cardTier(ovr), tier, String(ovr));
});

test("chutadores têm 3 macros, as demais famílias 6, e o long snapper usa os da linha ofensiva", () => {
  assert.deepEqual(Object.keys(macrosForPosition(uniform(70), "K")), ["POT", "PRE", "CON"]);
  assert.deepEqual(Object.keys(macrosForPosition(uniform(70), "QB")), ["PRE", "BRA", "LEI", "MOB", "CAL", "LID"]);
  assert.equal(FAMILY_OF.LS, "OFFENSIVE_LINE");
  assert.deepEqual(Object.keys(macrosForPosition(uniform(70), "LS")), ["BLP", "BLC", "FOR", "TEC", "AGI", "CON"]);
});
