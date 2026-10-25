package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.WorldRepository;
import br.com.velion.scoresaf.application.seed.DefaultWorldFactory;
import br.com.velion.scoresaf.application.seed.DefaultWorldSeeder;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Com {@code scores.seed.enabled=true} a subida grava o mundo padrão (D-23); chamar a semente de novo não duplica nada. */
@SpringBootTest(properties = "scores.seed.enabled=true")
@ActiveProfiles("test")
class SeedRunnerIntegrationTest {
  @Autowired WorldRepository worlds;
  @Autowired ClubRepository clubs;
  @Autowired PlayerRepository players;
  @Autowired DefaultWorldSeeder seeder;

  @Test
  void subida_grava_o_mundo_padrao_uma_vez_so() {
    World w = worlds.findFirstByKindAndOwnerUserIsNullOrderByCreatedAtAsc(WorldKind.TEMPLATE).orElseThrow();
    assertThat(w.getSeed()).isEqualTo(DefaultWorldFactory.DEFAULT_SEED);
    assertThat(w.getId()).as("mesma semente, mesmo id").isEqualTo(new DefaultWorldFactory().build().world().getId());
    assertThat(clubs.countByWorldId(w.getId())).isEqualTo(32);
    assertThat(players.countByWorldId(w.getId())).isEqualTo(32 * 69);
    assertThat(seeder.seedIfMissing().getId()).isEqualTo(w.getId());
    assertThat(worlds.count()).isEqualTo(1);
    assertThat(players.count()).isEqualTo(32 * 69);
  }
}
