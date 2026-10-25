"use client";
import { useParams } from "next/navigation";
import { useMemo, useState } from "react";
import { api } from "@/lib/api";
import { ApiError, describeError } from "@/lib/api/errors";
import { issuesFromDetails } from "@/lib/api/live-career";
import { UNITS, type DepthChart, type DepthIssue, type Roster, type Unit } from "@/lib/api/types";
import { useQuery } from "@/lib/hooks/useQuery";
import { changedSlots, draftFrom, knownAtSlot, sameDraft, slotsOf, toRequest, validateDraft, type DepthPlayerInfo, type Draft } from "@/lib/career/depth";
import { useT, useTd } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { useCareer } from "@/store/career";
import { PageHeader } from "@/components/shell/PageHeader";
import { DepthChartBoard } from "@/components/depth/DepthChartBoard";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Tabs } from "@/components/ui/Tabs";
import { ErrorState, InlineAlert, PageSkeleton } from "@/components/ui/States";
import { toast } from "@/components/ui/Toast";
import { IconCheck, IconLoop } from "@/components/ui/icons";

interface Loaded {
  dc: DepthChart;
  roster: Roster;
}

function infoFrom(roster: Roster, dc: DepthChart): Map<string, DepthPlayerInfo> {
  const m = new Map<string, DepthPlayerInfo>();
  for (const c of [...roster.units.OFFENSE, ...roster.units.DEFENSE, ...roster.units.SPECIAL_TEAMS, ...roster.practiceSquad, ...roster.injuredReserve]) {
    m.set(c.id, { id: c.id, name: c.name, jerseyNumber: c.jerseyNumber, position: c.position, secondaryPosition: c.secondaryPosition, overall: c.overall, tier: c.tier, rosterStatus: c.rosterStatus, suspensionWeeks: c.suspensionWeeks });
  }
  // quem está numa vaga mas já saiu do elenco (dispensado) aparece como indisponível, com o nome que o servidor mandou
  for (const s of slotsOf(dc)) {
    for (const e of s.players) {
      if (!m.has(e.playerId)) m.set(e.playerId, { id: e.playerId, name: e.name, jerseyNumber: e.jerseyNumber, position: e.position, secondaryPosition: null, overall: e.overall, tier: e.tier, rosterStatus: e.available ? "ACTIVE" : e.rosterStatus === "ACTIVE" ? "WAIVED" : e.rosterStatus, suspensionWeeks: 0 });
    }
  }
  return m;
}

function IssueList({ issues, players, tone, max }: { issues: DepthIssue[]; players: Map<string, DepthPlayerInfo>; tone: "bad" | "warn"; max: number }) {
  const td = useTd();
  if (!issues.length) return null;
  return (
    <ul className={cn("grid gap-1 text-[0.85rem]", tone === "bad" ? "text-[var(--bad-text)]" : "text-[var(--warn-text)]")}>
      {issues.map((i, n) => (
        <li key={`${i.code}-${i.slot}-${i.depth}-${n}`}>
          {td("roster", `depth.issue.${i.code}`, {
            slot: i.slot ? td("enums", `slotShort.${i.slot}`) : "",
            depth: i.depth ?? "",
            name: (i.playerId && players.get(i.playerId)?.name) || "—",
            max,
          })}
        </li>
      ))}
    </ul>
  );
}

export default function DepthChartPage() {
  const t = useT("roster");
  const tc = useT("common");
  const td = useTd();
  const { careerId } = useParams<{ careerId: string }>();
  const setDashboard = useCareer((s) => s.setDashboard);
  const q = useQuery<Loaded>(async () => {
    const [dc, roster] = await Promise.all([api.depthChart.get(careerId), api.roster.get(careerId)]);
    return { dc, roster };
  }, [careerId]);

  const [unit, setUnit] = useState<Unit>("OFFENSE");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [byPosition, setByPosition] = useState<Record<string, DepthPlayerInfo["byPosition"]>>({});
  const [saving, setSaving] = useState(false);
  const [serverIssues, setServerIssues] = useState<DepthIssue[] | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<{ current: DepthChart; changed: string[] } | null>(null);

  const loaded = q.data;
  const base = useMemo(() => (loaded ? draftFrom(loaded.dc) : null), [loaded]);
  const slots = useMemo(() => (loaded ? slotsOf(loaded.dc) : []), [loaded]);
  const known = useMemo(() => (loaded ? knownAtSlot(loaded.dc) : new Map<string, number>()), [loaded]);
  const players = useMemo(() => {
    if (!loaded) return new Map<string, DepthPlayerInfo>();
    const m = infoFrom(loaded.roster, loaded.dc);
    for (const [id, bp] of Object.entries(byPosition)) {
      const p = m.get(id);
      if (p) m.set(id, { ...p, byPosition: bp });
    }
    return m;
  }, [loaded, byPosition]);
  const current = draft ?? base;
  const check = useMemo(() => (loaded && current ? validateDraft(slots, current, players, known, loaded.dc.gameDayActives) : null), [loaded, current, slots, players, known]);

  if (q.loading && !loaded) return <PageSkeleton rows={3} />;
  if (q.error || !loaded || !current || !base || !check) return <ErrorState message={q.error} onRetry={q.refetch} />;

  const dirty = !sameDraft(current, base);
  const dc = loaded.dc;

  const fetchPositions = async (playerId: string) => {
    if (byPosition[playerId]) return;
    try {
      const d = await api.roster.player(careerId, playerId);
      setByPosition((m) => ({ ...m, [playerId]: d.overallByPosition }));
    } catch {
      /* sem o detalhe, o aviso de fora de posição fica pela posição */
    }
  };

  const applySaved = (saved: DepthChart) => {
    q.setData((prev) => (prev ? { ...prev, dc: saved } : prev));
    setDraft(null);
    setServerIssues(null);
    setSaveError(null);
    setConflict(null);
    void api.careers.dashboard(careerId).then(setDashboard).catch(() => undefined);
  };

  const save = async (version: number) => {
    setSaving(true);
    setSaveError(null);
    setServerIssues(null);
    try {
      const saved = await api.depthChart.save(careerId, toRequest(slots, current, version));
      applySaved(saved);
      toast(t("depth.saved", { n: saved.version }), "good");
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        try {
          const latest = await api.depthChart.get(careerId);
          setConflict({ current: latest, changed: changedSlots(base, draftFrom(latest)) });
        } catch (e2) {
          setSaveError(describeError(e2));
        }
      } else {
        if (e instanceof ApiError && e.status === 422) setServerIssues(issuesFromDetails(e.problem.details).errors);
        setSaveError(describeError(e));
      }
    } finally {
      setSaving(false);
    }
  };

  const unitSlots = slots.filter((s) => dc.units.find((u) => u.unit === unit)?.slots.some((x) => x.slot === s.slot));
  const errorsBy = (u: Unit) => check.errors.filter((i) => i.slot && dc.units.find((x) => x.unit === u)?.slots.some((s) => s.slot === i.slot)).length;

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        eyebrow={t("depth.eyebrow")}
        title={t("depth.title")}
        description={t("depth.description")}
        actions={
          <>
            <Button variant="ghost" icon={<IconLoop size={16} />} onClick={() => setDraft(null)} disabled={!dirty || saving}>
              {t("depth.reset")}
            </Button>
            <Button variant="led" icon={<IconCheck size={18} />} onClick={() => void save(dc.version)} loading={saving} disabled={!dirty || check.errors.length > 0}>
              {tc("actions.save")}
            </Button>
          </>
        }
      />
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge>{t("depth.version", { n: dc.version })}</Badge>
        <Badge tone={check.uniquePlayers > dc.gameDayActives ? "bad" : "neutral"}>{t("depth.actives", { n: check.uniquePlayers, max: dc.gameDayActives })}</Badge>
        {dirty ? <Badge tone="warn">{t("depth.unsaved")}</Badge> : <Badge tone={check.errors.length ? "bad" : "good"}>{check.errors.length ? t("depth.invalid") : t("depth.ready")}</Badge>}
        {check.warnings.length > 0 && <Badge tone="warn">{t("depth.warningsCount", { n: check.warnings.length })}</Badge>}
      </div>

      {(check.errors.length > 0 || check.warnings.length > 0 || saveError) && (
        <section aria-label={t("depth.issues.title")} className="grid gap-2 rounded-[var(--radius-lg)] border border-border bg-arena-2 p-3">
          {saveError && <InlineAlert tone="bad">{saveError}</InlineAlert>}
          {serverIssues && <IssueList issues={serverIssues} players={players} tone="bad" max={dc.gameDayActives} />}
          {check.errors.length > 0 && (
            <div>
              <h2 className="mb-1 font-heading text-[0.7rem] uppercase tracking-[0.14em] text-text-dim">{t("depth.issues.errors")}</h2>
              <IssueList issues={check.errors} players={players} tone="bad" max={dc.gameDayActives} />
            </div>
          )}
          {check.warnings.length > 0 && (
            <details>
              <summary className="cursor-pointer font-heading text-[0.7rem] uppercase tracking-[0.14em] text-text-dim">{t("depth.issues.warnings", { n: check.warnings.length })}</summary>
              <div className="mt-1">
                <IssueList issues={check.warnings} players={players} tone="warn" max={dc.gameDayActives} />
              </div>
            </details>
          )}
        </section>
      )}

      <Tabs
        value={unit}
        onChange={setUnit}
        tabs={UNITS.map((u) => ({ value: u, label: td("enums", `unit.${u}`), badge: errorsBy(u) ? <Badge tone="bad" size="xs">{errorsBy(u)}</Badge> : undefined }))}
      />
      <div role="tabpanel">
        <DepthChartBoard slots={unitSlots} draft={current} onChange={setDraft} players={players} known={known} issues={check.errors} onPick={(id) => void fetchPositions(id)} disabled={saving} />
      </div>

      <Dialog
        open={!!conflict}
        onClose={() => setConflict(null)}
        title={t("depth.conflict.title")}
        size="sm"
        footer={
          conflict && (
            <>
              <Button variant="ghost" onClick={() => conflict && applySaved(conflict.current)} disabled={saving}>
                {t("depth.conflict.reload")}
              </Button>
              <Button variant="danger" onClick={() => conflict && void save(conflict.current.version)} loading={saving}>
                {t("depth.conflict.overwrite")}
              </Button>
            </>
          )
        }
      >
        {conflict && (
          <div className="grid gap-3 text-[0.92rem] text-text-soft">
            <p>{t("depth.conflict.body", { mine: dc.version, current: conflict.current.version })}</p>
            <p className="rounded-[var(--radius-md)] border border-border bg-pitch p-3 text-[0.85rem]">
              {conflict.changed.length ? t("depth.conflict.changed", { slots: conflict.changed.map((s) => td("enums", `slotShort.${s}`)).join(", ") }) : t("depth.conflict.noDiff")}
            </p>
          </div>
        )}
      </Dialog>
    </div>
  );
}
