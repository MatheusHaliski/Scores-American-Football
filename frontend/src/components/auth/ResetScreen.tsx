"use client";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useSearchParams } from "next/navigation";
import { useT } from "@/i18n/provider";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { InlineAlert } from "@/components/ui/States";
import { api } from "@/lib/api";
import { describeError, fieldErrors } from "@/lib/api/errors";
import { AuthFrame } from "./AuthScreen";
import { validateReset } from "./validate";

/**
 * /auth/reset?token=… — link do e-mail de "esqueci minha senha" (o backend monta `<front>/auth/reset?token=`). Redefinir
 * encerra todas as sessões (HU-RF3 CA33); link inválido ou expirado → 401 INVALID_TOKEN.
 */
export function ResetScreen() {
  const t = useT("auth");
  const token = useSearchParams().get("token")?.trim() ?? "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const errs = validateReset(password, confirm, (k) => t(k));
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setLoading(true);
    try {
      await api.auth.resetPassword({ token, newPassword: password });
      setDone(true);
      setPassword("");
      setConfirm("");
    } catch (err) {
      const f = fieldErrors(err);
      setErrors(f.newPassword ? { password: f.newPassword } : {});
      setError(describeError(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthFrame>
      <h1 className="font-display text-3xl mt-4">{t("title.reset")}</h1>
      <p className="text-text-muted text-sm mt-1">{t("reset.hint")}</p>
      {!token ? (
        <div className="mt-4 flex flex-col gap-4">
          <InlineAlert tone="warn">{t("reset.missingToken")}</InlineAlert>
          <Button href="/auth?mode=forgot" variant="secondary" block>
            {t("reset.requestNew")}
          </Button>
        </div>
      ) : done ? (
        <div className="mt-4 flex flex-col gap-4">
          <InlineAlert tone="good">{t("reset.done")}</InlineAlert>
          <Button href="/auth" size="lg" block>
            {t("submit.login")}
          </Button>
        </div>
      ) : (
        <form onSubmit={submit} className="mt-4 flex flex-col gap-4" noValidate>
          <TextField label={t("field.newPassword")} type="password" autoComplete="new-password" maxLength={128} value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password} hint={t("hint.password")} />
          <TextField label={t("field.confirmPassword")} type="password" autoComplete="new-password" maxLength={128} value={confirm} onChange={(e) => setConfirm(e.target.value)} error={errors.confirm} />
          {error && <InlineAlert tone="bad">{error}</InlineAlert>}
          <Button type="submit" size="lg" block loading={loading}>
            {t("submit.reset")}
          </Button>
          <Link href="/auth?mode=forgot" className="self-center rounded px-2 text-[0.8rem] text-text-muted hover:text-text">
            {t("reset.requestNew")}
          </Link>
        </form>
      )}
    </AuthFrame>
  );
}
