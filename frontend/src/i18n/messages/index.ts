/**
 * Catálogos por namespace e idioma (ICU MessageFormat, chaves planas `a.b.c`).
 * pt-BR é a fonte da verdade para os tipos: en-US/es-ES precisam ter as mesmas chaves
 * (verificado em tempo de compilação por `Record<Locale, Catalog>` e no CI por `scripts/i18n-check.mjs`).
 */
import type { Locale } from "../config";

import ptCommon from "./pt-BR/common.json";
import ptErrors from "./pt-BR/errors.json";
import ptAuth from "./pt-BR/auth.json";
import ptNav from "./pt-BR/nav.json";
import ptSettings from "./pt-BR/settings.json";
import ptMatch from "./pt-BR/match.json";
import ptCareer from "./pt-BR/career.json";
import ptRoster from "./pt-BR/roster.json";
import ptEnums from "./pt-BR/enums.json";
import ptGame from "./pt-BR/game.json";
import ptSeason from "./pt-BR/season.json";

import enCommon from "./en-US/common.json";
import enErrors from "./en-US/errors.json";
import enAuth from "./en-US/auth.json";
import enNav from "./en-US/nav.json";
import enSettings from "./en-US/settings.json";
import enMatch from "./en-US/match.json";
import enCareer from "./en-US/career.json";
import enRoster from "./en-US/roster.json";
import enEnums from "./en-US/enums.json";
import enGame from "./en-US/game.json";
import enSeason from "./en-US/season.json";

import esCommon from "./es-ES/common.json";
import esErrors from "./es-ES/errors.json";
import esAuth from "./es-ES/auth.json";
import esNav from "./es-ES/nav.json";
import esSettings from "./es-ES/settings.json";
import esMatch from "./es-ES/match.json";
import esCareer from "./es-ES/career.json";
import esRoster from "./es-ES/roster.json";
import esEnums from "./es-ES/enums.json";
import esGame from "./es-ES/game.json";
import esSeason from "./es-ES/season.json";

export interface Catalog {
  common: typeof ptCommon;
  errors: typeof ptErrors;
  auth: typeof ptAuth;
  nav: typeof ptNav;
  settings: typeof ptSettings;
  /** Partida ao vivo (blocos 5–6): textos do play-by-play do motor, opções de chamada de jogada e faltas. */
  match: typeof ptMatch;
  /** Carreira (bloco 6a): carreiras salvas, nova carreira, central da franquia e calendário. */
  career: typeof ptCareer;
  /** Elenco, página do jogador e depth chart (bloco 6a). */
  roster: typeof ptRoster;
  /** Rótulos de enums do jogo: posições, vagas, unidades, situações, estilos, níveis, macros e atributos. */
  enums: typeof ptEnums;
  /** Playbook, plano de jogo e preparação do próximo jogo (bloco 6b). */
  game: typeof ptGame;
  /** Temporada (bloco 6c): classificação, playoffs, resumo, avanço, troféus, notícias, moral e analista. */
  season: typeof ptSeason;
}

/** Chave tipada de um namespace (derivada do catálogo pt-BR). */
export type MessageKey<NS extends keyof Catalog> = keyof Catalog[NS] & string;

export const MESSAGES: Record<Locale, Catalog> = {
  "pt-BR": { common: ptCommon, errors: ptErrors, auth: ptAuth, nav: ptNav, settings: ptSettings, match: ptMatch, career: ptCareer, roster: ptRoster, enums: ptEnums, game: ptGame, season: ptSeason },
  "en-US": { common: enCommon, errors: enErrors, auth: enAuth, nav: enNav, settings: enSettings, match: enMatch, career: enCareer, roster: enRoster, enums: enEnums, game: enGame, season: enSeason },
  "es-ES": { common: esCommon, errors: esErrors, auth: esAuth, nav: esNav, settings: esSettings, match: esMatch, career: esCareer, roster: esRoster, enums: esEnums, game: esGame, season: esSeason },
};
