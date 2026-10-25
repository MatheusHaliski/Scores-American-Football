"use client";
import { useEffect, type ReactNode } from "react";
import { MotionConfig } from "framer-motion";
import { api } from "@/lib/api";
import { I18nProvider, useT } from "@/i18n/provider";
import { applyAppearance, usePrefs } from "@/store/prefs";
import { useSession } from "@/store/session";
import { Toaster } from "@/components/ui/Toast";

function SkipLink() {
  const t = useT("common");
  return (
    <a href="#conteudo" className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-[999] focus:bg-orange focus:text-white focus:px-3 focus:py-2 focus:rounded">
      {t("app.skipToContent")}
    </a>
  );
}

/** Aparência (RF10/RF46) aplicada no `<html>` + sincronização com GET /me/settings quando há usuário. */
function PrefsSync() {
  const theme = usePrefs((s) => s.theme);
  const highContrast = usePrefs((s) => s.highContrast);
  const fontScale = usePrefs((s) => s.fontScale);
  const density = usePrefs((s) => s.density);
  const reduceMotion = usePrefs((s) => s.reduceMotion);
  const applyServer = usePrefs((s) => s.applyServer);
  const user = useSession((s) => s.user);

  useEffect(() => {
    applyAppearance({ theme, highContrast, fontScale, density, reduceMotion });
    if (theme !== "SYSTEM") return;
    const mq = window.matchMedia("(prefers-color-scheme: light)");
    const h = () => applyAppearance({ theme, highContrast, fontScale, density, reduceMotion });
    mq.addEventListener("change", h);
    return () => mq.removeEventListener("change", h);
  }, [theme, highContrast, fontScale, density, reduceMotion]);

  useEffect(() => {
    if (!user) return;
    let alive = true;
    api.auth
      .settings()
      .then((s) => alive && applyServer(s))
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [user, applyServer]);
  return null;
}

/** Providers globais: i18n (RF46), sessão + preferências/aparência (RF10), reduzir movimento e toasts. */
export function Providers({ children }: { children: ReactNode }) {
  const reduce = usePrefs((s) => s.reduceMotion);
  const serverLocale = usePrefs((s) => s.serverLocale);
  return (
    <I18nProvider userLocale={serverLocale}>
      <MotionConfig reducedMotion={reduce ? "always" : "user"}>
        <PrefsSync />
        <SkipLink />
        <div id="conteudo">{children}</div>
        <Toaster />
      </MotionConfig>
    </I18nProvider>
  );
}
