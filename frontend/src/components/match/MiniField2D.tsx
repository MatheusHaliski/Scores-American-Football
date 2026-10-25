"use client";
import type { ClubRef } from "@/lib/api/career-types";
import type { LiveMatch, PlayEvent } from "@/lib/api/match-types";
import { attacksUp, scrimmageMarkers } from "@/lib/match/live";
import { FIELD, HASH_FROM_SIDELINE_YD, hashYards, yardLines } from "@/lib/field/geometry";
import { useT } from "@/i18n/provider";

const W = FIELD.widthYd;
const X = (absYd: number) => absYd + FIELD.endZoneYd;
const SCRIMMAGE_TYPES = new Set(["RUN", "PASS"]);

/**
 * Campo 2D simples (RF27): end zones, linhas de 5 jardas e hash marks da geometria oficial (`lib/field`), a linha de scrimmage
 * (azul), a de primeira descida (amarela, some no "and goal"), a bola com o sentido do ataque e o último lance. A casa defende a
 * end zone da esquerda (jarda 0) e ataca para a direita.
 */
export function MiniField2D({ live, events, clubs, label }: { live: LiveMatch; events: PlayEvent[]; clubs: Map<string, ClubRef>; label: string }) {
  const t = useT("match");
  const up = attacksUp(live);
  const m = up == null ? null : scrimmageMarkers(live.ballOnAbsYd, live.distance, up);
  const hash = HASH_FROM_SIDELINE_YD.PRO;
  const last = [...events].reverse().find((e) => e.type != null && SCRIMMAGE_TYPES.has(e.type));
  const lastUp = last ? last.possessionClubId === live.home.clubId : null;
  const home = clubs.get(live.home.clubId);
  const away = clubs.get(live.away.clubId);
  const ballX = m ? X(m.scrimmageAbsYd) : X(50);
  return (
    <figure className="m-0">
      <svg viewBox={`0 0 ${FIELD.lengthYd} ${W}`} className="block h-auto w-full rounded-[var(--radius-md)] border border-border" role="img" aria-label={label}>
        <rect x={0} y={0} width={FIELD.lengthYd} height={W} fill="var(--turf)" />
        {Array.from({ length: 10 }).map((_, i) => (
          <rect key={i} x={X(i * 10)} y={0} width={5} height={W} fill="var(--turf-stripe)" />
        ))}
        <rect x={0} y={0} width={FIELD.endZoneYd} height={W} fill={home?.primaryColor ?? "var(--endzone-home)"} opacity={0.85} />
        <rect x={X(FIELD.playYd)} y={0} width={FIELD.endZoneYd} height={W} fill={away?.primaryColor ?? "var(--endzone-away)"} opacity={0.85} />
        <text x={5} y={W / 2} fill="var(--yard-line)" fontSize={4} textAnchor="middle" dominantBaseline="middle" transform={`rotate(-90 5 ${W / 2})`} fontFamily="var(--font-display)">{live.home.shortName}</text>
        <text x={FIELD.lengthYd - 5} y={W / 2} fill="var(--yard-line)" fontSize={4} textAnchor="middle" dominantBaseline="middle" transform={`rotate(90 ${FIELD.lengthYd - 5} ${W / 2})`} fontFamily="var(--font-display)">{live.away.shortName}</text>
        {yardLines().map((y) => (
          <g key={y.absYd}>
            <line x1={X(y.absYd)} x2={X(y.absYd)} y1={0} y2={W} stroke="var(--yard-line)" strokeWidth={y.goal ? 0.5 : 0.25} opacity={0.8} />
            {y.label != null && (
              <>
                <text x={X(y.absYd)} y={6} fill="var(--yard-line)" fontSize={3} textAnchor="middle" opacity={0.75}>{y.label}</text>
                <text x={X(y.absYd)} y={W - 3.5} fill="var(--yard-line)" fontSize={3} textAnchor="middle" opacity={0.75}>{y.label}</text>
              </>
            )}
          </g>
        ))}
        {hashYards().map((yd) => (
          <g key={yd} stroke="var(--yard-line)" strokeWidth={0.18} opacity={0.6}>
            <line x1={X(yd)} x2={X(yd)} y1={hash - 0.6} y2={hash + 0.6} />
            <line x1={X(yd)} x2={X(yd)} y1={W - hash - 0.6} y2={W - hash + 0.6} />
          </g>
        ))}
        {last && lastUp != null && (
          <line x1={X(last.ballOnAbsYd)} x2={X(Math.max(0, Math.min(100, last.ballOnAbsYd + (lastUp ? 1 : -1) * last.yards)))} y1={W / 2 + 4} y2={W / 2 + 4} stroke="var(--led)" strokeWidth={0.6} strokeDasharray="1.2 0.8" strokeLinecap="round">
            <title>{t("live.field.lastPlay", { yards: last.yards })}</title>
          </line>
        )}
        {m && (
          <>
            <line x1={X(m.scrimmageAbsYd)} x2={X(m.scrimmageAbsYd)} y1={0} y2={W} stroke="var(--scrimmage)" strokeWidth={0.6} />
            {!m.goalToGo && <line x1={X(m.firstDownAbsYd)} x2={X(m.firstDownAbsYd)} y1={0} y2={W} stroke="var(--first-down)" strokeWidth={0.6} />}
            <ellipse cx={ballX - m.direction * 0.9} cy={W / 2} rx={1.3} ry={0.75} fill="var(--wood)" stroke="var(--yard-line)" strokeWidth={0.15} />
            <polygon points={m.direction === 1 ? `${ballX + 1.2},${W / 2 - 1.4} ${ballX + 3.4},${W / 2} ${ballX + 1.2},${W / 2 + 1.4}` : `${ballX - 1.2},${W / 2 - 1.4} ${ballX - 3.4},${W / 2} ${ballX - 1.2},${W / 2 + 1.4}`} fill="var(--yard-line)" opacity={0.9} />
          </>
        )}
      </svg>
      <figcaption className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-[0.72rem] text-text-dim">
        <span className="inline-flex items-center gap-1.5"><span className="inline-block h-2.5 w-1 rounded-sm bg-[var(--scrimmage)]" aria-hidden /> {t("live.field.scrimmage")}</span>
        <span className="inline-flex items-center gap-1.5"><span className="inline-block h-2.5 w-1 rounded-sm bg-[var(--first-down)]" aria-hidden /> {t("live.field.firstDown")}</span>
        <span className="inline-flex items-center gap-1.5"><span className="inline-block h-0.5 w-3 rounded-sm bg-led" aria-hidden /> {t("live.field.lastPlayLegend")}</span>
      </figcaption>
    </figure>
  );
}
