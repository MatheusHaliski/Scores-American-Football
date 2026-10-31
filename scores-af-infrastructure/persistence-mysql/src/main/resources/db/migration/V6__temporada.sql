-- Scores: American Football (VELION TECH CO.) — V6: temporada (bloco 6c). MySQL 8, utf8mb4, InnoDB.
-- Campeã, vice e fim da temporada (RF35), sementes dos playoffs (RF66), prêmios individuais (RF35), relatórios do analista do sistema
-- (D-104) e o movimento CONTRACT_EXPIRED do avanço da temporada (D-100). Convenções da V1–V5: PK/FK UUID em BINARY(16); enums em
-- VARCHAR(32); booleanos em BIT(1); toda FK com ON DELETE explícito (política em ForeignKeyPolicy). Nunca editar V1–V5.

-- ===== Temporada: campeã, vice e o instante em que a final terminou. Perder a franquia só anula a referência. =====
ALTER TABLE season ADD COLUMN completed_at DATETIME(6);
ALTER TABLE season ADD COLUMN champion_club_id BINARY(16);
ALTER TABLE season ADD COLUMN runner_up_club_id BINARY(16);
CREATE INDEX ix_season_champion ON season (champion_club_id);
CREATE INDEX ix_season_runner_up ON season (runner_up_club_id);

-- ===== Sementes dos playoffs (RF66), congeladas no fim da temporada regular. =====
CREATE TABLE playoff_seed (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  division_champion BIT(1) NOT NULL,
  seed INT NOT NULL,
  tiebreak VARCHAR(32),
  club_id BINARY(16) NOT NULL,
  conference_id BINARY(16) NOT NULL,
  season_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_playoff_seed_club ON playoff_seed (club_id);
CREATE INDEX ix_playoff_seed_conference ON playoff_seed (conference_id);
ALTER TABLE playoff_seed ADD CONSTRAINT ux_playoff_seed_club UNIQUE (season_id, club_id);
ALTER TABLE playoff_seed ADD CONSTRAINT ux_playoff_seed_number UNIQUE (season_id, conference_id, seed);
ALTER TABLE playoff_seed ADD CONSTRAINT ck_playoff_seed_seed CHECK (seed BETWEEN 1 AND 16);
ALTER TABLE playoff_seed ADD CONSTRAINT ck_playoff_seed_tiebreak CHECK (tiebreak IS NULL OR tiebreak IN ('HEAD_TO_HEAD', 'DIVISION', 'COMMON_GAMES',
  'CONFERENCE', 'STRENGTH_OF_VICTORY', 'STRENGTH_OF_SCHEDULE', 'POINT_DIFFERENTIAL', 'PROJECTION', 'DRAW'));

-- ===== Prêmios individuais da temporada (RF35): um por tipo e temporada. =====
CREATE TABLE season_award (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  award_type VARCHAR(32) NOT NULL,
  position VARCHAR(32),
  score DOUBLE NOT NULL,
  stat_line VARCHAR(512) NOT NULL,
  winner_name VARCHAR(120) NOT NULL,
  club_id BINARY(16) NOT NULL,
  player_id BINARY(16),
  season_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_season_award_player ON season_award (player_id);
CREATE INDEX ix_season_award_club ON season_award (club_id);
ALTER TABLE season_award ADD CONSTRAINT ux_season_award_type UNIQUE (season_id, award_type);
ALTER TABLE season_award ADD CONSTRAINT ck_season_award_type CHECK (award_type IN ('MVP', 'OPOY', 'DPOY', 'OROY', 'DROY', 'COACH'));

-- ===== Relatórios do analista do sistema (por regras, sem IA externa): o mais recente é o do GET /analysis. =====
CREATE TABLE analysis_report (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  report_json MEDIUMTEXT NOT NULL,
  source VARCHAR(16) NOT NULL,
  week INT NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  season_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_analysis_report_career ON analysis_report (career_save_id, created_at);
CREATE INDEX ix_analysis_report_season ON analysis_report (season_id);
ALTER TABLE analysis_report ADD CONSTRAINT ck_analysis_report_source CHECK (source IN ('RULES'));

-- ===== Fim de contrato no avanço da temporada (D-100): o movimento é a pendência de renovação do bloco 7. =====
ALTER TABLE roster_move DROP CONSTRAINT ck_roster_move_kind;
ALTER TABLE roster_move ADD CONSTRAINT ck_roster_move_kind CHECK (kind IN ('STATUS_CHANGE', 'RELEASE', 'WAIVER_CLEARED', 'CONTRACT_EXPIRED'));

-- ===== Chaves estrangeiras. A temporada perde só a referência à campeã e à vice (SET NULL); sementes, prêmios e relatórios são da
-- carreira e caem com ela, com a temporada, a franquia, a conferência ou o jogador do mundo SAVE dela (CASCADE). =====
ALTER TABLE season ADD CONSTRAINT fk_season_champion FOREIGN KEY (champion_club_id) REFERENCES club (id) ON DELETE SET NULL;
ALTER TABLE season ADD CONSTRAINT fk_season_runner_up FOREIGN KEY (runner_up_club_id) REFERENCES club (id) ON DELETE SET NULL;
ALTER TABLE playoff_seed ADD CONSTRAINT fk_playoff_seed_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE CASCADE;
ALTER TABLE playoff_seed ADD CONSTRAINT fk_playoff_seed_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE playoff_seed ADD CONSTRAINT fk_playoff_seed_conference FOREIGN KEY (conference_id) REFERENCES conference (id) ON DELETE CASCADE;
ALTER TABLE season_award ADD CONSTRAINT fk_season_award_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE CASCADE;
ALTER TABLE season_award ADD CONSTRAINT fk_season_award_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE season_award ADD CONSTRAINT fk_season_award_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE;
ALTER TABLE analysis_report ADD CONSTRAINT fk_analysis_report_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE analysis_report ADD CONSTRAINT fk_analysis_report_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE CASCADE;
