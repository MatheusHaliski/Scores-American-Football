package br.com.velion.scoresaf.application.dto;

import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.shared.OvertimeFormat;
import java.util.Locale;

/**
 * Regras da liga no contrato do editor (RF5): espelho de {@link LeagueRules}, campo a campo. Na resposta vem tudo preenchido; no
 * comando, campo nulo = manter o valor atual. {@code version} é só leitura (sobe com o código, D-4).
 */
public record RulesDto(
    Integer version,
    Integer quarterMinutes,
    Integer playClockSeconds,
    Integer playClockAfterStoppageSeconds,
    Boolean twoMinuteWarning,
    Integer timeoutsPerHalf,
    Boolean clockStopsOnFirstDown,
    String overtimeFormat,
    Integer overtimeMinutes,
    Integer overtimeStartYardLine,
    Boolean regularSeasonTiesAllowed,
    Integer downs,
    Integer yardsToGain,
    Integer fieldLengthYards,
    Integer endZoneYards,
    Double hashFromSidelineFeet,
    Integer pointsTouchdown,
    Integer pointsExtraPoint,
    Integer pointsTwoPoint,
    Integer pointsFieldGoal,
    Integer pointsSafety,
    Integer patYardLine,
    Integer twoPointYardLine,
    Integer kickoffYardLine,
    Integer touchbackYardLine,
    Boolean oneFootInbounds,
    Integer regularSeasonWeeks,
    Integer gamesPerTeam,
    Integer byeWeeksPerTeam,
    Integer playoffSeedsPerConference,
    Integer playoffByeSeeds,
    Boolean neutralSiteFinal,
    Integer draftRounds,
    Boolean hardCap,
    Integer rosterMax,
    Integer gameDayActives,
    Integer practiceSquadMax,
    Integer practiceSquadProtectedMax,
    Integer practiceSquadElevations,
    Integer offseasonRosterMax,
    Integer irReturnsPerSeason,
    Integer irMinWeeks,
    Integer dlRotationMin,
    Integer dlRotationMax,
    Integer bonusProrationMaxYears) {

  public static RulesDto of(LeagueRules r) {
    return new RulesDto(r.getVersion(), r.getQuarterMinutes(), r.getPlayClockSeconds(), r.getPlayClockAfterStoppageSeconds(), r.isTwoMinuteWarning(), r.getTimeoutsPerHalf(), r.isClockStopsOnFirstDown(), r.getOvertimeFormat().name(), r.getOvertimeMinutes(), r.getOvertimeStartYardLine(), r.isRegularSeasonTiesAllowed(), r.getDowns(), r.getYardsToGain(), r.getFieldLengthYards(), r.getEndZoneYards(), r.getHashFromSidelineFeet(), r.getPointsTouchdown(), r.getPointsExtraPoint(), r.getPointsTwoPoint(), r.getPointsFieldGoal(), r.getPointsSafety(), r.getPatYardLine(), r.getTwoPointYardLine(), r.getKickoffYardLine(), r.getTouchbackYardLine(), r.isOneFootInbounds(), r.getRegularSeasonWeeks(), r.getGamesPerTeam(), r.getByeWeeksPerTeam(), r.getPlayoffSeedsPerConference(), r.getPlayoffByeSeeds(), r.isNeutralSiteFinal(), r.getDraftRounds(), r.isHardCap(), r.getRosterMax(), r.getGameDayActives(), r.getPracticeSquadMax(), r.getPracticeSquadProtectedMax(), r.getPracticeSquadElevations(), r.getOffseasonRosterMax(), r.getIrReturnsPerSeason(), r.getIrMinWeeks(), r.getDlRotationMin(), r.getDlRotationMax(), r.getBonusProrationMaxYears());
  }

  /** Cópia de {@code base} com os campos não nulos deste comando. Formato de prorrogação desconhecido lança {@link IllegalArgumentException}. */
  public LeagueRules applyTo(LeagueRules base) {
    LeagueRules.LeagueRulesBuilder b = base.toBuilder();
    if (quarterMinutes != null) b.quarterMinutes(quarterMinutes);
    if (playClockSeconds != null) b.playClockSeconds(playClockSeconds);
    if (playClockAfterStoppageSeconds != null) b.playClockAfterStoppageSeconds(playClockAfterStoppageSeconds);
    if (twoMinuteWarning != null) b.twoMinuteWarning(twoMinuteWarning);
    if (timeoutsPerHalf != null) b.timeoutsPerHalf(timeoutsPerHalf);
    if (clockStopsOnFirstDown != null) b.clockStopsOnFirstDown(clockStopsOnFirstDown);
    if (overtimeFormat != null) b.overtimeFormat(OvertimeFormat.valueOf(overtimeFormat.trim().toUpperCase(Locale.ROOT)));
    if (overtimeMinutes != null) b.overtimeMinutes(overtimeMinutes);
    if (overtimeStartYardLine != null) b.overtimeStartYardLine(overtimeStartYardLine);
    if (regularSeasonTiesAllowed != null) b.regularSeasonTiesAllowed(regularSeasonTiesAllowed);
    if (downs != null) b.downs(downs);
    if (yardsToGain != null) b.yardsToGain(yardsToGain);
    if (fieldLengthYards != null) b.fieldLengthYards(fieldLengthYards);
    if (endZoneYards != null) b.endZoneYards(endZoneYards);
    if (hashFromSidelineFeet != null) b.hashFromSidelineFeet(hashFromSidelineFeet);
    if (pointsTouchdown != null) b.pointsTouchdown(pointsTouchdown);
    if (pointsExtraPoint != null) b.pointsExtraPoint(pointsExtraPoint);
    if (pointsTwoPoint != null) b.pointsTwoPoint(pointsTwoPoint);
    if (pointsFieldGoal != null) b.pointsFieldGoal(pointsFieldGoal);
    if (pointsSafety != null) b.pointsSafety(pointsSafety);
    if (patYardLine != null) b.patYardLine(patYardLine);
    if (twoPointYardLine != null) b.twoPointYardLine(twoPointYardLine);
    if (kickoffYardLine != null) b.kickoffYardLine(kickoffYardLine);
    if (touchbackYardLine != null) b.touchbackYardLine(touchbackYardLine);
    if (oneFootInbounds != null) b.oneFootInbounds(oneFootInbounds);
    if (regularSeasonWeeks != null) b.regularSeasonWeeks(regularSeasonWeeks);
    if (gamesPerTeam != null) b.gamesPerTeam(gamesPerTeam);
    if (byeWeeksPerTeam != null) b.byeWeeksPerTeam(byeWeeksPerTeam);
    if (playoffSeedsPerConference != null) b.playoffSeedsPerConference(playoffSeedsPerConference);
    if (playoffByeSeeds != null) b.playoffByeSeeds(playoffByeSeeds);
    if (neutralSiteFinal != null) b.neutralSiteFinal(neutralSiteFinal);
    if (draftRounds != null) b.draftRounds(draftRounds);
    if (hardCap != null) b.hardCap(hardCap);
    if (rosterMax != null) b.rosterMax(rosterMax);
    if (gameDayActives != null) b.gameDayActives(gameDayActives);
    if (practiceSquadMax != null) b.practiceSquadMax(practiceSquadMax);
    if (practiceSquadProtectedMax != null) b.practiceSquadProtectedMax(practiceSquadProtectedMax);
    if (practiceSquadElevations != null) b.practiceSquadElevations(practiceSquadElevations);
    if (offseasonRosterMax != null) b.offseasonRosterMax(offseasonRosterMax);
    if (irReturnsPerSeason != null) b.irReturnsPerSeason(irReturnsPerSeason);
    if (irMinWeeks != null) b.irMinWeeks(irMinWeeks);
    if (dlRotationMin != null) b.dlRotationMin(dlRotationMin);
    if (dlRotationMax != null) b.dlRotationMax(dlRotationMax);
    if (bonusProrationMaxYears != null) b.bonusProrationMaxYears(bonusProrationMaxYears);
    return b.version(LeagueRules.CURRENT_VERSION).build();
  }
}
