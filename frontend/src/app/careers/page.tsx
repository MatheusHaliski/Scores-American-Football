"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import type { CareerList, CareerSummary } from "@/lib/api/types";
import { useQuery } from "@/lib/hooks/useQuery";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { useCareer } from "@/store/career";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { RequireAuth } from "@/components/shell/RequireAuth";
import { ClubBadge } from "@/components/career/ClubBadge";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { TextField } from "@/components/ui/Field";
import { EmptyState, ErrorState, InlineAlert, Skeleton } from "@/components/ui/States";
import { toast } from "@/components/ui/Toast";
import { IconFlag, IconLoop, IconPlus, IconTrash } from "@/components/ui/icons";

function CareerCard({ career, busy, onArchive, onReactivate, onDelete }: { career: CareerSummary; busy: boolean; onArchive: () => void; onReactivate: () => void; onDelete: () => void }) {
  const t = useT("career");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const router = useRouter();
  const setActive = useCareer((s) => s.setActiveCareer);
  const archived = career.status === "ARCHIVED";
  const club = career.clubShortName && career.clubPrimaryColor ? { shortName: career.clubShortName, name: career.clubName ?? undefined, primaryColor: career.clubPrimaryColor, secondaryColor: career.clubSecondaryColor ?? "#ffffff" } : null;
  const open = () => {
    setActive(career.id);
    router.push(`/c/${career.id}`);
  };
  return (
    <article className="flex min-w-0 flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-arena-2 p-4 hover:border-border-strong sm:flex-row sm:items-center">
      <div className="flex min-w-0 flex-1 items-center gap-3">
        {club ? <ClubBadge club={club} size={52} /> : <span className="size-[52px] shrink-0 rounded-[28%] bg-arena-3" aria-hidden />}
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            {career.slot != null && <span className="font-heading text-[0.7rem] uppercase tracking-[0.14em] text-text-dim">{t("list.slot", { n: career.slot })}</span>}
            {archived && <Badge size="xs">{t("list.archivedBadge")}</Badge>}
          </div>
          <h2 className="truncate font-heading text-lg font-semibold">{career.name}</h2>
          <p className="truncate text-[0.85rem] text-text-muted">
            {career.clubName ?? t("list.unemployed")} · {t("list.subtitle", { coach: career.coachName ?? "—", league: career.leagueName ?? "—" })}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            {career.seasonYear != null && <Badge tone="wood" size="xs">{tc("season.n", { n: career.seasonYear })}</Badge>}
            <Badge size="xs">{t("list.week", { week: career.currentWeek, total: career.regularSeasonWeeks })}</Badge>
            <Badge tone="led" size="xs">{t("list.record", { record: career.record.summary })}</Badge>
            <Badge size="xs">{td("enums", `difficulty.${career.difficulty}`)}</Badge>
            {career.lastPlayedAt && <span className="text-[0.72rem] text-text-dim">{t("list.lastPlayed", { when: f.relative(career.lastPlayedAt) })}</span>}
          </div>
        </div>
      </div>
      <div className="flex shrink-0 flex-wrap gap-2 sm:flex-col sm:items-stretch">
        {archived ? (
          <Button variant="secondary" size="sm" icon={<IconLoop size={16} />} onClick={onReactivate} loading={busy} aria-label={t("list.reactivateAria", { name: career.name })}>
            {tc("actions.reactivate")}
          </Button>
        ) : (
          <>
            <Button onClick={open}>{tc("actions.continue")}</Button>
            <Button variant="ghost" size="sm" onClick={onArchive} loading={busy} aria-label={t("list.archiveAria", { name: career.name })}>
              {tc("actions.archive")}
            </Button>
          </>
        )}
        <Button variant="ghost" size="sm" icon={<IconTrash size={16} />} onClick={onDelete} aria-label={t("list.deleteAria", { name: career.name })}>
          {tc("actions.delete")}
        </Button>
      </div>
    </article>
  );
}

function FreeSlot({ slot }: { slot: number }) {
  const t = useT("career");
  return (
    <Link href={`/careers/new?slot=${slot}`} className="flex min-h-24 items-center gap-3 rounded-[var(--radius-lg)] border border-dashed border-border-strong bg-pitch/50 p-4 text-text-muted transition-colors hover:border-rim hover:text-text">
      <span className="grid size-[52px] shrink-0 place-items-center rounded-[28%] bg-arena-3 text-led">
        <IconPlus size={22} />
      </span>
      <span className="min-w-0">
        <span className="block font-heading font-semibold uppercase tracking-[0.08em]">{t("list.slotFree", { n: slot })}</span>
        <span className="block text-[0.85rem]">{t("list.slotFreeHint")}</span>
      </span>
    </Link>
  );
}

function DeleteDialog({ career, onClose, onDeleted }: { career: CareerSummary | null; onClose: () => void; onDeleted: () => void }) {
  const t = useT("career");
  const tc = useT("common");
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastId, setLastId] = useState<string | null>(null);
  if ((career?.id ?? null) !== lastId) {
    setLastId(career?.id ?? null);
    setTyped("");
    setError(null);
  }
  const matches = !!career && typed.trim() === career.name;
  const confirm = async () => {
    if (!career) return;
    if (!matches) {
      setError(t("list.delete.mismatch"));
      return;
    }
    setBusy(true);
    try {
      await api.careers.remove(career.id, typed.trim());
      toast(t("list.delete.done"), "info");
      onDeleted();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open={!!career}
      onClose={onClose}
      title={t("list.delete.title")}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            {tc("actions.cancel")}
          </Button>
          <Button variant="danger" onClick={() => void confirm()} loading={busy} disabled={!matches}>
            {tc("actions.delete")}
          </Button>
        </>
      }
    >
      <form
        className="grid gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          void confirm();
        }}
      >
        <p className="text-[0.95rem] leading-relaxed text-text-soft">{t("list.delete.body", { name: career?.name ?? "" })}</p>
        <p className="rounded-[var(--radius-sm)] border border-border bg-pitch px-3 py-2 font-heading text-base break-words">{career?.name}</p>
        <TextField label={t("list.delete.label")} value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" spellCheck={false} data-autofocus error={error ?? undefined} />
      </form>
    </Dialog>
  );
}

function CareersBody() {
  const t = useT("career");
  const q = useQuery(() => api.careers.list(), []);
  const [toDelete, setToDelete] = useState<CareerSummary | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const patch = async (c: CareerSummary, status: "ACTIVE" | "ARCHIVED") => {
    setBusyId(c.id);
    try {
      const updated = await api.careers.update(c.id, { status, version: c.version });
      toast(status === "ARCHIVED" ? t("list.archive.done") : t("list.reactivate.done", { slot: updated.slot ?? "—" }), "good");
      await q.refetch();
    } catch (e) {
      toast(describeError(e), "bad");
    } finally {
      setBusyId(null);
    }
  };

  const data: CareerList | null = q.data;
  const active = data?.careers.filter((c) => c.status === "ACTIVE") ?? [];
  const archived = data?.careers.filter((c) => c.status !== "ACTIVE") ?? [];
  const full = !!data && data.freeSlots.length === 0;
  const slots = data ? Array.from({ length: data.maxActiveSlots }, (_, i) => i + 1) : [];

  return (
    <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow={t("list.eyebrow")}
        title={t("list.title")}
        description={t("list.description", { max: data?.maxActiveSlots ?? 5 })}
        actions={!full ? <Button href="/careers/new" icon={<IconPlus size={18} />}>{t("list.new")}</Button> : undefined}
      />
      {q.loading ? (
        <div className="grid gap-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-24" />)}</div>
      ) : q.error || !data ? (
        <ErrorState message={q.error} onRetry={q.refetch} />
      ) : (
        <div className="grid gap-6">
          <div className="flex flex-wrap items-center gap-2 text-[0.85rem] text-text-muted">
            <Badge tone={full ? "warn" : "neutral"}>{t("list.slots", { used: data.activeCount, max: data.maxActiveSlots })}</Badge>
          </div>
          {full && <InlineAlert tone="warn">{t("list.full")}</InlineAlert>}
          {data.careers.length === 0 ? (
            <EmptyState icon={<IconFlag size={24} />} title={t("list.empty.title")} body={t("list.empty.body")} action={<Button href="/careers/new" icon={<IconPlus size={18} />}>{t("list.new")}</Button>} />
          ) : (
            <section aria-label={t("list.activeTitle")} className="grid gap-3">
              {slots.map((slot) => {
                const c = active.find((x) => x.slot === slot);
                return c ? <CareerCard key={c.id} career={c} busy={busyId === c.id} onArchive={() => void patch(c, "ARCHIVED")} onReactivate={() => void patch(c, "ACTIVE")} onDelete={() => setToDelete(c)} /> : <FreeSlot key={`free-${slot}`} slot={slot} />;
              })}
            </section>
          )}
          {archived.length > 0 && (
            <section aria-labelledby="archived-title" className="grid gap-3">
              <h2 id="archived-title" className="font-heading text-sm uppercase tracking-[0.16em] text-text-muted">
                {t("list.archived")}
              </h2>
              {archived.map((c) => (
                <CareerCard key={c.id} career={c} busy={busyId === c.id} onArchive={() => void patch(c, "ARCHIVED")} onReactivate={() => void patch(c, "ACTIVE")} onDelete={() => setToDelete(c)} />
              ))}
            </section>
          )}
        </div>
      )}
      <DeleteDialog
        career={toDelete}
        onClose={() => setToDelete(null)}
        onDeleted={() => {
          setToDelete(null);
          void q.refetch();
        }}
      />
    </div>
  );
}

/** RF11 — carreiras salvas: até 5 ativas por slot, arquivadas, arquivar/reativar e excluir confirmando pelo nome. */
export default function CareersPage() {
  return (
    <RequireAuth>
      <AppShell>
        <CareersBody />
      </AppShell>
    </RequireAuth>
  );
}
