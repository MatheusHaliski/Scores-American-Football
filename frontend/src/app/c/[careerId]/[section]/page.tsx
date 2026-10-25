import { notFound } from "next/navigation";
import { sectionByKey } from "@/components/shell/sections";
import { UnderConstruction } from "@/components/shell/UnderConstruction";

/** Seções da central ainda não implementadas (fora da navegação; acessíveis só por URL). */
export default async function SectionPlaceholder({ params }: { params: Promise<{ careerId: string; section: string }> }) {
  const { careerId, section } = await params;
  const s = sectionByKey(section);
  if (!s || s.ready || s.href) notFound();
  return <UnderConstruction labelKey={s.labelKey} rf={s.rf} backHref={`/c/${careerId}`} />;
}
