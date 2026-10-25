"use client";
import { useEffect, useState, type ComponentType } from "react";
import Link from "next/link";
import { useT } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { AppShell } from "@/components/shell/AppShell";
import { RequireAuth } from "@/components/shell/RequireAuth";
import { IconChevronLeft, IconGlobe, IconLock, IconPalette, IconUser, IconDatabase } from "@/components/ui/icons";
import { AccountSection } from "./AccountSection";
import { AppearanceSection } from "./AppearanceSection";
import { DataSection } from "./DataSection";
import { LanguageSection } from "./LanguageSection";
import { PrivacySection } from "./PrivacySection";
import { useUserSettings } from "./useUserSettings";

export type SettingsSectionKey = "account" | "privacy" | "appearance" | "language" | "data";
const SECTIONS: { key: SettingsSectionKey; icon: ComponentType<{ size?: number; className?: string }> }[] = [
  { key: "account", icon: IconUser },
  { key: "privacy", icon: IconLock },
  { key: "appearance", icon: IconPalette },
  { key: "language", icon: IconGlobe },
  { key: "data", icon: IconDatabase },
];
const isSection = (v: string | null): v is SettingsSectionKey => SECTIONS.some((s) => s.key === v);

/**
 * Configurações (RF46): ponto de cumprimento da LGPD. Navegação lateral com 5 seções (abas roláveis no celular),
 * seção atual refletida em `?s=` (link direto, ex.: `/settings?s=privacy`). Dentro do shell da plataforma.
 */
export function SettingsScreen() {
  return (
    <RequireAuth>
      <AppShell>
        <SettingsBody />
      </AppShell>
    </RequireAuth>
  );
}

function SettingsBody() {
  const t = useT("settings");
  const [section, setSection] = useState<SettingsSectionKey>("account");
  const us = useUserSettings();

  // título da aba no idioma do usuário (o metadata estático fica em pt-BR, idioma padrão do servidor)
  useEffect(() => {
    document.title = `${t("title")} · Scores: American Football`;
  }, [t]);

  useEffect(() => {
    const s = new URLSearchParams(window.location.search).get("s");
    if (isSection(s)) setSection(s);
  }, []);

  const go = (s: SettingsSectionKey) => {
    setSection(s);
    const url = new URL(window.location.href);
    url.searchParams.set("s", s);
    window.history.replaceState(null, "", url);
    document.getElementById("settings-content")?.focus({ preventScroll: true });
  };

  return (
    <div>
      <header className="relative isolate overflow-hidden border-b border-border" style={{ backgroundImage: "var(--grad-arena), var(--grad-turf)" }}>
        <div className="max-w-6xl mx-auto px-4 sm:px-6 pt-6 pb-8 sm:pt-10 sm:pb-12">
          <Link href="/" className="inline-flex items-center gap-1 text-[0.85rem] text-text-soft hover:text-text rounded focus-visible:outline-3 focus-visible:outline-rim">
            <IconChevronLeft size={16} /> {t("back.menu")}
          </Link>
          <div className="eyebrow mt-4">{t("eyebrow")}</div>
          <h1 className="font-display text-4xl sm:text-5xl mt-1 drop-shadow">{t("title")}</h1>
          <p className="mt-2 max-w-2xl text-text-soft">{t("description")}</p>
        </div>
      </header>

      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 grid gap-6 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <nav aria-label={t("nav.aria")} className="lg:sticky lg:top-4 self-start -mx-4 px-4 lg:mx-0 lg:px-0 overflow-x-auto">
          <ul className="flex lg:flex-col gap-1 min-w-max lg:min-w-0">
            {SECTIONS.map(({ key, icon: Icon }) => (
              <li key={key}>
                <button
                  type="button"
                  onClick={() => go(key)}
                  aria-current={section === key ? "page" : undefined}
                  className={cn(
                    "w-full flex items-center gap-3 rounded-[var(--radius-md)] px-3 py-2.5 text-left transition-colors min-h-[var(--touch)] focus-visible:outline-3 focus-visible:outline-rim",
                    section === key ? "bg-orange text-white" : "text-text-muted hover:bg-arena-3 hover:text-text",
                  )}
                >
                  <Icon size={18} className="shrink-0" />
                  <span className="flex flex-col">
                    <span className="font-heading font-semibold leading-tight">{t(`nav.${key}` as "nav.account")}</span>
                    <span className={cn("hidden lg:block text-[0.72rem]", section === key ? "text-white/80" : "text-text-dim")}>{t(`nav.${key}.hint` as "nav.account.hint")}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </nav>

        <div id="settings-content" tabIndex={-1} className="min-w-0 outline-none">
          {section === "account" && <AccountSection />}
          {section === "privacy" && <PrivacySection />}
          {section === "appearance" && <AppearanceSection settings={us.settings} error={us.error} saving={us.saving} update={us.update} />}
          {section === "language" && <LanguageSection settings={us.settings} error={us.error} update={us.update} />}
          {section === "data" && <DataSection />}
        </div>
      </div>
    </div>
  );
}
