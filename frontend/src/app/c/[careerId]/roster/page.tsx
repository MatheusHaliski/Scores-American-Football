"use client";
import { useParams } from "next/navigation";
import { useMemo, useState } from "react";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import { MOVABLE_STATUSES, type MovableStatus, type PlayerCard, type PlayerDetail, type Roster, type Unit } from "@/lib/api/types";
import { useQuery } from "@/lib/hooks/useQuery";
import { fromCents } from "@/lib/career/format";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { PageHeader } from "@/components/shell/PageHeader";
import { PlayerCardCompact } from "@/components/roster/PlayerCardCompact";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Tabs } from "@/components/ui/Tabs";
import { EmptyState, ErrorState, InlineAlert, PageSkeleton } from "@/components/ui/States";
import { toast } from "@/components/ui/Toast";
import { IconSwap, IconTrash, IconUsers } from "@/components/ui/icons";

type TabKey = Unit | "PRACTICE_SQUAD" | "IR";
const TABS: TabKey[] = ["OFFENSE", "DEFENSE", "SPECIAL_TEAMS", "PRACTICE_SQUAD", "IR"];

function cardsOf(r: Roster, tab: TabKey): PlayerCard[] {
  if (tab === "PRACTICE_SQUAD") return r.practiceSquad;
  if (tab === "IR") return r.injuredReserve;
  return r.units[tab];
}

/** Agrupa por posição mantendo a ordem do servidor (posição, overall, número). */
function byPosition(cards: PlayerCard[]): [string, PlayerCard[]][] {
  const out = new Map<string, PlayerCard[]>();
  for (const c of cards) out.set(c.position, [...(out.get(c.position) ?? []), c]);
  return [...out.entries()];
}

function MoveDialog({ careerId, player, onClose, onDone }: { careerId: string; player: PlayerCard | null; onClose: () => void; onDone: () => void }) {
  const t = useT("roster");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const [busy, setBusy] = useState<MovableStatus | "release" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [releasing, setReleasing] = useState<PlayerDetail | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [lastId, setLastId] = useState<string | null>(null);
  if ((player?.id ?? null) !== lastId) {
    setLastId(player?.id ?? null);
    setError(null);
    setReleasing(null);
    setBusy(null);
  }

  const move = async (status: MovableStatus) => {
    if (!player) return;
    setBusy(status);
    setError(null);
    try {
      await api.roster.changeStatus(careerId, player.id, status);
      toast(t("move.done", { name: player.name, status: td("enums", `rosterStatus.${status}`) }), "good");
      onDone();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(null);
    }
  };
  const askRelease = async () => {
    if (!player) return;
    setLoadingDetail(true);
    setError(null);
    try {
      setReleasing(await api.roster.player(careerId, player.id));
    } catch (e) {
      setError(describeError(e));
    } finally {
      setLoadingDetail(false);
    }
  };
  const release = async () => {
    if (!player) return;
    setBusy("release");
    setError(null);
    try {
      const r = await api.roster.release(careerId, player.id);
      toast(t("release.done", { name: r.name, value: f.money(fromCents(r.deadMoney)) }), "info");
      onDone();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(null);
    }
  };

  const dead = releasing?.deadMoneyIfReleased ?? 0;
  const hit = player?.capHit ?? 0;
  return (
    <Dialog
      open={!!player}
      onClose={onClose}
      title={releasing ? t("release.title", { name: player?.name ?? "" }) : t("move.title", { name: player?.name ?? "" })}
      eyebrow={player ? `${player.position} · #${player.jerseyNumber}` : undefined}
      size="sm"
      footer={
        releasing ? (
          <>
            <Button variant="ghost" onClick={() => setReleasing(null)} disabled={busy === "release"}>
              {tc("actions.back")}
            </Button>
            <Button variant="danger" icon={<IconTrash size={16} />} onClick={() => void release()} loading={busy === "release"} data-autofocus>
              {t("release.confirm")}
            </Button>
          </>
        ) : undefined
      }
    >
      {player && !releasing && (
        <div className="grid gap-3">
          <p className="text-[0.9rem] text-text-muted">{t("move.body")}</p>
          <div className="grid gap-2">
            {MOVABLE_STATUSES.filter((s) => s !== player.rosterStatus).map((s) => (
              <Button key={s} variant="secondary" block onClick={() => void move(s)} loading={busy === s} disabled={!!busy} icon={<IconSwap size={16} />} className="justify-start">
                {t("move.to", { status: td("enums", `rosterStatus.${s}`) })}
              </Button>
            ))}
          </div>
          <div className="border-t border-border pt-3">
            <Button variant="danger" block icon={<IconTrash size={16} />} onClick={() => void askRelease()} loading={loadingDetail} disabled={!!busy}>
              {t("action.release")}
            </Button>
          </div>
          {error && <InlineAlert tone="bad">{error}</InlineAlert>}
        </div>
      )}
      {player && releasing && (
        <div className="grid gap-3">
          <p className="text-[0.95rem] leading-relaxed text-text-soft">{t("release.body", { name: player.name })}</p>
          <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-1.5 rounded-[var(--radius-md)] border border-border bg-pitch p-3 text-[0.9rem]">
            <dt className="text-text-muted">{t("release.dead")}</dt>
            <dd className="text-right font-semibold tabular text-[var(--bad-text)]">{f.money(fromCents(dead))}</dd>
            <dt className="text-text-muted">{t("release.capHit")}</dt>
            <dd className="text-right tabular">{f.money(fromCents(hit))}</dd>
            <dt className="text-text">{t("release.net")}</dt>
            <dd className={cn("text-right font-semibold tabular", hit - dead >= 0 ? "text-[var(--good-text)]" : "text-[var(--bad-text)]")}>
              {hit - dead >= 0 ? "+" : "−"}
              {f.money(fromCents(Math.abs(hit - dead)))}
            </dd>
          </dl>
          {error && <InlineAlert tone="bad">{error}</InlineAlert>}
        </div>
      )}
    </Dialog>
  );
}

export default function RosterPage() {
  const t = useT("roster");
  const td = useTd();
  const f = useFormat();
  const { careerId } = useParams<{ careerId: string }>();
  const q = useQuery(() => api.roster.get(careerId), [careerId]);
  const [tab, setTab] = useState<TabKey>("OFFENSE");
  const [acting, setActing] = useState<PlayerCard | null>(null);
  const groups = useMemo(() => (q.data ? byPosition(cardsOf(q.data, tab)) : []), [q.data, tab]);

  if (q.loading && !q.data) return <PageSkeleton rows={3} />;
  if (q.error && !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const r = q.data!;
  const count = (k: TabKey) => cardsOf(r, k).length;

  return (
    <div className="flex flex-col gap-4">
      <PageHeader eyebrow={t("eyebrow")} title={t("title")} description={t("description")} />
      <div className="flex flex-wrap gap-1.5">
        <Badge tone={r.counts.roster > r.rules.rosterMax ? "bad" : "neutral"}>{t("counts.roster", { n: r.counts.roster, max: r.rules.rosterMax })}</Badge>
        <Badge>{t("counts.inactive", { n: r.counts.inactive })}</Badge>
        <Badge>{t("counts.ps", { n: r.counts.practiceSquad, max: r.rules.practiceSquadMax })}</Badge>
        <Badge tone={r.counts.injuredReserve ? "bad" : "neutral"}>{t("counts.ir", { n: r.counts.injuredReserve })}</Badge>
        <Badge>{t("counts.irReturns", { n: r.counts.irReturnsUsed, max: r.rules.irReturnsPerSeason })}</Badge>
        <Badge tone={r.cap.capSpace < 0 ? "bad" : "led"}>{t("counts.capSpace", { value: f.money(fromCents(r.cap.capSpace)) })}</Badge>
      </div>
      <Tabs
        value={tab}
        onChange={setTab}
        tabs={TABS.map((k) => ({ value: k, label: t(`tab.${k}`), badge: <span className="tabular text-[0.7rem] text-text-dim">{count(k)}</span> }))}
      />
      {groups.length === 0 ? (
        <EmptyState compact icon={<IconUsers size={22} />} title={tab === "PRACTICE_SQUAD" ? t("empty.PRACTICE_SQUAD") : tab === "IR" ? t("empty.IR") : t("empty.unit")} />
      ) : (
        <div className="grid gap-5" role="tabpanel">
          {groups.map(([pos, cards]) => (
            <section key={pos} aria-label={td("enums", `position.${pos}`)}>
              <h2 className="mb-2 flex items-baseline gap-2 font-heading text-sm uppercase tracking-[0.14em] text-text-muted">
                <span className="text-led">{pos}</span> {td("enums", `position.${pos}`)} <span className="text-text-dim">· {cards.length}</span>
              </h2>
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {cards.map((c) => (
                  <PlayerCardCompact
                    key={c.id}
                    player={c}
                    href={`/c/${careerId}/players/${c.id}`}
                    actions={
                      <Button variant="ghost" size="sm" onClick={() => setActing(c)} aria-label={t("card.actions", { name: c.name })} aria-haspopup="dialog" className="shrink-0 px-2">
                        <IconSwap size={16} />
                      </Button>
                    }
                  />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
      <MoveDialog
        careerId={careerId}
        player={acting}
        onClose={() => setActing(null)}
        onDone={() => {
          setActing(null);
          void q.refetch();
        }}
      />
    </div>
  );
}
