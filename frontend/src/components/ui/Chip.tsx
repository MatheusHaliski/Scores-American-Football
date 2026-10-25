"use client";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

/** Tier visual de um valor 0–100 (estilos, overall e gauges chegam com os blocos de jogo). */
export type Tier = "bronze" | "silver" | "gold" | "icon";

export interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  selected?: boolean;
  tier?: Tier;
  icon?: ReactNode;
  shine?: boolean;
}

const tierBg: Record<Tier, string> = {
  bronze: "bg-[image:var(--grad-bronze)] text-[#2b1a0a]",
  silver: "bg-[image:var(--grad-silver)] text-[#1b1f26]",
  gold: "bg-[image:var(--grad-gold)] text-[#3a2404]",
  icon: "bg-[image:var(--grad-icon)] text-[#2a0f04]",
};

/** Chip clicável (filtros) ou decorativo com gradiente por tier (estilos de jogo). */
export function Chip({ selected, tier, icon, shine, className, children, ...rest }: ChipProps) {
  return (
    <button
      type="button"
      aria-pressed={rest.onClick ? selected : undefined}
      className={cn(
        "inline-flex items-center gap-1.5 h-9 px-3 rounded-[var(--radius-pill)] font-heading font-semibold text-[0.78rem] uppercase tracking-[0.08em] border transition-colors duration-[var(--dur-base)]",
        tier ? cn(tierBg[tier], "border-transparent shadow-sm", shine && "chip-shine") : selected ? "bg-orange text-white border-orange" : "bg-arena-3 text-text-soft border-border-strong hover:border-rim hover:text-text",
        !rest.onClick && "cursor-default",
        className,
      )}
      {...rest}
    >
      {icon}
      {children}
    </button>
  );
}
