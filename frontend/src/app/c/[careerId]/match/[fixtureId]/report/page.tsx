"use client";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { ApiError, describeError } from "@/lib/api/errors";
import type { MatchReport } from "@/lib/api/match-types";
import { clockText, decisionKeys } from "@/lib/match/live";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { PageHeader } from "@/components/shell/PageHeader";
import { ClubBadge } from "@/components/career/ClubBadge";
import { BoxScoreTable, BOX_TABS, type BoxTab } from "@/components/match/BoxScoreTable";
import { useEventText } from "@/components/match/EventFeed";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState, ErrorState, PageSkeleton } from "@/components/ui/States";
import { Tabs } from "@/components/ui/Tabs";
import { IconBall, IconCalendar, IconClock } from "@/components/ui/icons";

/** Relatório pós-jogo (RF27/RF31): placar por quarto, líderes, estatísticas das equipes, box score AF, drives, pontuação e decisões. */
export default function MatchReportPage() {
  const t = useT("match");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const eventText = useEventText();
  const { careerId, fixtureId } = useParams<{ careerId: string; fixtureId: string }>();
  const [tab, setTab] = useState<BoxTab>("passing");
  const [attempt, setAttempt] = useState(0);
  // resultado da última busca, com a chave do pedido: enquanto a chave não bate, a tela está carregando
  const key = `${fixtureId}#${attempt}`;
  const [result, setResult] = useState<{ key: string; report: MatchReport | null; error: unknown } | null>(null);

  useEffect(() => {
    let alive = true;
    api.matches
      .report(fixtureId)
      .then((r) => alive && setResult({ key, report: r, error: null }))
      .catch((e) => alive && setResult({ key, report: null, error: e }));
    return () => {
      alive = false;
    };
  }, [fixtureId, key]);

  if (!result || result.key !== key) return <PageSkeleton rows={2} />;
  const { report, error } = result;
  if (error instanceof ApiError && error.code === "MATCH_NOT_FINISHED") {
    return (
      <div className="flex flex-col gap-4">
        <PageHeader eyebrow={t("report.eyebrow")} title={t("report.notFinished.title")} back={{ href: `/c/${careerId}/calendar`, label: t("report.backCalendar") }} />
        <EmptyState icon={<IconClock size={24} />} title={t("report.notFinished.title")} body={t("report.notFinished.body")} action={<Button variant="led" href={`/c/${careerId}/match`} icon={<IconBall size={18} />}>{t("report.toMatch")}</Button>} />
      </div>
    );
  }
  if (error || !report) return <ErrorState message={error ? describeError(error) : null} onRetry={() => setAttempt((n) => n + 1)} />;

  const r = report;
  const clubName = (id: string | null) => (id === r.home.club.id ? r.home.club.shortName : id === r.away.club.id ? r.away.club.shortName : "—");
  const quarters = Math.max(4, r.home.quarters.length, r.away.quarters.length);
  const statRows: [string, (s: MatchReport["home"]["stats"]) => string][] = [
    [t("stats.firstDowns"), (s) => String(s.firstDowns)],
    [t("stats.totalYards"), (s) => String(s.totalYards)],
    [t("stats.yardsPerPlay"), (s) => f.num(s.yardsPerPlay, 1)],
    [t("stats.passing"), (s) => `${s.passCompletions}/${s.passAttempts} · ${s.passYards}`],
    [t("stats.rushing"), (s) => `${s.rushAttempts} · ${s.rushYards}`],
    [t("stats.thirdDowns"), (s) => `${s.thirdDownConversions}/${s.thirdDownAttempts}`],
    [t("stats.fourthDowns"), (s) => `${s.fourthDownConversions}/${s.fourthDownAttempts}`],
    [t("stats.redZone"), (s) => `${s.redZoneTouchdowns}/${s.redZoneTrips}`],
    [t("stats.turnovers"), (s) => String(s.turnovers)],
    [t("stats.sacks"), (s) => `${s.defensiveSacks}`],
    [t("stats.penalties"), (s) => `${s.penalties} · ${s.penaltyYards}`],
    [t("stats.fieldGoals"), (s) => `${s.fieldGoalsMade}/${s.fieldGoalAttempts}`],
    [t("stats.punts"), (s) => String(s.punts)],
    [t("stats.possessionTime"), (s) => clockText(s.timeOfPossessionSeconds)],
  ];
  const keys = decisionKeys(r.decisions);
  const decisionLabel = (code: string) => (code === "TIMEOUT" ? t("report.decision.TIMEOUT") : code === "PLAN_CHANGE" ? t("report.decision.PLAN_CHANGE") : td("match", `play.${code}`));

  return (
    <div className="flex flex-col gap-4 sm:gap-5">
      <PageHeader
        eyebrow={t("report.eyebrowWeek", { week: r.week })}
        title={t("report.title", { away: r.away.club.shortName, home: r.home.club.shortName })}
        description={[r.date ? f.dateLong(`${r.date}T12:00:00Z`) : null, r.kickoffSlot ? td("enums", `kickoff.${r.kickoffSlot}`) : null, r.weather ? td("enums", `weather.${r.weather}`) : null].filter(Boolean).join(" · ")}
        back={{ href: `/c/${careerId}/calendar`, label: t("report.backCalendar") }}
        actions={<Button variant="ghost" size="sm" href={`/c/${careerId}`} icon={<IconCalendar size={16} />}>{t("report.backHub")}</Button>}
      />

      <section className="overflow-hidden rounded-[var(--radius-xl)] border-2 border-arena-3 bg-pitch" aria-label={t("report.final")}>
        <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2 p-4 sm:gap-6 sm:p-6" style={{ backgroundImage: "var(--grad-arena)" }}>
          {[r.away, null, r.home].map((side, i) =>
            side ? (
              <div key={i} className="flex min-w-0 flex-col items-center gap-1 text-center">
                <ClubBadge club={side.club} size={56} />
                <span className="max-w-full truncate font-heading font-semibold">{side.club.name}</span>
                {r.winnerClubId === side.club.id && <Badge tone="good" size="xs">{t("report.winner")}</Badge>}
              </div>
            ) : (
              <div key={i} className="flex flex-col items-center gap-1">
                <div className="flex items-center gap-3 font-display text-5xl tabular sm:text-7xl">
                  <span className="led-text">{r.away.score}</span>
                  <span className="font-heading text-3xl text-text-dim" aria-hidden>:</span>
                  <span className="led-text">{r.home.score}</span>
                </div>
                <Badge size="md">{r.overtime ? t("report.finalOt") : t("report.final")}</Badge>
              </div>
            ),
          )}
        </div>
        <div className="hide-scrollbar overflow-x-auto border-t border-border">
          <table className="w-full text-center text-sm">
            <caption className="sr-only">{t("live.byQuarter")}</caption>
            <thead>
              <tr className="font-heading text-[0.66rem] uppercase tracking-[0.14em] text-text-dim">
                <th scope="col" className="px-3 py-1.5 text-left font-medium">{t("live.byQuarter")}</th>
                {Array.from({ length: quarters }).map((_, i) => <th scope="col" key={i} className="px-2 py-1.5 font-medium">{i < 4 ? t("live.q", { n: i + 1 }) : t("live.ot")}</th>)}
                <th scope="col" className="px-3 py-1.5 font-medium">{t("live.total")}</th>
              </tr>
            </thead>
            <tbody className="font-heading tabular">
              {[r.away, r.home].map((s) => (
                <tr key={s.club.id} className="border-t border-border/60">
                  <th scope="row" className="px-3 py-1.5 text-left font-semibold">{s.club.shortName}</th>
                  {Array.from({ length: quarters }).map((_, i) => <td key={i} className="px-2 py-1.5 text-text-soft">{s.quarters[i] ?? 0}</td>)}
                  <td className="px-3 py-1.5 font-bold text-led">{s.score}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <div className="grid gap-4 lg:grid-cols-3 [&>*]:min-w-0">
        <Card title={t("report.leaders")}>
          {r.leaders.length ? (
            <ul className="flex flex-col gap-2">
              {r.leaders.map((l) => (
                <li key={`${l.category}-${l.playerId}`} className="flex min-w-0 items-center gap-2 text-[0.88rem]">
                  <span className="w-24 shrink-0 font-heading text-[0.68rem] uppercase tracking-[0.12em] text-text-dim">{td("enums", `leader.${l.category}`)}</span>
                  <span className="min-w-0 flex-1 truncate">{l.name} <span className="text-text-dim">· {l.position} · {clubName(l.clubId)}</span></span>
                  <span className="shrink-0 font-heading tabular text-led">{td("match", `report.leaderValue.${l.category}`, { n: l.value })}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-text-muted">—</p>
          )}
        </Card>
        <Card title={t("report.teamStats")} className="lg:col-span-2">
          <table className="w-full text-[0.85rem]">
            <thead>
              <tr className="font-heading text-[0.68rem] uppercase tracking-[0.12em] text-text-dim">
                <th scope="col" className="pb-1 text-left font-normal">{r.away.club.shortName}</th>
                <th scope="col" className="pb-1 text-center font-normal"><span className="sr-only">{t("report.teamStats")}</span></th>
                <th scope="col" className="pb-1 text-right font-normal">{r.home.club.shortName}</th>
              </tr>
            </thead>
            <tbody>
              {statRows.map(([label, v]) => (
                <tr key={label} className="border-t border-border/60">
                  <td className="py-1 tabular">{v(r.away.stats)}</td>
                  <th scope="row" className="py-1 text-center font-normal text-text-muted">{label}</th>
                  <td className="py-1 text-right tabular">{v(r.home.stats)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      </div>

      <Card eyebrow={t("report.boxEyebrow")} title={t("report.box")}>
        <Tabs value={tab} onChange={setTab} className="mb-3" tabs={BOX_TABS.map((b) => ({ value: b, label: td("match", `report.tab.${b}`) }))} />
        <div className="grid gap-4 xl:grid-cols-2 [&>*]:min-w-0">
          {[r.away, r.home].map((s) => (
            <div key={s.club.id}>
              <h4 className="mb-1 flex items-center gap-2 font-heading font-semibold"><ClubBadge club={s.club} size={22} /> {s.club.name}</h4>
              <BoxScoreTable tab={tab} players={s.players} />
            </div>
          ))}
        </div>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2 [&>*]:min-w-0">
        <Card title={t("report.scoring")}>
          {r.scoringPlays.length ? (
            <ol className="grid gap-2">
              {r.scoringPlays.map((e) => (
                <li key={e.seq} className="flex items-start gap-3 text-[0.88rem]">
                  <span className="w-14 shrink-0 font-heading text-[0.7rem] uppercase text-text-dim tabular">{e.quarter > 4 ? t("live.ot") : t("live.q", { n: e.quarter })} {clockText(e.clockSeconds)}</span>
                  <span className="min-w-0 flex-1"><Badge size="xs" tone="good">{clubName(e.scoringClubId)} +{e.points}</Badge> {eventText(e)}</span>
                  <span className="shrink-0 tabular text-text-dim">{e.awayScore}–{e.homeScore}</span>
                </li>
              ))}
            </ol>
          ) : (
            <p className="text-sm text-text-muted">{t("report.noScoring")}</p>
          )}
        </Card>
        <Card title={t("report.decisions")}>
          {r.decisions.length ? (
            <ul className="grid gap-1.5">
              {r.decisions.map((d, i) => (
                <li key={keys[i]} className="flex flex-wrap items-center gap-2 text-[0.88rem]">
                  <span className="font-heading">{decisionLabel(d.optionCode)}</span>
                  {d.situation && <span className="text-text-dim">· {td("enums", `decisionSituation.${d.situation}`)}</span>}
                  <span className="text-text-dim">· {clubName(d.clubId)}</span>
                  <Badge size="xs" tone={d.auto ? "warn" : "orange"}>{d.auto ? t("report.decision.auto") : t("report.decision.user")}</Badge>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-text-muted">{t("report.noDecisions")}</p>
          )}
        </Card>
      </div>

      <Card title={t("report.drives")} padded={false}>
        <div className="scrollbar-thin overflow-x-auto">
          <table className="w-full min-w-[640px] text-[0.85rem]">
            <thead className="bg-pitch font-heading text-[0.66rem] uppercase tracking-[0.12em] text-text-dim">
              <tr>
                <th scope="col" className="px-3 py-2 text-left font-medium">#</th>
                <th scope="col" className="px-2 py-2 text-left font-medium">{t("report.drive.team")}</th>
                <th scope="col" className="px-2 py-2 text-left font-medium">{t("report.drive.start")}</th>
                <th scope="col" className="px-2 py-2 text-right font-medium">{t("report.drive.plays")}</th>
                <th scope="col" className="px-2 py-2 text-right font-medium">{t("report.drive.yards")}</th>
                <th scope="col" className="px-2 py-2 text-right font-medium">{t("report.drive.time")}</th>
                <th scope="col" className="px-3 py-2 text-left font-medium">{t("report.drive.result")}</th>
              </tr>
            </thead>
            <tbody>
              {r.drives.map((d) => (
                <tr key={d.seq} className="border-t border-border/60">
                  <td className="px-3 py-1.5 tabular text-text-dim">{d.seq}</td>
                  <td className="px-2 py-1.5">{clubName(d.clubId)}</td>
                  <td className="px-2 py-1.5 tabular">{t("live.q", { n: d.quarter })} {clockText(d.startClockSeconds)}</td>
                  <td className="px-2 py-1.5 text-right tabular">{d.plays}</td>
                  <td className="px-2 py-1.5 text-right tabular">{d.yards}</td>
                  <td className="px-2 py-1.5 text-right tabular">{clockText(d.timeOfPossessionSeconds)}</td>
                  <td className="px-3 py-1.5">{d.result ? <Badge size="xs" tone={d.result === "TD" || d.result === "FG" ? "good" : d.result === "TURNOVER" || d.result === "DOWNS" ? "bad" : "neutral"}>{td("enums", `driveResult.${d.result}`)}</Badge> : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <p className="text-[0.72rem] text-text-dim">{t("report.meta", { seed: r.seed, engine: r.engineVersion, rules: r.rulesVersion, plays: r.playCount })} · {tc("week.n", { n: r.week })}</p>
    </div>
  );
}
