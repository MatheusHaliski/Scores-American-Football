"use client";
import Link from "next/link";
import { useState, type FormEvent, type ReactNode } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useI18n, useT } from "@/i18n/provider";
import { BrandMark } from "@/components/brand/BrandMark";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { Tabs } from "@/components/ui/Tabs";
import { InlineAlert } from "@/components/ui/States";
import { IconChevronLeft } from "@/components/ui/icons";
import { useSession } from "@/store/session";
import { api, API_MODE } from "@/lib/api";
import { describeError, fieldErrors } from "@/lib/api/errors";
import { safeNext } from "@/lib/devgate/token";
import { validateAuth, type AuthForm, type AuthMode } from "./validate";

/** Moldura comum das telas de conta (entrar, cadastrar, recuperar e redefinir senha). */
export function AuthFrame({ children }: { children: ReactNode }) {
  const t = useT("auth");
  return (
    <main className="min-h-dvh grid place-items-center px-4 py-8" style={{ backgroundImage: "var(--grad-arena), var(--grad-turf)" }}>
      <div className="w-full max-w-md">
        <Link href="/" className="mb-3 inline-flex items-center gap-1 rounded text-[0.85rem] text-text-muted hover:text-text focus-visible:outline-3 focus-visible:outline-rim">
          <IconChevronLeft size={16} /> {t("back")}
        </Link>
        <div className="bg-arena-2 border border-border rounded-[var(--radius-xl)] p-5 sm:p-7 shadow-lg">
          <BrandMark size={36} withName />
          {children}
        </div>
      </div>
    </main>
  );
}

/**
 * /auth — entrar, cadastrar e "esqueci minha senha" (RF1/RF2). Validação igual à do backend (RegisterRequest: senha 8+,
 * nome até 80); erros por campo do 400 VALIDATION aparecem no campo. `?next=` só aceita caminho interno.
 */
export function AuthScreen() {
  const t = useT("auth");
  const { locale } = useI18n();
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const login = useSession((s) => s.login);
  const register = useSession((s) => s.register);
  const initial = params.get("mode");
  const [mode, setMode] = useState<AuthMode>(initial === "register" || initial === "forgot" ? initial : "login");
  const [form, setForm] = useState<AuthForm>({ email: "", password: "", displayName: "", marketingConsent: false });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);

  const switchMode = (m: AuthMode) => {
    setMode(m);
    setErrors({});
    setError(null);
    setSent(false);
  };

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const errs = validateAuth(mode, form, (k) => t(k));
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setLoading(true);
    try {
      if (mode === "login") {
        await login({ email: form.email.trim(), password: form.password });
        router.replace(next);
      } else if (mode === "register") {
        await register({ email: form.email.trim(), password: form.password, displayName: form.displayName.trim(), locale, marketingConsent: form.marketingConsent });
        router.replace(next);
      } else {
        await api.auth.forgotPassword({ email: form.email.trim() });
        setSent(true);
      }
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(describeError(err));
    } finally {
      setLoading(false);
    }
  }

  const title = mode === "login" ? t("title.login") : mode === "register" ? t("title.register") : t("title.forgot");
  const set = (k: keyof AuthForm) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: k === "marketingConsent" ? e.target.checked : e.target.value });

  return (
    <AuthFrame>
      <h1 className="font-display text-3xl mt-4">{title}</h1>
      <p className="text-text-muted text-sm mt-1">{mode === "forgot" ? t("forgot.hint") : API_MODE === "mock" ? t("hint.mock") : t("hint.live")}</p>
      {mode !== "forgot" && (
        <Tabs value={mode} onChange={switchMode} className="mt-4" tabs={[{ value: "login", label: t("tab.login") }, { value: "register", label: t("tab.register") }]} />
      )}
      <form onSubmit={submit} className="mt-4 flex flex-col gap-4" noValidate>
        {mode === "register" && <TextField label={t("field.managerName")} autoComplete="name" maxLength={80} value={form.displayName} onChange={set("displayName")} error={errors.displayName} />}
        <TextField label={t("field.email")} type="email" autoComplete="email" inputMode="email" maxLength={254} value={form.email} onChange={set("email")} error={errors.email} />
        {mode !== "forgot" && (
          <TextField
            label={t("field.password")}
            type="password"
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            maxLength={128}
            value={form.password}
            onChange={set("password")}
            error={errors.password}
            hint={mode === "login" ? undefined : t("hint.password")}
          />
        )}
        {mode === "register" && (
          <label className="flex items-start gap-3 text-[0.85rem] text-text-soft">
            <input type="checkbox" checked={form.marketingConsent} onChange={set("marketingConsent")} className="mt-0.5 size-5 shrink-0 accent-[var(--orange)]" />
            <span>{t("field.marketing")}</span>
          </label>
        )}
        {sent && <InlineAlert tone="good">{t("forgot.sent")}</InlineAlert>}
        {error && <InlineAlert tone="bad">{error}</InlineAlert>}
        <Button type="submit" size="lg" block loading={loading}>
          {mode === "login" ? t("submit.login") : mode === "register" ? t("submit.register") : t("submit.forgot")}
        </Button>
        <button type="button" className="text-[0.8rem] text-text-muted hover:text-text self-center touch px-2 rounded" onClick={() => switchMode(mode === "forgot" ? "login" : "forgot")}>
          {mode === "forgot" ? t("forgot.back") : t("forgot")}
        </button>
      </form>
    </AuthFrame>
  );
}
