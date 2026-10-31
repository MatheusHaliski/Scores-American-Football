"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { ApiError, describeError } from "@/lib/api/errors";
import { issuesFromDetails } from "@/lib/api/live-career";
import { playToMatch } from "@/lib/match/play";
import { isPlayoffKind } from "@/lib/season/bracket";
import { weekSituation } from "@/lib/season/week";
import { useCareer } from "@/store/career";
import { toast } from "@/components/ui/Toast";
import type { DepthIssue } from "@/lib/api/career-types";
import type { Matchup, PlayerRef, Preparation } from "@/lib/api/match-types";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { ClubBadge } from "@/components/career/ClubBadge";
import { TierChip } from "@/components/roster/PlayerCardCompact";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { EmptyState, ErrorState, InlineAlert, Skeleton } from "@/components/ui/States";
import { IconAlert, IconBall, IconCheck, IconTarget, IconWhistle } from "@/components/ui/icons";

export interface PrepareTeamModalProps {
  open: boolean;
  onClose: () => void;
  careerId: string;
  /** depois de jogar a semana (folga fechada ou partida criada), para a tela recarregar */
  onPlayed?: () => void;
}

function Section({ title, children, className }: { title: string; children: React.ReactNode; className?: string }) {
  return (
    <section className={cn("min-w-0 rounded-[var(--radius-lg)] border border-border bg-pitch/60 p-3 sm:p-4", className)}>
      <h3 className="mb-2 font-heading text-[0.72rem] uppercase tracking-[0.16em] text-text-dim">{title}</h3>
      {children}
    </section>
  );
}

function PlayerLine({ p }: { p: PlayerRef }) {
  return (
    <li className="flex min-w-0 items-center gap-2 py-0.5 text-[0.85rem]">
      <TierChip tier={p.tier} overall={p.overall} size="sm" />
      <span className="min-w-0 flex-1 truncate">{p.name}</span>
      <span className="shrink-0 text-[0.72rem] text-text-dim">{p.position} · #{p.jerseyNumber}</span>
    </li>
  );
}

function EdgeBar({ ours, theirs, label }: { ours: number; theirs: number; label: string }) {
  const total = Math.max(1, ours + theirs);
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center justify-between text-[0.78rem]">
        <span className="font-heading tabular text-led">{ours}</span>
        <span className="font-heading uppercase tracking-[0.12em] text-text-muted">{label}</span>
        <span className="font-heading tabular text-text-soft">{theirs}</span>
      </div>
      <div className="flex h-2 overflow-hidden rounded-full bg-arena-4" role="img" aria-label={`${label}: ${ours} × ${theirs}`}>
        <div className="h-full bg-orange" style={{ width: `${(ours / total) * 100}%` }} />
        <div className="h-full bg-arena-5" style={{ width: `${(theirs / total) * 100}%` }} />
      </div>
    </div>
  );
}

function MatchupCard({ m }: { m: Matchup }) {
  const t = useT("game");
  const td = useTd();
  const tone = m.advantage === "OURS" ? "good" : m.advantage === "THEIRS" ? "bad" : "neutral";
  return (
    <li className="rounded-[var(--radius-md)] border border-border bg-arena-2 p-3">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <span className="font-heading font-semibold">{td("enums", `matchup.${m.key}`)}</span>
        <Badge tone={tone} size="xs">{td("enums", `advantage.${m.advantage}`)}{m.edge !== 0 ? ` (${m.edge > 0 ? "+" : ""}${m.edge})` : ""}</Badge>
      </div>
      <EdgeBar ours={m.ours} theirs={m.theirs} label={t("prepare.average")} />
      <div className="mt-2 grid grid-cols-2 gap-3">
        <ul className="min-w-0">{m.ourPlayers.map((p) => <PlayerLine key={p.id} p={p} />)}</ul>
        <ul className="min-w-0">{m.theirPlayers.map((p) => <PlayerLine key={p.id} p={p} />)}</ul>
      </div>
    </li>
  );
}

function IssueList({ issues, tone }: { issues: DepthIssue[]; tone: "bad" | "warn" }) {
  const td = useTd();
  if (!issues.length) return null;
  return (
    <ul className={cn("grid gap-0.5 text-[0.85rem]", tone === "bad" ? "text-[var(--bad-text)]" : "text-[var(--warn-text)]")}>
      {issues.map((i, n) => (
        <li key={`${i.code}-${i.slot}-${n}`}>{td("roster", `depth.issue.${i.code}`, { slot: i.slot ? td("enums", `slotShort.${i.slot}`) : "", depth: i.depth ?? "", name: "—", max: 46 })}</li>
      ))}
    </ul>
  );
}

/**
 * "Preparar e jogar" (RF7): adversário, campanhas, clima, comparação por unidade, confrontos-chave (WR × CB, OL × DL, QB × pass rush,
 * dos dois lados), jogadores-chave, prontidão do depth chart e resumo do plano. "Jogar" chama `POST /weeks/current/play` e abre a
 * partida ao vivo (na semana de folga, encerra a folga e joga a semana seguinte, avisando: D-92); 422 DEPTH_CHART_INVALID mostra o
 * que corrigir e 409 NO_NEXT_GAME encerra a temporada regular.
 */
export function PrepareTeamModal(props: PrepareTeamModalProps) {
  // o conteúdo monta ao abrir (estado novo a cada abertura) e desmonta ao fechar
  return props.open ? <PrepareDialog {...props} /> : null;
}

function PrepareDialog({ open, onClose, careerId, onPlayed }: PrepareTeamModalProps) {
  const t = useT("game");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const router = useRouter();
  const [data, setData] = useState<Preparation | null>(null);
  const [loadError, setLoadError] = useState<ApiError | Error | null>(null);
  const [loading, setLoading] = useState(true);
  const [playing, setPlaying] = useState(false);
  const [playError, setPlayError] = useState<string | null>(null);
  const [blocking, setBlocking] = useState<DepthIssue[]>([]);
  const [byeWeeks, setByeWeeks] = useState<number[]>([]);
  const [tab, setTab] = useState<"OUR_OFFENSE" | "THEIR_OFFENSE">("OUR_OFFENSE");
  // rodada de playoff do jogo da preparação (tipo da semana: WILD_CARD…FINAL), para o título (6c)
  const [roundKind, setRoundKind] = useState<string | null>(null);
  const dashboard = useCareer((s) => s.dashboard);
  const situation = dashboard && dashboard.career.id === careerId ? weekSituation(dashboard) : null;
  const ts = useT("season");

  useEffect(() => {
    let alive = true;
    api.gamePlan
      .preparation(careerId)
      .then((d) => {
        if (!alive) return;
        setData(d);
        api.weeks
          .get(careerId, d.fixture.week)
          .then((b) => alive && setRoundKind(isPlayoffKind(b.kind) ? b.kind : null))
          .catch(() => undefined);
      })
      .catch((e) => alive && setLoadError(e as Error))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [careerId]);

  // rodada de playoff em que o usuário folga ou não joga: não há preparação (409 NO_NEXT_GAME); "jogar" só fecha a semana (D-92/D-96)
  const closeRound = async () => {
    setPlaying(true);
    setPlayError(null);
    try {
      await api.weeks.playCurrent(careerId);
      toast(situation === "playoffBye" ? ts("hub.round.byeDone") : ts("hub.round.outDone"), "good");
      onPlayed?.();
      onClose();
    } catch (e) {
      setPlayError(describeError(e));
    } finally {
      setPlaying(false);
    }
  };

  const play = async () => {
    setPlaying(true);
    setPlayError(null);
    setBlocking([]);
    try {
      // semana de folga: o primeiro "Jogar" só encerra a folga; joga de novo para entrar na partida que a preparação mostra (D-92)
      const { board, byeWeeks: byes } = await playToMatch(api.weeks, careerId, (w) => setByeWeeks((prev) => [...prev, w]));
      onPlayed?.();
      if (board.matchId) {
        onClose();
        router.push(`/c/${careerId}/match?m=${board.matchId}${byes.length ? `&bye=${byes.join(",")}` : ""}`);
      } else if (!byes.length) {
        onClose();
      }
    } catch (e) {
      if (e instanceof ApiError && e.code === "DEPTH_CHART_INVALID") setBlocking(issuesFromDetails(e.problem.details).errors);
      setPlayError(describeError(e));
    } finally {
      setPlaying(false);
    }
  };

  const d = data;
  const noNext = loadError instanceof ApiError && loadError.code === "NO_NEXT_GAME";
  const ready = !!d && d.readiness.ready && blocking.length === 0;
  const matchups = d ? d.keyMatchups.filter((m) => m.side === tab) : [];
  const homeClub = d ? (d.home ? d.club : d.opponent) : null;
  const awayClub = d ? (d.home ? d.opponent : d.club) : null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      size="xl"
      eyebrow={d && roundKind ? ts("round.label", { round: td("season", `playoffs.round.${roundKind}`), week: d.fixture.week }) : t("prepare.eyebrow")}
      title={d ? t("prepare.title", { week: d.fixture.week, opponent: d.opponent.name }) : t("prepare.titleFallback")}
      footer={
        <>
          {d && (
            <div className="mr-auto flex flex-wrap items-center gap-1.5">
              {ready ? <Badge tone="good"><IconCheck size={12} /> {t("prepare.ready")}</Badge> : <Badge tone="bad"><IconAlert size={12} /> {t("prepare.notReady")}</Badge>}
              {d.readiness.warnings.length > 0 && <Badge tone="warn">{t("prepare.warnings", { n: d.readiness.warnings.length })}</Badge>}
            </div>
          )}
          <Button variant="ghost" onClick={onClose} disabled={playing}>{tc("actions.close")}</Button>
          {d?.matchId ? (
            <Button variant="led" size="lg" icon={<IconBall size={18} />} href={`/c/${careerId}/match?m=${d.matchId}`}>{t("prepare.continue")}</Button>
          ) : (
            <Button variant="led" size="lg" icon={<IconBall size={18} />} onClick={() => void play()} loading={playing} disabled={!d || !ready} data-autofocus>
              {t("prepare.play")}
            </Button>
          )}
        </>
      }
    >
      {loading && !d ? (
        <div className="grid gap-3 md:grid-cols-2">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-48 w-full md:col-span-2" />
        </div>
      ) : noNext && (situation === "playoffBye" || situation === "playoffOut") ? (
        <div className="grid gap-3">
          <EmptyState
            icon={<IconBall size={24} />}
            title={t("prepare.noNext.title")}
            body={situation === "playoffBye" ? ts("hub.round.byeBody") : ts("hub.round.outBody")}
            action={<Button variant="led" onClick={() => void closeRound()} loading={playing}>{situation === "playoffBye" ? ts("hub.round.closeBye") : ts("hub.round.closeOut")}</Button>}
          />
          {playError && <InlineAlert tone="bad">{playError}</InlineAlert>}
        </div>
      ) : noNext ? (
        <EmptyState icon={<IconBall size={24} />} title={t("prepare.noNext.title")} body={t("prepare.noNext.body")} />
      ) : loadError || !d || !homeClub || !awayClub ? (
        <ErrorState message={loadError ? describeError(loadError) : null} />
      ) : (
        <div className="flex flex-col gap-3">
          <section className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2 rounded-[var(--radius-lg)] border border-border bg-arena-2 p-3 sm:gap-4 sm:p-4">
            {[awayClub, null, homeClub].map((c, i) =>
              c ? (
                <div key={i} className="flex min-w-0 flex-col items-center gap-1 text-center">
                  <ClubBadge club={c} size={52} />
                  <span className="max-w-full truncate font-heading text-sm font-semibold sm:text-base">{c.name}</span>
                  <span className="text-[0.72rem] text-text-dim">
                    {i === 0 ? tc("away") : tc("home")} · {(c.id === d.club.id ? d.record : d.opponentRecord).summary}
                    {c.id === d.club.id && ` · ${t("prepare.you")}`}
                  </span>
                </div>
              ) : (
                <div key={i} className="text-center">
                  <div className="font-display text-2xl text-led sm:text-4xl">{tc("at")}</div>
                  <div className="mt-1 text-[0.72rem] text-text-dim">{f.dateShort(`${d.fixture.date}T12:00:00Z`)}</div>
                  <div className="text-[0.72rem] text-text-dim">{td("enums", `kickoffShort.${d.fixture.kickoffSlot}`)}</div>
                </div>
              ),
            )}
          </section>

          <div className="grid gap-3 md:grid-cols-3 [&>*]:min-w-0">
            <Section title={t("prepare.weather")}>
              <div className="font-heading text-lg">{td("enums", `weather.${d.weather.condition}`)}</div>
              <p className="text-[0.82rem] text-text-muted">
                {[d.weather.stadium, d.weather.roof && td("enums", `roof.${d.weather.roof}`), d.weather.surface && td("enums", `surface.${d.weather.surface}`)].filter(Boolean).join(" · ") || "—"}
              </p>
            </Section>
            <Section title={t("prepare.units")} className="md:col-span-2">
              <div className="grid gap-2">
                {d.units.map((u) => <EdgeBar key={u.unit} ours={u.ours} theirs={u.theirs} label={td("enums", `unit.${u.unit}`)} />)}
              </div>
            </Section>
          </div>

          <Section title={t("prepare.matchups")}>
            <div className="mb-2 inline-flex rounded-[var(--radius-md)] border border-border bg-pitch p-1" role="radiogroup" aria-label={t("prepare.matchups")}>
              {(["OUR_OFFENSE", "THEIR_OFFENSE"] as const).map((s) => (
                <button key={s} type="button" role="radio" aria-checked={tab === s} onClick={() => setTab(s)} className={cn("h-10 rounded-[var(--radius-sm)] px-3 font-heading text-[0.78rem] font-semibold uppercase tracking-[0.08em]", tab === s ? "bg-orange text-white" : "text-text-muted hover:bg-arena-3 hover:text-text")}>
                  {td("enums", `matchupSide.${s}`)}
                </button>
              ))}
            </div>
            <div className="mb-1 grid grid-cols-2 gap-3 px-3 text-[0.7rem] uppercase tracking-[0.12em] text-text-dim">
              <span className="truncate">{d.club.shortName}</span>
              <span className="truncate">{d.opponent.shortName}</span>
            </div>
            <ul className="grid gap-2 md:grid-cols-2 2xl:grid-cols-3">{matchups.map((m) => <MatchupCard key={`${m.side}-${m.key}`} m={m} />)}</ul>
          </Section>

          <div className="grid gap-3 md:grid-cols-2 [&>*]:min-w-0">
            <Section title={t("prepare.keyPlayers", { team: d.club.shortName })}>
              <ul>{d.keyPlayers.map((p) => <PlayerLine key={p.id} p={p} />)}</ul>
            </Section>
            <Section title={t("prepare.keyPlayers", { team: d.opponent.shortName })}>
              <ul>{d.opponentKeyPlayers.map((p) => <PlayerLine key={p.id} p={p} />)}</ul>
            </Section>
          </div>

          <div className="grid gap-3 md:grid-cols-2 [&>*]:min-w-0">
            <Section title={t("prepare.readiness")}>
              <div className={cn("mb-2 flex items-center gap-2 font-heading font-semibold", d.readiness.ready ? "text-[var(--good-text)]" : "text-[var(--bad-text)]")}>
                {d.readiness.ready ? <IconCheck size={18} /> : <IconAlert size={18} />}
                {d.readiness.ready ? t("prepare.readinessOk", { n: d.readiness.depthChartVersion }) : t("prepare.readinessBad", { n: d.readiness.errors.length })}
              </div>
              <IssueList issues={d.readiness.errors} tone="bad" />
              <IssueList issues={d.readiness.warnings} tone="warn" />
              {(d.readiness.errors.length > 0 || d.readiness.warnings.length > 0) && (
                <Link href={`/c/${careerId}/depth-chart`} className="mt-2 inline-flex items-center gap-1 text-[0.85rem] text-led underline-offset-2 hover:underline">
                  <IconTarget size={14} /> {t("prepare.fixDepth")}
                </Link>
              )}
            </Section>
            <Section title={t("prepare.plan")}>
              <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-[0.85rem]">
                <dt className="text-text-muted">{t("plan.field.passRatio")}</dt>
                <dd className="text-right tabular">{t("plan.passRatio.value", { pass: d.gamePlan.passRatio, run: 100 - d.gamePlan.passRatio })}</dd>
                <dt className="text-text-muted">{t("plan.field.fourthDownAggression")}</dt>
                <dd className="text-right">{td("enums", `aggression.${d.gamePlan.fourthDownAggression}`)}</dd>
                <dt className="text-text-muted">{t("plan.field.tempo")}</dt>
                <dd className="text-right">{td("enums", `tempo.${d.gamePlan.tempo}`)}</dd>
                <dt className="text-text-muted">{t("plan.field.defensiveFocus")}</dt>
                <dd className="text-right">{td("enums", `defensiveFocus.${d.gamePlan.defensiveFocus}`)}</dd>
                <dt className="text-text-muted">{t("plan.field.blitzFrequency")}</dt>
                <dd className="text-right">{td("enums", `blitz.${d.gamePlan.blitzFrequency}`)}</dd>
                <dt className="text-text-muted">{t("plan.field.baseDefense")}</dt>
                <dd className="text-right">{td("enums", `baseDefense.${d.gamePlan.baseDefense}`)}</dd>
                <dt className="text-text-muted">{t("prepare.playbookCustom")}</dt>
                <dd className="text-right tabular">{d.playbookCustomWeights}</dd>
                <dt className="text-text-muted">{t("prepare.callingLevel")}</dt>
                <dd className="text-right">{td("enums", `playCalling.${d.playCallingLevel}`)}</dd>
              </dl>
              <Link href={`/c/${careerId}/playbook`} className="mt-2 inline-flex items-center gap-1 text-[0.85rem] text-led underline-offset-2 hover:underline">
                <IconWhistle size={14} /> {t("prepare.editPlan")}
              </Link>
            </Section>
          </div>

          {byeWeeks.length > 0 && <InlineAlert tone="info">{t("prepare.byeClosed", { week: byeWeeks.join(", ") })}</InlineAlert>}
          {playError && (
            <InlineAlert tone="bad">
              <p>{playError}</p>
              {blocking.length > 0 && (
                <div className="mt-1">
                  <IssueList issues={blocking} tone="bad" />
                  <Link href={`/c/${careerId}/depth-chart`} className="mt-1 inline-flex underline">{t("prepare.fixDepth")}</Link>
                </div>
              )}
            </InlineAlert>
          )}
        </div>
      )}
    </Dialog>
  );
}
