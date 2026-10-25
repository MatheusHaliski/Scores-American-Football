import { translate } from "@/i18n/runtime";
import { ApiError } from "../problem";
import type {
  AccountProfile,
  ActivityEntry,
  ConsentHistoryEntry,
  ConsentPurpose,
  ConsentState,
  DeletionStatus,
  ExportJob,
  Locale,
  NotificationKind,
  PageResponse,
  PrivacySettings,
  ProfileVisibility,
  UpdatePrivacyRequest,
  UpdateProfileRequest,
  UserAccount,
  UserSession,
  UserSettings,
  UUID,
} from "../types";
import { CONSENT_PURPOSES, NOTIFICATION_KINDS, PLAY_CALLING_LEVELS, QUARTER_MINUTES } from "../types";
import { uuidFrom } from "./rng";

/**
 * Conta, privacidade e direitos do titular no modo mock (RF46), com as mesmas regras do backend:
 * privacidade por padrão (nenhum consentimento marcado), histórico de consentimentos, exportação assíncrona
 * válida por 24 h, exclusão com dupla confirmação e 7 dias de arrependimento, idade mínima de 13 anos.
 */

const LEGAL_BASIS: Record<ConsentPurpose, string> = {
  ANALYTICS: "Art. 7º, I",
  MARKETING_EMAIL: "Art. 7º, I",
  PUBLIC_RANKING: "Art. 7º, I",
  AI_ANALYST: "Art. 7º, I",
  BIOMETRIC_AVATAR: "Art. 11, I",
};
/** Versão do texto por finalidade (igual ao ConsentPurpose do backend do basquete, que o futebol americano copia). */
const TEXT_VERSION: Record<ConsentPurpose, string> = {
  ANALYTICS: "2026-09-v1",
  MARKETING_EMAIL: "2026-09-v1",
  PUBLIC_RANKING: "2026-09-v1",
  AI_ANALYST: "2026-09-v1",
  BIOMETRIC_AVATAR: "2026-10-v2",
};
const CONFIRMATION_WORDS = new Set(["EXCLUIR", "DELETE", "ELIMINAR"]);
const MIN_AGE_YEARS = 13;
const GRACE_DAYS = 7;
const EXPORT_READY_MS = 1500;
const EXPORT_TTL_MS = 24 * 3600_000;

interface StoredConsent {
  granted: boolean;
  textVersion: string;
  source: ConsentHistoryEntry["source"];
  updatedAt: string;
}

interface AccountStore {
  birthDate: string | null;
  pendingEmail: string | null;
  createdAt: string;
  lastLoginAt: string;
  profileVisibility: ProfileVisibility;
  consents: Partial<Record<ConsentPurpose, StoredConsent>>;
  history: ConsentHistoryEntry[];
  notifications: Record<NotificationKind, boolean>;
  settings: UserSettings | null;
  exports: ExportJob[];
  activity: ActivityEntry[];
  revokedSessions: UUID[];
  deletionRequestedAt: string | null;
}

export function defaultSettings(locale: Locale): UserSettings {
  return {
    quarterDurationMinutes: QUARTER_MINUTES.default,
    playCallingLevel: "KEY_MOMENTS",
    difficulty: "NORMAL",
    backgroundArt: "",
    schemeArt: "led",
    soundtrack: "stadium",
    fontFamily: "default",
    reducedMotion: false,
    notificationsEnabled: true,
    theme: "SYSTEM",
    highContrast: false,
    fontScale: "MEDIUM",
    density: "COMFORTABLE",
    preferredLocale: locale,
    regionalFormat: locale,
    simulationSpeed: "FAST",
  };
}

const key = (userId: UUID) => `scoresaf.mock.account.${userId}`;

/** Cópia em memória por usuário: sem localStorage (modo privado, Node) o estado não se perde entre chamadas. */
const memory = new Map<UUID, AccountStore>();

function load(user: UserAccount): AccountStore {
  const cached = memory.get(user.id);
  if (cached) return cached;
  const st = loadStored(user);
  memory.set(user.id, st);
  return st;
}

function loadStored(user: UserAccount): AccountStore {
  try {
    const raw = typeof localStorage === "undefined" ? null : localStorage.getItem(key(user.id));
    if (raw) return JSON.parse(raw) as AccountStore;
  } catch {
    /* armazenamento indisponível */
  }
  const now = new Date().toISOString();
  return {
    birthDate: null,
    pendingEmail: null,
    createdAt: now,
    lastLoginAt: now,
    profileVisibility: "ONLY_ME",
    consents: {},
    history: [],
    notifications: Object.fromEntries(NOTIFICATION_KINDS.map((k) => [k, true])) as Record<NotificationKind, boolean>,
    settings: null,
    exports: [],
    activity: [{ action: "LOGIN", resource: "session", result: "SUCCESS", timestamp: now, ipMasked: "189.40.*.*", device: "Navegador" }],
    revokedSessions: [],
    deletionRequestedAt: null,
  };
}

function save(user: UserAccount, st: AccountStore): void {
  memory.set(user.id, st);
  try {
    localStorage.setItem(key(user.id), JSON.stringify(st));
  } catch {
    /* modo privado */
  }
}

function log(st: AccountStore, action: string, resource: string, result = "SUCCESS"): void {
  st.activity.unshift({ action, resource, result, timestamp: new Date().toISOString(), ipMasked: "189.40.*.*", device: "Navegador" });
  st.activity = st.activity.slice(0, 200);
}

function ageOn(birth: string, now = new Date()): number {
  const b = new Date(birth);
  let age = now.getFullYear() - b.getFullYear();
  const m = now.getMonth() - b.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < b.getDate())) age--;
  return age;
}

function refreshExports(st: AccountStore): void {
  const now = Date.now();
  for (const j of st.exports) {
    if (j.status === "REQUESTED" && now - Date.parse(j.requestedAt) >= EXPORT_READY_MS) {
      const ready = new Date(Date.parse(j.requestedAt) + EXPORT_READY_MS);
      j.status = "READY";
      j.readyAt = ready.toISOString();
      j.expiresAt = new Date(ready.getTime() + EXPORT_TTL_MS).toISOString();
      j.jsonUrl = `/me/export/${j.id}/download?format=json`;
      j.csvUrl = `/me/export/${j.id}/download?format=csv`;
      j.sizeBytes = 18_432;
    }
    if (j.status === "READY" && j.expiresAt && now > Date.parse(j.expiresAt)) {
      j.status = "EXPIRED";
      j.jsonUrl = null;
      j.csvUrl = null;
    }
  }
}

function consentStates(st: AccountStore): ConsentState[] {
  return CONSENT_PURPOSES.map((purpose) => {
    const c = st.consents[purpose];
    return {
      purpose,
      granted: c?.granted ?? false,
      legalBasis: LEGAL_BASIS[purpose],
      textVersion: c?.textVersion ?? null,
      currentTextVersion: TEXT_VERSION[purpose],
      sensitive: purpose === "BIOMETRIC_AVATAR",
      outdated: !!c?.granted && c.textVersion !== TEXT_VERSION[purpose],
      updatedAt: c?.updatedAt ?? null,
      source: c?.source ?? null,
    };
  });
}

function privacyView(st: AccountStore): PrivacySettings {
  return {
    profileVisibility: st.profileVisibility,
    consents: consentStates(st),
    notifications: { ...st.notifications },
    marketingConsent: st.consents.MARKETING_EMAIL?.granted ?? false,
  };
}

function deletion(st: AccountStore): DeletionStatus {
  if (!st.deletionRequestedAt) return { status: "ACTIVE", requestedAt: null, scheduledFor: null };
  return {
    status: "PENDING_DELETION",
    requestedAt: st.deletionRequestedAt,
    scheduledFor: new Date(Date.parse(st.deletionRequestedAt) + GRACE_DAYS * 86_400_000).toISOString(),
  };
}

export interface MockAccount {
  profile(): AccountProfile;
  updateProfile(req: UpdateProfileRequest): AccountProfile;
  changeEmail(newEmail: string, currentPassword: string): void;
  changePassword(currentPassword: string, newPassword: string): void;
  sessions(): UserSession[];
  revokeSession(id: UUID): void;
  settings(): UserSettings;
  updateSettings(patch: Partial<UserSettings>): UserSettings;
  privacy(): PrivacySettings;
  updatePrivacy(req: UpdatePrivacyRequest): PrivacySettings;
  history(): ConsentHistoryEntry[];
  requestExport(): ExportJob;
  exports(): ExportJob[];
  exportJob(id: UUID): ExportJob;
  /** `extra`: seções de outros módulos da conta, como o AccountDataCollector do backend */
  download(id: UUID, format: "json" | "csv", extra?: Record<string, unknown>): Blob;
  activity(page: number, size: number): PageResponse<ActivityEntry>;
  deleteAccount(currentPassword: string, confirmation: string): DeletionStatus;
  deletionStatus(): DeletionStatus;
  cancelDeletion(): DeletionStatus;
  /** trilha de atividade (RF46) para ações de outros módulos da conta */
  logActivity(action: string, resource: string): void;
}

/** Fachada da conta mock para o usuário da sessão; `onUser` persiste mudanças no resumo da sessão (nome, idioma). */
export function mockAccount(user: UserAccount, onUser: (u: UserAccount) => void): MockAccount {
  const st = load(user);
  const commit = () => save(user, st);

  const profile = (): AccountProfile => ({
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    avatarUrl: user.avatarUrl,
    locale: user.locale,
    role: user.role,
    status: st.deletionRequestedAt ? "PENDING_DELETION" : "ACTIVE",
    marketingConsent: st.consents.MARKETING_EMAIL?.granted ?? false,
    dataProcessingConsentAt: st.createdAt,
    lastLoginAt: st.lastLoginAt,
    createdAt: st.createdAt,
    profileVisibility: st.profileVisibility,
    birthDate: st.birthDate,
    pendingEmail: st.pendingEmail,
    deletionScheduledFor: deletion(st).scheduledFor,
  });

  const settings = (): UserSettings => st.settings ?? defaultSettings(user.locale);

  return {
    profile,
    updateProfile(req) {
      const errors: Record<string, string> = {};
      if (req.displayName !== undefined) {
        const n = req.displayName.trim();
        if (n.length < 2 || n.length > 80) errors.displayName = translate("errors", "INVALID_NAME");
      }
      if (req.birthDate) {
        if (Number.isNaN(Date.parse(req.birthDate)) || Date.parse(req.birthDate) > Date.now()) throw ApiError.badRequest("INVALID_BIRTH_DATE");
        if (ageOn(req.birthDate) < MIN_AGE_YEARS) throw ApiError.badRequest("MIN_AGE", { 0: MIN_AGE_YEARS });
      }
      if (Object.keys(errors).length) throw ApiError.badRequest("INVALID_NAME", undefined, errors);
      if (req.displayName !== undefined) user = { ...user, displayName: req.displayName.trim() };
      if (req.locale) user = { ...user, locale: req.locale };
      if (req.avatarUrl !== undefined) user = { ...user, avatarUrl: req.avatarUrl };
      if (req.birthDate !== undefined) st.birthDate = req.birthDate;
      onUser(user);
      log(st, "SENSITIVE_DATA_CHANGED", "account");
      commit();
      return profile();
    },
    changeEmail(newEmail, currentPassword) {
      if (!currentPassword) throw ApiError.badRequest("AUTH_FAILED");
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(newEmail)) throw ApiError.badRequest("INVALID_EMAIL");
      if (newEmail.toLowerCase() === user.email.toLowerCase()) throw ApiError.badRequest("EMAIL_UNCHANGED");
      st.pendingEmail = newEmail;
      log(st, "EMAIL_CHANGE_REQUESTED", "account");
      commit();
    },
    changePassword(currentPassword, newPassword) {
      if (!currentPassword) throw ApiError.badRequest("AUTH_FAILED");
      if (newPassword.length < 8) throw ApiError.badRequest("WEAK_PASSWORD");
      st.revokedSessions.push(uuidFrom("session", `${user.id}:tablet`));
      log(st, "PASSWORD_CHANGED", "account");
      commit();
    },
    sessions() {
      const now = Date.now();
      const all: UserSession[] = [
        { id: uuidFrom("session", `${user.id}:current`), ip: "189.40.*.*", userAgent: "Este navegador", createdAt: st.lastLoginAt, expiresAt: new Date(now + 30 * 86_400_000).toISOString(), current: true },
        { id: uuidFrom("session", `${user.id}:tablet`), ip: "177.12.*.*", userAgent: "Scores: American Football · Tablet Android", createdAt: new Date(now - 3 * 86_400_000).toISOString(), expiresAt: new Date(now + 27 * 86_400_000).toISOString(), current: false },
      ];
      return all.filter((s) => !st.revokedSessions.includes(s.id));
    },
    revokeSession(id) {
      if (id === uuidFrom("session", `${user.id}:current`)) throw ApiError.badRequest("INVALID_VALUE", { 0: "session" });
      st.revokedSessions.push(id);
      log(st, "SESSION_REVOKED", "session");
      commit();
    },
    settings,
    updateSettings(patch) {
      const q = patch.quarterDurationMinutes;
      if (q !== undefined && (!Number.isInteger(q) || q < QUARTER_MINUTES.min || q > QUARTER_MINUTES.max)) throw ApiError.of(422, "INVALID_QUARTER_DURATION", { 0: QUARTER_MINUTES.min, 1: QUARTER_MINUTES.max });
      if (patch.playCallingLevel !== undefined && !PLAY_CALLING_LEVELS.includes(patch.playCallingLevel)) throw ApiError.badRequest("INVALID_VALUE", { 0: String(patch.playCallingLevel) });
      st.settings = { ...settings(), ...patch };
      if (patch.preferredLocale) {
        user = { ...user, locale: patch.preferredLocale };
        onUser(user);
      }
      log(st, "SETTINGS_UPDATED", "settings");
      commit();
      return st.settings;
    },
    privacy: () => privacyView(st),
    updatePrivacy(req) {
      const now = new Date().toISOString();
      const source = req.source ?? "SETTINGS";
      for (const [p, granted] of Object.entries(req.consents ?? {}) as [ConsentPurpose, boolean][]) {
        if (!CONSENT_PURPOSES.includes(p)) throw ApiError.badRequest("INVALID_CONSENT_PURPOSE", { 0: p });
        const prev = st.consents[p]?.granted ?? false;
        // reaceite do texto novo (Q-81): grava um registro novo na versão atual, sem cascata de revogação
        const reaccept = granted && prev && st.consents[p]?.textVersion !== TEXT_VERSION[p];
        if (prev === granted && !reaccept) continue;
        st.consents[p] = { granted, textVersion: TEXT_VERSION[p], source, updatedAt: now };
        st.history.unshift({ purpose: p, granted, textVersion: TEXT_VERSION[p], source, recordedAt: now });
        log(st, granted ? "CONSENT_GRANTED" : "CONSENT_REVOKED", p);
        if (p === "BIOMETRIC_AVATAR" && !granted) log(st, "AVATAR_MEDIA_DELETED", "custom_player");
      }
      for (const [k, v] of Object.entries(req.notifications ?? {}) as [NotificationKind, boolean][]) {
        if (NOTIFICATION_KINDS.includes(k)) st.notifications[k] = v;
      }
      if (req.profileVisibility) {
        if (!["ONLY_ME", "FRIENDS", "PUBLIC"].includes(req.profileVisibility)) throw ApiError.badRequest("INVALID_PROFILE_VISIBILITY", { 0: req.profileVisibility });
        if (req.profileVisibility !== st.profileVisibility) log(st, "PROFILE_VISIBILITY_CHANGED", "account");
        st.profileVisibility = req.profileVisibility;
      }
      commit();
      return privacyView(st);
    },
    history: () => [...st.history],
    requestExport() {
      refreshExports(st);
      const pending = st.exports.find((j) => j.status === "REQUESTED");
      if (pending) return { ...pending };
      const job: ExportJob = { id: uuidFrom("export", `${user.id}:${Date.now()}`), status: "REQUESTED", requestedAt: new Date().toISOString(), readyAt: null, expiresAt: null, sizeBytes: 0, jsonUrl: null, csvUrl: null };
      st.exports.unshift(job);
      st.exports = st.exports.slice(0, 10);
      log(st, "DATA_EXPORT_REQUESTED", "export");
      commit();
      return { ...job };
    },
    exports() {
      refreshExports(st);
      commit();
      return st.exports.map((j) => ({ ...j }));
    },
    exportJob(id) {
      refreshExports(st);
      const j = st.exports.find((x) => x.id === id);
      if (!j) throw ApiError.notFound("export");
      commit();
      return { ...j };
    },
    download(id, format, extra = {}) {
      refreshExports(st);
      const j = st.exports.find((x) => x.id === id);
      if (!j) throw ApiError.notFound("export");
      if (j.status !== "READY") throw ApiError.of(410, "EXPORT_EXPIRED");
      log(st, "DATA_EXPORT_DOWNLOADED", format);
      commit();
      const data = { account: profile(), settings: settings(), privacy: privacyView(st), consentHistory: st.history, sessions: this.sessions(), activity: st.activity, ...extra, exportedAt: new Date().toISOString() };
      if (format === "json") return new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const rows = [
        ["field", "value"],
        ["id", user.id],
        ["email", user.email],
        ["displayName", user.displayName],
        ["locale", user.locale],
        ["profileVisibility", st.profileVisibility],
        ...CONSENT_PURPOSES.map((p) => [`consent.${p}`, String(st.consents[p]?.granted ?? false)]),
      ];
      return new Blob([rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(",")).join("\n")], { type: "text/csv" });
    },
    activity(page, size) {
      const content = st.activity.slice(page * size, page * size + size);
      return { content, page, size, totalElements: st.activity.length, totalPages: Math.max(1, Math.ceil(st.activity.length / size)) };
    },
    deleteAccount(currentPassword, confirmation) {
      if (!currentPassword) throw ApiError.badRequest("AUTH_FAILED");
      if (!CONFIRMATION_WORDS.has(confirmation.trim().toUpperCase())) throw ApiError.badRequest("DELETION_CONFIRMATION_MISMATCH");
      st.deletionRequestedAt = new Date().toISOString();
      log(st, "ACCOUNT_DELETION_REQUESTED", "account");
      commit();
      return deletion(st);
    },
    deletionStatus: () => deletion(st),
    cancelDeletion() {
      if (!st.deletionRequestedAt) throw ApiError.conflict("NO_PENDING_DELETION");
      st.deletionRequestedAt = null;
      log(st, "ACCOUNT_DELETION_CANCELLED", "account");
      commit();
      return deletion(st);
    },
    logActivity(action, resource) {
      log(st, action, resource);
      commit();
    },
  };
}
