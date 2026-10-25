import type { HTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

export type BadgeTone = "neutral" | "orange" | "led" | "good" | "bad" | "warn" | "info" | "wood";

const tones: Record<BadgeTone, string> = {
  neutral: "bg-arena-4 text-text-soft border-border-strong",
  orange: "bg-orange/15 text-rim border-orange/50",
  led: "bg-pitch text-led border-led/60 shadow-led",
  good: "bg-good/15 text-[var(--good-text)] border-good/50",
  bad: "bg-bad/15 text-[var(--bad-text)] border-bad/60",
  warn: "bg-warn/15 text-[var(--warn-text)] border-warn/50",
  info: "bg-info/15 text-[var(--info-text)] border-info/50",
  wood: "bg-wood/20 text-wood-light border-wood/60",
};

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
  size?: "xs" | "sm" | "md";
}

export function Badge({ tone = "neutral", size = "sm", className, children, ...rest }: BadgeProps) {
  const sizes = { xs: "text-[0.62rem] px-1.5 h-5", sm: "text-[0.7rem] px-2 h-6", md: "text-[0.8rem] px-2.5 h-7" };
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-[var(--radius-pill)] border font-heading font-semibold uppercase tracking-[0.1em] whitespace-nowrap", tones[tone], sizes[size], className)} {...rest}>
      {children}
    </span>
  );
}
