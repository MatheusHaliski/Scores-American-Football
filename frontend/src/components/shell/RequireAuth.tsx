"use client";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useSession } from "@/store/session";
import { PageSkeleton } from "@/components/ui/States";

/** Redireciona para /auth quando não há usuário (após hidratar o store). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const user = useSession((s) => s.user);
  const hydrated = useSession((s) => s.hydrated);
  const router = useRouter();
  useEffect(() => {
    if (hydrated && !user) router.replace(`/auth?next=${encodeURIComponent(window.location.pathname)}`);
  }, [hydrated, user, router]);
  if (!hydrated || !user) return <PageSkeleton rows={2} />;
  return <>{children}</>;
}
