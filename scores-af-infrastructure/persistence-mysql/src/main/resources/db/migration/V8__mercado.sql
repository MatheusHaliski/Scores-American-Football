-- Scores: American Football (VELION TECH CO.) — V8: mercado (bloco 7a): contratos, teto rígido, free agency e waivers (RF49, RF65).
-- Parcelas explícitas do bônus por ano (renovação, reestruturação e franchise tag, D-131), trade block do jogador, o histórico de
-- transações de contrato (com a chave única da franchise tag por temporada e franquia), a janela de free agency de cada temporada, as
-- reivindicações em waivers do usuário e os movimentos novos do elenco. Convenções da V1–V7: PK/FK UUID em BINARY(16); enums em
-- VARCHAR; booleanos em BIT(1); toda FK com ON DELETE explícito (política em ForeignKeyPolicy). Nunca editar V1–V7.

-- ===== Jogador: parcelas do bônus por ano (lista JSON; vazia = rateio igual) e a marca do trade block. =====
ALTER TABLE player ADD COLUMN contract_proration_by_year VARCHAR(255);
ALTER TABLE player ADD COLUMN trade_block BIT(1) NOT NULL DEFAULT 0;

-- ===== Transações de contrato (contratação, renovação, reestruturação, franchise tag, reivindicação em waivers). =====
CREATE TABLE contract_event (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  amount BIGINT NOT NULL,
  market_day INT,
  guaranteed BIGINT NOT NULL,
  kind VARCHAR(32) NOT NULL,
  signing_bonus BIGINT NOT NULL,
  source VARCHAR(16) NOT NULL,
  tag_slot INT,
  total_value BIGINT NOT NULL,
  years INT NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  club_id BINARY(16) NOT NULL,
  player_id BINARY(16) NOT NULL,
  season_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_contract_event_career ON contract_event (career_save_id, created_at);
CREATE INDEX ix_contract_event_player ON contract_event (player_id);
CREATE INDEX ix_contract_event_club ON contract_event (club_id);
ALTER TABLE contract_event ADD CONSTRAINT ux_contract_event_tag UNIQUE (season_id, club_id, tag_slot);
ALTER TABLE contract_event ADD CONSTRAINT ck_contract_event_kind CHECK (kind IN ('SIGNING', 'RENEWAL', 'RESTRUCTURE', 'FRANCHISE_TAG', 'WAIVER_CLAIM'));
ALTER TABLE contract_event ADD CONSTRAINT ck_contract_event_source CHECK (source IN ('USER', 'AI'));
ALTER TABLE contract_event ADD CONSTRAINT ck_contract_event_tag_slot CHECK (tag_slot IS NULL OR tag_slot = 1);
ALTER TABLE contract_event ADD CONSTRAINT ck_contract_event_years CHECK (years BETWEEN 0 AND 10);

-- ===== Janela de free agency (uma por temporada). =====
CREATE TABLE free_agency (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  closed_at DATETIME(6),
  current_day INT NOT NULL,
  days INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  season_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_free_agency_career ON free_agency (career_save_id);
ALTER TABLE free_agency ADD CONSTRAINT ux_free_agency_season UNIQUE (season_id);
ALTER TABLE free_agency ADD CONSTRAINT ck_free_agency_status CHECK (status IN ('OPEN', 'CLOSED'));
ALTER TABLE free_agency ADD CONSTRAINT ck_free_agency_day CHECK (current_day >= 0 AND current_day <= days);

-- ===== Reivindicações pendentes do usuário em waivers (uma por jogador e franquia). =====
CREATE TABLE waiver_claim (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(64),
  last_modified_by VARCHAR(64),
  updated_at DATETIME(6) NOT NULL,
  career_save_id BINARY(16) NOT NULL,
  club_id BINARY(16) NOT NULL,
  player_id BINARY(16) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_waiver_claim_player ON waiver_claim (player_id);
ALTER TABLE waiver_claim ADD CONSTRAINT ux_waiver_claim UNIQUE (career_save_id, player_id, club_id);

-- ===== Movimentos novos do elenco: contratação, reivindicação e saída de waivers sem reivindicação. =====
ALTER TABLE roster_move DROP CONSTRAINT ck_roster_move_kind;
ALTER TABLE roster_move ADD CONSTRAINT ck_roster_move_kind CHECK (kind IN ('STATUS_CHANGE', 'RELEASE', 'WAIVER_CLEARED', 'CONTRACT_EXPIRED', 'SIGNING',
  'WAIVER_CLAIM', 'WAIVER_UNCLAIMED'));

-- ===== Chaves estrangeiras: tudo é da carreira e cai com ela, com a temporada, a franquia ou o jogador do mundo SAVE dela (CASCADE). =====
ALTER TABLE contract_event ADD CONSTRAINT fk_contract_event_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE contract_event ADD CONSTRAINT fk_contract_event_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE CASCADE;
ALTER TABLE contract_event ADD CONSTRAINT fk_contract_event_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE contract_event ADD CONSTRAINT fk_contract_event_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE;
ALTER TABLE free_agency ADD CONSTRAINT fk_free_agency_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE free_agency ADD CONSTRAINT fk_free_agency_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE CASCADE;
ALTER TABLE waiver_claim ADD CONSTRAINT fk_waiver_claim_career FOREIGN KEY (career_save_id) REFERENCES career_save (id) ON DELETE CASCADE;
ALTER TABLE waiver_claim ADD CONSTRAINT fk_waiver_claim_club FOREIGN KEY (club_id) REFERENCES club (id) ON DELETE CASCADE;
ALTER TABLE waiver_claim ADD CONSTRAINT fk_waiver_claim_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE;
