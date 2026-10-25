import type { Metadata } from "next";
import { Suspense } from "react";
import { VerifyEmailScreen } from "@/components/auth/VerifyEmailScreen";

export const metadata: Metadata = { title: "Confirmar e-mail" };

/** Link do e-mail de troca de endereço: `/settings/verify-email?token=…` (RF46 CA02, montado pelo EmailChangeUseCases). */
export default function VerifyEmailPage() {
  return (
    <Suspense fallback={null}>
      <VerifyEmailScreen />
    </Suspense>
  );
}
