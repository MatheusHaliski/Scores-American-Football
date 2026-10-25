import type { Metadata } from "next";
import { Suspense } from "react";
import { ResetScreen } from "@/components/auth/ResetScreen";

export const metadata: Metadata = { title: "Redefinir senha" };

/** Link do e-mail de recuperação: `/auth/reset?token=…` (HU-RF3 CA30). */
export default function ResetPage() {
  return (
    <Suspense fallback={null}>
      <ResetScreen />
    </Suspense>
  );
}
