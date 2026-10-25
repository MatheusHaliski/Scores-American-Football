"use client";
import { useState } from "react";
import type { PlanIssue } from "@/lib/api/match-types";
import { issuesByField, issuesOfGroup, setWeight, weightOf, type PlaybookDraft, type PlaybookMeta } from "@/lib/match/plan";
import { useT, useTd } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { InlineAlert } from "@/components/ui/States";
import { Tabs } from "@/components/ui/Tabs";
import { IconLoop } from "@/components/ui/icons";
import { useIssueText } from "./GamePlanForm";

export interface PlaybookEditorProps {
  draft: PlaybookDraft;
  meta: PlaybookMeta;
  onChange: (d: PlaybookDraft) => void;
  issues?: PlanIssue[];
  disabled?: boolean;
  compact?: boolean;
}

const COVERAGES_TAB = "coverages";

function WeightRow({ field, label, value, meta, onChange, disabled, issues, group }: { field: string; label: string; value: number; meta: PlaybookMeta; onChange: (v: number) => void; disabled?: boolean; issues?: PlanIssue[]; group: string }) {
  const t = useT("game");
  const text = useIssueText();
  const id = `pb-${field.replace(/\./g, "-")}`;
  const off = value === 0;
  const neutral = value === meta.neutralWeight;
  const tone = off ? "text-text-dim" : value > meta.neutralWeight ? "text-led" : value < meta.neutralWeight ? "text-[var(--warn-text)]" : "text-text";
  const pct = Math.max(0, Math.min(100, ((value - meta.minWeight) / (meta.maxWeight - meta.minWeight)) * 100));
  const valueText = off ? t("playbook.weight.off") : neutral ? t("playbook.weight.neutral", { n: value }) : t("playbook.weight.value", { n: value });
  return (
    <li className={cn("py-2", !!issues?.length && "rounded-md bg-bad/10 px-2")}>
      <div className="flex items-center gap-2">
        <label htmlFor={id} className="flex min-w-0 flex-1 items-center gap-2 text-[0.9rem]">
          <span className="truncate">{label}</span>
          {off && <Badge size="xs" tone="neutral">{t("playbook.off")}</Badge>}
        </label>
        <input
          type="number"
          inputMode="numeric"
          min={meta.minWeight}
          max={meta.maxWeight}
          step={1}
          value={Number.isFinite(value) ? value : ""}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value === "" ? Number.NaN : Number(e.target.value))}
          aria-label={t("playbook.weight.aria", { item: label, group })}
          className={cn("h-11 w-[4.5rem] shrink-0 rounded-[var(--radius-md)] border border-border-strong bg-pitch px-2 text-right font-heading tabular focus:border-rim focus:outline-none focus-visible:ring-2 focus-visible:ring-rim/50", tone, !!issues?.length && "border-bad")}
        />
        <button
          type="button"
          onClick={() => onChange(meta.neutralWeight)}
          disabled={disabled || neutral}
          className="touch grid shrink-0 place-items-center rounded-md text-text-muted hover:bg-arena-3 hover:text-text disabled:opacity-30"
          aria-label={t("playbook.resetItem", { item: label })}
          title={t("playbook.resetItem", { item: label })}
        >
          <IconLoop size={16} />
        </button>
      </div>
      <input
        id={id}
        type="range"
        min={meta.minWeight}
        max={meta.maxWeight}
        step={5}
        value={Number.isFinite(value) ? value : meta.neutralWeight}
        disabled={disabled}
        onChange={(e) => onChange(Number(e.target.value))}
        aria-valuetext={valueText}
        aria-describedby={issues?.length ? `${id}-err` : undefined}
        className="mt-1 block w-full cursor-pointer accent-orange disabled:cursor-not-allowed"
        style={{ background: `linear-gradient(90deg, var(--orange) ${pct}%, var(--arena-4) ${pct}%)`, borderRadius: 999, height: 6 }}
      />
      {issues?.length ? (
        <p id={`${id}-err`} className="mt-1 text-[0.78rem] text-[var(--bad-text)]">
          {issues.map((i) => text(i)).join(" · ")}
        </p>
      ) : null}
    </li>
  );
}

/**
 * Editor de pesos do playbook (RF61, D-58): por situação, o peso de cada corrida e de cada passe; e o peso de cada cobertura. 0 a
 * 200, 100 = padrão da liga, 0 = desativado. Os erros do servidor (422) e da validação local aparecem no peso ou na situação.
 */
export function PlaybookEditor({ draft, meta, onChange, issues = [], disabled, compact }: PlaybookEditorProps) {
  const t = useT("game");
  const td = useTd();
  const text = useIssueText();
  const [tab, setTab] = useState<string>(meta.situations[0] ?? COVERAGES_TAB);
  const byField = issuesByField(issues);
  const groupLabel = (g: string) => (g === COVERAGES_TAB ? t("playbook.coverages") : td("enums", `situation.${g}`));
  const tabs = [...meta.situations, COVERAGES_TAB].map((g) => {
    const bad = issuesOfGroup(issues, g).length;
    const fields = g === COVERAGES_TAB ? meta.coverages.map((c) => `coverages.${c}`) : [...meta.runConcepts, ...meta.passConcepts].map((c) => `${g}.${c}`);
    const custom = fields.filter((f) => weightOf(draft, meta, f) !== meta.neutralWeight).length;
    return {
      value: g,
      label: compact && g !== COVERAGES_TAB ? td("enums", `situationShort.${g}`) : groupLabel(g),
      badge: bad ? <Badge tone="bad" size="xs" aria-label={t("playbook.tabErrors", { n: bad })}>{bad}</Badge> : custom ? <Badge tone="led" size="xs" aria-label={t("playbook.tabCustom", { n: custom })}>{custom}</Badge> : undefined,
    };
  });
  const row = (field: string, label: string) => (
    <WeightRow key={field} field={field} label={label} value={weightOf(draft, meta, field)} meta={meta} disabled={disabled} issues={byField.get(field)} group={groupLabel(tab)} onChange={(v) => onChange(setWeight(draft, field, v))} />
  );
  const resetGroup = () => {
    let d = draft;
    const fields = tab === COVERAGES_TAB ? meta.coverages.map((c) => `coverages.${c}`) : [...meta.runConcepts, ...meta.passConcepts].map((c) => `${tab}.${c}`);
    for (const f of fields) d = setWeight(d, f, meta.neutralWeight);
    onChange(d);
  };
  const groupIssues = issues.filter((i) => i.field === tab);

  return (
    <div className="flex flex-col gap-3">
      <Tabs value={tab} onChange={setTab} tabs={tabs} />
      <div role="tabpanel" aria-label={groupLabel(tab)} className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-[0.82rem] text-text-muted">{tab === COVERAGES_TAB ? t("playbook.coveragesHint") : td("enums", `situation.${tab}.hint`)}</p>
          <Button variant="ghost" size="sm" icon={<IconLoop size={14} />} onClick={resetGroup} disabled={disabled}>
            {t("playbook.resetGroup")}
          </Button>
        </div>
        {groupIssues.map((i, n) => (
          <InlineAlert key={`${i.code}-${n}`} tone="bad">{text(i, { situation: groupLabel(tab) })}</InlineAlert>
        ))}
        {tab === COVERAGES_TAB ? (
          <ul className="divide-y divide-border/60">{meta.coverages.map((c) => row(`coverages.${c}`, td("enums", `coverage.${c}`)))}</ul>
        ) : (
          <div className={cn("grid gap-x-6 gap-y-4", !compact && "md:grid-cols-2")}>
            <div>
              <h4 className="mb-1 font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-dim">{t("playbook.runs")}</h4>
              <ul className="divide-y divide-border/60">{meta.runConcepts.map((c) => row(`${tab}.${c}`, td("enums", `concept.${c}`)))}</ul>
            </div>
            <div>
              <h4 className="mb-1 font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-dim">{t("playbook.passes")}</h4>
              <ul className="divide-y divide-border/60">{meta.passConcepts.map((c) => row(`${tab}.${c}`, td("enums", `concept.${c}`)))}</ul>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
