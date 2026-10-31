"use client";
import Link from "next/link";
import { useState } from "react";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import type { Analysis, AnalysisSummary, PlanAdvice } from "@/lib/api/season-types";
import { useNow } from "@/lib/hooks/useNow";
import { analysisSummaryText, unitLabel } from "@/lib/season/analysis";
import { seasonReaction } from "@/lib/season/errors";
import { useI18n, useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { ClubBadge } from "@/components/career/ClubBadge";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Dialog } from "@/components/ui/Dialog";
import { EmptyState, InlineAlert, Skeleton } from "@/components/ui/States";
import { toast } from "@/components/ui/Toast";
import { IconBolt, IconChevronRight, IconLoop, IconTarget, IconWhistle } from "@/components/ui/icons";

const VERDICT_TONE: Record<string, BadgeTone> = { FAVORITE: "good", EVEN: "warn", UNDERDOG: "bad" };

function PlanValue({ field, value }: { field: string; value: string }) {
  const td = useTd();
  if (field === "passRatio") {
    const pass = Number(value);
    return <>{Number.isFinite(pass) ? td("game", "plan.passRatio.value", { pass, run: 100 - pass }) : value}</>;
  }
  if (field === "defensiveFocus") return <>{td("enums", `defensiveFocus.${value}`)}</>;
  if (field === "fourthDownAggression") return <>{td("enums", `aggression.${value}`)}</>;
  return <>{value}</>;
}

function AdviceLine({ a }: { a: PlanAdvice }) {
  const td = useTd();
  return (
    <li className="rounded-[var(--radius-md)] border border-border bg-pitch/60 p-2.5 text-[0.85rem]">
      <div className="font-heading font-semibold">{td("season", `analysis.field.${a.field}`)}</div>
      <div className="tabular text-text-soft">
        <PlanValue field={a.field} value={a.current} /> → <span className="text-led"><PlanValue field={a.field} value={a.recommended} /></span>
      </div>
      <div className="text-[0.75rem] text-text-dim">{td("season", `analysis.reason.${a.reason}`)}</div>
    </li>
  );
}

/** Relatório completo do analista (D-104): unidades contra a liga, pontos fortes e fracos, próximos 3 jogos e as recomendações. */
export function AnalystReport({ a, careerId }: { a: Analysis; careerId: string }) {
  const t = useT("season");
  const td = useTd();
  const f = useFormat();
  const { locale, pseudo } = useI18n();
  const max = Math.max(99, ...a.units.map((u) => Math.max(u.rating, u.leagueAverage)));
  return (
    <div className="flex flex-col gap-3">
      <p className="text-[0.95rem] leading-relaxed text-text-soft">{analysisSummaryText(a, locale, pseudo)}</p>
      <div className="flex flex-wrap gap-1.5">
        <Badge tone="led">{t("analysis.strength", { n: a.strength })}</Badge>
        <Badge>{t("analysis.league", { n: a.leagueStrength })}</Badge>
        <Badge size="sm">{t("analysis.generated", { week: a.week, when: f.dateTime(a.generatedAt) })}</Badge>
      </div>

      <div className="grid gap-3 md:grid-cols-2 [&>*]:min-w-0">
        <section className="rounded-[var(--radius-lg)] border border-border bg-pitch/60 p-3">
          <h3 className="mb-2 font-heading text-[0.72rem] uppercase tracking-[0.16em] text-text-dim">{t("analysis.units.title")}</h3>
          <ul className="grid gap-1.5">
            {a.units.map((u) => (
              <li key={u.unit} className="grid grid-cols-[minmax(0,8rem)_minmax(0,1fr)_3rem] items-center gap-2 text-[0.8rem]">
                <span className="truncate capitalize">{unitLabel(u.unit, locale, pseudo)}</span>
                <div className="relative h-2 rounded-full bg-arena-4" role="img" aria-label={`${unitLabel(u.unit, locale, pseudo)}: ${u.rating} × ${u.leagueAverage}`}>
                  <div className={cn("absolute inset-y-0 left-0 rounded-full", u.diff >= 3 ? "bg-good" : u.diff <= -3 ? "bg-bad" : "bg-orange")} style={{ width: `${(u.rating / max) * 100}%` }} />
                  <div className="absolute inset-y-[-3px] w-0.5 bg-line" style={{ left: `${(u.leagueAverage / max) * 100}%` }} />
                </div>
                <span className={cn("text-right tabular", u.diff > 0 ? "text-[var(--good-text)]" : u.diff < 0 ? "text-[var(--bad-text)]" : "text-text-muted")}>{u.diff > 0 ? `+${u.diff}` : u.diff}</span>
              </li>
            ))}
          </ul>
        </section>
        <section className="grid gap-3 rounded-[var(--radius-lg)] border border-border bg-pitch/60 p-3">
          {([
            ["analysis.strengths", a.strengths, "good"],
            ["analysis.weaknesses", a.weaknesses, "bad"],
          ] as const).map(([key, list, tone]) => (
            <div key={key}>
              <h3 className="mb-1 font-heading text-[0.72rem] uppercase tracking-[0.16em] text-text-dim">{t(key)}</h3>
              {list.length ? (
                <ul className="flex flex-wrap gap-1.5">
                  {list.map((u) => <li key={u.unit}><Badge tone={tone}>{unitLabel(u.unit, locale, pseudo)} {u.diff > 0 ? `+${u.diff}` : u.diff}</Badge></li>)}
                </ul>
              ) : (
                <p className="text-[0.8rem] text-text-muted">{t("analysis.noFindings")}</p>
              )}
            </div>
          ))}
        </section>
      </div>

      <section>
        <h3 className="mb-2 font-heading text-[0.72rem] uppercase tracking-[0.16em] text-text-dim">{t("analysis.upcoming.title")}</h3>
        {a.upcoming.length ? (
          <ul className="grid gap-2 lg:grid-cols-3">
            {a.upcoming.map((o) => (
              <li key={o.fixtureId} className="min-w-0 rounded-[var(--radius-md)] border border-border bg-arena-2 p-3">
                <div className="flex items-center gap-2">
                  <ClubBadge club={o.opponent} size={30} />
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-heading font-semibold">{o.opponent.name}</div>
                    <div className="text-[0.72rem] text-text-dim">{t("analysis.upcoming.week", { week: o.week })} · {o.neutralSite ? t("analysis.neutral") : o.home ? t("analysis.home") : t("analysis.away")}</div>
                  </div>
                  <Badge size="xs" tone={VERDICT_TONE[o.verdict] ?? "neutral"}>{td("season", `analysis.verdict.${o.verdict}`)}</Badge>
                </div>
                <div className="mt-2 flex items-center justify-between text-[0.8rem]">
                  <span className="font-heading text-led">{t("analysis.winChance", { chance: o.winProbability })}</span>
                  <span className="tabular text-text-muted">{t("analysis.edge", { ours: o.strength, theirs: o.opponentStrength })}</span>
                </div>
                {o.biggestThreat && <div className="mt-1 text-[0.75rem] text-[var(--warn-text)]">{t("analysis.threat", { unit: unitLabel(o.biggestThreat, locale, pseudo) })}</div>}
                <ul className="mt-2 grid gap-0.5 text-[0.75rem]">
                  {o.matchups.map((m) => (
                    <li key={m.key} className="flex items-center justify-between gap-2">
                      <span className="min-w-0 truncate text-text-muted">{td("season", `analysis.matchup.${m.key}`)}</span>
                      <span className={cn("shrink-0 tabular", m.advantage === "OURS" ? "text-[var(--good-text)]" : m.advantage === "THEIRS" ? "text-[var(--bad-text)]" : "text-text-soft")}>{m.edge > 0 ? `+${m.edge}` : m.edge}</span>
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-[0.85rem] text-text-muted">{t("analysis.upcoming.none")}</p>
        )}
      </section>

      <div className="grid gap-3 md:grid-cols-2 [&>*]:min-w-0">
        <section className="rounded-[var(--radius-lg)] border border-border bg-pitch/60 p-3">
          <div className="mb-2 flex items-center justify-between gap-2">
            <h3 className="font-heading text-[0.72rem] uppercase tracking-[0.16em] text-text-dim">{t("analysis.depth.title")}</h3>
            <Link href={`/c/${careerId}/depth-chart`} className="inline-flex items-center gap-1 text-[0.78rem] text-led underline-offset-2 hover:underline"><IconTarget size={14} /> {t("analysis.goDepth")}</Link>
          </div>
          {a.depthChart.length ? (
            <ul className="grid gap-1 text-[0.85rem]">
              {a.depthChart.map((d) => (
                <li key={`${d.slot}-${d.inPlayerId}`}>{t("analysis.depth.item", { slot: td("enums", `slotShort.${d.slot}`), in: d.inName, inOverall: d.inOverall, out: d.outName, outOverall: d.outOverall, gain: d.gain })}</li>
              ))}
            </ul>
          ) : (
            <p className="text-[0.85rem] text-text-muted">{t("analysis.depth.none")}</p>
          )}
        </section>
        <section className="rounded-[var(--radius-lg)] border border-border bg-pitch/60 p-3">
          <div className="mb-2 flex items-center justify-between gap-2">
            <h3 className="font-heading text-[0.72rem] uppercase tracking-[0.16em] text-text-dim">{t("analysis.plan.title")}</h3>
            <Link href={`/c/${careerId}/playbook`} className="inline-flex items-center gap-1 text-[0.78rem] text-led underline-offset-2 hover:underline"><IconWhistle size={14} /> {t("analysis.goPlaybook")}</Link>
          </div>
          {a.gamePlan.length ? <ul className="grid gap-2">{a.gamePlan.map((p) => <AdviceLine key={p.field} a={p} />)}</ul> : <p className="text-[0.85rem] text-text-muted">{t("analysis.plan.none")}</p>}
        </section>
      </div>
    </div>
  );
}

/**
 * Analista na central (D-112): o resumo do último relatório (`dashboard.analysis`), a chance no próximo jogo, "Atualizar análise"
 * (`POST /analysis/refresh`, com o 429 virando aviso de aguardar e o botão travado até lá) e o relatório completo num diálogo.
 */
export function AnalystCard({ careerId, summary, className }: { careerId: string; summary: AnalysisSummary | null; className?: string }) {
  const t = useT("season");
  const f = useFormat();
  const { locale, pseudo } = useI18n();
  const [report, setReport] = useState<Analysis | null>(null);
  const [current, setCurrent] = useState<AnalysisSummary | null>(summary);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [missing, setMissing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retryUntil, setRetryUntil] = useState<number | null>(null);
  // o relógio só anda enquanto houver espera do 429; ao zerar, o botão volta (o próximo pedido limpa a espera)
  const now = useNow(retryUntil != null, 1000);
  const waitSeconds = retryUntil != null ? Math.max(0, Math.ceil((retryUntil - now) / 1000)) : 0;
  const [lastSummary, setLastSummary] = useState(summary);
  if (summary !== lastSummary) {
    setLastSummary(summary);
    setCurrent(summary);
  }

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setReport(await api.analysis.get(careerId));
      setMissing(false);
    } catch (e) {
      if (seasonReaction(e).kind === "noAnalysis") setMissing(true);
      else setError(describeError(e));
    } finally {
      setLoading(false);
    }
  };
  const openReport = () => {
    setOpen(true);
    if (!report) void load();
  };
  const refresh = async () => {
    setBusy(true);
    setError(null);
    setRetryUntil(null);
    try {
      const a = await api.analysis.refresh(careerId);
      setReport(a);
      setMissing(false);
      setCurrent({ id: a.id, week: a.week, generatedAt: a.generatedAt, summaryCode: a.summaryCode, summary: analysisSummaryText(a, locale, pseudo), nextWinProbability: a.upcoming[0]?.winProbability ?? null });
      toast(t("analysis.refreshed"), "good");
    } catch (e) {
      const r = seasonReaction(e);
      if (r.kind === "rateLimited") setRetryUntil(Date.now() + r.retryAfterSeconds * 1000);
      else setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  const refreshButton = (
    <Button variant="outline" size="sm" icon={<IconLoop size={16} />} onClick={() => void refresh()} loading={busy} disabled={waitSeconds > 0}>
      {t("analysis.refresh")}
    </Button>
  );
  const limit = waitSeconds > 0 && <InlineAlert tone="warn">{t("analysis.rateLimited", { seconds: waitSeconds })}</InlineAlert>;

  return (
    <Card eyebrow={t("analysis.eyebrow")} title={t("analysis.title")} action={<IconBolt size={18} className="text-text-dim" />} className={className}>
      {current ? (
        <div className="grid gap-2">
          <p className="text-[0.9rem] leading-snug text-text-soft">{current.summary}</p>
          <div className="flex flex-wrap items-center gap-1.5 text-[0.75rem] text-text-dim">
            {current.nextWinProbability != null && <Badge tone="led" size="xs">{t("hub.analysis.chance", { chance: current.nextWinProbability })}</Badge>}
            <span>{t("analysis.generated", { week: current.week, when: f.dateTime(current.generatedAt) })}</span>
          </div>
        </div>
      ) : (
        <p className="text-[0.85rem] text-text-muted">{t("hub.analysis.empty")}</p>
      )}
      {limit && <div className="mt-2">{limit}</div>}
      {error && !open && <InlineAlert tone="bad" className="mt-2">{error}</InlineAlert>}
      <div className="mt-3 flex flex-wrap gap-2">
        {refreshButton}
        <Button variant="ghost" size="sm" iconRight={<IconChevronRight size={16} />} onClick={openReport}>
          {t("analysis.open")}
        </Button>
      </div>

      <Dialog open={open} onClose={() => setOpen(false)} size="xl" eyebrow={t("analysis.eyebrow")} title={t("analysis.title")} footer={refreshButton}>
        {limit && <div className="mb-3">{limit}</div>}
        {error && <InlineAlert tone="bad" className="mb-3">{error}</InlineAlert>}
        {loading && !report ? (
          <div className="grid gap-3">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-40 w-full" />
          </div>
        ) : report ? (
          <AnalystReport a={report} careerId={careerId} />
        ) : missing ? (
          <EmptyState icon={<IconBolt size={24} />} title={t("analysis.none.title")} body={t("analysis.none.body")} />
        ) : null}
      </Dialog>
    </Card>
  );
}
