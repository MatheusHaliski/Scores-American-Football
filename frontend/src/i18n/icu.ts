/**
 * Formatação ICU MessageFormat (plural, select, selectordinal, number, date) via `intl-messageformat`,
 * com cache por (idioma, mensagem). Nunca lança: em caso de erro devolve a mensagem crua.
 */
import IntlMessageFormat from "intl-messageformat";

export type MessageParamValue = string | number | boolean | Date | null | undefined;
export type MessageParams = Record<string, MessageParamValue>;

const cache = new Map<string, IntlMessageFormat | null>();
const MAX_CACHE = 4000;

function compile(locale: string, message: string): IntlMessageFormat | null {
  const key = `${locale}\u0000${message}`;
  const hit = cache.get(key);
  if (hit !== undefined) return hit;
  let mf: IntlMessageFormat | null;
  try {
    mf = new IntlMessageFormat(message, locale, undefined, { ignoreTag: true });
  } catch {
    mf = null;
  }
  if (cache.size >= MAX_CACHE) cache.clear();
  cache.set(key, mf);
  return mf;
}

/** Substituição simples `{name}` usada como último recurso quando a mensagem ICU é inválida. */
function naive(message: string, params?: MessageParams): string {
  if (!params) return message;
  return message.replace(/\{(\w+)\}/g, (m, k: string) => (params[k] === undefined || params[k] === null ? m : String(params[k])));
}

export function formatMessage(locale: string, message: string, params?: MessageParams): string {
  const mf = compile(locale, message);
  if (!mf) return naive(message, params);
  try {
    const out = mf.format(params ?? {});
    return typeof out === "string" ? out : Array.isArray(out) ? out.join("") : String(out);
  } catch {
    return naive(message, params);
  }
}

/** Para testes/ferramentas: limpa o cache de mensagens compiladas. */
export function clearMessageCache(): void {
  cache.clear();
}
