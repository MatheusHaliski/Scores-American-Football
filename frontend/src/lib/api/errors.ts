import { hasMessage, translate } from "@/i18n/runtime";
import { ApiError, NETWORK } from "./problem";

export { ApiError } from "./problem";

/**
 * Mensagem legível para o usuário, no idioma corrente. Ordem: o código do catálogo `errors:` (com os argumentos do mock
 * ou de `details`); se o texto do catálogo precisa de argumentos que não vieram, a `message` que o servidor já traduziu;
 * por fim, um texto genérico pelo status.
 */
export function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    const { code, params, details, message } = err.problem;
    if (code && hasMessage("errors", code)) {
      const text = translate("errors", code, { ...flatDetails(details), ...params });
      if (!/\{[^}]*\}/.test(text)) return text;
      if (message) return message;
    }
    if (message) return message;
    if (err.status === 401) return translate("errors", "unauthorized");
    if (err.status === 403) return translate("errors", "forbidden");
    if (err.status >= 500) return translate("errors", "server");
    return translate("errors", "http", { status: err.status });
  }
  if (err instanceof Error) {
    if (err.name === "TypeError" && /fetch/i.test(err.message)) return translate("errors", NETWORK);
    return err.message;
  }
  return translate("errors", "unexpected");
}

/** Erros por campo de um 400 (VALIDATION do backend ou validação do mock). */
export function fieldErrors(err: unknown): Record<string, string> {
  return err instanceof ApiError ? err.fields : {};
}

function flatDetails(details: Record<string, unknown>): Record<string, string | number | boolean | null> {
  const out: Record<string, string | number | boolean | null> = {};
  for (const [k, v] of Object.entries(details)) if (v === null || ["string", "number", "boolean"].includes(typeof v)) out[k] = v as string | number | boolean | null;
  return out;
}
