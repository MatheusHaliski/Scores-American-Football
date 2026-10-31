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
 * {@link #PRACTICE_SQUAD_VETERAN_SLOTS} vagas de veterano (confirmado pelo PO na Q-23). Sair dos 53 para o practice squad passa por
 * waivers (Q-23, D-78): o movimento é aceito para PRACTICE_SQUAD, mas o jogador fica em {@link RosterStatus#PRACTICE_SQUAD_WAIVERS}
 * ({@link #landingStatus}) até a semana avançar, com a vaga do practice squad (e a de veterano) reservada. Quem está em waivers não se
 * move por aqui. Dispensar não passa por aqui (vai a waivers como WAIVED).
 */
public final class RosterRules {
  public static final int PRACTICE_SQUAD_MAX_EXPERIENCE = 3;
  public static final int PRACTICE_SQUAD_VETERAN_SLOTS = 6;
  /** Situações que a franquia pode escolher por movimento (dispensa é outro caso de uso). */
  public static final Set<RosterStatus> MOVABLE = EnumSet.of(RosterStatus.ACTIVE, RosterStatus.INACTIVE, RosterStatus.PRACTICE_SQUAD, RosterStatus.IR);

  private RosterRules() {}

  /**
   * Contagem do elenco da franquia. {@code waivers}: expostos a waivers rumo ao practice squad (não contam nos 53 nem no practice
   * squad, mas reservam a vaga: {@link #practiceSquadReserved()}); {@code waiverVeterans}: quantos deles são veteranos.
   */
  public record Counts(int roster, int active, int inactive, int practiceSquad, int practiceSquadVeterans, int injuredReserve, int waivers,
                       int waiverVeterans) {
    public static Counts of(Collection<Player> clubPlayers) {
      int active = 0, inactive = 0, ps = 0, vets = 0, ir = 0, waivers = 0, waiverVets = 0;
      for (Player p : clubPlayers) {
        boolean veteran = p.getExperienceYears() > PRACTICE_SQUAD_MAX_EXPERIENCE;
        switch (p.getRosterStatus()) {
          case ACTIVE -> active++;
          case INACTIVE -> inactive++;
          case PRACTICE_SQUAD -> { ps++; if (veteran) vets++; }
          case PRACTICE_SQUAD_WAIVERS -> { waivers++; if (veteran) waiverVets++; }
          case IR -> ir++;
          default -> { }
        }
      }
      return new Counts(active + inactive, active, inactive, ps, vets, ir, waivers, waiverVets);
    }

    /** Vagas do practice squad ocupadas ou reservadas (quem está em waivers entra nele se não for reivindicado). */
    public int practiceSquadReserved() { return practiceSquad + waivers; }

    /** Vagas de veterano ocupadas ou reservadas. */
    public int veteransReserved() { return practiceSquadVeterans + waiverVeterans; }
  }

  /**
   * Situação em que o jogador fica depois de um movimento aceito para {@code requested}: dos 53 para o practice squad, ele vai a
   * waivers ({@link RosterStatus#PRACTICE_SQUAD_WAIVERS}, Q-23); o resto fica como pedido.
   */
  public static RosterStatus landingStatus(RosterStatus from, RosterStatus requested) {
    return requested == RosterStatus.PRACTICE_SQUAD && from != null && from.countsTowardRoster() ? RosterStatus.PRACTICE_SQUAD_WAIVERS : requested;
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
        if (c.practiceSquadReserved() >= rules.getPracticeSquadMax()) return Violation.of("PRACTICE_SQUAD_FULL", rules.getPracticeSquadMax());
        if (p.getExperienceYears() > PRACTICE_SQUAD_MAX_EXPERIENCE && c.veteransReserved() >= PRACTICE_SQUAD_VETERAN_SLOTS) {
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

  /**
   * Confere a entrada de um jogador de fora (agente livre contratado ou reivindicado em waivers, bloco 7a) em {@code to}; {@code null} =
   * permitido. Nos 53 (ACTIVE): vaga em {@code rosterMax}. No practice squad: vaga (contando as reservadas) e a elegibilidade (até 3
   * anos de experiência ou uma das 6 vagas de veterano).
   */
  public static Violation checkSigning(Player p, RosterStatus to, Counts c, LeagueRules rules) {
    if (to == RosterStatus.ACTIVE) return c.roster() >= rules.getRosterMax() ? Violation.of("ROSTER_FULL", rules.getRosterMax()) : null;
    if (to == RosterStatus.PRACTICE_SQUAD) {
      if (c.practiceSquadReserved() >= rules.getPracticeSquadMax()) return Violation.of("PRACTICE_SQUAD_FULL", rules.getPracticeSquadMax());
      if (p.getExperienceYears() > PRACTICE_SQUAD_MAX_EXPERIENCE && c.veteransReserved() >= PRACTICE_SQUAD_VETERAN_SLOTS) {
        return Violation.of("PRACTICE_SQUAD_INELIGIBLE", PRACTICE_SQUAD_MAX_EXPERIENCE, PRACTICE_SQUAD_VETERAN_SLOTS);
      }
      return null;
    }
    return Violation.of("ROSTER_MOVE_NOT_ALLOWED", String.valueOf(p.getRosterStatus()), String.valueOf(to));
  }
}
