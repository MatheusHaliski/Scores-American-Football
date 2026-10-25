"use client";
import Script from "next/script";
import { useCallback, useEffect, useRef, useState } from "react";
import { useI18n, useT } from "@/i18n/provider";
import { BrandMark } from "@/components/brand/BrandMark";
import { cn } from "@/lib/utils/cn";

interface GoogleId {
  initialize(o: { client_id: string; callback: (r: { credential: string }) => void; ux_mode?: "popup"; auto_select?: boolean; cancel_on_tap_outside?: boolean }): void;
  renderButton(el: HTMLElement, o: { theme?: string; size?: string; text?: string; shape?: string; logo_alignment?: string; width?: number; locale?: string }): void;
  disableAutoSelect(): void;
}
declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } };
  }
}

const CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? "";
/** o CSS global dá aos títulos a fonte/cor do tema escuro; o gate usa a própria tipografia sobre fundo claro */
const BODY_FONT = { fontFamily: "var(--font-body)" } as const;

/** Botão no formato do "Continuar com o Google" enquanto o script oficial carrega (ou se falhar). */
function GooglePlaceholder({ label, busy }: { label: string; busy: boolean }) {
  return (
    <div aria-busy={busy} className="relative flex h-[46px] w-full items-center justify-center rounded-lg border border-neutral-300 bg-white text-[0.98rem] text-neutral-800">
      <svg aria-hidden viewBox="0 0 48 48" className="absolute left-4 size-5">
        <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.6 5.4 2.7 13.3l7.9 6.1C12.5 13.6 17.8 9.5 24 9.5z" />
        <path fill="#4285F4" d="M46.1 24.6c0-1.6-.1-3.1-.4-4.6H24v9h12.4c-.5 2.9-2.2 5.3-4.6 6.9l7.4 5.7c4.3-4 6.9-9.9 6.9-17z" />
        <path fill="#FBBC05" d="M10.6 28.6c-.5-1.4-.8-3-.8-4.6s.3-3.2.8-4.6l-7.9-6.1C1 16.6 0 20.2 0 24s1 7.4 2.7 10.7l7.9-6.1z" />
        <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.4-5.7c-2.1 1.4-4.8 2.3-8.5 2.3-6.2 0-11.5-4.1-13.4-9.9l-7.9 6.1C6.6 42.6 14.6 48 24 48z" />
      </svg>
      {label}
    </div>
  );
}
const ERROR_CODES = ["GOOGLE_REQUIRED", "GOOGLE_INVALID", "NOT_ALLOWED", "PIN_INVALID", "PIN_FORMAT", "LOCKED", "GATE_NOT_CONFIGURED"] as const;
type GateError = (typeof ERROR_CODES)[number] | "generic";

/** E-mail do ID token só para exibir "Conectado como…" (a validação real é no servidor). */
function emailOf(credential: string): string | null {
  try {
    const part = credential.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/");
    return (JSON.parse(atob(part + "=".repeat((4 - (part.length % 4)) % 4))) as { email?: string }).email ?? null;
  } catch {
    return null;
  }
}

/**
 * DevAuthGate (/gate): Google + PIN antes de qualquer tela do app, inclusive login e cadastro.
 * O proxy manda para cá com `?next=`; a rota /api/dev-gate valida e grava o cookie assinado.
 */
export default function GatePage() {
  const t = useT("auth");
  const { locale } = useI18n();
  const buttonRef = useRef<HTMLDivElement>(null);
  const [script, setScript] = useState<"loading" | "ready" | "error">("loading");
  const [credential, setCredential] = useState<string | null>(null);
  const [pin, setPin] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<GateError | null>(null);

  const renderGoogle = useCallback(() => {
    const g = window.google?.accounts.id;
    const el = buttonRef.current;
    if (!g || !el || !CLIENT_ID) return;
    g.initialize({ client_id: CLIENT_ID, ux_mode: "popup", auto_select: false, cancel_on_tap_outside: true, callback: (r) => { setCredential(r.credential); setError(null); } });
    el.innerHTML = "";
    g.renderButton(el, { theme: "outline", size: "large", text: "continue_with", shape: "rectangular", logo_alignment: "left", width: Math.min(400, Math.max(240, el.offsetWidth)), locale });
  }, [locale]);

  useEffect(() => {
    if (script === "ready" && !credential) renderGoogle();
  }, [script, credential, renderGoogle]);

  async function verify(e: React.FormEvent) {
    e.preventDefault();
    if (!credential) return setError("GOOGLE_REQUIRED");
    setBusy(true);
    setError(null);
    try {
      const next = new URLSearchParams(window.location.search).get("next") ?? "/";
      const res = await fetch("/api/dev-gate", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ credential, pin, next }) });
      const data = (await res.json().catch(() => ({}))) as { ok?: boolean; next?: string; code?: string };
      if (res.ok && data.ok) {
        window.location.assign(data.next ?? "/");
        return;
      }
      const code = (ERROR_CODES as readonly string[]).includes(data.code ?? "") ? (data.code as GateError) : "generic";
      if (code === "GOOGLE_INVALID") setCredential(null);
      setError(code);
      if (code === "PIN_INVALID") setPin("");
    } catch {
      setError("generic");
    } finally {
      setBusy(false);
    }
  }

  const email = credential ? emailOf(credential) : null;
  const canVerify = !!credential && /^\d{4,12}$/.test(pin) && !busy;

  return (
    <main className="min-h-dvh grid place-items-center bg-[#f6f6f7] px-4 py-10 text-neutral-900">
      {CLIENT_ID && <Script src="https://accounts.google.com/gsi/client" strategy="afterInteractive" onReady={() => setScript("ready")} onError={() => setScript("error")} />}
      <form onSubmit={verify} noValidate className="w-full max-w-[600px] rounded-2xl border border-neutral-200 bg-white px-6 py-8 sm:px-11 sm:py-12 shadow-[0_1px_2px_rgba(0,0,0,0.04)]" aria-labelledby="gate-title">
        <BrandMark size={40} surface="light" withName className="mb-5" />
        <p className="text-[0.8rem] font-medium tracking-[0.22em] text-neutral-500">{t("gate.eyebrow")}</p>
        <h1 id="gate-title" style={BODY_FONT} className="mt-7 text-[1.9rem] sm:text-[2.05rem] font-semibold leading-tight tracking-[-0.01em] text-neutral-900">
          {t("gate.title1")}
          <br />
          {t("gate.title2")}
        </h1>

        <section className="mt-9" aria-labelledby="gate-google">
          <h2 id="gate-google" style={BODY_FONT} className="text-[1.1rem] font-semibold text-neutral-900">{t("gate.google")}</h2>
          <div className="mt-3">
            {!CLIENT_ID ? (
              <p role="alert" className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">{t("gate.notConfigured")}</p>
            ) : credential ? (
              <div className="flex items-center justify-between gap-3 rounded-lg border border-neutral-300 px-4 py-3">
                <span className="min-w-0 truncate text-[0.95rem]">{t("gate.signedAs", { email: email ?? "—" })}</span>
                <button type="button" onClick={() => { setCredential(null); window.google?.accounts.id.disableAutoSelect(); }} className="shrink-0 text-sm font-medium text-neutral-600 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-neutral-900 rounded">
                  {t("gate.switch")}
                </button>
              </div>
            ) : (
              <>
                {script !== "ready" && <GooglePlaceholder label={t("gate.googleContinue")} busy={script === "loading"} />}
                <div ref={buttonRef} className={cn("w-full justify-center", script === "ready" ? "flex min-h-[44px]" : "hidden")} />
                {script === "error" && <p role="alert" className="mt-2 text-sm text-red-700">{t("gate.googleUnavailable")}</p>}
              </>
            )}
          </div>
        </section>

        <section className="mt-8" aria-labelledby="gate-pin-label">
          <label id="gate-pin-label" htmlFor="gate-pin" className="block text-[1.1rem] font-semibold">{t("gate.pin")}</label>
          <input
            id="gate-pin"
            type="password"
            inputMode="numeric"
            autoComplete="off"
            maxLength={12}
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
            placeholder={t("gate.pinPlaceholder")}
            aria-invalid={error === "PIN_INVALID" || error === "PIN_FORMAT"}
            aria-describedby={error ? "gate-error" : undefined}
            className="mt-3 h-14 w-full rounded-lg bg-neutral-600 px-5 text-[1.05rem] text-white placeholder:text-neutral-300 outline-none focus-visible:ring-2 focus-visible:ring-neutral-900 focus-visible:ring-offset-2"
          />
          <button
            type="submit"
            disabled={!canVerify}
            className={cn(
              "mt-3 h-12 w-full rounded-lg text-[1.05rem] font-semibold text-white transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900",
              canVerify ? "bg-neutral-900 hover:bg-neutral-800" : "bg-neutral-400 cursor-not-allowed",
            )}
          >
            {busy ? t("gate.verifying") : t("gate.verify")}
          </button>
          {error && (
            <p id="gate-error" role="alert" className="mt-3 text-sm text-red-700">
              {t(`gate.error.${error}`)}
            </p>
          )}
        </section>

        <p className="mt-8 text-[0.8rem] text-neutral-500">{t("gate.hint")}</p>
      </form>
    </main>
  );
}
