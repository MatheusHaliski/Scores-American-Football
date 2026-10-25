package br.com.velion.scoresaf.application.dto;

import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.account.UserSettings;

/**
 * Configurações & interface (RF10 / HU-RF10) + Aparência e Idioma (RF46 CA04/CA05) + velocidade padrão da simulação (RF36).
 * {@code preferredLocale} é a tag BCP 47 do idioma da conta (pt-BR, en-US, es-ES). Futebol americano (D-10): no lugar do
 * {@code matchSpeed} do basquete, {@code quarterDurationMinutes} (tempo real de cada quarto na partida ao vivo, 1–15) e
 * {@code playCallingLevel} (KEY_MOMENTS | ALL_PLAYS | NONE).
 */
public record SettingsDto(int quarterDurationMinutes, String playCallingLevel, String difficulty, String backgroundArt, String schemeArt, String soundtrack, String fontFamily,
    boolean reducedMotion, boolean notificationsEnabled, String theme, boolean highContrast, String fontScale, String density,
    String preferredLocale, String regionalFormat, String simulationSpeed) {

  public static SettingsDto of(UserSettings s) { return of(s, s.getUser()); }

  public static SettingsDto of(UserSettings s, UserAccount u) {
    String locale = u == null || u.getLocale() == null ? "pt-BR" : u.getLocale().tag();
    return new SettingsDto(s.getQuarterDurationMinutes(), s.getPlayCallingLevel().name(), s.getDifficulty().name(), s.getBackgroundArt(), s.getSchemeArt(), s.getSoundtrack(), s.getFontFamily(),
        s.isReducedMotion(), s.isNotificationsEnabled(), s.getTheme().name(), s.isHighContrast(), s.getFontScale().name(), s.getDensity().name(),
        locale, s.getRegionalFormat() == null ? locale : s.getRegionalFormat(), s.getSimulationSpeed().name());
  }
}
