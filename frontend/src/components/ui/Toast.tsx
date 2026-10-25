"use client";
import { create } from "zustand";
import { AnimatePresence, motion } from "framer-motion";
import { cn } from "@/lib/utils/cn";

interface Toast {
  id: number;
  message: string;
  tone: "info" | "good" | "bad";
}
interface ToastState {
  toasts: Toast[];
  push: (message: string, tone?: Toast["tone"]) => void;
  remove: (id: number) => void;
}

export const useToast = create<ToastState>()((set, get) => ({
  toasts: [],
  push(message, tone = "info") {
    const id = Date.now() + Math.random();
    set({ toasts: [...get().toasts, { id, message, tone }] });
    setTimeout(() => get().remove(id), 4200);
  },
  remove(id) {
    set({ toasts: get().toasts.filter((t) => t.id !== id) });
  },
}));

export function toast(message: string, tone: Toast["tone"] = "info") {
  useToast.getState().push(message, tone);
}

export function Toaster() {
  const toasts = useToast((s) => s.toasts);
  const remove = useToast((s) => s.remove);
  return (
    <div className="fixed z-[200] bottom-[calc(var(--bottombar-h)+12px)] lg:bottom-6 left-1/2 -translate-x-1/2 flex flex-col gap-2 w-[min(480px,calc(100vw-32px))] pointer-events-none" aria-live="polite">
      <AnimatePresence>
        {toasts.map((t) => (
          <motion.div
            key={t.id}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 8 }}
            className={cn("pointer-events-auto rounded-[var(--radius-md)] border px-4 py-3 text-[0.92rem] shadow-lg backdrop-blur", t.tone === "good" ? "bg-arena-2/95 border-good text-[var(--good-text)]" : t.tone === "bad" ? "bg-arena-2/95 border-bad text-[var(--bad-text)]" : "bg-arena-3/95 border-border-strong text-text")}
            role="status"
            onClick={() => remove(t.id)}
          >
            {t.message}
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
