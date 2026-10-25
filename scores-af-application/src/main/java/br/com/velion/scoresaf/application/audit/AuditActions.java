package br.com.velion.scoresaf.application.audit;

/** Ações auditadas (RNF7). Plataforma comum (bloco 3): conta, sessão, consentimento, dados do titular e configurações; bloco 4: editor de mundos. */
public final class AuditActions {
  private AuditActions() {}
  public static final String REGISTER = "REGISTER";
  public static final String LOGIN_OK = "LOGIN_OK";
  public static final String LOGIN_FAILED = "LOGIN_FAILED";
  public static final String LOGIN_LOCKED = "LOGIN_LOCKED";
  public static final String ADMIN_BOOTSTRAPPED = "ADMIN_BOOTSTRAPPED";
  public static final String ADMIN_PROMOTED = "ADMIN_PROMOTED";
  public static final String LOGOUT = "LOGOUT";
  public static final String TOKEN_REFRESHED = "TOKEN_REFRESHED";
  public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
  public static final String ACCESS_DENIED = "ACCESS_DENIED";
  public static final String SENSITIVE_DATA_CHANGED = "SENSITIVE_DATA_CHANGED";
  public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
  public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
  public static final String CONSENT_GRANTED = "CONSENT_GRANTED";
  public static final String CONSENT_REVOKED = "CONSENT_REVOKED";
  public static final String ACCOUNT_EXPORTED = "ACCOUNT_EXPORTED";
  public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
  public static final String SESSION_REVOKED = "SESSION_REVOKED";
  public static final String RESULT_OK = "OK";
  public static final String RESULT_DENIED = "DENIED";
  public static final String RESULT_FAILED = "FAILED";
  // RF46 — configurações/privacidade
  public static final String SETTINGS_UPDATED = "SETTINGS_UPDATED";
  public static final String PROFILE_VISIBILITY_CHANGED = "PROFILE_VISIBILITY_CHANGED";
  public static final String EMAIL_CHANGE_REQUESTED = "EMAIL_CHANGE_REQUESTED";
  public static final String EMAIL_CHANGED = "EMAIL_CHANGED";
  public static final String DATA_EXPORT_REQUESTED = "DATA_EXPORT_REQUESTED";
  public static final String DATA_EXPORT_DOWNLOADED = "DATA_EXPORT_DOWNLOADED";
  public static final String ACCOUNT_DELETION_REQUESTED = "ACCOUNT_DELETION_REQUESTED";
  public static final String ACCOUNT_DELETION_CANCELLED = "ACCOUNT_DELETION_CANCELLED";
  // RF5 — editor de mundos (bloco 4). O recurso (World, League, Club, Stadium, Player) vai no campo resource.
  public static final String WORLD_CLONED = "WORLD_CLONED";
  public static final String WORLD_UPDATED = "WORLD_UPDATED";
  public static final String WORLD_DELETED = "WORLD_DELETED";
  public static final String CATALOG_CREATED = "CATALOG_CREATED";
  public static final String CATALOG_UPDATED = "CATALOG_UPDATED";
  public static final String CATALOG_DELETED = "CATALOG_DELETED";
  public static final String LEAGUE_STRUCTURE_UPDATED = "LEAGUE_STRUCTURE_UPDATED";
  /** ADMIN leu o mundo de outro usuário (suporte, D-21). */
  public static final String ADMIN_WORLD_READ = "ADMIN_WORLD_READ";
  // As demais ações do jogo (partida, mercado, carreira, loja, Meu jogador) entram com os blocos que as usam (docs/01 §5).
}
