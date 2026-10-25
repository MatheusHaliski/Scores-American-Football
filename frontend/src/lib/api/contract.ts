import type {
  AccountProfile,
  ActivityEntry,
  AuthResponse,
  ChangeEmailRequest,
  ChangePasswordRequest,
  ConsentHistoryEntry,
  DeleteAccountRequest,
  DeletionStatus,
  ExportJob,
  ForgotPasswordRequest,
  LoginRequest,
  PageResponse,
  PrivacySettings,
  RegisterRequest,
  ResetPasswordRequest,
  UpdatePrivacyRequest,
  UpdateProfileRequest,
  UserAccount,
  UserSession,
  UserSettings,
  UUID,
} from "./types";

/**
 * Contrato do cliente de API da plataforma comum (conta, configurações e privacidade). Implementado por `live.ts`
 * (backend Java) e por `mock/index.ts` (dados no navegador). Todo consumidor de dados do frontend passa por aqui —
 * nunca por `fetch` direto. As áreas do jogo (franquias, elenco, partida) entram nos blocos seguintes.
 */
export interface ScoresApi {
  /** RF1/RF2/RF10 — sessão e configurações. */
  auth: {
    login(req: LoginRequest): Promise<AuthResponse>;
    /** cadastra e já entra (o servidor responde 201 `{ userId }`; a sessão sai do login em seguida) */
    register(req: RegisterRequest): Promise<AuthResponse>;
    logout(): Promise<void>;
    me(): Promise<UserAccount>;
    /** 202 uniforme, exista ou não a conta (HU-RF3 CA29) */
    forgotPassword(req: ForgotPasswordRequest): Promise<void>;
    /** 204; link inválido ou expirado → 401 (HU-RF3 CA30) */
    resetPassword(req: ResetPasswordRequest): Promise<void>;
    /** RF46 CA02 — confirma o novo e-mail pelo link recebido (público: o token é a prova). 204; link inválido → 422 INVALID_TOKEN */
    verifyEmail(token: string): Promise<void>;
    settings(): Promise<UserSettings>;
    updateSettings(patch: Partial<UserSettings>): Promise<UserSettings>;
  };
  /** RF3/RF46 — conta, privacidade e direitos do titular (LGPD). */
  account: {
    profile(): Promise<AccountProfile>;
    updateProfile(req: UpdateProfileRequest): Promise<AccountProfile>;
    /** 202: link de verificação enviado ao novo endereço (Art. 7º, V) */
    changeEmail(req: ChangeEmailRequest): Promise<void>;
    changePassword(req: ChangePasswordRequest): Promise<void>;
    sessions(): Promise<UserSession[]>;
    revokeSession(id: UUID): Promise<void>;
    privacy(): Promise<PrivacySettings>;
    updatePrivacy(req: UpdatePrivacyRequest): Promise<PrivacySettings>;
    consentHistory(): Promise<ConsentHistoryEntry[]>;
    requestExport(): Promise<ExportJob>;
    exportJobs(): Promise<ExportJob[]>;
    exportJob(id: UUID): Promise<ExportJob>;
    /** download autenticado (json | csv) */
    downloadExport(id: UUID, format: "json" | "csv"): Promise<Blob>;
    activity(page?: number, size?: number): Promise<PageResponse<ActivityEntry>>;
    deleteAccount(req: DeleteAccountRequest): Promise<DeletionStatus>;
    deletionStatus(): Promise<DeletionStatus>;
    cancelDeletion(): Promise<DeletionStatus>;
  };
}
