"use client";
import type { LiveMatch, PendingDecision } from "@/lib/api/match-types";
import { clockText, countdownTone, spotFor } from "@/lib/match/live";
import { splitRefKey } from "@/i18n/runtime";
import { useT, useTd } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { ProgressBar } from "@/components/ui/Stat";
import { IconClock, IconWhistle } from "@/components/ui/icons";

export interface PlayCallPanelProps {
  decision: PendingDecision;
  live: LiveMatch;
  /** segundos que faltam (null sem prazo) */
  left: number | null;
  total: number;
  busy: boolean;
  onCall: (code: string) => void;
  /** prazo vencido: a IA chama no próximo avanço */
  onContinue: () => void;
}

/**
 * Chamada de jogada (RF62): a situação (descida, distância, bola, placar), as opções do motor e a contagem regressiva até o prazo de
 * 30 s (D-61). Vencido o prazo, a IA chama a opção padrão (marcada) no próximo avanço.
 */
export function PlayCallPanel({ decision, live, left, total, busy, onCall, onContinue }: PlayCallPanelProps) {
  const t = useT("match");
  const td = useTd();
  const up = decision.clubId === live.home.clubId ? decision.side === "OFFENSE" : decision.side !== "OFFENSE";
  const offense = up ? live.home : live.away;
  const defense = up ? live.away : live.home;
  const spot = spotFor(decision.ballOnAbsYd, up);
  const spotText = spot.side === "MID" ? t("live.spot.mid") : t("live.spot.side", { team: spot.side === "OWN" ? offense.shortName : defense.shortName, yard: spot.yard });
  const expired = left === 0;
  const tone = left == null ? "ok" : countdownTone(left);
  // anuncia o prazo em marcos (20, 10, 5 s) para leitores de tela, sem repetir a cada segundo
  const milestone = left != null && (left === 20 || left === 10 || left === 5) ? t("call.secondsLeft", { n: left }) : "";
  return (
    <section className={cn("rounded-[var(--radius-lg)] border-2 bg-arena-2 p-3 sm:p-4", expired ? "border-border-strong" : "border-led shadow-led")} aria-labelledby="call-title">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="eyebrow">{td("enums", `decisionSituation.${decision.situation}`)}</div>
          <h2 id="call-title" className="font-heading text-lg font-semibold leading-tight sm:text-xl">{t(decision.side === "OFFENSE" ? "call.title.OFFENSE" : "call.title.DEFENSE")}</h2>
          <p className="text-[0.85rem] text-text-muted">
            {t("live.downDistance", { down: decision.down, distance: decision.distance })} · {spotText} · {t("live.quarter", { n: decision.quarter })} {clockText(decision.clockSeconds)} · {live.away.shortName} {decision.awayScore} – {decision.homeScore} {live.home.shortName}
          </p>
        </div>
        {left != null && (
          <div className="flex min-w-[7rem] flex-col items-end gap-1" role="timer" aria-label={t("call.timer")}>
            <span className={cn("inline-flex items-center gap-1 font-display text-4xl tabular leading-none", tone === "urgent" ? "text-[var(--bad-text)]" : tone === "warn" ? "text-[var(--warn-text)]" : "text-led")}>
              <IconClock size={22} /> {left}
            </span>
            <ProgressBar value={left} max={total} tone={tone === "urgent" ? "bad" : tone === "warn" ? "warn" : "led"} className="w-28" />
          </div>
        )}
      </div>
      <p className="sr-only" aria-live="assertive">{milestone}</p>
      {expired ? (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-[0.9rem] text-text-soft">{t("call.expired", { play: td("match", splitRefKey(decision.options.find((o) => o.code === decision.autoOptionCode)?.labelKey ?? `match:play.${decision.autoOptionCode}`).key) })}</p>
          <Button variant="led" icon={<IconWhistle size={16} />} onClick={onContinue} loading={busy}>{t("call.continue")}</Button>
        </div>
      ) : (
        <ul className="grid gap-2 sm:grid-cols-2">
          {decision.options.map((o) => {
            const label = td("match", splitRefKey(o.labelKey).key);
            const auto = o.code === decision.autoOptionCode;
            return (
              <li key={o.code}>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => onCall(o.code)}
                  className="touch flex min-h-14 w-full items-center justify-between gap-2 rounded-[var(--radius-md)] border border-border-strong bg-pitch px-3 py-2 text-left transition-colors hover:border-led hover:bg-arena-3 disabled:opacity-50"
                >
                  <span className="min-w-0">
                    <span className="block font-heading font-semibold">{label}</span>
                    {(o.formation || o.defensePackage) && (
                      <span className="block text-[0.72rem] text-text-dim">{[o.formation && td("enums", `formation.${o.formation}`), o.defensePackage && td("enums", `defensePackage.${o.defensePackage}`), o.coverage && td("enums", `coverage.${o.coverage}`)].filter(Boolean).join(" · ")}</span>
                    )}
                  </span>
                  {auto && <Badge size="xs" tone="wood">{t("call.aiDefault")}</Badge>}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
