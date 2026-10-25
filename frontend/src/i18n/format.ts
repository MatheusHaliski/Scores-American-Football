"use client";
/**
 * Formatação regional via `Intl` (datas, números, moeda fictícia S$, moedas reais BRL/USD/EUR,
 * tempo relativo, listas, nomes de países). Herdado do basquete, sem os rótulos de quarto/altura (blocos de jogo).
 */
import { useMemo } from "react";
import { LOCALE_META, type Locale } from "./config";
import { translateIn } from "./runtime";
import { useI18n } from "./provider";
import { usePrefs } from "@/store/prefs";

export interface Formatters {
  locale: Locale;
  num(v: number, digits?: number): string;
  /** 0–1 ⇒ "62%" */
  pct(v: number, digits?: number): string;
  /** moeda fictícia do jogo (S$); compacta por padrão (S$ 1,2 mi / S$ 1.2M) */
  money(v: number, compact?: boolean): string;
  /** moeda real por idioma (prévia de formato regional) */
  currency(v: number, code?: "BRL" | "USD" | "EUR"): string;
  dateShort(iso: string | Date): string;
  dateLong(iso: string | Date): string;
  dateTime(iso: string | Date): string;
  dateNumeric(iso: string | Date): string;
  /** "há 5 min" / "5 min ago" */
  relative(iso: string | Date, now?: number): string;
  list(items: string[], type?: "conjunction" | "disjunction"): string;
  /** nome do país a partir do código ISO-3166 (ou devolve o texto quando não é código) */
  region(codeOrName: string): string;
  ordinal(n: number): string;
}

const nf = new Map<string, Intl.NumberFormat>();
function numberFormat(locale: string, opts: Intl.NumberFormatOptions): Intl.NumberFormat {
  const k = locale + JSON.stringify(opts);
  let f = nf.get(k);
  if (!f) {
    f = new Intl.NumberFormat(locale, opts);
    nf.set(k, f);
  }
  return f;
}
const df = new Map<string, Intl.DateTimeFormat>();
function dateFormat(locale: string, opts: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const k = locale + JSON.stringify(opts);
  let f = df.get(k);
  if (!f) {
    f = new Intl.DateTimeFormat(locale, opts);
    df.set(k, f);
  }
  return f;
}

function toDate(v: string | Date): Date {
  return v instanceof Date ? v : new Date(v);
}

/**
 * `locale` decide os textos; `region` (formato regional, RF46 CA05) decide números, datas e moeda real.
 * Por padrão o formato regional acompanha o idioma.
 */
export function createFormatters(locale: Locale, region: string = locale): Formatters {
  const meta = LOCALE_META[(Object.keys(LOCALE_META) as Locale[]).find((l) => l === region) ?? locale];
  const R = region;
  return {
    locale,
    num: (v, digits = 0) => numberFormat(R, { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(v),
    pct: (v, digits = 0) => numberFormat(R, { style: "percent", maximumFractionDigits: digits }).format(v),
    money: (v, compact = true) => {
      const abs = Math.abs(v);
      if (compact && abs >= 1_000) return `S$ ${numberFormat(R, { notation: "compact", maximumFractionDigits: abs >= 1_000_000 ? 1 : 0 }).format(v)}`;
      return `S$ ${numberFormat(R, { maximumFractionDigits: 0 }).format(v)}`;
    },
    currency: (v, code = meta.currency) => numberFormat(R, { style: "currency", currency: code, maximumFractionDigits: 2 }).format(v),
    dateShort: (iso) => dateFormat(R, { day: "2-digit", month: "short" }).format(toDate(iso)),
    dateLong: (iso) => dateFormat(R, { weekday: "short", day: "2-digit", month: "long", year: "numeric" }).format(toDate(iso)),
    dateTime: (iso) => dateFormat(R, { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(toDate(iso)),
    dateNumeric: (iso) => dateFormat(R, { dateStyle: "short" }).format(toDate(iso)),
    relative: (iso, now = Date.now()) => {
      const diff = toDate(iso).getTime() - now;
      const rtf = new Intl.RelativeTimeFormat(locale, { numeric: "auto", style: "narrow" });
      const min = Math.round(diff / 60_000);
      if (Math.abs(min) < 1) return translateIn(locale, "common", "time.now");
      if (Math.abs(min) < 60) return rtf.format(min, "minute");
      const h = Math.round(min / 60);
      if (Math.abs(h) < 24) return rtf.format(h, "hour");
      return rtf.format(Math.round(h / 24), "day");
    },
    list: (items, type = "conjunction") => new Intl.ListFormat(locale, { type, style: "long" }).format(items),
    region: (codeOrName) => {
      if (!/^[A-Z]{2}$/.test(codeOrName)) return codeOrName;
      try {
        return new Intl.DisplayNames(locale, { type: "region" }).of(codeOrName) ?? codeOrName;
      } catch {
        return codeOrName;
      }
    },
    ordinal: (n) => translateIn(locale, "common", "ordinal", { n }),
  };
}

/** Formatadores ligados ao idioma corrente. */
export function useFormat(): Formatters {
  const { locale } = useI18n();
  const region = usePrefs((s) => s.regionalFormat);
  return useMemo(() => createFormatters(locale, region ?? locale), [locale, region]);
}
