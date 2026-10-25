"use client";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import type { DeletionStatus, ExportJob } from "@/lib/api/types";
import { useQuery } from "@/lib/hooks/useQuery";
import { useI18n, useT, useTd, type TFunction } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { useSession } from "@/store/session";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Dialog } from "@/components/ui/Dialog";
import { TextField } from "@/components/ui/Field";
import { ErrorState, InlineAlert, PageSkeleton } from "@/components/ui/States";
import { toast } from "@/components/ui/Toast";
import { IconAlert, IconArrowDown, IconClock, IconLogout, IconTrash } from "@/components/ui/icons";
import { LgpdTag, SectionHeader } from "./parts";

/** Palavra de confirmação por idioma — o backend aceita qualquer uma das três. */
const CONFIRM_WORD: Record<string, string> = { "pt-BR": "EXCLUIR", "en-US": "DELETE", "es-ES": "ELIMINAR" };

/** Seus dados (RF46 CA07): portabilidade (Art. 18, II/V), histórico de atividades (Art. 9º), sessões e exclusão (Art. 18, VI). */
export function DataSection() {
  const t = useT("settings");
  return (
    <div className="flex flex-col gap-5">
      <SectionHeader title={t("data.title")} summary={t("data.summary")} note={t("data.note")} />
      <ExportCard />
      <SessionsCard />
      <ActivityCard />
      <DangerZone />
    </div>
  );
}

function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function ExportCard() {
  const t = useT("settings");
  const td = useTd();
  const f = useFormat();
  const q = useQuery(() => api.account.exportJobs(), []);
  const [busy, setBusy] = useState(false);
  const [downloading, setDownloading] = useState<string | null>(null);
  const pending = q.data?.find((j) => j.status === "REQUESTED") ?? null;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // acompanha o pedido em preparo (exportação assíncrona)
  useEffect(() => {
    if (!pending) return;
    timer.current = setTimeout(async () => {
      try {
        const j = await api.account.exportJob(pending.id);
        q.setData((prev) => (prev ?? []).map((x) => (x.id === j.id ? j : x)));
        if (j.status === "READY") toast(t("data.export.readyToast"), "good");
      } catch {
        /* tenta de novo no próximo ciclo */
      }
    }, 1500);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pending?.id, pending?.status, q.data]);

  const request = async () => {
    setBusy(true);
    try {
      const j = await api.account.requestExport();
      q.setData((prev) => [j, ...(prev ?? []).filter((x) => x.id !== j.id)]);
      toast(t("data.export.requested"), "info");
    } catch (e) {
      toast(describeError(e), "bad");
    } finally {
      setBusy(false);
    }
  };

  const download = async (j: ExportJob, format: "json" | "csv") => {
    setDownloading(`${j.id}:${format}`);
    try {
      const blob = await api.account.downloadExport(j.id, format);
      saveBlob(blob, `scores-american-football-meus-dados-${j.requestedAt.slice(0, 10)}.${format}`);
    } catch (e) {
      toast(describeError(e), "bad");
    } finally {
      setDownloading(null);
    }
  };

  return (
    <Card eyebrow={t("data.export.eyebrow")} title={t("data.export.title")} action={<LgpdTag article="Art. 18, II e V" explain={t("lgpd.art18v")} />}>
      <p className="text-[0.85rem] text-text-muted">{t("data.export.explain")}</p>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <Button onClick={request} loading={busy || !!pending} icon={<IconArrowDown size={16} />}>
          {pending ? t("data.export.preparing") : t("data.export.request")}
        </Button>
        <span className="text-[0.78rem] text-text-dim">{t("data.export.ttl")}</span>
      </div>
      {q.loading && !q.data ? (
        <PageSkeleton rows={1} />
      ) : q.error ? (
        <ErrorState message={q.error} onRetry={q.refetch} />
      ) : q.data && q.data.length > 0 ? (
        <ul className="mt-4 divide-y divide-border/60 rounded-[var(--radius-md)] border border-border">
          {q.data.slice(0, 5).map((j) => (
            <li key={j.id} className="flex flex-wrap items-center justify-between gap-3 p-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <Badge tone={j.status === "READY" ? "good" : j.status === "REQUESTED" ? "info" : j.status === "FAILED" ? "bad" : "neutral"} size="xs">
                    {td("settings", `enum.exportStatus.${j.status}`)}
                  </Badge>
                  <span className="text-[0.85rem] text-text">{t("data.export.requestedAt", { date: f.dateTime(j.requestedAt) })}</span>
                </div>
                {j.status === "READY" && j.expiresAt && (
                  <div className="mt-1 text-[0.75rem] text-text-dim inline-flex items-center gap-1">
                    <IconClock size={12} aria-hidden /> {t("data.export.expires", { date: f.dateTime(j.expiresAt), size: f.num(Math.max(1, Math.round(j.sizeBytes / 1024))) })}
                  </div>
                )}
              </div>
              {j.status === "READY" && (
                <div className="flex gap-2">
                  <Button size="sm" variant="secondary" loading={downloading === `${j.id}:json`} onClick={() => void download(j, "json")}>
                    JSON
                  </Button>
                  <Button size="sm" variant="secondary" loading={downloading === `${j.id}:csv`} onClick={() => void download(j, "csv")}>
                    CSV
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      ) : null}
    </Card>
  );
}

function SessionsCard() {
  const t = useT("settings");
  const f = useFormat();
  const q = useQuery(() => api.account.sessions(), []);
  const [busy, setBusy] = useState<string | null>(null);
  const revoke = async (id: string) => {
    setBusy(id);
    try {
      await api.account.revokeSession(id);
      q.setData((prev) => (prev ?? []).filter((s) => s.id !== id));
      toast(t("data.sessions.revoked"), "good");
    } catch (e) {
      toast(describeError(e), "bad");
    } finally {
      setBusy(null);
    }
  };
  return (
    <Card eyebrow={t("data.sessions.eyebrow")} title={t("data.sessions.title")} action={<LgpdTag article="Art. 46" explain={t("lgpd.art46")} />}>
      <p className="text-[0.85rem] text-text-muted mb-3">{t("data.sessions.explain")}</p>
      {q.loading && !q.data ? (
        <PageSkeleton rows={1} />
      ) : q.error ? (
        <ErrorState message={q.error} onRetry={q.refetch} />
      ) : (
        <ul className="divide-y divide-border/60 rounded-[var(--radius-md)] border border-border">
          {(q.data ?? []).map((s) => (
            <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 p-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2 font-medium text-text">
                  <span className="truncate">{s.userAgent || t("data.sessions.unknownDevice")}</span>
                  {s.current && (
                    <Badge tone="good" size="xs">
                      {t("data.sessions.current")}
                    </Badge>
                  )}
                </div>
                <div className="text-[0.75rem] text-text-dim">{t("data.sessions.meta", { ip: s.ip || "—", date: f.dateTime(s.createdAt) })}</div>
              </div>
              {!s.current && (
                <Button size="sm" variant="ghost" icon={<IconLogout size={14} />} loading={busy === s.id} onClick={() => void revoke(s.id)}>
                  {t("data.sessions.revoke")}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function ActivityCard() {
  const t = useT("settings");
  const f = useFormat();
  const [page, setPage] = useState(0);
  const q = useQuery(() => api.account.activity(page, 10), [page]);
  return (
    <Card eyebrow={t("data.activity.eyebrow")} title={t("data.activity.title")} action={<LgpdTag article="Art. 9º" explain={t("lgpd.art9")} />}>
      <p className="text-[0.85rem] text-text-muted mb-3">{t("data.activity.explain")}</p>
      {q.loading && !q.data ? (
        <PageSkeleton rows={1} />
      ) : q.error ? (
        <ErrorState message={q.error} onRetry={q.refetch} />
      ) : !q.data?.content.length ? (
        <p className="text-text-dim text-sm">{t("data.activity.empty")}</p>
      ) : (
        <>
          <ul className="divide-y divide-border/60 rounded-[var(--radius-md)] border border-border text-sm">
            {q.data.content.map((a, i) => (
              <li key={`${a.timestamp}-${i}`} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
                <span className="text-text">{activityLabel(t, a.action)}</span>
                <span className="text-[0.75rem] text-text-dim tabular">
                  {f.dateTime(a.timestamp)}
                  {a.ipMasked ? ` · ${a.ipMasked}` : ""}
                </span>
              </li>
            ))}
          </ul>
          {q.data.totalPages > 1 && (
            <div className="mt-3 flex items-center justify-between text-[0.8rem] text-text-muted">
              <Button size="sm" variant="ghost" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                {t("data.activity.prev")}
              </Button>
              <span>{t("data.activity.page", { page: page + 1, total: q.data.totalPages })}</span>
              <Button size="sm" variant="ghost" disabled={page + 1 >= q.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                {t("data.activity.next")}
              </Button>
            </div>
          )}
        </>
      )}
    </Card>
  );
}

/** Ações de auditoria conhecidas têm rótulo traduzido; as demais aparecem com o código técnico. */
function activityLabel(t: TFunction<"settings">, action: string): string {
  const key = `data.activity.action.${action}`;
  const label = t(key as "data.activity.action.LOGIN");
  return label.startsWith("settings:") ? action : label;
}

function DangerZone() {
  const t = useT("settings");
  const f = useFormat();
  const { locale } = useI18n();
  const router = useRouter();
  const logout = useSession((s) => s.logout);
  const q = useQuery(() => api.account.deletionStatus(), []);
  const [open, setOpen] = useState(false);
  const [pwd, setPwd] = useState("");
  const [word, setWord] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const expected = CONFIRM_WORD[locale] ?? "EXCLUIR";
  const matches = word.trim().toUpperCase() === expected;

  const confirm = async () => {
    setBusy(true);
    setErr(null);
    try {
      const st: DeletionStatus = await api.account.deleteAccount({ currentPassword: pwd, confirmation: word.trim().toUpperCase() });
      q.setData(st);
      setOpen(false);
      setPwd("");
      setWord("");
      toast(t("data.delete.scheduledToast"), "info");
    } catch (e) {
      setErr(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    setBusy(true);
    try {
      q.setData(await api.account.cancelDeletion());
      toast(t("data.delete.cancelledToast"), "good");
    } catch (e) {
      toast(describeError(e), "bad");
    } finally {
      setBusy(false);
    }
  };

  const pendingDeletion = q.data?.status === "PENDING_DELETION";

  return (
    <section aria-labelledby="danger-zone" className="rounded-[var(--radius-lg)] border-2 border-bad/60 bg-bad/5 p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <div className="eyebrow text-bad">{t("data.delete.eyebrow")}</div>
          <h3 id="danger-zone" className="font-heading font-semibold text-lg">
            {t("data.delete.title")}
          </h3>
        </div>
        <LgpdTag article="Art. 18, VI" explain={t("lgpd.art18vi")} />
      </div>
      {q.loading && !q.data ? (
        <PageSkeleton rows={1} />
      ) : pendingDeletion ? (
        <div className="mt-3 flex flex-col gap-3">
          <InlineAlert tone="warn">{t("data.delete.pending", { date: q.data?.scheduledFor ? f.dateLong(q.data.scheduledFor) : "—" })}</InlineAlert>
          <div className="flex flex-wrap gap-2">
            <Button variant="primary" loading={busy} onClick={() => void cancel()}>
              {t("data.delete.cancel")}
            </Button>
            <Button
              variant="ghost"
              icon={<IconLogout size={16} />}
              onClick={async () => {
                await logout();
                router.replace("/");
              }}
            >
              {t("data.delete.logout")}
            </Button>
          </div>
        </div>
      ) : (
        <>
          <ul className="mt-3 list-disc pl-5 text-[0.85rem] text-text-soft space-y-1">
            <li>{t("data.delete.point.grace")}</li>
            <li>{t("data.delete.point.anonymize")}</li>
            <li>{t("data.delete.point.media")}</li>
            <li>{t("data.delete.point.export")}</li>
          </ul>
          <Button className="mt-4" variant="danger" icon={<IconTrash size={16} />} onClick={() => setOpen(true)}>
            {t("data.delete.open")}
          </Button>
        </>
      )}

      <Dialog
        open={open}
        onClose={() => !busy && setOpen(false)}
        eyebrow={t("data.delete.eyebrow")}
        title={t("data.delete.dialogTitle")}
        footer={
          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="secondary" onClick={() => setOpen(false)} disabled={busy}>
              {t("actions.cancel")}
            </Button>
            <Button variant="danger" loading={busy} disabled={!pwd || !matches} onClick={() => void confirm()}>
              {t("data.delete.confirm")}
            </Button>
          </div>
        }
      >
        <div className="flex flex-col gap-3">
          <InlineAlert tone="bad">
            <span className="inline-flex gap-2">
              <IconAlert size={16} className="mt-0.5 shrink-0" aria-hidden />
              {t("data.delete.dialogBody")}
            </span>
          </InlineAlert>
          <TextField label={t("account.reauth.password")} type="password" autoComplete="current-password" value={pwd} onChange={(e) => setPwd(e.target.value)} />
          <TextField label={t("data.delete.typeWord", { word: expected })} value={word} onChange={(e) => setWord(e.target.value)} autoComplete="off" autoCapitalize="characters" spellCheck={false} hint={matches ? t("data.delete.wordOk") : undefined} />
          {err && <InlineAlert tone="bad">{err}</InlineAlert>}
        </div>
      </Dialog>
    </section>
  );
}
