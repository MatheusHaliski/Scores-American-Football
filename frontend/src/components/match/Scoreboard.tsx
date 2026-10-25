"use client";
import { motion, useReducedMotion } from "framer-motion";
import type { ClubRef } from "@/lib/api/career-types";
import type { MatchState, TeamLine } from "@/lib/api/match-types";
import { attacksUp, clockText, scrimmageMarkers, spotFor } from "@/lib/match/live";
import { useT } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { ClubBadge } from "@/components/career/ClubBadge";
import { Badge } from "@/components/ui/Badge";

function AnimatedScore({ value, label }: { value: number; label: string }) {
  const reduce = useReducedMotion();
  // a chave troca a cada ponto: o número entra com um "pulo" curto (sem estado nem efeito)
  return (
    <motion.span key={value} className="led-text inline-block font-display text-5xl tabular sm:text-7xl" initial={reduce ? false : { scale: 1.14 }} animate={{ scale: 1 }} transition={{ duration: 0.35 }} aria-label={label}>
      {value}
    </motion.span>
  );
}

function Side({ team, club, possession, user, side }: { team: TeamLine; club: ClubRef | undefined; possession: boolean; user: boolean; side: "home" | "away" }) {
  const t = useT("match");
  const tc = useT("common");
  return (
    <div className="flex min-w-0 flex-col items-center gap-1 text-center">
      {club ? <ClubBadge club={club} size={52} /> : <span className="grid size-[52px] place-items-center rounded-[28%] border-2 border-border-strong bg-arena-4 font-display text-text">{team.shortName}</span>}
      <div className="max-w-full truncate font-heading text-sm font-semibold leading-tight sm:text-base">{team.name}</div>
      <div className="flex flex-wrap items-center justify-center gap-1">
        <span className="text-[0.68rem] uppercase tracking-[0.12em] text-text-dim">{side === "home" ? tc("home") : tc("away")}</span>
        {user && <Badge tone="orange" size="xs">{t("live.you")}</Badge>}
        {possession && <Badge tone="led" size="xs">● {t("live.possession")}</Badge>}
      </div>
      <div className="flex items-center gap-1" aria-label={t("live.timeoutsLeft", { n: team.timeouts })} title={t("live.timeoutsLeft", { n: team.timeouts })}>
        {[0, 1, 2].map((i) => (
          <span key={i} className={cn("h-1.5 w-4 rounded-full", i < team.timeouts ? "bg-led" : "bg-arena-4")} aria-hidden />
        ))}
      </div>
    </div>
  );
}

/** Placar ao vivo (RF27): times, pontos, quarto, relógio, posse, descida e distância, bola, tempos técnicos e placar por quarto. */
export function Scoreboard({ state, clubs }: { state: MatchState; clubs: Map<string, ClubRef> }) {
  const t = useT("match");
  const l = state.live;
  const up = attacksUp(l);
  const live = state.status === "LIVE" || state.status === "AWAITING_DECISION";
  const statusLabel =
    state.status === "FINISHED" ? t("live.status.FINISHED") :
    state.status === "HALFTIME" ? t("live.status.HALFTIME") :
    state.status === "BREAK" ? t("live.status.BREAK") :
    state.status === "NOT_STARTED" ? t("live.status.NOT_STARTED") :
    l.quarter > 4 ? t("live.overtime", { n: l.quarter - 4 }) : t("live.quarter", { n: l.quarter });
  const offense = up == null ? null : up ? l.home : l.away;
  const defenseTeam = up == null ? null : up ? l.away : l.home;
  const markers = up == null ? null : scrimmageMarkers(l.ballOnAbsYd, l.distance, up);
  const spot = up == null ? null : spotFor(l.ballOnAbsYd, up);
  const spotText = !spot || !offense || !defenseTeam ? null : spot.side === "MID" ? t("live.spot.mid") : t("live.spot.side", { team: spot.side === "OWN" ? offense.shortName : defenseTeam.shortName, yard: spot.yard });
  const downText = markers?.goalToGo ? t("live.downGoal", { down: l.down }) : t("live.downDistance", { down: l.down, distance: l.distance });
  const quarters = Math.max(4, l.home.quarters.length, l.away.quarters.length);
  // entre quartos e no intervalo o próximo período ainda não começou: a coluna dele fica "–" enquanto estiver zerada
  const playedQuarters = state.status === "BREAK" || state.status === "HALFTIME" ? l.quarter - 1 : state.status === "NOT_STARTED" ? 0 : l.quarter;

  return (
    <section className="overflow-hidden rounded-[var(--radius-xl)] border-2 border-arena-3 bg-pitch" aria-label={t("live.scoreboard")}>
      <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2 p-3 sm:gap-6 sm:p-5" style={{ backgroundImage: "var(--grad-arena)" }}>
        <Side team={l.away} club={clubs.get(l.away.clubId)} possession={live && up === false} user={!state.userHome} side="away" />
        <div className="flex flex-col items-center gap-1">
          <div className="flex items-center gap-2 sm:gap-4">
            <AnimatedScore value={l.away.score} label={`${l.away.shortName} ${l.away.score}`} />
            <span className="font-heading text-2xl text-text-dim sm:text-4xl" aria-hidden>:</span>
            <AnimatedScore value={l.home.score} label={`${l.home.shortName} ${l.home.score}`} />
          </div>
          <div className="flex flex-wrap items-center justify-center gap-2">
            <Badge tone={live ? "led" : "neutral"} size="md">{statusLabel}</Badge>
            <span className="font-heading text-xl font-bold tabular sm:text-3xl" aria-label={t("live.clock", { time: clockText(l.clockSeconds) })}>{clockText(l.clockSeconds)}</span>
          </div>
          {live && offense && (
            <div className="mt-1 text-center font-heading text-[0.85rem] uppercase tracking-[0.1em] text-text-soft sm:text-base" aria-live="polite">
              <span className="text-led">{downText}</span>
              {spotText && <span className="text-text-muted"> · {spotText}</span>}
            </div>
          )}
        </div>
        <Side team={l.home} club={clubs.get(l.home.clubId)} possession={live && up === true} user={state.userHome} side="home" />
      </div>
      <div className="hide-scrollbar overflow-x-auto border-t border-border">
        <table className="w-full text-center text-sm">
          <caption className="sr-only">{t("live.byQuarter")}</caption>
          <thead>
            <tr className="font-heading text-[0.66rem] uppercase tracking-[0.14em] text-text-dim">
              <th scope="col" className="px-3 py-1.5 text-left font-medium">{t("live.byQuarter")}</th>
              {Array.from({ length: quarters }).map((_, i) => (
                <th scope="col" key={i} className="px-2 py-1.5 font-medium">{i < 4 ? t("live.q", { n: i + 1 }) : t("live.ot")}</th>
              ))}
              <th scope="col" className="px-3 py-1.5 font-medium">{t("live.total")}</th>
            </tr>
          </thead>
          <tbody className="font-heading tabular">
            {[l.away, l.home].map((tm) => (
              <tr key={tm.clubId} className="border-t border-border/60">
                <th scope="row" className="px-3 py-1.5 text-left font-semibold">{tm.shortName}</th>
                {Array.from({ length: quarters }).map((_, i) => (
                  <td key={i} className={cn("px-2 py-1.5", i === l.quarter - 1 && live ? "text-led" : "text-text-soft")}>{i < playedQuarters || state.status === "FINISHED" || (tm.quarters[i] ?? 0) > 0 ? (tm.quarters[i] ?? 0) : "–"}</td>
                ))}
                <td className="px-3 py-1.5 font-bold text-led">{tm.score}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
