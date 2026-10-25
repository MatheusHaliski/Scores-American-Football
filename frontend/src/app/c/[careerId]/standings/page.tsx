"use client";
import { useParams } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api";
import type { Standings } from "@/lib/api/season-types";
import { useQuery } from "@/lib/hooks/useQuery";
import { defaultDir, sortRows, type SortDir, type SortKey } from "@/lib/season/standings";
import { useT, useTd } from "@/i18n/provider";
import { PageHeader } from "@/components/shell/PageHeader";
import { SeedBadge, StandingsTable } from "@/components/season/StandingsTable";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Segmented } from "@/components/ui/Field";
import { ErrorState, InlineAlert, PageSkeleton } from "@/components/ui/States";
import { IconTrophy } from "@/components/ui/icons";

type View = "division" | "conference";

function Legend({ s }: { s: Standings }) {
  const t = useT("season");
  const td = useTd();
  const divisions = s.conferences[0]?.divisions.length ?? 4;
  const sample = (seed: number | null, eliminated = false) => ({ seed, eliminated });
  return (
    <Card title={t("standings.legend.title")} tone="sunken">
      <ul className="grid gap-2 text-[0.85rem] sm:grid-cols-2 lg:grid-cols-4">
        <li className="flex items-center gap-2"><SeedBadge row={sample(1)} divisions={divisions} byeSeeds={s.byeSeeds} /> {t("standings.legend.bye")}</li>
        <li className="flex items-center gap-2"><SeedBadge row={sample(Math.min(divisions, s.byeSeeds + 1))} divisions={divisions} byeSeeds={s.byeSeeds} /> {t("standings.legend.division", { n: divisions })}</li>
        <li className="flex items-center gap-2"><SeedBadge row={sample(divisions + 1)} divisions={divisions} byeSeeds={s.byeSeeds} /> {t("standings.legend.wildCard")}</li>
        <li className="flex items-center gap-2"><SeedBadge row={sample(null, true)} divisions={divisions} byeSeeds={s.byeSeeds} /> {t("standings.legend.eliminated")}</li>
        <li className="flex items-center gap-2"><span className="inline-block h-4 w-6 rounded-sm border border-led/50 bg-orange/10" aria-hidden /> {t("standings.legend.you")}</li>
      </ul>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        {([
          ["division", s.divisionTiebreakers],
          ["wildCard", s.wildCardTiebreakers],
        ] as const).map(([k, chain]) => (
          <section key={k} aria-labelledby={`tb-${k}`}>
            <h3 id={`tb-${k}`} className="mb-1 font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-dim">
              {t("standings.tiebreak.title")} · {t(k === "division" ? "standings.tiebreak.division" : "standings.tiebreak.wildCard")}
            </h3>
            <ol className="list-decimal space-y-0.5 pl-5 text-[0.85rem] text-text-soft">
              {chain.map((c) => <li key={c}>{td("season", `tiebreaker.${c}`)}</li>)}
            </ol>
          </section>
        ))}
      </div>
      <p className="mt-3 text-[0.78rem] text-text-dim">{t("standings.tiebreak.hint")}</p>
    </Card>
  );
}

/** Classificação (RF16): por divisão ou por conferência, com sementes, garantias, eliminação, desempate e a franquia do usuário. */
export default function StandingsPage() {
  const t = useT("season");
  const { careerId } = useParams<{ careerId: string }>();
  const q = useQuery(() => api.season.standings(careerId), [careerId]);
  const [view, setView] = useState<View>("division");
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({ key: "rank", dir: "asc" });
  const onSort = (key: SortKey) => setSort((s) => (s.key === key ? { key, dir: s.dir === "asc" ? "desc" : "asc" } : { key, dir: defaultDir(key) }));

  if (q.loading && !q.data) return <PageSkeleton rows={3} />;
  if (q.error || !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const s = q.data;
  const divisions = s.conferences[0]?.divisions.length ?? 4;

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        eyebrow={t("standings.eyebrow")}
        title={t("standings.title", { year: String(s.seasonYear) })}
        description={t("standings.description", { week: s.throughWeek, total: s.regularSeasonWeeks })}
        actions={<Button variant="outline" size="sm" href={`/c/${careerId}/playoffs`} icon={<IconTrophy size={16} />}>{t("hub.playoffs.bracket")}</Button>}
      />
      <InlineAlert tone={s.official ? "good" : "info"}>{s.official ? t("standings.official") : t("standings.projection")}</InlineAlert>
      <Segmented
        label={t("standings.view.label")}
        value={view}
        onChange={(v) => {
          setView(v);
          setSort({ key: "rank", dir: "asc" });
        }}
        options={[
          { value: "division", label: t("standings.view.division") },
          { value: "conference", label: t("standings.view.conference") },
        ]}
      />
      <div className="grid gap-4 2xl:grid-cols-2 [&>*]:min-w-0">
        {s.conferences.map((conf) => (
          <Card key={conf.id} eyebrow={conf.shortName} title={conf.name}>
            {view === "conference" ? (
              <StandingsTable rows={sortRows(conf.rows, sort.key, sort.dir)} caption={conf.name} divisions={divisions} byeSeeds={s.byeSeeds} seedsPerConference={s.seedsPerConference} showCutline sort={sort} onSort={onSort} />
            ) : (
              <div className="grid gap-5 [&>*]:min-w-0">
                {conf.divisions.map((d) => (
                  <section key={d.id} aria-label={d.name} className="min-w-0">
                    <h3 className="mb-1 font-heading text-[0.75rem] uppercase tracking-[0.14em] text-text-muted">{d.name}</h3>
                    <StandingsTable rows={sortRows(d.rows, sort.key, sort.dir)} caption={d.name} divisions={divisions} byeSeeds={s.byeSeeds} seedsPerConference={s.seedsPerConference} showCutline={false} sort={sort} onSort={onSort} />
                  </section>
                ))}
              </div>
            )}
          </Card>
        ))}
      </div>
      <Legend s={s} />
    </div>
  );
}
