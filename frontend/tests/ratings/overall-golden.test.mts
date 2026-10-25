// Vetor de ouro Java ↔ TS do overall (D-5): `docs/validation/golden/overall-vectors.json` é gravado pelo backend
// (OverallGoldenVectorsTest, a partir de OverallCalculator/MacroRatings/CardTier) e aqui o port de `src/lib/ratings` precisa
// reproduzir cada número. Se um peso mudar só de um lado, este teste (ou o do Java) fica vermelho.
import { test } from "node:test";
import assert from "node:assert/strict";
import { ATTRIBUTE_ORDER, OVR_VERSION, isOutOfPosition, overall, type GroupedAttributes, type Position } from "@/lib/ratings/overall";
import { POSITION_FAMILIES, macros, type PositionFamily } from "@/lib/ratings/macros";
import { cardTier } from "@/lib/ratings/tier";
import { loadGolden } from "../golden.mts";

interface Rating {
  position: Position;
  overall: number;
  tier: string;
  outOfPosition: boolean;
}
interface Case {
  id: string;
  naturalPosition: Position;
  secondaryPosition: Position | null;
  attributes: GroupedAttributes;
  macros: Record<PositionFamily, Record<string, number>>;
  ratings: Rating[];
}
interface Golden {
  version: string;
  attributeOrder: string[];
  cases: Case[];
}

const golden = loadGolden("overall-vectors.json") as Golden;

test("versão dos pesos e ordem dos subatributos iguais às do Java", () => {
  assert.equal(golden.version, OVR_VERSION);
  assert.deepEqual([...ATTRIBUTE_ORDER], golden.attributeOrder);
  assert.ok(golden.cases.length >= 30, `casos: ${golden.cases.length}`);
});

test("overall, nível e aviso de fora de posição batem com o Java em todas as posições", () => {
  let checked = 0;
  for (const c of golden.cases) {
    assert.equal(c.ratings.length, 20, c.id);
    for (const r of c.ratings) {
      const ovr = overall(c.attributes, r.position);
      assert.equal(ovr, r.overall, `${c.id} @ ${r.position}: overall`);
      assert.equal(cardTier(ovr), r.tier, `${c.id} @ ${r.position}: nível`);
      assert.equal(isOutOfPosition(c.naturalPosition, r.position, c.secondaryPosition), r.outOfPosition, `${c.id} @ ${r.position}: fora de posição`);
      checked++;
    }
  }
  assert.ok(checked >= 600);
});

test("macros de cada família batem com o Java, na mesma ordem", () => {
  for (const c of golden.cases) {
    for (const f of POSITION_FAMILIES) {
      const expected = c.macros[f];
      const got = macros(c.attributes, f);
      assert.deepEqual(Object.keys(got), Object.keys(expected), `${c.id}/${f}: ordem dos códigos`);
      assert.deepEqual(got, expected, `${c.id}/${f}`);
    }
  }
});
