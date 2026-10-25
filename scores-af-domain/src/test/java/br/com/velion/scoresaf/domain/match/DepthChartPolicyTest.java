package br.com.velion.scoresaf.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.engine.model.DepthSlot;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras do depth chart salvo (RF60): padrão completo nos 46, erros, aviso de fora de posição e conversão para o motor. */
class DepthChartPolicyTest {
  private static final Map<Position, Integer> ROSTER = new LinkedHashMap<>();
  static {
    ROSTER.put(Position.QB, 3); ROSTER.put(Position.RB, 3); ROSTER.put(Position.FB, 1); ROSTER.put(Position.WR, 6); ROSTER.put(Position.TE, 3);
    ROSTER.put(Position.LT, 2); ROSTER.put(Position.LG, 2); ROSTER.put(Position.C, 2); ROSTER.put(Position.RG, 1); ROSTER.put(Position.RT, 2);
    ROSTER.put(Position.DE, 5); ROSTER.put(Position.DT, 4); ROSTER.put(Position.OLB, 4); ROSTER.put(Position.MLB, 2); ROSTER.put(Position.CB, 6);
    ROSTER.put(Position.FS, 2); ROSTER.put(Position.SS, 2); ROSTER.put(Position.K, 1); ROSTER.put(Position.P, 1); ROSTER.put(Position.LS, 1);
  }

  final Club club = new Club();
  final List<Player> roster = new ArrayList<>();
  final Map<UUID, Player> byId = new LinkedHashMap<>();

  DepthChartPolicyTest() {
    int jersey = 1;
    for (Map.Entry<Position, Integer> e : ROSTER.entrySet()) {
      for (int i = 0; i < e.getValue(); i++) {
        Player p = new Player();
        p.setClub(club); p.setPosition(e.getKey()); p.setJerseyNumber(jersey++); p.setName(e.getKey() + "" + i);
        p.setAttributes(PlayerAttributes.uniform(40).with(AttributeKey.SPEED, 60 + i).with(AttributeKey.KICK_RETURN, 50 + i));
        p.setOverall(p.ratingAt(p.getPosition()).overall());
        p.setRosterStatus(RosterStatus.ACTIVE);
        roster.add(p);
        byId.put(p.getId(), p);
      }
    }
  }

  private Player first(Position pos) { return roster.stream().filter(p -> p.getPosition() == pos).findFirst().orElseThrow(); }

  private static Set<String> codes(DepthChartPolicy.Result r) {
    Set<String> out = new LinkedHashSet<>();
    r.errors().forEach(i -> out.add(i.code()));
    return out;
  }

  private Map<ChartSlot, List<UUID>> mutable(Map<ChartSlot, List<UUID>> chart) {
    Map<ChartSlot, List<UUID>> m = new EnumMap<>(ChartSlot.class);
    chart.forEach((k, v) -> m.put(k, new ArrayList<>(v)));
    return m;
  }

  @Test
  void padrao_e_completo_e_cabe_nos_ativos_do_dia_de_jogo() {
    Map<ChartSlot, List<UUID>> chart = DepthChartPolicy.defaults(roster, 46);
    DepthChartPolicy.Result r = DepthChartPolicy.validate(chart, byId, club.getId(), 46);
    assertThat(r.errors()).isEmpty();
    assertThat(r.uniquePlayers()).isLessThanOrEqualTo(46);
    assertThat(chart.get(ChartSlot.QB)).startsWith(first(Position.QB).getId());
    assertThat(chart.get(ChartSlot.H)).first().isEqualTo(first(Position.P).getId());
    // com um limite menor, o padrão apara as vagas mais fundas e continua completo
    Map<ChartSlot, List<UUID>> small = DepthChartPolicy.defaults(roster, 30);
    assertThat(DepthChartPolicy.validate(small, byId, club.getId(), 30).errors()).isEmpty();
  }

  @Test
  void erros_indisponivel_repetido_incompleto_e_ativos_demais() {
    Map<ChartSlot, List<UUID>> chart = mutable(DepthChartPolicy.defaults(roster, 46));
    Player ps = first(Position.WR);
    ps.setRosterStatus(RosterStatus.PRACTICE_SQUAD);
    chart.get(ChartSlot.QB).add(0, ps.getId());
    chart.get(ChartSlot.CB).add(chart.get(ChartSlot.CB).get(0));
    chart.put(ChartSlot.K, new ArrayList<>());
    assertThat(codes(DepthChartPolicy.validate(chart, byId, club.getId(), 46)))
        .contains(DepthChartPolicy.UNAVAILABLE, DepthChartPolicy.DUPLICATE, DepthChartPolicy.INCOMPLETE);
    ps.setRosterStatus(RosterStatus.ACTIVE);
    Player suspended = first(Position.TE);
    suspended.setSuspensionWeeks(1);
    assertThat(codes(DepthChartPolicy.validate(Map.of(ChartSlot.TE, List.of(suspended.getId())), byId, club.getId(), 46))).contains(DepthChartPolicy.UNAVAILABLE);
    suspended.setSuspensionWeeks(0);
    Player stranger = new Player();
    stranger.setClub(new Club()); stranger.setPosition(Position.QB); stranger.setRosterStatus(RosterStatus.ACTIVE);
    Map<UUID, Player> withStranger = new LinkedHashMap<>(byId);
    withStranger.put(stranger.getId(), stranger);
    assertThat(codes(DepthChartPolicy.validate(Map.of(ChartSlot.QB, List.of(stranger.getId())), withStranger, club.getId(), 46)))
        .as("jogador de outra franquia").contains(DepthChartPolicy.UNAVAILABLE);
    Map<ChartSlot, List<UUID>> all = mutable(DepthChartPolicy.defaults(roster, 46));
    List<UUID> everyone = roster.stream().map(Player::getId).toList();
    all.put(ChartSlot.FB, new ArrayList<>(everyone.subList(0, 10)));
    all.put(ChartSlot.THIRD_DOWN_BACK, new ArrayList<>(everyone.subList(10, 20)));
    all.put(ChartSlot.KR, new ArrayList<>(everyone.subList(20, 30)));
    all.put(ChartSlot.PR, new ArrayList<>(everyone.subList(30, 40)));
    all.put(ChartSlot.H, new ArrayList<>(everyone.subList(40, 50)));
    assertThat(codes(DepthChartPolicy.validate(all, byId, club.getId(), 46))).contains(DepthChartPolicy.TOO_MANY_ACTIVES);
  }

  @Test
  void fora_de_posicao_e_aviso_e_posicao_natural_ou_secundaria_nunca_avisa() {
    Player wr = first(Position.WR);
    Player k = first(Position.K);
    k.setAttributes(PlayerAttributes.uniform(40).with(AttributeKey.KICK_POWER, 95).with(AttributeKey.KICK_ACCURACY, 95));
    assertThat(DepthChartPolicy.overallAt(k, ChartSlot.QB)).isLessThan(k.ratingAt(Position.K).overall() - DepthChartPolicy.OUT_OF_POSITION_MARGIN);
    assertThat(DepthChartPolicy.outOfPosition(k, ChartSlot.QB)).isTrue();
    assertThat(DepthChartPolicy.outOfPosition(wr, ChartSlot.WR)).isFalse();
    assertThat(DepthChartPolicy.outOfPosition(wr, ChartSlot.KR)).isFalse();
    assertThat(DepthChartPolicy.outOfPosition(first(Position.DT), ChartSlot.KR)).isTrue();
    assertThat(DepthChartPolicy.outOfPosition(first(Position.P), ChartSlot.H)).isFalse();
    k.setSecondaryPosition(Position.QB);
    assertThat(DepthChartPolicy.outOfPosition(k, ChartSlot.QB)).isFalse();
    k.setSecondaryPosition(null);
    Map<ChartSlot, List<UUID>> chart = mutable(DepthChartPolicy.defaults(roster, 46));
    chart.get(ChartSlot.QB).add(k.getId());
    DepthChartPolicy.Result r = DepthChartPolicy.validate(chart, byId, club.getId(), 46);
    assertThat(r.errors()).isEmpty();
    assertThat(r.warnings()).anySatisfy(w -> {
      assertThat(w.code()).isEqualTo(DepthChartPolicy.OUT_OF_POSITION);
      assertThat(w.slot()).isEqualTo(ChartSlot.QB);
      assertThat(w.playerId()).isEqualTo(k.getId());
    });
  }

  @Test
  void conversao_para_o_motor_e_texto_das_vagas() {
    Map<ChartSlot, List<UUID>> chart = DepthChartPolicy.defaults(roster, 46);
    var engine = DepthChartPolicy.toEngine(chart);
    assertThat(engine.get(DepthSlot.QB)).isEqualTo(chart.get(ChartSlot.QB));
    assertThat(engine.get(DepthSlot.KR)).isEqualTo(chart.get(ChartSlot.KR));
    assertThat(engine.slots()).doesNotContainKey(null);
    assertThat(ChartSlot.parse("3rd-down-back")).isEqualTo(ChartSlot.THIRD_DOWN_BACK);
    assertThat(ChartSlot.parse(" holder ")).isEqualTo(ChartSlot.H);
    assertThat(ChartSlot.parse("long_snapper")).isEqualTo(ChartSlot.LS);
    assertThat(ChartSlot.parse("xx")).isNull();
    assertThat(ChartSlot.THIRD_DOWN_BACK.engineSlot()).isNull();
    for (DepthSlot s : DepthSlot.values()) assertThat(ChartSlot.of(s).engineSlot()).isEqualTo(s);
  }
}
