"use client";
import { useParams } from "next/navigation";
import { api } from "@/lib/api";
import { useQuery } from "@/lib/hooks/useQuery";
import { useT } from "@/i18n/provider";
import { PageHeader } from "@/components/shell/PageHeader";
import { GamePlanSection, PlaybookSection } from "@/components/game-plan/PlanSections";
import { ErrorState, PageSkeleton } from "@/components/ui/States";

/** Playbook e plano de jogo da franquia (RF61): as tendências que o motor usa e os pesos de cada conceito e cobertura. */
export default function PlaybookPage() {
  const t = useT("game");
  const { careerId } = useParams<{ careerId: string }>();
  const q = useQuery(async () => {
    const [plan, book] = await Promise.all([api.gamePlan.get(careerId), api.gamePlan.playbook(careerId)]);
    return { plan, book };
  }, [careerId]);

  if (q.loading && !q.data) return <PageSkeleton rows={2} />;
  if (q.error || !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const { plan, book } = q.data;
  return (
    <div className="flex flex-col gap-4 sm:gap-5">
      <PageHeader eyebrow={t("page.eyebrow")} title={t("page.title")} description={t("page.description")} />
      <GamePlanSection careerId={careerId} initial={plan} />
      <PlaybookSection careerId={careerId} initial={book} editable={plan.editable} />
    </div>
  );
}
