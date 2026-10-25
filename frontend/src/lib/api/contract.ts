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
import type {
  CareerDashboard,
  CareerList,
  CareerSummary,
  CreateCareerRequest,
  DepthChart,
  LeagueClubs,
  LeagueSummary,
  MovableStatus,
  PlayerDetail,
  ReleaseResult,
  Roster,
  RosterChange,
  SaveDepthChartRequest,
  SeasonCalendar,
  SetRoleRequest,
  UpdateCareerRequest,
  WorldSummary,
} from "./career-types";

/**
 * Contrato do cliente de API da plataforma comum (conta, configurações e privacidade). Implementado por `live.ts`
 * (backend Java) e por `mock/index.ts` (dados no navegador). Todo consumidor de dados do frontend passa por aqui —
 * nunca por `fetch` direto. O bloco 6a acrescenta carreiras, central da franquia, calendário, elenco e depth chart.
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
  /** RF5 (leitura) — mundos, ligas e franquias para a nova carreira. */
  worlds: {
    list(): Promise<WorldSummary[]>;
    leagues(worldId: UUID): Promise<LeagueSummary[]>;
    /** estrutura da liga + franquias, agrupadas por conferência e divisão */
    leagueClubs(worldId: UUID, leagueId: UUID): Promise<LeagueClubs>;
  };
  /** RF11/RF34/RF40/RF21 — carreiras salvas, nova carreira, central da franquia e calendário. */
  careers: {
    list(): Promise<CareerList>;
    create(req: CreateCareerRequest): Promise<CareerSummary>;
    get(id: UUID): Promise<CareerSummary>;
    update(id: UUID, req: UpdateCareerRequest): Promise<CareerSummary>;
    /** exclusão definitiva; `confirm` = nome da carreira, exatamente (422 CAREER_CONFIRMATION_REQUIRED) */
    remove(id: UUID, confirm: string): Promise<void>;
    dashboard(id: UUID): Promise<CareerDashboard>;
    calendar(id: UUID, week?: number): Promise<SeasonCalendar>;
  };
  /** RF4/RF56 — elenco, jogador, movimentos e dispensa. */
  roster: {
    get(careerId: UUID): Promise<Roster>;
    player(careerId: UUID, playerId: UUID): Promise<PlayerDetail>;
    changeStatus(careerId: UUID, playerId: UUID, status: MovableStatus): Promise<RosterChange>;
    release(careerId: UUID, playerId: UUID): Promise<ReleaseResult>;
  };
  /** RF60 — depth chart com versão otimista (409 DEPTH_CHART_VERSION_CONFLICT; 422 com `details.errors`). */
  depthChart: {
    get(careerId: UUID): Promise<DepthChart>;
    save(careerId: UUID, req: SaveDepthChartRequest): Promise<DepthChart>;
    setRole(careerId: UUID, playerId: UUID, req: SetRoleRequest): Promise<DepthChart>;
  };
}
