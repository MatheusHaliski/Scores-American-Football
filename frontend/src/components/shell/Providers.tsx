"use client";
import type { ReactNode } from "react";
import { I18nProvider, useT } from "@/i18n/provider";

function SkipLink() {
  const t = useT("common");
  return (
    <a href="#conteudo" className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-[999] focus:bg-orange focus:text-white focus:px-3 focus:py-2 focus:rounded">
      {t("app.skipToContent")}
    </a>
  );
}

/** Providers globais do esqueleto: i18n (RF46). Preferências, sessão e toasts chegam com a plataforma comum (bloco 3). */
export function Providers({ children }: { children: ReactNode }) {
  return (
    <I18nProvider>
      <SkipLink />
      <div id="conteudo">{children}</div>
    </I18nProvider>
  );
}
