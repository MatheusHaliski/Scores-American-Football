package br.com.velion.scoresaf.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.shared.ContractType;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Renovação automática da IA (Q-42, D-120/D-121): renova só o que falta para os limites de {@code LeagueRules}, primeiro o mínimo de
 * titulares do depth chart (mesmo com overall baixo), depois a composição de antes e o melhor jogador; o practice squad com quem estava
 * nele e respeitando as vagas de veterano; a escolha não depende da ordem da entrada; o contrato novo é de 1 ano.
 */
class AutoAiContractRenewalTest {
  private static final LeagueRules RULES = LeagueRules.pro().toBuilder().rosterMax(27).practiceSquadMax(3).build();

  private static Player p(String name, Position pos, int overall, int age, int experience, RosterStatus status) {
    Player p = new Player();
    p.setName(name); p.setPosition(pos); p.setOverall(overall); p.setAge(age); p.setExperienceYears(experience); p.setRosterStatus(status);
    return p;
  }

  /** 23 nos 53: o mínimo de titulares de cada vaga, menos o K e com um WR a menos. */
  private static List<Player> staying() {
    List<Player> out = new ArrayList<>();
    Object[][] need = {{Position.QB, 1}, {Position.RB, 1}, {Position.WR, 2}, {Position.TE, 1}, {Position.LT, 1}, {Position.LG, 1}, {Position.C, 1},
        {Position.RG, 1}, {Position.RT, 1}, {Position.DE, 2}, {Position.DT, 2}, {Position.OLB, 2}, {Position.MLB, 1}, {Position.CB, 2}, {Position.FS, 1},
        {Position.SS, 1}, {Position.P, 1}, {Position.LS, 1}};
    for (Object[] n : need) for (int i = 0; i < (Integer) n[1]; i++) out.add(p("Fica " + n[0] + i, (Position) n[0], 70, 27, 5, RosterStatus.ACTIVE));
    return out;
  }

  private static List<AiContractRenewal.Expired> expired() {
    return List.of(new AiContractRenewal.Expired(p("Chutador", Position.K, 50, 30, 8, RosterStatus.ACTIVE), RosterStatus.ACTIVE),
        new AiContractRenewal.Expired(p("Recebedor Bom", Position.WR, 70, 26, 4, RosterStatus.ACTIVE), RosterStatus.ACTIVE),
        new AiContractRenewal.Expired(p("Recebedor Médio", Position.WR, 65, 26, 4, RosterStatus.ACTIVE), RosterStatus.ACTIVE),
        new AiContractRenewal.Expired(p("Quarterback Reserva", Position.QB, 80, 33, 10, RosterStatus.ACTIVE), RosterStatus.ACTIVE),
        new AiContractRenewal.Expired(p("Linebacker Sobra", Position.MLB, 85, 24, 2, RosterStatus.ACTIVE), RosterStatus.ACTIVE),
        new AiContractRenewal.Expired(p("Corredor Treino", Position.RB, 60, 23, 1, RosterStatus.PRACTICE_SQUAD), RosterStatus.PRACTICE_SQUAD),
        new AiContractRenewal.Expired(p("Corner Veterano", Position.CB, 55, 29, 5, RosterStatus.PRACTICE_SQUAD), RosterStatus.PRACTICE_SQUAD));
  }

  private static List<String> names(List<Player> l) { return l.stream().map(Player::getName).toList(); }

  @Test
  void renova_so_o_necessario_pela_necessidade_do_depth_chart_depois_composicao_e_melhor() {
    AutoAiContractRenewal.Choice c = AutoAiContractRenewal.choose(staying(), expired(), RULES);
    // 4 vagas nos 53: primeiro os mínimos do depth chart na ordem das posições do chart (o melhor WR, que completa os 3, e o K, apesar
    // do overall); depois a composição de antes, na ordem das posições (o QB, depois o outro WR); o MLB de overall 85 sobra porque a
    // franquia já tinha o MLB que precisava e as vagas acabaram
    assertThat(names(c.roster())).containsExactly("Recebedor Bom", "Chutador", "Quarterback Reserva", "Recebedor Médio");
    assertThat(names(c.practiceSquad())).containsExactly("Corredor Treino", "Corner Veterano", "Linebacker Sobra");
  }

  @Test
  void vaga_que_a_composicao_de_antes_nao_preenche_vai_para_o_melhor_que_sobrou() {
    // a franquia estava abaixo do limite antes do fim dos contratos (28 nos 53, com o limite em 29): depois do mínimo do depth chart e
    // da composição de antes (o QB, o outro WR e o MLB), a vaga que sobra vai para o melhor dos que restam, mesmo vindo do practice squad
    AutoAiContractRenewal.Choice c = AutoAiContractRenewal.choose(staying(), expired(), RULES.toBuilder().rosterMax(29).build());
    assertThat(names(c.roster())).containsExactly("Recebedor Bom", "Chutador", "Quarterback Reserva", "Recebedor Médio", "Linebacker Sobra", "Corredor Treino");
    assertThat(names(c.practiceSquad())).containsExactly("Corner Veterano");
  }

  @Test
  void elenco_completo_nao_renova_e_veterano_so_entra_com_vaga_de_veterano() {
    List<Player> full = new ArrayList<>(staying());
    for (int i = 0; i < 4; i++) full.add(p("Extra " + i, Position.TE, 60, 27, 5, RosterStatus.ACTIVE));
    for (int i = 0; i < 6; i++) full.add(p("Veterano treino " + i, Position.TE, 50, 28, 6, RosterStatus.PRACTICE_SQUAD));
    LeagueRules bigPractice = RULES.toBuilder().practiceSquadMax(10).build();
    AutoAiContractRenewal.Choice c = AutoAiContractRenewal.choose(full, expired(), bigPractice);
    assertThat(c.roster()).isEmpty();
    // 6 veteranos já ocupam as vagas de veterano: só entram os de até 3 anos de experiência
    assertThat(c.practiceSquad()).extracting(Player::getName).containsExactly("Corredor Treino", "Linebacker Sobra");
  }

  @Test
  void a_escolha_nao_depende_da_ordem_da_entrada() {
    List<Player> s = staying();
    List<AiContractRenewal.Expired> e = expired();
    AutoAiContractRenewal.Choice first = AutoAiContractRenewal.choose(s, e, RULES);
    Random shuffle = new Random(7);
    for (int i = 0; i < 20; i++) {
      List<Player> s2 = new ArrayList<>(s);
      List<AiContractRenewal.Expired> e2 = new ArrayList<>(e);
      Collections.shuffle(s2, shuffle);
      Collections.shuffle(e2, shuffle);
      AutoAiContractRenewal.Choice again = AutoAiContractRenewal.choose(s2, e2, RULES);
      assertThat(again.roster()).isEqualTo(first.roster());
      assertThat(again.practiceSquad()).isEqualTo(first.practiceSquad());
    }
  }

  @Test
  void contrato_novo_de_1_ano_pela_formula_salarial_e_situacao_de_volta() {
    List<AiContractRenewal.Expired> e = expired();
    List<UUID> renewed = new AutoAiContractRenewal().renew(UUID.randomUUID(), staying(), e, 2027, RULES);
    assertThat(renewed).hasSize(7);
    Player qb = e.get(3).player(), rb = e.get(5).player();
    assertThat(qb.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(qb.getContract().getStartSeason()).isEqualTo(2027);
    assertThat(qb.getContract().years()).isEqualTo(1);
    assertThat(qb.getContract().getBaseSalaryByYear()).containsExactly(br.com.velion.scoresaf.application.seed.DefaultWorldFactory.yearOneSalary(Position.QB, 80) * 100L);
    assertThat(rb.getRosterStatus()).isEqualTo(RosterStatus.PRACTICE_SQUAD);
    assertThat(rb.getContract().getType()).isEqualTo(ContractType.PRACTICE);
  }
}
