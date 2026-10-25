/**
 * Runtime de tradução fora do React (stores, `describeError`, mock que preenche textos legados):
 * idioma corrente + `translate()` com cadeia de fallback. O `I18nProvider` mantém este singleton sincronizado.
 */
import { DEFAULT_LOCALE, FALLBACK_CHAIN, type Locale, type Namespace } from "./config";
import { formatMessage, type MessageParams } from "./icu";
import { MESSAGES, type MessageKey } from "./messages";
import { pseudoize } from "./pseudo";

export type { MessageParams } from "./icu";

/** Referência a uma mensagem do catálogo: `key` no formato `namespace:chave` + parâmetros (conteúdo de jogo por chave). */
export interface MessageRef {
  key: string;
  params?: MessageParams;
}

const state = { locale: DEFAULT_LOCALE as Locale, pseudo: false };
const listeners = new Set<() => void>();

export function getLocale(): Locale {
  return state.locale;
}
export function isPseudo(): boolean {
  return state.pseudo;
}
export function setRuntimeLocale(locale: Locale, pseudo = false): void {
  if (state.locale === locale && state.pseudo === pseudo) return;
  state.locale = locale;
  state.pseudo = pseudo;
  for (const l of listeners) l();
}
export function subscribeLocale(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function lookup(locale: Locale, ns: Namespace, key: string): string | null {
  const dict = MESSAGES[locale]?.[ns] as Record<string, string> | undefined;
  const v = dict?.[key];
  if (typeof v === "string") return v;
  return null;
}

/** Mensagem crua (sem formatar) com fallback; `null` quando não existe em nenhum idioma. */
export function rawMessage(locale: Locale, ns: Namespace, key: string): string | null {
  const direct = lookup(locale, ns, key);
  if (direct !== null) return direct;
  for (const fb of FALLBACK_CHAIN[locale]) {
    const v = lookup(fb, ns, key);
    if (v !== null) return v;
  }
  return null;
}

export function hasMessage(ns: Namespace, key: string, locale: Locale = state.locale): boolean {
  return rawMessage(locale, ns, key) !== null;
}

/** Traduz num idioma explícito. Chave ausente ⇒ devolve `ns:key` (visível no pseudo/QA, nunca quebra). */
export function translateIn<NS extends Namespace>(locale: Locale, ns: NS, key: MessageKey<NS> | (string & {}), params?: MessageParams, pseudo = false): string {
  const raw = rawMessage(locale, ns, key);
  if (raw === null) return `${ns}:${key}`;
  const out = formatMessage(locale, raw, params);
  return pseudo ? pseudoize(out) : out;
}

/** Traduz no idioma corrente do runtime. */
export function translate<NS extends Namespace>(ns: NS, key: MessageKey<NS> | (string & {}), params?: MessageParams): string {
  return translateIn(state.locale, ns, key, params, state.pseudo);
}

export function splitRefKey(key: string): { ns: Namespace; key: string } {
  const i = key.indexOf(":");
  if (i < 0) return { ns: "common", key };
  return { ns: key.slice(0, i) as Namespace, key: key.slice(i + 1) };
}

/** Renderiza uma `MessageRef` (`ns:chave` + params) num idioma. */
export function translateRefIn(locale: Locale, ref: MessageRef, pseudo = false): string {
  const { ns, key } = splitRefKey(ref.key);
  return translateIn(locale, ns, key, ref.params, pseudo);
}
export function translateRef(ref: MessageRef): string {
  return translateRefIn(state.locale, ref, state.pseudo);
}

/** Cria uma referência `ns:chave` (helper para o mock e para o backend simulado). */
export function ref(key: string, params?: MessageParams): MessageRef {
  return params ? { key, params } : { key };
}

/** Texto legado em pt-BR (idioma padrão do servidor) para campos `text`/`subject` que o backend ainda envia como string. */
export function legacyText(r: MessageRef): string {
  return translateRefIn(DEFAULT_LOCALE, r);
}
