"use client";
import Link from "next/link";
import { useState } from "react";
import type { Bracket, BracketGame, BracketTeam } from "@/lib/api/season-types";
import { bracketView, initialRoundIndex, reportHref, winnerSide, type BracketColumn } from "@/lib/season/bracket";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { ClubBadge } from "@/components/career/ClubBadge";
import { Badge } from "@/components/ui/Badge";
import { Tabs } from "@/components/ui/Tabs";
import { IconChevronRight, IconTrophy } from "@/components/ui/icons";

function TeamLine({ team, score, won, lost, user }: { team: BracketTeam; score: number | null; won: boolean; lost: boolean; user: boolean }) {
  return (
    <div className={cn("flex min-w-0 items-center gap-2 px-2 py-1.5", lost && "opacity-60", user && "text-led")}>
      <span className="w-4 shrink-0 text-right font-heading text-[0.7rem] tabular text-text-dim">{team.seed || ""}</span>
      <ClubBadge club={team.club} size={22} />
      <span className={cn("min-w-0 flex-1 truncate text-[0.85rem]", won && "font-semibold")}>{team.club.shortName}</span>
      <span className={cn("shrink-0 font-heading tabular", won ? "text-text" : "text-text-muted")}>{score ?? ""}</span>
    </div>
  );
}

/** Jogo da chave: as duas sementes, o placar, prorrogação, campo neutro e o link do relatório (rota do 6b) quando encerrado. */
export function BracketGameCard({ g, careerId, userClubId }: { g: BracketGame; careerId: string; userClubId: string | null }) {
  const t = useT("season");
  const f = useFormat();
  const side = winnerSide(g);
  const href = reportHref(careerId, g);
  const body = (
    <>
      <TeamLine team={g.away} score={g.awayScore} won={side === "away"} lost={side === "home"} user={g.away.club.id === userClubId} />
      <div className="border-t border-border/70" />
      <TeamLine team={g.home} score={g.homeScore} won={side === "home"} lost={side === "away"} user={g.home.club.id === userClubId} />
      <div className="flex flex-wrap items-center gap-1 border-t border-border/70 px-2 py-1 text-[0.66rem] text-text-dim">
        {g.status === "PROJECTED" ? <span>{t("playoffs.roundStatus.PROJECTED")}</span> : g.date ? <span>{f.dateShort(`${g.date}T12:00:00Z`)}</span> : null}
        {g.overtime && <span>· {t("playoffs.overtime")}</span>}
        {g.neutralSite && <span>· {t("playoffs.neutral")}</span>}
        {href && (
          <span className="ml-auto inline-flex items-center gap-0.5 text-led">
            <IconChevronRight size={12} />
          </span>
        )}
      </div>
    </>
  );
  const cls = cn("block overflow-hidden rounded-[var(--radius-md)] border bg-arena-2", g.userGame ? "border-led/60 shadow-led" : "border-border");
  return href ? (
    <Link href={href} className={cn(cls, "hover:border-led focus-visible:outline-3 focus-visible:outline-rim")} aria-label={t("playoffs.report", { away: g.away.club.name, home: g.home.club.name })}>
      {body}
    </Link>
  ) : (
    <div className={cls}>{body}</div>
  );
}

function ByeCard({ team }: { team: BracketTeam }) {
  const t = useT("season");
  return (
    <div className="flex min-w-0 items-center gap-2 rounded-[var(--radius-md)] border border-dashed border-border-strong bg-pitch/60 px-2 py-2 text-[0.85rem]">
      <span className="w-4 shrink-0 text-right font-heading text-[0.7rem] tabular text-text-dim">{team.seed}</span>
      <ClubBadge club={team.club} size={22} />
      <span className="min-w-0 flex-1 truncate">{team.club.shortName}</span>
      <Badge size="xs" tone="led">{t("playoffs.bye")}</Badge>
    </div>
  );
}

function Column({ col, careerId, userClubId }: { col: BracketColumn; careerId: string; userClubId: string | null }) {
  const t = useT("season");
  const td = useTd();
  const empty = col.games.length === 0 && col.byes.length === 0;
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <div className="flex items-baseline justify-between gap-2">
        <h4 className="font-heading text-[0.72rem] uppercase tracking-[0.14em] text-text-muted">{td("season", `playoffs.round.${col.round.kind}`)}</h4>
        <span className="text-[0.66rem] text-text-dim">{col.round.week != null ? t("playoffs.week", { n: col.round.week }) : td("season", `playoffs.roundStatus.${col.round.status}`)}</span>
      </div>
      <div className="flex flex-1 flex-col justify-around gap-2">
        {col.byes.map((b) => <ByeCard key={b.club.id} team={b} />)}
        {col.games.map((g, i) => <BracketGameCard key={g.fixtureId ?? `${col.round.index}-${i}`} g={g} careerId={careerId} userClubId={userClubId} />)}
        {empty && <div className="grid min-h-16 place-items-center rounded-[var(--radius-md)] border border-dashed border-border text-[0.8rem] text-text-dim">{t("playoffs.tbd")}</div>}
      </div>
    </div>
  );
}

/**
 * Chave dos playoffs (RF66). No desktop, uma coluna por rodada em cada conferência e a final no meio; no celular (390 px), abas por
 * rodada com os jogos das duas conferências em lista. Placar, prorrogação, campo neutro, folgas, sementes e o link de cada relatório.
 */
export function PlayoffBracket({ bracket, careerId }: { bracket: Bracket; careerId: string }) {
  const t = useT("season");
  const td = useTd();
  const view = bracketView(bracket);
  const [tab, setTab] = useState(String(initialRoundIndex(bracket)));
  const final = view.final;

  const champion = view.champion && (
    <div className="flex items-center gap-3 rounded-[var(--radius-lg)] border-2 border-led bg-arena-2 p-3 shadow-led">
      <IconTrophy size={28} className="text-led" />
      <ClubBadge club={view.champion} size={40} />
      <div className="min-w-0">
        <div className="eyebrow">{t("playoffs.champion")}</div>
        <div className="truncate font-heading text-lg font-semibold">{view.champion.name}</div>
      </div>
    </div>
  );

  return (
    <div className="flex flex-col gap-4">
      {/* desktop: cada conferência numa faixa com uma coluna por rodada; a final e o campeão embaixo */}
      <div className="hidden flex-col gap-5 lg:flex">
        {view.conferences.map((c) => (
          <section key={c.conference} aria-label={c.conference} className="min-w-0 rounded-[var(--radius-lg)] border border-border bg-pitch/40 p-3">
            <h3 className="mb-2 font-heading text-sm uppercase tracking-[0.16em] text-text-soft">{c.conference}</h3>
            <div className="grid gap-3" style={{ gridTemplateColumns: `repeat(${c.columns.length}, minmax(0, 1fr))` }}>
              {c.columns.map((col) => <Column key={col.round.index} col={col} careerId={careerId} userClubId={bracket.userClubId} />)}
            </div>
          </section>
        ))}
        {(final || champion) && (
          <section aria-label={t("playoffs.final")} className="mx-auto grid w-full max-w-2xl gap-3 sm:grid-cols-2">
            {final && <Column col={final} careerId={careerId} userClubId={bracket.userClubId} />}
            <div className="flex items-center">{champion}</div>
          </section>
        )}
      </div>

      {/* celular e tablet: abas por rodada, jogos em lista */}
      <div className="lg:hidden">
        <Tabs
          value={tab}
          onChange={setTab}
          tabs={bracket.rounds.map((r) => ({ value: String(r.index), label: <span title={td("season", `playoffs.round.${r.kind}`)}>{td("season", `playoffs.roundShort.${r.kind}`)}</span> }))}
        />
        {bracket.rounds
          .filter((r) => String(r.index) === tab)
          .map((r) => (
            <div key={r.index} role="tabpanel" aria-label={td("season", `playoffs.round.${r.kind}`)} className="mt-3 grid gap-4 sm:grid-cols-2">
              {r.kind === "FINAL" ? (
                <div className="grid gap-2 sm:col-span-2">
                  <div className="text-[0.72rem] text-text-dim">{r.week != null ? t("playoffs.week", { n: r.week }) : td("season", `playoffs.roundStatus.${r.status}`)}</div>
                  {r.games.length ? r.games.map((g, i) => <BracketGameCard key={g.fixtureId ?? i} g={g} careerId={careerId} userClubId={bracket.userClubId} />) : <div className="grid min-h-16 place-items-center rounded-[var(--radius-md)] border border-dashed border-border text-[0.8rem] text-text-dim">{t("playoffs.tbd")}</div>}
                  {champion}
                </div>
              ) : (
                view.conferences.map((c) => {
                  const col = c.columns.find((x) => x.round.index === r.index);
                  return col ? (
                    <section key={c.conference} aria-label={c.conference} className="grid gap-2">
                      <h3 className="font-heading text-[0.75rem] uppercase tracking-[0.14em] text-text-soft">{c.conference}</h3>
                      <Column col={col} careerId={careerId} userClubId={bracket.userClubId} />
                    </section>
                  ) : null;
                })
              )}
            </div>
          ))}
      </div>
    </div>
  );
}
