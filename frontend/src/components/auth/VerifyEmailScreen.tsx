"use client";
import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useT } from "@/i18n/provider";
import { Button } from "@/components/ui/Button";
import { InlineAlert } from "@/components/ui/States";
import { api } from "@/lib/api";
import { AuthFrame } from "./AuthScreen";

type State = "checking" | "done" | "failed";

/**
 * /settings/verify-email?token=… — o link vai para o endereço novo e prova a posse dele (RF46 CA02). Público: o token é a
 * prova, não precisa estar logado. Confirma uma vez só (o link é de uso único).
 */
export function VerifyEmailScreen() {
  const t = useT("auth");
  const token = useSearchParams().get("token")?.trim() ?? "";
  const [state, setState] = useState<State>("checking");
  const sent = useRef(false);

  useEffect(() => {
    if (!token || sent.current) return;
    sent.current = true;
    api.auth
      .verifyEmail(token)
      .then(() => setState("done"))
      .catch(() => setState("failed"));
  }, [token]);

  return (
    <AuthFrame>
      <h1 className="font-display text-3xl mt-4">{t("title.verifyEmail")}</h1>
      <div className="mt-4 flex flex-col gap-4" aria-live="polite">
        {!token ? (
          <InlineAlert tone="warn">{t("verifyEmail.missingToken")}</InlineAlert>
        ) : state === "checking" ? (
          <p className="text-text-muted">{t("verifyEmail.checking")}</p>
        ) : state === "done" ? (
          <InlineAlert tone="good">{t("verifyEmail.done")}</InlineAlert>
        ) : (
          <InlineAlert tone="bad">{t("verifyEmail.failed")}</InlineAlert>
        )}
        {(state !== "checking" || !token) && (
          <Button href="/settings?s=account" variant="secondary" block>
            {t("verifyEmail.toSettings")}
          </Button>
        )}
      </div>
    </AuthFrame>
  );
}
