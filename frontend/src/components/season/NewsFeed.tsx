"use client";
import Link from "next/link";
import type { NewsItem } from "@/lib/api/season-types";
import { newsText } from "@/lib/season/news";
import { useI18n, useTd } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { IconChevronRight } from "@/components/ui/icons";

const TONE: Record<string, BadgeTone> = { POSITIVE: "good", NEGATIVE: "bad", DRAMATIC: "led", NEUTRAL: "neutral" };

/**
 * Feed de notícias da semana (RF21, D-103): manchete e texto montados no idioma da tela pelo `textKey`/`params` (reserva: o texto do
 * servidor), o tom como selo e, nas notícias de jogo, o link para o relatório (rota do 6b).
 */
export function NewsFeed({ items, careerId, compact }: { items: NewsItem[]; careerId: string; compact?: boolean }) {
  const { locale, pseudo } = useI18n();
  const td = useTd();
  return (
    <ul className={cn("grid", compact ? "gap-2" : "gap-3")}>
      {items.map((n) => {
        const text = newsText(n, locale, pseudo);
        return (
          <li key={n.id} className="min-w-0 rounded-[var(--radius-md)] border border-border bg-pitch/60 p-3">
            <div className="mb-1 flex flex-wrap items-center gap-1.5">
              <Badge size="xs" tone={TONE[n.tone] ?? "neutral"}>{td("season", `news.tone.${n.tone}`)}</Badge>
            </div>
            <p className="font-heading text-[0.95rem] font-semibold leading-snug text-text">{text.headline}</p>
            {!compact && <p className="mt-0.5 text-[0.85rem] text-text-muted">{text.body}</p>}
            {compact && <p className="mt-0.5 line-clamp-2 text-[0.8rem] text-text-muted">{text.body}</p>}
            {n.fixtureId && (
              <Link href={`/c/${careerId}/match/${n.fixtureId}/report`} className="mt-1 inline-flex items-center gap-0.5 text-[0.78rem] text-led underline-offset-2 hover:underline">
                {td("career", "calendar.report")} <IconChevronRight size={12} />
              </Link>
            )}
          </li>
        );
      })}
    </ul>
  );
}
