"use client";
import { useId, type ReactNode } from "react";
import { cn } from "@/lib/utils/cn";
import { useT } from "@/i18n/provider";
import { IconInfo, IconShield } from "@/components/ui/icons";

/**
 * Etiqueta da base legal (RF46 CA01): cada controle mostra o artigo da LGPD que fundamenta o tratamento
 * ou o direito exercido, com uma explicação didática acessível (tooltip nativo + descrição para leitores de tela).
 */
export function LgpdTag({ article, explain, className }: { article: string; explain?: string; className?: string }) {
  const id = useId();
  const t = useT("settings");
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-[var(--radius-xs)] border border-info/50 bg-info/10 px-1.5 py-0.5 text-[0.68rem] font-heading uppercase tracking-[0.08em] text-info whitespace-nowrap", className)} title={explain ?? undefined} aria-describedby={explain ? id : undefined}>
      <IconShield size={12} aria-hidden />
      <span className="sr-only">{t("lgpd.prefix")}</span>
      LGPD · {article}
      {explain && (
        <span id={id} className="sr-only">
          {explain}
        </span>
      )}
    </span>
  );
}

/** Linha de configuração: rótulo + descrição + etiqueta LGPD à esquerda; controle à direita (empilha no celular). */
export function SettingRow({ label, description, tag, control, className, htmlFor }: { label: ReactNode; description?: ReactNode; tag?: ReactNode; control: ReactNode; className?: string; htmlFor?: string }) {
  return (
    <div className={cn("flex flex-col gap-3 py-4 border-b border-border/70 last:border-0 sm:flex-row sm:items-start sm:justify-between", className)}>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          {htmlFor ? (
            <label htmlFor={htmlFor} className="font-heading font-semibold text-[0.98rem] text-text">
              {label}
            </label>
          ) : (
            <span className="font-heading font-semibold text-[0.98rem] text-text">{label}</span>
          )}
          {tag}
        </div>
        {description && <p className="mt-1 text-[0.85rem] leading-relaxed text-text-muted max-w-prose">{description}</p>}
      </div>
      <div className="shrink-0 sm:max-w-[55%]">{control}</div>
    </div>
  );
}

/** Cabeçalho de seção com título, resumo e (opcional) nota didática. */
export function SectionHeader({ title, summary, note }: { title: string; summary: string; note?: ReactNode }) {
  return (
    <header className="mb-2">
      <h2 className="font-display text-2xl sm:text-3xl">{title}</h2>
      <p className="mt-1 text-text-muted text-[0.95rem] max-w-2xl">{summary}</p>
      {note && (
        <p className="mt-3 flex gap-2 rounded-[var(--radius-md)] border border-border bg-pitch/70 p-3 text-[0.82rem] text-text-soft max-w-2xl">
          <IconInfo size={16} className="mt-0.5 shrink-0 text-info" aria-hidden />
          <span>{note}</span>
        </p>
      )}
    </header>
  );
}

/** Interruptor acessível (`role="switch"`), com rótulo próprio para leitores de tela. */
export function Switch({ checked, onChange, label, disabled, busy }: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean; busy?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      aria-busy={busy || undefined}
      disabled={disabled || busy}
      onClick={() => onChange(!checked)}
      className={cn(
        "relative inline-flex h-8 w-14 shrink-0 items-center rounded-full border transition-colors disabled:opacity-50 focus-visible:outline-3 focus-visible:outline-rim",
        checked ? "bg-orange border-orange" : "bg-arena-4 border-border-strong",
      )}
    >
      <span className={cn("absolute size-6 rounded-full bg-white shadow transition-transform", checked ? "translate-x-7" : "translate-x-1")} />
    </button>
  );
}
