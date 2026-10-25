/**
 * Plano de jogo e playbook no front (RF61/RF29): rascunho editável, validação local com as mesmas regras do backend
 * (`GamePlanPolicy`: passRatio na faixa das opções, valores do motor, pesos de 0 a 200, toda situação com uma corrida por dentro
 * ou por fora e um passe, pelo menos 2 coberturas ativas), erros por campo e a mesclagem depois de um 409 (o que o usuário mudou
 * vai por cima da versão nova do servidor; o que os dois mudaram diferente vira conflito avisado). Puro, sem React.
 */
import type { GamePlanOptions, PlanIssue, PlanTendencies, Playbook, PlaybookWeights } from "@/lib/api/match-types";
import { MIN_ACTIVE_COVERAGES, PLAN_FIELDS, REQUIRED_RUNS } from "@/lib/api/match-types";

/* ───────────────────────── plano de jogo ───────────────────────── */

export function planDraftFrom(p: PlanTendencies): PlanTendencies {
  return { passRatio: p.passRatio, fourthDownAggression: p.fourthDownAggression, tempo: p.tempo, defensiveFocus: p.defensiveFocus, blitzFrequency: p.blitzFrequency, baseDefense: p.baseDefense };
}

export function samePlan(a: PlanTendencies, b: PlanTendencies): boolean {
  return PLAN_FIELDS.every((k) => a[k] === b[k]);
}

/** Campos do plano que mudaram de `a` para `b`. */
export function changedPlanFields(a: PlanTendencies, b: PlanTendencies): (keyof PlanTendencies)[] {
  return PLAN_FIELDS.filter((k) => a[k] !== b[k]);
}

/** Mesmas regras do `GamePlanPolicy.plan`: códigos `REQUIRED`, `PASS_RATIO_OUT_OF_RANGE` e `UNKNOWN_VALUE`. */
export function validatePlan(d: PlanTendencies, options: GamePlanOptions): PlanIssue[] {
  const out: PlanIssue[] = [];
  if (d.passRatio == null || !Number.isFinite(d.passRatio)) out.push({ code: "REQUIRED", field: "passRatio", value: null });
  else if (!Number.isInteger(d.passRatio) || d.passRatio < options.minPassRatio || d.passRatio > options.maxPassRatio) out.push({ code: "PASS_RATIO_OUT_OF_RANGE", field: "passRatio", value: String(d.passRatio) });
  const enumField = (field: Exclude<keyof PlanTendencies, "passRatio">, allowed: readonly string[]) => {
    const v = d[field];
    if (!v) out.push({ code: "REQUIRED", field, value: null });
    else if (!allowed.includes(v)) out.push({ code: "UNKNOWN_VALUE", field, value: v });
  };
  enumField("fourthDownAggression", options.fourthDownAggression);
  enumField("tempo", options.tempo);
  enumField("defensiveFocus", options.defensiveFocus);
  enumField("blitzFrequency", options.blitzFrequency);
  enumField("baseDefense", options.baseDefense);
  return out;
}

export interface Merge<T, F extends string> {
  merged: T;
  /** mudanças do usuário reaplicadas sobre a versão nova */
  applied: F[];
  /** mudadas pelos dois lados com valores diferentes: fica a do usuário, com aviso */
  conflicts: F[];
}

/**
 * 409 do plano: `base` é a versão que o usuário abriu, `mine` o rascunho e `theirs` a versão atual do servidor. Campo que só o
 * usuário mudou vai por cima; campo que só o servidor mudou fica o do servidor; campo que os dois mudaram diferente fica o do
 * usuário e entra em `conflicts`, para a tela avisar antes de gravar de novo.
 */
export function mergePlan(base: PlanTendencies, mine: PlanTendencies, theirs: PlanTendencies): Merge<PlanTendencies, keyof PlanTendencies> {
  const merged = planDraftFrom(theirs);
  const applied: (keyof PlanTendencies)[] = [];
  const conflicts: (keyof PlanTendencies)[] = [];
  for (const k of changedPlanFields(base, mine)) {
    (merged as unknown as Record<string, unknown>)[k] = mine[k];
    applied.push(k);
    if (theirs[k] !== base[k] && theirs[k] !== mine[k]) conflicts.push(k);
  }
  return { merged, applied, conflicts };
}

/* ───────────────────────── playbook ───────────────────────── */

/** Pesos editáveis: situação → conceito → peso, e cobertura → peso. */
export interface PlaybookDraft {
  situations: Record<string, Record<string, number>>;
  coverages: Record<string, number>;
}

/** O que não muda entre versões: ordem das situações, conceitos de corrida e passe, coberturas e limites. */
export interface PlaybookMeta {
  situations: string[];
  runConcepts: string[];
  passConcepts: string[];
  coverages: string[];
  minWeight: number;
  maxWeight: number;
  neutralWeight: number;
}

export function playbookMeta(pb: Playbook): PlaybookMeta {
  return {
    situations: pb.situations.map((s) => s.situation),
    runConcepts: pb.runConcepts,
    passConcepts: pb.passConcepts,
    coverages: pb.coverages.map((c) => c.item),
    minWeight: pb.minWeight,
    maxWeight: pb.maxWeight,
    neutralWeight: pb.neutralWeight,
  };
}

export function playbookDraftFrom(pb: Pick<Playbook, "situations" | "coverages">): PlaybookDraft {
  const situations: PlaybookDraft["situations"] = {};
  for (const s of pb.situations) situations[s.situation] = Object.fromEntries(s.concepts.map((c) => [c.item, c.weight]));
  return { situations, coverages: Object.fromEntries(pb.coverages.map((c) => [c.item, c.weight])) };
}

/** Peso no rascunho (o que faltar vale o neutro, como no backend). */
export function weightOf(d: PlaybookDraft, meta: PlaybookMeta, field: string): number {
  const [head, item] = field.split(".");
  const v = head === "coverages" ? d.coverages[item] : d.situations[head]?.[item];
  return typeof v === "number" ? v : meta.neutralWeight;
}

/** Copia o rascunho trocando um peso (`EARLY_DOWN.DEEP_PASS` ou `coverages.COVER_3`). */
export function setWeight(d: PlaybookDraft, field: string, weight: number): PlaybookDraft {
  const [head, item] = field.split(".");
  if (head === "coverages") return { situations: d.situations, coverages: { ...d.coverages, [item]: weight } };
  return { situations: { ...d.situations, [head]: { ...(d.situations[head] ?? {}), [item]: weight } }, coverages: d.coverages };
}

/** Todos os campos do playbook, na ordem da tela. */
export function playbookFields(meta: PlaybookMeta): string[] {
  const concepts = [...meta.runConcepts, ...meta.passConcepts];
  return [...meta.situations.flatMap((s) => concepts.map((c) => `${s}.${c}`)), ...meta.coverages.map((c) => `coverages.${c}`)];
}

/** Corpo do PUT / ajuste PLAYBOOK, na ordem do servidor. */
export function playbookWeights(d: PlaybookDraft, meta: PlaybookMeta): PlaybookWeights {
  const concepts = [...meta.runConcepts, ...meta.passConcepts];
  return {
    situations: meta.situations.map((s) => ({ situation: s, concepts: concepts.map((c) => ({ item: c, weight: weightOf(d, meta, `${s}.${c}`) })) })),
    coverages: meta.coverages.map((c) => ({ item: c, weight: weightOf(d, meta, `coverages.${c}`) })),
  };
}

export function samePlaybook(a: PlaybookDraft, b: PlaybookDraft, meta: PlaybookMeta): boolean {
  return playbookFields(meta).every((f) => weightOf(a, meta, f) === weightOf(b, meta, f));
}

/** Pesos fora do neutro (o `customWeights` do servidor). */
export function customWeights(d: PlaybookDraft, meta: PlaybookMeta): number {
  return playbookFields(meta).filter((f) => weightOf(d, meta, f) !== meta.neutralWeight).length;
}

/** Mesmas regras do `GamePlanPolicy.playbook` para o que a tela edita: faixa de peso e o playbook mínimo. */
export function validatePlaybook(d: PlaybookDraft, meta: PlaybookMeta): PlanIssue[] {
  const out: PlanIssue[] = [];
  for (const f of playbookFields(meta)) {
    const w = weightOf(d, meta, f);
    if (!Number.isInteger(w) || w < meta.minWeight || w > meta.maxWeight) out.push({ code: "WEIGHT_OUT_OF_RANGE", field: f, value: String(w) });
  }
  for (const s of meta.situations) {
    const runs = REQUIRED_RUNS.filter((c) => meta.runConcepts.includes(c));
    if (runs.every((c) => weightOf(d, meta, `${s}.${c}`) === 0)) out.push({ code: "NO_RUN_CONCEPT", field: s, value: null });
    if (meta.passConcepts.every((c) => weightOf(d, meta, `${s}.${c}`) === 0)) out.push({ code: "NO_PASS_CONCEPT", field: s, value: null });
  }
  if (meta.coverages.filter((c) => weightOf(d, meta, `coverages.${c}`) > 0).length < MIN_ACTIVE_COVERAGES) out.push({ code: "NO_COVERAGE", field: "coverages", value: null });
  return out;
}

const INCOMPLETE = new Set(["NO_RUN_CONCEPT", "NO_PASS_CONCEPT", "NO_COVERAGE"]);

/** Código do 422 do playbook: PLAYBOOK_INCOMPLETE quando só falta o mínimo, PLAYBOOK_INVALID no resto (como o backend). */
export function playbookErrorCode(issues: PlanIssue[]): "PLAYBOOK_INCOMPLETE" | "PLAYBOOK_INVALID" {
  return issues.length > 0 && issues.every((i) => INCOMPLETE.has(i.code)) ? "PLAYBOOK_INCOMPLETE" : "PLAYBOOK_INVALID";
}

/** 409 do playbook: mesma regra do plano, peso a peso. */
export function mergePlaybook(base: PlaybookDraft, mine: PlaybookDraft, theirs: PlaybookDraft, meta: PlaybookMeta): Merge<PlaybookDraft, string> {
  let merged = theirs;
  const applied: string[] = [];
  const conflicts: string[] = [];
  for (const f of playbookFields(meta)) {
    const b = weightOf(base, meta, f), m = weightOf(mine, meta, f), t = weightOf(theirs, meta, f);
    if (m === b) continue;
    merged = setWeight(merged, f, m);
    applied.push(f);
    if (t !== b && t !== m) conflicts.push(f);
  }
  return { merged, applied, conflicts };
}

/* ───────────────────────── erros por campo ───────────────────────── */

/** Agrupa os problemas por campo (o 422 do servidor ou a validação local). */
export function issuesByField(issues: PlanIssue[]): Map<string, PlanIssue[]> {
  const m = new Map<string, PlanIssue[]>();
  for (const i of issues) m.set(i.field, [...(m.get(i.field) ?? []), i]);
  return m;
}

/** Problemas de uma situação inteira ou de um peso dela (`EARLY_DOWN` e `EARLY_DOWN.*`). */
export function issuesOfGroup(issues: PlanIssue[], group: string): PlanIssue[] {
  return issues.filter((i) => i.field === group || i.field.startsWith(`${group}.`));
}

