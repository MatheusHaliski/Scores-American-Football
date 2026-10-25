package br.com.velion.scoresaf.application.usecase.rf17;

import br.com.velion.scoresaf.application.dto.SeasonDtos;
import br.com.velion.scoresaf.application.port.out.persistence.SeasonRepository;
import br.com.velion.scoresaf.application.usecase.rf35.SeasonResults;
import br.com.velion.scoresaf.application.usecase.support.CareerAssembler;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.season.PlayoffSeed;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.AwardType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF35/RF17 — sala de troféus da carreira ({@code GetTrophiesUseCase} do basquete): por temporada, os títulos de divisão (campeões de
 * divisão das sementes), de conferência (as finalistas, quando há duas conferências) e da liga, com a marca da franquia do usuário, a
 * contagem dele (títulos e participações em playoffs) e o histórico das temporadas (campeã, vice, MVP e o resultado do usuário). Tudo
 * sai das temporadas gravadas: nada novo no banco além das sementes e dos prêmios. A franquia do usuário é a atual (até o bloco 7 ele
 * não troca de franquia).
 */
@Service
public class GetTrophiesUseCase {
  private final CareerSupport support; private final SeasonRepository seasons; private final SeasonResults results;

  public GetTrophiesUseCase(CareerSupport support, SeasonRepository seasons, SeasonResults results) {
    this.support = support; this.seasons = seasons; this.results = results;
  }

  @Transactional(readOnly = true)
  public SeasonDtos.TrophiesDto execute(UUID careerId, UUID userId) {
    CareerSave save = support.owned(careerId, userId);
    UUID user = save.getClub() == null ? null : save.getClub().getId();
    LeagueRules rules = CareerSupport.rules(save);
    List<SeasonDtos.TrophyDto> trophies = new ArrayList<>();
    List<SeasonDtos.SeasonHistoryDto> history = new ArrayList<>();
    int championships = 0, conferences = 0, divisions = 0, appearances = 0;
    List<Season> all = new ArrayList<>(seasons.findByCareerIdOrderByNumberAsc(save.getId()));
    all.sort(Comparator.comparingInt(Season::getNumber).reversed());
    for (Season s : all) {
      SeasonResults.View v = results.view(s, rules);
      Club champ = s.getChampionClub(), runner = s.getRunnerUpClub();
      if (champ != null) {
        trophies.add(trophy(s, "CHAMPIONSHIP", champ, user));
        if (champ.getId().equals(user)) championships++;
      }
      if (v.league().conferences().size() > 1) {
        for (Club c : new Club[] {champ, runner}) {
          if (c == null) continue;
          trophies.add(trophy(s, "CONFERENCE", c, user));
          if (c.getId().equals(user)) conferences++;
        }
      }
      for (PlayoffSeed seed : v.seeds().stream().filter(PlayoffSeed::isDivisionChampion)
          .sorted(Comparator.comparingInt((PlayoffSeed x) -> x.getConference().getSortOrder()).thenComparing(x -> x.getClub().getDivision() == null ? 0
              : x.getClub().getDivision().getSortOrder())).toList()) {
        trophies.add(trophy(s, "DIVISION", seed.getClub(), user));
        if (seed.getClub().getId().equals(user)) divisions++;
      }
      if (user != null && v.seeds().stream().anyMatch(x -> x.getClub().getId().equals(user))) appearances++;
      SeasonDtos.AwardDto mvp = v.awards().stream().filter(a -> a.getType() == AwardType.MVP).findFirst().map(SeasonResults::award).orElse(null);
      history.add(new SeasonDtos.SeasonHistoryDto(s.getId(), s.getNumber(), s.getYear(), s.isCompleted(), champ == null ? null : CareerAssembler.club(champ),
          runner == null ? null : CareerAssembler.club(runner), mvp, user == null ? null : SeasonResults.record(v, user), v.playoffResult(user)));
    }
    return new SeasonDtos.TrophiesDto(user, championships, conferences, divisions, appearances, trophies, history);
  }

  private static SeasonDtos.TrophyDto trophy(Season s, String kind, Club c, UUID user) {
    return new SeasonDtos.TrophyDto(s.getId(), s.getNumber(), s.getYear(), kind, CareerAssembler.club(c), c.getConference() == null ? null : c.getConference().getShortName(),
        c.getDivision() == null ? null : c.getDivision().getName(), c.getId().equals(user));
  }
}
