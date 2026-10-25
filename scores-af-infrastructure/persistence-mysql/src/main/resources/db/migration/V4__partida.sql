-- Scores: American Football (VELION TECH CO.) — V4: partida (bloco 6b). MySQL 8, utf8mb4, InnoDB.
-- Plano de jogo e playbook salvos da franquia do usuário (RF61), partida ao vivo com o snapshot do motor (RF27/RF29/RF62),
-- relatório pós-jogo e box score por jogador de todo jogo do calendário (RF27/RF31). Convenções da V1–V3: PK/FK UUID em BINARY(16);
-- enums em VARCHAR(32); booleanos em BIT(1); toda FK com ON DELETE explícito (política em ForeignKeyPolicy): tudo aqui é da carreira
-- e cai com ela (CASCADE), com o jogo do calendário, a franquia ou o jogador do mundo SAVE dela.

-- ===== Plano de jogo salvo (RF61): as tendências que o motor entende (GamePlan). =====
CREATE TABLE game_plan (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  base_defense VARCHAR(32) NOT NULL,
  blitz_frequency VARCHAR(32) NOT NULL,
  defensive_focus VARCHAR(32) NOT NULL,
  fourth_down_aggression VARCHAR(32) NOT NULL,
  pass_ratio INT NOT NULL,
  revision INT NOT NULL,
  tempo VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  club_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_game_plan_club ON game_plan (club_id);
ALTER TABLE game_plan ADD CONSTRAINT ux_game_plan_career_club UNIQUE (career_save_id, club_id);
ALTER TABLE game_plan ADD CONSTRAINT ck_game_plan_pass_ratio CHECK (pass_ratio BETWEEN 20 AND 80);
ALTER TABLE game_plan ADD CONSTRAINT ck_game_plan_aggression CHECK (fourth_down_aggression IN ('CONSERVATIVE', 'NORMAL', 'AGGRESSIVE'));
ALTER TABLE game_plan ADD CONSTRAINT ck_game_plan_tempo CHECK (tempo IN ('HURRY_UP', 'NORMAL', 'MILK_CLOCK'));
ALTER TABLE game_plan ADD CONSTRAINT ck_game_plan_focus CHECK (defensive_focus IN ('STOP_RUN', 'BALANCED', 'STOP_PASS'));
ALTER TABLE game_plan ADD CONSTRAINT ck_game_plan_blitz CHECK (blitz_frequency IN ('LOW', 'NORMAL', 'HIGH'));
ALTER TABLE game_plan ADD CONSTRAINT ck_game_plan_base_defense CHECK (base_defense IN ('BASE_4_3', 'BASE_3_4'));

-- ===== Playbook salvo (RF61) e os pesos: conceito por situação e cobertura, 0–200 (100 = padrão da liga). =====
CREATE TABLE playbook (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  revision INT NOT NULL,
  version BIGINT NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  club_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_playbook_club ON playbook (club_id);
ALTER TABLE playbook ADD CONSTRAINT ux_playbook_career_club UNIQUE (career_save_id, club_id);

CREATE TABLE playbook_weight (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  item VARCHAR(32) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  weight INT NOT NULL,
  playbook_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE playbook_weight ADD CONSTRAINT ux_playbook_weight_item UNIQUE (playbook_id, scope, item);
ALTER TABLE playbook_weight ADD CONSTRAINT ck_playbook_weight_range CHECK (weight BETWEEN 0 AND 200);
ALTER TABLE playbook_weight ADD CONSTRAINT ck_playbook_weight_scope CHECK (scope IN ('EARLY_DOWN', 'THIRD_DOWN', 'THIRD_AND_LONG', 'RED_ZONE', 'TWO_MINUTE', 'COVERAGE'));

-- ===== Partida ao vivo do usuário (RF27/RF29/RF62): snapshot do motor e colunas de consulta. Uma por jogo. =====
CREATE TABLE match_session (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  away_score INT NOT NULL,
  clock_seconds INT NOT NULL,
  decision_deadline_at DATETIME(6),
  decision_id VARCHAR(64),
  engine_version INT NOT NULL,
  finished_at DATETIME(6),
  home_score INT NOT NULL,
  play_calling_level VARCHAR(32) NOT NULL,
  possession_club_id BINARY(16),
  quarter INT NOT NULL,
  rng_cursor BIGINT NOT NULL,
  seed BIGINT NOT NULL,
  snapshot_json MEDIUMTEXT NOT NULL,
  snapshot_version BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  fixture_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_match_session_career_status ON match_session (career_save_id, status);
ALTER TABLE match_session ADD CONSTRAINT ux_match_session_fixture UNIQUE (fixture_id);
ALTER TABLE match_session ADD CONSTRAINT ck_match_session_status CHECK (status IN ('NOT_STARTED', 'LIVE', 'BREAK', 'HALFTIME', 'AWAITING_DECISION', 'FINISHED'));
ALTER TABLE match_session ADD CONSTRAINT ck_match_session_level CHECK (play_calling_level IN ('KEY_MOMENTS', 'ALL_PLAYS', 'NONE'));

-- ===== Relatório pós-jogo (RF27/RF31) de todo jogo do calendário: placar, semente, versões e o box score completo (JSON). =====
CREATE TABLE match_report (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  away_score INT NOT NULL,
  engine_version INT NOT NULL,
  home_score INT NOT NULL,
  overtime BIT(1) NOT NULL,
  play_count INT NOT NULL,
  report_json MEDIUMTEXT NOT NULL,
  rules_version INT NOT NULL,
  seed BIGINT NOT NULL,
  user_game BIT(1) NOT NULL,
  winner_club_id BINARY(16),
  career_save_id BINARY(16) NOT NULL,
  fixture_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_match_report_career ON match_report (career_save_id);
ALTER TABLE match_report ADD CONSTRAINT ux_match_report_fixture UNIQUE (fixture_id);

-- ===== Box score por jogador e jogo (docs/01 §3.6), só o que o motor gera (D-35). =====
CREATE TABLE player_match_stats (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  extra_point_attempts INT NOT NULL,
  extra_points_made INT NOT NULL,
  field_goal_attempts INT NOT NULL,
  field_goals_made INT NOT NULL,
  forced_fumbles INT NOT NULL,
  fumble_recoveries INT NOT NULL,
  fumbles_lost INT NOT NULL,
  interceptions INT NOT NULL,
  interceptions_thrown INT NOT NULL,
  pass_attempts INT NOT NULL,
  pass_completions INT NOT NULL,
  pass_touchdowns INT NOT NULL,
  pass_yards INT NOT NULL,
  passes_defended INT NOT NULL,
  punt_yards INT NOT NULL,
  punts INT NOT NULL,
  receiving_touchdowns INT NOT NULL,
  receiving_yards INT NOT NULL,
  receptions INT NOT NULL,
  rush_attempts INT NOT NULL,
  rush_touchdowns INT NOT NULL,
  rush_yards INT NOT NULL,
  sacked INT NOT NULL,
  sacks INT NOT NULL,
  snaps INT NOT NULL,
  solo_tackles INT NOT NULL,
  tackles_for_loss INT NOT NULL,
  targets INT NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  club_id BINARY(16) NOT NULL,
  fixture_id BINARY(16) NOT NULL,
  player_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_player_match_stats_player ON player_match_stats (player_id);
CREATE INDEX ix_player_match_stats_career ON player_match_stats (career_save_id);
CREATE INDEX ix_player_match_stats_club ON player_match_stats (club_id);
ALTER TABLE player_match_stats ADD CONSTRAINT ux_player_match_stats_fixture_player UNIQUE (fixture_id, player_id);

-- ===== Chaves estrangeiras: tudo cai com a carreira (e com o jogo, a franquia ou o jogador do mundo SAVE). =====
ALTER TABLE game_plan ADD CONSTRAINT fk_game_plan_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE game_plan ADD CONSTRAINT fk_game_plan_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE playbook ADD CONSTRAINT fk_playbook_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE playbook ADD CONSTRAINT fk_playbook_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE playbook_weight ADD CONSTRAINT fk_playbook_weight_playbook FOREIGN KEY (playbook_id) REFERENCES playbook (id) ON DELETE CASCADE;
ALTER TABLE match_session ADD CONSTRAINT fk_match_session_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE match_session ADD CONSTRAINT fk_match_session_fixture FOREIGN KEY (fixture_id) REFERENCES fixture (id) ON DELETE CASCADE;
ALTER TABLE match_report ADD CONSTRAINT fk_match_report_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE match_report ADD CONSTRAINT fk_match_report_fixture FOREIGN KEY (fixture_id) REFERENCES fixture (id) ON DELETE CASCADE;
ALTER TABLE player_match_stats ADD CONSTRAINT fk_player_match_stats_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE player_match_stats ADD CONSTRAINT fk_player_match_stats_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE player_match_stats ADD CONSTRAINT fk_player_match_stats_fixture FOREIGN KEY (fixture_id) REFERENCES fixture (id) ON DELETE CASCADE;
ALTER TABLE player_match_stats ADD CONSTRAINT fk_player_match_stats_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE;
