package br.com.velion.scoresaf.application.usecase.rf11;

import br.com.velion.scoresaf.application.port.out.persistence.CareerSaveRepository;
import br.com.velion.scoresaf.application.port.out.persistence.RosterMoveRepository;
import br.com.velion.scoresaf.application.service.CareerDeletion;
import br.com.velion.scoresaf.application.usecase.rf46.AccountDataCollector;
import br.com.velion.scoresaf.application.usecase.rf46.AccountDataContributor;
import br.com.velion.scoresaf.application.usecase.rf46.AccountErasureHandler;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.career.RosterMove;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * As carreiras na exportação e na eliminação da conta (RF46 CA07, LGPD Art. 18, II, V e VI; D-12): a exportação ganha a seção
 * {@code careers} (uma linha por carreira: nome, slot, situação, head coach, franquia, liga, temporada, semente, datas) e a seção
 * {@code careerRosterMoves} (os movimentos do elenco que o titular fez); a eliminação apaga cada carreira inteira, com o mundo SAVE
 * ({@link CareerDeletion}).
 */
@Component
public class CareerAccountData implements AccountDataContributor, AccountErasureHandler {
  private final CareerSaveRepository saves;
  private final RosterMoveRepository moves;
  private final CareerDeletion deletion;

  public CareerAccountData(CareerSaveRepository saves, RosterMoveRepository moves, CareerDeletion deletion) {
    this.saves = saves; this.moves = moves; this.deletion = deletion;
  }

  @Override
  @Transactional(readOnly = true)
  public void contribute(UUID userId, Map<String, Object> export) {
    List<Map<String, Object>> careers = new ArrayList<>();
    List<Map<String, Object>> rosterMoves = new ArrayList<>();
    for (CareerSave s : saves.findByUserIdOrderByCreatedAtAsc(userId)) {
      careers.add(AccountDataCollector.row("id", String.valueOf(s.getId()), "name", s.getName(), "slot", s.getSlot(), "status", s.getStatus().name(),
          "difficulty", s.getDifficulty().name(), "coachName", s.getManager().getName(), "coachNationality", s.getManager().getNationality(),
          "coachStyle", s.getManager().getCoachStyle().name(), "club", s.getClub() == null ? null : s.getClub().getName(),
          "league", s.getLeague() == null ? null : s.getLeague().getName(), "seasonYear", s.getSeason() == null ? null : s.getSeason().getYear(),
          "currentWeek", s.getCurrentWeek(), "seed", s.getSeed(), "sourceWorld", s.getSourceWorldName(), "createdAt", String.valueOf(s.getCreatedAt()),
          "lastPlayedAt", String.valueOf(s.getLastPlayedAt())));
      for (RosterMove m : moves.findByCareerIdOrderByCreatedAtAsc(s.getId())) {
        rosterMoves.add(AccountDataCollector.row("careerId", String.valueOf(s.getId()), "player", m.getPlayer().getName(), "week", m.getWeek(),
            "kind", m.getKind().name(), "from", m.getFromStatus().name(), "to", m.getToStatus().name(), "deadMoney", m.getDeadMoney(),
            "at", String.valueOf(m.getCreatedAt())));
      }
    }
    export.put("careers", careers);
    export.put("careerRosterMoves", rosterMoves);
  }

  @Override
  @Transactional
  public void erase(UUID userId) {
    for (CareerSave s : saves.findByUserIdOrderByCreatedAtAsc(userId)) deletion.delete(s);
  }
}
