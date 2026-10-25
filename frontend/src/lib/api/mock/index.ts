import type { ScoresApi } from "../contract";
import { ApiError } from "../problem";
import type { AuthResponse, UserAccount } from "../types";
import { getLocale, translate } from "@/i18n/runtime";
import { matchLocale } from "@/i18n/config";
import { mockAccount } from "./account";
import { mockCareers } from "./career";
import { mockMatches } from "./match";
import { uuidFrom } from "./rng";
import { getUser, setUser } from "./state";

const LATENCY = 120;
function delay<T>(v: T, ms = LATENCY): Promise<T> {
  return new Promise((r) => setTimeout(() => r(structuredClone(v)), ms));
}

/** Ações que lançam (validação do mock) viram promessa rejeitada depois da mesma latência das demais. */
function later<T>(fn: () => T, ms = LATENCY): Promise<T> {
  return new Promise((resolve, reject) => setTimeout(() => Promise.resolve().then(fn).then((v) => resolve(structuredClone(v)), reject), ms));
}

function requireUser(): UserAccount {
  const u = getUser();
  if (!u) throw ApiError.of(401, "UNAUTHENTICATED");
  return u;
}

/** Conta mock do usuário da sessão (RF46); alterações de nome/idioma voltam para o resumo da sessão. */
function acct() {
  return mockAccount(requireUser(), (u) => setUser(u));
}

/** Carreiras mock do usuário da sessão (bloco 6a). */
function careers() {
  return mockCareers(requireUser());
}

/** Plano de jogo, playbook, semana e partida mock do usuário da sessão (bloco 6b). */
function matches() {
  return mockMatches(requireUser());
}

/** Tokens de mentira gerados na hora (o modo mock não manda Bearer a lugar nenhum). */
function auth(user: UserAccount): AuthResponse {
  return { user, tokens: { accessToken: `mock.${crypto.randomUUID()}`, refreshToken: null, accessExpiresAt: Date.now() + 15 * 60_000 } };
}

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

function newUser(email: string, displayName: string, locale?: string): UserAccount {
  const roles = email.toLowerCase().startsWith("admin") ? ["ADMIN"] : ["PLAYER"];
  return { id: uuidFrom("user", email.toLowerCase()), email, displayName, avatarUrl: null, locale: matchLocale(locale) ?? getLocale(), role: roles.includes("ADMIN") ? "ADMIN" : "PLAYER", roles };
}

/** Implementação `mock` do contrato: tudo no navegador, persistido em localStorage, com as regras do backend (6b: partida por descida). */
export function createMockApi(): ScoresApi {
  return {
    auth: {
      login: (req) =>
        later(() => {
          if (!EMAIL.test(req.email) || !req.password) throw ApiError.of(401, "AUTH_FAILED", undefined, { password: translate("auth", "validation.passwordRequired") });
          const existing = getUser();
          const user = existing && existing.email === req.email ? existing : newUser(req.email, req.email.split("@")[0]);
          setUser(user);
          return auth(user);
        }, 400),
      register: (req) =>
        later(() => {
          if (!EMAIL.test(req.email)) throw ApiError.badRequest("VALIDATION", undefined, { email: translate("auth", "validation.email") });
          if (req.password.length < 8) throw ApiError.badRequest("VALIDATION", undefined, { password: translate("auth", "validation.password") });
          const name = req.displayName.trim();
          if (name.length < 2 || name.length > 80) throw ApiError.badRequest("VALIDATION", undefined, { displayName: translate("auth", "validation.displayName") });
          const user = newUser(req.email, name, req.locale);
          setUser(user);
          if (req.marketingConsent) mockAccount(user, setUser).updatePrivacy({ consents: { MARKETING_EMAIL: true }, source: "SIGNUP" });
          return auth(user);
        }, 500),
      async logout() {
        setUser(null);
        return delay(undefined);
      },
      me: () => later(() => requireUser()),
      forgotPassword: (req) =>
        later(() => {
          if (!EMAIL.test(req.email)) throw ApiError.badRequest("VALIDATION", undefined, { email: translate("auth", "validation.email") });
          // resposta uniforme: não revela se a conta existe
        }, 300),
      resetPassword: (req) =>
        later(() => {
          if (!req.token.trim()) throw ApiError.of(401, "INVALID_TOKEN");
          if (req.newPassword.length < 8) throw ApiError.badRequest("WEAK_PASSWORD");
        }, 300),
      verifyEmail: (token) =>
        later(() => {
          if (!token.trim()) throw ApiError.badRequest("INVALID_TOKEN");
        }, 300),
      settings: () => later(() => acct().settings()),
      updateSettings: (patch) => later(() => acct().updateSettings(patch)),
    },
    account: {
      profile: () => later(() => acct().profile()),
      updateProfile: (req) => later(() => acct().updateProfile(req), 250),
      changeEmail: (req) => later(() => acct().changeEmail(req.newEmail, req.currentPassword), 300),
      changePassword: (req) => later(() => acct().changePassword(req.currentPassword, req.newPassword), 300),
      sessions: () => later(() => acct().sessions()),
      revokeSession: (id) => later(() => acct().revokeSession(id)),
      privacy: () => later(() => acct().privacy()),
      updatePrivacy: (req) => later(() => acct().updatePrivacy(req), 200),
      consentHistory: () => later(() => acct().history()),
      requestExport: () => later(() => acct().requestExport(), 250),
      exportJobs: () => later(() => acct().exports()),
      exportJob: (id) => later(() => acct().exportJob(id)),
      // Blob não passa por structuredClone com segurança em todos os ambientes: devolve direto
      downloadExport: (id, format) => new Promise((resolve, reject) => setTimeout(() => {
        try {
          resolve(acct().download(id, format));
        } catch (e) {
          reject(e);
        }
      }, LATENCY)),
      activity: (page = 0, size = 20) => later(() => acct().activity(page, size)),
      deleteAccount: (req) => later(() => acct().deleteAccount(req.currentPassword, req.confirmation), 400),
      deletionStatus: () => later(() => acct().deletionStatus()),
      cancelDeletion: () => later(() => acct().cancelDeletion(), 300),
    },
    worlds: {
      list: () => later(() => careers().worlds()),
      leagues: (worldId) => later(() => careers().leagues(worldId)),
      leagueClubs: (worldId, leagueId) => later(() => careers().leagueClubs(worldId, leagueId)),
    },
    careers: {
      list: () => later(() => careers().list()),
      create: (req) => later(() => careers().create(req), 600),
      get: (id) => later(() => careers().get(id)),
      update: (id, req) => later(() => careers().update(id, req), 200),
      remove: (id, confirm) => later(() => careers().remove(id, confirm), 300),
      dashboard: (id) => later(() => ({ ...careers().dashboard(id), live: matches().liveRef(id) })),
      calendar: (id, week) => later(() => careers().calendar(id, week)),
    },
    roster: {
      get: (careerId) => later(() => careers().roster(careerId)),
      player: (careerId, playerId) => later(() => careers().player(careerId, playerId)),
      changeStatus: (careerId, playerId, status) => later(() => careers().changeStatus(careerId, playerId, status), 200),
      release: (careerId, playerId) => later(() => careers().release(careerId, playerId), 250),
    },
    depthChart: {
      get: (careerId) => later(() => careers().depthChart(careerId)),
      save: (careerId, req) => later(() => careers().saveDepthChart(careerId, req), 250),
      setRole: (careerId, playerId, req) => later(() => careers().setRole(careerId, playerId, req), 200),
    },
    gamePlan: {
      get: (careerId) => later(() => matches().gamePlan(careerId)),
      save: (careerId, req) => later(() => matches().saveGamePlan(careerId, req), 200),
      playbook: (careerId) => later(() => matches().playbook(careerId)),
      savePlaybook: (careerId, req) => later(() => matches().savePlaybook(careerId, req), 200),
      preparation: (careerId) => later(() => matches().preparation(careerId), 250),
    },
    weeks: {
      playCurrent: (careerId) => later(() => matches().playCurrent(careerId), 300),
      get: (careerId, week) => later(() => matches().week(careerId, week)),
    },
    matches: {
      get: (mid) => later(() => matches().get(mid)),
      advance: (mid, req) => later(() => matches().advance(mid, req), 160),
      decisions: (mid, req) => later(() => matches().decisions(mid, req), 160),
      playCall: (mid, decisionId, req) => later(() => matches().playCall(mid, decisionId, req), 160),
      events: (mid, after, limit) => later(() => matches().events(mid, after, limit), 60),
      report: (mid) => later(() => matches().report(mid)),
    },
  };
}
