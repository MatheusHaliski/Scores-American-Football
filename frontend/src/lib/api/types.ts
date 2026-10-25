/**
 * DTOs de conta, configurações e privacidade (RF1–RF3, RF10, RF46) — espelham os records Java das rotas
 * `/api/v1/auth/...` e `/api/v1/me/...` (AuthController, AccountController, AccountDto, SettingsDto, SessionDto).
 * Os tipos da carreira (bloco 6a) ficam em `career-types.ts` e os da partida (bloco 6b) em `match-types.ts`, reexportados aqui.
 */
export type UUID = string;
export type ISODate = string;

/* ───────────────────────── Conta ───────────────────────── */
/** Idiomas suportados (BCP-47). O backend legado usa PT_BR/EN — mapeado em `live.ts`. */
export type Locale = "pt-BR" | "en-US" | "es-ES";
export type UserRole = "PLAYER" | "ADMIN";
/**
 * Quando o manager chama a jogada na partida ao vivo (RF10/RF62, D-10 do futebol americano): só nos momentos decisivos
 * (4ª descida, 2 minutos finais de cada metade, prorrogação), em todas ou nunca. Substitui o `MatchSpeed` do basquete.
 */
export type PlayCallingLevel = "KEY_MOMENTS" | "ALL_PLAYS" | "NONE";
export const PLAY_CALLING_LEVELS: PlayCallingLevel[] = ["KEY_MOMENTS", "ALL_PLAYS", "NONE"];
/** Tempo real de cada quarto na partida ao vivo (`UserSettings.MIN/MAX/DEFAULT_QUARTER_MINUTES` do backend). */
export const QUARTER_MINUTES = { min: 1, max: 15, default: 3 } as const;
export type Difficulty = "EASY" | "NORMAL" | "HARD" | "LEGEND";
export type SimulationSpeed = "INSTANT" | "FAST" | "NORMAL";
/** RF46 · Aparência — mesmos valores dos enums do backend (`ThemeMode`, `FontScale`, `UiDensity`). */
export type ThemeMode = "SYSTEM" | "DARK" | "LIGHT";
export type FontScale = "SMALL" | "MEDIUM" | "LARGE";
export type UiDensity = "COMFORTABLE" | "COMPACT";

/** Resumo do usuário autenticado (sessão). */
export interface UserAccount {
  id: UUID;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  locale: Locale;
  role: UserRole;
  /** papéis brutos do backend (ex.: ["PLAYER"]) */
  roles: string[];
}

/** GET/PUT /me/settings (`SettingsDto`). */
export interface UserSettings {
  /** minutos reais por quarto na partida ao vivo (1–15; o relógio de jogo continua com 15 min regulamentares) */
  quarterDurationMinutes: number;
  playCallingLevel: PlayCallingLevel;
  difficulty: Difficulty;
  backgroundArt: string;
  schemeArt: string;
  soundtrack: string;
  fontFamily: string;
  reducedMotion: boolean;
  notificationsEnabled: boolean;
  /** RF46 · Aparência */
  theme: ThemeMode;
  highContrast: boolean;
  fontScale: FontScale;
  density: UiDensity;
  /** RF46 · Idioma (persistido no perfil; refletido em Accept-Language) */
  preferredLocale: Locale;
  /** tag BCP-47 do formato regional de datas/números/moeda (padrão = idioma) */
  regionalFormat: string;
  /** RF36: velocidade padrão da simulação pelo calendário */
  simulationSpeed: SimulationSpeed;
}

/* ───────────────────────── RF46 · Conta, privacidade e dados (LGPD) ───────────────────────── */
export type UserStatus = "ACTIVE" | "BLOCKED" | "PENDING_DELETION" | "DELETED";
export type ProfileVisibility = "ONLY_ME" | "FRIENDS" | "PUBLIC";
export type ConsentPurpose = "ANALYTICS" | "MARKETING_EMAIL" | "PUBLIC_RANKING" | "AI_ANALYST" | "BIOMETRIC_AVATAR";
export type ConsentSource = "SIGNUP" | "SETTINGS" | "AVATAR_FLOW" | "API" | "SYSTEM";
export type NotificationKind = "MATCH_RESULT" | "TRANSFER" | "BOARD" | "INBOX" | "JOB_OFFER" | "GOAL_COMPLETED" | "STORE" | "SEASON" | "SYSTEM";

export const CONSENT_PURPOSES: ConsentPurpose[] = ["ANALYTICS", "MARKETING_EMAIL", "PUBLIC_RANKING", "AI_ANALYST", "BIOMETRIC_AVATAR"];
export const NOTIFICATION_KINDS: NotificationKind[] = ["MATCH_RESULT", "TRANSFER", "BOARD", "INBOX", "JOB_OFFER", "GOAL_COMPLETED", "STORE", "SEASON", "SYSTEM"];

/** GET/PUT /me (`AccountDto`). */
export interface AccountProfile {
  id: UUID;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  locale: Locale;
  role: UserRole;
  status: UserStatus;
  marketingConsent: boolean;
  dataProcessingConsentAt: ISODate | null;
  lastLoginAt: ISODate | null;
  createdAt: ISODate;
  profileVisibility: ProfileVisibility;
  /** só para idade mínima (Art. 6º, III) — nunca exibida a terceiros */
  birthDate: ISODate | null;
  /** e-mail aguardando verificação de posse (Art. 7º, V) */
  pendingEmail: string | null;
  deletionScheduledFor: ISODate | null;
}

/** PUT /me — correção de dados (Art. 18, III). */
export interface UpdateProfileRequest {
  displayName?: string;
  avatarUrl?: string | null;
  locale?: Locale;
  birthDate?: ISODate | null;
}

export interface ChangeEmailRequest {
  newEmail: string;
  currentPassword: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** GET /me/sessions (`SessionDto`). */
export interface UserSession {
  id: UUID;
  ip: string;
  userAgent: string;
  createdAt: ISODate;
  expiresAt: ISODate;
  current: boolean;
}

/** Item de GET /me/consents (`ConsentState`). Sem registro ⇒ não consentido (privacidade por padrão). */
export interface ConsentState {
  purpose: ConsentPurpose;
  granted: boolean;
  /** artigo da LGPD que fundamenta (etiqueta na UI) */
  legalBasis: string;
  /** versão do texto aceito (null se nunca consentiu) */
  textVersion: string | null;
  currentTextVersion: string;
  /** dado pessoal sensível (Art. 11) */
  sensitive: boolean;
  /** texto mudou desde o aceite ⇒ pedir novo consentimento */
  outdated: boolean;
  updatedAt: ISODate | null;
  source: ConsentSource | null;
}

/** GET/PUT /me/consents. */
export interface PrivacySettings {
  profileVisibility: ProfileVisibility;
  consents: ConsentState[];
  notifications: Record<NotificationKind, boolean>;
  marketingConsent: boolean;
}

export interface UpdatePrivacyRequest {
  consents?: Partial<Record<ConsentPurpose, boolean>>;
  notifications?: Partial<Record<NotificationKind, boolean>>;
  profileVisibility?: ProfileVisibility;
  source?: ConsentSource;
}

/** GET /me/consents/history — prova do consentimento (Art. 8º §2º). */
export interface ConsentHistoryEntry {
  purpose: ConsentPurpose;
  granted: boolean;
  textVersion: string;
  source: ConsentSource;
  recordedAt: ISODate;
}

export type ExportStatus = "REQUESTED" | "READY" | "EXPIRED" | "FAILED";

/** POST /me/export → 202; GET /me/export/{jobId} (`ExportJobDto`). JSON completo + CSV resumido, válidos por 24 h. */
export interface ExportJob {
  id: UUID;
  status: ExportStatus;
  requestedAt: ISODate;
  readyAt: ISODate | null;
  expiresAt: ISODate | null;
  sizeBytes: number;
  jsonUrl: string | null;
  csvUrl: string | null;
}

/** GET /me/activity — auditoria do próprio titular (Art. 9º). */
export interface ActivityEntry {
  action: string;
  resource: string | null;
  result: string | null;
  timestamp: ISODate;
  ipMasked: string | null;
  device: string | null;
}

export interface DeleteAccountRequest {
  currentPassword: string;
  /** palavra de confirmação digitada: EXCLUIR / DELETE / ELIMINAR */
  confirmation: string;
}

/** GET /me/deletion, DELETE /me → 202 (`DeletionStatus`). Prazo de arrependimento: 7 dias. */
export interface DeletionStatus {
  status: UserStatus;
  requestedAt: ISODate | null;
  scheduledFor: ISODate | null;
}

export interface AuthTokens {
  accessToken: string;
  /** Só existe para cliente que não é navegador: no navegador o refresh mora no cookie HttpOnly (D-114). */
  refreshToken?: string | null;
  /** epoch ms */
  accessExpiresAt: number;
}

export interface AuthResponse {
  user: UserAccount;
  tokens: AuthTokens;
}

export interface LoginRequest {
  email: string;
  password: string;
}
/** POST /auth/register (`RegisterRequest`): o servidor responde 201 `{ userId }` e o cliente entra em seguida. */
export interface RegisterRequest extends LoginRequest {
  displayName: string;
  /** tag BCP-47 do idioma da conta */
  locale?: Locale;
  /** consentimento de e-mail de novidades (MARKETING_EMAIL), nunca pré-marcado */
  marketingConsent?: boolean;
}

export interface ForgotPasswordRequest {
  email: string;
}
export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export * from "./career-types";
export * from "./match-types";
