"use client";
import Link from "next/link";
import type { ClubRef } from "@/lib/api/career-types";
import type { Award, SeasonAdvanceResult, SeasonSummary, Trophies } from "@/lib/api/season-types";
import { AWARD_TYPES, LEADER_CATEGORIES } from "@/lib/api/season-types";
import { fromCents, orDash } from "@/lib/career/format";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { ClubBadge } from "@/components/career/ClubBadge";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";
import { Stat } from "@/components/ui/Stat";
import { EmptyState, InlineAlert } from "@/components/ui/States";
import { IconShield, IconStar, IconTrophy } from "@/components/ui/icons";

const RESULT_TONE: Record<string, BadgeTone> = { CHAMPION: "led", RUNNER_UP: "good", CONFERENCE: "info", DIVISIONAL: "info", WILD_CARD: "info", PRELIMINARY: "info", MISSED: "bad", IN_PROGRESS: "neutral" };
const STEP_TONE: Record<string, BadgeTone> = { DONE: "good", SKIPPED: "warn", PENDING: "neutral" };

function ClubLine({ club, label, highlight }: { club: ClubRef | null; label: string; highlight?: boolean }) {
  const t = useT("season");
  return (
    <div className={cn("flex min-w-0 items-center gap-3 rounded-[var(--radius-lg)] border bg-arena-2 p-3", highlight ? "border-2 border-led shadow-led" : "border-border")}>
      {club ? <ClubBadge club={club} size={44} /> : <span className="grid size-11 place-items-center rounded-[28%] border border-dashed border-border-strong text-text-dim">?</span>}
      <div className="min-w-0">
        <div className="eyebrow">{label}</div>
        <div className="truncate font-heading text-lg font-semibold">{club?.name ?? t("season.tbd")}</div>
      </div>
    </div>
  );
}

function AwardCard({ a, careerId }: { a: Award; careerId: string }) {
  const td = useTd();
  const f = useFormat();
  const stats = Object.entries(a.statLine).filter(([k]) => k !== "games");
  return (
    <li className="min-w-0 rounded-[var(--radius-md)] border border-border bg-pitch/60 p-3">
      <div className="mb-1 flex items-center gap-2">
        {a.type === "MVP" ? <IconTrophy size={16} className="text-led" /> : <IconStar size={16} className="text-wood-light" />}
        <span className="font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-muted">{td("season", `award.${a.type}`)}</span>
      </div>
      <div className="flex min-w-0 items-center gap-2">
        {a.club && <ClubBadge club={a.club} size={26} />}
        <div className="min-w-0">
          {a.playerId ? (
            <Link href={`/c/${careerId}/players/${a.playerId}`} className="block truncate font-semibold hover:text-led">{a.name}</Link>
          ) : (
            <span className="block truncate font-semibold">{a.name}</span>
          )}
          <span className="block truncate text-[0.72rem] text-text-dim">{[a.position, a.club?.shortName].filter(Boolean).join(" · ")}</span>
        </div>
      </div>
      <dl className="mt-2 grid grid-cols-2 gap-x-3 gap-y-0.5 text-[0.75rem]">
        <dt className="text-text-dim">{td("season", "stat.games")}</dt>
        <dd className="text-right tabular">{a.statLine.games ?? "—"}</dd>
        {stats.map(([k, v]) => (
          <div key={k} className="contents">
            <dt className="truncate text-text-dim">{td("season", `stat.${k}`)}</dt>
            <dd className="text-right tabular">{f.num(v)}</dd>
          </div>
        ))}
      </dl>
    </li>
  );
}

/** Resumo da temporada (RF35): campeão e vice, a campanha do usuário, prêmios (provisórios até a final), líderes e a entressafra. */
export function SeasonSummaryView({ s, careerId }: { s: SeasonSummary; careerId: string }) {
  const t = useT("season");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const c = s.campaign;
  const awards = [...s.awards].sort((a, b) => AWARD_TYPES.indexOf(a.type as (typeof AWARD_TYPES)[number]) - AWARD_TYPES.indexOf(b.type as (typeof AWARD_TYPES)[number]));
  const categories = [...LEADER_CATEGORIES.filter((k) => k in s.leaders), ...Object.keys(s.leaders).filter((k) => !(LEADER_CATEGORIES as readonly string[]).includes(k))];
  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <ClubLine club={s.champion} label={t("season.champion")} highlight={!!s.champion} />
        <ClubLine club={s.runnerUp} label={t("season.runnerUp")} />
      </div>

      {c && (
        <Card title={t("season.campaign.title")} action={c.playoffResult ? <Badge tone={RESULT_TONE[c.playoffResult] ?? "neutral"}>{td("season", `result.${c.playoffResult}`)}</Badge> : undefined}>
          <div className="grid grid-cols-2 gap-0.5 overflow-hidden rounded-md border border-arena-3 bg-pitch sm:grid-cols-3 lg:grid-cols-6">
            <Stat value={c.record.summary} label={t("season.campaign.record")} led />
            <Stat value={f.num(c.pointsFor)} label={t("season.campaign.pf")} />
            <Stat value={f.num(c.pointsAgainst)} label={t("season.campaign.pa")} />
            <Stat value={orDash(c.divisionRank, (n) => tc("ordinal", { n }))} label={t("season.campaign.divisionRank")} />
            <Stat value={orDash(c.conferenceRank, (n) => tc("ordinal", { n }))} label={t("season.campaign.conferenceRank")} />
            <Stat value={orDash(c.seed)} label={t("season.campaign.seed")} />
          </div>
        </Card>
      )}

      <Card title={t("season.awards.title")} action={s.provisionalAwards ? <Badge tone="warn">{t("season.inProgress")}</Badge> : <Badge tone="good">{t("season.completed")}</Badge>}>
        {s.provisionalAwards && awards.length > 0 && <InlineAlert tone="warn" className="mb-3">{t("season.awards.provisional")}</InlineAlert>}
        {awards.length ? (
          <ul className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">{awards.map((a) => <AwardCard key={a.type} a={a} careerId={careerId} />)}</ul>
        ) : (
          <EmptyState compact icon={<IconStar size={22} />} title={t("season.awards.empty")} />
        )}
      </Card>

      <Card title={t("season.leaders.title")}>
        {categories.some((k) => s.leaders[k]?.length) ? (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {categories.map((k) => (
              <section key={k} aria-label={td("season", `leader.${k}`)} className="min-w-0">
                <h4 className="mb-1 font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-muted">{td("season", `leader.${k}`)}</h4>
                <ol className="grid gap-0.5">
                  {(s.leaders[k] ?? []).map((l, i) => (
                    <li key={l.playerId} className={cn("flex min-w-0 items-center gap-2 text-[0.82rem]", l.club?.id === s.userClubId && "text-led")}>
                      <span className="w-4 shrink-0 text-right tabular text-text-dim">{i + 1}</span>
                      {l.club && <ClubBadge club={l.club} size={18} />}
                      <Link href={`/c/${careerId}/players/${l.playerId}`} className="min-w-0 flex-1 truncate hover:text-led">{l.name}</Link>
                      <span className="shrink-0 text-[0.7rem] text-text-dim">{l.position}</span>
                      <span className="w-12 shrink-0 text-right font-heading tabular">{f.num(l.value)}</span>
                    </li>
                  ))}
                </ol>
              </section>
            ))}
          </div>
        ) : (
          <p className="text-sm text-text-muted">{t("season.leaders.empty")}</p>
        )}
      </Card>

      {s.offseason && (
        <Card title={t("season.offseason.title")}>
          <ol className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
            {s.offseason.steps.map((st, i) => (
              <li key={st.phase} className="min-w-0 rounded-[var(--radius-md)] border border-border bg-pitch/60 p-2.5">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-heading font-semibold">{i + 1}. {td("season", `step.${st.phase}`)}</span>
                  <Badge size="xs" tone={STEP_TONE[st.status] ?? "neutral"}>{td("season", `stepStatus.${st.status}`)}</Badge>
                </div>
                <div className="mt-0.5 text-[0.72rem] text-text-dim">{td("season", `handledBy.${st.handledBy}`)}</div>
              </li>
            ))}
          </ol>
          <h4 className="mb-1 font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-muted">{t("season.expiring.title", { n: s.offseason.expiringContracts.length })}</h4>
          {s.offseason.expiringContracts.length ? (
            <ul className="grid gap-x-6 gap-y-0.5 sm:grid-cols-2 xl:grid-cols-3">
              {s.offseason.expiringContracts.map((x) => (
                <li key={x.playerId} className="flex min-w-0 items-center gap-2 border-t border-border/60 py-1 text-[0.82rem]">
                  <span className="w-8 shrink-0 font-heading text-text-dim">{x.position}</span>
                  <Link href={`/c/${careerId}/players/${x.playerId}`} className="min-w-0 flex-1 truncate hover:text-led">{x.name}</Link>
                  <span className="shrink-0 tabular text-text-muted">{x.overall}</span>
                  <span className="w-20 shrink-0 whitespace-nowrap text-right tabular text-text-dim">{f.money(fromCents(x.capHit))}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-text-muted">{t("season.expiring.none")}</p>
          )}
        </Card>
      )}
    </div>
  );
}

/** Resultado do avanço (D-99): temporada nova, passos, contratos encerrados, quem evoluiu e regrediu, dead money levado. */
export function AdvanceResultView({ r, careerId }: { r: SeasonAdvanceResult; careerId: string }) {
  const t = useT("season");
  const td = useTd();
  const f = useFormat();
  return (
    <Card tone="orange" eyebrow={t("season.eyebrow")} title={t("season.advance.resultTitle", { n: r.seasonNumber })}>
      <p className="mb-1 text-[0.9rem] text-text-soft">{t("season.advance.done", { n: r.seasonNumber, year: String(r.seasonYear) })}</p>
      <p className="mb-3 text-[0.85rem] text-text-muted">{t("season.advance.aiRenewalsLine", { n: r.aiRenewals })}</p>
      <div className="grid grid-cols-2 gap-0.5 overflow-hidden rounded-md border border-arena-3 bg-pitch sm:grid-cols-3 lg:grid-cols-5">
        <Stat value={f.num(r.aiRenewals)} label={t("season.advance.aiRenewals")} />
        <Stat value={f.num(r.expiredContracts)} label={t("season.advance.expired")} />
        <Stat value={f.num(r.improved)} label={t("season.advance.improved")} led />
        <Stat value={f.num(r.declined)} label={t("season.advance.declined")} />
        <Stat value={f.money(fromCents(r.deadMoneyCarried))} label={t("season.advance.deadMoney")} />
      </div>
      <ol className="mt-3 flex flex-wrap gap-1.5">
        {r.steps.map((st) => (
          <li key={st.phase}>
            <Badge tone={STEP_TONE[st.status] ?? "neutral"}>{td("season", `step.${st.phase}`)} · {td("season", `stepStatus.${st.status}`)}</Badge>
          </li>
        ))}
      </ol>
      {r.userExpiredContracts.length > 0 && (
        <div className="mt-3">
          <h4 className="mb-1 font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-muted">{t("season.advance.userExpired", { n: r.userExpiredContracts.length })}</h4>
          <p className="mb-1 text-[0.78rem] text-text-dim">{t("season.advance.userExpiredHint")}</p>
          <ul className="grid gap-x-6 gap-y-0.5 sm:grid-cols-2 xl:grid-cols-3">
            {r.userExpiredContracts.map((x) => (
              <li key={x.playerId} className="flex min-w-0 items-center gap-2 border-t border-border/60 py-1 text-[0.82rem]">
                <span className="w-8 shrink-0 font-heading text-text-dim">{x.position}</span>
                <Link href={`/c/${careerId}/players/${x.playerId}`} className="min-w-0 flex-1 truncate hover:text-led">{x.name}</Link>
                <span className="shrink-0 tabular text-text-muted">{x.overall}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

/** Sala de troféus (RF35/RF17): a contagem do usuário, os títulos da liga por temporada e o histórico da carreira. */
export function TrophyRoom({ tr }: { tr: Trophies }) {
  const t = useT("season");
  const td = useTd();
  const mine = tr.trophies.filter((x) => x.userClub);
  const bySeason = new Map<number, typeof tr.trophies>();
  for (const x of tr.trophies) bySeason.set(x.seasonNumber, [...(bySeason.get(x.seasonNumber) ?? []), x]);
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-0.5 overflow-hidden rounded-[var(--radius-lg)] border border-arena-3 bg-pitch sm:grid-cols-4">
        <Stat value={tr.championships} label={t("trophies.championships")} led />
        <Stat value={tr.conferenceTitles} label={t("trophies.conferenceTitles")} />
        <Stat value={tr.divisionTitles} label={t("trophies.divisionTitles")} />
        <Stat value={tr.playoffAppearances} label={t("trophies.playoffAppearances")} />
      </div>
      <Card title={t("trophies.mine")}>
        {mine.length ? (
          <ul className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
            {mine.map((x) => (
              <li key={`${x.seasonId}-${x.kind}`} className="flex min-w-0 items-center gap-3 rounded-[var(--radius-md)] border border-led/50 bg-orange/10 p-3">
                {x.kind === "CHAMPIONSHIP" ? <IconTrophy size={28} className="text-led" /> : <IconShield size={26} className="text-wood-light" />}
                <div className="min-w-0">
                  <div className="truncate font-heading font-semibold">{td("season", `trophies.kind.${x.kind}`)}</div>
                  <div className="truncate text-[0.75rem] text-text-dim">{t("trophies.season", { n: x.seasonNumber, year: String(x.seasonYear) })}{x.kind === "DIVISION" && x.division ? ` · ${x.division}` : x.kind === "CONFERENCE" && x.conference ? ` · ${x.conference}` : ""}</div>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState compact icon={<IconTrophy size={22} />} title={t("trophies.empty")} />
        )}
      </Card>
      {bySeason.size > 0 && (
        <Card title={t("trophies.all")}>
          <div className="grid gap-4">
            {[...bySeason.entries()].sort((a, b) => b[0] - a[0]).map(([n, list]) => (
              <section key={n} aria-label={t("trophies.season", { n, year: String(list[0].seasonYear) })}>
                <h4 className="mb-1 font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-muted">{t("trophies.season", { n, year: String(list[0].seasonYear) })}</h4>
                <ul className="flex flex-wrap gap-1.5">
                  {list.map((x) => (
                    <li key={`${x.kind}-${x.club?.id}`} className={cn("flex items-center gap-1.5 rounded-[var(--radius-pill)] border px-2 py-1 text-[0.75rem]", x.userClub ? "border-led/60 bg-orange/10 text-led" : "border-border bg-arena-2")}>
                      {x.club && <ClubBadge club={x.club} size={18} />}
                      <span className="font-semibold">{x.club?.shortName}</span>
                      <span className="text-text-dim">{td("season", `trophies.kind.${x.kind}`)}</span>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        </Card>
      )}
      <Card title={t("trophies.history")}>
        <div className="overflow-x-auto">
          <table className="w-full text-[0.82rem]">
            <thead>
              <tr className="font-heading text-[0.66rem] uppercase tracking-[0.1em] text-text-dim">
                <th scope="col" className="pb-2 text-left font-normal">{t("trophies.history.season")}</th>
                <th scope="col" className="pb-2 text-left font-normal">{t("trophies.history.champion")}</th>
                <th scope="col" className="hidden pb-2 text-left font-normal sm:table-cell">{t("trophies.history.runnerUp")}</th>
                <th scope="col" className="hidden pb-2 text-left font-normal md:table-cell">{t("trophies.history.mvp")}</th>
                <th scope="col" className="pb-2 text-right font-normal">{t("trophies.history.record")}</th>
                <th scope="col" className="pb-2 text-right font-normal">{t("trophies.history.result")}</th>
              </tr>
            </thead>
            <tbody>
              {tr.seasons.map((x) => (
                <tr key={x.seasonId} className="border-t border-border">
                  <td className="py-1.5 tabular">{x.number} · {x.year}</td>
                  <td className="py-1.5">{x.champion ? <span className="flex min-w-0 items-center gap-1.5"><ClubBadge club={x.champion} size={18} /><span className="truncate">{x.champion.shortName}</span></span> : "—"}</td>
                  <td className="hidden py-1.5 sm:table-cell">{x.runnerUp ? <span className="flex min-w-0 items-center gap-1.5"><ClubBadge club={x.runnerUp} size={18} /><span className="truncate">{x.runnerUp.shortName}</span></span> : "—"}</td>
                  <td className="hidden py-1.5 md:table-cell">{x.mvp ? `${x.mvp.name} (${x.mvp.club?.shortName ?? "—"})` : "—"}</td>
                  <td className="py-1.5 text-right tabular">{x.userRecord?.summary ?? "—"}</td>
                  <td className="py-1.5 text-right">{x.userResult ? <Badge size="xs" tone={RESULT_TONE[x.userResult] ?? "neutral"}>{td("season", `result.${x.userResult}`)}</Badge> : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
