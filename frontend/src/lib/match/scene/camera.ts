/**
 * Câmeras do campo 3D da partida, sem React (testáveis em node). Quatro modos, todos com o campo da casa à esquerda da tela
 * (a câmera fica do lado +z, olhando para −z, então +x é a direita da tela, como no campo 2D):
 *  - TV: lateral elevada, a câmera de transmissão (padrão); segue a linha de scrimmage;
 *  - TOP: de cima, com a largura inteira do campo na tela; segue a linha de scrimmage;
 *  - END_ZONE: atrás do ataque, olhando o campo no sentido do ataque; segue a linha de scrimmage;
 *  - PLAY: "câmera do lance", mais baixa e mais perto, segue a bola.
 * A distância sai do campo de visão horizontal do canvas: num canvas estreito (390 px) a câmera recua para caber o mesmo
 * trecho de campo, com um trecho menor que no desktop para os atletas continuarem legíveis.
 */
import { FIELD_M, YARD_M } from "@/lib/field/geometry";
import type { AttackDir } from "@/lib/field/world";

export type CameraMode = "TV" | "TOP" | "END_ZONE" | "PLAY";
export const CAMERA_MODES: CameraMode[] = ["TV", "TOP", "END_ZONE", "PLAY"];
export const DEFAULT_CAMERA: CameraMode = "TV";

export type Vec3 = [number, number, number];

export interface CameraShot {
  position: Vec3;
  target: Vec3;
  /** campo de visão vertical, em graus */
  fov: number;
}

export interface CameraFocus {
  /** x (m) da linha de scrimmage */
  losX: number;
  /** bola no mundo (a câmera do lance segue a bola) */
  ball: Vec3;
  dir: AttackDir;
  /** largura ÷ altura do canvas */
  aspect: number;
}

const deg = Math.PI / 180;
/** Canvas "estreito" (celular): mostra menos campo para os atletas não virarem pontos. */
export const NARROW_ASPECT = 1.45;

/** Tangente da metade do campo de visão horizontal. */
function halfTanH(fov: number, aspect: number): number {
  return Math.tan((fov * deg) / 2) * Math.max(0.5, aspect);
}

/** O foco não sai do gramado: a câmera nunca mostra só o vazio atrás de uma end zone. */
function clampX(x: number, margin: number): number {
  const lim = FIELD_M.length / 2 - margin;
  return Math.max(-lim, Math.min(lim, x));
}

/** Distância para caber `spanYd` jardas de largura no plano do alvo. */
function distanceFor(spanYd: number, fov: number, aspect: number): number {
  return (spanYd * YARD_M) / 2 / halfTanH(fov, aspect);
}

export function cameraFor(mode: CameraMode, f: CameraFocus): CameraShot {
  const narrow = f.aspect < NARROW_ASPECT;
  switch (mode) {
    case "TOP": {
      const fov = 36;
      // a largura inteira do campo na vertical da tela; no horizontal, ao menos 40 jardas em volta da linha
      const fitWidth = (FIELD_M.width / 2 + 3) / Math.tan((fov * deg) / 2);
      const fitSpan = distanceFor(narrow ? 40 : 60, fov, f.aspect);
      const h = Math.max(fitWidth, fitSpan);
      const x = clampX(f.losX, 18);
      // um tantinho em +z: a câmera olha "para baixo" com −z no alto da tela (sem a ambiguidade do "up" na vertical)
      return { position: [x, h, 0.01], target: [x, 0, 0], fov };
    }
    case "END_ZONE": {
      const fov = 46;
      const x = clampX(f.losX, 6);
      const back = (narrow ? 26 : 22) * YARD_M;
      return { position: [x - f.dir * back, narrow ? 15 : 12, 0], target: [x + f.dir * 14 * YARD_M, 0, 0], fov };
    }
    case "PLAY": {
      const fov = 38;
      const dist = distanceFor(narrow ? 22 : 30, fov, f.aspect);
      const elev = 24 * deg;
      const x = clampX(f.ball[0], 4);
      const z = f.ball[2];
      return { position: [x, dist * Math.sin(elev) + 1, z + dist * Math.cos(elev)], target: [x, 0.8, z], fov };
    }
    case "TV":
    default: {
      const fov = 34;
      const dist = distanceFor(narrow ? 42 : 52, fov, f.aspect);
      const elev = 30 * deg;
      // o alvo fica um pouco à frente da linha, no sentido do ataque, onde o lance acontece
      const x = clampX(f.losX + f.dir * 6 * YARD_M, 12);
      return { position: [x, dist * Math.sin(elev), dist * Math.cos(elev)], target: [x, 0, -2], fov };
    }
  }
}

/**
 * Amortecimento da câmera (segue a linha sem tranco): fração do caminho até o alvo andada em `dt` segundos. Com movimento
 * reduzido, 1 (a câmera vai direto, sem animação).
 */
export function cameraStep(dt: number, reduceMotion: boolean, rate = 3.2): number {
  if (reduceMotion) return 1;
  return 1 - Math.exp(-rate * Math.max(0, dt));
}
