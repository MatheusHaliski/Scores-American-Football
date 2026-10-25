-- Scores: American Football (VELION TECH CO.) — V1: plataforma comum (bloco 3). MySQL 8, utf8mb4, InnoDB.
-- Só as tabelas da conta: usuário (com origem da conta), sessão de refresh com família, configurações, preferências de
-- notificação, notificações, consentimentos, exportação de dados e auditoria. Extraído do Scores de basquete (V1 baseline +
-- V3 privacidade + V7 política de FKs e origem da conta + V8 família de sessão), já no estado final, sem o que é do jogo
-- (D-8). O jogo ganha migrations próprias a partir do bloco 4 (V2__dominio_af.sql, V3__mundo_semente.sql).
-- Convenções: PK/FK UUID em BINARY(16); enums em VARCHAR(32); booleanos em BIT(1); toda FK com ON DELETE explícito (política
-- conferida por ForeignKeyPolicyTest). spring.jpa.hibernate.ddl-auto=validate (perfil mysql) confere este schema contra as
-- entidades; FlywayBaselineValidationTest faz o mesmo em H2 modo MySQL no mvn verify.

-- ===== Conta (RF1–RF3, RF46) =====
CREATE TABLE user_account (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  account_origin VARCHAR(16) NOT NULL DEFAULT 'REAL',
  fixture_key VARCHAR(80) NULL,
  avatar_url VARCHAR(512),
  birth_date DATE,
  data_processing_consent_at DATETIME(6),
  deletion_requested_at DATETIME(6),
  display_name VARCHAR(512) NOT NULL,
  email VARCHAR(512) NOT NULL,
  email_hash VARCHAR(64) NOT NULL,
  export_requested_at DATETIME(6),
  failed_login_attempts INT NOT NULL,
  last_login_at DATETIME(6),
  locale VARCHAR(32) NOT NULL,
  locked_until DATETIME(6),
  marketing_consent BIT(1) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  pending_email VARCHAR(512),
  profile_visibility VARCHAR(32) NOT NULL DEFAULT 'ONLY_ME',
  role VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  -- conta de teste ou demo fica fora de rankings compartilhados e métricas (D-23 do basquete); derivada, nunca gravada
  test_account BOOLEAN GENERATED ALWAYS AS (account_origin IN ('TEST_SEED', 'DEMO')),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE user_account ADD CONSTRAINT ux_user_account_email_hash UNIQUE (email_hash);
ALTER TABLE user_account ADD CONSTRAINT uk_user_account_fixture_key UNIQUE (fixture_key);
ALTER TABLE user_account ADD CONSTRAINT ck_user_account_origin CHECK (account_origin IN ('REAL', 'TEST_SEED', 'DEMO', 'SYSTEM'));
CREATE INDEX ix_user_account_status ON user_account (status);
CREATE INDEX ix_user_account_test_account ON user_account (test_account);

-- ===== Sessão de refresh rotativo com família (RF2, RNF4) =====
CREATE TABLE user_session (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  -- família = id da sessão do login, mantido em cada rotação; o access token leva a família (sid)
  family_id BINARY(16) NULL,
  ip VARCHAR(64),
  refresh_token_hash VARCHAR(64) NOT NULL,
  replaced_by BINARY(16),
  revoked_at DATETIME(6),
  user_agent VARCHAR(255),
  user_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE user_session ADD CONSTRAINT ux_user_session_token_hash UNIQUE (refresh_token_hash);
CREATE INDEX ix_user_session_expires ON user_session (expires_at);
CREATE INDEX ix_user_session_user ON user_session (user_id);
CREATE INDEX ix_user_session_family ON user_session (family_id);
ALTER TABLE user_session ADD CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE;

-- ===== Configurações (RF10, RF46 aparência e idioma, RF36) =====
CREATE TABLE user_settings (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  background_art VARCHAR(64),
  density VARCHAR(32) NOT NULL DEFAULT 'COMFORTABLE',
  difficulty VARCHAR(32) NOT NULL,
  font_family VARCHAR(64),
  font_scale VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  high_contrast BIT(1) NOT NULL DEFAULT 0,
  notifications_enabled BIT(1) NOT NULL,
  -- futebol americano (D-10): no lugar do match_speed do basquete
  play_calling_level VARCHAR(32) NOT NULL DEFAULT 'KEY_MOMENTS',
  quarter_duration_minutes INT NOT NULL DEFAULT 3,
  reduced_motion BIT(1) NOT NULL,
  regional_format VARCHAR(16),
  scheme_art VARCHAR(64),
  simulation_speed VARCHAR(32) NOT NULL DEFAULT 'FAST',
  soundtrack VARCHAR(64),
  theme VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
  user_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE user_settings ADD CONSTRAINT ux_user_settings_user UNIQUE (user_id);
ALTER TABLE user_settings ADD CONSTRAINT ck_user_settings_quarter CHECK (quarter_duration_minutes BETWEEN 1 AND 15);
ALTER TABLE user_settings ADD CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE;

-- ===== Notificações e preferências (RF10, RF13/RF24) =====
CREATE TABLE notification_preference (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  enabled BIT(1) NOT NULL,
  type VARCHAR(32) NOT NULL,
  user_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE notification_preference ADD CONSTRAINT ux_notification_pref_user_type UNIQUE (user_id, type);
ALTER TABLE notification_preference ADD CONSTRAINT fk_notification_preference_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE;

-- o vínculo com a carreira (save_id) entra com a migration da carreira (bloco 6)
CREATE TABLE notification (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  body TEXT,
  kind VARCHAR(32) NOT NULL,
  is_read BIT(1) NOT NULL,
  title VARCHAR(200) NOT NULL,
  user_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_notification_user_read ON notification (user_id, is_read, created_at);
ALTER TABLE notification ADD CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE;

-- ===== LGPD (RF46, RNF8) =====
-- registro imutável de consentimentos (prova do consentimento, Art. 8º §2º); RESTRICT: a prova sobrevive à conta
CREATE TABLE consent_record (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  granted BIT(1) NOT NULL,
  ip_hash VARCHAR(64),
  purpose VARCHAR(32) NOT NULL,
  recorded_at DATETIME(6) NOT NULL,
  source VARCHAR(32) NOT NULL,
  text_version VARCHAR(32) NOT NULL,
  user_agent VARCHAR(120),
  user_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_consent_record_user_purpose ON consent_record (user_id, purpose, recorded_at);
ALTER TABLE consent_record ADD CONSTRAINT fk_consent_record_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE RESTRICT;

-- pedidos de exportação (arquivos privados por 24 h)
CREATE TABLE data_export_job (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  csv_key VARCHAR(255),
  expires_at DATETIME(6),
  failure_reason VARCHAR(255),
  json_key VARCHAR(255),
  ready_at DATETIME(6),
  requested_at DATETIME(6) NOT NULL,
  size_bytes BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  user_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_data_export_job_user ON data_export_job (user_id, requested_at);
ALTER TABLE data_export_job ADD CONSTRAINT fk_data_export_job_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE;

-- ===== Auditoria (RNF7): sem FK, sobrevive à exclusão do recurso =====
CREATE TABLE audit_log (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  action VARCHAR(64) NOT NULL,
  actor VARCHAR(64) NOT NULL,
  correlation_id VARCHAR(64),
  details TEXT,
  ip VARCHAR(64),
  resource VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64),
  result VARCHAR(16) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  user_agent VARCHAR(255),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_audit_log_actor ON audit_log (actor);
CREATE INDEX ix_audit_log_correlation ON audit_log (correlation_id);
CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX ix_audit_log_resource ON audit_log (resource, resource_id);
