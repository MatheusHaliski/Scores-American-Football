"use client";
import type { ReactNode } from "react";
import { useT } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { Button } from "./Button";
import { IconAlert, IconBall } from "./icons";

/** Estado vazio desenhado (sem partida, sem propostas, sem dados). */
export function EmptyState({ icon, title, body, action, className, compact }: { icon?: ReactNode; title: string; body?: ReactNode; action?: ReactNode; className?: string; compact?: boolean }) {
  return (
    <div className={cn("flex flex-col items-center text-center rounded-[var(--radius-lg)] border border-dashed border-border-strong bg-pitch/50", compact ? "px-4 py-6 gap-2" : "px-6 py-10 gap-3", className)}>
      <div className="size-12 rounded-full grid place-items-center bg-arena-3 text-wood-light">{icon ?? <IconBall size={24} />}</div>
      <h3 className="font-heading font-semibold text-lg">{title}</h3>
      {body && <p className="text-text-muted text-[0.92rem] max-w-md leading-relaxed">{body}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

/** Estado de erro com ação de tentar novamente. */
export function ErrorState({ title, message, onRetry, className }: { title?: string; message?: string | null; onRetry?: () => void; className?: string }) {
  const t = useT("common");
  return (
    <div role="alert" className={cn("flex flex-col items-center text-center gap-3 rounded-[var(--radius-lg)] border border-bad/50 bg-bad/10 px-6 py-8", className)}>
      <div className="size-12 rounded-full grid place-items-center bg-bad/20 text-[var(--bad-text)]">
        <IconAlert size={24} />
      </div>
      <h3 className="font-heading font-semibold text-lg">{title ?? t("state.errorTitle")}</h3>
      {message && <p className="text-text-soft text-[0.92rem] max-w-md">{message}</p>}
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          {t("actions.retry")}
        </Button>
      )}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("skeleton", className)} aria-hidden />;
}

export function PageSkeleton({ rows = 3 }: { rows?: number }) {
  const t = useT("common");
  return (
    <div className="flex flex-col gap-4" aria-busy aria-label={t("state.loading")}>
      <Skeleton className="h-28 w-full" />
      <div className="grid gap-4 md:grid-cols-2">
        {Array.from({ length: rows * 2 }).map((_, i) => (
          <Skeleton key={i} className="h-40 w-full" />
        ))}
      </div>
    </div>
  );
}

export function InlineAlert({ tone = "info", children, className }: { tone?: "info" | "warn" | "bad" | "good"; children: ReactNode; className?: string }) {
  const tones = { info: "border-info/50 bg-info/10 text-[var(--info-text)]", warn: "border-warn/50 bg-warn/10 text-[var(--warn-text)]", bad: "border-bad/50 bg-bad/10 text-[var(--bad-text)]", good: "border-good/50 bg-good/10 text-[var(--good-text)]" };
  return (
    <div role={tone === "bad" ? "alert" : "status"} className={cn("flex gap-2 items-start rounded-[var(--radius-md)] border px-3 py-2.5 text-[0.9rem]", tones[tone], className)}>
      <IconAlert size={18} className="shrink-0 mt-0.5" />
      <div>{children}</div>
    </div>
  );
}
