"use client";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

export function Tabs<T extends string>({ value, onChange, tabs, className }: { value: T; onChange: (v: T) => void; tabs: { value: T; label: ReactNode; badge?: ReactNode }[]; className?: string }) {
  return (
    <div role="tablist" className={cn("flex gap-1 border-b border-border overflow-x-auto hide-scrollbar", className)}>
      {tabs.map((t) => (
        <button
          key={t.value}
          role="tab"
          type="button"
          aria-selected={t.value === value}
          onClick={() => onChange(t.value)}
          className={cn(
            "relative h-11 px-3 sm:px-4 font-heading font-semibold uppercase tracking-[0.1em] text-[0.8rem] whitespace-nowrap transition-colors",
            t.value === value ? "text-led" : "text-text-muted hover:text-text",
          )}
        >
          <span className="inline-flex items-center gap-2">
            {t.label}
            {t.badge}
          </span>
          {t.value === value && <span className="absolute left-2 right-2 -bottom-px h-0.5 bg-led shadow-led" />}
        </button>
      ))}
    </div>
  );
}
