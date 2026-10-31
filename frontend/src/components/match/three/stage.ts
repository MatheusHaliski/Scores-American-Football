/**
 * O que o campo 3D está mostrando agora: um roteiro (`PlayScript`) e o relógio dele. Fica num ref lido a cada quadro pelos
 * atletas, pela bola e pela câmera, então a animação não passa pelo React.
 */
import type { RefObject } from "react";
import type { FieldLines } from "@/lib/field/world";
import type { PlayScript } from "@/lib/match/scene/script";

/** play = lance encenado; reset = atletas voltando para a formação; presnap = parados antes do próximo snap; final = quadro final (movimento reduzido ou fim do lance). */
export type StageRole = "play" | "reset" | "presnap" | "final";

export interface Stage {
  script: PlayScript;
  role: StageRole;
  /** ms (performance.now) em que o roteiro começou (pular a animação adianta o começo) */
  startedAt: number;
  /** faixas de transmissão a mostrar (as do lance antes do snap; depois, as do estado) */
  lines: FieldLines | null;
  /** traço do último lance (x do início e do fim, em metros) */
  trace: { fromX: number; toX: number } | null;
  /** números nas camisas, por vaga */
  numbers: (number | null)[];
  /** segundos do roteiro agora (parado no fim quando acaba) */
  time: () => number;
}

export type StageRef = RefObject<Stage | null>;

export function makeStage(script: PlayScript, role: StageRole, lines: FieldLines | null, trace: Stage["trace"], numbers: (number | null)[]): Stage {
  const stage: Stage = {
    script,
    role,
    startedAt: performance.now(),
    lines,
    trace,
    numbers,
    time: () => (role === "presnap" || role === "final" ? script.duration : Math.min(script.duration, (performance.now() - stage.startedAt) / 1000)),
  };
  return stage;
}

/** O roteiro já chegou ao fim? */
export function stageDone(s: Stage): boolean {
  return s.time() >= s.script.duration - 1e-6;
}
