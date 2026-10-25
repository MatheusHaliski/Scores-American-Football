"use client";
import { PLAY_CALLING_LEVELS, QUARTER_MINUTES, type FontScale, type SimulationSpeed, type ThemeMode, type UiDensity, type UserSettings } from "@/lib/api/types";
import { useT, useTd } from "@/i18n/provider";
import { Card } from "@/components/ui/Card";
import { Segmented, SelectField } from "@/components/ui/Field";
import { ErrorState, PageSkeleton } from "@/components/ui/States";
import { SectionHeader, SettingRow, Switch } from "./parts";

const THEMES: ThemeMode[] = ["SYSTEM", "DARK", "LIGHT"];
const FONT_SCALES: FontScale[] = ["SMALL", "MEDIUM", "LARGE"];
const DENSITIES: UiDensity[] = ["COMFORTABLE", "COMPACT"];
const QUARTER_OPTIONS = Array.from({ length: QUARTER_MINUTES.max - QUARTER_MINUTES.min + 1 }, (_, i) => QUARTER_MINUTES.min + i);
const SIM_SPEEDS: SimulationSpeed[] = ["NORMAL", "FAST", "INSTANT"];

/**
 * Aparência (RF46 CA04): preferências de interface — aplicadas na hora, sem recarregar, e persistidas no perfil.
 * Sem o seletor de artes de fundo do basquete (dependia das ilustrações dele); `backgroundArt` segue no contrato, intocado.
 */
export function AppearanceSection({ settings, error, saving, update }: { settings: UserSettings | null; error: string | null; saving: keyof UserSettings | null; update: (p: Partial<UserSettings>) => void }) {
  const t = useT("settings");
  const td = useTd();
  if (error) return <ErrorState message={error} />;
  if (!settings) return <PageSkeleton rows={3} />;
  const s = settings;

  return (
    <div className="flex flex-col gap-5">
      <SectionHeader title={t("appearance.title")} summary={t("appearance.summary")} note={t("appearance.note")} />

      <Card eyebrow={t("appearance.display.eyebrow")} title={t("appearance.display.title")}>
        <SettingRow
          label={t("appearance.theme.label")}
          description={t("appearance.theme.hint")}
          control={<Segmented size="sm" value={s.theme} onChange={(v) => update({ theme: v })} options={THEMES.map((v) => ({ value: v, label: td("settings", `enum.theme.${v}`) }))} />}
        />
        <SettingRow label={t("appearance.contrast.label")} description={t("appearance.contrast.hint")} control={<Switch checked={s.highContrast} busy={saving === "highContrast"} onChange={(v) => update({ highContrast: v })} label={t("appearance.contrast.label")} />} />
        <SettingRow
          label={t("appearance.fontScale.label")}
          description={t("appearance.fontScale.hint")}
          control={<Segmented size="sm" value={s.fontScale} onChange={(v) => update({ fontScale: v })} options={FONT_SCALES.map((v) => ({ value: v, label: <span style={{ fontSize: v === "SMALL" ? "0.8em" : v === "LARGE" ? "1.15em" : "1em" }}>{td("settings", `enum.fontScale.${v}`)}</span> }))} />}
        />
        <SettingRow label={t("appearance.density.label")} description={t("appearance.density.hint")} control={<Segmented size="sm" value={s.density} onChange={(v) => update({ density: v })} options={DENSITIES.map((v) => ({ value: v, label: td("settings", `enum.density.${v}`) }))} />} />
        <SettingRow label={t("appearance.motion.label")} description={t("appearance.motion.hint")} control={<Switch checked={s.reducedMotion} busy={saving === "reducedMotion"} onChange={(v) => update({ reducedMotion: v })} label={t("appearance.motion.label")} />} />
      </Card>

      <Card eyebrow={t("appearance.game.eyebrow")} title={t("appearance.game.title")}>
        <SettingRow
          label={t("appearance.quarter.label")}
          htmlFor="quarter-minutes"
          description={t("appearance.quarter.hint", { min: QUARTER_MINUTES.min, max: QUARTER_MINUTES.max })}
          control={
            <SelectField id="quarter-minutes" value={String(s.quarterDurationMinutes)} onChange={(e) => update({ quarterDurationMinutes: Number(e.target.value) })} className="min-w-[10rem]">
              {QUARTER_OPTIONS.map((m) => (
                <option key={m} value={m}>
                  {t("appearance.quarter.option", { n: m })}
                </option>
              ))}
            </SelectField>
          }
        />
        <SettingRow label={t("appearance.playCalling.label")} description={t(`appearance.playCalling.hint.${s.playCallingLevel}` as "appearance.playCalling.hint.KEY_MOMENTS")} control={<Segmented size="sm" value={s.playCallingLevel} onChange={(v) => update({ playCallingLevel: v })} options={PLAY_CALLING_LEVELS.map((v) => ({ value: v, label: td("settings", `enum.playCallingLevel.${v}`) }))} />} />
        <SettingRow label={t("appearance.simSpeed.label")} description={t("appearance.simSpeed.hint")} control={<Segmented size="sm" value={s.simulationSpeed} onChange={(v) => update({ simulationSpeed: v })} options={SIM_SPEEDS.map((v) => ({ value: v, label: td("settings", `enum.simulationSpeed.${v}`) }))} />} />
        <SettingRow label={t("appearance.notifications.label")} description={t("appearance.notifications.hint")} control={<Switch checked={s.notificationsEnabled} busy={saving === "notificationsEnabled"} onChange={(v) => update({ notificationsEnabled: v })} label={t("appearance.notifications.label")} />} />
      </Card>
    </div>
  );
}
