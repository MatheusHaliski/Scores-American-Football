package br.com.velion.scoresaf.application.service;

import br.com.velion.scoresaf.application.seed.DefaultWorldFactory;
import br.com.velion.scoresaf.domain.career.RosterRules;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.match.ChartSlot;
import br.com.velion.scoresaf.domain.shared.ContractType;
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
 * Renovação automática da IA até o bloco 7 (Q-42, D-120/D-121): a franquia renova só o necessário para voltar aos limites de
 * {@code LeagueRules} — {@code rosterMax} (53) no elenco principal e {@code practiceSquadMax} (16) no practice squad —, escolhendo entre
 * os próprios contratos que terminaram, por regra determinística (sem sorteio; a ordem da entrada não importa):
 *
 * <ol>
 *   <li><b>53, necessidade do depth chart</b>: posição por posição, até o mínimo de titulares que o depth chart exige
 *   ({@link ChartSlot#minStarters()}: 1 QB, 3 WR, 2 DE, 2 DT, 2 OLB, 2 CB, K, P, LS…).</li>
 *   <li><b>53, composição de antes</b>: posição por posição, até o número que a franquia tinha nos 53 antes dos contratos acabarem.</li>
 *   <li><b>53, o resto</b>: as vagas que sobrarem, pelo melhor jogador.</li>
 *   <li><b>Practice squad</b>: quem estava no practice squad primeiro, respeitando a elegibilidade ({@link RosterRules}: até 3 anos de
 *   experiência, mais 6 vagas de veterano).</li>
 * </ol>
 * Em cada passo, o melhor é o de maior overall, depois o mais novo, depois o nome e o id. Contrato novo: 1 ano a partir da nova
 * temporada; nos 53, o salário da fórmula do mundo semente pelo overall de agora ({@link DefaultWorldFactory#yearOneSalary}), sem
 * bônus nem garantia; no practice squad, o salário do practice squad. O teto salarial não bloqueia a renovação (como o resto do teto,
 * fica para o bloco 7).
 */
@Component
public class AutoAiContractRenewal implements AiContractRenewal {

  /** Escolha da renovação: quem volta aos 53 e quem volta ao practice squad, na ordem da escolha. */
  public record Choice(List<Player> roster, List<Player> practiceSquad) {}

  static final Comparator<Player> BEST = Comparator.comparingInt((Player p) -> -p.getOverall()).thenComparingInt(Player::getAge)
      .thenComparing(Player::getName).thenComparing(p -> p.getId().toString());

  @Override
  public List<UUID> renew(UUID clubId, List<Player> staying, List<Expired> expired, int newSeason, LeagueRules rules) {
    Choice c = choose(staying, expired, rules);
    List<UUID> out = new ArrayList<>();
    for (Player p : c.roster()) {
      long salary = DefaultWorldFactory.yearOneSalary(p.getPosition(), p.getOverall());
      p.setContract(PlayerContract.builder().startSeason(newSeason).baseSalaryByYear(List.of(salary * 100L))
          .type(DefaultWorldFactory.minimumSalary(salary) ? ContractType.MINIMUM : ContractType.VETERAN).build());
      p.setRosterStatus(RosterStatus.ACTIVE);
      out.add(p.getId());
    }
    for (Player p : c.practiceSquad()) {
      p.setContract(PlayerContract.builder().startSeason(newSeason).baseSalaryByYear(List.of(DefaultWorldFactory.practiceSalary() * 100L))
          .type(ContractType.PRACTICE).build());
      p.setRosterStatus(RosterStatus.PRACTICE_SQUAD);
      out.add(p.getId());
    }
    return out;
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
