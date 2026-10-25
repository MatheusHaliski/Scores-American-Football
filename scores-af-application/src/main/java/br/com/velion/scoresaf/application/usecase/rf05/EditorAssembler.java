package br.com.velion.scoresaf.application.usecase.rf05;

import br.com.velion.scoresaf.application.dto.EditorDtos;
import br.com.velion.scoresaf.application.dto.RulesDto;
import br.com.velion.scoresaf.domain.catalog.Club;
import br.com.velion.scoresaf.domain.catalog.HelmetDesign;
import br.com.velion.scoresaf.domain.catalog.League;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.catalog.Stadium;
import br.com.velion.scoresaf.domain.catalog.StadiumSector;
import br.com.velion.scoresaf.domain.catalog.TicketPolicy;
import br.com.velion.scoresaf.domain.catalog.Uniform;
import br.com.velion.scoresaf.domain.engine.ratings.CardTier;
import br.com.velion.scoresaf.domain.engine.ratings.OverallCalculator;
import br.com.velion.scoresaf.domain.engine.ratings.PositionRating;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Entidade → DTO do editor (RF5). O mesmo mapeamento gera as amostras douradas que o front lê (D-5). */
public final class EditorAssembler {
  private EditorAssembler() {}

  public static EditorDtos.LeagueDto league(League l, int clubCount, boolean structureValid) {
    LeagueRules r = l.getRules() == null ? LeagueRules.defaults(l.getRuleset()) : l.getRules();
    return new EditorDtos.LeagueDto(l.getId(), l.getWorld().getId(), l.getName(), l.getShortName(), l.getCountry(), l.getTier(), l.getLogoUrl(),
        l.getRuleset().name(), clubCount, l.getConferenceCount(), l.getDivisionsPerConference(), l.getSalaryCap(), l.getSalaryFloor(),
        l.getPrestige(), l.getTvRightsPerSeason(), structureValid, RulesDto.of(r));
  }

  public static EditorDtos.ClubDto club(Club c, List<Uniform> kits, HelmetDesign helmet, int roster, int practice, LeagueRules rules) {
    List<EditorDtos.UniformDto> us = kits.stream().sorted(Comparator.comparing(Uniform::getSlot)).map(u -> new EditorDtos.UniformDto(u.getId(),
        u.getSlot().name(), u.getJerseyColor(), u.getPantsColor(), u.getNumberColor(), u.getNumberOutlineColor(), u.getPattern().name(),
        u.getTextureKey(), u.isActive(), u.getRevision())).toList();
    EditorDtos.HelmetDto h = helmet == null ? null : new EditorDtos.HelmetDto(helmet.getShellColor(), helmet.getStripe().name(),
        helmet.getStripeColor(), helmet.getFacemaskColor(), helmet.getDecalKey(), helmet.getFinish().name());
    return new EditorDtos.ClubDto(c.getId(), c.getWorld().getId(), c.getLeague() == null ? null : c.getLeague().getId(),
        c.getConference() == null ? null : c.getConference().getId(), c.getDivision() == null ? null : c.getDivision().getId(), c.getName(),
        c.getShortName(), c.getCity(), c.getFoundedYear(), c.getLogoKey(), c.getPrimaryColor(), c.getSecondaryColor(), c.getAccentColor(),
        c.getPrestige(), c.getReputationBoard(), c.getReputationFans(), c.getCash(), c.getScoutingBudget(), c.getScoutingLevel(), c.getExpectedWins(),
        c.getStadium() == null ? null : c.getStadium().getId(), c.getStadium() == null ? null : c.getStadium().getName(),
        c.getManager() == null ? null : c.getManager().getName(), roster, practice, rules.getRosterMax(), rules.getPracticeSquadMax(), us, h);
  }

  public static EditorDtos.StadiumDto stadium(Stadium s, List<StadiumSector> secs) {
    TicketPolicy tp = s.getTicketPolicy() == null ? new TicketPolicy() : s.getTicketPolicy();
    List<EditorDtos.SectorDto> out = secs.stream().sorted(Comparator.comparing(StadiumSector::getType))
        .map(x -> new EditorDtos.SectorDto(x.getId(), x.getType().name(), x.getSeats(), x.getTicketPrice(), x.getCondition(), x.getUpgradeCost())).toList();
    return new EditorDtos.StadiumDto(s.getId(), s.getName(), s.getCapacity(), s.getRoof().name(), s.getSurface().name(), s.getClimateZone().name(),
        s.getLevel(), s.getAtmosphere(), s.getValue(), s.getMaintenanceCostPerWeek(), s.getModel3dUrl(), s.getEndZoneArtKey(), tp.getMemberDiscountPct(),
        tp.isDynamicPricing(), tp.getSeasonTicketPct(), out);
  }

  public static EditorDtos.PlayerSummaryDto summary(Player p) {
    return new EditorDtos.PlayerSummaryDto(p.getId(), p.getName(), p.getPosition().name(), p.getUnit().name(), p.getRosterStatus().name(),
        p.getJerseyNumber(), p.getAge(), p.getHeightCm(), p.getWeightKg(), p.getOverall(), CardTier.of(p.getOverall()).name());
  }

  public static EditorDtos.PlayerEditorDto player(Player p, LeagueRules rules) {
    PositionRating r = p.ratingAt(p.getPosition());
    EditorDtos.RatingDto rating = new EditorDtos.RatingDto(r.version(), r.position().name(), r.position().family().name(), r.overall(), r.tier().name(),
        r.outOfPosition(), r.macros().values());
    Map<String, Integer> byPosition = new LinkedHashMap<>();
    OverallCalculator.overallByPosition(p.getAttributes()).forEach((pos, v) -> byPosition.put(pos.name(), v));
    return new EditorDtos.PlayerEditorDto(p.getId(), p.getWorld().getId(), p.getClub() == null ? null : p.getClub().getId(), p.getName(),
        p.getNationality(), p.getCollege(), br.com.velion.scoresaf.application.common.text.IsoDates.of(p.getBirthDate()), p.getAge(), p.getHeightCm(), p.getWeightKg(), p.getPosition().name(),
        p.getSecondaryPosition() == null ? null : p.getSecondaryPosition().name(), p.getUnit().name(), p.getRosterStatus().name(), p.getJerseyNumber(),
        p.getExperienceYears(), p.getPotential(), p.isLegendary(), p.getAttributes().asGroupedMap(), rating, byPosition, contract(p.getContract(), rules));
  }

  public static EditorDtos.ContractDto contract(PlayerContract c, LeagueRules rules) {
    if (c == null) return null;
    int prorate = rules.getBonusProrationMaxYears();
    List<Long> dead = new ArrayList<>();
    for (int y = 0; y < c.years(); y++) dead.add(c.deadMoneyIfCut(y, prorate));
    return new EditorDtos.ContractDto(c.getStartSeason(), c.endSeason(), c.years(), List.copyOf(c.getBaseSalaryByYear()), c.getSigningBonus(),
        c.getGuaranteed(), c.capHitByYear(prorate), dead, c.getType() == null ? null : c.getType().name(), c.isFranchiseTag(), c.totalValue());
  }
}
