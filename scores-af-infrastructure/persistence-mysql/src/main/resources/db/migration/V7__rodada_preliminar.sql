-- Scores: American Football (VELION TECH CO.) — V7: rodada preliminar dos playoffs (D-123, correção da revisão do bloco 6c). MySQL 8.
-- O editor aceita de 1 a 8 sementes por conferência e de 0 a "sementes" folgas; algumas combinações (8/1, 8/2, 7/2, 7/3, 8/4, 6/3...)
-- dão 4 rodadas de conferência e, com duas conferências, 5 rodadas com a final. A rodada a mais abre a chave como PRELIMINARY, antes
-- do WILD_CARD. Convenções da V1–V6. Nunca editar V1–V6.

ALTER TABLE season_week DROP CONSTRAINT ck_season_week_kind;
ALTER TABLE season_week ADD CONSTRAINT ck_season_week_kind CHECK (kind IN ('PRESEASON', 'REGULAR', 'PRELIMINARY', 'WILD_CARD', 'DIVISIONAL', 'CONFERENCE', 'FINAL'));
