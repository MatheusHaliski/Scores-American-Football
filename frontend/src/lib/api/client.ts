import { ApiError, networkError, problemFromBody } from "./problem";
import type { AuthTokens } from "./types";
import { getLocale } from "@/i18n/runtime";

/**
 * Cliente HTTP tipado para o backend Java (`/api/v1`).
 * - Bearer JWT (access 15 min) com refresh automático (refresh 7 dias rotativo);
 * - erros RFC 7807 convertidos em `ApiError` (mesmo leitor do `problem.ts`); sem rede, `ApiError` NETWORK;
 * - uma única promessa de refresh em voo (evita tempestade de refresh).
 */

export interface TokenStorage {
  get(): AuthTokens | null;
  set(tokens: AuthTokens | null): void;
}

const memoryTokens: { current: AuthTokens | null } = { current: null };
export const memoryTokenStorage: TokenStorage = {
  get: () => memoryTokens.current,
  set: (t) => {
    memoryTokens.current = t;
  },
};

export interface HttpClientOptions {
  baseUrl: string;
  tokens?: TokenStorage;
  onUnauthorized?: () => void;
}

type Query = Record<string, string | number | boolean | undefined | null>;

export class HttpClient {
  private readonly baseUrl: string;
  private readonly tokens: TokenStorage;
  private readonly onUnauthorized?: () => void;
  private refreshing: Promise<AuthTokens | null> | null = null;

  constructor(opts: HttpClientOptions) {
    this.baseUrl = opts.baseUrl.replace(/\/$/, "");
    this.tokens = opts.tokens ?? memoryTokenStorage;
    this.onUnauthorized = opts.onUnauthorized;
  }

  get<T>(path: string, query?: Query): Promise<T> {
    return this.request<T>("GET", path, undefined, query);
  }
  post<T>(path: string, body?: unknown, query?: Query): Promise<T> {
    return this.request<T>("POST", path, body, query);
  }
  put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("PUT", path, body);
  }
  patch<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("PATCH", path, body);
  }
  delete<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("DELETE", path, body);
  }
  /** Download autenticado (exportação de dados — RF46 CA07). */
  async blob(path: string, query?: Query): Promise<Blob> {
    const tokens = await this.ensureFreshTokens();
    const headers: Record<string, string> = { "Accept-Language": getLocale() };
    if (tokens?.accessToken) headers.Authorization = `Bearer ${tokens.accessToken}`;
    const res = await this.send(this.buildUrl(path, query), { headers, credentials: "include" });
    if (!res.ok) throw await this.toError(res);
    return res.blob();
  }

  private buildUrl(path: string, query?: Query): string {
    const url = new URL(this.baseUrl + (path.startsWith("/") ? path : `/${path}`));
    if (query) {
      for (const [k, v] of Object.entries(query)) {
        if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, String(v));
      }
    }
    return url.toString();
  }

  private async request<T>(method: string, path: string, body?: unknown, query?: Query, retry = true): Promise<T> {
    const tokens = await this.ensureFreshTokens();
    // X-Scores-Refresh: cabeçalho próprio que autoriza o uso do cookie HttpOnly (defesa de CSRF; D-114)
    const headers: Record<string, string> = { Accept: "application/json", "Accept-Language": getLocale(), "X-Scores-Refresh": "1" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (tokens?.accessToken) headers.Authorization = `Bearer ${tokens.accessToken}`;

    const res = await this.send(this.buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "include",
    });

    // o refresh vive no cookie HttpOnly: dá para tentar renovar mesmo sem token nenhum em memória (aba recarregada)
    if (res.status === 401 && retry) {
      const refreshed = await this.refresh();
      if (refreshed) return this.request<T>(method, path, body, query, false);
    }
    if (res.status === 401) {
      this.tokens.set(null);
      this.onUnauthorized?.();
    }
    if (!res.ok) throw await this.toError(res);
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  /** `fetch` que transforma a falha de rede em `ApiError` NETWORK (o abort segue como está). */
  private async send(url: string, init: RequestInit): Promise<Response> {
    try {
      return await fetch(url, init);
    } catch (e) {
      if ((e as Error)?.name === "AbortError") throw e;
      throw networkError();
    }
  }

  private async toError(res: Response): Promise<ApiError> {
    let body: unknown = null;
    try {
      const ct = res.headers.get("content-type") ?? "";
      if (ct.includes("json")) body = await res.json();
    } catch {
      body = null;
    }
    return new ApiError(problemFromBody(res.status, body));
  }

  private async ensureFreshTokens(): Promise<AuthTokens | null> {
    const t = this.tokens.get();
    if (!t) return null;
    // renova 30 s antes de expirar
    if (t.accessExpiresAt - Date.now() < 30_000) {
      return (await this.refresh()) ?? t;
    }
    return t;
  }

  /**
   * Renova pelo cookie HttpOnly do refresh (D-114). O cabeçalho `X-Scores-Refresh` é a defesa de CSRF que o servidor
   * exige: um formulário de outro site não consegue acrescentá-lo. O corpo só vai quando existe um refresh em memória
   * (cliente que não é navegador).
   */
  async refresh(): Promise<AuthTokens | null> {
    if (this.refreshing) return this.refreshing;
    const current = this.tokens.get();
    this.refreshing = (async () => {
      try {
        const res = await this.send(this.buildUrl("/auth/refresh"), {
          method: "POST",
          headers: { "Content-Type": "application/json", Accept: "application/json", "Accept-Language": getLocale(), "X-Scores-Refresh": "1" },
          body: JSON.stringify(current?.refreshToken ? { refreshToken: current.refreshToken } : {}),
          credentials: "include",
        });
        if (!res.ok) {
          // 401/403: sessão acabou; outro status (5xx) também derruba, como no basquete
          this.tokens.set(null);
          this.onUnauthorized?.();
          return null;
        }
        const data = (await res.json()) as { accessToken: string; refreshToken?: string | null; accessExpiresAt?: string | number };
        const next: AuthTokens = {
          accessToken: data.accessToken,
          refreshToken: data.refreshToken ?? null,
          accessExpiresAt: toEpochMs(data.accessExpiresAt),
        };
        this.tokens.set(next);
        return next;
      } catch {
        // sem rede no refresh: segue sem sessão renovada; quem chamou recebe o 401 original
        return null;
      } finally {
        this.refreshing = null;
      }
    })();
    return this.refreshing;
  }
}

/** Instante do servidor (`Instant` ISO-8601, epoch em segundos ou ms) em epoch ms; ausente ⇒ agora + `fallbackSeconds`. */
export function toEpochMs(v: string | number | null | undefined, fallbackSeconds = 15 * 60): number {
  if (typeof v === "number") return v < 1e12 ? v * 1000 : v;
  if (typeof v === "string") {
    const t = Date.parse(v);
    if (!Number.isNaN(t)) return t;
  }
  return Date.now() + fallbackSeconds * 1000;
}
