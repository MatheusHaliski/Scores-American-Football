package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.port.out.persistence.DepthChartRepository;
import br.com.velion.scoresaf.application.port.out.persistence.DepthChartSlotRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.usecase.rf42.SavedDepthCharts;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.match.DepthChart;
import br.com.velion.scoresaf.domain.match.DepthChartPolicy;
import br.com.velion.scoresaf.domain.match.DepthChartSlot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura e gravação do depth chart salvo (RF60): o padrão nasce do automático do motor (bloco 5) na criação da carreira; gravar
 * troca todas as vagas (DELETE em lote + INSERT); dispensa e troca de situação tiram o jogador das vagas. Também é a fonte do
 * depth chart que o motor usa ({@link SavedDepthCharts}).
 */
@Service
public class DepthChartService implements SavedDepthCharts {
  private final DepthChartRepository charts;
  private final DepthChartSlotRepository slots;
  private final PlayerRepository players;

  public DepthChartService(DepthChartRepository charts, DepthChartSlotRepository slots, PlayerRepository players) {
    this.charts = charts; this.slots = slots; this.players = players;
  }

  public static List<Player> eligible(List<Player> roster, UUID clubId) {
    return roster.stream().filter(p -> DepthChartPolicy.available(p, clubId))
        .sorted(Comparator.comparingInt(Player::getOverall).reversed().thenComparingInt(Player::getJerseyNumber)).toList();
  }

  public static Map<UUID, Player> byId(List<Player> roster) {
    Map<UUID, Player> m = new LinkedHashMap<>();
    roster.forEach(p -> m.put(p.getId(), p));
    return m;
  }

  /** Depth chart padrão da franquia do usuário (criação da carreira, ou quando ainda não existe). */
  @Transactional
  public DepthChart createDefault(CareerSave save, Club club, int gameDayActives) {
    List<Player> roster = players.findByClubIdOrderByOverallDesc(club.getId());
    Map<ChartSlot, List<UUID>> chart = DepthChartPolicy.defaults(eligible(roster, club.getId()), gameDayActives);
    DepthChart dc = new DepthChart();
    dc.setCareer(save); dc.setClub(club);
    dc.touch(DepthChartPolicy.validate(chart, byId(roster), club.getId(), gameDayActives).valid());
    charts.saveAndFlush(dc);
    write(dc, chart, byId(roster));
    return dc;
  }

  @Transactional
  public DepthChart getOrCreate(CareerSave save, Club club, int gameDayActives) {
    return charts.findByCareerIdAndClubId(save.getId(), club.getId()).orElseGet(() -> createDefault(save, club, gameDayActives));
  }

  /** Vagas salvas, na ordem das vagas e da profundidade (todas as vagas presentes, vazias quando não há ninguém). */
  public Map<ChartSlot, List<UUID>> read(DepthChart chart) {
    Map<ChartSlot, List<UUID>> out = new EnumMap<>(ChartSlot.class);
    for (ChartSlot s : ChartSlot.values()) out.put(s, new ArrayList<>());
    slots.findByDepthChartId(chart.getId()).stream()
        .sorted(Comparator.comparingInt((DepthChartSlot s) -> s.getSlot().ordinal()).thenComparingInt(DepthChartSlot::getDepth))
        .forEach(s -> out.get(s.getSlot()).add(s.getPlayer().getId()));
    return out;
  }

  /** Troca todas as vagas pelas de {@code chart} (os jogadores precisam estar em {@code players}). */
  public void write(DepthChart dc, Map<ChartSlot, List<UUID>> chart, Map<UUID, Player> players) {
    slots.deleteByDepthChartId(dc.getId());
    List<DepthChartSlot> rows = new ArrayList<>();
    chart.forEach((slot, ids) -> {
      int depth = 0;
      for (UUID id : ids) {
        Player p = players.get(id);
        if (p != null) rows.add(DepthChartSlot.of(dc, slot, ++depth, p));
      }
    });
    slots.saveAll(rows);
  }

  /** Tira o jogador de todas as vagas do depth chart da franquia (dispensa, practice squad, IR, inativo). */
  @Transactional
  public void removePlayer(CareerSave save, Club club, UUID playerId, int gameDayActives) {
    Optional<DepthChart> found = charts.findByCareerIdAndClubId(save.getId(), club.getId());
    if (found.isEmpty()) return;
    DepthChart dc = found.get();
    Map<ChartSlot, List<UUID>> chart = read(dc);
    boolean changed = false;
    for (List<UUID> ids : chart.values()) changed |= ids.remove(playerId);
    if (!changed) return;
    List<Player> roster = players.findByClubIdOrderByOverallDesc(club.getId());
    Map<UUID, Player> byId = byId(roster);
    write(dc, chart, byId);
    dc.touch(DepthChartPolicy.validate(chart, byId, club.getId(), gameDayActives).valid());
    charts.saveAndFlush(dc);
  }

  @Override
  @Transactional(readOnly = true)
  public br.com.velion.scoresaf.domain.engine.model.DepthChart forClub(UUID clubId) {
    return charts.findFirstByClubId(clubId).map(dc -> DepthChartPolicy.toEngine(read(dc))).orElse(null);
  }
}
