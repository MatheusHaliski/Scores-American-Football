"use client";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import type { SeasonAdvanceResult } from "@/lib/api/season-types";
import { useQuery } from "@/lib/hooks/useQuery";
import { seasonReaction } from "@/lib/season/errors";
import { useT, useTd } from "@/i18n/provider";
import { useCareer } from "@/store/career";
import { PageHeader } from "@/components/shell/PageHeader";
import { AdvanceResultView, SeasonSummaryView, TrophyRoom } from "@/components/season/SeasonViews";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { SelectField } from "@/components/ui/Field";
import { Tabs } from "@/components/ui/Tabs";
import { ErrorState, InlineAlert, PageSkeleton } from "@/components/ui/States";
import { toast } from "@/components/ui/Toast";
import { IconForward } from "@/components/ui/icons";

type Tab = "summary" | "trophies";

/**
 * Temporada (RF35): resumo (campeão, campanha, prêmios provisórios até a final, líderes e entressafra), histórico por `?number=`,
 * sala de troféus (`?tab=trophies`, D-111) e o avanço para a próxima temporada com confirmação. O avanço manda o `seasonId` que a tela
 * mostra: 422 SEASON_NOT_FINISHED (cedo demais) e 409 SEASON_ALREADY_ADVANCED (outra aba ou clique duplo) recarregam e explicam.
 */
function SeasonScreen() {
  const t = useT("season");
  const td = useTd();
  const { careerId } = useParams<{ careerId: string }>();
  const search = useSearchParams();
  const router = useRouter();
  const setDashboard = useCareer((s) => s.setDashboard);
  const numberParam = Number(search.get("number"));
  const number = Number.isInteger(numberParam) && numberParam > 0 ? numberParam : undefined;
  const tab: Tab = search.get("tab") === "trophies" ? "trophies" : "summary";
  const q = useQuery(async () => {
    const [summary, trophies] = await Promise.all([api.season.summary(careerId, number), api.season.trophies(careerId)]);
    return { summary, trophies };
  }, [careerId, number]);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<SeasonAdvanceResult | null>(null);
  const [notice, setNotice] = useState<{ tone: "warn" | "info" | "bad"; text: string } | null>(null);

  const go = (next: { number?: number | null; tab?: Tab }) => {
    const p = new URLSearchParams(search.toString());
    if (next.number !== undefined) {
      if (next.number == null) p.delete("number");
      else p.set("number", String(next.number));
    }
    if (next.tab) {
      if (next.tab === "summary") p.delete("tab");
      else p.set("tab", next.tab);
    }
    const qs = p.toString();
    router.replace(`/c/${careerId}/season${qs ? `?${qs}` : ""}`);
  };

  if (q.loading && !q.data) return <PageSkeleton rows={3} />;
  if (q.error || !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const { summary: s, trophies } = q.data;
  const current = trophies.seasons.reduce((m, x) => Math.max(m, x.number), s.number);
  const viewingCurrent = number == null || number === current;
  const expiring = s.offseason?.expiringContracts.length ?? 0;

  const advance = async () => {
    setBusy(true);
    setNotice(null);
    try {
      const r = await api.season.advance(careerId, { seasonId: s.seasonId });
      setResult(r);
      setConfirm(false);
      toast(t("season.advance.done", { n: r.seasonNumber, year: String(r.seasonYear) }), "good");
      void api.careers.dashboard(careerId).then(setDashboard).catch(() => undefined);
      go({ number: null, tab: "summary" });
      await q.refetch();
    } catch (e) {
      setConfirm(false);
      const r = seasonReaction(e);
      if (r.kind === "notFinished") setNotice({ tone: "warn", text: t("season.notFinished", { phase: r.phase ? td("enums", `seasonPhase.${r.phase}`) : "—", week: r.week ?? "—" }) });
      else if (r.kind === "alreadyAdvanced") setNotice({ tone: "info", text: t("season.alreadyAdvanced", { n: r.seasonNumber ?? "—" }) });
      else setNotice({ tone: "bad", text: describeError(e) });
      if (r.reload) await q.refetch();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        eyebrow={t("season.eyebrow")}
        title={t("season.title", { year: String(s.year) })}
        description={t("season.description")}
        actions={
          viewingCurrent ? (
            <div className="flex flex-col items-start gap-1 sm:items-end">
              <Button variant="led" icon={<IconForward size={18} />} onClick={() => setConfirm(true)} disabled={!s.offseason?.canAdvance} loading={busy}>
                {t("season.advance.cta")}
              </Button>
              {!s.offseason?.canAdvance && <span className="text-[0.75rem] text-text-dim">{t("season.advance.locked")}</span>}
            </div>
          ) : undefined
        }
      />
      {notice && <InlineAlert tone={notice.tone}>{notice.text}</InlineAlert>}
      {result && <AdvanceResultView r={result} careerId={careerId} />}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <Tabs
          value={tab}
          onChange={(v) => go({ tab: v })}
          tabs={[
            { value: "summary", label: t("season.tab.summary") },
            { value: "trophies", label: t("season.tab.trophies") },
          ]}
        />
        {tab === "summary" && trophies.seasons.length > 1 && (
          <SelectField label={t("season.history.label")} value={String(number ?? current)} onChange={(e) => go({ number: Number(e.target.value) === current ? null : Number(e.target.value) })} className="sm:w-64">
            {[...trophies.seasons].sort((a, b) => b.number - a.number).map((x) => (
              <option key={x.seasonId} value={x.number}>
                {x.number === current ? t("season.history.current", { n: x.number, year: String(x.year) }) : t("season.history.item", { n: x.number, year: String(x.year) })}
              </option>
            ))}
          </SelectField>
        )}
      </div>

      <div role="tabpanel">{tab === "summary" ? <SeasonSummaryView s={s} careerId={careerId} /> : <TrophyRoom tr={trophies} />}</div>

      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        onConfirm={() => void advance()}
        loading={busy}
        title={t("season.advance.confirmTitle", { next: s.number + 1 })}
        body={t("season.advance.confirmBody", { year: String(s.year), n: expiring })}
        confirmLabel={t("season.advance.confirm")}
      />
    </div>
  );
}

export default function SeasonPage() {
  return (
    <Suspense fallback={<PageSkeleton rows={3} />}>
      <SeasonScreen />
    </Suspense>
  );
}
