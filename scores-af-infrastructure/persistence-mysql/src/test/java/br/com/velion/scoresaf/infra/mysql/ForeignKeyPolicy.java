package br.com.velion.scoresaf.infra.mysql;

import java.util.List;

/**
 * Política de ON DELETE esperada para cada FK (D-23 do basquete). FK nova nasce com ON DELETE na própria migration e entra aqui
 * no mesmo PR, senão o ForeignKeyPolicyTest fica vermelho. Bloco 3: as FKs da plataforma (V1__plataforma.sql); a conta é
 * anonimizada e nunca apagada, mas o que é dela cai junto se um dia for (sessões, configurações, preferências, notificações,
 * pedidos de exportação); a prova do consentimento (consent_record) segura a exclusão. Bloco 4: as do catálogo (V2__dominio_af.sql).
 * Bloco 6a: as da carreira (V3__carreira.sql). Bloco 6b: as da partida (V4__partida.sql).
 */
final class ForeignKeyPolicy {
  record Fk(String table, String column, String refTable, String refColumn, String onDelete) {}

  static final List<Fk> POLICY = List.of(
      // V4 — partida (bloco 6b): plano de jogo, playbook, partida ao vivo, relatório e box score são da carreira e caem com ela, com
      // o jogo do calendário, a franquia ou o jogador do mundo SAVE dela (CASCADE).
      new Fk("game_plan", "career_save_id", "career_save", "id", "CASCADE"),
      new Fk("game_plan", "club_id", "club", "id", "CASCADE"),
      new Fk("match_report", "career_save_id", "career_save", "id", "CASCADE"),
      new Fk("match_report", "fixture_id", "fixture", "id", "CASCADE"),
      new Fk("match_session", "career_save_id", "career_save", "id", "CASCADE"),
      new Fk("match_session", "fixture_id", "fixture", "id", "CASCADE"),
      new Fk("playbook", "career_save_id", "career_save", "id", "CASCADE"),
      new Fk("playbook", "club_id", "club", "id", "CASCADE"),
      new Fk("playbook_weight", "playbook_id", "playbook", "id", "CASCADE"),
      new Fk("player_match_stats", "career_save_id", "career_save", "id", "CASCADE"),
      new Fk("player_match_stats", "club_id", "club", "id", "CASCADE"),
      new Fk("player_match_stats", "fixture_id", "fixture", "id", "CASCADE"),
      new Fk("player_match_stats", "player_id", "player", "id", "CASCADE"),
      // V3 — carreira e elenco (bloco 6a): o que é da carreira cai com o save (CASCADE) e com o mundo SAVE dela (franquias, ligas e
      // jogadores do mundo levam calendário, movimentos e depth chart); o save só perde a referência à liga, franquia ou temporada
      // atual (SET NULL: sem clube = desempregado). O save cai com a conta, o mundo e o head coach humano (todos dele).
      new Fk("career_save", "club_id", "club", "id", "SET NULL"),
      new Fk("career_save", "league_id", "league", "id", "SET NULL"),
      new Fk("career_save", "manager_id", "manager_profile", "id", "CASCADE"),
      new Fk("career_save", "season_id", "season", "id", "SET NULL"),
      new Fk("career_save", "user_id", "user_account", "id", "CASCADE"),
      new Fk("career_save", "world_id", "world", "id", "CASCADE"),
      new Fk("depth_chart", "career_save_id", "career_save", "id", "CASCADE"),
      new Fk("depth_chart", "club_id", "club", "id", "CASCADE"),
      new Fk("depth_chart_slot", "depth_chart_id", "depth_chart", "id", "CASCADE"),
      new Fk("depth_chart_slot", "player_id", "player", "id", "CASCADE"),
      new Fk("fixture", "away_club_id", "club", "id", "CASCADE"),
      new Fk("fixture", "home_club_id", "club", "id", "CASCADE"),
      new Fk("fixture", "season_id", "season", "id", "CASCADE"),
      new Fk("fixture", "week_id", "season_week", "id", "CASCADE"),
      new Fk("roster_move", "career_save_id", "career_save", "id", "CASCADE"),
      new Fk("roster_move", "club_id", "club", "id", "SET NULL"),
      new Fk("roster_move", "player_id", "player", "id", "CASCADE"),
      new Fk("roster_move", "season_id", "season", "id", "CASCADE"),
      new Fk("season", "career_save_id", "career_save", "id", "CASCADE"),
      new Fk("season", "league_id", "league", "id", "CASCADE"),
      new Fk("season_week", "season_id", "season", "id", "CASCADE"),
      // V2 — domínio do futebol americano (bloco 4): o mundo leva o catálogo junto (CASCADE); a franquia segura liga, conferência e
      // divisão (RESTRICT: o editor move as franquias antes de apagar a estrutura); perder estádio, head coach ou clube só anula a
      // referência (SET NULL: o jogador vira agente livre).
      new Fk("club", "conference_id", "conference", "id", "RESTRICT"),
      new Fk("club", "division_id", "division", "id", "RESTRICT"),
      new Fk("club", "league_id", "league", "id", "RESTRICT"),
      new Fk("club", "manager_id", "manager_profile", "id", "SET NULL"),
      new Fk("club", "stadium_id", "stadium", "id", "SET NULL"),
      new Fk("club", "world_id", "world", "id", "CASCADE"),
      new Fk("conference", "league_id", "league", "id", "CASCADE"),
      new Fk("division", "conference_id", "conference", "id", "CASCADE"),
      new Fk("helmet_design", "club_id", "club", "id", "CASCADE"),
      new Fk("league", "world_id", "world", "id", "CASCADE"),
      new Fk("manager_profile", "user_account_id", "user_account", "id", "SET NULL"),
      new Fk("manager_profile", "world_id", "world", "id", "CASCADE"),
      new Fk("player", "club_id", "club", "id", "SET NULL"),
      new Fk("player", "world_id", "world", "id", "CASCADE"),
      new Fk("stadium", "world_id", "world", "id", "CASCADE"),
      new Fk("stadium_sector", "stadium_id", "stadium", "id", "CASCADE"),
      new Fk("uniform", "club_id", "club", "id", "CASCADE"),
      new Fk("world", "owner_user_id", "user_account", "id", "CASCADE"),
      new Fk("world", "source_world_id", "world", "id", "SET NULL"),
      // V1 — plataforma
      new Fk("consent_record", "user_id", "user_account", "id", "RESTRICT"),
      new Fk("data_export_job", "user_id", "user_account", "id", "CASCADE"),
      new Fk("notification", "user_id", "user_account", "id", "CASCADE"),
      new Fk("notification_preference", "user_id", "user_account", "id", "CASCADE"),
      new Fk("user_session", "user_id", "user_account", "id", "CASCADE"),
      new Fk("user_settings", "user_id", "user_account", "id", "CASCADE"));

  private ForeignKeyPolicy() {}
}
