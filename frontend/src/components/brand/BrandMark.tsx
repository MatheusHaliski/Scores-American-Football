"use client";
import { useT } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";

/**
 * Marca provisória do Scores: American Football — selo vetorial (bola oval com costura sobre placa laranja) + nome.
 * Substitui o `BrandMark` do basquete, que dependia do kit de marca dele (PNGs e texturas); o kit próprio do
 * futebol americano troca só este componente.
 */
export function BrandMark({ size = 32, withName = false, surface = "dark", className }: { size?: number; withName?: boolean; surface?: "dark" | "light"; className?: string }) {
  const t = useT("common");
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <svg width={size} height={size} viewBox="0 0 48 48" role="img" aria-label={t("brand.logoAlt")} className="shrink-0">
        <rect x="2" y="2" width="44" height="44" rx="11" fill="var(--orange)" />
        <rect x="2" y="2" width="44" height="44" rx="11" fill="none" stroke={surface === "light" ? "rgba(0,0,0,0.12)" : "rgba(255,255,255,0.18)"} strokeWidth="2" />
        <g transform="rotate(-35 24 24)">
          <ellipse cx="24" cy="24" rx="15" ry="9.5" fill="#fbf6ec" />
          <path d="M11 24h4M33 24h4" stroke="var(--orange-deep)" strokeWidth="2" strokeLinecap="round" />
          <path d="M18 24h12M20 21.5v5M24 21.5v5M28 21.5v5" stroke="#1e1810" strokeWidth="1.8" strokeLinecap="round" />
        </g>
      </svg>
      {withName && (
        <span className="flex flex-col leading-none" aria-hidden>
          <span className={cn("font-display tracking-wide", surface === "light" ? "text-neutral-900" : "text-text")} style={{ fontSize: Math.round(size * 0.62) }}>
            {t("app.name")}
          </span>
          <span className={cn("font-heading uppercase tracking-[0.24em]", surface === "light" ? "text-neutral-500" : "text-led")} style={{ fontSize: Math.max(9, Math.round(size * 0.24)) }}>
            {t("app.sport")}
          </span>
        </span>
      )}
    </span>
  );
}
