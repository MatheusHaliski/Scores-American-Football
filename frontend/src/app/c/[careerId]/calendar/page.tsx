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
import { IconChevronRight } from "@/components/ui/icons";

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

/** Calendário da temporada (RF21): semanas com o jogo da franquia ou o bye; a liga inteira sob demanda; relatório dos jogos encerrados (6b). */
export default function CalendarPage() {
  const t = useT("career");
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
      <ol className="grid gap-2">
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
                      {mine.divisional && <Badge tone="led" size="xs">{t("hub.next.divisional")}</Badge>}
                      {mine.homeScore != null && mine.awayScore != null && <Badge size="xs">{home ? `${mine.homeScore}–${mine.awayScore}` : `${mine.awayScore}–${mine.homeScore}`}</Badge>}
                      {mine.status === "LIVE" && <Badge tone="led" size="xs">{t("calendar.live")}</Badge>}
                    </div>
                    {mine.status === "FINISHED" && (
                      <Link href={`/c/${careerId}/match/${mine.id}/report`} className="touch inline-flex shrink-0 items-center gap-1 rounded-md px-2 text-[0.8rem] text-led underline-offset-2 hover:underline">
                        {t("calendar.report")} <IconChevronRight size={14} />
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
    </div>
  );
}
