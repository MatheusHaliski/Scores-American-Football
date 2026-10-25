/**
 * Catálogos por namespace e idioma (ICU MessageFormat, chaves planas `a.b.c`).
 * pt-BR é a fonte da verdade para os tipos: en-US/es-ES precisam ter as mesmas chaves
 * (verificado em tempo de compilação por `Record<Locale, Catalog>` e no CI por `scripts/i18n-check.mjs`).
 */
import type { Locale } from "../config";

import ptCommon from "./pt-BR/common.json";
import ptErrors from "./pt-BR/errors.json";

import enCommon from "./en-US/common.json";
import enErrors from "./en-US/errors.json";

import esCommon from "./es-ES/common.json";
import esErrors from "./es-ES/errors.json";

export interface Catalog {
  common: typeof ptCommon;
  errors: typeof ptErrors;
}

/** Chave tipada de um namespace (derivada do catálogo pt-BR). */
export type MessageKey<NS extends keyof Catalog> = keyof Catalog[NS] & string;

export const MESSAGES: Record<Locale, Catalog> = {
  "pt-BR": { common: ptCommon, errors: ptErrors },
  "en-US": { common: enCommon, errors: enErrors },
  "es-ES": { common: esCommon, errors: esErrors },
};
