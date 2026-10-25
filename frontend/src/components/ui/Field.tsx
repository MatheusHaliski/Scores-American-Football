"use client";
import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

const inputCls = "w-full h-11 px-3 rounded-[var(--radius-md)] bg-pitch border border-border-strong text-text placeholder:text-text-dim focus:border-rim focus:outline-none focus-visible:ring-2 focus-visible:ring-rim/50 disabled:opacity-50";

interface Common {
  label?: ReactNode;
  hint?: ReactNode;
  error?: string | null;
  className?: string;
}

export function TextField({ label, hint, error, className, id, ...rest }: Common & InputHTMLAttributes<HTMLInputElement>) {
  const auto = useId();
  const fid = id ?? auto;
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label && (
        <label htmlFor={fid} className="font-heading text-[0.8rem] uppercase tracking-[0.12em] text-text-muted">
          {label}
        </label>
      )}
      <input id={fid} className={cn(inputCls, error && "border-bad")} aria-invalid={!!error} aria-describedby={error ? `${fid}-err` : hint ? `${fid}-hint` : undefined} {...rest} />
      {error ? (
        <p id={`${fid}-err`} className="text-[0.8rem] text-[var(--bad-text)]">
          {error}
        </p>
      ) : hint ? (
        <p id={`${fid}-hint`} className="text-[0.8rem] text-text-dim">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export function SelectField({ label, hint, error, className, id, children, ...rest }: Common & SelectHTMLAttributes<HTMLSelectElement>) {
  const auto = useId();
  const fid = id ?? auto;
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label && (
        <label htmlFor={fid} className="font-heading text-[0.8rem] uppercase tracking-[0.12em] text-text-muted">
          {label}
        </label>
      )}
      <select id={fid} className={cn(inputCls, "appearance-none bg-[url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2216%22 height=%2216%22 viewBox=%220 0 24 24%22 fill=%22none%22 stroke=%22%23b5a78d%22 stroke-width=%222%22><path d=%22m6 9 6 6 6-6%22/></svg>')] bg-no-repeat bg-[right_10px_center] pr-9", error && "border-bad")} aria-invalid={!!error} {...rest}>
        {children}
      </select>
      {error ? <p className="text-[0.8rem] text-[var(--bad-text)]">{error}</p> : hint ? <p className="text-[0.8rem] text-text-dim">{hint}</p> : null}
    </div>
  );
}

export interface SliderFieldProps extends Common {
  value: number;
  onChange: (v: number) => void;
  min?: number;
  max?: number;
  step?: number;
  leftLabel?: string;
  rightLabel?: string;
  format?: (v: number) => string;
}

export function SliderField({ label, hint, value, onChange, min = 0, max = 100, step = 5, leftLabel, rightLabel, format, className }: SliderFieldProps) {
  const id = useId();
  const p = ((value - min) / (max - min)) * 100;
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <div className="flex items-center justify-between">
        {label && (
          <label htmlFor={id} className="font-heading text-[0.8rem] uppercase tracking-[0.12em] text-text-muted">
            {label}
          </label>
        )}
        <span className="led-text text-sm">{format ? format(value) : value}</span>
      </div>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full h-11 cursor-pointer accent-orange"
        style={{ background: `linear-gradient(90deg, var(--orange) ${p}%, var(--arena-4) ${p}%)`, borderRadius: 999, height: 6, marginTop: 18, marginBottom: 18 }}
      />
      {(leftLabel || rightLabel) && (
        <div className="flex justify-between text-[0.72rem] uppercase tracking-[0.1em] text-text-dim font-heading">
          <span>{leftLabel}</span>
          <span>{rightLabel}</span>
        </div>
      )}
      {hint && <p className="text-[0.8rem] text-text-dim">{hint}</p>}
    </div>
  );
}

export interface SegmentedProps<T extends string> extends Common {
  value: T;
  onChange: (v: T) => void;
  options: { value: T; label: ReactNode; disabled?: boolean }[];
  size?: "sm" | "md";
}

export function Segmented<T extends string>({ label, value, onChange, options, className, size = "md" }: SegmentedProps<T>) {
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label && <span className="font-heading text-[0.8rem] uppercase tracking-[0.12em] text-text-muted">{label}</span>}
      <div role="radiogroup" className="inline-flex flex-wrap gap-1 p-1 rounded-[var(--radius-md)] bg-pitch border border-border">
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={o.value === value}
            disabled={o.disabled}
            onClick={() => onChange(o.value)}
            className={cn(
              "rounded-[var(--radius-sm)] font-heading font-semibold uppercase tracking-[0.08em] transition-colors disabled:opacity-40",
              size === "sm" ? "h-9 px-2.5 text-[0.72rem]" : "h-10 px-3.5 text-[0.8rem]",
              o.value === value ? "bg-orange text-white shadow-sm" : "text-text-muted hover:text-text hover:bg-arena-3",
            )}
          >
            {o.label}
          </button>
        ))}
      </div>
    </div>
  );
}

export function Toggle({ label, checked, onChange, hint }: { label: ReactNode; checked: boolean; onChange: (v: boolean) => void; hint?: ReactNode }) {
  return (
    <label className="flex items-center justify-between gap-4 py-2 cursor-pointer">
      <span className="flex flex-col">
        <span className="text-text text-[0.95rem]">{label}</span>
        {hint && <span className="text-text-dim text-[0.8rem]">{hint}</span>}
      </span>
      <button type="button" role="switch" aria-checked={checked} onClick={() => onChange(!checked)} className={cn("relative w-12 h-7 rounded-full border transition-colors shrink-0", checked ? "bg-orange border-orange" : "bg-arena-4 border-border-strong")}>
        <span className={cn("absolute top-0.5 size-6 rounded-full bg-white shadow transition-transform", checked ? "translate-x-[22px]" : "translate-x-0.5")} />
      </button>
    </label>
  );
}
