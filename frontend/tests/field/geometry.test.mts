import { test } from "node:test";
import assert from "node:assert/strict";
import { FIELD_M, GOALPOST_M, HASH_FROM_SIDELINE_YD, YARD_M, downMarkers, fieldPosition, hashYards, hashZ, yardLines, yardToX } from "@/lib/field/geometry";

const close = (a: number, b: number, eps = 1e-6) => assert.ok(Math.abs(a - b) < eps, `${a} ≠ ${b}`);
const FOOT_M = YARD_M / 3;

test("campo oficial: 120 × 53⅓ jardas, end zones de 10", () => {
  close(FIELD_M.length, 109.728);
  close(FIELD_M.width, 48.768);
  close(FIELD_M.endZone, 9.144);
  close(yardToX(50), 0);
  close(yardToX(0), -50 * YARD_M);
  close(yardToX(110), FIELD_M.length / 2);
});

test("21 linhas de 5 em 5, números 10…50…10 e as duas linhas de gol sem número", () => {
  const lines = yardLines();
  assert.equal(lines.length, 21);
  assert.deepEqual(lines.filter((l) => l.goal).map((l) => l.absYd), [0, 100]);
  assert.deepEqual(lines.filter((l) => l.label).map((l) => l.label), [10, 20, 30, 40, 50, 40, 30, 20, 10]);
});

test("hash marks em toda jarda que não é linha de 5", () => {
  const ys = hashYards();
  assert.equal(ys.length, 80);
  assert.ok(ys.every((y) => y % 5 !== 0 && y > 0 && y < 100));
});

test("hash marks profissionais: 18 pés e 6 polegadas entre elas, a largura das traves", () => {
  close(2 * hashZ("PRO"), 18.5 * FOOT_M, 1e-9);
  close(2 * hashZ("PRO"), GOALPOST_M.width, 1e-9);
  close(HASH_FROM_SIDELINE_YD.PRO * 3, 70.75, 1e-9);
});

test("hash marks universitárias: 40 pés entre elas, 60 pés da lateral", () => {
  close(2 * hashZ("COLLEGE"), 40 * FOOT_M, 1e-9);
  close(HASH_FROM_SIDELINE_YD.COLLEGE * 3, 60, 1e-9);
});

test("linha amarela a 10 jardas da bola; perto do gol vira \"and goal\" sem linha amarela", () => {
  const m = downMarkers(25, 10);
  close(m.scrimmageX, yardToX(25));
  close(m.firstDownX, yardToX(35));
  assert.equal(m.goalToGo, false);
  const g = downMarkers(94, 10);
  assert.equal(g.goalToGo, true);
  close(g.firstDownX, yardToX(100));
});

test("bola fora do campo de jogo é presa entre as linhas de gol", () => {
  close(downMarkers(-5, 10).scrimmageX, yardToX(0));
  close(downMarkers(130, 1).scrimmageX, yardToX(100));
});

test("posição de transmissão: own, meio-campo e opp", () => {
  assert.deepEqual(fieldPosition(25), { side: "OWN", yard: 25 });
  assert.deepEqual(fieldPosition(50), { side: "MID", yard: 50 });
  assert.deepEqual(fieldPosition(60), { side: "OPP", yard: 40 });
});
