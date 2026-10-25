"use client";
import { useEffect, useState } from "react";
import { analyticsGranted, setAnalyticsConsent } from "@/lib/analytics";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import { NOTIFICATION_KINDS, type ConsentPurpose, type ConsentState, type NotificationKind, type PrivacySettings, type ProfileVisibility } from "@/lib/api/types";
import { useQuery } from "@/lib/hooks/useQuery";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Dialog } from "@/components/ui/Dialog";
import { ErrorState, InlineAlert, PageSkeleton } from "@/components/ui/States";
import { toast } from "@/components/ui/Toast";
import { IconAlert, IconChevronDown } from "@/components/ui/icons";
import { LgpdTag, SectionHeader, SettingRow, Switch } from "./parts";

const VISIBILITY: ProfileVisibility[] = ["ONLY_ME", "FRIENDS", "PUBLIC"];

/**
 * Privacidade (RF46 CA03): visibilidade do perfil (padrão "Só eu") e um consentimento por finalidade —
 * nunca pré-marcado, revogável no mesmo lugar e com o mesmo esforço (Art. 8º §5º). O uso da foto para o
 * avatar 3D é dado biométrico (Art. 11): consentimento específico e destacado, e revogá-lo apaga foto,
 * textura e modelo 3D do "Meu jogador".
 */
export function PrivacySection() {
  const t = useT("settings");
  const q = useQuery(() => api.account.privacy(), []);
  const [busy, setBusy] = useState<string | null>(null);
  const [bioDialog, setBioDialog] = useState<"grant" | "revoke" | null>(null);

  const save = async (key: string, req: Parameters<typeof api.account.updatePrivacy>[0], okMsg: string) => {
    setBusy(key);
    try {
      const next = await api.account.updatePrivacy({ source: "SETTINGS", ...req });
      q.setData(next);
      toast(okMsg, "good");
    } catch (e) {
      toast(describeError(e), "bad");
    } finally {
      setBusy(null);
    }
  };

  const setConsent = (c: ConsentState, granted: boolean) => {
    if (c.purpose === "BIOMETRIC_AVATAR") return setBioDialog(granted ? "grant" : "revoke");
    void save(c.purpose, { consents: { [c.purpose]: granted } }, granted ? t("privacy.consent.granted") : t("privacy.consent.revoked"));
  };

  // conceder ou revogar aqui vale na hora para o coletor de analytics; a sessão carrega o valor no login
  const analyticsOn = analyticsGranted(q.data);
  useEffect(() => setAnalyticsConsent(analyticsOn), [analyticsOn]);

  if (q.loading && !q.data) return <PageSkeleton rows={3} />;
  if (q.error || !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const data = q.data;

  return (
    <div className="flex flex-col gap-5">
      <SectionHeader title={t("privacy.title")} summary={t("privacy.summary")} note={t("privacy.note")} />

      <Card eyebrow={t("privacy.visibility.eyebrow")} title={t("privacy.visibility.title")} action={<LgpdTag article="Art. 6º, VIII" explain={t("lgpd.art6viii")} />}>
        <p className="text-[0.85rem] text-text-muted mb-3">{t("privacy.visibility.explain")}</p>
        <VisibilityPicker value={data.profileVisibility} disabled={busy === "visibility"} onChange={(v) => void save("visibility", { profileVisibility: v }, t("privacy.visibility.saved"))} />
      </Card>

      <Card eyebrow={t("privacy.consents.eyebrow")} title={t("privacy.consents.title")} action={<LgpdTag article="Art. 8º, §5º" explain={t("lgpd.art8p5")} />}>
        <p className="text-[0.85rem] text-text-muted">{t("privacy.consents.explain")}</p>
        <div className="mt-2">
          {data.consents.map((c) => (
            <ConsentRow key={c.purpose} c={c} busy={busy === c.purpose} onChange={(v) => setConsent(c, v)} />
          ))}
        </div>
      </Card>

      <NotificationsCard data={data} busy={busy} onChange={(kind, v) => void save(`n:${kind}`, { notifications: { [kind]: v } }, t("saved"))} />

      <HistoryCard />

      <BiometricDialog
        mode={bioDialog}
        busy={busy === "BIOMETRIC_AVATAR"}
        onClose={() => setBioDialog(null)}
        onConfirm={async () => {
          const granted = bioDialog === "grant";
          await save("BIOMETRIC_AVATAR", { consents: { BIOMETRIC_AVATAR: granted } }, granted ? t("privacy.biometric.grantedToast") : t("privacy.biometric.revokedToast"));
          setBioDialog(null);
        }}
      />
    </div>
  );
}

function VisibilityPicker({ value, onChange, disabled }: { value: ProfileVisibility; onChange: (v: ProfileVisibility) => void; disabled?: boolean }) {
  const t = useT("settings");
  const td = useTd();
  return (
    <div role="radiogroup" aria-label={t("privacy.visibility.title")} className="grid gap-2 sm:grid-cols-3">
      {VISIBILITY.map((v) => (
        <button
          key={v}
          type="button"
          role="radio"
          aria-checked={value === v}
          disabled={disabled}
          onClick={() => onChange(v)}
          className={cn(
            "text-left rounded-[var(--radius-md)] border p-3 transition-colors disabled:opacity-60 focus-visible:outline-3 focus-visible:outline-rim",
            value === v ? "border-orange bg-orange/10" : "border-border bg-pitch hover:border-border-strong",
          )}
        >
          <span className="flex items-center justify-between gap-2">
            <span className="font-heading font-semibold">{td("settings", `enum.profileVisibility.${v}`)}</span>
            {v === "ONLY_ME" && <Badge tone="good" size="xs">{t("privacy.visibility.default")}</Badge>}
          </span>
          <span className="block mt-1 text-[0.8rem] text-text-muted">{t(`privacy.visibility.${v}.hint` as "privacy.visibility.ONLY_ME.hint")}</span>
        </button>
      ))}
    </div>
  );
}

function ConsentRow({ c, busy, onChange }: { c: ConsentState; busy: boolean; onChange: (v: boolean) => void }) {
  const t = useT("settings");
  const td = useTd();
  const f = useFormat();
  const key = c.purpose as ConsentPurpose;
  return (
    <SettingRow
      className={c.sensitive ? "rounded-[var(--radius-md)] border border-warn/40 bg-warn/5 px-3 my-2 !border-b" : undefined}
      label={
        <span className="inline-flex items-center gap-2">
          {t(`privacy.purpose.${key}.title` as "privacy.purpose.ANALYTICS.title")}
          {c.sensitive && (
            <Badge tone="warn" size="xs">
              {t("privacy.sensitive")}
            </Badge>
          )}
          {c.outdated && (
            <Badge tone="info" size="xs">
              {t("privacy.outdated")}
            </Badge>
          )}
        </span>
      }
      tag={<LgpdTag article={c.legalBasis} explain={c.sensitive ? t("lgpd.art11") : t("lgpd.art7i")} />}
      description={
        <>
          {t(`privacy.purpose.${key}.body` as "privacy.purpose.ANALYTICS.body")}
          <span className="block mt-1 text-[0.75rem] text-text-dim">
            {c.updatedAt
              ? t("privacy.consent.lastChange", { state: c.granted ? t("privacy.consent.on") : t("privacy.consent.off"), date: f.dateTime(c.updatedAt), source: c.source ? td("settings", `enum.consentSource.${c.source}`) : "—", version: c.textVersion ?? c.currentTextVersion })
              : t("privacy.consent.never", { version: c.currentTextVersion })}
          </span>
        </>
      }
      control={<Switch checked={c.granted} busy={busy} onChange={onChange} label={t(`privacy.purpose.${key}.title` as "privacy.purpose.ANALYTICS.title")} />}
    />
  );
}

function NotificationsCard({ data, busy, onChange }: { data: PrivacySettings; busy: string | null; onChange: (k: NotificationKind, v: boolean) => void }) {
  const t = useT("settings");
  const td = useTd();
  return (
    <Card eyebrow={t("privacy.notifications.eyebrow")} title={t("privacy.notifications.title")}>
      <p className="text-[0.85rem] text-text-muted mb-1">{t("privacy.notifications.explain")}</p>
      <div className="grid sm:grid-cols-2 gap-x-6">
        {NOTIFICATION_KINDS.map((k) => (
          <div key={k} className="flex items-center justify-between gap-3 py-2.5 border-b border-border/60">
            <span className="text-[0.92rem]">{td("settings", `enum.notificationKind.${k}`)}</span>
            <Switch checked={data.notifications[k] ?? true} busy={busy === `n:${k}`} onChange={(v) => onChange(k, v)} label={td("settings", `enum.notificationKind.${k}`)} />
          </div>
        ))}
      </div>
    </Card>
  );
}

function HistoryCard() {
  const t = useT("settings");
  const td = useTd();
  const f = useFormat();
  const [open, setOpen] = useState(false);
  const q = useQuery(() => api.account.consentHistory(), [open], open);
  return (
    <Card eyebrow={t("privacy.history.eyebrow")} title={t("privacy.history.title")} action={<LgpdTag article="Art. 8º, §2º" explain={t("lgpd.art8p2")} />}>
      <button type="button" aria-expanded={open} onClick={() => setOpen((o) => !o)} className="inline-flex items-center gap-1 text-[0.85rem] text-led hover:underline">
        <IconChevronDown size={16} className={cn("transition-transform", open && "rotate-180")} aria-hidden /> {open ? t("privacy.history.hide") : t("privacy.history.show")}
      </button>
      {open && (
        <div className="mt-3">
          {q.loading ? (
            <PageSkeleton rows={1} />
          ) : q.error ? (
            <ErrorState message={q.error} onRetry={q.refetch} />
          ) : !q.data?.length ? (
            <p className="text-text-dim text-sm">{t("privacy.history.empty")}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <caption className="sr-only">{t("privacy.history.title")}</caption>
                <thead>
                  <tr className="text-left text-[0.7rem] uppercase tracking-[0.1em] text-text-dim font-heading">
                    <th className="py-1.5 pr-3 font-medium">{t("privacy.history.col.when")}</th>
                    <th className="py-1.5 pr-3 font-medium">{t("privacy.history.col.purpose")}</th>
                    <th className="py-1.5 pr-3 font-medium">{t("privacy.history.col.action")}</th>
                    <th className="py-1.5 pr-3 font-medium">{t("privacy.history.col.source")}</th>
                    <th className="py-1.5 font-medium">{t("privacy.history.col.version")}</th>
                  </tr>
                </thead>
                <tbody>
                  {q.data.map((h, i) => (
                    <tr key={`${h.recordedAt}-${i}`} className="border-t border-border/60">
                      <td className="py-1.5 pr-3 whitespace-nowrap">{f.dateTime(h.recordedAt)}</td>
                      <td className="py-1.5 pr-3">{t(`privacy.purpose.${h.purpose}.title` as "privacy.purpose.ANALYTICS.title")}</td>
                      <td className="py-1.5 pr-3">
                        <Badge tone={h.granted ? "good" : "neutral"} size="xs">
                          {h.granted ? t("privacy.consent.on") : t("privacy.consent.off")}
                        </Badge>
                      </td>
                      <td className="py-1.5 pr-3">{td("settings", `enum.consentSource.${h.source}`)}</td>
                      <td className="py-1.5 tabular">{h.textVersion}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}

/** Consentimento biométrico destacado (Art. 11): texto específico, dois botões de mesmo peso, nada pré-marcado. */
function BiometricDialog({ mode, busy, onClose, onConfirm }: { mode: "grant" | "revoke" | null; busy: boolean; onClose: () => void; onConfirm: () => void }) {
  const t = useT("settings");
  if (!mode) return null;
  const grant = mode === "grant";
  return (
    <Dialog
      open
      onClose={onClose}
      eyebrow={t("privacy.biometric.eyebrow")}
      title={grant ? t("privacy.biometric.grantTitle") : t("privacy.biometric.revokeTitle")}
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            {grant ? t("privacy.biometric.notNow") : t("privacy.biometric.keep")}
          </Button>
          <Button variant={grant ? "primary" : "danger"} loading={busy} onClick={onConfirm}>
            {grant ? t("privacy.biometric.authorize") : t("privacy.biometric.revokeAndDelete")}
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-3 text-[0.9rem] text-text-soft">
        <LgpdTag article="Art. 11, I" explain={t("lgpd.art11")} className="self-start" />
        {grant ? (
          <>
            <p>{t("privacy.biometric.grantBody")}</p>
            <ul className="list-disc pl-5 space-y-1 text-[0.85rem] text-text-muted">
              <li>{t("privacy.biometric.point.local")}</li>
              <li>{t("privacy.biometric.point.purpose")}</li>
              <li>{t("privacy.biometric.point.retention")}</li>
              <li>{t("privacy.biometric.point.revoke")}</li>
            </ul>
          </>
        ) : (
          <InlineAlert tone="warn">
            <span className="inline-flex gap-2">
              <IconAlert size={16} className="mt-0.5 shrink-0" aria-hidden />
              {t("privacy.biometric.revokeBody")}
            </span>
          </InlineAlert>
        )}
      </div>
    </Dialog>
  );
}
