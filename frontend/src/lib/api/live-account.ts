/**
 * Adaptadores das respostas de conta do backend Java para os tipos do front. Puros (sem rede), testados contra as
 * amostras gravadas pela API: auth-login.json (`AuthController.TokenResponse`), me.json (`AccountDto`) e
 * me-settings.json (`SettingsDto`) em docs/validation/golden/ (D-5).
 */
import { DEFAULT_LOCALE, matchLocale } from "@/i18n/config";
import { toEpochMs } from "./client";
import { PLAY_CALLING_LEVELS, QUARTER_MINUTES, type AccountProfile, type AuthResponse, type AuthTokens, type ProfileVisibility, type UserAccount, type UserSettings, type UserStatus } from "./types";

/** `AuthController.UserSummary`. */
export interface UserSummaryDto {
  id: string;
  displayName: string;
  roles: string[];
}

/**
 * `AuthController.TokenResponse` de POST /auth/login e /auth/refresh. `refreshToken` vem nulo no navegador: o refresh
 * mora no cookie HttpOnly (D-114) e o JavaScript nunca o vê.
 */
export interface TokenResponseDto {
  accessToken: string;
  accessExpiresAt: string;
  refreshToken: string | null;
  refreshExpiresAt: string;
  user: UserSummaryDto;
}

/** `AccountDto` de GET/PUT /me. */
export interface AccountDto {
  id: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  locale: string;
  role: string;
  status: string;
  marketingConsent: boolean;
  dataProcessingConsentAt: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  profileVisibility: string;
  birthDate: string | null;
  pendingEmail: string | null;
  deletionScheduledFor: string | null;
}

/**
 * `SettingsDto` de GET/PUT /me/settings (enums como texto; as strings livres podem vir nulas do banco). No futebol americano
 * (D-10) o `matchSpeed` do basquete deu lugar a `quarterDurationMinutes` e `playCallingLevel`.
 */
export type SettingsDto = { [K in keyof UserSettings]: UserSettings[K] extends string ? string | null : UserSettings[K] };

/** Campos que o front lê de cada resposta (o teste de contrato confere que o golden traz exatamente estes). */
export const TOKEN_RESPONSE_FIELDS = ["accessExpiresAt", "accessToken", "refreshExpiresAt", "refreshToken", "user"] as const;
export const USER_SUMMARY_FIELDS = ["displayName", "id", "roles"] as const;
export const ACCOUNT_FIELDS = [
  "avatarUrl",
  "birthDate",
  "createdAt",
  "dataProcessingConsentAt",
  "deletionScheduledFor",
  "displayName",
  "email",
  "id",
  "lastLoginAt",
  "locale",
  "marketingConsent",
  "pendingEmail",
  "profileVisibility",
  "role",
  "status",
] as const;
export const SETTINGS_FIELDS = [
  "backgroundArt",
  "density",
  "difficulty",
  "fontFamily",
  "fontScale",
  "highContrast",
  "notificationsEnabled",
  "playCallingLevel",
  "preferredLocale",
  "quarterDurationMinutes",
  "reducedMotion",
  "regionalFormat",
  "schemeArt",
  "simulationSpeed",
  "soundtrack",
  "theme",
] as const;

/** Resumo da sessão a partir do `UserSummary` do login (sem e-mail) ou do `AccountDto` de GET /me. */
export function userFromDto(raw: Partial<UserSummaryDto> & Partial<Pick<AccountDto, "email" | "avatarUrl" | "locale" | "role">> & { id: string; displayName: string }): UserAccount {
  const roles = raw.roles ?? (raw.role ? [raw.role] : ["PLAYER"]);
  return {
    id: raw.id,
    email: raw.email ?? "",
    displayName: raw.displayName,
    avatarUrl: raw.avatarUrl ?? null,
    locale: matchLocale(raw.locale) ?? DEFAULT_LOCALE,
    role: roles.includes("ADMIN") ? "ADMIN" : "PLAYER",
    roles,
  };
}

export function authFromDto(raw: TokenResponseDto): AuthResponse {
  const tokens: AuthTokens = { accessToken: raw.accessToken, refreshToken: raw.refreshToken ?? null, accessExpiresAt: toEpochMs(raw.accessExpiresAt) };
  return { user: userFromDto(raw.user), tokens };
}

const STATUSES: UserStatus[] = ["ACTIVE", "BLOCKED", "PENDING_DELETION", "DELETED"];
const VISIBILITIES: ProfileVisibility[] = ["ONLY_ME", "FRIENDS", "PUBLIC"];

export function profileFromDto(raw: AccountDto): AccountProfile {
  return {
    id: raw.id,
    email: raw.email,
    displayName: raw.displayName,
    avatarUrl: raw.avatarUrl ?? null,
    locale: matchLocale(raw.locale) ?? DEFAULT_LOCALE,
    role: raw.role === "ADMIN" ? "ADMIN" : "PLAYER",
    status: (STATUSES as string[]).includes(raw.status) ? (raw.status as UserStatus) : "ACTIVE",
    marketingConsent: !!raw.marketingConsent,
    dataProcessingConsentAt: raw.dataProcessingConsentAt ?? null,
    lastLoginAt: raw.lastLoginAt ?? null,
    createdAt: raw.createdAt,
    // privacidade por padrão: valor desconhecido conta como "só eu"
    profileVisibility: (VISIBILITIES as string[]).includes(raw.profileVisibility) ? (raw.profileVisibility as ProfileVisibility) : "ONLY_ME",
    birthDate: raw.birthDate ?? null,
    pendingEmail: raw.pendingEmail ?? null,
    deletionScheduledFor: raw.deletionScheduledFor ?? null,
  };
}

const oneOf = <T extends string>(v: string | null, allowed: readonly T[], fallback: T): T => ((allowed as readonly string[]).includes(v ?? "") ? (v as T) : fallback);

/** Minutos por quarto dentro da faixa do backend; fora dela (ou ausente), o padrão. */
function quarterMinutes(v: unknown): number {
  return typeof v === "number" && Number.isInteger(v) && v >= QUARTER_MINUTES.min && v <= QUARTER_MINUTES.max ? v : QUARTER_MINUTES.default;
}

/** O backend devolve tags BCP-47 (`pt-BR`); aceita também o legado `PT_BR`/`EN`. Valor fora do enum cai no padrão. */
export function settingsFromDto(raw: SettingsDto): UserSettings {
  const preferredLocale = matchLocale(raw.preferredLocale) ?? DEFAULT_LOCALE;
  return {
    quarterDurationMinutes: quarterMinutes(raw.quarterDurationMinutes),
    playCallingLevel: oneOf(raw.playCallingLevel, PLAY_CALLING_LEVELS, "KEY_MOMENTS"),
    difficulty: oneOf(raw.difficulty, ["EASY", "NORMAL", "HARD", "LEGEND"], "NORMAL"),
    backgroundArt: raw.backgroundArt ?? "",
    schemeArt: raw.schemeArt ?? "",
    soundtrack: raw.soundtrack ?? "",
    fontFamily: raw.fontFamily ?? "",
    reducedMotion: !!raw.reducedMotion,
    notificationsEnabled: raw.notificationsEnabled !== false,
    theme: oneOf(raw.theme, ["SYSTEM", "DARK", "LIGHT"], "SYSTEM"),
    highContrast: !!raw.highContrast,
    fontScale: oneOf(raw.fontScale, ["SMALL", "MEDIUM", "LARGE"], "MEDIUM"),
    density: oneOf(raw.density, ["COMFORTABLE", "COMPACT"], "COMFORTABLE"),
    preferredLocale,
    regionalFormat: raw.regionalFormat || preferredLocale,
    simulationSpeed: oneOf(raw.simulationSpeed, ["INSTANT", "FAST", "NORMAL"], "FAST"),
  };
}
