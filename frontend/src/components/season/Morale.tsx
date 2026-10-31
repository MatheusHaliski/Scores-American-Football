"use client";
import Link from "next/link";
import type { MoraleSummary, PlayerMorale, SquadMorale } from "@/lib/api/season-types";
import { MORALE_FACTORS, MORALE_LABELS } from "@/lib/api/season-types";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ProgressBar } from "@/components/ui/Stat";
import { IconArrowDown, IconArrowUp, IconChevronRight, IconHeart } from "@/components/ui/icons";

export const MORALE_TONE: Record<string, BadgeTone> = { HIGH: "good", GOOD: "good", NEUTRAL: "neutral", LOW: "warn", CRITICAL: "bad" };
const BAR_TONE: Record<string, "good" | "orange" | "warn" | "bad"> = { HIGH: "good", GOOD: "good", NEUTRAL: "orange", LOW: "warn", CRITICAL: "bad" };

function Trend({ trend }: { trend: string }) {
  const td = useTd();
  const label = td("season", `morale.trend.${trend}`);
  if (trend === "UP") return <span className="inline-flex items-center gap-0.5 text-[var(--good-text)]"><IconArrowUp size={14} /> {label}</span>;
  if (trend === "DOWN") return <span className="inline-flex items-center gap-0.5 text-[var(--bad-text)]"><IconArrowDown size={14} /> {label}</span>;
  return <span className="text-text-muted">{label}</span>;
}

/** Card da central (RF41): média, faixa, tendência, quantos estão em baixa e pedidos de troca; link para a moral por jogador. */
export function MoraleCard({ m, careerId }: { m: MoraleSummary | null; careerId: string }) {
  const t = useT("season");
  const td = useTd();
  return (
    <Card title={t("hub.morale.title")} action={<IconHeart size={18} className="text-text-dim" />}>
      {m ? (
        <div className="grid gap-3">
          <div className="flex items-end gap-3">
            <span className="font-display text-4xl leading-none tabular">{m.average}</span>
            <div className="pb-0.5">
              <Badge tone={MORALE_TONE[m.label] ?? "neutral"}>{td("season", `morale.label.${m.label}`)}</Badge>
              <div className="mt-1 text-[0.8rem]"><Trend trend={m.trend} /></div>
            </div>
          </div>
          <ProgressBar value={m.average} max={100} tone={BAR_TONE[m.label] ?? "orange"} label={t("morale.title")} />
          <ul className="grid gap-0.5 text-[0.85rem]">
            <li className={cn(m.low > 0 ? "text-[var(--warn-text)]" : "text-text-muted")}>{t("hub.morale.low", { n: m.low })}</li>
            <li className={cn(m.tradeRequests > 0 ? "text-[var(--bad-text)]" : "text-text-muted")}>{t("hub.morale.trades", { n: m.tradeRequests })}</li>
          </ul>
          <Button variant="ghost" size="sm" href={`/c/${careerId}/roster?tab=MORALE`} iconRight={<IconChevronRight size={16} />}>{t("hub.morale.players")}</Button>
        </div>
      ) : (
        <p className="text-sm text-text-muted">—</p>
      )}
    </Card>
  );
}

function PlayerRow({ p, careerId }: { p: PlayerMorale; careerId: string }) {
  const t = useT("season");
  const td = useTd();
  const f = useFormat();
  const share = (v: number | null) => (v == null ? "—" : f.pct(v));
  const factors = MORALE_FACTORS.filter((k) => (p.factors[k] ?? 0) !== 0);
  return (
    <li className="min-w-0 rounded-[var(--radius-md)] border border-border bg-arena-2 p-3">
      <div className="flex min-w-0 items-center gap-2">
        <span className="w-8 shrink-0 font-heading text-led">{p.position}</span>
        <Link href={`/c/${careerId}/players/${p.playerId}`} className="min-w-0 flex-1 truncate font-semibold hover:text-led">
          {p.name} <span className="text-[0.75rem] font-normal text-text-dim">#{p.jerseyNumber}</span>
        </Link>
        <span className="font-display text-2xl leading-none tabular">{p.morale}</span>
      </div>
      <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[0.75rem]">
        <Badge size="xs" tone={MORALE_TONE[p.label] ?? "neutral"}>{td("season", `morale.label.${p.label}`)}</Badge>
        <Badge size="xs">{td("season", `morale.role.${p.role}`)}</Badge>
        {p.rosterStatus === "PRACTICE_SQUAD" && <Badge size="xs" tone="info">{t("morale.practiceSquad")}</Badge>}
        {p.contractFinalYear && <Badge size="xs" tone="warn">{t("morale.contractFinal")}</Badge>}
        {p.tradeRequest && <Badge size="xs" tone="bad">{t("morale.tradeRequest")}</Badge>}
        <Trend trend={p.trend} />
        <span className="text-text-dim">{t("morale.delta", { value: p.lastDelta })}</span>
      </div>
      <dl className="mt-1.5 grid grid-cols-2 gap-x-3 gap-y-0.5 text-[0.75rem]">
        <dt className="text-text-dim">{t("morale.col.snaps")}</dt>
        <dd className="text-right tabular">{t("morale.snaps", { expected: share(p.expectedSnapShare), season: share(p.seasonSnapShare) })}</dd>
        <dt className="text-text-dim">{t("morale.col.contract")}</dt>
        <dd className="text-right">{t("morale.contractYears", { n: p.contractYearsLeft })}</dd>
      </dl>
      {factors.length > 0 && (
        <details className="mt-1.5 text-[0.75rem]">
          <summary className="cursor-pointer text-text-muted">{t("morale.factors")}</summary>
          <ul className="mt-1 grid grid-cols-2 gap-x-3">
            {factors.map((k) => (
              <li key={k} className="flex justify-between gap-2">
                <span className="text-text-dim">{td("season", `morale.factor.${k}`)}</span>
                <span className={cn("tabular", (p.factors[k] ?? 0) > 0 ? "text-[var(--good-text)]" : "text-[var(--bad-text)]")}>{f.num(p.factors[k] ?? 0, 1)}</span>
              </li>
            ))}
          </ul>
          <p className="mt-1 text-text-dim">{t("morale.performance", { value: p.performanceModifier })}</p>
        </details>
      )}
    </li>
  );
}

/** Moral por jogador (RF41, aba "Moral" do elenco, D-113): o resumo dos 53, a distribuição e cada jogador com papel, snaps e fatores. */
export function MoralePanel({ m, careerId }: { m: SquadMorale; careerId: string }) {
  const t = useT("season");
  const td = useTd();
  const total = MORALE_LABELS.reduce((a, k) => a + (m.distribution[k] ?? 0), 0) || 1;
  const players = [...m.players].sort((a, b) => Number(b.tradeRequest) - Number(a.tradeRequest) || a.morale - b.morale || a.name.localeCompare(b.name));
  return (
    <div className="grid gap-4">
      <p className="text-[0.85rem] text-text-muted">{t("morale.description", { week: m.throughWeek })}</p>
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={MORALE_TONE[m.label] ?? "neutral"} size="md">{t("morale.squad", { average: m.average, label: td("season", `morale.label.${m.label}`) })}</Badge>
        <span className="text-[0.85rem]"><Trend trend={m.trend} /></span>
        {m.teamStreak && <Badge>{t("morale.streak", { streak: m.teamStreak })}</Badge>}
        <Badge tone={m.tradeRequests ? "bad" : "neutral"}>{t("hub.morale.trades", { n: m.tradeRequests })}</Badge>
      </div>
      <section aria-label={t("morale.distribution")}>
        <h3 className="mb-1 font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-dim">{t("morale.distribution")}</h3>
        <div className="flex h-3 overflow-hidden rounded-full bg-arena-4" role="img" aria-label={MORALE_LABELS.map((k) => `${td("season", `morale.label.${k}`)} ${m.distribution[k] ?? 0}`).join(", ")}>
          {MORALE_LABELS.map((k) => (
            <div key={k} className={cn("h-full", k === "HIGH" ? "bg-good" : k === "GOOD" ? "bg-good/60" : k === "NEUTRAL" ? "bg-orange/70" : k === "LOW" ? "bg-warn" : "bg-bad")} style={{ width: `${((m.distribution[k] ?? 0) / total) * 100}%` }} />
          ))}
        </div>
        <ul className="mt-1 flex flex-wrap gap-x-3 text-[0.75rem] text-text-muted">
          {MORALE_LABELS.map((k) => <li key={k}>{td("season", `morale.label.${k}`)} {m.distribution[k] ?? 0}</li>)}
        </ul>
      </section>
      <ul className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">{players.map((p) => <PlayerRow key={p.playerId} p={p} careerId={careerId} />)}</ul>
    </div>
  );
}
