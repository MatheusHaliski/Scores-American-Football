package br.com.velion.scoresaf.domain.career;

import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Regras de movimento do elenco (RF4, RF56), puras, sobre {@link LeagueRules}: o elenco de 53 ({@code rosterMax}, ACTIVE + INACTIVE),
 * o practice squad ({@code practiceSquadMax}) e a lista de lesionados (IR exige lesão de pelo menos {@code irMinWeeks} semanas; a volta
 * exige {@code irMinWeeks} semanas na lista, jogador recuperado, vaga nos 53 e retorno disponível em {@code irReturnsPerSeason}).
 * Practice squad: jogadores com até {@link #PRACTICE_SQUAD_MAX_EXPERIENCE} anos de experiência, mais
 * {@link #PRACTICE_SQUAD_VETERAN_SLOTS} vagas de veterano (provisório, Q-23). Dispensar não passa por aqui (vai a waivers).
 */
public final class RosterRules {
  public static final int PRACTICE_SQUAD_MAX_EXPERIENCE = 3;
  public static final int PRACTICE_SQUAD_VETERAN_SLOTS = 6;
  /** Situações que a franquia pode escolher por movimento (dispensa é outro caso de uso). */
  public static final Set<RosterStatus> MOVABLE = EnumSet.of(RosterStatus.ACTIVE, RosterStatus.INACTIVE, RosterStatus.PRACTICE_SQUAD, RosterStatus.IR);

  private RosterRules() {}

  public record Counts(int roster, int active, int inactive, int practiceSquad, int practiceSquadVeterans, int injuredReserve) {
    public static Counts of(Collection<Player> clubPlayers) {
      int active = 0, inactive = 0, ps = 0, vets = 0, ir = 0;
      for (Player p : clubPlayers) {
        switch (p.getRosterStatus()) {
          case ACTIVE -> active++;
          case INACTIVE -> inactive++;
          case PRACTICE_SQUAD -> { ps++; if (p.getExperienceYears() > PRACTICE_SQUAD_MAX_EXPERIENCE) vets++; }
          case IR -> ir++;
          default -> { }
        }
      }
      return new Counts(active + inactive, active, inactive, ps, vets, ir);
    }
  }

  /** Movimento recusado: código de erro ({@code error.<code>}) e parâmetros da mensagem. */
  public record Violation(String code, List<Object> args) {
    static Violation of(String code, Object... args) { return new Violation(code, List.of(args)); }
  }

  /**
   * Confere o movimento de {@code p} para {@code to}; {@code null} = permitido. {@code weeksOnIr}: semanas desde que entrou na IR;
   * {@code irReturnsUsed}: retornos da IR já usados na temporada.
   */
  public static Violation checkMove(Player p, RosterStatus to, Counts c, LeagueRules rules, int weeksOnIr, int irReturnsUsed) {
    RosterStatus from = p.getRosterStatus();
    if (to == null || !MOVABLE.contains(to) || !MOVABLE.contains(from)) return Violation.of("ROSTER_MOVE_NOT_ALLOWED", String.valueOf(from), String.valueOf(to));
    if (from == to) return null;
    switch (to) {
      case ACTIVE, INACTIVE -> {
        if (from.countsTowardRoster()) return null;
        if (from == RosterStatus.IR) {
          if (weeksOnIr < rules.getIrMinWeeks()) return Violation.of("IR_RETURN_TOO_EARLY", rules.getIrMinWeeks(), weeksOnIr);
          if (p.getInjuryWeeksOut() > 0) return Violation.of("IR_STILL_INJURED", p.getInjuryWeeksOut());
          if (irReturnsUsed >= rules.getIrReturnsPerSeason()) return Violation.of("IR_RETURNS_EXHAUSTED", rules.getIrReturnsPerSeason());
        }
        if (c.roster() >= rules.getRosterMax()) return Violation.of("ROSTER_FULL", rules.getRosterMax());
        return null;
      }
      case PRACTICE_SQUAD -> {
        if (!from.countsTowardRoster()) return Violation.of("ROSTER_MOVE_NOT_ALLOWED", from.name(), to.name());
        if (c.practiceSquad() >= rules.getPracticeSquadMax()) return Violation.of("PRACTICE_SQUAD_FULL", rules.getPracticeSquadMax());
        if (p.getExperienceYears() > PRACTICE_SQUAD_MAX_EXPERIENCE && c.practiceSquadVeterans() >= PRACTICE_SQUAD_VETERAN_SLOTS) {
          return Violation.of("PRACTICE_SQUAD_INELIGIBLE", PRACTICE_SQUAD_MAX_EXPERIENCE, PRACTICE_SQUAD_VETERAN_SLOTS);
        }
        return null;
      }
      case IR -> {
        if (!from.countsTowardRoster()) return Violation.of("ROSTER_MOVE_NOT_ALLOWED", from.name(), to.name());
        if (p.getInjuryWeeksOut() < rules.getIrMinWeeks()) return Violation.of("IR_NOT_ALLOWED", rules.getIrMinWeeks());
        return null;
      }
      default -> { return Violation.of("ROSTER_MOVE_NOT_ALLOWED", from.name(), to.name()); }
    }
  }
}
