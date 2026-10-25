import type { ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

/** Célula de placar/estatística: número tabular grande + rótulo (estilo "board" da aula). */
export function Stat({ value, label, led, size = "md", className, hint }: { value: ReactNode; label: ReactNode; led?: boolean; size?: "sm" | "md" | "lg"; className?: string; hint?: ReactNode }) {
  const sizes = { sm: "text-xl", md: "text-3xl", lg: "text-5xl" };
  return (
    <div className={cn("bg-arena-2 px-3 py-3 text-center rounded-[var(--radius-sm)]", className)}>
      <div className={cn("font-heading font-bold tabular leading-none", sizes[size], led ? "led-text" : "text-text")}>{value}</div>
      <div className="font-heading uppercase tracking-[0.14em] text-[0.62rem] text-text-dim mt-2">{label}</div>
      {hint && <div className="text-[0.7rem] text-text-muted mt-0.5">{hint}</div>}
    </div>
  );
}

export function ProgressBar({ value, max = 100, tone = "orange", label, className, showValue }: { value: number; max?: number; tone?: "orange" | "good" | "bad" | "warn" | "led" | "auto"; label?: ReactNode; className?: string; showValue?: boolean }) {
  const p = Math.max(0, Math.min(100, (value / max) * 100));
  const autoTone = p >= 70 ? "good" : p >= 40 ? "warn" : "bad";
  const t = tone === "auto" ? autoTone : tone;
  const colors = { orange: "bg-orange", good: "bg-good", bad: "bg-bad", warn: "bg-warn", led: "bg-led shadow-led" };
  return (
    <div className={cn("flex flex-col gap-1", className)}>
      {(label || showValue) && (
        <div className="flex justify-between text-[0.72rem] font-heading uppercase tracking-[0.1em] text-text-muted">
          <span>{label}</span>
          {showValue && <span className="tabular text-text">{Math.round(value)}</span>}
        </div>
      )}
      <div className="h-2 rounded-full bg-arena-4 overflow-hidden" role="progressbar" aria-valuenow={Math.round(value)} aria-valuemin={0} aria-valuemax={max}>
        <div className={cn("h-full rounded-full transition-[width] duration-[var(--dur-slow)]", colors[t])} style={{ width: `${p}%` }} />
      </div>
    </div>
  );
}
