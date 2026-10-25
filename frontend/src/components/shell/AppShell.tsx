"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { useT } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { useSession } from "@/store/session";
import { BrandMark } from "@/components/brand/BrandMark";
import { IconHome, IconLogout, IconSettings } from "@/components/ui/icons";

const LINKS = [
  { href: "/", key: "section.home", icon: IconHome },
  { href: "/settings", key: "section.settings", icon: IconSettings },
] as const;

/**
 * Shell da plataforma comum: barra superior com a marca, os atalhos (início e configurações) e a conta. Mobile-first,
 * alvos ≥ 44 px, foco visível. A navegação da franquia (sidebar com elenco, partida etc.) chega com os blocos de jogo.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const t = useT("nav");
  const tc = useT("common");
  const pathname = usePathname();
  const router = useRouter();
  const user = useSession((s) => s.user);
  const logout = useSession((s) => s.logout);

  const signOut = async () => {
    await logout().catch(() => undefined);
    router.replace("/");
  };

  return (
    <div className="min-h-dvh flex flex-col">
      <header className="sticky top-0 z-40 border-b border-border bg-pitch/95 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4 sm:px-6">
          <Link href="/" aria-label={t("homeLink")} className="mr-auto rounded-md focus-visible:outline-3 focus-visible:outline-rim">
            <span className="sm:hidden">
              <BrandMark size={28} />
            </span>
            <span className="hidden sm:inline-flex">
              <BrandMark size={30} withName />
            </span>
          </Link>
          <nav aria-label={t("mainNav")} className="flex items-center gap-1">
            {LINKS.map(({ href, key, icon: Icon }) => {
              const active = href === "/" ? pathname === "/" : pathname.startsWith(href);
              return (
                <Link
                  key={href}
                  href={href}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "touch inline-flex items-center justify-center gap-2 rounded-[var(--radius-md)] px-2.5 font-heading text-[0.9rem] transition-colors",
                    active ? "bg-orange/15 text-led" : "text-text-soft hover:bg-arena-3 hover:text-text",
                  )}
                >
                  <Icon size={18} />
                  <span className="hidden sm:inline">{t(key)}</span>
                  <span className="sr-only sm:hidden">{t(key)}</span>
                </Link>
              );
            })}
          </nav>
          {user && (
            <div className="ml-1 flex items-center gap-1 border-l border-border pl-2" aria-label={t("accountMenu")} role="group">
              <span className="hidden max-w-[12rem] truncate text-[0.8rem] text-text-muted md:inline">{user.displayName || t("userFallback")}</span>
              <button type="button" onClick={() => void signOut()} className="touch grid place-items-center rounded-md text-text-muted hover:bg-arena-3 hover:text-text" aria-label={tc("actions.logout")} title={tc("actions.logout")}>
                <IconLogout size={18} />
              </button>
            </div>
          )}
        </div>
      </header>
      <main className="flex-1 min-w-0">{children}</main>
    </div>
  );
}
