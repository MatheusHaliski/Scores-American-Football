/**
 * Elenco da franquia no modo mock: 53 (composição do mundo padrão: 25 de ataque, 25 de defesa, K, P e LS), practice squad de 16
 * e um jogador na IR. Subatributos gerados por semente e overall/macros/nível calculados pela mesma lib de ratings que o backend
 * (OVR v1), então os números do card batem com os da página do jogador. Contratos ajustados para ~95% do teto, como o
 * `DefaultWorldFactory`. Nada aqui sai do navegador.
 */
import { ATTRIBUTE_GROUPS, OVERALL_WEIGHTS_V1, overall, type AttributeGroup, type GroupedAttributes, type Position } from "@/lib/ratings/overall";
import type { PlayerContract, RosterStatus, Unit } from "../career-types";
import { hashString } from "./rng";

/** Gerador determinístico (mulberry32). */
export function rngFrom(seed: string): () => number {
  let a = hashString(seed) || 1;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const int = (rng: () => number, min: number, max: number) => min + Math.floor(rng() * (max - min + 1));
const pick = <T>(rng: () => number, xs: readonly T[]): T => xs[Math.floor(rng() * xs.length)];
const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, Math.round(v)));

export const UNIT_OF: Record<Position, Unit> = {
  QB: "OFFENSE", RB: "OFFENSE", FB: "OFFENSE", WR: "OFFENSE", TE: "OFFENSE", LT: "OFFENSE", LG: "OFFENSE", C: "OFFENSE", RG: "OFFENSE", RT: "OFFENSE",
  DE: "DEFENSE", DT: "DEFENSE", OLB: "DEFENSE", MLB: "DEFENSE", CB: "DEFENSE", FS: "DEFENSE", SS: "DEFENSE", K: "SPECIAL_TEAMS", P: "SPECIAL_TEAMS", LS: "SPECIAL_TEAMS",
};

/** Os 53 do mundo padrão (mesma composição do roster.json). */
const ACTIVE_53: Position[] = [
  "QB", "QB", "QB", "RB", "RB", "RB", "FB", "WR", "WR", "WR", "WR", "WR", "WR", "TE", "TE", "TE", "LT", "LT", "LG", "LG", "C", "C", "RG", "RT", "RT",
  "DE", "DE", "DE", "DE", "DE", "DT", "DT", "DT", "DT", "OLB", "OLB", "OLB", "OLB", "MLB", "MLB", "CB", "CB", "CB", "CB", "CB", "CB", "FS", "FS", "SS", "SS",
  "K", "P", "LS",
];
const PRACTICE_SQUAD: Position[] = ["QB", "RB", "WR", "WR", "TE", "LT", "LG", "C", "DE", "DE", "DT", "OLB", "MLB", "CB", "CB", "SS"];
/** Titulares do pacote base por posição (os demais da posição vêm atrás). */
const STARTERS: Partial<Record<Position, number>> = { WR: 3, DE: 2, DT: 2, OLB: 2, CB: 2 };

const SECONDARY: Partial<Record<Position, Position[]>> = {
  RB: ["FB", "WR"], FB: ["RB", "TE"], WR: ["RB", "TE"], TE: ["FB", "WR"], LT: ["RT", "LG"], RT: ["LT", "RG"], LG: ["C", "RG"], RG: ["C", "LG"], C: ["LG", "RG"],
  DE: ["OLB", "DT"], DT: ["DE"], OLB: ["MLB", "DE"], MLB: ["OLB"], CB: ["FS", "SS"], FS: ["SS", "CB"], SS: ["FS", "OLB"], K: ["P"], P: ["K"],
};

/** Altura (cm) e peso (kg) por posição. */
const BODY: Record<Position, [number, number, number, number]> = {
  QB: [183, 198, 95, 112], RB: [170, 185, 88, 102], FB: [178, 190, 105, 118], WR: [175, 196, 82, 100], TE: [188, 200, 110, 122],
  LT: [193, 203, 140, 155], LG: [188, 198, 138, 152], C: [185, 196, 135, 148], RG: [188, 198, 138, 152], RT: [193, 203, 140, 155],
  DE: [188, 198, 115, 130], DT: [185, 196, 135, 152], OLB: [185, 196, 105, 117], MLB: [183, 193, 107, 118], CB: [175, 188, 82, 93],
  FS: [178, 190, 88, 98], SS: [178, 190, 90, 100], K: [173, 188, 78, 95], P: [180, 193, 85, 100], LS: [183, 193, 105, 115],
};

const JERSEYS: Record<Position, [number, number][]> = {
  QB: [[1, 19]], RB: [[20, 49]], FB: [[30, 49]], WR: [[10, 19], [80, 89]], TE: [[80, 89], [40, 49]], LT: [[60, 79]], LG: [[60, 79]], C: [[50, 79]], RG: [[60, 79]],
  RT: [[60, 79]], DE: [[90, 99], [50, 59]], DT: [[90, 99], [70, 79]], OLB: [[40, 59]], MLB: [[40, 59]], CB: [[20, 39]], FS: [[20, 49]], SS: [[20, 49]],
  K: [[1, 19]], P: [[1, 19]], LS: [[40, 59]],
};

/** Teto de salário de referência por posição (S$ inteiros), como o `DefaultWorldFactory`. */
const TOP_SALARY: Record<Position, number> = {
  QB: 45_000_000, RB: 12_000_000, FB: 3_500_000, WR: 25_000_000, TE: 14_000_000, LT: 20_000_000, LG: 20_000_000, C: 20_000_000, RG: 20_000_000, RT: 20_000_000,
  DE: 26_000_000, DT: 26_000_000, OLB: 18_000_000, MLB: 18_000_000, CB: 20_000_000, FS: 15_000_000, SS: 15_000_000, K: 5_000_000, P: 3_500_000, LS: 1_500_000,
};
const MIN_SALARY = 900_000;
const PRACTICE_SALARY = 216_000;
const BONUS_PRORATION_MAX_YEARS = 5;

const FIRST = ["Lucas", "Gabriel", "Rafael", "Mateus", "Bruno", "Thiago", "Felipe", "Diego", "Caio", "Vinícius", "Rodrigo", "Guilherme", "André", "Pedro", "Henrique", "Igor", "Renan", "Danilo", "Eduardo", "Otávio", "Jordan", "Tyler", "Marcus", "Malik", "Devin", "Cameron", "Trey", "Isaiah", "Brandon", "Kyle", "Darius", "Jamal", "Elijah", "Corey", "Austin", "Logan", "Mason", "Nolan", "Garrett", "Dante", "Mateo", "Santiago", "Joaquín", "Tomás", "Emiliano", "Gonzalo", "Iván", "Andrés", "Noah", "Hugo", "Théo", "Kofi", "Emeka", "Tunde", "Ravi", "Kenji", "Aleksander", "Bjorn", "Luca", "Enzo"];
const SURNAME_HEAD = ["Albu", "Bran", "Cor", "Dal", "Esper", "Fal", "Gar", "Hal", "Ivar", "Jor", "Kel", "Lor", "Mar", "Nor", "Orl", "Pen", "Quel", "Ros", "Sal", "Tor", "Ulm", "Var", "Wes", "Zel", "Bel", "Cas", "Dor", "Fen", "Gil", "Mon"];
const SURNAME_TAIL = ["dell", "vane", "more", "sen", "ley", "ard", "wick", "ton", "ford", "mont", "quist", "berg", "ane", "ino", "eiro", "ales", "ado", "ette", "ore", "sby", "aro", "elli", "anski", "ovic"];
const COLLEGE_KIND = ["Universidade Estadual de", "Universidade Federal de", "Instituto Politécnico de", "Universidade Técnica de", "Colégio Superior de"];
const COLLEGE_PLACE = ["Vale Brumoso", "Serra Lavanda", "Campo Ventoso", "Rio Cristalino do Sul", "Lago Âmbar", "Pedra Sonora", "Bela Aurora do Norte", "Cruzeiro Alto", "Monte Prateado", "Porto Celeste", "Santa Neblina", "Ribeirão Dourado"];
const NATIONALITY = ["BRA", "BRA", "BRA", "BRA", "BRA", "BRA", "BRA", "USA", "USA", "USA", "USA", "USA", "MEX", "MEX", "CAN", "CAN", "ARG", "COL", "GER", "NGA"];

/** Jogador da franquia guardado pelo mock (o card e a página saem daqui). */
export interface MockPlayer {
  id: string;
  name: string;
  jerseyNumber: number;
  position: Position;
  secondaryPosition: Position | null;
  age: number;
  birthDate: string;
  heightCm: number;
  weightKg: number;
  nationality: string;
  college: string;
  attributes: GroupedAttributes;
  potential: number;
  rosterStatus: RosterStatus;
  condition: number;
  morale: number;
  experienceYears: number;
  injuryWeeksOut: number;
  suspensionWeeks: number;
  contract: PlayerContract;
  /** franquia (nula depois da dispensa) */
  onClub: boolean;
}

function attributes(rng: () => number, pos: Position, target: number): GroupedAttributes {
  const weights = OVERALL_WEIGHTS_V1[pos] as Record<string, number>;
  const out = {} as Record<AttributeGroup, Record<string, number>>;
  for (const g of Object.keys(ATTRIBUTE_GROUPS) as AttributeGroup[]) {
    out[g] = {};
    for (const k of ATTRIBUTE_GROUPS[g]) out[g][k] = k in weights ? clamp(target + int(rng, -6, 6), 25, 99) : clamp(40 + rng() * 24, 25, 80);
  }
  const attrs = out as unknown as GroupedAttributes;
  for (let i = 0; i < 5; i++) {
    const diff = target - overall(attrs, pos);
    if (diff === 0) break;
    for (const g of Object.keys(ATTRIBUTE_GROUPS) as AttributeGroup[]) for (const k of ATTRIBUTE_GROUPS[g]) if (k in weights) out[g][k] = clamp(out[g][k] + diff, 25, 99);
  }
  return attrs;
}

function proratedBonus(c: PlayerContract): number {
  return Math.round(c.signingBonus / Math.min(c.years, BONUS_PRORATION_MAX_YEARS));
}

/** Monta o contrato (valores por temporada) a partir do salário-base do primeiro ano e do bônus de assinatura, em centavos. */
export function buildContract(startSeason: number, years: number, firstBase: number, signingBonus: number, type: string): PlayerContract {
  const base = Array.from({ length: years }, (_, i) => Math.round(firstBase * 1.05 ** i));
  const c: PlayerContract = { startSeason, endSeason: startSeason + years - 1, years, baseSalaryByYear: base, signingBonus, guaranteed: 0, capHitByYear: [], deadMoneyIfCutByYear: [], type, franchiseTag: false, totalValue: 0 };
  const pro = proratedBonus(c);
  const proYears = Math.min(years, BONUS_PRORATION_MAX_YEARS);
  c.capHitByYear = base.map((b, i) => b + (i < proYears ? pro : 0));
  c.deadMoneyIfCutByYear = base.map((_, i) => Math.max(0, proYears - i) * pro);
  c.totalValue = base.reduce((a, b) => a + b, 0) + signingBonus;
  return c;
}

export function yearIndex(c: PlayerContract, season: number): number {
  return Math.max(0, Math.min(c.years - 1, season - c.startSeason));
}
export function capHitOf(p: MockPlayer, season: number): number {
  return season > p.contract.endSeason ? 0 : (p.contract.capHitByYear[yearIndex(p.contract, season)] ?? 0);
}
export function deadMoneyOf(p: MockPlayer, season: number): number {
  return season > p.contract.endSeason ? 0 : (p.contract.deadMoneyIfCutByYear[yearIndex(p.contract, season)] ?? 0);
}

/**
 * Gera o elenco de uma franquia pela semente da carreira. `prestige` (56–84) puxa o nível dos titulares; `season` é o ano da
 * temporada; `capCents` é o teto da liga.
 */
export function generateRoster(seed: string, ids: (i: number) => string, prestige: number, season: number, capCents: number): MockPlayer[] {
  const rng = rngFrom(`roster:${seed}`);
  const names = new Set<string>();
  const jerseys = new Set<number>();
  const base = 70 + (prestige - 56) * 0.3;

  const jersey = (pos: Position) => {
    for (let tries = 0; tries < 200; tries++) {
      const [a, b] = pick(rng, JERSEYS[pos]);
      const n = int(rng, a, b);
      if (!jerseys.has(n)) {
        jerseys.add(n);
        return n;
      }
    }
    for (let n = 0; n < 100; n++) {
      if (!jerseys.has(n)) {
        jerseys.add(n);
        return n;
      }
    }
    return 0;
  };
  const name = () => {
    for (;;) {
      const n = `${pick(rng, FIRST)} ${pick(rng, SURNAME_HEAD)}${pick(rng, SURNAME_TAIL)}`;
      if (!names.has(n)) {
        names.add(n);
        return n;
      }
    }
  };

  const make = (i: number, pos: Position, target: number, status: RosterStatus): MockPlayer => {
    const age = status === "PRACTICE_SQUAD" ? int(rng, 21, 25) : int(rng, 21, 34);
    const [hMin, hMax, wMin, wMax] = BODY[pos];
    const heightCm = int(rng, hMin, hMax);
    const share = (heightCm - hMin) / Math.max(1, hMax - hMin);
    const attrs = attributes(rng, pos, target);
    const ov = overall(attrs, pos);
    const experienceYears = Math.max(0, age - 22 + int(rng, -1, 1));
    const secondaryList = SECONDARY[pos];
    let contract: PlayerContract;
    if (status === "PRACTICE_SQUAD") contract = buildContract(season, 1, PRACTICE_SALARY * 100, 0, "PRACTICE");
    else {
      const years = int(rng, 1, 5);
      const start = season - int(rng, 0, years - 1);
      const value = Math.max(MIN_SALARY, TOP_SALARY[pos] * Math.max(0, (ov - 50) / 45) ** 2) * 100;
      const bonus = Math.round(value * years * (experienceYears <= 3 ? 0.2 : 0.25));
      contract = buildContract(start, years, Math.round(value * 0.8), bonus, experienceYears <= 3 ? "ROOKIE" : "VETERAN");
    }
    return {
      id: ids(i),
      name: name(),
      jerseyNumber: jersey(pos),
      position: pos,
      secondaryPosition: secondaryList && rng() < 0.3 ? pick(rng, secondaryList) : null,
      age,
      birthDate: `${season - age}-${String(int(rng, 1, 12)).padStart(2, "0")}-${String(int(rng, 1, 28)).padStart(2, "0")}`,
      heightCm,
      weightKg: clamp(wMin + share * (wMax - wMin) + int(rng, -3, 3), wMin, wMax),
      nationality: pick(rng, NATIONALITY),
      college: `${pick(rng, COLLEGE_KIND)} ${pick(rng, COLLEGE_PLACE)}`,
      attributes: attrs,
      potential: Math.min(99, ov + (age < 25 ? int(rng, 4, 16) : int(rng, 0, 4))),
      rosterStatus: status,
      condition: int(rng, 86, 100),
      morale: int(rng, 48, 80),
      experienceYears,
      injuryWeeksOut: 0,
      suspensionWeeks: 0,
      contract,
      onClub: true,
    };
  };

  const out: MockPlayer[] = [];
  const depthIndex = new Map<Position, number>();
  for (const pos of ACTIVE_53) {
    const d = depthIndex.get(pos) ?? 0;
    depthIndex.set(pos, d + 1);
    const starters = STARTERS[pos] ?? 1;
    const star = d === 0 && rng() < 0.25 ? int(rng, 4, 9) : 0;
    const target = d < starters ? base + star + int(rng, -5, 6) : base - 8 - (d - starters) * 4 + int(rng, -4, 3);
    out.push(make(out.length, pos, clamp(target, 50, 95), "ACTIVE"));
  }
  for (const pos of PRACTICE_SQUAD) out.push(make(out.length, pos, clamp(base - 15 + int(rng, -4, 4), 45, 72), "PRACTICE_SQUAD"));
  // um linebacker na IR (lesão de 6 semanas) e um tight end reserva tocado (2 semanas, ainda nos 53)
  const ir = make(out.length, "OLB", clamp(base - 4, 55, 90), "IR");
  ir.injuryWeeksOut = 6;
  out.push(ir);
  const te = out.filter((p) => p.position === "TE")[1];
  if (te) te.injuryWeeksOut = 2;

  // contratos dos 53 + IR ajustados para ~95% do teto (o practice squad fica com o salário fixo)
  const target = capCents * 0.95 - out.filter((p) => p.rosterStatus === "PRACTICE_SQUAD").reduce((a, p) => a + capHitOf(p, season), 0);
  const signed = out.filter((p) => p.rosterStatus !== "PRACTICE_SQUAD");
  const total = signed.reduce((a, p) => a + capHitOf(p, season), 0);
  const k = total > 0 ? target / total : 1;
  for (const p of signed) {
    const c = p.contract;
    const start = c.startSeason;
    const firstBase = Math.round((c.baseSalaryByYear[0] * k) / 100) * 100;
    const bonus = Math.round((c.signingBonus * k) / 100) * 100;
    p.contract = buildContract(start, c.years, Math.max(MIN_SALARY * 100, firstBase), bonus, c.type);
  }
  return out;
}
