package br.com.velion.scoresaf.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * Fim do período de waivers (Q-23; bloco 7a, D-137): a fila ({@link WaiverClaimPolicy}) decide primeiro; sem reivindicação, quem ia ao
 * practice squad entra nele (WAIVER_CLEARED) e o dispensado vira agente livre (WAIVER_UNCLAIMED, com a franquia que dispensou).
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
    claimed.setName("A reivindicado"); cleared.setName("B liberado");
    when(players.findByWorldIdAndRosterStatus(w.getId(), RosterStatus.PRACTICE_SQUAD_WAIVERS)).thenReturn(List.of(claimed, cleared));
    when(players.findByWorldIdAndRosterStatus(w.getId(), RosterStatus.WAIVED)).thenReturn(List.of());
    when(claims.claim(any(), any(), anyInt(), any())).thenReturn(java.util.Set.of(claimed.getId()));
    List<RosterMove> saved = new ArrayList<>();
    when(moves.save(any())).thenAnswer(i -> { saved.add(i.getArgument(0)); return i.getArgument(0); });

    int n = new WaiverWire(players, moves, claims, mock(AuditService.class)).resolve(save, season, 3);

    assertThat(n).isEqualTo(1);
    assertThat(cleared.getRosterStatus()).isEqualTo(RosterStatus.PRACTICE_SQUAD);
    assertThat(cleared.getClub()).isSameAs(club);
    assertThat(claimed.getRosterStatus()).as("a fila move o reivindicado").isEqualTo(RosterStatus.PRACTICE_SQUAD_WAIVERS);
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
  void dispensado_sem_reivindicacao_vira_agente_livre_com_a_franquia_que_dispensou() {
    World w = new World();
    Club club = new Club(); club.setWorld(w);
    CareerSave save = new CareerSave(); save.setWorld(w);
    Player released = new Player();
    released.setWorld(w); released.setName("Dispensado"); released.setRosterStatus(RosterStatus.WAIVED);
    RosterMove release = new RosterMove();
    release.setKind(RosterMoveKind.RELEASE); release.setClub(club); release.setPlayer(released);
    when(players.findByWorldIdAndRosterStatus(w.getId(), RosterStatus.PRACTICE_SQUAD_WAIVERS)).thenReturn(List.of());
    when(players.findByWorldIdAndRosterStatus(w.getId(), RosterStatus.WAIVED)).thenReturn(List.of(released));
    when(moves.findByCareerIdAndPlayerIdOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(release));
    when(claims.claim(any(), any(), anyInt(), any())).thenReturn(java.util.Set.of());
    List<RosterMove> saved = new ArrayList<>();
    when(moves.save(any())).thenAnswer(i -> { saved.add(i.getArgument(0)); return i.getArgument(0); });

    assertThat(new WaiverWire(players, moves, claims, mock(AuditService.class)).resolve(save, new Season(), 5)).isZero();

    assertThat(released.getRosterStatus()).isEqualTo(RosterStatus.FREE_AGENT);
    assertThat(saved).singleElement().satisfies(m -> {
      assertThat(m.getKind()).isEqualTo(RosterMoveKind.WAIVER_UNCLAIMED);
      assertThat(m.getClub()).isSameAs(club);
      assertThat(m.getToStatus()).isEqualTo(RosterStatus.FREE_AGENT);
    });
  }
}
