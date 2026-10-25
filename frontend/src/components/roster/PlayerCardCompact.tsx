"use client";
import Link from "next/link";
import type { ReactNode } from "react";
import type { PlayerCard } from "@/lib/api/types";
import { chipTier, fromCents } from "@/lib/career/format";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { Badge } from "@/components/ui/Badge";

const TIER_BG: Record<string, string> = {
  bronze: "bg-[image:var(--grad-bronze)] text-[#2b1a0a]",
  silver: "bg-[image:var(--grad-silver)] text-[#1b1f26]",
  gold: "bg-[image:var(--grad-gold)] text-[#3a2404]",
  icon: "bg-[image:var(--grad-icon)] text-[#2a0f04]",
};

/** Overall no gradiente do nível (Bronze, Prata, Ouro, Ícone, Elite), com o nome do nível para leitor de tela. */
export function TierChip({ tier, overall, size = "md" }: { tier: string; overall: number; size?: "sm" | "md" | "lg" }) {
  const td = useTd();
  const sizes = { sm: "size-8 text-sm", md: "size-10 text-lg", lg: "size-16 text-3xl" };
  return (
    <span className={cn("grid shrink-0 place-items-center rounded-[var(--radius-md)] font-display leading-none shadow-sm", TIER_BG[chipTier(tier)], tier === "ELITE" && "chip-shine", sizes[size])} title={td("enums", `tier.${tier}`)}>
      <span aria-hidden>{overall}</span>
      <span className="sr-only">{td("enums", "tierLabel", { tier: td("enums", `tier.${tier}`), overall })}</span>
    </span>
  );
}

/** Macros da família na ordem do card (PRE BRA LEI…), com o nome completo no `title`. */
export function MacroRow({ family, macros, dense }: { family: string; macros: Record<string, number>; dense?: boolean }) {
  const td = useTd();
  return (
    <dl className={cn("grid gap-1", dense ? "grid-cols-6" : "grid-cols-3 sm:grid-cols-6")}>
      {Object.entries(macros).map(([code, v]) => (
        <div key={code} className="min-w-0 rounded-[var(--radius-sm)] bg-pitch px-1 py-0.5 text-center" title={td("enums", `macro.${family}.${code}`)}>
          <dt className="truncate font-heading text-[0.56rem] uppercase tracking-[0.08em] text-text-dim">{td("enums", `macroAbbr.${family}.${code}`)}</dt>
          <dd className={cn("font-heading text-[0.85rem] tabular leading-tight", v >= 85 ? "text-led" : v >= 75 ? "text-text" : "text-text-soft")}>{v}</dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * Card compacto do elenco: overall e nível, posição (e secundária), situação, macros da família, contrato da temporada e vagas no
 * depth chart. `actions` entra no canto (mover, dispensar); o nome leva à página do jogador.
 */
export function PlayerCardCompact({ player, href, actions }: { player: PlayerCard; href: string; actions?: ReactNode }) {
  const t = useT("roster");
  const td = useTd();
  const f = useFormat();
  const p = player;
  return (
    <article className="flex min-w-0 flex-col gap-2 rounded-[var(--radius-lg)] border border-border bg-arena-2 p-3">
      <div className="flex items-start gap-3">
        <TierChip tier={p.tier} overall={p.overall} />
        <div className="min-w-0 flex-1">
          <Link href={href} className="block truncate font-heading font-semibold leading-tight hover:text-led focus-visible:outline-3 focus-visible:outline-rim" aria-label={t("card.open", { name: p.name })}>
            {p.name}
          </Link>
          <div className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[0.75rem] text-text-dim">
            <span className="font-heading text-text-soft">{p.position}</span>
            <span>#{p.jerseyNumber}</span>
            <span>{t("card.age", { n: p.age })}</span>
            {p.secondaryPosition && <span>{t("card.secondary", { pos: p.secondaryPosition })}</span>}
          </div>
        </div>
        {actions}
      </div>
      <div className="flex flex-wrap gap-1">
        {p.rosterStatus === "INACTIVE" && <Badge size="xs">{td("enums", "rosterStatus.INACTIVE")}</Badge>}
        {p.injuryWeeksOut > 0 && <Badge tone="bad" size="xs">{t("card.injured", { n: p.injuryWeeksOut })}</Badge>}
        {p.suspensionWeeks > 0 && <Badge tone="warn" size="xs">{t("card.suspended")}</Badge>}
        {p.depthChart.slice(0, 3).map((s) => (
          <Badge key={s} tone="led" size="xs">
            {td("enums", "slotLabel", { slot: td("enums", `slotShort.${s.replace(/\d+$/, "")}`), depth: s.match(/\d+$/)?.[0] ?? "" })}
          </Badge>
        ))}
        {p.depthChart.length > 3 && <Badge size="xs">+{p.depthChart.length - 3}</Badge>}
      </div>
      <MacroRow family={p.family} macros={p.macros} dense />
      <div className="flex items-center justify-between gap-2 text-[0.72rem] text-text-dim">
        <span className="truncate">{p.contractType ? td("enums", `contractType.${p.contractType}`) : "—"}{p.contractEndSeason != null && ` · ${t("card.contract", { year: p.contractEndSeason })}`}</span>
        <span className="shrink-0 tabular">{t("card.capHit", { value: f.money(fromCents(p.capHit)) })}</span>
      </div>
    </article>
  );
}
