"use client";
import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import type { GamePlanOptions, MatchAdjustment, MatchSessionStatus, PlanTendencies, PlaybookWeights } from "@/lib/api/match-types";
import { planDraftFrom, playbookDraftFrom, playbookMeta, playbookWeights, samePlan, samePlaybook, validatePlan, validatePlaybook, type PlaybookDraft, type PlaybookMeta } from "@/lib/match/plan";
import { useT } from "@/i18n/provider";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { InlineAlert, Skeleton } from "@/components/ui/States";
import { Tabs } from "@/components/ui/Tabs";
import { toast } from "@/components/ui/Toast";
import { IconCheck, IconLoop } from "@/components/ui/icons";
import { GamePlanForm } from "@/components/game-plan/GamePlanForm";
import { PlaybookEditor } from "@/components/game-plan/PlaybookEditor";

interface Base {
  plan: PlanTendencies;
  options: GamePlanOptions;
  draft: PlaybookDraft;
  meta: PlaybookMeta;
}

export interface BreakPanelProps {
  careerId: string;
  status: MatchSessionStatus;
  busy: boolean;
  /** último ajuste mandado nesta partida (o estado da API não traz o plano em vigor) */
  adjusted: { plan?: PlanTendencies; playbook?: PlaybookWeights } | null;
  onApply: (adjustments: MatchAdjustment[]) => Promise<boolean>;
}

/**
 * Playbook do intervalo (RF29): antes do kickoff, entre quartos e no intervalo, o plano de jogo e os pesos do playbook em modo
 * compacto. "Aplicar" manda `POST /matches/{mid}/decisions` (GAME_PLAN e/ou PLAYBOOK) só com o que mudou; vale do próximo snap e não
 * muda o plano salvo da franquia (D-60).
 */
export function BreakPanel({ careerId, status, busy, adjusted, onApply }: BreakPanelProps) {
  const t = useT("match");
  const [base, setBase] = useState<Base | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [plan, setPlan] = useState<PlanTendencies | null>(null);
  const [draft, setDraft] = useState<PlaybookDraft | null>(null);
  const [tab, setTab] = useState<"plan" | "playbook">("plan");

  useEffect(() => {
    let alive = true;
    Promise.all([api.gamePlan.get(careerId), api.gamePlan.playbook(careerId)])
      .then(([gp, pb]) => {
        if (!alive) return;
        const b: Base = {
          plan: planDraftFrom(adjusted?.plan ?? gp),
          options: gp.options,
          draft: playbookDraftFrom(adjusted?.playbook ?? pb),
          meta: playbookMeta(pb),
        };
        setBase(b);
        setPlan(b.plan);
        setDraft(b.draft);
      })
      .catch((e) => alive && setLoadError(describeError(e)));
    return () => {
      alive = false;
    };
  }, [careerId, adjusted]);

  const planIssues = useMemo(() => (plan && base ? validatePlan(plan, base.options) : []), [plan, base]);
  const bookIssues = useMemo(() => (draft && base ? validatePlaybook(draft, base.meta) : []), [draft, base]);
  const planChanged = !!plan && !!base && !samePlan(plan, base.plan);
  const bookChanged = !!draft && !!base && !samePlaybook(draft, base.draft, base.meta);

  const apply = async () => {
    if (!base || !plan || !draft) return;
    const adjustments: MatchAdjustment[] = [];
    if (planChanged) adjustments.push({ type: "GAME_PLAN", gamePlan: plan });
    if (bookChanged) adjustments.push({ type: "PLAYBOOK", playbook: playbookWeights(draft, base.meta) });
    if (!adjustments.length) return;
    if (await onApply(adjustments)) toast(t("break.applied"), "good");
  };

  const title = status === "HALFTIME" ? t("break.title.HALFTIME") : status === "NOT_STARTED" ? t("break.title.NOT_STARTED") : t("break.title.BREAK");
  return (
    <Card eyebrow={t("break.eyebrow")} title={title} tone="orange">
      <p className="mb-3 text-[0.85rem] text-text-muted">{t("break.description")}</p>
      {loadError ? (
        <InlineAlert tone="bad">{loadError}</InlineAlert>
      ) : !base || !plan || !draft ? (
        <Skeleton className="h-64 w-full" />
      ) : (
        <>
          <Tabs value={tab} onChange={setTab} className="mb-3" tabs={[{ value: "plan", label: t("break.tab.plan") }, { value: "playbook", label: t("break.tab.playbook") }]} />
          {tab === "plan" ? (
            <GamePlanForm value={plan} onChange={setPlan} options={base.options} issues={planIssues} disabled={busy} compact />
          ) : (
            <PlaybookEditor draft={draft} meta={base.meta} onChange={setDraft} issues={bookIssues} disabled={busy} compact />
          )}
          <div className="mt-4 flex flex-wrap items-center justify-end gap-2 border-t border-border pt-3">
            <span className="mr-auto text-[0.75rem] text-text-dim">{planChanged || bookChanged ? t("break.pending", { n: Number(planChanged) + Number(bookChanged) }) : t("break.noChanges")}</span>
            <Button variant="ghost" size="sm" icon={<IconLoop size={14} />} onClick={() => { setPlan(base.plan); setDraft(base.draft); }} disabled={busy || (!planChanged && !bookChanged)}>{t("break.discard")}</Button>
            <Button variant="primary" icon={<IconCheck size={16} />} onClick={() => void apply()} loading={busy} disabled={(!planChanged && !bookChanged) || planIssues.length > 0 || bookIssues.length > 0}>{t("break.apply")}</Button>
          </div>
        </>
      )}
    </Card>
  );
}
