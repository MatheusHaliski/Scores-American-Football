"use client";
import { useEffect, useState, type FormEvent } from "react";
import { api } from "@/lib/api";
import { describeError, fieldErrors } from "@/lib/api/errors";
import type { AccountProfile } from "@/lib/api/types";
import { useQuery } from "@/lib/hooks/useQuery";
import { useT } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { useSession } from "@/store/session";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { TextField } from "@/components/ui/Field";
import { ErrorState, InlineAlert, PageSkeleton } from "@/components/ui/States";
import { toast } from "@/components/ui/Toast";
import { LgpdTag, SectionHeader } from "./parts";

const MIN_AGE = 13;

function fieldError(e: unknown, field: string): string | null {
  return fieldErrors(e)[field] ?? null;
}

/** Conta (RF46 CA02): correção de dados (Art. 18, III), e-mail com verificação de posse e senha com reautenticação. */
export function AccountSection() {
  const t = useT("settings");
  const f = useFormat();
  const q = useQuery(() => api.account.profile(), []);
  const setSessionUser = useSession((s) => s.setUser);
  const sessionUser = useSession((s) => s.user);

  const onSaved = (p: AccountProfile) => {
    q.setData(p);
    if (sessionUser) setSessionUser({ ...sessionUser, displayName: p.displayName, email: p.email, locale: p.locale });
  };

  if (q.loading && !q.data) return <PageSkeleton rows={3} />;
  if (q.error || !q.data) return <ErrorState message={q.error} onRetry={q.refetch} />;
  const p = q.data;

  return (
    <div className="flex flex-col gap-5">
      <SectionHeader title={t("account.title")} summary={t("account.summary")} note={t("account.note")} />
      <ProfileCard profile={p} onSaved={onSaved} />
      <EmailCard profile={p} onChanged={() => void q.refetch()} />
      <PasswordCard />
      <Card eyebrow={t("account.meta.eyebrow")} title={t("account.meta.title")}>
        <dl className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
          <div>
            <dt className="text-text-dim text-[0.75rem] uppercase tracking-[0.1em] font-heading">{t("account.meta.createdAt")}</dt>
            <dd className="text-text">{f.dateLong(p.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-text-dim text-[0.75rem] uppercase tracking-[0.1em] font-heading">{t("account.meta.lastLogin")}</dt>
            <dd className="text-text">{p.lastLoginAt ? f.dateTime(p.lastLoginAt) : "—"}</dd>
          </div>
          <div>
            <dt className="text-text-dim text-[0.75rem] uppercase tracking-[0.1em] font-heading">{t("account.meta.consentAt")}</dt>
            <dd className="text-text">{p.dataProcessingConsentAt ? f.dateTime(p.dataProcessingConsentAt) : "—"}</dd>
          </div>
        </dl>
      </Card>
    </div>
  );
}

function ProfileCard({ profile, onSaved }: { profile: AccountProfile; onSaved: (p: AccountProfile) => void }) {
  const t = useT("settings");
  const [name, setName] = useState(profile.displayName);
  const [birth, setBirth] = useState(profile.birthDate ?? "");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<{ name?: string | null; birth?: string | null; form?: string | null }>({});
  useEffect(() => {
    setName(profile.displayName);
    setBirth(profile.birthDate ?? "");
  }, [profile.displayName, profile.birthDate]);

  const dirty = name.trim() !== profile.displayName || (birth || null) !== (profile.birthDate ?? null);
  const maxBirth = new Date(new Date().setFullYear(new Date().getFullYear() - MIN_AGE)).toISOString().slice(0, 10);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const n = name.trim();
    const next: typeof err = {};
    if (n.length < 2 || n.length > 80) next.name = t("account.profile.nameInvalid");
    if (birth && birth > maxBirth) next.birth = t("account.profile.minAge", { n: MIN_AGE });
    setErr(next);
    if (next.name || next.birth) return;
    setBusy(true);
    try {
      const saved = await api.account.updateProfile({ displayName: n, birthDate: birth || null });
      onSaved(saved);
      toast(t("account.profile.saved"), "good");
    } catch (ex) {
      setErr({ name: fieldError(ex, "displayName"), form: describeError(ex) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card eyebrow={t("account.profile.eyebrow")} title={t("account.profile.title")} action={<LgpdTag article="Art. 18, III" explain={t("lgpd.art18iii")} />}>
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2" noValidate>
        <TextField label={t("account.profile.displayName")} value={name} onChange={(e) => setName(e.target.value)} maxLength={80} autoComplete="nickname" error={err.name} hint={t("account.profile.displayNameHint")} required />
        <div className="flex flex-col gap-1.5">
          <TextField label={t("account.profile.birthDate")} type="date" value={birth} max={maxBirth} onChange={(e) => setBirth(e.target.value)} autoComplete="bday" error={err.birth} hint={t("account.profile.birthDateHint", { n: MIN_AGE })} />
          <LgpdTag article="Art. 6º, III" explain={t("lgpd.art6iii")} className="self-start" />
        </div>
        {err.form && (
          <InlineAlert tone="bad" className="sm:col-span-2">
            {err.form}
          </InlineAlert>
        )}
        <div className="sm:col-span-2 flex justify-end">
          <Button type="submit" loading={busy} disabled={!dirty}>
            {t("account.profile.save")}
          </Button>
        </div>
      </form>
    </Card>
  );
}

function EmailCard({ profile, onChanged }: { profile: AccountProfile; onChanged: () => void }) {
  const t = useT("settings");
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [pwd, setPwd] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return setErr(t("account.email.invalid"));
    if (!pwd) return setErr(t("account.reauth.required"));
    setBusy(true);
    setErr(null);
    try {
      await api.account.changeEmail({ newEmail: email.trim(), currentPassword: pwd });
      toast(t("account.email.sent", { email: email.trim() }), "good");
      setOpen(false);
      setEmail("");
      setPwd("");
      onChanged();
    } catch (ex) {
      setErr(describeError(ex));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card eyebrow={t("account.email.eyebrow")} title={t("account.email.title")} action={<LgpdTag article="Art. 7º, V" explain={t("lgpd.art7v")} />}>
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="text-[0.75rem] uppercase tracking-[0.1em] text-text-dim font-heading">{t("account.email.current")}</div>
            <div className="text-text font-medium break-all">{profile.email}</div>
          </div>
          {!open && (
            <Button variant="secondary" onClick={() => setOpen(true)}>
              {t("account.email.change")}
            </Button>
          )}
        </div>
        {profile.pendingEmail && <InlineAlert tone="warn">{t("account.email.pending", { email: profile.pendingEmail })}</InlineAlert>}
        {open && (
          <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
            <TextField label={t("account.email.new")} type="email" inputMode="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <TextField label={t("account.reauth.password")} type="password" autoComplete="current-password" value={pwd} onChange={(e) => setPwd(e.target.value)} hint={t("account.reauth.hint")} required />
            {err && (
              <InlineAlert tone="bad" className="sm:col-span-2">
                {err}
              </InlineAlert>
            )}
            <p className="sm:col-span-2 text-[0.8rem] text-text-dim">{t("account.email.explain")}</p>
            <div className="sm:col-span-2 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => { setOpen(false); setErr(null); }}>
                {t("actions.cancel")}
              </Button>
              <Button type="submit" loading={busy}>
                {t("account.email.send")}
              </Button>
            </div>
          </form>
        )}
      </div>
    </Card>
  );
}

function PasswordCard() {
  const t = useT("settings");
  const [cur, setCur] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const strength = next.length === 0 ? 0 : next.length < 8 ? 1 : /[A-Z]/.test(next) && /[0-9]/.test(next) && next.length >= 12 ? 3 : 2;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!cur) return setErr(t("account.reauth.required"));
    if (next.length < 8) return setErr(t("account.password.weak"));
    if (next !== confirm) return setErr(t("account.password.mismatch"));
    setBusy(true);
    setErr(null);
    try {
      await api.account.changePassword({ currentPassword: cur, newPassword: next });
      toast(t("account.password.changed"), "good");
      setCur("");
      setNext("");
      setConfirm("");
    } catch (ex) {
      setErr(describeError(ex));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card eyebrow={t("account.password.eyebrow")} title={t("account.password.title")} action={<LgpdTag article="Art. 46" explain={t("lgpd.art46")} />}>
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-3" noValidate>
        <TextField label={t("account.password.current")} type="password" autoComplete="current-password" value={cur} onChange={(e) => setCur(e.target.value)} />
        <TextField label={t("account.password.new")} type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} hint={t(`account.password.strength.${strength}` as "account.password.strength.0")} />
        <TextField label={t("account.password.confirm")} type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        {err && (
          <InlineAlert tone="bad" className="sm:col-span-3">
            {err}
          </InlineAlert>
        )}
        <p className="sm:col-span-2 text-[0.8rem] text-text-dim self-center">{t("account.password.explain")}</p>
        <div className="flex justify-end">
          <Button type="submit" loading={busy} disabled={!cur || !next}>
            {t("account.password.save")}
          </Button>
        </div>
      </form>
    </Card>
  );
}
