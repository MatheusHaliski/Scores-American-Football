"use client";
import { useT } from "@/i18n/provider";
import { Button } from "@/components/ui/Button";
import { IconCube } from "@/components/ui/icons";
import type { SectionLabelKey } from "./sections";

/** Página "em construção" para as seções da central que chegam nos próximos blocos (só por URL; fora da navegação). */
export function UnderConstruction({ labelKey, rf, backHref }: { labelKey: SectionLabelKey; rf: string; backHref: string }) {
  const t = useT("common");
  const tn = useT("nav");
  return (
    <div className="grid min-h-[60vh] place-items-center">
      <div className="max-w-lg text-center">
        <div className="mx-auto mb-4 grid size-16 place-items-center rounded-2xl bg-arena-3 text-wood-light">
          <IconCube size={30} />
        </div>
        <div className="eyebrow">{rf}</div>
        <h1 className="mt-1 font-display text-4xl">{tn(`section.${labelKey}`)}</h1>
        <p className="mt-3 text-text-muted">{t("construction.body")}</p>
        <div className="mt-6 flex justify-center">
          <Button href={backHref}>{t("construction.backCentral")}</Button>
        </div>
      </div>
    </div>
  );
}
