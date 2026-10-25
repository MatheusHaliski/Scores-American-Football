"use client";
/**
 * Tela inicial: marca, campo 3D, o estado da conexão com a API ({@code GET /api/v1/meta}) e a conta (bloco 3: entrar,
 * criar conta e, logado, o atalho para as configurações) e o atalho para as carreiras (bloco 6a). As regras mostradas e as hash marks do campo seguem o
 * {@code defaultRuleset} que o servidor devolve (D-4).
 */
import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import { LOCALES, LOCALE_META, useI18n, useT } from "@/i18n";
import { API_MODE } from "@/lib/api/config";
import { fetchMeta, type GameMeta } from "@/lib/api/meta";
import { BrandMark } from "@/components/brand/BrandMark";
import { Button } from "@/components/ui/Button";
import { IconFlag, IconLogout, IconSettings, IconUser } from "@/components/ui/icons";
import { useSession } from "@/store/session";

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

/** Conta: visitante vê entrar/criar conta; logado vê o nome, o atalho para as configurações e sair. */
function AccountPanel() {
  const t = useT("common");
  const user = useSession((s) => s.user);
  const hydrated = useSession((s) => s.hydrated);
  const logout = useSession((s) => s.logout);

  return (
    <section className="surface-2 flex flex-wrap items-center justify-between gap-3 p-4" aria-labelledby="home-account" aria-busy={!hydrated}>
      <div className="min-w-0">
        <h2 id="home-account" className="text-sm uppercase tracking-widest text-text-muted">
          {t("home.account.title")}
        </h2>
        <p className="text-text truncate">{!hydrated ? t("state.loading") : user ? t("home.account.hello", { name: user.displayName }) : t("home.account.guest")}</p>
        {hydrated && user && <p className="text-[0.85rem] text-text-muted">{t("home.account.signedIn")}</p>}
      </div>
      {hydrated && (
        <div className="flex flex-wrap gap-2">
          {user ? (
            <>
              <Button href="/settings" icon={<IconSettings size={18} />}>
                {t("home.cta.settings")}
              </Button>
              <Button variant="ghost" icon={<IconLogout size={18} />} onClick={() => void logout().catch(() => undefined)}>
                {t("actions.logout")}
              </Button>
            </>
          ) : (
            <>
              <Button href="/auth" icon={<IconUser size={18} />}>
                {t("home.cta.login")}
              </Button>
              <Button href="/auth?mode=register" variant="secondary">
                {t("home.cta.register")}
              </Button>
            </>
          )}
        </div>
      )}
    </section>
  );
}

/** Atalho para as carreiras (bloco 6a); visitante vai ao login e volta para /careers. */
function CareersCta() {
  const t = useT("common");
  const user = useSession((s) => s.user);
  const hydrated = useSession((s) => s.hydrated);
  if (!hydrated) return null;
  return (
    <Button href={user ? "/careers" : "/auth?next=%2Fcareers"} variant="led" icon={<IconFlag size={18} />}>
      {user ? t("home.cta.careers") : t("home.cta.careersLogin")}
    </Button>
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
          <BrandMark size={44} className="mt-3" />
          <h1 className="font-display text-6xl text-text sm:text-7xl">
            {t("app.name")}
            <span className="mt-1 block font-heading text-xl tracking-[0.3em] text-led">{t("app.sport")}</span>
          </h1>
        </div>
        <LocaleSwitch />
      </header>

      <p className="max-w-2xl text-lg text-text-soft">{t("app.tagline")}</p>

      <AccountPanel />

      <section className="surface relative aspect-[16/9] w-full overflow-hidden">
        <FieldScene ruleset={ruleset} label={t("home.field.label")} />
      </section>

      <section className="surface-2 flex flex-wrap items-center justify-between gap-3 p-4" aria-live="polite">
        <div>
          <h2 className="text-sm uppercase tracking-widest text-text-muted">{t("home.api.title")}</h2>
          <p className={api.kind === "offline" ? "text-bad" : "text-text"}>{status}</p>
        </div>
        <CareersCta />
      </section>
    </main>
  );
}
