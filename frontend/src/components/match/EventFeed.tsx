"use client";
import type { LiveMatch, PlayEvent } from "@/lib/api/match-types";
import { clockText } from "@/lib/match/live";
import { splitRefKey } from "@/i18n/runtime";
import { useT, useTd } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { Badge } from "@/components/ui/Badge";

const SCRIMMAGE = new Set(["RUN", "PASS", "KNEEL", "SPIKE"]);
const PERIOD = new Set(["END_OF_PERIOD", "TWO_MINUTE_WARNING", "COIN_TOSS"]);

/** Lance relevante para "só destaques": pontos, turnover, chamada do manager, fim de período, tempo técnico, falta. */
export function isHighlight(e: PlayEvent): boolean {
  return e.points > 0 || e.turnover || e.callSource === "USER" || e.callSource === "AUTO" || (e.type != null && (PERIOD.has(e.type) || e.type === "TIMEOUT")) || !!e.penalty;
}

/** Texto do lance pela chave do motor (`match:event.*`) com os parâmetros, no idioma corrente. */
export function useEventText(): (e: PlayEvent) => string {
  const td = useTd();
  return (e) => {
    const { ns, key } = splitRefKey(e.textKey);
    return td(ns, key, e.params);
  };
}

/**
 * Play-by-play (RF27): o lance mais novo em cima, com quarto e relógio, descida e distância, o texto do motor traduzido e marcas de
 * touchdown/pontos, turnover, primeira descida e quem chamou a jogada (o manager ou a IA no prazo vencido).
 */
export function EventFeed({ events, live, highlightsOnly, userClubId, maxHeight = 560 }: { events: PlayEvent[]; live: LiveMatch; highlightsOnly: boolean; userClubId: string; maxHeight?: number }) {
  const t = useT("match");
  const text = useEventText();
  const short = (id: string | null) => (id === live.home.clubId ? live.home.shortName : id === live.away.clubId ? live.away.shortName : "");
  const list = (highlightsOnly ? events.filter(isHighlight) : events).slice(-300).reverse();
  const latest = events[events.length - 1];
  return (
    <div>
      <p className="sr-only" aria-live="polite" aria-atomic="true">{latest ? text(latest) : ""}</p>
      <ol className="scrollbar-thin divide-y divide-border/60 overflow-y-auto" style={{ maxHeight }} aria-label={t("live.feed.title")}>
        {list.map((e) => {
          const big = e.points > 0 || e.turnover;
          const mine = e.possessionClubId === userClubId;
          return (
            <li key={e.seq} className={cn("flex items-start gap-3 px-3 py-2", big && "bg-arena-3/50")}>
              <div className="w-14 shrink-0 pt-0.5 font-heading text-[0.68rem] uppercase tracking-[0.06em] text-text-dim tabular">
                <div>{e.quarter > 4 ? t("live.ot") : t("live.q", { n: Math.max(1, e.quarter) })}</div>
                <div>{clockText(e.clockSeconds)}</div>
              </div>
              <div className="min-w-0 flex-1">
                {e.type != null && SCRIMMAGE.has(e.type) && e.possessionClubId && (
                  <div className="mb-0.5 font-heading text-[0.68rem] uppercase tracking-[0.08em] text-text-dim">
                    {short(e.possessionClubId)} · {t("live.downDistance", { down: e.down, distance: e.distance })}
                  </div>
                )}
                <div className={cn("text-[0.9rem] leading-snug", big ? "font-medium text-text" : "text-text-soft", mine && "border-l-2 border-orange pl-2")}>{text(e)}</div>
                <div className="mt-1 flex flex-wrap items-center gap-1">
                  {e.points > 0 && <Badge tone="good" size="xs">{t("live.feed.points", { n: e.points, team: short(e.scoringClubId) })}</Badge>}
                  {e.turnover && <Badge tone="bad" size="xs">{t("live.feed.turnover")}</Badge>}
                  {e.firstDown && e.points === 0 && <Badge tone="info" size="xs">{t("live.feed.firstDown")}</Badge>}
                  {e.callSource === "USER" && <Badge tone="orange" size="xs">{t("live.feed.userCall")}</Badge>}
                  {e.callSource === "AUTO" && <Badge tone="warn" size="xs">{t("live.feed.autoCall")}</Badge>}
                  {e.points > 0 && <span className="text-[0.7rem] text-text-dim tabular">{live.away.shortName} {e.awayScore} – {e.homeScore} {live.home.shortName}</span>}
                </div>
              </div>
            </li>
          );
        })}
        {list.length === 0 && <li className="px-3 py-6 text-center text-sm text-text-dim">{t("live.feed.empty")}</li>}
      </ol>
    </div>
  );
}
