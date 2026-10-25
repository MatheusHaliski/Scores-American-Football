"use client";
import type { PlayerBoxScore } from "@/lib/api/match-types";
import { useT } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";

export const BOX_TABS = ["passing", "rushing", "receiving", "defense", "kicking", "punting", "returns"] as const;
export type BoxTab = (typeof BOX_TABS)[number];

type Cell = string | number | null;
interface Column {
  key: string;
  label: string;
  value: (p: PlayerBoxScore) => Cell;
}

/** Linhas de um grupo do box score: só quem fez algo nele, ordenadas pelo número principal. */
export function boxRows(players: PlayerBoxScore[], tab: BoxTab): PlayerBoxScore[] {
  const main: Record<BoxTab, (p: PlayerBoxScore) => number> = {
    passing: (p) => p.passing?.yards ?? -1,
    rushing: (p) => p.rushing?.yards ?? -1,
    receiving: (p) => p.receiving?.yards ?? -1,
    defense: (p) => (p.defense ? p.defense.soloTackles * 10 + p.defense.sacks * 5 + p.defense.interceptions * 20 : -1),
    kicking: (p) => (p.kicking ? p.kicking.fgAttempts + p.kicking.patAttempts : -1),
    punting: (p) => p.punting?.punts ?? -1,
    returns: (p) => (p.returns ? p.returns.kickReturnYards + p.returns.puntReturnYards : -1),
  };
  return players.filter((p) => p[tab] != null).sort((a, b) => main[tab](b) - main[tab](a));
}

/**
 * Box score AF de um time por grupo (passe, corrida, recepção, defesa, chutes, punts e retornos). Mobile-first: rolagem
 * horizontal com o nome fixo. Tackles assistidos e QB hits o motor não gera (D-35): "—".
 */
export function BoxScoreTable({ players, tab }: { players: PlayerBoxScore[]; tab: BoxTab }) {
  const t = useT("match");
  const tc = useT("common");
  const f = useFormat();
  const dash = tc("state.none");
  const cols: Record<BoxTab, Column[]> = {
    passing: [
      { key: "ca", label: t("box.compAtt"), value: (p) => `${p.passing!.completions}/${p.passing!.attempts}` },
      { key: "yds", label: t("box.yds"), value: (p) => p.passing!.yards },
      { key: "td", label: t("box.td"), value: (p) => p.passing!.touchdowns },
      { key: "int", label: t("box.int"), value: (p) => p.passing!.interceptions },
      { key: "sck", label: t("box.sacked"), value: (p) => `${p.passing!.sacked}-${p.passing!.sackYards}` },
      { key: "lng", label: t("box.lng"), value: (p) => p.passing!.longest },
      { key: "rtg", label: t("box.rating"), value: (p) => f.num(p.passing!.rating, 1) },
    ],
    rushing: [
      { key: "att", label: t("box.att"), value: (p) => p.rushing!.attempts },
      { key: "yds", label: t("box.yds"), value: (p) => p.rushing!.yards },
      { key: "avg", label: t("box.avg"), value: (p) => f.num(p.rushing!.average, 1) },
      { key: "td", label: t("box.td"), value: (p) => p.rushing!.touchdowns },
      { key: "fum", label: t("box.fumLost"), value: (p) => `${p.rushing!.fumbles}/${p.rushing!.fumblesLost}` },
      { key: "lng", label: t("box.lng"), value: (p) => p.rushing!.longest },
    ],
    receiving: [
      { key: "rec", label: t("box.recTgt"), value: (p) => `${p.receiving!.receptions}/${p.receiving!.targets}` },
      { key: "yds", label: t("box.yds"), value: (p) => p.receiving!.yards },
      { key: "yac", label: t("box.yac"), value: (p) => p.receiving!.yardsAfterCatch },
      { key: "td", label: t("box.td"), value: (p) => p.receiving!.touchdowns },
      { key: "drp", label: t("box.drops"), value: (p) => p.receiving!.drops },
      { key: "lng", label: t("box.lng"), value: (p) => p.receiving!.longest },
    ],
    defense: [
      { key: "tkl", label: t("box.tackles"), value: (p) => p.defense!.soloTackles },
      { key: "ast", label: t("box.assisted"), value: (p) => p.defense!.assistedTackles },
      { key: "tfl", label: t("box.tfl"), value: (p) => p.defense!.tacklesForLoss },
      { key: "sck", label: t("box.sacks"), value: (p) => p.defense!.sacks },
      { key: "qbh", label: t("box.qbHits"), value: (p) => p.defense!.qbHits },
      { key: "int", label: t("box.int"), value: (p) => p.defense!.interceptions },
      { key: "pd", label: t("box.pd"), value: (p) => p.defense!.passesDefended },
      { key: "ff", label: t("box.ff"), value: (p) => p.defense!.forcedFumbles },
      { key: "fr", label: t("box.fr"), value: (p) => p.defense!.fumbleRecoveries },
      { key: "td", label: t("box.td"), value: (p) => p.defense!.touchdowns },
    ],
    kicking: [
      { key: "fg", label: t("box.fg"), value: (p) => `${p.kicking!.fgMade}/${p.kicking!.fgAttempts}` },
      { key: "039", label: t("box.fg0to39"), value: (p) => `${p.kicking!.fgMade0to39}/${p.kicking!.fgAttempts0to39}` },
      { key: "4049", label: t("box.fg40to49"), value: (p) => `${p.kicking!.fgMade40to49}/${p.kicking!.fgAttempts40to49}` },
      { key: "50", label: t("box.fg50plus"), value: (p) => `${p.kicking!.fgMade50plus}/${p.kicking!.fgAttempts50plus}` },
      { key: "lng", label: t("box.lng"), value: (p) => p.kicking!.longest },
      { key: "pat", label: t("box.pat"), value: (p) => `${p.kicking!.patMade}/${p.kicking!.patAttempts}` },
    ],
    punting: [
      { key: "no", label: t("box.punts"), value: (p) => p.punting!.punts },
      { key: "yds", label: t("box.yds"), value: (p) => p.punting!.yards },
      { key: "avg", label: t("box.avg"), value: (p) => f.num(p.punting!.average, 1) },
      { key: "i20", label: t("box.inside20"), value: (p) => p.punting!.inside20 },
      { key: "tb", label: t("box.touchbacks"), value: (p) => p.punting!.touchbacks },
      { key: "lng", label: t("box.lng"), value: (p) => p.punting!.longest },
    ],
    returns: [
      { key: "kr", label: t("box.kickReturns"), value: (p) => `${p.returns!.kickReturns}-${p.returns!.kickReturnYards}` },
      { key: "pr", label: t("box.puntReturns"), value: (p) => `${p.returns!.puntReturns}-${p.returns!.puntReturnYards}` },
      { key: "td", label: t("box.td"), value: (p) => p.returns!.touchdowns },
      { key: "lng", label: t("box.lng"), value: (p) => p.returns!.longest },
    ],
  };
  const rows = boxRows(players, tab);
  const columns = cols[tab];
  if (!rows.length) return <p className="rounded-[var(--radius-md)] border border-dashed border-border px-3 py-4 text-center text-sm text-text-dim">{t("box.empty")}</p>;
  return (
    <div className="scrollbar-thin overflow-x-auto rounded-[var(--radius-md)] border border-border">
      <table className={cn("w-full text-sm tabular", columns.length > 6 ? "min-w-[560px]" : "min-w-[420px]")}>
        <thead className="bg-pitch font-heading text-[0.66rem] uppercase tracking-[0.12em] text-text-dim">
          <tr>
            <th scope="col" className="sticky left-0 bg-pitch px-3 py-2 text-left font-medium">{t("box.player")}</th>
            {columns.map((c) => <th scope="col" key={c.key} className="px-2 py-2 text-right font-medium">{c.label}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((p) => (
            <tr key={p.playerId} className="border-t border-border/60">
              <th scope="row" className="sticky left-0 whitespace-nowrap bg-arena-2 px-3 py-1.5 text-left font-normal">
                <span className="mr-1 text-text-dim">#{p.jerseyNumber}</span>
                {p.name} <span className="text-[0.7rem] text-text-dim">{p.position}</span>
              </th>
              {columns.map((c) => {
                const v = c.value(p);
                return <td key={c.key} className="px-2 py-1.5 text-right">{v == null ? dash : v}</td>;
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
