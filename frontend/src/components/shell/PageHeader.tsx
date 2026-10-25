import type { ReactNode } from "react";
import Link from "next/link";
import { cn } from "@/lib/utils/cn";
import { IconChevronLeft } from "@/components/ui/icons";

/** Cabeçalho padrão de página: eyebrow (RF), título Anton, descrição, ações, voltar. */
export function PageHeader({ eyebrow, title, description, actions, back, className }: { eyebrow?: ReactNode; title: ReactNode; description?: ReactNode; actions?: ReactNode; back?: { href: string; label: string }; className?: string }) {
  return (
    <header className={cn("flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between mb-5", className)}>
      <div className="min-w-0">
        {back && (
          <Link href={back.href} className="inline-flex items-center gap-1 text-[0.8rem] text-text-muted hover:text-text mb-2 rounded focus-visible:outline-3 focus-visible:outline-rim">
            <IconChevronLeft size={16} /> {back.label}
          </Link>
        )}
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1 className="font-display text-3xl sm:text-4xl mt-1">{title}</h1>
        {description && <p className="text-text-muted mt-1 max-w-2xl text-[0.95rem]">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2 shrink-0">{actions}</div>}
    </header>
  );
}
