import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

export interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, "title"> {
  title?: ReactNode;
  eyebrow?: ReactNode;
  action?: ReactNode;
  padded?: boolean;
  tone?: "default" | "raised" | "sunken" | "orange";
}

export function Card({ title, eyebrow, action, padded = true, tone = "default", className, children, ...rest }: CardProps) {
  const tones = {
    default: "bg-arena-2 border-border",
    raised: "bg-arena-3 border-border-strong shadow-md",
    sunken: "bg-pitch border-border",
    orange: "bg-arena-2 border-[color:var(--border-orange)] shadow-orange",
  };
  return (
    <section className={cn("min-w-0 rounded-[var(--radius-lg)] border", tones[tone], padded && "p-4 sm:p-5", className)} {...rest}>
      {(title || eyebrow || action) && (
        <header className={cn("flex items-start justify-between gap-3", padded ? "mb-3" : "px-4 pt-4 mb-2")}>
          <div className="min-w-0">
            {eyebrow && <div className="eyebrow mb-1">{eyebrow}</div>}
            {title && <h3 className="font-heading font-semibold text-lg leading-tight text-text truncate">{title}</h3>}
          </div>
          {action && <div className="shrink-0">{action}</div>}
        </header>
      )}
      {children}
    </section>
  );
}
