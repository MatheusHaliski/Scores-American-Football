// Campo 3D da partida (D-140..): conversão do referencial do ataque para o mundo (com o espelhamento quando o visitante
// ataca), as faixas de scrimmage e de primeira descida, as câmeras por modo e a escolha entre 3D e 2D com a preferência do
// usuário. Lógica pura, sem React.
import { test } from "node:test";
import assert from "node:assert/strict";
import { FIELD_M, YARD_M, yardToX } from "@/lib/field/geometry";
import { absYdAt, fieldLines, offenseToWorld, worldToOffense, xToAbsYd } from "@/lib/field/world";
import { CAMERA_MODES, cameraFor, cameraStep, type CameraFocus } from "@/lib/match/scene/camera";
import { chooseView, readViewPref, VIEW_PREF_KEY, writeViewPref } from "@/lib/match/scene/view";

const close = (a: number, b: number, eps = 1e-6, msg?: string) => assert.ok(Math.abs(a - b) < eps, msg ?? `${a} ≠ ${b}`);

test("referencial do ataque → mundo: a casa ataca para +x com a direita em +z; o visitante tem o campo espelhado", () => {
  // casa na própria 25, ataca para a jarda 100
  const [x0, z0] = offenseToWorld(25, 1, 0, 0);
  close(x0, yardToX(25));
  close(z0, 0);
  const [x1, z1] = offenseToWorld(25, 1, 10, 5);
  close(x1, yardToX(35), 1e-6, "10 jardas à frente = jarda 35");
  close(z1, 5 * YARD_M, 1e-6, "direita do ataque da casa = +z");
  // visitante na jarda absoluta 75 (a própria 25), ataca para a jarda 0
  const [x2, z2] = offenseToWorld(75, -1, 10, 5);
  close(x2, yardToX(65), 1e-6, "10 jardas à frente do visitante = jarda 65");
  close(z2, -5 * YARD_M, 1e-6, "direita do ataque do visitante = −z (espelhado)");
  close(absYdAt(75, -1, 10), 65);
  // ida e volta
  for (const dir of [1, -1] as const) {
    const [x, z] = offenseToWorld(40, dir, -7.5, 12);
    const [d, w] = worldToOffense(40, dir, x, z);
    close(d, -7.5);
    close(w, 12);
  }
  close(xToAbsYd(yardToX(37.5)), 37.5);
  // a mesma jogada nos dois sentidos fica simétrica pelo meio-campo
  const [xh] = offenseToWorld(30, 1, 15, 0);
  const [xa] = offenseToWorld(70, -1, 15, 0);
  close(xh, -xa);
});

test("faixas: scrimmage azul na bola e primeira descida amarela à frente, nos dois sentidos; sem amarela no 'and goal' e sem posse", () => {
  const home = fieldLines(25, 10, true)!;
  close(home.scrimmageX, yardToX(25));
  close(home.firstDownX!, yardToX(35));
  assert.equal(home.direction, 1);
  const away = fieldLines(75, 10, false)!;
  close(away.scrimmageX, yardToX(75));
  close(away.firstDownX!, yardToX(65), 1e-6, "o visitante ganha jardas rumo à 0");
  assert.equal(away.direction, -1);
  const goal = fieldLines(95, 5, true)!;
  close(goal.scrimmageX, yardToX(95));
  assert.equal(goal.firstDownX, null, "and goal: só a azul");
  assert.equal(fieldLines(8, 8, false)!.firstDownX, null);
  assert.equal(fieldLines(50, 10, null), null);
});

const focus = (over: Partial<CameraFocus> = {}): CameraFocus => ({ losX: yardToX(30), ball: [yardToX(30), 0.1, 0], dir: 1, aspect: 16 / 9, ...over });

test("câmeras: TV lateral elevada do lado +z seguindo a linha; Topo em cima da linha; End zone atrás do ataque; do lance segue a bola", () => {
  const tv = cameraFor("TV", focus());
  assert.ok(tv.position[2] > 20, "do lado +z (casa à esquerda da tela)");
  assert.ok(tv.position[1] > 10, "elevada");
  assert.ok(Math.abs(tv.target[0] - yardToX(30)) < 7, "mira perto da linha de scrimmage");
  const tv2 = cameraFor("TV", focus({ losX: yardToX(60) }));
  close(tv2.target[0] - tv.target[0], yardToX(60) - yardToX(30), 1e-6, "segue a linha");
  const top = cameraFor("TOP", focus());
  close(top.position[0], top.target[0]);
  assert.ok(top.position[1] > 50, "alto o bastante para a largura inteira");
  assert.ok(Math.abs(top.position[2]) < 0.1, "em cima do campo");
  const halfV = Math.tan((top.fov * Math.PI) / 180 / 2) * top.position[1];
  assert.ok(halfV >= FIELD_M.width / 2, "a largura do campo cabe na vertical");
  for (const dir of [1, -1] as const) {
    const ez = cameraFor("END_ZONE", focus({ dir }));
    assert.ok((ez.target[0] - ez.position[0]) * dir > 0, "atrás do ataque, olhando no sentido do ataque");
  }
  const ball: [number, number, number] = [yardToX(48), 1, 4];
  const play = cameraFor("PLAY", focus({ ball }));
  close(play.target[0], ball[0]);
  close(play.target[2], ball[2]);
  assert.equal(CAMERA_MODES.length, 4);
});

test("câmeras: canvas estreito (390 px) recua a TV; o foco não sai do gramado; movimento reduzido = sem animação", () => {
  const wide = cameraFor("TV", focus());
  const narrow = cameraFor("TV", focus({ aspect: 4 / 3 }));
  const dist = (s: typeof wide) => Math.hypot(s.position[0] - s.target[0], s.position[1] - s.target[1], s.position[2] - s.target[2]);
  assert.ok(dist(narrow) > dist(wide) * 0.9, "no estreito a câmera não chega mais perto");
  const far = cameraFor("TV", focus({ losX: yardToX(108) }));
  assert.ok(far.target[0] <= FIELD_M.length / 2, "não mira o vazio atrás da end zone");
  assert.equal(cameraStep(0.016, true), 1);
  const s = cameraStep(0.016, false);
  assert.ok(s > 0 && s < 0.2);
});

test("3D ou 2D: sem WebGL é 2D; falha da cena é 2D; a preferência do usuário vale; antes da detecção, nada", () => {
  assert.equal(chooseView({ webgl: null, pref: null, fallback: null }), null);
  assert.equal(chooseView({ webgl: false, pref: "3d", fallback: null }), "2d", "sem WebGL vira 2D mesmo preferindo 3D");
  assert.equal(chooseView({ webgl: true, pref: null, fallback: null }), "3d", "o padrão é 3D");
  assert.equal(chooseView({ webgl: true, pref: "2d", fallback: null }), "2d");
  assert.equal(chooseView({ webgl: true, pref: "3d", fallback: "contextLost" }), "2d");
  assert.equal(chooseView({ webgl: true, pref: "3d", fallback: "error" }), "2d");
});

test("preferência 2D/3D no localStorage: lê e grava; valor estranho, armazenamento ausente ou que lança exceção não quebram", () => {
  const mem = new Map<string, string>();
  const ok = { getItem: (k: string) => mem.get(k) ?? null, setItem: (k: string, v: string) => void mem.set(k, v) };
  assert.equal(readViewPref(ok), null);
  assert.equal(writeViewPref(ok, "2d"), true);
  assert.equal(mem.get(VIEW_PREF_KEY), "2d");
  assert.equal(readViewPref(ok), "2d");
  mem.set(VIEW_PREF_KEY, "4d");
  assert.equal(readViewPref(ok), null);
  const broken = {
    getItem: () => {
      throw new Error("SecurityError");
    },
    setItem: () => {
      throw new Error("QuotaExceeded");
    },
  };
  assert.equal(readViewPref(broken), null);
  assert.equal(writeViewPref(broken, "3d"), false);
  assert.equal(readViewPref(null), null);
  assert.equal(writeViewPref(null, "3d"), false);
});
