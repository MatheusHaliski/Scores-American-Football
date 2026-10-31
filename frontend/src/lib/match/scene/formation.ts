/**
 * Formações pré-snap do campo 3D, sem React (testáveis em node): as 22 vagas de cada lance, no referencial do ataque
 * (`lib/field/world`: `d` jardas a partir da linha de scrimmage no sentido do ataque, `w` jardas para a direita de quem
 * ataca). O "ataque" de uma formação é quem tem a bola no snap; nos chutes de saída (kickoff), é o time que chuta.
 *
 * O pessoal sai da formação do motor (`Formation`: I_FORM, SINGLEBACK, SHOTGUN, PISTOL, EMPTY, GOAL_LINE) e do pacote
 * defensivo (`DefensePackage`: BASE_4_3, BASE_3_4, NICKEL, DIME, GOAL_LINE); a cobertura e a blitz só mexem na
 * profundidade dos safeties e dos linebackers. Sem formação (lance antigo, mock), a escolha vai pelo conceito.
 * Ninguém começa em impedimento: o ataque fica atrás da linha (d < 0) e a defesa à frente (d > 0); no kickoff, o time que
 * chuta fica atrás da linha do chute e o que recebe a pelo menos 10 jardas dela.
 */
import { HALF_WIDTH_YD } from "@/lib/field/world";

export type Side = "O" | "D";

/** Papel na jogada: posição no depth chart (para o número na camisa) ou função nos times especiais. */
export type Role =
  | "QB" | "RB" | "FB" | "WR" | "TE" | "LT" | "LG" | "C" | "RG" | "RT"
  | "DE" | "DT" | "OLB" | "MLB" | "CB" | "FS" | "SS"
  | "K" | "P" | "LS" | "H" | "KR" | "ST";

/** Postura antes do snap: três apoios (linha), dois apoios (recebedores, linebackers), em pé, QB sob o center, ajoelhado. */
export type Stance = "three" | "two" | "up" | "qbUnder" | "kneel";

export interface Slot {
  side: Side;
  role: Role;
  d: number;
  w: number;
  stance: Stance;
}

export type FormationKind = "SCRIMMAGE" | "KICKOFF" | "ONSIDE" | "PUNT" | "FIELD_GOAL" | "KNEEL";

/** Distância mínima da linha antes do snap (meia jarda: a "zona neutra" é o comprimento da bola). */
export const NEUTRAL_YD = 0.5;
/** Distância mínima do time que recebe até a linha do chute de saída. */
export const KICK_RECEIVE_YD = 10;

const OL: Slot[] = [
  { side: "O", role: "C", d: -1, w: 0, stance: "three" },
  { side: "O", role: "LG", d: -1.1, w: -1.4, stance: "three" },
  { side: "O", role: "RG", d: -1.1, w: 1.4, stance: "three" },
  { side: "O", role: "LT", d: -1.3, w: -2.8, stance: "three" },
  { side: "O", role: "RT", d: -1.3, w: 2.8, stance: "three" },
];

const o = (role: Role, d: number, w: number, stance: Stance): Slot => ({ side: "O", role, d, w, stance });
const df = (role: Role, d: number, w: number, stance: Stance): Slot => ({ side: "D", role, d, w, stance });

/** Os 6 do ataque fora da linha ofensiva (QB, corredores, recebedores), por formação. */
const OFFENSE_SKILL: Record<string, Slot[]> = {
  I_FORM: [o("QB", -1.9, 0, "qbUnder"), o("FB", -4.5, 0, "three"), o("RB", -7, 0, "two"), o("TE", -1.3, 4.2, "three"), o("WR", -1, -20, "two"), o("WR", -1.6, 17, "two")],
  SINGLEBACK: [o("QB", -1.9, 0, "qbUnder"), o("RB", -6.5, 0, "two"), o("TE", -1.3, 4.2, "three"), o("WR", -1, -20, "two"), o("WR", -1.6, -9, "two"), o("WR", -1.6, 17, "two")],
  SHOTGUN: [o("QB", -5, 0, "up"), o("RB", -5, -1.6, "two"), o("TE", -1.3, 4.2, "three"), o("WR", -1, -20, "two"), o("WR", -1.6, -9, "two"), o("WR", -1.6, 17, "two")],
  PISTOL: [o("QB", -4, 0, "up"), o("RB", -7, 0, "two"), o("TE", -1.3, 4.2, "three"), o("TE", -1.3, -4.2, "three"), o("WR", -1, -20, "two"), o("WR", -1.6, 17, "two")],
  EMPTY: [o("QB", -5, 0, "up"), o("TE", -1.3, 4.2, "three"), o("WR", -1, -20, "two"), o("WR", -1.6, -11, "two"), o("WR", -1.6, 11, "two"), o("WR", -1, 20, "two")],
  GOAL_LINE: [o("QB", -1.9, 0, "qbUnder"), o("FB", -4, 0, "three"), o("RB", -6.5, 0, "two"), o("TE", -1.3, 4.2, "three"), o("TE", -1.3, -4.2, "three"), o("TE", -2, 5.6, "two")],
};

/** Linha defensiva, linebackers e secundária por pacote (11 cada). */
const DEFENSE: Record<string, Slot[]> = {
  BASE_4_3: [df("DE", 1, -4, "three"), df("DT", 1, -1, "three"), df("DT", 1, 1.2, "three"), df("DE", 1, 4.2, "three"), df("OLB", 4.5, -4, "two"), df("MLB", 4.5, 0, "two"), df("OLB", 4.5, 4, "two"), df("CB", 6, -19.5, "two"), df("CB", 6, 16.5, "two"), df("FS", 12, -6, "up"), df("SS", 10, 6, "up")],
  BASE_3_4: [df("DE", 1, -3.2, "three"), df("DT", 1, 0, "three"), df("DE", 1, 3.2, "three"), df("OLB", 1.4, -6, "two"), df("MLB", 4.5, -1.8, "two"), df("MLB", 4.5, 1.8, "two"), df("OLB", 1.4, 6, "two"), df("CB", 6, -19.5, "two"), df("CB", 6, 16.5, "two"), df("FS", 12, -6, "up"), df("SS", 10, 6, "up")],
  NICKEL: [df("DE", 1, -4, "three"), df("DT", 1, -1, "three"), df("DT", 1, 1.2, "three"), df("DE", 1, 4.2, "three"), df("OLB", 4.5, -2.2, "two"), df("MLB", 4.5, 2.2, "two"), df("CB", 6, -19.5, "two"), df("CB", 6, 16.5, "two"), df("CB", 5, -9, "two"), df("FS", 12, -6, "up"), df("SS", 10, 6, "up")],
  DIME: [df("DE", 1, -4, "three"), df("DT", 1, -1, "three"), df("DT", 1, 1.2, "three"), df("DE", 1, 4.2, "three"), df("MLB", 5, 0, "two"), df("CB", 6, -19.5, "two"), df("CB", 6, 16.5, "two"), df("CB", 5, -9, "two"), df("CB", 5, 9, "two"), df("FS", 12, -6, "up"), df("SS", 10, 6, "up")],
  GOAL_LINE: [df("DE", 1, -4.5, "three"), df("DT", 1, -2.2, "three"), df("DT", 1, 0, "three"), df("DT", 1, 2.2, "three"), df("DE", 1, 4.5, "three"), df("OLB", 3, -3, "two"), df("MLB", 3, 0, "two"), df("OLB", 3, 3, "two"), df("CB", 3, -10, "two"), df("CB", 3, 10, "two"), df("SS", 6, 0, "up")],
};

export const OFFENSE_FORMATIONS = Object.keys(OFFENSE_SKILL);
export const DEFENSE_PACKAGES = Object.keys(DEFENSE);

const PASS_CONCEPTS = new Set(["SHORT_PASS", "MEDIUM_PASS", "DEEP_PASS", "SCREEN", "PLAY_ACTION", "SPIKE"]);

/** Formação ofensiva do lance: a do motor; sem ela, pelo conceito (passe → shotgun, corrida → I, sneak → goal line). */
export function offenseFormationOf(formation: string | null | undefined, concept: string | null | undefined, type?: string | null): string {
  if (formation && OFFENSE_SKILL[formation]) return formation;
  if (concept === "QB_SNEAK") return "GOAL_LINE";
  if (concept === "SPIKE" || type === "SPIKE") return "SINGLEBACK";
  if (concept && PASS_CONCEPTS.has(concept)) return "SHOTGUN";
  if (type === "PASS") return "SHOTGUN";
  if (concept === "OUTSIDE_RUN") return "SINGLEBACK";
  return "I_FORM";
}

export function defensePackageOf(pkg: string | null | undefined): string {
  return pkg && DEFENSE[pkg] ? pkg : "BASE_4_3";
}

const clampW = (w: number) => Math.max(-(HALF_WIDTH_YD - 1), Math.min(HALF_WIDTH_YD - 1, w));

/** Ajuste da defesa pela cobertura e pela blitz: um safety no fundo (Cover 1/3), dois (Cover 2/4) ou nenhum (Cover 0). */
function coverageShift(slots: Slot[], coverage: string | null | undefined, blitz: boolean): Slot[] {
  return slots.map((s) => {
    if (s.role === "FS" || s.role === "SS") {
      if (coverage === "COVER_0") return { ...s, d: 7, w: s.role === "FS" ? -3 : 3 };
      if (coverage === "COVER_1" || coverage === "COVER_3" || coverage === "COVER_6") return s.role === "FS" ? { ...s, d: 14, w: 0 } : { ...s, d: 7, w: 5 };
      if (coverage === "COVER_2" || coverage === "COVER_4") return { ...s, d: 12, w: s.role === "FS" ? -10 : 10 };
    }
    if (blitz && (s.role === "MLB" || s.role === "OLB") && s.d > 2) return { ...s, d: 2.6 };
    return s;
  });
}

export interface FormationInput {
  kind: FormationKind;
  formation?: string | null;
  concept?: string | null;
  type?: string | null;
  defensePackage?: string | null;
  coverage?: string | null;
  blitz?: boolean;
  /** jardas até a linha de gol do adversário (limita o fundo da defesa e dos retornadores) */
  toGoal: number;
  /** jardas até a própria linha de gol (limita o recuo do ataque, do punter e do kicker) */
  toOwnGoal: number;
}

/** As 22 vagas do lance: primeiro as 11 do ataque (ou do time que chuta), depois as 11 da defesa (ou do time que recebe). */
export function formationFor(input: FormationInput): Slot[] {
  const deep = (d: number) => Math.min(d, input.toGoal + 9);
  const back = (d: number) => Math.max(d, -(input.toOwnGoal + 9));
  let slots: Slot[];
  switch (input.kind) {
    case "KICKOFF":
    case "ONSIDE": {
      const onside = input.kind === "ONSIDE";
      const kick: Slot[] = [o("K", -6, onside ? -2 : 0, "up"), ...[-23.5, -19, -14, -9, -4, 4, 9, 14, 19, 23.5].map((w) => o("ST", -1, w, "up"))];
      const front = onside ? 10.5 : 12;
      const recv: Slot[] = [
        ...[-16, -8, 0, 8, 16].map((w) => df("ST", front, w, "two")),
        ...[-12, -4, 4, 12].map((w) => df("ST", onside ? 14 : 24, w, "two")),
        df("KR", deep(onside ? 22 : 62), -3, "up"),
        df("KR", deep(onside ? 24 : 60), 3, "up"),
      ];
      slots = [...kick, ...recv];
      break;
    }
    case "PUNT": {
      const off: Slot[] = [
        o("LS", -1, 0, "three"),
        o("ST", -1.1, -1.4, "two"), o("ST", -1.1, 1.4, "two"), o("ST", -1.3, -2.8, "two"), o("ST", -1.3, 2.8, "two"),
        o("ST", -2, -4.2, "two"), o("ST", -2, 4.2, "two"),
        o("ST", -1, -21, "two"), o("ST", -1, 21, "two"),
        o("ST", back(-5), 1, "up"),
        o("P", back(-14), 0, "up"),
      ];
      const def: Slot[] = [
        ...[-4.5, -2.5, -0.8, 0.8, 2.5, 4.5].map((w) => df("ST", 1, w, "three")),
        df("CB", 1.5, -20, "two"), df("CB", 1.5, 20, "two"),
        df("ST", 9, -6, "two"), df("ST", 9, 6, "two"),
        df("KR", deep(40), 0, "up"),
      ];
      slots = [...off, ...def];
      break;
    }
    case "FIELD_GOAL": {
      const off: Slot[] = [
        o("LS", -1, 0, "three"),
        o("ST", -1.1, -1.4, "three"), o("ST", -1.1, 1.4, "three"), o("ST", -1.3, -2.8, "three"), o("ST", -1.3, 2.8, "three"),
        o("TE", -1.3, -4.2, "three"), o("TE", -1.3, 4.2, "three"),
        o("ST", -1.9, -5.4, "two"), o("ST", -1.9, 5.4, "two"),
        o("H", -7, 0, "kneel"),
        o("K", -9.6, -1.6, "up"),
      ];
      const def: Slot[] = [...[-5.5, -4, -2.5, -1, 1, 2.5, 4, 5.5].map((w) => df("ST", 1, w, "three")), df("CB", 5, -7, "two"), df("CB", 5, 7, "two"), df("SS", deep(9), 0, "up")];
      slots = [...off, ...def];
      break;
    }
    case "KNEEL": {
      const off: Slot[] = [...OL, o("QB", -1.9, 0, "qbUnder"), o("TE", -1.3, -4.2, "three"), o("TE", -1.3, 4.2, "three"), o("RB", -3.2, -1.6, "two"), o("FB", -3.2, 1.6, "two"), o("RB", back(-8), 0, "up")];
      slots = [...off, ...coverageShift(DEFENSE[defensePackageOf(input.defensePackage)], input.coverage, false)];
      break;
    }
    case "SCRIMMAGE":
    default: {
      const f = offenseFormationOf(input.formation, input.concept, input.type);
      const def = coverageShift(DEFENSE[defensePackageOf(input.defensePackage)], input.coverage, !!input.blitz);
      slots = [...OL, ...OFFENSE_SKILL[f], ...def];
      break;
    }
  }
  return slots.map((s) => {
    // nunca além do fundo do campo (o gramado acaba 10 jardas depois da linha de gol) nem em impedimento
    const d = s.side === "O" ? Math.min(-NEUTRAL_YD, back(s.d)) : Math.max(NEUTRAL_YD, deep(s.d));
    return { ...s, d, w: clampW(s.w) };
  });
}
