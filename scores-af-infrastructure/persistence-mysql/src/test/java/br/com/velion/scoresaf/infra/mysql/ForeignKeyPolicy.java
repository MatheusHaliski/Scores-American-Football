package br.com.velion.scoresaf.infra.mysql;

import java.util.List;

/**
 * Política de ON DELETE esperada para cada FK (D-23 do basquete). FK nova nasce com ON DELETE na própria migration e entra aqui
 * no mesmo PR, senão o ForeignKeyPolicyTest fica vermelho. Bloco 3: só as FKs da plataforma (V1__plataforma.sql); a conta é
 * anonimizada e nunca apagada, mas o que é dela cai junto se um dia for (sessões, configurações, preferências, notificações,
 * pedidos de exportação); a prova do consentimento (consent_record) segura a exclusão.
 */
final class ForeignKeyPolicy {
  record Fk(String table, String column, String refTable, String refColumn, String onDelete) {}

  static final List<Fk> POLICY = List.of(
      new Fk("consent_record", "user_id", "user_account", "id", "RESTRICT"),
      new Fk("data_export_job", "user_id", "user_account", "id", "CASCADE"),
      new Fk("notification", "user_id", "user_account", "id", "CASCADE"),
      new Fk("notification_preference", "user_id", "user_account", "id", "CASCADE"),
      new Fk("user_session", "user_id", "user_account", "id", "CASCADE"),
      new Fk("user_settings", "user_id", "user_account", "id", "CASCADE"));

  private ForeignKeyPolicy() {}
}
