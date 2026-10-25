"use client";
import type { GamePlanOptions, PlanIssue, PlanTendencies } from "@/lib/api/match-types";
import { issuesByField } from "@/lib/match/plan";
import { useT, useTd } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { Segmented, SliderField } from "@/components/ui/Field";

export interface GamePlanFormProps {
  value: PlanTendencies;
  onChange: (v: PlanTendencies) => void;
  options: GamePlanOptions;
  /** problemas por campo (validação local ou `details.errors` do 422) */
  issues?: PlanIssue[];
  disabled?: boolean;
  /** versão do intervalo (RF29): uma coluna */
  compact?: boolean;
  className?: string;
}

/** Mensagem de um problema do plano, no idioma corrente. */
export function useIssueText(): (i: PlanIssue, extra?: Record<string, string | number>) => string {
  const td = useTd();
  return (i, extra) => td("game", `issue.${i.code}`, { value: i.value ?? "", field: i.field, ...extra });
}

function FieldIssues({ issues }: { issues: PlanIssue[] | undefined }) {
  const text = useIssueText();
  if (!issues?.length) return null;
  return (
    <ul className="grid gap-0.5 text-[0.8rem] text-[var(--bad-text)]" role="alert">
      {issues.map((i, n) => (
        <li key={`${i.code}-${n}`}>{text(i)}</li>
      ))}
    </ul>
  );
}

/** Plano de jogo (RF61): corrida × passe, 4ª descida, ritmo, foco defensivo, blitz e defesa base, com os valores que o servidor aceita. */
export function GamePlanForm({ value, onChange, options, issues = [], disabled, compact, className }: GamePlanFormProps) {
  const t = useT("game");
  const td = useTd();
  const byField = issuesByField(issues);
  const set = <K extends keyof PlanTendencies>(k: K, v: PlanTendencies[K]) => onChange({ ...value, [k]: v });
  const seg = <K extends Exclude<keyof PlanTendencies, "passRatio">>(k: K, enumKey: string, values: readonly PlanTendencies[K][]) => (
    <div className="flex flex-col gap-1.5">
      <Segmented
        label={td("game", `plan.field.${k}`)}
        value={value[k]}
        onChange={(v) => !disabled && set(k, v)}
        size={compact ? "sm" : "md"}
        options={values.map((v) => ({ value: v, label: td("enums", `${enumKey}.${v}`), disabled }))}
      />
      <p className="text-[0.78rem] text-text-dim">{td("enums", `${enumKey}.${value[k]}.hint`)}</p>
      <FieldIssues issues={byField.get(k)} />
    </div>
  );
  return (
    <div className={cn("grid gap-5", !compact && "lg:grid-cols-2", className)}>
      <div className={cn("flex flex-col gap-1", !compact && "lg:col-span-2")}>
        <SliderField
          label={t("plan.field.passRatio")}
          value={value.passRatio}
          onChange={(v) => !disabled && set("passRatio", v)}
          min={options.minPassRatio}
          max={options.maxPassRatio}
          step={1}
          leftLabel={t("plan.passRatio.run", { n: 100 - options.minPassRatio })}
          rightLabel={t("plan.passRatio.pass", { n: options.maxPassRatio })}
          format={(v) => t("plan.passRatio.value", { pass: v, run: 100 - v })}
          hint={t("plan.passRatio.hint", { min: options.minPassRatio, max: options.maxPassRatio })}
        />
        <FieldIssues issues={byField.get("passRatio")} />
      </div>
      {seg("fourthDownAggression", "aggression", options.fourthDownAggression)}
      {seg("tempo", "tempo", options.tempo)}
      {seg("defensiveFocus", "defensiveFocus", options.defensiveFocus)}
      {seg("blitzFrequency", "blitz", options.blitzFrequency)}
      {seg("baseDefense", "baseDefense", options.baseDefense)}
    </div>
  );
}
