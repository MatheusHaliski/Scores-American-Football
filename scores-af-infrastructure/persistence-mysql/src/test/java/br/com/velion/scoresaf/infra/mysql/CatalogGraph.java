package br.com.velion.scoresaf.infra.mysql;

import br.com.velion.scoresaf.application.seed.DefaultWorld;
import br.com.velion.scoresaf.infra.mysql.repository.ClubJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.ConferenceJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.DivisionJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.HelmetDesignJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.LeagueJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.ManagerProfileJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.PlayerJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.StadiumJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.StadiumSectorJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.UniformJpaRepository;
import br.com.velion.scoresaf.infra.mysql.repository.WorldJpaRepository;
import org.springframework.stereotype.Component;

/** Grava um grafo de mundo pelos repositórios, na ordem das FKs (o mesmo que o {@code WorldGraphStore} da aplicação faz). */
@Component
class CatalogGraph {
  final WorldJpaRepository worlds; final LeagueJpaRepository leagues; final ConferenceJpaRepository conferences; final DivisionJpaRepository divisions;
  final ManagerProfileJpaRepository managers; final StadiumJpaRepository stadiums; final StadiumSectorJpaRepository sectors; final ClubJpaRepository clubs;
  final UniformJpaRepository uniforms; final HelmetDesignJpaRepository helmets; final PlayerJpaRepository players;

  CatalogGraph(WorldJpaRepository worlds, LeagueJpaRepository leagues, ConferenceJpaRepository conferences, DivisionJpaRepository divisions,
               ManagerProfileJpaRepository managers, StadiumJpaRepository stadiums, StadiumSectorJpaRepository sectors, ClubJpaRepository clubs,
               UniformJpaRepository uniforms, HelmetDesignJpaRepository helmets, PlayerJpaRepository players) {
    this.worlds = worlds; this.leagues = leagues; this.conferences = conferences; this.divisions = divisions; this.managers = managers;
    this.stadiums = stadiums; this.sectors = sectors; this.clubs = clubs; this.uniforms = uniforms; this.helmets = helmets; this.players = players;
  }

  void persist(DefaultWorld w) {
    worlds.save(w.world());
    leagues.saveAll(w.leagues());
    conferences.saveAll(w.conferences());
    divisions.saveAll(w.divisions());
    managers.saveAll(w.managers());
    stadiums.saveAll(w.stadiums());
    sectors.saveAll(w.sectors());
    clubs.saveAll(w.clubs());
    uniforms.saveAll(w.uniforms());
    helmets.saveAll(w.helmets());
    players.saveAll(w.players());
  }
}
