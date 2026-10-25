-- Scores: American Football — respostas do PO às perguntas Q-22..Q-28 (25/10/2026), sobre a V3 (carreira e elenco).
-- Q-23 (D-78): mover dos 53 para o practice squad passa por waivers; o jogador fica em PRACTICE_SQUAD_WAIVERS até a semana avançar
--   e, sem reivindicação, entra no practice squad (movimento WAIVER_CLEARED).
-- Q-24 (D-79): regra do 1º de junho; a parte do dead money que vai para a temporada seguinte fica no movimento da dispensa.
-- Sem FK nova (ForeignKeyPolicy inalterada). Nunca editar V1–V4.

ALTER TABLE player DROP CONSTRAINT ck_player_roster_status;
ALTER TABLE player ADD CONSTRAINT ck_player_roster_status
  CHECK (roster_status IN ('ACTIVE', 'INACTIVE', 'PRACTICE_SQUAD', 'IR', 'PUP', 'FREE_AGENT', 'WAIVED', 'PRACTICE_SQUAD_WAIVERS'));

ALTER TABLE roster_move DROP CONSTRAINT ck_roster_move_kind;
ALTER TABLE roster_move ADD CONSTRAINT ck_roster_move_kind CHECK (kind IN ('STATUS_CHANGE', 'RELEASE', 'WAIVER_CLEARED'));

ALTER TABLE roster_move ADD COLUMN dead_money_next_season BIGINT NOT NULL DEFAULT 0;
ALTER TABLE roster_move ADD CONSTRAINT ck_roster_move_dead_money_next CHECK (dead_money_next_season >= 0);
