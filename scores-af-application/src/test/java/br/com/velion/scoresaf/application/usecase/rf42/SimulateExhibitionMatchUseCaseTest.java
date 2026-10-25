package br.com.velion.scoresaf.application.usecase.rf42;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.port.out.persistence.ClubRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integração do motor (bloco 5) com o catálogo do mundo semente (bloco 4), sem gravar a partida. */
class SimulateExhibitionMatchUseCaseTest {
  private final ClubRepository clubs = mock(ClubRepository.class);
  private final PlayerRepository players = mock(PlayerRepository.class);
  private final SimulateExhibitionMatchUseCase useCase = new SimulateExhibitionMatchUseCase(clubs, players);
  private Club home;
  private Club away;

  @BeforeEach
  void stubs() {
    home = SeedMatches.club("PAV");
    away = SeedMatches.byStrength().stream().filter(c -> c != home).findFirst().orElseThrow();
    for (Club c : SeedMatches.world().clubs()) {
      when(clubs.findById(c.getId())).thenReturn(Optional.of(c));
      when(players.findByClubIdOrderByOverallDesc(c.getId()))
          .thenReturn(SeedMatches.roster(c).stream().sorted(Comparator.comparingInt(Player::getOverall).reversed()).toList());
    }
  }

  @Test
  void simula_o_jogo_inteiro_entre_duas_franquias_do_mundo_e_e_deterministico() {
    MatchResult r = useCase.execute(home.getId(), away.getId(), 2026L);
    assertThat(r.homeClubId()).isEqualTo(home.getId());
    assertThat(r.awayClubId()).isEqualTo(away.getId());
    assertThat(r.events()).hasSizeGreaterThan(120);
    assertThat(r.homeScore() + r.awayScore()).isPositive();
    assertThat(r.rulesVersion()).isEqualTo(home.getLeague().getRules().getVersion());
    assertThat(useCase.execute(home.getId(), away.getId(), 2026L)).isEqualTo(r);
    assertThat(useCase.execute(home.getId(), away.getId(), 2027L).events()).isNotEqualTo(r.events());
    // só jogadores ativos do elenco entram, no máximo os 46 do dia de jogo
    List<UUID> active = SeedMatches.roster(home).stream().filter(p -> p.getRosterStatus() == RosterStatus.ACTIVE).map(Player::getId).toList();
    assertThat(r.homePlayers()).extracting(PlayerBoxScore::playerId).allMatch(active::contains).hasSizeLessThanOrEqualTo(46);
    assertThat(r.homePlayers()).anyMatch(p -> p.passing() != null && p.passing().attempts() > 10);
  }

  @Test
  void recusa_franquia_inexistente_a_mesma_franquia_e_mundos_diferentes() {
    UUID ghost = UUID.randomUUID();
    when(clubs.findById(ghost)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(home.getId(), ghost, 1L)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> useCase.execute(home.getId(), home.getId(), 1L)).isInstanceOf(BusinessRuleException.class);
    Club other = new Club();
    other.setId(UUID.randomUUID());
    World w = new World();
    w.setId(UUID.randomUUID());
    other.setWorld(w);
    when(clubs.findById(other.getId())).thenReturn(Optional.of(other));
    assertThatThrownBy(() -> useCase.execute(home.getId(), other.getId(), 1L)).isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("mesmo mundo");
  }
}
