/**
 * Texto das notícias da semana (RF21, D-103) no idioma da tela: o backend manda `textKey` ("news.USER_WIN") e `params` nomeados, e
 * o front monta manchete e texto pelo catálogo `season:news.<KIND>.headline|body`. Se o front não conhece a chave (tipo novo do
 * backend) ou falta um parâmetro que o texto pede, vale a manchete/texto que o servidor já traduziu no idioma do pedido.
 */
import type { Locale } from "@/i18n/config";
import { rawMessage, translateIn } from "@/i18n/runtime";
import type { MessageParams } from "@/i18n/icu";
import type { NewsItem, ParamValue } from "@/lib/api/season-types";

export interface NewsText {
  headline: string;
  body: string;
  /** veio do catálogo do front (false = reserva do servidor) */
  translated: boolean;
}

/** Parâmetros nomeados como vieram (argumento simples do ICU não formata número, então o ano sai sem separador de milhar). */
function paramsFor(params: Record<string, ParamValue>): MessageParams {
  return { ...params };
}

/** Nomes de argumento usados por uma mensagem ICU simples ({name}, {n, plural, …}). */
function argNames(message: string): string[] {
  return [...message.matchAll(/\{\s*([A-Za-z_]\w*)\s*[,}]/g)].map((m) => m[1]);
}

export function newsText(item: Pick<NewsItem, "textKey" | "params" | "headline" | "body">, locale: Locale, pseudo = false): NewsText {
  const base = item.textKey.startsWith("news.") ? item.textKey : `news.${item.textKey}`;
  const h = rawMessage(locale, "season", `${base}.headline`);
  const b = rawMessage(locale, "season", `${base}.body`);
  const params = paramsFor(item.params);
  const complete = (m: string) => argNames(m).every((a) => a in params);
  if (h === null || b === null || !complete(h) || !complete(b)) return { headline: item.headline, body: item.body, translated: false };
  return { headline: translateIn(locale, "season", `${base}.headline`, params, pseudo), body: translateIn(locale, "season", `${base}.body`, params, pseudo), translated: true };
}
