package br.com.velion.scoresaf.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import br.com.velion.scoresaf.domain.career.RosterMoveKind;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.World;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fim do período de waivers no avanço da semana (Q-23): o gancho do bloco 7 ({@link WaiverClaimPolicy}) decide primeiro; sem
 * reivindicação, o jogador entra no practice squad da franquia que o expôs e o movimento WAIVER_CLEARED vai ao histórico.
 */
class WaiverWireTest {
  private final PlayerRepository players = mock(PlayerRepository.class);
  private final RosterMoveRepository moves = mock(RosterMoveRepository.class);
  private final WaiverClaimPolicy claims = mock(WaiverClaimPolicy.class);

  private static Player exposed(World w, Club club) {
    Player p = new Player();
    p.setWorld(w); p.setClub(club); p.setName("Exposto"); p.setRosterStatus(RosterStatus.PRACTICE_SQUAD_WAIVERS);
    return p;
  }

  @Test
  void sem_reivindicacao_entra_no_practice_squad_e_reivindicado_fica_com_o_bloco_7() {
    World w = new World();
    Club club = new Club(); club.setWorld(w);
    CareerSave save = new CareerSave(); save.setWorld(w);
    Season season = new Season();
    Player claimed = exposed(w, club), cleared = exposed(w, club);
    when(players.findByWorldIdAndRosterStatus(w.getId(), RosterStatus.PRACTICE_SQUAD_WAIVERS)).thenReturn(List.of(claimed, cleared));
    when(claims.tryClaim(save, claimed)).thenReturn(true);
    List<RosterMove> saved = new ArrayList<>();
    when(moves.save(any())).thenAnswer(i -> { saved.add(i.getArgument(0)); return i.getArgument(0); });

    int n = new WaiverWire(players, moves, claims, mock(AuditService.class)).resolve(save, season, 3);

    assertThat(n).isEqualTo(1);
    assertThat(cleared.getRosterStatus()).isEqualTo(RosterStatus.PRACTICE_SQUAD);
    assertThat(cleared.getClub()).isSameAs(club);
    assertThat(claimed.getRosterStatus()).as("o bloco 7 move o reivindicado").isEqualTo(RosterStatus.PRACTICE_SQUAD_WAIVERS);
    verify(players).save(cleared);
    verify(players, never()).save(claimed);
    assertThat(saved).singleElement().satisfies(m -> {
      assertThat(m.getKind()).isEqualTo(RosterMoveKind.WAIVER_CLEARED);
      assertThat(m.getFromStatus()).isEqualTo(RosterStatus.PRACTICE_SQUAD_WAIVERS);
      assertThat(m.getToStatus()).isEqualTo(RosterStatus.PRACTICE_SQUAD);
      assertThat(m.getPlayer()).isSameAs(cleared);
      assertThat(m.getClub()).isSameAs(club);
      assertThat(m.getWeek()).isEqualTo(3);
      assertThat(m.getSeason()).isSameAs(season);
    });
  }

  @Test
  void ate_o_bloco_7_a_ia_nao_reivindica_ninguem() {
    assertThat(new NoWaiverClaims().tryClaim(new CareerSave(), new Player())).isFalse();
  }
}
