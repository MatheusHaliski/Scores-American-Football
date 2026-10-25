"use client";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api";
import type { ClubRef, Fixture } from "@/lib/api/types";
import { useQuery } from "@/lib/hooks/useQuery";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { PageHeader } from "@/components/shell/PageHeader";
import { ClubBadge } from "@/components/career/ClubBadge";
import { Badge } from "@/components/ui/Badge";
import { Toggle } from "@/components/ui/Field";
import { ErrorState, PageSkeleton } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { IconChevronDown, IconChevronRight, IconMessage, IconTrophy } from "@/components/ui/icons";
import { NewsFeed } from "@/components/season/NewsFeed";
import type { WeekNews as WeekNewsData } from "@/lib/api/season-types";
import { isPlayoffKind } from "@/lib/season/bracket";
import { describeError } from "@/lib/api/errors";

function GameLine({ f, clubs, userClubId }: { f: Fixture; clubs: Map<string, ClubRef>; userClubId: string }) {
  const tc = useT("common");
  const td = useTd();
  const home = clubs.get(f.homeClubId);
  const away = clubs.get(f.awayClubId);
  const played = f.homeScore != null && f.awayScore != null;
  return (
    <li className={cn("flex min-w-0 items-center gap-2 py-1 text-[0.85rem]", f.userGame && "font-semibold text-text")}>
      {away && <ClubBadge club={away} size={20} />}
      <span className={cn("truncate", away?.id === userClubId && "text-led")}>{away?.shortName ?? "—"}</span>
      <span className="text-text-dim">{tc("at")}</span>
      {home && <ClubBadge club={home} size={20} />}
      <span className={cn("truncate", home?.id === userClubId && "text-led")}>{home?.shortName ?? "—"}</span>
      <span className="ml-auto shrink-0 text-[0.72rem] text-text-dim">{played ? `${f.awayScore}–${f.homeScore}` : td("enums", `kickoffShort.${f.kickoffSlot}`)}</span>
    </li>
  );
}

/** Notícias de uma semana encerrada (RF21), carregadas ao abrir (`GET /weeks/{n}/news`). */
function WeekNewsToggle({ careerId, week }: { careerId: string; week: number }) {
  const ts = useT("season");
  const [open, setOpen] = useState(false);
  const [data, setData] = useState<WeekNewsData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (next && !data && !loading) {
      setLoading(true);
      setError(null);
      try {
        setData(await api.season.weekNews(careerId, week));
      } catch (e) {
        setError(describeError(e));
      } finally {
        setLoading(false);
      }
    }
  };
  return (
    <div className="mt-2">
      <Button variant="ghost" size="sm" icon={<IconMessage size={14} />} iconRight={<IconChevronDown size={14} className={cn("transition-transform", open && "rotate-180")} />} onClick={() => void toggle()} aria-expanded={open}>
        {open ? ts("news.hide") : ts("news.show")}
      </Button>
      {open && (
        <div className="mt-2 border-t border-border pt-2">
          {loading ? (
            <p className="text-[0.8rem] text-text-dim">{ts("news.loading")}</p>
          ) : error ? (
            <p className="text-[0.8rem] text-[var(--bad-text)]">{error}</p>
          ) : data && data.items.length ? (
            <NewsFeed items={data.items} careerId={careerId} compact />
          ) : (
            <p className="text-[0.8rem] text-text-muted">{ts("news.empty")}</p>
          )}
        </div>
      )}
    </div>
  );
}

/** Calendário da temporada (RF21): semanas com o jogo da franquia ou o bye; a liga inteira sob demanda; relatório dos jogos encerrados (6b). */
export default function CalendarPage() {
  const t = useT("career");
  const ts = useT("season");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const { careerId } = useParams<{ careerId: string }>();
  const q = useQuery(() => api.careers.calendar(careerId), [careerId]);
  const [all, setAll] = useState(false);

  if (q.loading) return <PageSkeleton rows={2} />;
  if (q.error || !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const c = q.data;
  const clubs = new Map(c.clubs.map((x) => [x.id, x]));
  const me = c.userClubId;

  return (
    <div className="flex flex-col gap-4">
      <PageHeader eyebrow={t("calendar.eyebrow")} title={t("calendar.title", { year: c.seasonYear })} description={t("calendar.description", { games: c.gamesPerTeam, weeks: c.regularSeasonWeeks })} />
      <Toggle label={t("calendar.allGames")} checked={all} onChange={setAll} />
      <ol className="grid gap-2 [&>*]:min-w-0">
        {c.weeks.map((w) => {
          const mine = w.fixtures.find((x) => x.homeClubId === me || x.awayClubId === me);
          const home = mine?.homeClubId === me;
          const opp = mine ? clubs.get(home ? mine.awayClubId : mine.homeClubId) : undefined;
          const current = w.status === "CURRENT";
          return (
            <li key={w.number} className={cn("rounded-[var(--radius-lg)] border bg-arena-2 p-3", current ? "border-rim shadow-orange" : "border-border")} aria-current={current ? "step" : undefined}>
              <div className="flex flex-wrap items-center gap-3">
                <div className="w-16 shrink-0">
                  <div className="font-display text-xl leading-none">{tc("week.short", { n: w.number })}</div>
                  <div className="text-[0.7rem] text-text-dim">{f.dateShort(`${w.startsOn}T12:00:00Z`)}</div>
                  {isPlayoffKind(w.kind) && <div className="mt-0.5 text-[0.62rem] uppercase tracking-[0.08em] text-led">{td("season", `playoffs.round.${w.kind}`)}</div>}
                </div>
                {w.userBye ? (
                  <div className="min-w-0 flex-1">
                    <Badge tone="info">{t("calendar.bye")}</Badge> <span className="text-[0.85rem] text-text-muted">{t("calendar.byeBody")}</span>
                  </div>
                ) : mine && opp ? (
                  <div className="flex min-w-0 flex-1 items-center gap-2">
                    <span className="w-5 shrink-0 text-center font-heading text-text-dim">{home ? tc("vs") : tc("at")}</span>
                    <ClubBadge club={opp} size={32} />
                    <div className="min-w-0">
                      <div className="truncate font-heading font-semibold">{opp.name}</div>
                      <div className="truncate text-[0.72rem] text-text-dim">
                        {f.dateShort(`${mine.date}T12:00:00Z`)} · {td("enums", `kickoff.${mine.kickoffSlot}`)}
                      </div>
                    </div>
                    <div className="ml-auto flex shrink-0 gap-1">
                      {mine.divisional && <span className="hidden sm:inline"><Badge tone="led" size="xs">{t("hub.next.divisional")}</Badge></span>}
                      {mine.homeScore != null && mine.awayScore != null && <Badge size="xs">{home ? `${mine.homeScore}–${mine.awayScore}` : `${mine.awayScore}–${mine.homeScore}`}</Badge>}
                      {mine.status === "LIVE" && <Badge tone="led" size="xs">{t("calendar.live")}</Badge>}
                    </div>
                    {mine.status === "FINISHED" && (
                      <Link href={`/c/${careerId}/match/${mine.id}/report`} className="touch inline-flex shrink-0 items-center gap-1 rounded-md px-2 text-[0.8rem] text-led underline-offset-2 hover:underline">
                        <span className="sr-only sm:not-sr-only">{t("calendar.report")}</span> <IconChevronRight size={14} />
                      </Link>
                    )}
                    {mine.status === "LIVE" && (
                      <Link href={`/c/${careerId}/match`} className="touch inline-flex shrink-0 items-center gap-1 rounded-md px-2 text-[0.8rem] text-led underline-offset-2 hover:underline">
                        {t("calendar.continue")} <IconChevronRight size={14} />
                      </Link>
                    )}
                  </div>
                ) : (
                  <div className="flex-1 text-text-muted">—</div>
                )}
                {current && <Badge tone="orange" size="xs">{t("calendar.current")}</Badge>}
              </div>
              {w.status === "COMPLETED" && <WeekNewsToggle careerId={careerId} week={w.number} />}
              {all && (
                <div className="mt-2 border-t border-border pt-2">
                  <div className="mb-1 text-[0.72rem] text-text-dim">
                    {t("calendar.leagueGames", { n: w.fixtures.length })}
                    {w.byeClubIds.length > 0 && ` · ${t("calendar.byeTeams", { teams: w.byeClubIds.map((id) => clubs.get(id)?.shortName ?? "?").join(", ") })}`}
                  </div>
                  <ul className="grid gap-x-6 sm:grid-cols-2 lg:grid-cols-3">
                    {w.fixtures.map((x) => <GameLine key={x.id} f={x} clubs={clubs} userClubId={me} />)}
                  </ul>
                </div>
              )}
            </li>
          );
        })}
      </ol>
      {c.playoffWeeks.some((p) => !c.weeks.some((w) => w.number === p.number)) && (
        <section aria-labelledby="cal-playoffs" className="rounded-[var(--radius-lg)] border border-border bg-pitch/60 p-3">
          <h2 id="cal-playoffs" className="mb-2 flex items-center gap-2 font-heading text-sm uppercase tracking-[0.14em] text-text-muted">
            <IconTrophy size={16} className="text-led" /> {ts("calendar.playoffs.title")}
          </h2>
          <ol className="grid gap-1.5 sm:grid-cols-2">
            {c.playoffWeeks
              .filter((p) => !c.weeks.some((w) => w.number === p.number))
              .map((p) => (
                <li key={p.number} className="flex items-center gap-3 rounded-[var(--radius-md)] border border-dashed border-border-strong px-3 py-2">
                  <span className="w-14 shrink-0 font-display text-lg leading-none">{tc("week.short", { n: p.number })}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-heading font-semibold">{td("season", `playoffs.round.${p.kind}`)}</span>
                    <span className="block text-[0.72rem] text-text-dim">{f.dateShort(`${p.startsOn}T12:00:00Z`)} · {ts("calendar.playoffs.pending")}</span>
                  </span>
                </li>
              ))}
          </ol>
        </section>
      )}
    </div>
  );
}
