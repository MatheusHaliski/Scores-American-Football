-- Scores: American Football (VELION TECH CO.) — V2: domínio do futebol americano (bloco 4). MySQL 8, utf8mb4, InnoDB.
-- Catálogo do mundo: mundo, liga (regras rule_* embutidas e versionadas, D-4), conferência, divisão, head coach, estádio e setores,
-- franquia, uniformes, capacete e jogador (49 subatributos attr_*, contrato contract_*). O mundo padrão fictício (Q-4) é semeado por
-- código, como no basquete (DefaultWorldFactory + SeedRunner, D-23): não há V3 de dados.
-- Convenções da V1: PK/FK UUID em BINARY(16); enums em VARCHAR(32); booleanos em BIT(1); toda FK com ON DELETE explícito (política
-- em ForeignKeyPolicy, conferida por ForeignKeyPolicyTest/ForeignKeyPolicyMySqlTest). ddl-auto=validate confere contra as entidades.

-- ===== Mundo = catálogo (RF5, RF34). owner_user_id nulo = mundo padrão (semente por código, D-23). =====
CREATE TABLE world (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  base_year INT NOT NULL,
  description VARCHAR(500),
  kind VARCHAR(32) NOT NULL,
  name VARCHAR(120) NOT NULL,
  seed BIGINT NOT NULL,
  owner_user_id BINARY(16),
  source_world_id BINARY(16),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_world_owner ON world (owner_user_id);
CREATE INDEX ix_world_kind ON world (kind);
ALTER TABLE world ADD CONSTRAINT ck_world_kind CHECK (kind IN ('TEMPLATE', 'SAVE'));

-- ===== Liga com regras embutidas e versionadas (rule_*, D-4) e estrutura de conferências × divisões (8–32 franquias). =====
CREATE TABLE league (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  conference_count INT NOT NULL,
  country VARCHAR(3) NOT NULL,
  divisions_per_conference INT NOT NULL,
  logo_url VARCHAR(512),
  name VARCHAR(120) NOT NULL,
  prestige INT NOT NULL,
  rule_bonus_proration_max_years INT NOT NULL,
  rule_bye_weeks_per_team INT NOT NULL,
  rule_clock_stops_on_first_down BIT(1) NOT NULL,
  rule_dl_rotation_max INT NOT NULL,
  rule_dl_rotation_min INT NOT NULL,
  rule_downs INT NOT NULL,
  rule_draft_rounds INT NOT NULL,
  rule_end_zone_yards INT NOT NULL,
  rule_field_length_yards INT NOT NULL,
  rule_game_day_actives INT NOT NULL,
  rule_games_per_team INT NOT NULL,
  rule_hard_cap BIT(1) NOT NULL,
  rule_hash_from_sideline_feet DOUBLE NOT NULL,
  rule_ir_min_weeks INT NOT NULL,
  rule_ir_returns_per_season INT NOT NULL,
  rule_kickoff_yard_line INT NOT NULL,
  rule_neutral_site_final BIT(1) NOT NULL,
  rule_offseason_roster_max INT NOT NULL,
  rule_one_foot_inbounds BIT(1) NOT NULL,
  rule_overtime_format VARCHAR(32) NOT NULL,
  rule_overtime_minutes INT NOT NULL,
  rule_overtime_start_yard_line INT NOT NULL,
  rule_pat_yard_line INT NOT NULL,
  rule_play_clock_after_stoppage_seconds INT NOT NULL,
  rule_play_clock_seconds INT NOT NULL,
  rule_playoff_bye_seeds INT NOT NULL,
  rule_playoff_seeds_per_conference INT NOT NULL,
  rule_points_extra_point INT NOT NULL,
  rule_points_field_goal INT NOT NULL,
  rule_points_safety INT NOT NULL,
  rule_points_touchdown INT NOT NULL,
  rule_points_two_point INT NOT NULL,
  rule_practice_squad_elevations INT NOT NULL,
  rule_practice_squad_max INT NOT NULL,
  rule_practice_squad_protected_max INT NOT NULL,
  rule_quarter_minutes INT NOT NULL,
  rule_regular_season_ties_allowed BIT(1) NOT NULL,
  rule_regular_season_weeks INT NOT NULL,
  rule_roster_max INT NOT NULL,
  rule_timeouts_per_half INT NOT NULL,
  rule_touchback_yard_line INT NOT NULL,
  rule_two_minute_warning BIT(1) NOT NULL,
  rule_two_point_yard_line INT NOT NULL,
  rule_version INT NOT NULL,
  rule_yards_to_gain INT NOT NULL,
  ruleset VARCHAR(32) NOT NULL,
  salary_cap BIGINT NOT NULL,
  salary_floor BIGINT NOT NULL,
  short_name VARCHAR(8) NOT NULL,
  team_count INT NOT NULL,
  tier INT NOT NULL,
  tv_rights_per_season BIGINT NOT NULL,
  version BIGINT NOT NULL,
  world_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_league_world ON league (world_id);
ALTER TABLE league ADD CONSTRAINT ck_league_ruleset CHECK (ruleset IN ('PRO', 'COLLEGE'));
ALTER TABLE league ADD CONSTRAINT ck_league_team_count CHECK (team_count BETWEEN 0 AND 32);

-- ===== Conferências da liga (1 ou 2). =====
CREATE TABLE conference (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  logo_key VARCHAR(120),
  name VARCHAR(80) NOT NULL,
  short_name VARCHAR(8) NOT NULL,
  sort_order INT NOT NULL,
  league_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_conference_league ON conference (league_id);

-- ===== Divisões das conferências. =====
CREATE TABLE division (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  name VARCHAR(80) NOT NULL,
  sort_order INT NOT NULL,
  conference_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_division_conference ON division (conference_id);

-- ===== Head coaches (IA ou humano). =====
CREATE TABLE manager_profile (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  avatar_url VARCHAR(512),
  career_games INT NOT NULL,
  career_playoff_wins INT NOT NULL,
  career_titles INT NOT NULL,
  career_wins INT NOT NULL,
  coach_style VARCHAR(32) NOT NULL,
  human BIT(1) NOT NULL,
  name VARCHAR(120) NOT NULL,
  nationality VARCHAR(3) NOT NULL,
  overall INT NOT NULL,
  preferred_defense VARCHAR(32) NOT NULL,
  preferred_formation VARCHAR(32) NOT NULL,
  reputation_league INT NOT NULL,
  user_account_id BINARY(16),
  world_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_manager_profile_world ON manager_profile (world_id);
CREATE INDEX ix_manager_profile_user ON manager_profile (user_account_id);

-- ===== Estádio: cobertura, piso, clima, política de ingressos; capacidade = soma dos setores (60–80 mil, conferido no editor). =====
CREATE TABLE stadium (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  atmosphere INT NOT NULL,
  capacity INT NOT NULL,
  climate_zone VARCHAR(32) NOT NULL,
  end_zone_art_key VARCHAR(120),
  level INT NOT NULL,
  maintenance_cost_per_week BIGINT NOT NULL,
  model3d_url VARCHAR(512),
  name VARCHAR(120) NOT NULL,
  roof VARCHAR(32) NOT NULL,
  surface VARCHAR(32) NOT NULL,
  ticket_dynamic_pricing BIT(1) NOT NULL,
  ticket_member_discount_pct INT NOT NULL,
  ticket_season_ticket_pct INT NOT NULL,
  valuation BIGINT NOT NULL,
  version BIGINT NOT NULL,
  world_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_stadium_world ON stadium (world_id);
ALTER TABLE stadium ADD CONSTRAINT ck_stadium_capacity CHECK (capacity BETWEEN 0 AND 80000);

-- ===== Setores do estádio (um por tipo, D-11). =====
CREATE TABLE stadium_sector (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  sector_condition INT NOT NULL,
  seats INT NOT NULL,
  ticket_price BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL,
  upgrade_cost BIGINT NOT NULL,
  stadium_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_stadium_sector_stadium ON stadium_sector (stadium_id);
ALTER TABLE stadium_sector ADD CONSTRAINT ux_stadium_sector_type UNIQUE (stadium_id, type);
ALTER TABLE stadium_sector ADD CONSTRAINT ck_stadium_sector_type CHECK (type IN ('LOWER_BOWL', 'UPPER_BOWL', 'SIDELINE_CLUB', 'END_ZONE', 'LUXURY_SUITES'));

-- ===== Franquia: liga, conferência, divisão, cores, dinheiro (centavos de S$); o clube guarda a FK do estádio (única). =====
CREATE TABLE club (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  accent_color VARCHAR(7) NOT NULL,
  cash BIGINT NOT NULL,
  city VARCHAR(80) NOT NULL,
  expected_wins INT,
  founded_year INT NOT NULL,
  logo_key VARCHAR(120),
  name VARCHAR(120) NOT NULL,
  prestige INT NOT NULL,
  primary_color VARCHAR(7) NOT NULL,
  reputation_board INT NOT NULL,
  reputation_fans INT NOT NULL,
  scouting_budget BIGINT NOT NULL,
  scouting_level INT NOT NULL,
  secondary_color VARCHAR(7) NOT NULL,
  short_name VARCHAR(4) NOT NULL,
  user_controlled BIT(1) NOT NULL,
  version BIGINT NOT NULL,
  conference_id BINARY(16),
  division_id BINARY(16),
  league_id BINARY(16),
  manager_id BINARY(16),
  stadium_id BINARY(16),
  world_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_club_world ON club (world_id);
CREATE INDEX ix_club_league ON club (league_id);
CREATE INDEX ix_club_conference ON club (conference_id);
CREATE INDEX ix_club_division ON club (division_id);
CREATE INDEX ix_club_manager ON club (manager_id);
ALTER TABLE club ADD CONSTRAINT ux_club_stadium UNIQUE (stadium_id);

-- ===== Uniformes da franquia (um por slot). =====
CREATE TABLE uniform (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  active BIT(1) NOT NULL,
  jersey_color VARCHAR(7) NOT NULL,
  number_color VARCHAR(7) NOT NULL,
  number_outline_color VARCHAR(7) NOT NULL,
  pants_color VARCHAR(7) NOT NULL,
  pattern VARCHAR(32) NOT NULL,
  revision INT NOT NULL,
  slot VARCHAR(32) NOT NULL,
  texture_key VARCHAR(120),
  club_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_uniform_club ON uniform (club_id);
ALTER TABLE uniform ADD CONSTRAINT ux_uniform_club_slot UNIQUE (club_id, slot);

-- ===== Capacete da franquia (um por clube). =====
CREATE TABLE helmet_design (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  decal_key VARCHAR(120),
  facemask_color VARCHAR(7) NOT NULL,
  finish VARCHAR(32) NOT NULL,
  shell_color VARCHAR(7) NOT NULL,
  stripe VARCHAR(32) NOT NULL,
  stripe_color VARCHAR(7) NOT NULL,
  club_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE helmet_design ADD CONSTRAINT ux_helmet_design_club UNIQUE (club_id);

-- ===== Jogador: posição do futebol americano, situação no elenco, 49 subatributos (attr_*), contrato embutido (contract_*; salários por ano em JSON). =====
CREATE TABLE player (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  age INT NOT NULL,
  attr_acceleration INT NOT NULL,
  attr_agility INT NOT NULL,
  attr_awareness INT NOT NULL,
  attr_ball_security INT NOT NULL,
  attr_block_finesse INT NOT NULL,
  attr_block_power INT NOT NULL,
  attr_block_shedding INT NOT NULL,
  attr_block_technique INT NOT NULL,
  attr_break_tackle INT NOT NULL,
  attr_carrying INT NOT NULL,
  attr_catch_in_traffic INT NOT NULL,
  attr_deep_accuracy INT NOT NULL,
  attr_deep_route INT NOT NULL,
  attr_durability INT NOT NULL,
  attr_finesse_moves INT NOT NULL,
  attr_hands INT NOT NULL,
  attr_hit_power INT NOT NULL,
  attr_juke INT NOT NULL,
  attr_jumping INT NOT NULL,
  attr_kick_accuracy INT NOT NULL,
  attr_kick_power INT NOT NULL,
  attr_kick_return INT NOT NULL,
  attr_leadership INT NOT NULL,
  attr_man_coverage INT NOT NULL,
  attr_medium_accuracy INT NOT NULL,
  attr_medium_route INT NOT NULL,
  attr_pass_block INT NOT NULL,
  attr_play_action INT NOT NULL,
  attr_play_recognition INT NOT NULL,
  attr_poise INT NOT NULL,
  attr_power_moves INT NOT NULL,
  attr_press INT NOT NULL,
  attr_pursuit INT NOT NULL,
  attr_read_defense INT NOT NULL,
  attr_release INT NOT NULL,
  attr_run_block INT NOT NULL,
  attr_short_accuracy INT NOT NULL,
  attr_short_route INT NOT NULL,
  attr_spectacular_catch INT NOT NULL,
  attr_speed INT NOT NULL,
  attr_spin INT NOT NULL,
  attr_stamina INT NOT NULL,
  attr_stiff_arm INT NOT NULL,
  attr_strength INT NOT NULL,
  attr_tackle INT NOT NULL,
  attr_throw_on_run INT NOT NULL,
  attr_throw_power INT NOT NULL,
  attr_vision INT NOT NULL,
  attr_zone_coverage INT NOT NULL,
  birth_date DATE NOT NULL,
  college VARCHAR(120),
  physical_condition INT NOT NULL,
  contract_base_salary_by_year VARCHAR(255),
  contract_franchise_tag BIT(1),
  contract_guaranteed BIGINT,
  contract_signing_bonus BIGINT,
  contract_start_season INT,
  contract_type VARCHAR(32),
  experience_years INT NOT NULL,
  height_cm INT NOT NULL,
  jersey_number INT NOT NULL,
  legendary BIT(1) NOT NULL,
  model3d_url VARCHAR(512),
  morale INT NOT NULL,
  name VARCHAR(120) NOT NULL,
  nationality VARCHAR(3) NOT NULL,
  overall INT NOT NULL,
  photo_url VARCHAR(512),
  position VARCHAR(32) NOT NULL,
  potential INT NOT NULL,
  roster_status VARCHAR(32) NOT NULL,
  secondary_position VARCHAR(32),
  suspension_weeks INT NOT NULL,
  version BIGINT NOT NULL,
  weight_kg INT NOT NULL,
  club_id BINARY(16),
  world_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_player_world ON player (world_id);
CREATE INDEX ix_player_club ON player (club_id);
CREATE INDEX ix_player_club_status ON player (club_id, roster_status);
CREATE INDEX ix_player_overall ON player (overall);
ALTER TABLE player ADD CONSTRAINT ck_player_position CHECK (position IN ('QB','RB','FB','WR','TE','LT','LG','C','RG','RT','DE','DT','OLB','MLB','CB','FS','SS','K','P','LS'));
ALTER TABLE player ADD CONSTRAINT ck_player_secondary_position CHECK (secondary_position IS NULL OR secondary_position IN ('QB','RB','FB','WR','TE','LT','LG','C','RG','RT','DE','DT','OLB','MLB','CB','FS','SS','K','P','LS'));
ALTER TABLE player ADD CONSTRAINT ck_player_roster_status CHECK (roster_status IN ('ACTIVE', 'INACTIVE', 'PRACTICE_SQUAD', 'IR', 'PUP', 'FREE_AGENT', 'WAIVED'));
ALTER TABLE player ADD CONSTRAINT ck_player_jersey CHECK (jersey_number BETWEEN 0 AND 99);

-- ===== Chaves estrangeiras (depois das tabelas: club ↔ stadium e world → world) =====
ALTER TABLE world ADD CONSTRAINT fk_world_owner FOREIGN KEY (owner_user_id) REFERENCES user_account (id) ON DELETE CASCADE;
ALTER TABLE world ADD CONSTRAINT fk_world_source FOREIGN KEY (source_world_id) REFERENCES world (id) ON DELETE SET NULL;
ALTER TABLE league ADD CONSTRAINT fk_league_world FOREIGN KEY (world_id) REFERENCES world (id) ON DELETE CASCADE;
ALTER TABLE conference ADD CONSTRAINT fk_conference_league FOREIGN KEY (league_id) REFERENCES league (id) ON DELETE CASCADE;
ALTER TABLE division ADD CONSTRAINT fk_division_conference FOREIGN KEY (conference_id) REFERENCES conference (id) ON DELETE CASCADE;
ALTER TABLE manager_profile ADD CONSTRAINT fk_manager_profile_world FOREIGN KEY (world_id) REFERENCES world (id) ON DELETE CASCADE;
ALTER TABLE manager_profile ADD CONSTRAINT fk_manager_profile_user FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE SET NULL;
ALTER TABLE stadium ADD CONSTRAINT fk_stadium_world FOREIGN KEY (world_id) REFERENCES world (id) ON DELETE CASCADE;
ALTER TABLE stadium_sector ADD CONSTRAINT fk_stadium_sector_stadium FOREIGN KEY (stadium_id) REFERENCES stadium (id) ON DELETE CASCADE;
ALTER TABLE club ADD CONSTRAINT fk_club_world FOREIGN KEY (world_id) REFERENCES world (id) ON DELETE CASCADE;
ALTER TABLE club ADD CONSTRAINT fk_club_league FOREIGN KEY (league_id) REFERENCES league (id) ON DELETE RESTRICT;
ALTER TABLE club ADD CONSTRAINT fk_club_conference FOREIGN KEY (conference_id) REFERENCES conference (id) ON DELETE RESTRICT;
ALTER TABLE club ADD CONSTRAINT fk_club_division FOREIGN KEY (division_id) REFERENCES division (id) ON DELETE RESTRICT;
ALTER TABLE club ADD CONSTRAINT fk_club_manager FOREIGN KEY (manager_id) REFERENCES manager_profile (id) ON DELETE SET NULL;
ALTER TABLE club ADD CONSTRAINT fk_club_stadium FOREIGN KEY (stadium_id) REFERENCES stadium (id) ON DELETE SET NULL;
ALTER TABLE uniform ADD CONSTRAINT fk_uniform_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE helmet_design ADD CONSTRAINT fk_helmet_design_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE player ADD CONSTRAINT fk_player_world FOREIGN KEY (world_id) REFERENCES world (id) ON DELETE CASCADE;
ALTER TABLE player ADD CONSTRAINT fk_player_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE SET NULL;
