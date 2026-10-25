"use client";
import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { useT } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { IconClose } from "./icons";

export interface DialogProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  eyebrow?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: "sm" | "md" | "lg" | "xl" | "full";
  /** impede fechar clicando fora / ESC (ex.: painel tático obrigatório) */
  locked?: boolean;
}

const sizes = { sm: "max-w-md", md: "max-w-xl", lg: "max-w-3xl", xl: "max-w-6xl", full: "max-w-[min(1400px,96vw)]" };

/** Modal acessível: foco preso, ESC fecha, role=dialog. Renderiza via portal. */
export function Dialog({ open, onClose, title, eyebrow, children, footer, size = "md", locked }: DialogProps) {
  const id = useId();
  const ref = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();
  const t = useT("common");

  useEffect(() => {
    if (!open) return;
    const prev = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !locked) onClose();
      if (e.key === "Tab" && ref.current) {
        const f = ref.current.querySelectorAll<HTMLElement>('button,[href],input,select,textarea,[tabindex]:not([tabindex="-1"])');
        if (!f.length) return;
        const first = f[0];
        const last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    const t = setTimeout(() => ref.current?.querySelector<HTMLElement>("[data-autofocus],button,input,select")?.focus(), 30);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      clearTimeout(t);
      prev?.focus?.();
    };
  }, [open, onClose, locked]);

  if (typeof document === "undefined") return null;
  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-[100] flex items-end sm:items-center justify-center p-0 sm:p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: reduce ? 0 : 0.18 }}>
          <div className="absolute inset-0 bg-black/70 backdrop-blur-[2px]" onClick={locked ? undefined : onClose} aria-hidden />
          <motion.div
            ref={ref}
            role="dialog"
            aria-modal="true"
            aria-labelledby={title ? `${id}-title` : undefined}
            className={cn("relative w-full max-h-[94dvh] sm:max-h-[92vh] flex flex-col bg-arena-2 border border-border-strong rounded-t-[var(--radius-xl)] sm:rounded-[var(--radius-xl)] shadow-lg overflow-hidden", sizes[size])}
            initial={reduce ? false : { y: 24, opacity: 0, scale: 0.98 }}
            animate={{ y: 0, opacity: 1, scale: 1 }}
            exit={reduce ? undefined : { y: 24, opacity: 0, scale: 0.98 }}
            transition={{ type: "spring", stiffness: 380, damping: 32 }}
          >
            {(title || !locked) && (
              <header className="flex items-start justify-between gap-3 px-4 sm:px-6 pt-4 sm:pt-5 pb-3 border-b border-border">
                <div className="min-w-0">
                  {eyebrow && <div className="eyebrow mb-1">{eyebrow}</div>}
                  {title && (
                    <h2 id={`${id}-title`} className="font-heading font-semibold text-xl leading-tight">
                      {title}
                    </h2>
                  )}
                </div>
                {!locked && (
                  <button type="button" onClick={onClose} className="touch inline-flex items-center justify-center rounded-[var(--radius-md)] text-text-muted hover:text-text hover:bg-arena-3" aria-label={t("actions.close")}>
                    <IconClose />
                  </button>
                )}
              </header>
            )}
            <div className="flex-1 overflow-y-auto scrollbar-thin px-4 sm:px-6 py-4">{children}</div>
            {footer && <footer className="px-4 sm:px-6 py-3 border-t border-border bg-arena-3/60 flex flex-wrap gap-2 justify-end">{footer}</footer>}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  );
}

export interface ConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  body: ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  loading?: boolean;
}
