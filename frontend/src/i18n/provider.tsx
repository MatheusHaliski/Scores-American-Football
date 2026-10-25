"use client";
/**
 * Provider de i18n: idioma corrente, troca imediata (sem reload), `<html lang>` + `dir`, resolução
 * URL `?lang=` → configuração do usuário (`/me/settings`) → localStorage → `navigator.language` → pt-BR,
 * e hooks `useT(namespace)` tipados por chave.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { DEFAULT_LOCALE, LOCALE_META, LOCALE_QUERY_PARAM, LOCALE_STORAGE_KEY, PSEUDO_LOCALE, matchLocale, type Locale, type Namespace } from "./config";
import type { MessageParams } from "./icu";
import type { MessageKey } from "./messages";
import { setRuntimeLocale, translateIn, translateRefIn, type MessageRef } from "./runtime";

export type LocaleSource = "url" | "user" | "storage" | "navigator" | "default";

export interface I18nContextValue {
  locale: Locale;
  source: LocaleSource;
  pseudo: boolean;
  /** troca imediata; persiste em localStorage (e o chamador decide se persiste em /me/settings) */
  setLocale: (locale: Locale, source?: LocaleSource) => void;
  setPseudo: (on: boolean) => void;
}

const I18nContext = createContext<I18nContextValue | null>(null);

function readStorage(): Locale | null {
  try {
    return matchLocale(window.localStorage.getItem(LOCALE_STORAGE_KEY));
  } catch {
    return null;
  }
}
function writeStorage(locale: Locale): void {
  try {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    /* modo privado */
  }
}

/** Resolve o idioma inicial no cliente (sem a configuração do usuário, que chega depois). */
export function resolveInitialLocale(): { locale: Locale; source: LocaleSource; pseudo: boolean; urlLocked: boolean } {
  if (typeof window === "undefined") return { locale: DEFAULT_LOCALE, source: "default", pseudo: false, urlLocked: false };
  const q = new URLSearchParams(window.location.search).get(LOCALE_QUERY_PARAM);
  if (q === PSEUDO_LOCALE) return { locale: DEFAULT_LOCALE, source: "url", pseudo: true, urlLocked: true };
  const fromUrl = matchLocale(q);
  if (fromUrl) return { locale: fromUrl, source: "url", pseudo: false, urlLocked: true };
  const fromStorage = readStorage();
  if (fromStorage) return { locale: fromStorage, source: "storage", pseudo: false, urlLocked: false };
  const fromNav = matchLocale(navigator.language) ?? (navigator.languages ?? []).map(matchLocale).find(Boolean) ?? null;
  if (fromNav) return { locale: fromNav, source: "navigator", pseudo: false, urlLocked: false };
  return { locale: DEFAULT_LOCALE, source: "default", pseudo: false, urlLocked: false };
}

export function I18nProvider({ children, userLocale }: { children: ReactNode; userLocale?: Locale | null }) {
  const [locale, setLocaleState] = useState<Locale>(DEFAULT_LOCALE);
  const [source, setSource] = useState<LocaleSource>("default");
  const [pseudo, setPseudoState] = useState(false);
  const urlLocked = useRef(false);

  // 1) resolução inicial no cliente (URL → storage → navigator → padrão)
  useEffect(() => {
    const r = resolveInitialLocale();
    urlLocked.current = r.urlLocked;
    setLocaleState(r.locale);
    setSource(r.source);
    setPseudoState(r.pseudo);
  }, []);

  // 2) configuração do usuário (/me/settings) vence storage/navigator, mas não a URL
  useEffect(() => {
    if (!userLocale || urlLocked.current) return;
    setLocaleState(userLocale);
    setSource("user");
    writeStorage(userLocale);
  }, [userLocale]);

  // 3) reflete no documento e no runtime (fora do React)
  useEffect(() => {
    document.documentElement.lang = pseudo ? PSEUDO_LOCALE : locale;
    document.documentElement.dir = LOCALE_META[locale].dir;
    setRuntimeLocale(locale, pseudo);
  }, [locale, pseudo]);

  const setLocale = useCallback((l: Locale, s: LocaleSource = "user") => {
    setLocaleState(l);
    setSource(s);
    setPseudoState(false);
    urlLocked.current = false;
    writeStorage(l);
  }, []);
  const setPseudo = useCallback((on: boolean) => setPseudoState(on), []);

  const value = useMemo<I18nContextValue>(() => ({ locale, source, pseudo, setLocale, setPseudo }), [locale, source, pseudo, setLocale, setPseudo]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useI18n must be used inside <I18nProvider>");
  return ctx;
}

export function useLocale(): Locale {
  return useI18n().locale;
}

export type TFunction<NS extends Namespace> = (key: MessageKey<NS>, params?: MessageParams) => string;

/** `t(chave, params)` tipado pelas chaves do namespace (catálogo pt-BR). */
export function useT<NS extends Namespace>(ns: NS): TFunction<NS> {
  const { locale, pseudo } = useI18n();
  return useCallback<TFunction<NS>>((key, params) => translateIn(locale, ns, key, params, pseudo), [locale, ns, pseudo]);
}

/** Traduz chaves dinâmicas (`ns:chave` ou `chave` dentro de um namespace) — para enums e conteúdo por chave. */
export function useTRef(): (ref: MessageRef | null | undefined, fallback?: string | null) => string {
  const { locale, pseudo } = useI18n();
  return useCallback((r, fallback) => (r ? translateRefIn(locale, r, pseudo) : (fallback ?? "")), [locale, pseudo]);
}

/** Tradução "solta" (namespace + chave string) para enums: `useTd()("enums", `position.${pos}`)`. */
export function useTd(): (ns: Namespace, key: string, params?: MessageParams) => string {
  const { locale, pseudo } = useI18n();
  return useCallback((ns, key, params) => translateIn(locale, ns, key, params, pseudo), [locale, pseudo]);
}

export type { MessageRef };
