/**
 * Overall por posição — port fiel do Java (`OverallCalculator` + `OverallWeights`, "OVR v1", docs/01 §3.4). Os pesos são inteiros
 * em milésimos e somam 1000 em cada posição; a conta é inteira, `(Σ peso × valor + 500) / 1000`, então Java e TypeScript dão o
 * mesmo número sem diferença de ponto flutuante. O vetor de ouro `docs/validation/golden/overall-vectors.json`, gravado pelo Java,
 * prende os dois lados (`tests/ratings/overall-golden.test.mts`). Mudar um peso é publicar uma versão nova nos dois lados.
 */

export const OVR_VERSION = "OVR v1";
export const WEIGHT_SCALE = 1000;

/** Grupos e subatributos na ordem canônica (a mesma de `AttributeKey` no Java). */
export const ATTRIBUTE_GROUPS = {
  physical: ["speed", "acceleration", "agility", "strength", "jumping", "stamina", "durability"],
  passing: ["shortAccuracy", "mediumAccuracy", "deepAccuracy", "throwPower", "throwOnRun", "playAction", "readDefense", "poise"],
  rushing: ["vision", "breakTackle", "juke", "spin", "stiffArm", "ballSecurity", "carrying"],
  receiving: ["hands", "catchInTraffic", "spectacularCatch", "shortRoute", "mediumRoute", "deepRoute", "release"],
  blocking: ["passBlock", "runBlock", "blockTechnique", "blockFinesse", "blockPower"],
  defense: ["tackle", "hitPower", "pursuit", "playRecognition", "manCoverage", "zoneCoverage", "press", "finesseMoves", "powerMoves", "blockShedding"],
  kicking: ["kickPower", "kickAccuracy", "kickReturn"],
  mental: ["awareness", "leadership"],
} as const;

export type AttributeGroup = keyof typeof ATTRIBUTE_GROUPS;
export type AttributeKey = (typeof ATTRIBUTE_GROUPS)[AttributeGroup][number];
/** Subatributos agrupados, no formato do DTO do editor e do card (`PlayerAttributes.asGroupedMap()` no Java). */
export type GroupedAttributes = { [G in AttributeGroup]: { [K in (typeof ATTRIBUTE_GROUPS)[G][number]]: number } };

export const ATTRIBUTE_ORDER: readonly AttributeKey[] = (Object.keys(ATTRIBUTE_GROUPS) as AttributeGroup[]).flatMap(
  (g) => ATTRIBUTE_GROUPS[g] as readonly AttributeKey[],
);

export const POSITIONS = ["QB", "RB", "FB", "WR", "TE", "LT", "LG", "C", "RG", "RT", "DE", "DT", "OLB", "MLB", "CB", "FS", "SS", "K", "P", "LS"] as const;
export type Position = (typeof POSITIONS)[number];

export type Weights = Partial<Record<AttributeKey, number>>;

/** Pesos oficiais OVR v1, em milésimos (cópia de `OverallWeights.v1()`). */
export const OVERALL_WEIGHTS_V1: Record<Position, Weights> = {
  QB: { shortAccuracy: 150, mediumAccuracy: 150, deepAccuracy: 120, throwPower: 120, throwOnRun: 60, playAction: 50, readDefense: 130, poise: 90, awareness: 60, leadership: 30, speed: 20, agility: 20 },
  RB: { speed: 120, acceleration: 110, agility: 100, vision: 120, breakTackle: 100, juke: 80, spin: 50, stiffArm: 50, ballSecurity: 80, carrying: 60, hands: 50, shortRoute: 30, passBlock: 20, awareness: 30 },
  FB: { runBlock: 200, passBlock: 100, blockPower: 120, strength: 110, carrying: 100, ballSecurity: 70, breakTackle: 60, hands: 70, shortRoute: 40, vision: 40, awareness: 50, speed: 40 },
  WR: { speed: 140, acceleration: 90, agility: 70, hands: 140, catchInTraffic: 70, spectacularCatch: 50, shortRoute: 90, mediumRoute: 100, deepRoute: 90, release: 80, jumping: 30, awareness: 50 },
  TE: { hands: 130, catchInTraffic: 100, shortRoute: 80, mediumRoute: 70, release: 40, runBlock: 140, passBlock: 100, blockPower: 50, strength: 90, speed: 60, breakTackle: 40, awareness: 70, spectacularCatch: 30 },
  LT: { passBlock: 260, runBlock: 140, blockTechnique: 130, blockFinesse: 120, blockPower: 60, strength: 110, agility: 70, awareness: 80, durability: 30 },
  LG: { passBlock: 160, runBlock: 250, blockTechnique: 110, blockFinesse: 60, blockPower: 150, strength: 140, agility: 40, awareness: 70, durability: 20 },
  C: { passBlock: 180, runBlock: 200, blockTechnique: 160, blockFinesse: 60, blockPower: 100, strength: 110, awareness: 150, leadership: 20, durability: 20 },
  RG: { passBlock: 170, runBlock: 240, blockTechnique: 110, blockFinesse: 60, blockPower: 150, strength: 140, agility: 40, awareness: 70, durability: 20 },
  RT: { passBlock: 220, runBlock: 180, blockTechnique: 120, blockFinesse: 90, blockPower: 100, strength: 120, agility: 60, awareness: 80, durability: 30 },
  DE: { finesseMoves: 150, powerMoves: 140, blockShedding: 140, speed: 90, acceleration: 90, strength: 100, tackle: 90, pursuit: 70, playRecognition: 70, hitPower: 60 },
  DT: { powerMoves: 170, blockShedding: 190, strength: 170, finesseMoves: 80, tackle: 110, playRecognition: 80, hitPower: 70, acceleration: 60, pursuit: 40, stamina: 30 },
  OLB: { tackle: 130, pursuit: 120, speed: 100, acceleration: 70, playRecognition: 110, blockShedding: 90, finesseMoves: 70, powerMoves: 50, zoneCoverage: 80, manCoverage: 50, hitPower: 80, awareness: 50 },
  MLB: { tackle: 160, playRecognition: 160, pursuit: 120, hitPower: 90, blockShedding: 90, zoneCoverage: 90, manCoverage: 40, speed: 70, strength: 50, awareness: 80, leadership: 50 },
  CB: { manCoverage: 180, zoneCoverage: 140, press: 110, speed: 150, acceleration: 80, agility: 80, playRecognition: 80, hands: 40, jumping: 40, tackle: 50, awareness: 50 },
  FS: { zoneCoverage: 190, manCoverage: 90, speed: 130, playRecognition: 140, pursuit: 90, tackle: 80, hands: 50, acceleration: 60, hitPower: 40, awareness: 90, jumping: 40 },
  SS: { tackle: 140, hitPower: 110, zoneCoverage: 140, manCoverage: 90, playRecognition: 120, pursuit: 100, speed: 100, strength: 50, blockShedding: 50, awareness: 70, acceleration: 30 },
  K: { kickPower: 420, kickAccuracy: 450, awareness: 70, poise: 60 },
  P: { kickPower: 450, kickAccuracy: 400, awareness: 80, poise: 70 },
  LS: { blockTechnique: 300, awareness: 250, passBlock: 150, strength: 120, tackle: 100, speed: 40, durability: 40 },
};

const clamp = (v: number) => Math.max(0, Math.min(100, v));

/** Valor do subatributo nos atributos agrupados (0 se faltar). */
export function attributeValue(attributes: GroupedAttributes, key: AttributeKey): number {
  for (const g of Object.keys(ATTRIBUTE_GROUPS) as AttributeGroup[]) {
    const group = attributes[g] as Record<string, number> | undefined;
    if (group && key in group) return group[key];
  }
  return 0;
}

/** Média ponderada inteira com pesos em milésimos (soma 1000), arredondada para cima a partir de ,5 — igual ao Java. */
export function weighted(attributes: GroupedAttributes, weights: Weights): number {
  let sum = 0;
  for (const key of ATTRIBUTE_ORDER) {
    const w = weights[key];
    if (w) sum += w * clamp(attributeValue(attributes, key));
  }
  return Math.floor((sum + WEIGHT_SCALE / 2) / WEIGHT_SCALE);
}

/** Overall do jogador na posição (OVR v1). */
export function overall(attributes: GroupedAttributes, position: Position): number {
  return weighted(attributes, OVERALL_WEIGHTS_V1[position]);
}

/** Overall em cada uma das 20 posições, na ordem de `POSITIONS`. */
export function overallByPosition(attributes: GroupedAttributes): Record<Position, number> {
  return Object.fromEntries(POSITIONS.map((p) => [p, overall(attributes, p)])) as Record<Position, number>;
}

/** Fora de posição: a posição em que joga não é a natural nem a secundária. */
export function isOutOfPosition(natural: Position, playing: Position, secondary?: Position | null): boolean {
  return playing !== natural && playing !== secondary;
}
