-- Scores: American Football (VELION TECH CO.) — V3: carreira e elenco (bloco 6a). MySQL 8, utf8mb4, InnoDB.
-- Carreira salva (RF11/RF34) com mundo próprio (kind SAVE, RNF1), temporada e calendário (RF21: semanas e jogos), movimentos do
-- elenco (RF4: 53, practice squad, IR e dispensas com dead money) e depth chart salvo (RF60). Lesão do jogador (semanas fora), que o
-- elenco lê para a IR. Convenções da V1/V2: PK/FK UUID em BINARY(16); enums em VARCHAR(32); booleanos em BIT(1); toda FK com ON
-- DELETE explícito (política em ForeignKeyPolicy): o que é da carreira cai com ela (CASCADE) e com o mundo SAVE dela; perder liga,
-- franquia ou temporada atual só anula a referência do save (SET NULL).

-- ===== Lesão do jogador (semanas que faltam; 0 = sem lesão). O relatório de lesões e o motor que lesiona são do RF64. =====
ALTER TABLE player ADD COLUMN injury_weeks_out INT NOT NULL DEFAULT 0;
ALTER TABLE player ADD CONSTRAINT ck_player_injury_weeks CHECK (injury_weeks_out >= 0);

-- ===== Carreira salva (RF11, RF34). slot 1–5 enquanto ACTIVE; nulo quando ARCHIVED (único por usuário). =====
CREATE TABLE career_save (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  current_week INT NOT NULL,
  difficulty VARCHAR(32) NOT NULL,
  employment_status VARCHAR(32) NOT NULL,
  last_played_at DATETIME(6),
  name VARCHAR(40) NOT NULL,
  seed BIGINT NOT NULL,
  slot INT,
  source_world_name VARCHAR(120),
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL,
  club_id BINARY(16),
  league_id BINARY(16),
  manager_id BINARY(16) NOT NULL,
  season_id BINARY(16),
  user_id BINARY(16) NOT NULL,
  world_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_career_save_user ON career_save (user_id);
CREATE INDEX ix_career_save_world ON career_save (world_id);
ALTER TABLE career_save ADD CONSTRAINT ux_career_save_user_slot UNIQUE (user_id, slot);
ALTER TABLE career_save ADD CONSTRAINT ck_career_save_status CHECK (status IN ('ACTIVE', 'ARCHIVED'));
ALTER TABLE career_save ADD CONSTRAINT ck_career_save_slot CHECK (slot IS NULL OR slot BETWEEN 1 AND 5);
ALTER TABLE career_save ADD CONSTRAINT ck_career_save_difficulty CHECK (difficulty IN ('EASY', 'NORMAL', 'HARD', 'LEGEND'));
ALTER TABLE career_save ADD CONSTRAINT ck_career_save_employment CHECK (employment_status IN ('EMPLOYED', 'UNEMPLOYED'));

-- ===== Temporada da carreira (RF16, RF35): fase, semana atual e formato efetivo da temporada regular. =====
CREATE TABLE season (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  current_week INT NOT NULL,
  games_per_team INT NOT NULL,
  number INT NOT NULL,
  phase VARCHAR(32) NOT NULL,
  regular_season_weeks INT NOT NULL,
  schedule_seed BIGINT NOT NULL,
  season_year INT NOT NULL,
  version BIGINT NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  league_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_season_league ON season (league_id);
ALTER TABLE season ADD CONSTRAINT ux_season_career_number UNIQUE (career_save_id, number);
ALTER TABLE season ADD CONSTRAINT ck_season_phase CHECK (phase IN ('PRESEASON', 'REGULAR', 'PLAYOFFS', 'DRAFT', 'FREE_AGENCY', 'OFFSEASON'));

-- ===== Semanas do calendário (RF21). =====
CREATE TABLE season_week (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  number INT NOT NULL,
  starts_on DATE NOT NULL,
  status VARCHAR(32) NOT NULL,
  season_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE season_week ADD CONSTRAINT ux_season_week_number UNIQUE (season_id, number);
ALTER TABLE season_week ADD CONSTRAINT ck_season_week_kind CHECK (kind IN ('PRESEASON', 'REGULAR', 'WILD_CARD', 'DIVISIONAL', 'CONFERENCE', 'FINAL'));
ALTER TABLE season_week ADD CONSTRAINT ck_season_week_status CHECK (status IN ('UPCOMING', 'CURRENT', 'COMPLETED'));

-- ===== Jogos do calendário (RF16, RF21, RF27): horário, mando, divisão/conferência e placar (6b). =====
CREATE TABLE fixture (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  away_score INT,
  conference_game BIT(1) NOT NULL,
  divisional BIT(1) NOT NULL,
  home_score INT,
  kickoff_date DATE NOT NULL,
  kickoff_slot VARCHAR(32) NOT NULL,
  neutral_site BIT(1) NOT NULL,
  overtime BIT(1) NOT NULL,
  seed BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL,
  week_number INT NOT NULL,
  away_club_id BINARY(16) NOT NULL,
  home_club_id BINARY(16) NOT NULL,
  season_id BINARY(16) NOT NULL,
  week_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_fixture_season_week ON fixture (season_id, week_number);
CREATE INDEX ix_fixture_week ON fixture (week_id);
CREATE INDEX ix_fixture_home ON fixture (home_club_id);
CREATE INDEX ix_fixture_away ON fixture (away_club_id);
ALTER TABLE fixture ADD CONSTRAINT ck_fixture_slot CHECK (kickoff_slot IN ('THU_NIGHT', 'SUN_EARLY', 'SUN_LATE', 'SUN_NIGHT', 'MON_NIGHT'));
ALTER TABLE fixture ADD CONSTRAINT ck_fixture_status CHECK (status IN ('SCHEDULED', 'LIVE', 'FINISHED'));
ALTER TABLE fixture ADD CONSTRAINT ck_fixture_clubs CHECK (home_club_id <> away_club_id);

-- ===== Movimentos do elenco (RF4): troca de situação ou dispensa (waivers) com dead money da temporada. =====
CREATE TABLE roster_move (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  cap_hit_removed BIGINT NOT NULL,
  dead_money BIGINT NOT NULL,
  from_status VARCHAR(32) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  to_status VARCHAR(32) NOT NULL,
  week INT NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  club_id BINARY(16),
  player_id BINARY(16) NOT NULL,
  season_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_roster_move_career_player ON roster_move (career_save_id, player_id);
CREATE INDEX ix_roster_move_season ON roster_move (season_id);
ALTER TABLE roster_move ADD CONSTRAINT ck_roster_move_kind CHECK (kind IN ('STATUS_CHANGE', 'RELEASE'));
ALTER TABLE roster_move ADD CONSTRAINT ck_roster_move_dead_money CHECK (dead_money >= 0);

-- ===== Depth chart salvo da franquia do usuário (RF60) e as vagas (posição ou papel, profundidade, jogador). =====
CREATE TABLE depth_chart (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  revision INT NOT NULL,
  validated BIT(1) NOT NULL,
  version BIGINT NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  club_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_depth_chart_club ON depth_chart (club_id);
ALTER TABLE depth_chart ADD CONSTRAINT ux_depth_chart_career_club UNIQUE (career_save_id, club_id);

CREATE TABLE depth_chart_slot (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  depth INT NOT NULL,
  slot VARCHAR(32) NOT NULL,
  depth_chart_id BINARY(16) NOT NULL,
  player_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_depth_chart_slot_player ON depth_chart_slot (player_id);
ALTER TABLE depth_chart_slot ADD CONSTRAINT ux_depth_chart_slot_depth UNIQUE (depth_chart_id, slot, depth);
ALTER TABLE depth_chart_slot ADD CONSTRAINT ux_depth_chart_slot_player UNIQUE (depth_chart_id, slot, player_id);
ALTER TABLE depth_chart_slot ADD CONSTRAINT ck_depth_chart_slot_depth CHECK (depth BETWEEN 1 AND 10);
ALTER TABLE depth_chart_slot ADD CONSTRAINT ck_depth_chart_slot_slot CHECK (slot IN ('QB','RB','FB','WR','TE','LT','LG','C','RG','RT','DE','DT','OLB','MLB','CB','FS','SS','K','P','LS','KR','PR','H','THIRD_DOWN_BACK'));

-- ===== Chaves estrangeiras (depois das tabelas: career_save ↔ season) =====
ALTER TABLE career_save ADD CONSTRAINT fk_career_save_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE;
ALTER TABLE career_save ADD CONSTRAINT fk_career_save_world FOREIGN KEY (world_id) REFERENCES world (id) ON DELETE CASCADE;
ALTER TABLE career_save ADD CONSTRAINT fk_career_save_manager FOREIGN KEY (manager_id) REFERENCES manager_profile (id) ON DELETE CASCADE;
ALTER TABLE career_save ADD CONSTRAINT fk_career_save_league FOREIGN KEY (league_id) REFERENCES league (id) ON DELETE SET NULL;
ALTER TABLE career_save ADD CONSTRAINT fk_career_save_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE SET NULL;
ALTER TABLE career_save ADD CONSTRAINT fk_career_save_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE SET NULL;
ALTER TABLE season ADD CONSTRAINT fk_season_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE season ADD CONSTRAINT fk_season_league FOREIGN KEY (league_id) REFERENCES league (id) ON DELETE CASCADE;
ALTER TABLE season_week ADD CONSTRAINT fk_season_week_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE CASCADE;
ALTER TABLE fixture ADD CONSTRAINT fk_fixture_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE CASCADE;
ALTER TABLE fixture ADD CONSTRAINT fk_fixture_week FOREIGN KEY (week_id) REFERENCES season_week (id) ON DELETE CASCADE;
ALTER TABLE fixture ADD CONSTRAINT fk_fixture_home FOREIGN KEY (home_club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE fixture ADD CONSTRAINT fk_fixture_away FOREIGN KEY (away_club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE roster_move ADD CONSTRAINT fk_roster_move_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE roster_move ADD CONSTRAINT fk_roster_move_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE CASCADE;
ALTER TABLE roster_move ADD CONSTRAINT fk_roster_move_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE;
ALTER TABLE roster_move ADD CONSTRAINT fk_roster_move_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE SET NULL;
ALTER TABLE depth_chart ADD CONSTRAINT fk_depth_chart_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE depth_chart ADD CONSTRAINT fk_depth_chart_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE depth_chart_slot ADD CONSTRAINT fk_depth_chart_slot_chart FOREIGN KEY (depth_chart_id) REFERENCES depth_chart (id) ON DELETE CASCADE;
ALTER TABLE depth_chart_slot ADD CONSTRAINT fk_depth_chart_slot_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE;
