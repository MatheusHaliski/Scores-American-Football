package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.service.market.MarketService;
import br.com.velion.scoresaf.domain.market.ContractDemand;
import br.com.velion.scoresaf.domain.market.ContractMath;
import br.com.velion.scoresaf.domain.market.ContractOffer;
import br.com.velion.scoresaf.domain.market.MarketRules;
import br.com.velion.scoresaf.domain.career.RosterRules;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Renovação da IA no avanço da temporada, antes da free agency abrir (bloco 7a; substitui a renovação automática provisória da Q-42,
 * D-120 a D-122; D-138). A franquia escolhe quem renovar entre os próprios contratos que terminaram, na ordem da necessidade
 * ({@link #choose}, determinística, sem sorteio):
 *
 * <ol>
 *   <li><b>53, necessidade do depth chart</b>: posição por posição, até o mínimo de titulares que o depth chart exige
 *   ({@link ChartSlot#minStarters()}: 1 QB, 3 WR, 2 DE, 2 DT, 2 OLB, 2 CB, K, P, LS…).</li>
 *   <li><b>53, composição de antes</b>: posição por posição, até o número que a franquia tinha nos 53 antes dos contratos acabarem.</li>
 *   <li><b>53, o resto</b>: as vagas que sobrarem, pelo melhor jogador.</li>
 *   <li><b>Practice squad</b>: quem estava no practice squad primeiro, respeitando a elegibilidade ({@link RosterRules}).</li>
 * </ol>
 * Em cada passo, o melhor é o de maior overall, depois o mais novo, depois o nome e o id. O contrato é o que o jogador pede
 * ({@link ContractDemand}, com a moral dele e sem desconto: anos pela idade, luvas e garantias pelo overall); no practice squad, 1 ano
 * pelo salário do practice squad. <b>Teto rígido</b>: a franquia só renova quem cabe no teto da temporada nova deixando a reserva das
 * vagas que ainda faltam (cada uma pelo mínimo); quem não cabe vai para a free agency, onde a IA completa o elenco.
 */
@Component
public class MarketAiContractRenewal implements AiContractRenewal {

  /** Escolha da renovação: quem volta aos 53 e quem volta ao practice squad, na ordem da escolha. */
  public record Choice(List<Player> roster, List<Player> practiceSquad) {}

  static final Comparator<Player> BEST = MarketService.BEST;

  @Override
  public List<UUID> renew(UUID clubId, List<Player> staying, List<Expired> expired, int newSeason, LeagueRules rules, long salaryCap, long deadMoney) {
    Choice c = choose(staying, expired, rules);
    int prorate = rules.getBonusProrationMaxYears();
    long committed = deadMoney;
    int roster = 0, practice = 0;
    for (Player p : staying) {
      committed += CapSheet.capHit(p, newSeason, rules);
      if (p.getRosterStatus().countsTowardRoster()) roster++;
      if (p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD) practice++;
    }
    List<UUID> out = new ArrayList<>();
    for (Player p : c.roster()) {
      ContractOffer ask = ContractDemand.ask(MarketService.profile(p, p.getMorale(), null), 0);
      PlayerContract contract = ContractMath.sign(newSeason, ask);
      long hit = contract.capHit(0, prorate);
      if (rules.isHardCap() && committed + hit + reserve(rules, roster + 1, practice) > salaryCap) continue;
      p.setContract(contract);
      p.setRosterStatus(RosterStatus.ACTIVE);
      committed += hit;
      roster++;
      out.add(p.getId());
    }
    for (Player p : c.practiceSquad()) {
      PlayerContract contract = ContractMath.practice(newSeason);
      long hit = contract.capHit(0, prorate);
      if (rules.isHardCap() && committed + hit + reserve(rules, roster, practice + 1) > salaryCap) continue;
      p.setContract(contract);
      p.setRosterStatus(RosterStatus.PRACTICE_SQUAD);
      committed += hit;
      practice++;
      out.add(p.getId());
    }
    return out;
  }

  /** Reserva das vagas que faltam (pelo mínimo dos 53 e pelo salário do practice squad). */
  static long reserve(LeagueRules rules, int roster, int practice) {
    return Math.max(0, rules.getRosterMax() - roster) * MarketRules.MIN_SALARY + Math.max(0, rules.getPracticeSquadMax() - practice) * MarketRules.PRACTICE_SALARY;
  }

  /** A escolha (pura, sem mexer nos jogadores). */
  public static Choice choose(List<Player> staying, List<Expired> expired, LeagueRules rules) {
    List<Expired> pool = new ArrayList<>(expired);
    pool.sort(Comparator.comparing(Expired::player, BEST));
    Set<Player> taken = new LinkedHashSet<>();
    Map<Position, Integer> now = new EnumMap<>(Position.class), before = new EnumMap<>(Position.class);
    int roster = 0, practice = 0, veterans = 0;
    for (Player p : staying) {
      if (p.getRosterStatus().countsTowardRoster()) { roster++; now.merge(p.getPosition(), 1, Integer::sum); before.merge(p.getPosition(), 1, Integer::sum); }
      if (p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD) {
        practice++;
        if (p.getExperienceYears() > RosterRules.PRACTICE_SQUAD_MAX_EXPERIENCE) veterans++;
      }
    }
    for (Expired e : expired) if (e.from().countsTowardRoster()) before.merge(e.player().getPosition(), 1, Integer::sum);
    List<Player> toRoster = new ArrayList<>();
    // 1. mínimo de titulares do depth chart, posição por posição
    for (ChartSlot slot : ChartSlot.values()) {
      if (slot.isRole() || slot.minStarters() == 0) continue;
      Position pos = slot.position();
      while (roster < rules.getRosterMax() && now.getOrDefault(pos, 0) < slot.minStarters()) {
        Player p = best(pool, taken, pos);
        if (p == null) break;
        taken.add(p); toRoster.add(p); roster++; now.merge(pos, 1, Integer::sum);
      }
    }
    // 2. a composição dos 53 de antes
    for (Position pos : Position.values()) {
      while (roster < rules.getRosterMax() && now.getOrDefault(pos, 0) < before.getOrDefault(pos, 0)) {
        Player p = best(pool, taken, pos);
        if (p == null) break;
        taken.add(p); toRoster.add(p); roster++; now.merge(pos, 1, Integer::sum);
      }
    }
    // 3. as vagas que sobraram, pelo melhor
    while (roster < rules.getRosterMax()) {
      Player p = best(pool, taken, null);
      if (p == null) break;
      taken.add(p); toRoster.add(p); roster++;
    }
    // 4. practice squad: quem estava nele primeiro, com a elegibilidade
    List<Expired> psOrder = new ArrayList<>(pool);
    psOrder.sort(Comparator.comparing((Expired e) -> e.from() == RosterStatus.PRACTICE_SQUAD ? 0 : 1).thenComparing(Expired::player, BEST));
    List<Player> toPractice = new ArrayList<>();
    for (Expired e : psOrder) {
      if (practice >= rules.getPracticeSquadMax()) break;
      Player p = e.player();
      if (taken.contains(p)) continue;
      boolean veteran = p.getExperienceYears() > RosterRules.PRACTICE_SQUAD_MAX_EXPERIENCE;
      if (veteran && veterans >= RosterRules.PRACTICE_SQUAD_VETERAN_SLOTS) continue;
      taken.add(p); toPractice.add(p); practice++;
      if (veteran) veterans++;
    }
    return new Choice(List.copyOf(toRoster), List.copyOf(toPractice));
  }

  private static Player best(List<Expired> pool, Set<Player> taken, Position pos) {
    for (Expired e : pool) if (!taken.contains(e.player()) && (pos == null || e.player().getPosition() == pos)) return e.player();
    return null;
  }
}
