package br.com.velion.scoresaf.domain.engine.sim;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.engine.model.MatchResult;
import br.com.velion.scoresaf.domain.engine.model.PlayerBoxScore;
import br.com.velion.scoresaf.domain.engine.model.TeamBoxScore;
import br.com.velion.scoresaf.domain.shared.Position;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Box score (docs/01 §3.6): soma certa e "—" (null), nunca zero, para o que o motor não gera. */
class BoxScoreTest {

  @Test
  void o_que_o_motor_nao_gera_e_null_e_nunca_zero() throws Exception {
    int defenders = 0;
    for (MatchResult r : SampleGames.pro().subList(0, 30)) {
      for (PlayerBoxScore b : concat(r.homePlayers(), r.awayPlayers())) {
        for (String path : PlayerBoxScore.NOT_GENERATED) {
          String[] p = path.split("\\.");
          Object group = component(b, p[0]);
          if (group == null) continue;
          assertThat(component(group, p[1])).as("%s de %s", path, b.name()).isNull();
          defenders++;
        }
        // linha que o jogador não tem é ausente (—), não uma linha de zeros
        if (b.position() == Position.WR || b.position() == Position.CB) assertThat(b.passing()).isNull();
        if (b.position() == Position.QB) assertThat(b.kicking()).isNull();
        if (b.defense() != null) {
          assertThat(b.defense().soloTackles() + b.defense().sacks() + b.defense().interceptions() + b.defense().passesDefended()
              + b.defense().forcedFumbles() + b.defense().fumbleRecoveries() + b.defense().tacklesForLoss() + b.defense().touchdowns()).isPositive();
        }
      }
    }
    assertThat(defenders).isGreaterThan(500);
  }

  @Test
  void campos_gerados_nunca_sao_null() throws Exception {
    MatchResult r = SampleGames.pro().get(0);
    for (PlayerBoxScore b : concat(r.homePlayers(), r.awayPlayers())) {
      for (RecordComponent group : PlayerBoxScore.class.getRecordComponents()) {
        Object g = group.getAccessor().invoke(b);
        if (g == null || !g.getClass().isRecord()) continue;
        for (RecordComponent f : g.getClass().getRecordComponents()) {
          String path = group.getName() + "." + f.getName();
          if (!PlayerBoxScore.NOT_GENERATED.contains(path)) assertThat(f.getAccessor().invoke(g)).as(path).isNotNull();
        }
      }
    }
  }

  @Test
  void totais_da_equipe_batem_com_a_soma_dos_jogadores_e_com_o_placar() {
    for (MatchResult r : SampleGames.pro().subList(0, 60)) {
      check(r.homeTeam(), r.homePlayers(), r.homeScore());
      check(r.awayTeam(), r.awayPlayers(), r.awayScore());
      assertThat(r.homeTeam().timeOfPossessionSeconds() + r.awayTeam().timeOfPossessionSeconds()).isBetween(3600, 3600 + 10 * 60 * 3);
    }
  }

  private static void check(TeamBoxScore t, List<PlayerBoxScore> ps, int score) {
    assertThat(t.points()).isEqualTo(score);
    assertThat(t.pointsByQuarter().stream().mapToInt(Integer::intValue).sum()).isEqualTo(score);
    int passYds = ps.stream().filter(p -> p.passing() != null).mapToInt(p -> p.passing().yards()).sum();
    int sackYds = ps.stream().filter(p -> p.passing() != null).mapToInt(p -> p.passing().sackYards()).sum();
    int cmp = ps.stream().filter(p -> p.passing() != null).mapToInt(p -> p.passing().completions()).sum();
    int rec = ps.stream().filter(p -> p.receiving() != null).mapToInt(p -> p.receiving().receptions()).sum();
    int recYds = ps.stream().filter(p -> p.receiving() != null).mapToInt(p -> p.receiving().yards()).sum();
    int rush = ps.stream().filter(p -> p.rushing() != null).mapToInt(p -> p.rushing().yards()).sum();
    int ints = ps.stream().filter(p -> p.passing() != null).mapToInt(p -> p.passing().interceptions()).sum();
    assertThat(t.passYards()).isEqualTo(passYds - sackYds);
    assertThat(cmp).isEqualTo(rec).isEqualTo(t.passCompletions());
    assertThat(recYds).isEqualTo(passYds);
    assertThat(t.rushYards()).isEqualTo(rush);
    assertThat(t.totalYards()).isEqualTo(t.rushYards() + t.passYards());
    assertThat(t.interceptionsThrown()).isEqualTo(ints);
    assertThat(t.turnovers()).isEqualTo(t.interceptionsThrown() + t.fumblesLost());
    assertThat(t.thirdDownConversions()).isLessThanOrEqualTo(t.thirdDownAttempts());
    assertThat(t.redZoneTouchdowns()).isLessThanOrEqualTo(t.redZoneTrips());
    assertThat(t.firstDowns()).isEqualTo(t.firstDownsRushing() + t.firstDownsPassing() + t.firstDownsPenalty());
  }

  private static List<PlayerBoxScore> concat(List<PlayerBoxScore> a, List<PlayerBoxScore> b) {
    List<PlayerBoxScore> out = new java.util.ArrayList<>(a);
    out.addAll(b);
    return out;
  }

  private static Object component(Object record, String name) throws Exception {
    for (RecordComponent c : record.getClass().getRecordComponents()) if (c.getName().equals(name)) return c.getAccessor().invoke(record);
    throw new IllegalArgumentException(name);
  }
}
