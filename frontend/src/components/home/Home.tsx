"use client";
/**
 * Tela inicial do esqueleto (bloco 2): marca, campo 3D e o estado da conexão com a API ({@code GET /api/v1/meta}).
 * As regras mostradas e as hash marks do campo seguem o {@code defaultRuleset} que o servidor devolve (D-4).
 */
import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import { LOCALES, LOCALE_META, useI18n, useT } from "@/i18n";
import { API_MODE } from "@/lib/api/config";
import { fetchMeta, type GameMeta } from "@/lib/api/meta";

type ApiState = { kind: "checking" } | { kind: "online"; meta: GameMeta } | { kind: "offline" };

function FieldLoading() {
  const t = useT("common");
  return <div className="skeleton grid h-full w-full place-items-center text-text-muted">{t("home.field.loading")}</div>;
}

// WebGL só no cliente
const FieldScene = dynamic(() => import("@/components/field/FieldScene"), { ssr: false, loading: () => <FieldLoading /> });

function LocaleSwitch() {
  const { locale, setLocale } = useI18n();
  const t = useT("common");
  return (
    <div role="group" aria-label={t("locale.label")} className="flex gap-1">
      {LOCALES.map((l) => (
        <button
          key={l}
          type="button"
          lang={l}
          aria-pressed={l === locale}
          onClick={() => setLocale(l)}
          className="touch rounded-[var(--radius-sm)] border border-border px-3 text-sm text-text-soft aria-pressed:border-orange aria-pressed:text-text"
        >
          {LOCALE_META[l].nativeName}
        </button>
      ))}
    </div>
  );
}

export function Home() {
  const t = useT("common");
  const { locale } = useI18n();
  const [api, setApi] = useState<ApiState>({ kind: "checking" });

  useEffect(() => {
    const ctrl = new AbortController();
    fetchMeta({ signal: ctrl.signal, locale })
      .then((meta) => setApi({ kind: "online", meta }))
      .catch((e: unknown) => {
        if ((e as Error)?.name !== "AbortError") setApi({ kind: "offline" });
      });
    return () => ctrl.abort();
  }, [locale]);

  const ruleset = api.kind === "online" ? api.meta.ruleset : "PRO";
  const status =
    api.kind === "checking"
      ? t("home.api.checking")
      : api.kind === "offline"
        ? t("home.api.offline")
        : API_MODE === "mock"
          ? t("home.api.mock")
          : t("home.api.online", { version: api.meta.version, ruleset: t(`ruleset.${api.meta.ruleset}`) });

  return (
    <main className="mx-auto flex min-h-dvh max-w-6xl flex-col gap-6 px-[var(--gutter)] py-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">{t("home.eyebrow")}</p>
          <h1 className="font-display text-6xl text-text sm:text-7xl">
            {t("app.name")}
            <span className="mt-1 block font-heading text-xl tracking-[0.3em] text-led">{t("app.sport")}</span>
          </h1>
        </div>
        <LocaleSwitch />
      </header>

      <p className="max-w-2xl text-lg text-text-soft">{t("app.tagline")}</p>

      <section className="surface relative aspect-[16/9] w-full overflow-hidden">
        <FieldScene ruleset={ruleset} label={t("home.field.label")} />
      </section>

      <section className="surface-2 flex flex-wrap items-center justify-between gap-3 p-4" aria-live="polite">
        <div>
          <h2 className="text-sm uppercase tracking-widest text-text-muted">{t("home.api.title")}</h2>
          <p className={api.kind === "offline" ? "text-bad" : "text-text"}>{status}</p>
        </div>
        <button type="button" disabled className="touch rounded-[var(--radius-md)] bg-orange px-5 font-heading uppercase tracking-wider text-white opacity-60">
          {t("home.cta.soon")}
        </button>
      </section>
    </main>
  );
}
