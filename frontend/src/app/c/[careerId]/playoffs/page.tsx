"use client";
import { useParams } from "next/navigation";
import { api } from "@/lib/api";
import type { PlayoffSituation } from "@/lib/api/season-types";
import { useQuery } from "@/lib/hooks/useQuery";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { PageHeader } from "@/components/shell/PageHeader";
import { ClubBadge } from "@/components/career/ClubBadge";
import { PlayoffBracket } from "@/components/season/PlayoffBracket";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ErrorState, InlineAlert, PageSkeleton } from "@/components/ui/States";
import { IconTable } from "@/components/ui/icons";

const STATUS_TONE: Record<string, BadgeTone> = { NOT_STARTED: "info", IN_PROGRESS: "led", FINISHED: "good" };
const USER_TONE: Record<string, BadgeTone> = { CHAMPION: "led", RUNNER_UP: "good", ALIVE: "good", BYE: "led", PROJECTED_IN: "info", PROJECTED_OUT: "warn", ELIMINATED: "bad", MISSED: "bad" };

function UserCard({ p }: { p: PlayoffSituation }) {
  const t = useT("season");
  const td = useTd();
  const f = useFormat();
  const u = p.user;
  if (!u) return null;
  return (
    <Card eyebrow={t("playoffs.user.title")} title={td("season", `playoffs.user.${u.status}`)} action={u.seed != null ? <Badge tone={USER_TONE[u.status] ?? "neutral"}>{t("standings.seed", { n: u.seed })}</Badge> : undefined}>
      {(u.round || u.nextGame) && <div className="flex flex-wrap items-center gap-2 text-[0.85rem] text-text-muted">
        {u.round && <Badge size="xs">{td("season", `playoffs.round.${u.round}`)}</Badge>}
        {u.nextGame && (
          <span>
            {t("hub.playoffs.next", { week: u.nextGame.week })} · {f.dateShort(`${u.nextGame.date}T12:00:00Z`)} · {td("enums", `kickoff.${u.nextGame.kickoffSlot}`)}
          </span>
        )}
      </div>}
    </Card>
  );
}

function SeedsCard({ p }: { p: PlayoffSituation }) {
  const t = useT("season");
  const td = useTd();
  const userId = p.user?.clubId;
  return (
    <div className="grid gap-4 md:grid-cols-2 [&>*]:min-w-0">
      {p.conferences.map((c) => (
        <Card key={c.id} eyebrow={c.shortName} title={t("playoffs.seedsOf", { conference: c.name })}>
          <ol className="grid gap-1">
            {c.seeds.map((s) => (
              <li key={s.club.id} className={cn("flex min-w-0 items-center gap-2 rounded px-1 py-1 text-[0.85rem]", s.club.id === userId && "bg-orange/10 text-led", s.status === "ELIMINATED" && "opacity-60")}>
                <span className="w-5 shrink-0 text-right font-heading tabular text-text-dim">{s.seed}</span>
                <ClubBadge club={s.club} size={22} />
                <span className="min-w-0 flex-1 truncate">{s.club.name}</span>
                <span className="shrink-0 tabular text-text-muted">{s.record.summary}</span>
                {s.bye && <Badge size="xs" tone="led">{t("playoffs.bye")}</Badge>}
                {s.divisionChampion && !s.bye && <Badge size="xs" tone="good" title={t("playoffs.divisionChampion")}>{t("playoffs.divisionShort")}</Badge>}
                {s.status !== "ALIVE" && s.status !== "PROJECTED" && <Badge size="xs" tone={s.status === "CHAMPION" ? "led" : "bad"}>{td("season", `playoffs.seedStatus.${s.status}`)}</Badge>}
              </li>
            ))}
          </ol>
        </Card>
      ))}
    </div>
  );
}

/** Playoffs (RF66): situação, a franquia do usuário, a chave (projetada antes do fim da temporada regular) e as sementes. */
export default function PlayoffsPage() {
  const t = useT("season");
  const td = useTd();
  const { careerId } = useParams<{ careerId: string }>();
  const q = useQuery(async () => {
    const [situation, bracket] = await Promise.all([api.season.playoffs(careerId), api.season.bracket(careerId)]);
    return { situation, bracket };
  }, [careerId]);

  if (q.loading && !q.data) return <PageSkeleton rows={3} />;
  if (q.error || !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const { situation: p, bracket } = q.data;

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        eyebrow={t("playoffs.eyebrow")}
        title={t("playoffs.title")}
        description={t("playoffs.description", { seeds: p.seedsPerConference, byes: p.byeSeeds })}
        actions={
          <>
            <Badge tone={STATUS_TONE[p.status] ?? "neutral"} size="md">{td("season", `playoffs.status.${p.status}`)}</Badge>
            <Button variant="outline" size="sm" href={`/c/${careerId}/standings`} icon={<IconTable size={16} />}>{t("hub.playoffs.standings")}</Button>
          </>
        }
      />
      {p.projection && <InlineAlert tone="info">{t("playoffs.projection")}</InlineAlert>}
      {(p.champion || p.runnerUp) && (
        <div className="grid gap-3 sm:grid-cols-2">
          {p.champion && (
            <div className="flex items-center gap-3 rounded-[var(--radius-lg)] border-2 border-led bg-arena-2 p-3 shadow-led">
              <ClubBadge club={p.champion} size={44} />
              <div className="min-w-0"><div className="eyebrow">{t("playoffs.champion")}</div><div className="truncate font-heading text-lg font-semibold">{p.champion.name}</div></div>
            </div>
          )}
          {p.runnerUp && (
            <div className="flex items-center gap-3 rounded-[var(--radius-lg)] border border-border bg-arena-2 p-3">
              <ClubBadge club={p.runnerUp} size={44} />
              <div className="min-w-0"><div className="eyebrow">{t("playoffs.runnerUp")}</div><div className="truncate font-heading text-lg font-semibold">{p.runnerUp.name}</div></div>
            </div>
          )}
        </div>
      )}
      <UserCard p={p} />
      <Card title={t("playoffs.roundsLabel")} eyebrow={p.currentRound ? td("season", `playoffs.round.${p.currentRound}`) : undefined}>
        <PlayoffBracket bracket={bracket} careerId={careerId} />
      </Card>
      <SeedsCard p={p} />
    </div>
  );
}
