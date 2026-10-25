// O DTO do jogador do editor (world-editor-player.json, gravado pelo ApiGoldenTest a partir da API real) traz os subatributos, a
// avaliação na posição natural e o overall em cada posição. A lib de ratings do front precisa chegar aos mesmos números a partir dos
// subatributos: é o que a tela do editor vai fazer ao vivo quando o usuário mexer num atributo.
import { test } from "node:test";
import assert from "node:assert/strict";
import { OVR_VERSION, POSITIONS, overall, type GroupedAttributes, type Position } from "@/lib/ratings/overall";
import { FAMILY_OF, macrosForPosition } from "@/lib/ratings/macros";
import { cardTier } from "@/lib/ratings/tier";
import { loadGolden } from "../golden.mts";

interface EditorPlayer {
  position: Position;
  attributes: GroupedAttributes;
  rating: { version: string; position: Position; family: string; overall: number; tier: string; outOfPosition: boolean; macros: Record<string, number> };
  overallByPosition: Record<Position, number>;
}

const player = loadGolden("world-editor-player.json") as EditorPlayer;

test("avaliação do DTO do editor é reproduzida pela lib de ratings", () => {
  assert.equal(player.rating.version, OVR_VERSION);
  assert.equal(player.rating.family, FAMILY_OF[player.position]);
  assert.equal(overall(player.attributes, player.position), player.rating.overall);
  assert.equal(cardTier(player.rating.overall), player.rating.tier);
  assert.deepEqual(macrosForPosition(player.attributes, player.position), player.rating.macros);
  assert.deepEqual(Object.keys(player.overallByPosition), [...POSITIONS]);
  for (const p of POSITIONS) assert.equal(overall(player.attributes, p), player.overallByPosition[p], p);
});
