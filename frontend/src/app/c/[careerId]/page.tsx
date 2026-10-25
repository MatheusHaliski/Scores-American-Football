"use client";
import Link from "next/link";
import { useParams } from "next/navigation";
import { api } from "@/lib/api";
import type { CareerDashboard, ClubRef, Fixture } from "@/lib/api/types";
import { useQuery } from "@/lib/hooks/useQuery";
import { fromCents, orDash } from "@/lib/career/format";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { useCareer } from "@/store/career";
import { ClubBadge } from "@/components/career/ClubBadge";
import { TierChip } from "@/components/roster/PlayerCardCompact";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ProgressBar, Stat } from "@/components/ui/Stat";
import { EmptyState, ErrorState, PageSkeleton } from "@/components/ui/States";
import { IconAlert, IconCalendar, IconCheck, IconChevronRight, IconPill, IconTarget, IconUsers } from "@/components/ui/icons";

function NextGameCard({ d, careerId }: { d: CareerDashboard; careerId: string }) {
  const t = useT("career");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const next = d.nextGame;
  const me = d.club;
  const title = d.byeWeek ? t("hub.next.bye") : next ? tc("week.n", { n: next.fixture.week }) : t("hub.next.none");
  return (
    <Card eyebrow={t("hub.next.title")} title={title} tone="orange" className="lg:col-span-2" action={next ? <div className="flex flex-wrap justify-end gap-1">{next.fixture.divisional && <Badge tone="led" size="xs">{t("hub.next.divisional")}</Badge>}{next.fixture.conferenceGame && !next.fixture.divisional && <Badge size="xs">{t("hub.next.conference")}</Badge>}</div> : undefined}>
      {d.byeWeek && <p className="mb-3 text-[0.9rem] text-text-muted">{t("hub.next.byeBody")}</p>}
      {next && me ? (
        <div className="flex flex-col gap-4">
          {d.byeWeek && <div className="eyebrow">{tc("week.n", { n: next.fixture.week })}</div>}
          <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2 sm:gap-4">
            {[next.home ? me : next.opponent, null, next.home ? next.opponent : me].map((c, i) =>
              c ? (
                <div key={i} className="flex min-w-0 flex-col items-center gap-1 text-center">
                  <ClubBadge club={c} size={60} />
                  <span className="max-w-full truncate font-heading text-sm font-semibold leading-tight sm:text-base">{c.name}</span>
                  <span className="text-[0.7rem] uppercase tracking-[0.12em] text-text-dim">{i === 0 ? tc("home") : tc("away")}</span>
                </div>
              ) : (
                <div key={i} className="text-center">
                  <div className="font-display text-3xl text-led sm:text-5xl">{tc("vs")}</div>
                </div>
              ),
            )}
          </div>
          <div className="grid grid-cols-3 gap-0.5 overflow-hidden rounded-md border border-arena-3 bg-pitch">
            <Stat size="sm" value={f.dateShort(`${next.fixture.date}T12:00:00Z`)} label={td("enums", `kickoff.${next.fixture.kickoffSlot}`)} />
            <Stat size="sm" value={next.home ? t("hub.next.home") : t("hub.next.away")} label={t("hub.next.venue")} />
            <Stat size="sm" value={next.opponentRecord.summary} label={t("hub.next.opponentRecord")} />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="led" href={`/c/${careerId}/depth-chart`} icon={<IconTarget size={18} />} className="w-full sm:w-auto">
              {t("hub.next.depthChart")}
            </Button>
            <Button variant="ghost" size="sm" href={`/c/${careerId}/calendar`} icon={<IconCalendar size={16} />}>
              {t("hub.next.calendar")}
            </Button>
          </div>
        </div>
      ) : (
        !d.byeWeek && <EmptyState compact icon={<IconCalendar size={22} />} title={t("hub.next.none")} body={t("hub.next.noneBody")} />
      )}
    </Card>
  );
}

function DivisionCard({ d }: { d: CareerDashboard }) {
  const t = useT("career");
  const tc = useT("common");
  const f = useFormat();
  const clubs = new Map<string, ClubRef>(d.division.map((r) => [r.club.id, r.club]));
  if (d.club) clubs.set(d.club.id, d.club);
  const opp = (g: Fixture) => clubs.get(g.homeClubId === d.club?.id ? g.awayClubId : g.homeClubId);
  return (
    <Card eyebrow={d.club?.conference ?? undefined} title={d.club?.division ? t("hub.division.title", { name: d.club.division }) : t("hub.division.titleNone")}>
      {d.division.length ? (
        <table className="w-full text-[0.85rem]">
          <thead>
            <tr className="font-heading text-[0.68rem] uppercase tracking-[0.12em] text-text-dim">
              <th className="w-6 pb-2 text-left font-normal">#</th>
              <th className="pb-2 text-left font-normal">{t("hub.division.team")}</th>
              <th className="pb-2 text-right font-normal">{t("hub.division.record")}</th>
              <th className="pb-2 text-right font-normal">{t("hub.division.pf")}</th>
              <th className="pb-2 text-right font-normal">{t("hub.division.pa")}</th>
            </tr>
          </thead>
          <tbody>
            {d.division.map((r) => (
              <tr key={r.club.id} className={cn("border-t border-border", r.userClub && "text-led")}>
                <td className="py-1.5 tabular">{r.rank}</td>
                <td className="py-1.5">
                  <span className="flex min-w-0 items-center gap-2">
                    <ClubBadge club={r.club} size={22} />
                    <span className="truncate">{r.club.shortName}</span>
                  </span>
                </td>
                <td className="py-1.5 text-right tabular">{r.record.summary}</td>
                <td className="py-1.5 text-right tabular">{f.num(r.pointsFor)}</td>
                <td className="py-1.5 text-right tabular">{f.num(r.pointsAgainst)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="text-sm text-text-muted">—</p>
      )}
      {d.upcomingDivisionGames.length > 0 && (
        <div className="mt-4">
          <h4 className="mb-1 font-heading text-[0.7rem] uppercase tracking-[0.14em] text-text-dim">{t("hub.division.upcoming")}</h4>
          <ul className="grid gap-1">
            {d.upcomingDivisionGames.map((g) => {
              const o = opp(g);
              return (
                <li key={g.id} className="flex items-center gap-2 text-[0.85rem]">
                  <span className="w-10 shrink-0 font-heading text-text-dim">{tc("week.short", { n: g.week })}</span>
                  <span className="text-text-dim">{g.homeClubId === d.club?.id ? tc("vs") : tc("at")}</span>
                  {o && <ClubBadge club={o} size={20} />}
                  <span className="truncate">{o?.name ?? "—"}</span>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </Card>
  );
}

export default function FranchiseHubPage() {
  const t = useT("career");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const { careerId } = useParams<{ careerId: string }>();
  const setDashboard = useCareer((s) => s.setDashboard);
  const q = useQuery(async () => {
    const d = await api.careers.dashboard(careerId);
    setDashboard(d);
    return d;
  }, [careerId]);

  if (q.loading) return <PageSkeleton rows={3} />;
  if (q.error || !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const d = q.data;
  const club = d.club;
  const cap = d.cap;
  const dc = d.depthChart;

  return (
    <div className="flex flex-col gap-4 sm:gap-5">
      <section className="relative overflow-hidden rounded-[var(--radius-xl)] border border-border bg-arena-2 p-4 sm:p-6" style={club ? { backgroundImage: `linear-gradient(120deg, ${club.primaryColor}55, transparent 60%)` } : undefined}>
        <div className="eyebrow">{t("hub.eyebrow")}</div>
        <div className="mt-2 flex flex-col gap-4 sm:flex-row sm:items-center">
          <div className="flex min-w-0 flex-1 items-center gap-3 sm:gap-4">
            {club && <ClubBadge club={club} size={64} />}
            <div className="min-w-0">
              <h1 className="font-display text-2xl leading-tight break-words sm:text-4xl">{club?.name ?? d.career.name}</h1>
              <p className="text-[0.9rem] text-text-muted">
                {d.career.leagueName ?? "—"}
                {club?.division && ` · ${club.division}`}
              </p>
            </div>
          </div>
          <div className="grid shrink-0 grid-cols-2 gap-0.5 overflow-hidden rounded-md border border-arena-3 bg-pitch">
            <Stat value={d.record.summary} label={t("hub.record.title")} led />
            <Stat value={orDash(d.divisionRank, (n) => tc("ordinal", { n }))} label={t("hub.record.rank")} />
          </div>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-1.5">
          {d.career.seasonYear != null && <Badge tone="wood">{tc("season.n", { n: d.career.seasonYear })}</Badge>}
          <Badge>{t("hub.week", { week: d.currentWeek, total: d.career.regularSeasonWeeks })}</Badge>
          {d.career.seasonPhase && <Badge>{td("enums", `seasonPhase.${d.career.seasonPhase}`)}</Badge>}
        </div>
        {d.coach && <p className="mt-2 text-[0.85rem] text-text-soft">{t("hub.coach", { name: d.coach.name, style: d.coach.style ? td("enums", `coachStyle.${d.coach.style}`) : "—" })}</p>}
      </section>

      <div className="grid gap-4 sm:gap-5 lg:grid-cols-3 [&>*]:min-w-0">
        <NextGameCard d={d} careerId={careerId} />
        <DivisionCard d={d} />

        <Card title={t("hub.highlights.title")} action={<Button variant="ghost" size="sm" href={`/c/${careerId}/roster`} iconRight={<IconChevronRight size={16} />}>{t("hub.highlights.all")}</Button>}>
          {d.rosterHighlights.length ? (
            <ul className="grid gap-1">
              {d.rosterHighlights.map((p) => (
                <li key={p.id}>
                  <Link href={`/c/${careerId}/players/${p.id}`} className="-mx-1 flex items-center gap-3 rounded px-1 py-1.5 hover:bg-arena-3/60">
                    <TierChip tier={p.tier} overall={p.overall} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-text">{p.name}</span>
                      <span className="block text-[0.72rem] text-text-dim">
                        {p.position} · #{p.jerseyNumber}
                      </span>
                    </span>
                    <IconChevronRight size={16} className="text-text-dim" />
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState compact icon={<IconUsers size={22} />} title="—" />
          )}
        </Card>

        <Card title={t("hub.injuries.title")} action={<IconPill size={18} className="text-text-dim" />}>
          {d.injuries.length ? (
            <ul className="grid gap-1.5">
              {d.injuries.map((i) => (
                <li key={i.playerId} className="flex items-center gap-2 text-[0.9rem]">
                  <Link href={`/c/${careerId}/players/${i.playerId}`} className="min-w-0 flex-1 truncate hover:text-led">
                    {i.name} <span className="text-text-dim">· {i.position}</span>
                  </Link>
                  {i.rosterStatus === "IR" && <Badge tone="bad" size="xs">{td("enums", "rosterStatusShort.IR")}</Badge>}
                  <span className="shrink-0 text-[0.8rem] text-warn">{t("hub.injuries.weeks", { n: i.weeksOut })}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-text-muted">{t("hub.injuries.none")}</p>
          )}
        </Card>

        <Card title={t("hub.cap.title")}>
          {cap ? (
            <div className="grid gap-3">
              <ProgressBar value={cap.capUsed} max={cap.salaryCap} tone={cap.capSpace < 0 ? "bad" : cap.capUsed / cap.salaryCap > 0.97 ? "warn" : "good"} label={t("hub.cap.used")} />
              <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-[0.85rem]">
                <dt className="text-text-muted">{t("hub.cap.cap")}</dt>
                <dd className="text-right tabular">{f.money(fromCents(cap.salaryCap))}</dd>
                <dt className="text-text-muted">{t("hub.cap.used")}</dt>
                <dd className="text-right tabular">{f.money(fromCents(cap.capUsed))}</dd>
                <dt className="text-text-muted">{t("hub.cap.dead")}</dt>
                <dd className="text-right tabular">{f.money(fromCents(cap.deadMoney))}</dd>
                <dt className="font-semibold text-text">{t("hub.cap.space")}</dt>
                <dd className={cn("text-right font-semibold tabular", cap.capSpace < 0 ? "text-[var(--bad-text)]" : "text-led")}>{f.money(fromCents(cap.capSpace))}</dd>
              </dl>
            </div>
          ) : (
            <p className="text-sm text-text-muted">—</p>
          )}
        </Card>

        <Card title={t("hub.depth.title")} action={dc && <Badge size="xs">{t("hub.depth.version", { n: dc.version })}</Badge>}>
          {dc ? (
            <div className="grid gap-3">
              <div className={cn("flex items-center gap-2 font-heading font-semibold", dc.valid ? "text-[var(--good-text)]" : "text-[var(--bad-text)]")}>
                {dc.valid ? <IconCheck size={18} /> : <IconAlert size={18} />}
                {dc.valid ? t("hub.depth.valid") : t("hub.depth.invalid")}
              </div>
              <p className="text-[0.85rem] text-text-muted">{t("hub.depth.counts", { errors: dc.errors, warnings: dc.warnings })}</p>
              <Button variant="outline" size="sm" href={`/c/${careerId}/depth-chart`} icon={<IconTarget size={16} />}>
                {t("hub.depth.edit")}
              </Button>
            </div>
          ) : (
            <p className="text-sm text-text-muted">—</p>
          )}
        </Card>

        <Card title={t("hub.coming.title")} tone="sunken" className="lg:col-span-3">
          <dl className="grid grid-cols-2 gap-3 text-[0.85rem] sm:grid-cols-4">
            {([
              ["morale", d.morale],
              ["inbox", d.unreadInbox],
              ["scoresPoints", d.scoresPoints],
              ["analysis", d.analysis],
            ] as const).map(([k, v]) => (
              <div key={k} className="min-w-0">
                <dt className="font-heading text-[0.68rem] uppercase tracking-[0.12em] text-text-dim">{t(`hub.coming.${k}`)}</dt>
                <dd className="mt-0.5 font-heading text-lg tabular text-text-muted">{typeof v === "number" ? f.num(v) : "—"}</dd>
              </div>
            ))}
          </dl>
        </Card>
      </div>
    </div>
  );
}
