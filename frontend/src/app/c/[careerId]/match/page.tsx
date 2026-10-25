"use client";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/lib/api";
import type { ClubRef } from "@/lib/api/career-types";
import type { MatchState, TeamBoxScore, WeekBoard } from "@/lib/api/match-types";
import { attacksUp, canAdvance, clockText, currentDrive, decisionOpen, isBreak, scrimmageMarkers, secondsLeft, spotFor } from "@/lib/match/live";
import { useNow } from "@/lib/hooks/useNow";
import { useT, useTd } from "@/i18n/provider";
import { useCareer } from "@/store/career";
import { useMatch } from "@/store/match";
import { PageHeader } from "@/components/shell/PageHeader";
import { ClubBadge } from "@/components/career/ClubBadge";
import { BreakPanel } from "@/components/match/BreakPanel";
import { EventFeed } from "@/components/match/EventFeed";
import { MatchControls } from "@/components/match/MatchControls";
import { MiniField2D } from "@/components/match/MiniField2D";
import { PlayCallPanel } from "@/components/match/PlayCallPanel";
import { Scoreboard } from "@/components/match/Scoreboard";
import { PrepareTeamModal } from "@/components/game-plan/PrepareTeamModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Toggle } from "@/components/ui/Field";
import { EmptyState, ErrorState, InlineAlert, PageSkeleton } from "@/components/ui/States";
import { IconBall, IconFlag, IconLed } from "@/components/ui/icons";

function StatsCard({ state }: { state: MatchState }) {
  const t = useT("match");
  const l = state.live;
  const rows: [string, (s: TeamBoxScore) => string][] = [
    [t("stats.totalYards"), (s) => String(s.totalYards)],
    [t("stats.passing"), (s) => `${s.passCompletions}/${s.passAttempts} · ${s.passYards}`],
    [t("stats.rushing"), (s) => `${s.rushAttempts} · ${s.rushYards}`],
    [t("stats.firstDowns"), (s) => String(s.firstDowns)],
    [t("stats.thirdDowns"), (s) => `${s.thirdDownConversions}/${s.thirdDownAttempts}`],
    [t("stats.turnovers"), (s) => String(s.turnovers)],
    [t("stats.sacks"), (s) => String(s.defensiveSacks)],
    [t("stats.possessionTime"), (s) => clockText(s.timeOfPossessionSeconds)],
  ];
  return (
    <Card eyebrow={t("live.stats.eyebrow")} title={t("live.stats.title")}>
      <table className="w-full text-[0.85rem]">
        <thead>
          <tr className="font-heading text-[0.68rem] uppercase tracking-[0.12em] text-text-dim">
            <th scope="col" className="pb-1 text-left font-normal">{l.away.shortName}</th>
            <th scope="col" className="pb-1 text-center font-normal"><span className="sr-only">{t("live.stats.title")}</span></th>
            <th scope="col" className="pb-1 text-right font-normal">{l.home.shortName}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(([label, v]) => (
            <tr key={label} className="border-t border-border/60">
              <td className="py-1 tabular">{v(l.away.stats)}</td>
              <th scope="row" className="py-1 text-center font-normal text-text-muted">{label}</th>
              <td className="py-1 text-right tabular">{v(l.home.stats)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

function WeekGames({ board, careerId }: { board: WeekBoard; careerId: string }) {
  const t = useT("match");
  const tc = useT("common");
  const others = board.games.filter((g) => g.fixture.id !== board.userFixtureId);
  return (
    <Card eyebrow={tc("week.n", { n: board.week })} title={t("live.week.title")}>
      <p className="mb-2 text-[0.8rem] text-text-dim">{t("live.week.hint")}</p>
      <ul className="grid gap-x-6 sm:grid-cols-2">
        {others.map((g) => {
          const done = g.fixture.homeScore != null && g.fixture.awayScore != null;
          return (
            <li key={g.fixture.id} className="flex min-w-0 items-center gap-2 border-t border-border/60 py-1.5 text-[0.85rem]">
              <ClubBadge club={g.away} size={20} />
              <span className="truncate">{g.away.shortName}</span>
              <span className="text-text-dim">{tc("at")}</span>
              <ClubBadge club={g.home} size={20} />
              <span className="truncate">{g.home.shortName}</span>
              {done ? (
                <Link href={`/c/${careerId}/match/${g.fixture.id}/report`} className="ml-auto shrink-0 tabular text-text-soft underline-offset-2 hover:underline">{g.fixture.awayScore}–{g.fixture.homeScore}</Link>
              ) : (
                <span className="ml-auto shrink-0 text-[0.72rem] text-text-dim">{t("live.week.pending")}</span>
              )}
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

/** Partida ao vivo por descida (RF27/RF29/RF62). A partida vem de `?m=`, da central (`dashboard.live`) ou do painel da semana atual. */
function MatchScreen() {
  const t = useT("match");
  const tg = useT("game");
  const td = useTd();
  const { careerId } = useParams<{ careerId: string }>();
  const search = useSearchParams();
  const router = useRouter();
  const setDashboard = useCareer((s) => s.setDashboard);
  const m = useMatch();
  const [resolving, setResolving] = useState(true);
  const [missing, setMissing] = useState(false);
  const [board, setBoard] = useState<WeekBoard | null>(null);
  const [prepare, setPrepare] = useState(false);
  const [highlights, setHighlights] = useState(false);
  const fromUrl = search.get("m");

  // acha a partida: ?m= → partida em andamento na central → painel da semana atual
  useEffect(() => {
    let alive = true;
    (async () => {
      setResolving(true);
      setMissing(false);
      let id = fromUrl;
      if (!id) {
        try {
          const d = await api.careers.dashboard(careerId);
          setDashboard(d);
          id = d.live?.matchId ?? null;
          if (!id) id = (await api.weeks.get(careerId, d.currentWeek).catch(() => null))?.matchId ?? null;
        } catch {
          id = null;
        }
        if (id && alive) router.replace(`/c/${careerId}/match?m=${id}`);
      }
      if (!alive) return;
      if (id) await useMatch.getState().load(id);
      else setMissing(true);
      setResolving(false);
    })();
    return () => {
      alive = false;
    };
  }, [careerId, fromUrl, router, setDashboard]);

  useEffect(() => () => useMatch.getState().reset(), []);

  const state = m.state;
  const week = state?.week;
  const finished = state?.status === "FINISHED";
  // franquias (cores e escudos) e os outros jogos da semana
  useEffect(() => {
    if (!week) return;
    let alive = true;
    api.weeks.get(careerId, week).then((b) => alive && setBoard(b)).catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [careerId, week, finished]);

  const open = !!state && state.status === "AWAITING_DECISION" && !!state.live.decision;
  const now = useNow(open);
  const left = state ? secondsLeft(state.decisionDeadlineAt, now) : null;
  // venceu o prazo: relê o estado (o servidor passa a aceitar o avanço e a IA chama)
  const expiredFor = useRef<string | null>(null);
  useEffect(() => {
    if (!state || !open || left !== 0) return;
    const id = state.live.decision!.id;
    if (expiredFor.current === id) return;
    expiredFor.current = id;
    void useMatch.getState().refresh();
  }, [state, open, left]);

  // ao terminar, a central muda (campanha, semana): atualiza o cache do topo
  useEffect(() => {
    if (finished) void api.careers.dashboard(careerId).then(setDashboard).catch(() => undefined);
  }, [finished, careerId, setDashboard]);

  const clubs = useMemo(() => {
    const map = new Map<string, ClubRef>();
    for (const g of board?.games ?? []) {
      map.set(g.home.id, g.home);
      map.set(g.away.id, g.away);
    }
    return map;
  }, [board]);

  if (resolving || (m.loading && !state)) return <PageSkeleton rows={2} />;
  if (missing) {
    return (
      <div className="flex flex-col gap-4">
        <PageHeader eyebrow={t("live.eyebrow")} title={t("live.none.title")} />
        <EmptyState icon={<IconBall size={24} />} title={t("live.none.title")} body={t("live.none.body")} action={<Button variant="led" icon={<IconLed size={18} />} onClick={() => setPrepare(true)}>{tg("prepare.cta")}</Button>} />
        <PrepareTeamModal open={prepare} onClose={() => setPrepare(false)} careerId={careerId} />
      </div>
    );
  }
  if (!state) return <ErrorState message={m.error} onRetry={() => fromUrl && void m.load(fromUrl)} />;

  const l = state.live;
  const user = state.userHome ? l.home : l.away;
  const opp = state.userHome ? l.away : l.home;
  const up = attacksUp(l);
  const drive = currentDrive(l, m.events);
  const advance = canAdvance(state, now);
  const showBreak = isBreak(state.status) && state.adjustable;
  const markers = up == null ? null : scrimmageMarkers(l.ballOnAbsYd, l.distance, up);
  const spot = up == null ? null : spotFor(l.ballOnAbsYd, up);
  const offense = up == null ? null : up ? l.home : l.away;
  const defenseTeam = up == null ? null : up ? l.away : l.home;
  const fieldLabel = !offense || !spot || !markers || !defenseTeam
    ? t("live.field.noBall")
    : t("live.field.aria", {
        team: offense.shortName,
        spot: spot.side === "MID" ? t("live.spot.mid") : t("live.spot.side", { team: spot.side === "OWN" ? offense.shortName : defenseTeam.shortName, yard: spot.yard }),
        down: markers.goalToGo ? t("live.downGoal", { down: l.down }) : t("live.downDistance", { down: l.down, distance: l.distance }),
        direction: markers.direction === 1 ? t("live.field.right") : t("live.field.left"),
      });

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        eyebrow={t("live.eyebrowWeek", { week: state.week })}
        title={t("live.title", { away: l.away.shortName, home: l.home.shortName })}
        description={t("live.description", { level: td("enums", `playCalling.${state.playCallingLevel}`), weather: td("enums", `weather.${l.weather}`) })}
        actions={finished ? <Button variant="led" size="lg" icon={<IconFlag size={18} />} href={`/c/${careerId}/match/${state.fixtureId}/report`}>{t("live.report")}</Button> : undefined}
      />

      {m.notice && (
        <InlineAlert tone={m.notice === "aiCalled" || m.notice === "finished" ? "info" : "warn"}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span>{td("match", `live.notice.${m.notice}`)}</span>
            <button type="button" className="text-[0.8rem] underline" onClick={m.dismiss}>{t("live.notice.dismiss")}</button>
          </div>
        </InlineAlert>
      )}
      {m.error && <InlineAlert tone="bad">{m.error}</InlineAlert>}

      <Scoreboard state={state} clubs={clubs} />

      {finished && (
        <InlineAlert tone="good">
          {user.score > opp.score ? t("live.final.win", { score: `${user.score}–${opp.score}`, opponent: opp.name }) : user.score < opp.score ? t("live.final.loss", { score: `${user.score}–${opp.score}`, opponent: opp.name }) : t("live.final.tie", { score: `${user.score}–${opp.score}`, opponent: opp.name })}
        </InlineAlert>
      )}

      {open && state.live.decision && (
        <PlayCallPanel decision={state.live.decision} live={l} left={left} total={state.decisionTimeoutSeconds} busy={m.busy} onCall={(c) => void m.callPlay(c)} onContinue={() => void m.advance("PLAY")} />
      )}

      {showBreak && <BreakPanel careerId={careerId} status={state.status} busy={m.busy} adjusted={m.adjusted?.matchId === state.matchId ? m.adjusted : null} onApply={m.adjust} />}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] [&>*]:min-w-0">
        <div className="flex flex-col gap-4">
          {!finished && (
            <MatchControls
              canAdvance={advance && !(open && decisionOpen(state, now))}
              busy={m.busy}
              timeoutsLeft={user.timeouts}
              canTimeout={state.status === "LIVE"}
              onAdvance={(mode) => void m.advance(mode)}
              onTimeout={() => void m.timeout()}
            />
          )}
          <Card eyebrow={t("live.field.eyebrow")} title={drive ? t("live.drive.title", { team: offense?.shortName ?? "" }) : t("live.field.title")} action={drive ? <Badge tone="led" size="xs">{t("live.drive.number", { n: drive.number })}</Badge> : undefined}>
            <MiniField2D live={l} events={m.events} clubs={clubs} label={fieldLabel} />
            {drive && (
              <dl className="mt-3 grid grid-cols-3 gap-0.5 overflow-hidden rounded-md border border-arena-3 bg-pitch text-center">
                <div className="bg-arena-2 px-2 py-2"><dt className="font-heading text-[0.62rem] uppercase tracking-[0.14em] text-text-dim">{t("live.drive.plays")}</dt><dd className="font-heading text-xl tabular">{drive.plays}</dd></div>
                <div className="bg-arena-2 px-2 py-2"><dt className="font-heading text-[0.62rem] uppercase tracking-[0.14em] text-text-dim">{t("live.drive.yards")}</dt><dd className="font-heading text-xl tabular">{drive.yards}</dd></div>
                <div className="bg-arena-2 px-2 py-2"><dt className="font-heading text-[0.62rem] uppercase tracking-[0.14em] text-text-dim">{t("live.drive.start")}</dt><dd className="font-heading text-xl tabular">{(() => { const s = spotFor(drive.startAbsYd, up ?? true); return s.side === "MID" ? "50" : `${s.side === "OWN" ? offense?.shortName : defenseTeam?.shortName} ${s.yard}`; })()}</dd></div>
              </dl>
            )}
          </Card>
          <StatsCard state={state} />
        </div>
        <Card eyebrow={t("live.feed.eyebrow")} title={t("live.feed.title")} padded={false} action={<div className="px-1"><Toggle label={<span className="text-[0.8rem]">{t("live.feed.highlights")}</span>} checked={highlights} onChange={setHighlights} /></div>}>
          <EventFeed events={m.events} live={l} highlightsOnly={highlights} userClubId={state.userClubId} />
        </Card>
      </div>

      {board && board.games.length > 1 && <WeekGames board={board} careerId={careerId} />}
      <p className="text-[0.72rem] text-text-dim">{t("live.version", { n: l.version, engine: l.engineVersion, rules: l.rulesVersion })}</p>
    </div>
  );
}

export default function MatchPage() {
  return (
    <Suspense fallback={<PageSkeleton rows={2} />}>
      <MatchScreen />
    </Suspense>
  );
}
