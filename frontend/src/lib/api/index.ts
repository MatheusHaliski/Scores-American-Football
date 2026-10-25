import type { ScoresApi } from "./contract";
import { API_MODE, API_URL } from "./config";
import { createLiveApi } from "./live";
import { createMockApi } from "./mock";
import { memoryTokenStorage } from "./client";

export { API_MODE, API_URL, type ApiMode } from "./config";

/**
 * Tokens da sessão **em memória** (D-114, herdado do basquete). O access token vive só nesta aba, enquanto ela estiver
 * aberta, e o refresh fica num cookie HttpOnly que o JavaScript não lê: um XSS não leva a sessão inteira, e ela não
 * atravessa reinícios do navegador.
 *
 * Efeito colateral desejado: uma janela anônima tem cookie e memória próprios, então **já entra deslogada** — não há
 * (nem é possível) detectar o modo anônimo; o isolamento do navegador é que faz o trabalho.
 */
export const sessionTokenStorage = memoryTokenStorage;

let unauthorizedHandler: (() => void) | null = null;
export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler;
}

let instance: ScoresApi | null = null;

/** Ponto único de acesso à API. `NEXT_PUBLIC_API_MODE=mock|live`. */
export function getApi(): ScoresApi {
  if (!instance) instance = API_MODE === "live" ? createLiveApi(API_URL, sessionTokenStorage, () => unauthorizedHandler?.()) : createMockApi();
  return instance;
}

export const api: ScoresApi = new Proxy({} as ScoresApi, {
  get(_t, prop: keyof ScoresApi) {
    return getApi()[prop];
  },
});

export * from "./types";
export { ApiError, describeError, fieldErrors } from "./errors";
export type { ScoresApi } from "./contract";
