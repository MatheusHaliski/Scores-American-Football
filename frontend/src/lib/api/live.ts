import { HttpClient, type TokenStorage } from "./client";
import type { ScoresApi } from "./contract";
import { authFromDto, profileFromDto, settingsFromDto, userFromDto, type AccountDto, type SettingsDto, type TokenResponseDto } from "./live-account";

/** Implementação `live` do contrato: chama o backend Java em NEXT_PUBLIC_API_URL (rotas iguais às do Scores de basquete). */
export function createLiveApi(baseUrl: string, tokens: TokenStorage, onUnauthorized?: () => void): ScoresApi {
  const http = new HttpClient({ baseUrl, tokens, onUnauthorized });

  const login: ScoresApi["auth"]["login"] = async (req) => {
    const auth = authFromDto(await http.post<TokenResponseDto>("/auth/login", req));
    tokens.set(auth.tokens);
    return auth;
  };

  return {
    auth: {
      login,
      // POST /auth/register responde 201 `{ userId }` sem tokens: a sessão nasce do login logo em seguida
      async register(req) {
        await http.post<{ userId: string }>("/auth/register", { marketingConsent: false, ...req });
        return login({ email: req.email, password: req.password });
      },
      async logout() {
        try {
          // o servidor acha a sessão pelo cookie; o token só vai no corpo se existir (cliente fora do navegador)
          await http.post<void>("/auth/logout", { refreshToken: tokens.get()?.refreshToken ?? null });
        } finally {
          tokens.set(null);
        }
      },
      me: async () => userFromDto(await http.get<AccountDto>("/me")),
      forgotPassword: async (req) => {
        await http.post("/auth/password/forgot", req);
      },
      resetPassword: (req) => http.post("/auth/password/reset", req),
      settings: async () => settingsFromDto(await http.get<SettingsDto>("/me/settings")),
      updateSettings: async (patch) => settingsFromDto(await http.put<SettingsDto>("/me/settings", patch)),
    },
    account: {
      profile: async () => profileFromDto(await http.get<AccountDto>("/me")),
      updateProfile: async (req) => profileFromDto(await http.put<AccountDto>("/me", req)),
      changeEmail: async (req) => {
        await http.put("/me/email", req);
      },
      // a sessão que sobrevive à troca de senha o servidor tira do cookie (D-114)
      changePassword: (req) => http.put("/me/password", { ...req, currentRefreshToken: tokens.get()?.refreshToken ?? null }),
      sessions: () => http.get("/me/sessions"),
      revokeSession: (id) => http.delete(`/me/sessions/${id}`),
      privacy: () => http.get("/me/consents"),
      updatePrivacy: (req) => http.put("/me/consents", { source: "SETTINGS", ...req }),
      consentHistory: () => http.get("/me/consents/history"),
      requestExport: () => http.post("/me/export"),
      exportJobs: () => http.get("/me/export/jobs"),
      exportJob: (id) => http.get(`/me/export/${id}`),
      downloadExport: (id, format) => http.blob(`/me/export/${id}/download`, { format }),
      activity: (page = 0, size = 20) => http.get("/me/activity", { page, size }),
      deleteAccount: (req) => http.delete("/me", req),
      deletionStatus: () => http.get("/me/deletion"),
      cancelDeletion: () => http.post("/me/deletion/cancel"),
    },
  };
}
