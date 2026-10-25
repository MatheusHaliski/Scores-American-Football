import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthScreen } from "@/components/auth/AuthScreen";

export const metadata: Metadata = { title: "Entrar" };

/** Entrar, cadastrar e recuperar a senha (RF1/RF2). */
export default function AuthPage() {
  return (
    <Suspense fallback={null}>
      <AuthScreen />
    </Suspense>
  );
}
