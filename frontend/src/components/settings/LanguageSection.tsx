"use client";
import { useMemo } from "react";
import type { UserSettings } from "@/lib/api/types";
import { LOCALES, LOCALE_META, type Locale } from "@/i18n/config";
import { createFormatters } from "@/i18n/format";
import { useI18n, useT } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { usePrefs } from "@/store/prefs";
import { Card } from "@/components/ui/Card";
import { SelectField } from "@/components/ui/Field";
import { ErrorState, PageSkeleton } from "@/components/ui/States";
import { IconCheck } from "@/components/ui/icons";
import { LgpdTag, SectionHeader, SettingRow, Switch } from "./parts";

const SAMPLE_DATE = "2026-09-26T19:30:00Z";

/**
 * Idioma (RF46 CA05): troca imediata sem recarregar (textos, `<html lang>`, Accept-Language), persistida no perfil;
 * formato regional independente do idioma (datas, números, moeda). Lista sem bandeiras (idioma ≠ país).
 */
export function LanguageSection({ settings, error, update }: { settings: UserSettings | null; error: string | null; update: (p: Partial<UserSettings>) => void }) {
  const t = useT("settings");
  const { locale, setLocale, pseudo, setPseudo } = useI18n();
  const setServerLocale = usePrefs((s) => s.setServerLocale);
  const regional = usePrefs((s) => s.regionalFormat);
  const setRegional = usePrefs((s) => s.setRegionalFormat);
  const region = regional ?? locale;
  const preview = useMemo(() => createFormatters(locale, region), [locale, region]);

  if (error) return <ErrorState message={error} />;
  if (!settings) return <PageSkeleton rows={2} />;

  const chooseLocale = (l: Locale) => {
    if (l === locale && !pseudo) return;
    setLocale(l, "user");
    setServerLocale(l);
    update({ preferredLocale: l, ...(regional ? {} : { regionalFormat: l }) });
  };
  const chooseRegion = (r: string) => {
    const value = r === "auto" ? null : r;
    setRegional(value);
    update({ regionalFormat: value ?? locale });
  };

  return (
    <div className="flex flex-col gap-5">
      <SectionHeader title={t("language.title")} summary={t("language.summary")} />

      <Card eyebrow={t("language.ui.eyebrow")} title={t("language.ui.title")} action={<LgpdTag article="Art. 6º, VI" explain={t("lgpd.art6vi")} />}>
        <div role="radiogroup" aria-label={t("language.ui.title")} className="grid gap-2 sm:grid-cols-3">
          {LOCALES.map((l) => {
            const selected = l === locale && !pseudo;
            return (
              <button
                key={l}
                type="button"
                role="radio"
                aria-checked={selected}
                lang={l}
                onClick={() => chooseLocale(l)}
                className={cn(
                  "flex items-center justify-between gap-2 rounded-[var(--radius-md)] border p-3 text-left transition-colors focus-visible:outline-3 focus-visible:outline-rim",
                  selected ? "border-orange bg-orange/10" : "border-border bg-pitch hover:border-border-strong",
                )}
              >
                <span>
                  <span className="block font-heading font-semibold">{LOCALE_META[l].nativeName}</span>
                  <span className="block text-[0.75rem] text-text-dim">{l}</span>
                </span>
                {selected && <IconCheck size={18} className="text-orange" aria-hidden />}
              </button>
            );
          })}
        </div>
        <p className="mt-3 text-[0.8rem] text-text-dim">{t("language.ui.hint")}</p>
      </Card>

      <Card eyebrow={t("language.region.eyebrow")} title={t("language.region.title")}>
        <div className="grid gap-4 sm:grid-cols-[minmax(0,16rem)_1fr] items-start">
          <SelectField label={t("language.region.label")} value={regional ?? "auto"} onChange={(e) => chooseRegion(e.target.value)} hint={t("language.region.hint")}>
            <option value="auto">{t("language.region.auto", { name: LOCALE_META[locale].nativeName })}</option>
            {LOCALES.map((l) => (
              <option key={l} value={l}>
                {LOCALE_META[l].nativeName} · {LOCALE_META[l].region}
              </option>
            ))}
          </SelectField>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 rounded-[var(--radius-md)] border border-border bg-pitch p-3 text-sm" aria-live="polite">
            <dt className="text-text-dim">{t("language.region.date")}</dt>
            <dd className="text-text tabular">{preview.dateLong(SAMPLE_DATE)}</dd>
            <dt className="text-text-dim">{t("language.region.time")}</dt>
            <dd className="text-text tabular">{preview.dateTime(SAMPLE_DATE)}</dd>
            <dt className="text-text-dim">{t("language.region.number")}</dt>
            <dd className="text-text tabular">{preview.num(1234567.89, 2)}</dd>
            <dt className="text-text-dim">{t("language.region.gameMoney")}</dt>
            <dd className="text-text tabular">
              {preview.money(12_500_000)} · {preview.money(48_250, false)}
            </dd>
            <dt className="text-text-dim">{t("language.region.realMoney")}</dt>
            <dd className="text-text tabular">{preview.currency(19.9)}</dd>
          </dl>
        </div>
      </Card>

      {process.env.NODE_ENV !== "production" && (
        <Card eyebrow={t("language.dev.eyebrow")} title={t("language.dev.title")}>
          <SettingRow label={t("language.dev.pseudo")} description={t("language.dev.pseudoHint")} control={<Switch checked={pseudo} onChange={setPseudo} label={t("language.dev.pseudo")} />} />
        </Card>
      )}
    </div>
  );
}
