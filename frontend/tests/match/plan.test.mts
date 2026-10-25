// Plano de jogo e playbook no front (RF61/RF29): validação local com as regras do `GamePlanPolicy`, código do 422 do playbook,
// corpo do PUT na ordem do servidor e a mesclagem depois do 409 (o que o usuário mudou vai por cima da versão nova; o que os dois
// mudaram diferente vira conflito avisado). Os rascunhos partem dos goldens game-plan.json e playbook.json.
import { test } from "node:test";
import assert from "node:assert/strict";
import { gamePlanFromDto, playbookFromDto } from "@/lib/api/live-match";
import {
  customWeights,
  issuesByField,
  issuesOfGroup,
  mergePlan,
  mergePlaybook,
  planDraftFrom,
  playbookDraftFrom,
  playbookErrorCode,
  playbookFields,
  playbookMeta,
  playbookWeights,
  samePlaybook,
  setWeight,
  validatePlan,
  validatePlaybook,
} from "@/lib/match/plan";
import { loadGolden } from "../golden.mts";

const plan = gamePlanFromDto(loadGolden("game-plan.json"));
const book = playbookFromDto(loadGolden("playbook.json"));
const meta = playbookMeta(book);
const base = playbookDraftFrom(book);
const codes = (xs: { code: string }[]) => xs.map((x) => x.code);

test("plano do golden é válido; passRatio fora de 20–80, fracionário ou ausente e valor desconhecido são barrados", () => {
  const d = planDraftFrom(plan);
  assert.deepEqual(validatePlan(d, plan.options), []);
  assert.deepEqual(validatePlan({ ...d, passRatio: 19 }, plan.options), [{ code: "PASS_RATIO_OUT_OF_RANGE", field: "passRatio", value: "19" }]);
  assert.deepEqual(codes(validatePlan({ ...d, passRatio: 81 }, plan.options)), ["PASS_RATIO_OUT_OF_RANGE"]);
  assert.deepEqual(codes(validatePlan({ ...d, passRatio: 50.5 }, plan.options)), ["PASS_RATIO_OUT_OF_RANGE"]);
  assert.deepEqual(codes(validatePlan({ ...d, passRatio: Number.NaN }, plan.options)), ["REQUIRED"]);
  assert.deepEqual(validatePlan({ ...d, passRatio: 20 }, plan.options), [], "os limites valem");
  assert.deepEqual(validatePlan({ ...d, passRatio: 80 }, plan.options), []);
  const bad = validatePlan({ ...d, baseDefense: "NICKEL" as never }, plan.options);
  assert.deepEqual(bad, [{ code: "UNKNOWN_VALUE", field: "baseDefense", value: "NICKEL" }], "defesa base só 4-3 ou 3-4 (D-65)");
});

test("playbook do golden: válido, tudo no neutro, 45 pesos e o corpo do PUT na ordem do servidor", () => {
  assert.deepEqual(validatePlaybook(base, meta), []);
  assert.equal(customWeights(base, meta), 0);
  assert.equal(playbookFields(meta).length, 5 * 8 + 6);
  const w = playbookWeights(base, meta);
  assert.deepEqual(w.situations.map((s) => s.situation), book.situations.map((s) => s.situation));
  assert.deepEqual(w.situations[0].concepts.map((c) => c.item), [...book.runConcepts, ...book.passConcepts]);
  assert.deepEqual(w.coverages, book.coverages);
});

test("playbook mínimo: sem corrida por dentro/por fora, sem passe ou com menos de 2 coberturas = PLAYBOOK_INCOMPLETE", () => {
  let d = setWeight(setWeight(base, "RED_ZONE.INSIDE_RUN", 0), "RED_ZONE.OUTSIDE_RUN", 0);
  assert.deepEqual(validatePlaybook(d, meta), [{ code: "NO_RUN_CONCEPT", field: "RED_ZONE", value: null }], "o QB sneak não conta como corrida");
  for (const c of meta.passConcepts) d = setWeight(d, `TWO_MINUTE.${c}`, 0);
  for (const c of meta.coverages.slice(1)) d = setWeight(d, `coverages.${c}`, 0);
  const issues = validatePlaybook(d, meta);
  assert.deepEqual(codes(issues), ["NO_RUN_CONCEPT", "NO_PASS_CONCEPT", "NO_COVERAGE"]);
  assert.equal(playbookErrorCode(issues), "PLAYBOOK_INCOMPLETE");
  assert.deepEqual(issuesOfGroup(issues, "TWO_MINUTE").map((i) => i.code), ["NO_PASS_CONCEPT"]);
});

test("peso fora de 0–200 ou vazio = WEIGHT_OUT_OF_RANGE no campo; misturado com o mínimo, o código é PLAYBOOK_INVALID", () => {
  const d = setWeight(setWeight(base, "EARLY_DOWN.DEEP_PASS", 201), "coverages.COVER_2", Number.NaN);
  const issues = validatePlaybook(d, meta);
  assert.deepEqual(issues.map((i) => i.field), ["EARLY_DOWN.DEEP_PASS", "coverages.COVER_2"]);
  assert.equal(playbookErrorCode(issues), "PLAYBOOK_INVALID");
  const mixed = validatePlaybook(setWeight(setWeight(setWeight(d, "EARLY_DOWN.INSIDE_RUN", 0), "EARLY_DOWN.OUTSIDE_RUN", 0), "EARLY_DOWN.DEEP_PASS", 0), meta);
  assert.equal(playbookErrorCode(mixed), "PLAYBOOK_INVALID");
  assert.deepEqual(issuesByField(issues).get("coverages.COVER_2")?.[0].code, "WEIGHT_OUT_OF_RANGE");
  assert.deepEqual(validatePlaybook(setWeight(base, "EARLY_DOWN.DEEP_PASS", 0), meta), [], "0 desativa sem erro");
  assert.deepEqual(validatePlaybook(setWeight(base, "EARLY_DOWN.DEEP_PASS", 200), meta), []);
});

test("409 do plano: campo só meu vai por cima, só deles fica, os dois mudaram diferente = conflito com o meu valor", () => {
  const b = planDraftFrom(plan);
  const mine = { ...b, passRatio: 70, tempo: "HURRY_UP" as const };
  const theirs = { ...b, passRatio: 40, blitzFrequency: "HIGH" as const };
  const m = mergePlan(b, mine, theirs);
  assert.deepEqual(m.merged, { ...b, passRatio: 70, tempo: "HURRY_UP", blitzFrequency: "HIGH" });
  assert.deepEqual(m.applied.sort(), ["passRatio", "tempo"]);
  assert.deepEqual(m.conflicts, ["passRatio"]);
  // os dois mudaram para o mesmo valor: não é conflito
  assert.deepEqual(mergePlan(b, { ...b, tempo: "MILK_CLOCK" }, { ...b, tempo: "MILK_CLOCK" }).conflicts, []);
});

test("409 do playbook: mesma regra peso a peso", () => {
  const mine = setWeight(setWeight(base, "EARLY_DOWN.DEEP_PASS", 150), "coverages.COVER_0", 40);
  const theirs = setWeight(setWeight(base, "EARLY_DOWN.DEEP_PASS", 60), "RED_ZONE.QB_SNEAK", 180);
  const m = mergePlaybook(base, mine, theirs, meta);
  assert.equal(m.merged.situations.EARLY_DOWN.DEEP_PASS, 150);
  assert.equal(m.merged.coverages.COVER_0, 40);
  assert.equal(m.merged.situations.RED_ZONE.QB_SNEAK, 180, "mudança só do servidor fica");
  assert.deepEqual(m.conflicts, ["EARLY_DOWN.DEEP_PASS"]);
  assert.equal(customWeights(m.merged, meta), 3);
  assert.ok(!samePlaybook(m.merged, theirs, meta));
  assert.ok(samePlaybook(mergePlaybook(base, base, theirs, meta).merged, theirs, meta), "sem mudança minha: fica a versão deles");
});
