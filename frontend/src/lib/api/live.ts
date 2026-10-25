import { HttpClient, type TokenStorage } from "./client";
import type { ScoresApi } from "./contract";
import {
  calendarFromDto,
  careerFromDto,
  careerListFromDto,
  dashboardFromDto,
  depthChartFromDto,
  leagueClubsFrom,
  leagueFromDto,
  playerDetailFromDto,
  releaseFromDto,
  rosterChangeFromDto,
  rosterFromDto,
  worldFromDto,
} from "./live-career";
import { gamePlanFromDto, matchEventsFromDto, matchReportFromDto, matchStateFromDto, playbookFromDto, preparationFromDto, weekBoardFromDto } from "./live-match";
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
      verifyEmail: async (token) => {
        await http.post("/auth/email/verify", { token });
      },
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
    worlds: {
      list: async () => (await http.get<unknown[]>("/worlds")).map(worldFromDto),
      leagues: async (worldId) => (await http.get<unknown[]>(`/worlds/${worldId}/leagues`)).map(leagueFromDto),
      leagueClubs: async (worldId, leagueId) => {
        const [structure, clubs] = await Promise.all([http.get<unknown>(`/worlds/${worldId}/leagues/${leagueId}/structure`), http.get<unknown>(`/leagues/${leagueId}/clubs`)]);
        return leagueClubsFrom(leagueId, structure, clubs);
      },
    },
    careers: {
      list: async () => careerListFromDto(await http.get("/careers")),
      create: async (req) => careerFromDto(await http.post("/careers", req)),
      get: async (id) => careerFromDto(await http.get(`/careers/${id}`)),
      update: async (id, req) => careerFromDto(await http.patch(`/careers/${id}`, req)),
      remove: (id, confirm) => http.delete(`/careers/${id}?confirm=${encodeURIComponent(confirm)}`),
      dashboard: async (id) => dashboardFromDto(await http.get(`/careers/${id}/dashboard`)),
      calendar: async (id, week) => calendarFromDto(await http.get(`/careers/${id}/calendar`, { week })),
    },
    roster: {
      get: async (careerId) => rosterFromDto(await http.get(`/careers/${careerId}/roster`)),
      player: async (careerId, playerId) => playerDetailFromDto(await http.get(`/careers/${careerId}/players/${playerId}`)),
      changeStatus: async (careerId, playerId, status) => rosterChangeFromDto(await http.patch(`/careers/${careerId}/players/${playerId}/roster-status`, { status })),
      release: async (careerId, playerId) => releaseFromDto(await http.post(`/careers/${careerId}/players/${playerId}/release`)),
    },
    depthChart: {
      get: async (careerId) => depthChartFromDto(await http.get(`/careers/${careerId}/depth-chart`)),
      save: async (careerId, req) => depthChartFromDto(await http.put(`/careers/${careerId}/depth-chart`, req)),
      setRole: async (careerId, playerId, req) => depthChartFromDto(await http.patch(`/careers/${careerId}/players/${playerId}/role`, req)),
    },
    gamePlan: {
      get: async (careerId) => gamePlanFromDto(await http.get(`/careers/${careerId}/game-plan`)),
      save: async (careerId, req) => gamePlanFromDto(await http.put(`/careers/${careerId}/game-plan`, req)),
      playbook: async (careerId) => playbookFromDto(await http.get(`/careers/${careerId}/playbook`)),
      savePlaybook: async (careerId, req) => playbookFromDto(await http.put(`/careers/${careerId}/playbook`, req)),
      preparation: async (careerId) => preparationFromDto(await http.get(`/careers/${careerId}/next-match/preparation`)),
    },
    weeks: {
      playCurrent: async (careerId) => weekBoardFromDto(await http.post(`/careers/${careerId}/weeks/current/play`)),
      get: async (careerId, week) => weekBoardFromDto(await http.get(`/careers/${careerId}/weeks/${week}`)),
    },
    matches: {
      get: async (mid) => matchStateFromDto(await http.get(`/matches/${mid}`)),
      advance: async (mid, req) => matchStateFromDto(await http.post(`/matches/${mid}/advance`, req ?? {})),
      decisions: async (mid, req) => matchStateFromDto(await http.post(`/matches/${mid}/decisions`, req)),
      playCall: async (mid, decisionId, req) => matchStateFromDto(await http.post(`/matches/${mid}/play-calls/${encodeURIComponent(decisionId)}`, req)),
      events: async (mid, after = 0, limit = 200) => matchEventsFromDto(await http.get(`/matches/${mid}/events`, { after, limit })),
      report: async (mid) => matchReportFromDto(await http.get(`/matches/${mid}/report`)),
    },
  };
}
