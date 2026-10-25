"use client";
import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { QUARTER_MINUTES, type FontScale, type Locale, type PlayCallingLevel, type SimulationSpeed, type ThemeMode, type UiDensity, type UserSettings } from "@/lib/api/types";

interface PrefsState {
  quarterDurationMinutes: number;
  playCallingLevel: PlayCallingLevel;
  simulationSpeed: SimulationSpeed;
  reduceMotion: boolean;
  soundEnabled: boolean;
  theme: ThemeMode;
  highContrast: boolean;
  fontScale: FontScale;
  density: UiDensity;
  /** idioma persistido em /me/settings (null até carregar) — vence storage/navigator, não a URL */
  serverLocale: Locale | null;
  /** formato regional (números/datas/moeda); null = acompanha o idioma */
  regionalFormat: string | null;
  hydratedFromServer: boolean;
  setQuarterDurationMinutes: (m: number) => void;
  setPlayCallingLevel: (l: PlayCallingLevel) => void;
  setSimulationSpeed: (s: SimulationSpeed) => void;
  setReduceMotion: (v: boolean) => void;
  setSoundEnabled: (v: boolean) => void;
  setTheme: (t: ThemeMode) => void;
  setHighContrast: (v: boolean) => void;
  setFontScale: (v: FontScale) => void;
  setDensity: (v: UiDensity) => void;
  setServerLocale: (l: Locale | null) => void;
  setRegionalFormat: (r: string | null) => void;
  /** aplica o que veio de GET /me/settings (RF10/RF46) */
  applyServer: (s: UserSettings) => void;
}

/**
 * Preferências do usuário (RF10 + RF46 Aparência): aplicadas imediatamente e persistidas (servidor + localStorage).
 * Só preferências de interface vão para o localStorage — nada de sessão (essa fica em memória, `store/session.ts`).
 */
export const usePrefs = create<PrefsState>()(
  persist(
    (set) => ({
      quarterDurationMinutes: QUARTER_MINUTES.default,
      playCallingLevel: "KEY_MOMENTS",
      simulationSpeed: "FAST",
      reduceMotion: false,
      soundEnabled: true,
      theme: "DARK",
      highContrast: false,
      fontScale: "MEDIUM",
      density: "COMFORTABLE",
      serverLocale: null,
      regionalFormat: null,
      hydratedFromServer: false,
      setQuarterDurationMinutes: (quarterDurationMinutes) => set({ quarterDurationMinutes }),
      setPlayCallingLevel: (playCallingLevel) => set({ playCallingLevel }),
      setSimulationSpeed: (simulationSpeed) => set({ simulationSpeed }),
      setReduceMotion: (reduceMotion) => set({ reduceMotion }),
      setSoundEnabled: (soundEnabled) => set({ soundEnabled }),
      setTheme: (theme) => set({ theme }),
      setHighContrast: (highContrast) => set({ highContrast }),
      setFontScale: (fontScale) => set({ fontScale }),
      setDensity: (density) => set({ density }),
      setServerLocale: (serverLocale) => set({ serverLocale }),
      setRegionalFormat: (regionalFormat) => set({ regionalFormat }),
      applyServer: (s) => set({ quarterDurationMinutes: s.quarterDurationMinutes, playCallingLevel: s.playCallingLevel, simulationSpeed: s.simulationSpeed, reduceMotion: s.reducedMotion, theme: s.theme, highContrast: s.highContrast, fontScale: s.fontScale, density: s.density, serverLocale: s.preferredLocale, regionalFormat: s.regionalFormat && s.regionalFormat !== s.preferredLocale ? s.regionalFormat : null, hydratedFromServer: true }),
    }),
    { name: "scoresaf.prefs", version: 1, migrate: () => ({}) as never, storage: createJSONStorage(() => localStorage), partialize: (s) => ({ quarterDurationMinutes: s.quarterDurationMinutes, playCallingLevel: s.playCallingLevel, simulationSpeed: s.simulationSpeed, reduceMotion: s.reduceMotion, soundEnabled: s.soundEnabled, theme: s.theme, highContrast: s.highContrast, fontScale: s.fontScale, density: s.density, regionalFormat: s.regionalFormat }) },
  ),
);

export function resolveTheme(theme: ThemeMode): "light" | "dark" {
  if (theme === "LIGHT") return "light";
  if (theme === "DARK") return "dark";
  if (typeof window === "undefined") return "dark";
  return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

/** Reflete as preferências de aparência no `<html>` (tokens em `tokens.css`). */
export function applyAppearance(p: Pick<PrefsState, "theme" | "highContrast" | "fontScale" | "density" | "reduceMotion">): void {
  if (typeof document === "undefined") return;
  const el = document.documentElement;
  el.dataset.theme = resolveTheme(p.theme);
  el.dataset.themePref = p.theme.toLowerCase();
  el.dataset.contrast = p.highContrast ? "high" : "normal";
  el.dataset.fontScale = p.fontScale.toLowerCase();
  el.dataset.density = p.density.toLowerCase();
  el.style.colorScheme = resolveTheme(p.theme);
  el.dataset.reduceMotion = String(p.reduceMotion);
}
