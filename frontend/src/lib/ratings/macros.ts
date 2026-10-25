/**
 * Macros do card por família de posição (D-6) — port fiel de `MacroRatings` (Java). QB: PRE BRA LEI MOB CAL LID; corredores e
 * recebedores: VEL AGI MAO ROT QBT FOR; linha ofensiva (e long snapper, D-22): BLP BLC FOR TEC AGI CON; defesa: TAC IMP COB PRS VEL
 * LEI; chutadores: POT PRE CON. Mesma conta inteira do overall.
 */
import { weighted, type GroupedAttributes, type Position, type Weights } from "./overall";

export const POSITION_FAMILIES = ["QUARTERBACK", "SKILL", "OFFENSIVE_LINE", "DEFENSE", "KICKER"] as const;
export type PositionFamily = (typeof POSITION_FAMILIES)[number];

/** Família de cada posição (cópia de `Position.family()`). */
export const FAMILY_OF: Record<Position, PositionFamily> = {
  QB: "QUARTERBACK", RB: "SKILL", FB: "SKILL", WR: "SKILL", TE: "SKILL", LT: "OFFENSIVE_LINE", LG: "OFFENSIVE_LINE", C: "OFFENSIVE_LINE", RG: "OFFENSIVE_LINE", RT: "OFFENSIVE_LINE", DE: "DEFENSE", DT: "DEFENSE", OLB: "DEFENSE", MLB: "DEFENSE", CB: "DEFENSE", FS: "DEFENSE", SS: "DEFENSE", K: "KICKER", P: "KICKER", LS: "OFFENSIVE_LINE",
};

/** Definição dos macros: família → código (na ordem do card) → pesos em milésimos. */
export const MACRO_DEFINITIONS: Record<PositionFamily, ReadonlyArray<readonly [string, Weights]>> = {
  QUARTERBACK: [
    ["PRE", { shortAccuracy: 350, mediumAccuracy: 350, deepAccuracy: 300 }],
    ["BRA", { throwPower: 800, deepAccuracy: 200 }],
    ["LEI", { readDefense: 600, awareness: 250, playAction: 150 }],
    ["MOB", { speed: 300, agility: 300, acceleration: 150, throwOnRun: 250 }],
    ["CAL", { poise: 1000 }],
    ["LID", { leadership: 700, awareness: 300 }],
  ],
  SKILL: [
    ["VEL", { speed: 600, acceleration: 400 }],
    ["AGI", { agility: 500, juke: 300, spin: 200 }],
    ["MAO", { hands: 600, catchInTraffic: 250, spectacularCatch: 150 }],
    ["ROT", { shortRoute: 300, mediumRoute: 300, deepRoute: 200, release: 200 }],
    ["QBT", { breakTackle: 500, stiffArm: 250, carrying: 250 }],
    ["FOR", { strength: 500, runBlock: 300, ballSecurity: 200 }],
  ],
  OFFENSIVE_LINE: [
    ["BLP", { passBlock: 1000 }],
    ["BLC", { runBlock: 1000 }],
    ["FOR", { strength: 600, blockPower: 400 }],
    ["TEC", { blockTechnique: 600, blockFinesse: 400 }],
    ["AGI", { agility: 500, acceleration: 500 }],
    ["CON", { awareness: 1000 }],
  ],
  DEFENSE: [
    ["TAC", { tackle: 1000 }],
    ["IMP", { hitPower: 700, strength: 300 }],
    ["COB", { manCoverage: 400, zoneCoverage: 400, press: 200 }],
    ["PRS", { finesseMoves: 350, powerMoves: 350, blockShedding: 300 }],
    ["VEL", { speed: 500, acceleration: 250, pursuit: 250 }],
    ["LEI", { playRecognition: 700, awareness: 300 }],
  ],
  KICKER: [
    ["POT", { kickPower: 1000 }],
    ["PRE", { kickAccuracy: 1000 }],
    ["CON", { poise: 500, awareness: 500 }],
  ],
};

export type MacroValues = Record<string, number>;

/** Macros da família, na ordem do card. */
export function macros(attributes: GroupedAttributes, family: PositionFamily): MacroValues {
  const out: MacroValues = {};
  for (const [code, weights] of MACRO_DEFINITIONS[family]) out[code] = weighted(attributes, weights);
  return out;
}

/** Macros da família da posição. */
export function macrosForPosition(attributes: GroupedAttributes, position: Position): MacroValues {
  return macros(attributes, FAMILY_OF[position]);
}
