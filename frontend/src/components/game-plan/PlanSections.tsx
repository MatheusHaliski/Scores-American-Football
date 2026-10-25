"use client";
import { useMemo, useState } from "react";
import { api } from "@/lib/api";
import { ApiError, describeError } from "@/lib/api/errors";
import { planIssuesFromDetails } from "@/lib/api/live-match";
import type { GamePlan, PlanIssue, PlanTendencies, Playbook } from "@/lib/api/match-types";
import {
  customWeights,
  mergePlan,
  mergePlaybook,
  planDraftFrom,
  playbookDraftFrom,
  playbookMeta,
  playbookWeights,
  samePlan,
  samePlaybook,
  validatePlan,
  validatePlaybook,
  type PlaybookDraft,
} from "@/lib/match/plan";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { InlineAlert } from "@/components/ui/States";
import { toast } from "@/components/ui/Toast";
import { IconCheck, IconLoop } from "@/components/ui/icons";
import { GamePlanForm } from "./GamePlanForm";
import { PlaybookEditor } from "./PlaybookEditor";

interface MergeNotice {
  version: number;
  applied: number;
  conflicts: string[];
}

/** Aviso depois de um 409: a versão nova foi carregada e as mudanças do usuário reaplicadas por cima. */
function MergeAlert({ notice, label }: { notice: MergeNotice; label: (field: string) => string }) {
  const t = useT("game");
  return (
    <InlineAlert tone="warn">
      <p>{t("merge.body", { version: notice.version, n: notice.applied })}</p>
      {notice.conflicts.length > 0 && <p className="mt-1">{t("merge.conflicts", { fields: notice.conflicts.map(label).join(", ") })}</p>}
    </InlineAlert>
  );
}

/** Plano de jogo da franquia (RF61) com versão otimista: 409 recarrega e mescla com aviso; 422 marca os campos. */
export function GamePlanSection({ careerId, initial }: { careerId: string; initial: GamePlan }) {
  const t = useT("game");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const [plan, setPlan] = useState(initial);
  const [draft, setDraft] = useState<PlanTendencies>(() => planDraftFrom(initial));
  const [serverIssues, setServerIssues] = useState<PlanIssue[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [merge, setMerge] = useState<MergeNotice | null>(null);
  const [saving, setSaving] = useState(false);
  const base = useMemo(() => planDraftFrom(plan), [plan]);
  const local = validatePlan(draft, plan.options);
  const dirty = !samePlan(draft, base);
  const issues = [...local, ...serverIssues];

  const change = (v: PlanTendencies) => {
    setDraft(v);
    setServerIssues([]);
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    setServerIssues([]);
    try {
      const saved = await api.gamePlan.save(careerId, { version: plan.version, ...draft });
      setPlan(saved);
      setDraft(planDraftFrom(saved));
      setMerge(null);
      toast(t("plan.saved", { n: saved.version }), "good");
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        try {
          const latest = await api.gamePlan.get(careerId);
          const m = mergePlan(base, draft, planDraftFrom(latest));
          setPlan(latest);
          setDraft(m.merged);
          setMerge({ version: latest.version, applied: m.applied.length, conflicts: m.conflicts });
        } catch (e2) {
          setError(describeError(e2));
        }
      } else {
        if (e instanceof ApiError && e.status === 422) setServerIssues(planIssuesFromDetails(e.problem.details));
        setError(describeError(e));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      eyebrow={t("plan.eyebrow")}
      title={t("plan.title")}
      action={
        <div className="flex flex-wrap justify-end gap-1">
          <Badge tone={plan.source === "SAVED" ? "good" : "wood"} size="xs">{t(plan.source === "SAVED" ? "plan.source.SAVED" : "plan.source.COACH_STYLE", { style: plan.coachStyle ? td("enums", `coachStyle.${plan.coachStyle}`) : "—" })}</Badge>
          <Badge size="xs">{t("version", { n: plan.version })}</Badge>
        </div>
      }
    >
      <p className="mb-4 text-[0.88rem] text-text-muted">{t("plan.description")}</p>
      {!plan.editable && <InlineAlert tone="info" className="mb-3">{t("readOnly")}</InlineAlert>}
      {merge && <div className="mb-3"><MergeAlert notice={merge} label={(k) => td("game", `plan.field.${k}`)} /></div>}
      <GamePlanForm value={draft} onChange={change} options={plan.options} issues={issues} disabled={!plan.editable || saving} />
      {error && <InlineAlert tone="bad" className="mt-4">{error}</InlineAlert>}
      <div className="mt-5 flex flex-wrap items-center justify-end gap-2 border-t border-border pt-4">
        {plan.updatedAt && <span className="mr-auto text-[0.75rem] text-text-dim">{t("updatedAt", { when: f.dateTime(plan.updatedAt) })}</span>}
        <Button variant="ghost" size="sm" icon={<IconLoop size={16} />} onClick={() => change(base)} disabled={!dirty || saving}>{t("discard")}</Button>
        <Button variant="primary" icon={<IconCheck size={18} />} onClick={() => void save()} loading={saving} disabled={!plan.editable || !dirty || local.length > 0}>{tc("actions.save")}</Button>
      </div>
    </Card>
  );
}

/** Playbook da franquia (RF61): pesos 0–200 por situação e cobertura, versão otimista (409 mescla com aviso) e erros do 422 por peso. */
export function PlaybookSection({ careerId, initial, editable = true }: { careerId: string; initial: Playbook; editable?: boolean }) {
  const t = useT("game");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const [book, setBook] = useState(initial);
  const meta = useMemo(() => playbookMeta(book), [book]);
  const base = useMemo(() => playbookDraftFrom(book), [book]);
  const [draft, setDraft] = useState<PlaybookDraft>(() => playbookDraftFrom(initial));
  const [serverIssues, setServerIssues] = useState<PlanIssue[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [merge, setMerge] = useState<MergeNotice | null>(null);
  const [saving, setSaving] = useState(false);
  const local = validatePlaybook(draft, meta);
  const dirty = !samePlaybook(draft, base, meta);
  const issues = [...local, ...serverIssues];
  const label = (field: string) => {
    const [g, item] = field.split(".");
    return g === "coverages" ? td("enums", `coverage.${item}`) : `${td("enums", `situationShort.${g}`)} · ${td("enums", `concept.${item}`)}`;
  };

  const change = (d: PlaybookDraft) => {
    setDraft(d);
    setServerIssues([]);
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    setServerIssues([]);
    try {
      const saved = await api.gamePlan.savePlaybook(careerId, { version: book.version, ...playbookWeights(draft, meta) });
      setBook(saved);
      setDraft(playbookDraftFrom(saved));
      setMerge(null);
      toast(t("playbook.saved", { n: saved.version }), "good");
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        try {
          const latest = await api.gamePlan.playbook(careerId);
          const m = mergePlaybook(base, draft, playbookDraftFrom(latest), playbookMeta(latest));
          setBook(latest);
          setDraft(m.merged);
          setMerge({ version: latest.version, applied: m.applied.length, conflicts: m.conflicts });
        } catch (e2) {
          setError(describeError(e2));
        }
      } else {
        if (e instanceof ApiError && e.status === 422) setServerIssues(planIssuesFromDetails(e.problem.details));
        setError(describeError(e));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      eyebrow={t("playbook.eyebrow")}
      title={t("playbook.title")}
      action={
        <div className="flex flex-wrap justify-end gap-1">
          <Badge tone={customWeights(draft, meta) ? "led" : "neutral"} size="xs">{t("playbook.custom", { n: customWeights(draft, meta) })}</Badge>
          <Badge size="xs">{t("version", { n: book.version })}</Badge>
        </div>
      }
    >
      <p className="mb-3 text-[0.88rem] text-text-muted">{t("playbook.description", { min: meta.minWeight, max: meta.maxWeight, neutral: meta.neutralWeight })}</p>
      {!editable && <InlineAlert tone="info" className="mb-3">{t("readOnly")}</InlineAlert>}
      {merge && <div className="mb-3"><MergeAlert notice={merge} label={label} /></div>}
      <PlaybookEditor draft={draft} meta={meta} onChange={change} issues={issues} disabled={!editable || saving} />
      {local.length > 0 && <InlineAlert tone="warn" className="mt-3">{t("playbook.fixBeforeSave", { n: local.length })}</InlineAlert>}
      {error && <InlineAlert tone="bad" className="mt-3">{error}</InlineAlert>}
      <div className="mt-5 flex flex-wrap items-center justify-end gap-2 border-t border-border pt-4">
        {book.updatedAt && <span className="mr-auto text-[0.75rem] text-text-dim">{t("updatedAt", { when: f.dateTime(book.updatedAt) })}</span>}
        <Button variant="ghost" size="sm" icon={<IconLoop size={16} />} onClick={() => change(base)} disabled={!dirty || saving}>{t("discard")}</Button>
        <Button variant="primary" icon={<IconCheck size={18} />} onClick={() => void save()} loading={saving} disabled={!editable || !dirty || local.length > 0}>{tc("actions.save")}</Button>
      </div>
    </Card>
  );
}
