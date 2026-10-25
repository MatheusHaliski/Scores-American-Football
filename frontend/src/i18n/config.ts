/**
 * Configuração de i18n (RF46 CA05/CA06). Idiomas iniciais pt-BR (padrão), en-US e es-ES;
 * cadeia de fallback por idioma; namespaces dos catálogos ICU MessageFormat.
 */
export const LOCALES = ["pt-BR", "en-US", "es-ES"] as const;
export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "pt-BR";

/** Pseudo-localização (dev): `?lang=qps-ploc` expande 35 % + acentos para testar truncamento. */
export const PSEUDO_LOCALE = "qps-ploc";

/** Cadeia de fallback: pt-BR → en-US; es-ES → pt-BR → en-US. A chave em si é o último recurso. */
export const FALLBACK_CHAIN: Record<Locale, Locale[]> = {
  "pt-BR": ["en-US"],
  "en-US": ["pt-BR"],
  "es-ES": ["pt-BR", "en-US"],
};

export const NAMESPACES = ["common", "errors"] as const;
export type Namespace = (typeof NAMESPACES)[number];

export interface LocaleMeta {
  /** nome do idioma no próprio idioma (lista sem bandeiras) */
  nativeName: string;
  /** moeda real usada na prévia de formato regional */
  currency: "BRL" | "USD" | "EUR";
  dir: "ltr" | "rtl";
  region: string;
  /** código legado do backend (`UserAccount.locale`) */
  legacy: string;
}

export const LOCALE_META: Record<Locale, LocaleMeta> = {
  "pt-BR": { nativeName: "Português (Brasil)", currency: "BRL", dir: "ltr", region: "BR", legacy: "PT_BR" },
  "en-US": { nativeName: "English (US)", currency: "USD", dir: "ltr", region: "US", legacy: "EN" },
  "es-ES": { nativeName: "Español (España)", currency: "EUR", dir: "ltr", region: "ES", legacy: "ES" },
};

export const LOCALE_STORAGE_KEY = "scoresaf.locale";
export const LOCALE_QUERY_PARAM = "lang";

export function isLocale(v: unknown): v is Locale {
  return typeof v === "string" && (LOCALES as readonly string[]).includes(v);
}

/** Casa uma tag BCP-47 qualquer (navigator.language, legado do backend) com um dos idiomas suportados. */
export function matchLocale(tag: string | null | undefined): Locale | null {
  if (!tag) return null;
  const norm = tag.replace("_", "-").trim();
  if (isLocale(norm)) return norm;
  const upper = norm.toUpperCase();
  for (const l of LOCALES) if (LOCALE_META[l].legacy === upper) return l;
  const lang = norm.toLowerCase().split("-")[0];
  if (lang === "pt") return "pt-BR";
  if (lang === "en") return "en-US";
  if (lang === "es") return "es-ES";
  return null;
}
