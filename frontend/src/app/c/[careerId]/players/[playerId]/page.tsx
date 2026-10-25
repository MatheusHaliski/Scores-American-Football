"use client";
import { useParams } from "next/navigation";
import { api } from "@/lib/api";
import { POSITIONS, type Position } from "@/lib/ratings/overall";
import { useQuery } from "@/lib/hooks/useQuery";
import { fromCents, iso2, orDash } from "@/lib/career/format";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { PageHeader } from "@/components/shell/PageHeader";
import { MacroRow, TierChip } from "@/components/roster/PlayerCardCompact";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";
import { ErrorState, PageSkeleton } from "@/components/ui/States";

function attrTone(v: number): string {
  return v >= 85 ? "bg-led" : v >= 75 ? "bg-good" : v >= 60 ? "bg-warn" : "bg-bad/80";
}

/** Página simples do jogador da carreira (RF22): card, atributos agrupados, overall por posição, contrato e histórico. */
export default function PlayerPage() {
  const t = useT("roster");
  const td = useTd();
  const f = useFormat();
  const { careerId, playerId } = useParams<{ careerId: string; playerId: string }>();
  const q = useQuery(() => api.roster.player(careerId, playerId), [careerId, playerId]);

  if (q.loading) return <PageSkeleton rows={2} />;
  if (q.error || !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const p = q.data;
  const c = p.card;
  const contract = p.contract;
  const byPos = POSITIONS.filter((pos) => p.overallByPosition[pos] != null);

  return (
    <div className="flex flex-col gap-4">
      <PageHeader eyebrow={t("player.eyebrow")} title={c.name} back={{ href: `/c/${careerId}/roster`, label: t("player.back") }} />

      <section className="flex flex-wrap items-center gap-4 rounded-[var(--radius-xl)] border border-border bg-arena-2 p-4">
        <TierChip tier={c.tier} overall={c.overall} size="lg" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge tone="led">{c.position}</Badge>
            {c.secondaryPosition && <Badge>{t("card.secondary", { pos: c.secondaryPosition })}</Badge>}
            <Badge tone="wood">{td("enums", `tier.${c.tier}`)}</Badge>
            <Badge>{td("enums", `rosterStatus.${c.rosterStatus}`)}</Badge>
            {c.injuryWeeksOut > 0 && <Badge tone="bad">{t("card.injured", { n: c.injuryWeeksOut })}</Badge>}
          </div>
          <p className="mt-1 text-text-muted">
            #{c.jerseyNumber} · {td("enums", `position.${c.position}`)} · {t("player.bio", { age: c.age, height: c.heightCm, weight: c.weightKg })}
          </p>
          <p className="text-[0.85rem] text-text-dim">{p.ownPlayer ? (p.clubName ?? "—") : t("player.freeAgent")}</p>
        </div>
        <div className="w-full sm:w-80">
          <MacroRow family={c.family} macros={c.macros} />
        </div>
      </section>

      <div className="grid gap-4 lg:grid-cols-3 [&>*]:min-w-0">
        <Card title={t("player.profile")}>
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-[0.88rem]">
            <dt className="text-text-muted">{t("player.nationality")}</dt>
            <dd className="text-right">{p.nationality ? f.region(iso2(p.nationality)) : "—"}</dd>
            <dt className="text-text-muted">{t("player.birthDate")}</dt>
            <dd className="text-right">{p.birthDate ? f.dateNumeric(`${p.birthDate}T12:00:00Z`) : "—"}</dd>
            <dt className="text-text-muted">{t("player.college")}</dt>
            <dd className="truncate text-right" title={p.college ?? undefined}>{p.college ?? "—"}</dd>
            <dt className="text-text-muted">{t("player.experience")}</dt>
            <dd className="text-right">{t("card.exp", { n: c.experienceYears })}</dd>
            <dt className="text-text-muted">{t("player.potential")}</dt>
            <dd className="text-right tabular">{p.potential}</dd>
            <dt className="text-text-muted">{t("player.condition")}</dt>
            <dd className="text-right tabular">{c.condition}</dd>
            <dt className="text-text-muted">{t("player.morale")}</dt>
            <dd className="text-right tabular">{c.morale}</dd>
            <dt className="text-text-muted">{t("player.depth")}</dt>
            <dd className="text-right">{c.depthChart.length ? c.depthChart.join(", ") : "—"}</dd>
          </dl>
          {p.irPlacedWeek != null && <p className="mt-3 text-[0.85rem] text-warn">{t("player.ir", { week: p.irPlacedWeek, back: orDash(p.irEarliestReturnWeek) })}</p>}
        </Card>

        <Card title={t("player.byPosition")} className="lg:col-span-2">
          <p className="mb-2 text-[0.78rem] text-text-dim">{t("player.byPositionHint")}</p>
          <ul className="grid grid-cols-4 gap-1.5 sm:grid-cols-5 md:grid-cols-10">
            {byPos.map((pos: Position) => {
              const v = p.overallByPosition[pos]!;
              return (
                <li key={pos} className={cn("rounded-[var(--radius-sm)] border px-1 py-1 text-center", pos === c.position ? "border-led bg-orange/10" : pos === c.secondaryPosition ? "border-rim" : "border-border bg-pitch")} title={td("enums", `position.${pos}`)}>
                  <div className="font-heading text-[0.62rem] uppercase tracking-[0.08em] text-text-dim">{pos}</div>
                  <div className="font-heading tabular">{v}</div>
                </li>
              );
            })}
          </ul>
        </Card>

        <Card title={t("player.attributes")} className="lg:col-span-2">
          <div className="grid gap-4 sm:grid-cols-2">
            {Object.entries(p.attributes).map(([group, attrs]) => (
              <section key={group} aria-label={td("enums", `attrGroup.${group}`)}>
                <h4 className="mb-1.5 font-heading text-[0.72rem] uppercase tracking-[0.14em] text-led">{td("enums", `attrGroup.${group}`)}</h4>
                <ul className="grid gap-1">
                  {Object.entries(attrs).map(([k, v]) => (
                    <li key={k} className="grid grid-cols-[minmax(0,1fr)_2.2rem] items-center gap-2 text-[0.82rem]">
                      <span className="min-w-0">
                        <span className="block truncate text-text-soft">{td("enums", `attr.${k}`)}</span>
                        <span className="mt-0.5 block h-1.5 overflow-hidden rounded-full bg-arena-4">
                          <span className={cn("block h-full rounded-full", attrTone(v))} style={{ width: `${Math.max(0, Math.min(100, v))}%` }} />
                        </span>
                      </span>
                      <span className="text-right font-heading tabular">{v}</span>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        </Card>

        <div className="grid content-start gap-4">
          <Card title={t("player.contract")}>
            {contract ? (
              <div className="grid gap-3">
                <div className="flex flex-wrap gap-1.5">
                  <Badge tone="wood">{td("enums", `contractType.${contract.type}`)}</Badge>
                  <Badge>{t("player.contract.years", { start: contract.startSeason, end: contract.endSeason })}</Badge>
                  {contract.franchiseTag && <Badge tone="led">{t("player.contract.tag")}</Badge>}
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[16rem] text-[0.8rem]">
                    <thead>
                      <tr className="font-heading text-[0.62rem] uppercase tracking-[0.1em] text-text-dim">
                        <th className="pb-1 text-left font-normal">{t("player.contract.season")}</th>
                        <th className="pb-1 text-right font-normal">{t("player.contract.base")}</th>
                        <th className="pb-1 text-right font-normal">{t("player.contract.capHit")}</th>
                        <th className="pb-1 text-right font-normal">{t("player.contract.dead")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {contract.capHitByYear.map((hit, i) => (
                        <tr key={i} className="border-t border-border">
                          <td className="py-1">{contract.startSeason + i}</td>
                          <td className="py-1 text-right tabular">{f.money(fromCents(contract.baseSalaryByYear[i] ?? 0))}</td>
                          <td className="py-1 text-right tabular">{f.money(fromCents(hit))}</td>
                          <td className="py-1 text-right tabular">{f.money(fromCents(contract.deadMoneyIfCutByYear[i] ?? 0))}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="text-[0.8rem] text-text-muted">
                  {t("player.contract.bonus", { value: f.money(fromCents(contract.signingBonus)) })} · {t("player.contract.total", { value: f.money(fromCents(contract.totalValue)) })}
                </p>
                {p.ownPlayer && (
                  <p className="text-[0.82rem] text-warn">
                    {t("player.deadNow", { value: f.money(fromCents(p.deadMoneyIfReleased)) })}
                    {p.deadMoneyNextSeasonIfReleased > 0 && ` ${t("player.deadNext", { value: f.money(fromCents(p.deadMoneyNextSeasonIfReleased)) })}`}
                  </p>
                )}
              </div>
            ) : (
              <p className="text-sm text-text-muted">{t("player.contract.none")}</p>
            )}
          </Card>

          <Card title={t("player.history")}>
            {p.history.length ? (
              <ul className="grid gap-1 text-[0.85rem]">
                {p.history.map((m) => (
                  <li key={m.id}>
                    {t("player.history.row", { week: m.week, kind: td("enums", `moveKind.${m.kind}`), from: m.fromStatus ? td("enums", `rosterStatus.${m.fromStatus}`) : "—", to: m.toStatus ? td("enums", `rosterStatus.${m.toStatus}`) : "—" })}
                    {m.deadMoney > 0 && <span className="text-text-dim"> · {t("release.dead")} {f.money(fromCents(m.deadMoney))}</span>}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-text-muted">{t("player.history.none")}</p>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
