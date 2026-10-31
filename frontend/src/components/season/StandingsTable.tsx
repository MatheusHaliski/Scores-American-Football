"use client";
import type { StandingsRow } from "@/lib/api/season-types";
import { cutlineIndex, pctText, seedMark, type SeedMark, type SortDir, type SortKey } from "@/lib/season/standings";
import { useT, useTd } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { ClubBadge } from "@/components/career/ClubBadge";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { IconArrowDown, IconArrowUp } from "@/components/ui/icons";

const MARK_TONE: Record<Exclude<SeedMark, null>, BadgeTone> = { BYE: "led", DIVISION: "good", WILD_CARD: "info", ELIMINATED: "bad" };

/** Selo da semente/marca da linha (cor + texto, nunca só cor). */
export function SeedBadge({ row, divisions, byeSeeds }: { row: Pick<StandingsRow, "seed" | "eliminated">; divisions: number; byeSeeds: number }) {
  const t = useT("season");
  const mark = seedMark(row, divisions, byeSeeds);
  if (!mark) return null;
  if (mark === "ELIMINATED") {
    return (
      <Badge tone="bad" size="xs" title={t("standings.legend.eliminated")}>
        <span className="sm:hidden">{t("standings.eliminatedShort")}</span>
        <span className="hidden sm:inline">{t("standings.eliminated")}</span>
      </Badge>
    );
  }
  const label = mark === "BYE" ? t("standings.legend.bye") : mark === "DIVISION" ? t("standings.legend.division", { n: divisions }) : t("standings.legend.wildCard");
  return (
    <Badge tone={MARK_TONE[mark]} size="xs" title={label} aria-label={`${t("standings.seed", { n: row.seed ?? 0 })} · ${label}`}>
      {row.seed}
    </Badge>
  );
}

const COLUMNS: { key: SortKey; label: "rank" | "record" | "pct" | "pf" | "pa" | "diff" | "streak" | "home" | "away" | "division" | "conference"; hideBelow?: "sm" | "md" | "lg" }[] = [
  { key: "record", label: "record" },
  { key: "pct", label: "pct" },
  { key: "pf", label: "pf", hideBelow: "sm" },
  { key: "pa", label: "pa", hideBelow: "sm" },
  { key: "diff", label: "diff" },
  { key: "streak", label: "streak" },
  { key: "home", label: "home", hideBelow: "md" },
  { key: "away", label: "away", hideBelow: "md" },
  { key: "division", label: "division", hideBelow: "lg" },
  { key: "conference", label: "conference", hideBelow: "lg" },
];

const HIDE: Record<string, string> = { sm: "hidden sm:table-cell", md: "hidden md:table-cell", lg: "hidden lg:table-cell" };

/**
 * Tabela da divisão ou da conferência (RF16): V-D-E, %, PP/PC, saldo, sequência, casa/fora, divisão e conferência; semente (folga,
 * campeão de divisão, wild card) ou eliminação; o critério de desempate sob o nome; a franquia do usuário destacada; na conferência,
 * a linha de corte dos playoffs. Cabeçalhos ordenam (aria-sort); no celular as colunas secundárias somem e a tabela não estoura a
 * largura da página.
 */
export function StandingsTable({
  rows,
  caption,
  divisions,
  byeSeeds,
  seedsPerConference,
  showCutline,
  sort,
  onSort,
}: {
  rows: StandingsRow[];
  caption: string;
  divisions: number;
  byeSeeds: number;
  seedsPerConference: number;
  showCutline: boolean;
  sort: { key: SortKey; dir: SortDir };
  onSort: (key: SortKey) => void;
}) {
  const t = useT("season");
  const td = useTd();
  const cut = showCutline && sort.key === "rank" && sort.dir === "asc" ? cutlineIndex(rows, seedsPerConference) : -1;
  const ariaSort = (k: SortKey): "ascending" | "descending" | "none" => (sort.key === k ? (sort.dir === "asc" ? "ascending" : "descending") : "none");
  const header = (k: SortKey, label: string, long: string, className?: string) => (
    <th scope="col" aria-sort={ariaSort(k)} className={cn("px-1.5 pb-2 text-right font-normal", className)}>
      <button type="button" onClick={() => onSort(k)} title={long} aria-label={t("standings.sort", { column: long })} className={cn("inline-flex min-h-8 items-center gap-0.5 rounded px-1 hover:text-text", sort.key === k && "text-led")}>
        {label}
        {sort.key === k && (sort.dir === "asc" ? <IconArrowUp size={12} /> : <IconArrowDown size={12} />)}
      </button>
    </th>
  );

  return (
    <div className="-mx-1 overflow-x-auto px-1">
      <table className="w-full min-w-0 whitespace-nowrap text-[0.82rem] sm:text-[0.85rem]">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="font-heading text-[0.66rem] uppercase tracking-[0.1em] text-text-dim">
            {header("rank", t("standings.col.rank"), t("standings.colLong.rank"), "w-8 text-left")}
            <th scope="col" className="px-1.5 pb-2 text-left font-normal">{t("standings.col.team")}</th>
            {COLUMNS.map((c) => header(c.key, t(`standings.col.${c.label}`), t(`standings.colLong.${c.label}`), c.hideBelow ? HIDE[c.hideBelow] : undefined))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr
              key={r.club.id}
              aria-current={r.userClub ? "true" : undefined}
              className={cn("border-t border-border", r.userClub && "bg-orange/10 text-led", i === cut && "border-b-2 border-b-led/70")}
            >
              <td className="px-1.5 py-1.5 tabular">{r.rank}</td>
              <td className="max-w-[11rem] px-1.5 py-1.5 sm:max-w-none">
                <div className="flex min-w-0 items-center gap-2">
                  <ClubBadge club={r.club} size={22} />
                  <div className="min-w-0">
                    <div className="flex min-w-0 items-center gap-1.5">
                      <span className="truncate font-semibold sm:hidden">{r.club.shortName}</span>
                      <span className="hidden truncate font-semibold sm:inline">{r.club.name}</span>
                      <SeedBadge row={r} divisions={divisions} byeSeeds={byeSeeds} />
                    </div>
                    {((r.clinch && !r.eliminated) || r.tiebreaker) && (
                      <div className="truncate text-[0.66rem] text-text-dim">
                        {r.clinch && !r.eliminated && <span className="text-[var(--good-text)]">{td("season", `standings.clinch.${r.clinch}`)}</span>}
                        {r.clinch && !r.eliminated && r.tiebreaker && " · "}
                        {r.tiebreaker && td("season", `tiebreaker.${r.tiebreaker}`)}
                      </div>
                    )}
                  </div>
                </div>
              </td>
              <td className="px-1.5 py-1.5 text-right tabular">{r.record.summary}</td>
              <td className="px-1.5 py-1.5 text-right tabular">{pctText(r.winPct)}</td>
              <td className={cn("px-1.5 py-1.5 text-right tabular", HIDE.sm)}>{r.pointsFor}</td>
              <td className={cn("px-1.5 py-1.5 text-right tabular", HIDE.sm)}>{r.pointsAgainst}</td>
              <td className={cn("px-1.5 py-1.5 text-right tabular", r.pointDiff > 0 ? "text-[var(--good-text)]" : r.pointDiff < 0 ? "text-[var(--bad-text)]" : undefined)}>
                {r.pointDiff > 0 ? `+${r.pointDiff}` : r.pointDiff}
              </td>
              <td className="px-1.5 py-1.5 text-right tabular">{r.streak}</td>
              <td className={cn("px-1.5 py-1.5 text-right tabular", HIDE.md)}>{r.home.summary}</td>
              <td className={cn("px-1.5 py-1.5 text-right tabular", HIDE.md)}>{r.away.summary}</td>
              <td className={cn("px-1.5 py-1.5 text-right tabular", HIDE.lg)}>{r.division.summary}</td>
              <td className={cn("px-1.5 py-1.5 text-right tabular", HIDE.lg)}>{r.conference.summary}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {cut >= 0 && <p className="mt-1 text-[0.7rem] text-text-dim">{t("standings.cutline", { n: seedsPerConference })}</p>}
    </div>
  );
}
