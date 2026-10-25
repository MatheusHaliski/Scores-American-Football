package br.com.velion.scoresaf.application.usecase.rf10;

import br.com.velion.scoresaf.application.audit.AuditActions;
import br.com.velion.scoresaf.application.audit.AuditService;
import br.com.velion.scoresaf.application.common.exception.BusinessRuleException;
import br.com.velion.scoresaf.application.common.exception.InvalidInputException;
import br.com.velion.scoresaf.application.common.exception.NotFoundException;
import br.com.velion.scoresaf.application.dto.SettingsDto;
import br.com.velion.scoresaf.application.port.out.persistence.UserAccountRepository;
import br.com.velion.scoresaf.application.port.out.persistence.UserSettingsRepository;
import br.com.velion.scoresaf.domain.account.FontScale;
import br.com.velion.scoresaf.domain.account.PlayCallingLevel;
import br.com.velion.scoresaf.domain.account.ThemeMode;
import br.com.velion.scoresaf.domain.account.UiDensity;
import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserLocale;
import br.com.velion.scoresaf.domain.account.UserSettings;
import br.com.velion.scoresaf.domain.shared.Difficulty;
import br.com.velion.scoresaf.domain.shared.SimulationSpeed;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF10 / HU-RF10 — duração do quarto na partida ao vivo e nível de chamada de jogada (futebol americano, D-10), dificuldade, artes, trilha, fontes, acessibilidade; RF46 CA04 (Aparência: tema, alto
 * contraste, tamanho da fonte, reduzir movimento, densidade) e CA05 (Idioma: {@code preferredLocale} grava o idioma da conta;
 * formato regional); RF36 (velocidade padrão da simulação). Preferências de interface não são dado pessoal sensível.
 */
@Service
public class SettingsUseCases {
  public record UpdateCommand(Integer quarterDurationMinutes, String playCallingLevel, String difficulty, String backgroundArt, String schemeArt,
      String soundtrack, String fontFamily, Boolean reducedMotion, Boolean notificationsEnabled, String theme, Boolean highContrast, String fontScale,
      String density, String preferredLocale, String regionalFormat, String simulationSpeed) {}

  private final UserSettingsRepository settings; private final UserAccountRepository users; private final AuditService audit;
  public SettingsUseCases(UserSettingsRepository settings, UserAccountRepository users, AuditService audit) { this.settings = settings; this.users = users; this.audit = audit; }

  @Transactional
  public SettingsDto get(UUID userId) { UserSettings s = load(userId); return SettingsDto.of(s, s.getUser()); }

  @Transactional
  public SettingsDto update(UUID userId, UpdateCommand c) {
    UserSettings s = load(userId);
    List<String> changed = new ArrayList<>();
    if (c.quarterDurationMinutes() != null) {
      int m = c.quarterDurationMinutes();
      if (m < UserSettings.MIN_QUARTER_MINUTES || m > UserSettings.MAX_QUARTER_MINUTES)
        throw new InvalidInputException("INVALID_QUARTER_DURATION", "Duração do quarto fora da faixa: " + m, UserSettings.MIN_QUARTER_MINUTES, UserSettings.MAX_QUARTER_MINUTES);
      s.setQuarterDurationMinutes(m); changed.add("quarterDurationMinutes");
    }
    if (c.playCallingLevel() != null) { s.setPlayCallingLevel(parse(PlayCallingLevel.class, c.playCallingLevel())); changed.add("playCallingLevel"); }
    if (c.difficulty() != null) { s.setDifficulty(parse(Difficulty.class, c.difficulty())); changed.add("difficulty"); }
    if (c.backgroundArt() != null) { s.setBackgroundArt(c.backgroundArt()); changed.add("backgroundArt"); }
    if (c.schemeArt() != null) { s.setSchemeArt(c.schemeArt()); changed.add("schemeArt"); }
    if (c.soundtrack() != null) { s.setSoundtrack(c.soundtrack()); changed.add("soundtrack"); }
    if (c.fontFamily() != null) { s.setFontFamily(c.fontFamily()); changed.add("fontFamily"); }
    if (c.reducedMotion() != null) { s.setReducedMotion(c.reducedMotion()); changed.add("reducedMotion"); }
    if (c.notificationsEnabled() != null) { s.setNotificationsEnabled(c.notificationsEnabled()); changed.add("notificationsEnabled"); }
    if (c.theme() != null) { s.setTheme(parse(ThemeMode.class, c.theme())); changed.add("theme"); }
    if (c.highContrast() != null) { s.setHighContrast(c.highContrast()); changed.add("highContrast"); }
    if (c.fontScale() != null) { s.setFontScale(parse(FontScale.class, c.fontScale())); changed.add("fontScale"); }
    if (c.density() != null) { s.setDensity(parse(UiDensity.class, c.density())); changed.add("density"); }
    if (c.simulationSpeed() != null) { s.setSimulationSpeed(parse(SimulationSpeed.class, c.simulationSpeed())); changed.add("simulationSpeed"); }
    if (c.regionalFormat() != null) {
      if (c.regionalFormat().isBlank()) s.setRegionalFormat(null);
      else { UserLocale rf = UserLocale.parseOrNull(c.regionalFormat()); if (rf == null) throw invalid(c.regionalFormat()); s.setRegionalFormat(rf.tag()); }
      changed.add("regionalFormat");
    }
    UserAccount u = s.getUser();
    if (c.preferredLocale() != null) {
      UserLocale l = UserLocale.parseOrNull(c.preferredLocale());
      if (l == null) throw new BusinessRuleException("INVALID_LOCALE", "Idioma inválido: " + c.preferredLocale(), c.preferredLocale());
      if (u.getLocale() != l) { u.setLocale(l); users.save(u); }
      changed.add("preferredLocale");
    }
    UserSettings saved = settings.save(s);
    if (!changed.isEmpty()) audit.ok(AuditActions.SETTINGS_UPDATED, "UserSettings", saved.getId(), String.join(",", changed));
    return SettingsDto.of(saved, u);
  }

  private UserSettings load(UUID userId) {
    return settings.findByUserId(userId).orElseGet(() -> {
      UserSettings s = new UserSettings(); s.setUser(users.findById(userId).orElseThrow(() -> new NotFoundException("Conta", userId))); return settings.save(s);
    });
  }

  private static BusinessRuleException invalid(String v) { return new BusinessRuleException("INVALID_VALUE", "Valor inválido: " + v, v); }
  private static <E extends Enum<E>> E parse(Class<E> type, String v) { try { return Enum.valueOf(type, v.trim().toUpperCase()); } catch (IllegalArgumentException e) { throw invalid(v); } }
}
