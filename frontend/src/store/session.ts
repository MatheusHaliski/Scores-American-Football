"use client";
import { create } from "zustand";
import { api, API_MODE, onUnauthorized, sessionTokenStorage } from "@/lib/api";
import type { LoginRequest, RegisterRequest, UserAccount } from "@/lib/api/types";
import { setAnalyticsConsent, syncConsentForUser } from "@/lib/analytics";

/**
 * Sessão do navegador (D-114, herdada do basquete). **Nada aqui é persistido**: o access token vive em memória
 * (`sessionTokenStorage`) e o refresh, num cookie HttpOnly que o JavaScript não lê. Ao recarregar a página, `restore()`
 * pergunta ao servidor quem está logado — e é o cookie que responde.
 *
 * Duas consequências que o produto pediu:
 * - **aba anônima entra deslogada**, porque o perfil anônimo tem cookie e memória próprios (não se detecta o modo, e
 *   não é preciso: o isolamento do navegador faz o trabalho);
 * - **sair numa aba sai em todas**, pelo canal `BroadcastChannel` abaixo — o logout no servidor é o que vale, isto só
 *   alinha as outras abas na hora, sem esperar o próximo 401.
 */
interface SessionState {
  user: UserAccount | null;
  hydrated: boolean;
  login: (req: LoginRequest) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  restore: () => Promise<void>;
  setHydrated: (v: boolean) => void;
  /** atualiza o resumo da sessão após editar a conta (nome, e-mail, idioma) */
  setUser: (u: UserAccount | null) => void;
}

/** Consentimento de analytics do usuário da sessão: zera na troca de usuário e recarrega o do atual. */
function syncAnalyticsConsent(userId: string | null): Promise<void> {
  return syncConsentForUser(userId, { loadPrivacy: () => api.account.privacy(), currentUserId: () => useSession.getState().user?.id ?? null });
}

const CANAL = "scoresaf.auth";
type AvisoDeSaida = { type: "LOGOUT" };

/** O canal não entrega ao remetente: quem desloga limpa o próprio estado por conta. */
function canalDeSessao(): BroadcastChannel | null {
  if (typeof window === "undefined" || typeof BroadcastChannel === "undefined") return null;
  try {
    return new BroadcastChannel(CANAL);
  } catch {
    return null;
  }
}

const canal = canalDeSessao();

export const useSession = create<SessionState>()((set) => ({
  user: null,
  hydrated: false,
  setHydrated: (v) => set({ hydrated: v }),
  setUser: (user) => set({ user }),
  async login(req) {
    const res = await api.auth.login(req);
    if (API_MODE === "live") sessionTokenStorage.set(res.tokens);
    set({ user: res.user });
    void syncAnalyticsConsent(res.user.id);
  },
  async register(req) {
    const res = await api.auth.register(req);
    if (API_MODE === "live") sessionTokenStorage.set(res.tokens);
    set({ user: res.user });
    void syncAnalyticsConsent(res.user.id);
  },
  async logout() {
    try {
      await api.auth.logout();
    } finally {
      limpaSessaoLocal();
      canal?.postMessage({ type: "LOGOUT" } satisfies AvisoDeSaida);
    }
  },
  async restore() {
    try {
      const u = await api.auth.me();
      set({ user: u });
      void syncAnalyticsConsent(u.id);
    } catch {
      setAnalyticsConsent(false);
      set({ user: null });
    } finally {
      set({ hydrated: true });
    }
  },
}));

/** Limpa o que esta aba guarda da sessão; a invalidação que vale é a do servidor (OWASP). */
function limpaSessaoLocal(): void {
  sessionTokenStorage.set(null);
  setAnalyticsConsent(false);
  useSession.setState({ user: null });
}

if (canal) {
  canal.onmessage = (e: MessageEvent<AvisoDeSaida>) => {
    if (e.data?.type === "LOGOUT") limpaSessaoLocal();
  };
}

// Sessão expirada no meio do uso: sai de verdade (servidor + outras abas). Sem usuário em memória — visitante, ou a
// verificação inicial abaixo — só limpa o local: nada de chamar /auth/logout nem avisar as outras abas à toa.
onUnauthorized(() => {
  if (useSession.getState().user) void useSession.getState().logout().catch(() => undefined);
  else limpaSessaoLocal();
});

// Sem persistência, quem diz se há sessão ao abrir ou recarregar a página é o servidor (o cookie HttpOnly, no modo
// live; o estado do mock, no modo mock). Sem esta chamada, `hydrated` nunca vira true e o RequireAuth fica no esqueleto.
if (typeof window !== "undefined") void useSession.getState().restore();
