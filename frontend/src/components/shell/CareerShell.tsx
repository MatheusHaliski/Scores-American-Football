"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { api } from "@/lib/api";
import { useT, useTd } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { useCareer } from "@/store/career";
import { useSession } from "@/store/session";
import { BrandMark } from "@/components/brand/BrandMark";
import { ClubBadge } from "@/components/career/ClubBadge";
import { Dialog } from "@/components/ui/Dialog";
import { IconChevronRight, IconFlag, IconLogout, IconMenu, IconSettings } from "@/components/ui/icons";
import { RequireAuth } from "./RequireAuth";
import { GROUPS, NAV_SECTIONS, sectionHref, type Section } from "./sections";

function NavItem({ s, careerId, active }: { s: Section; careerId: string; active: boolean }) {
  const t = useT("nav");
  const Icon = s.icon;
  return (
    <Link
      href={sectionHref(careerId, s)}
      aria-current={active ? "page" : undefined}
      className={cn(
        "flex h-11 items-center gap-3 rounded-[var(--radius-md)] border px-3 font-heading text-[0.9rem] tracking-[0.02em] transition-colors",
        active ? "border-orange/40 bg-orange/15 text-led" : "border-transparent text-text-soft hover:bg-arena-3 hover:text-text",
      )}
    >
      <Icon size={20} className={cn("shrink-0", active ? "text-led" : "text-text-muted")} />
      <span className="flex-1 truncate">{t(`section.${s.labelKey}`)}</span>
    </Link>
  );
}

/**
 * Layout da carreira: barra lateral fixa (desktop) com as seções prontas da central da franquia, topo e barra inferior no
 * celular com "Mais". Carrega o dashboard para o topo (franquia, campanha, semana). Mobile-first, alvos ≥ 44 px, foco visível.
 */
export function CareerShell({ careerId, children }: { careerId: string; children: ReactNode }) {
  return (
    <RequireAuth>
      <CareerFrame careerId={careerId}>{children}</CareerFrame>
    </RequireAuth>
  );
}

function CareerFrame({ careerId, children }: { careerId: string; children: ReactNode }) {
  const t = useT("nav");
  const td = useTd();
  const tc = useT("common");
  const pathname = usePathname();
  const router = useRouter();
  const dashboard = useCareer((s) => s.dashboard);
  const setActive = useCareer((s) => s.setActiveCareer);
  const setDashboard = useCareer((s) => s.setDashboard);
  const user = useSession((s) => s.user);
  const logout = useSession((s) => s.logout);
  const [more, setMore] = useState(false);
  const base = `/c/${careerId}`;
  const rest = pathname.startsWith(base) ? pathname.slice(base.length).replace(/^\//, "") : "";
  const first = rest.split("/")[0];
  const activeKey = first === "players" ? "roster" : first;

  useEffect(() => {
    setActive(careerId);
    let alive = true;
    api.careers
      .dashboard(careerId)
      .then((d) => alive && setDashboard(d))
      .catch(() => alive && setDashboard(null));
    return () => {
      alive = false;
    };
  }, [careerId, setActive, setDashboard]);

  // fecha o menu "Mais" ao trocar de tela
  const [lastPath, setLastPath] = useState(pathname);
  if (lastPath !== pathname) {
    setLastPath(pathname);
    setMore(false);
  }

  const current = dashboard && dashboard.career.id === careerId ? dashboard : null;
  const club = current?.club ?? null;
  const mobileItems = NAV_SECTIONS.filter((s) => s.mobile);
  const signOut = async () => {
    await logout().catch(() => undefined);
    router.replace("/");
  };
  // depois da temporada regular (playoffs e entressafra, 6c) o topo mostra a fase em vez de "semana 19 de 18"
  const status = !current
    ? null
    : current.currentWeek > current.career.regularSeasonWeeks && current.career.seasonPhase
      ? t("clubStatusPhase", { record: current.record.summary, phase: td("enums", `seasonPhase.${current.career.seasonPhase}`) })
      : t("clubStatus", { record: current.record.summary, week: current.currentWeek, total: current.career.regularSeasonWeeks });

  const groups = (onItem?: "dialog") =>
    GROUPS.filter((g) => NAV_SECTIONS.some((s) => s.group === g)).map((g) => (
      <div key={g}>
        <div className={cn("pb-1 font-heading text-[0.62rem] uppercase tracking-[0.2em] text-text-dim", !onItem && "px-3")}>{t(`group.${g}`)}</div>
        <div className={onItem ? "grid grid-cols-1 gap-1 sm:grid-cols-2" : "space-y-0.5"}>
          {NAV_SECTIONS.filter((s) => s.group === g).map((s) => {
            if (!onItem) return <NavItem key={s.key} s={s} careerId={careerId} active={activeKey === s.key} />;
            const Icon = s.icon;
            return (
              <Link key={s.key} href={sectionHref(careerId, s)} className="flex h-12 items-center gap-3 rounded-[var(--radius-md)] px-3 text-text-soft hover:bg-arena-3">
                <Icon size={20} className="text-text-muted" />
                <span className="flex-1 text-[0.9rem]">{t(`section.${s.labelKey}`)}</span>
                <IconChevronRight size={16} className="text-text-dim" />
              </Link>
            );
          })}
          {g === "conta" && (
            <Link href="/careers" className={onItem ? "flex h-12 items-center gap-3 rounded-[var(--radius-md)] px-3 text-text-soft hover:bg-arena-3" : "flex h-11 items-center gap-3 rounded-[var(--radius-md)] border border-transparent px-3 font-heading text-[0.9rem] text-text-soft hover:bg-arena-3 hover:text-text"}>
              <IconFlag size={20} className="shrink-0 text-text-muted" />
              <span className="flex-1 truncate">{t("section.careers")}</span>
            </Link>
          )}
        </div>
      </div>
    ));

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[var(--sidebar-w)_1fr]">
      <aside className="scrollbar-thin fixed inset-y-0 left-0 hidden w-[var(--sidebar-w)] flex-col overflow-y-auto border-r border-border bg-pitch lg:flex" aria-label={t("careerNav")}>
        <div className="border-b border-border px-4 py-4">
          <Link href="/" aria-label={t("homeLink")} className="inline-flex rounded-md focus-visible:outline-3 focus-visible:outline-rim">
            <BrandMark size={34} withName />
          </Link>
        </div>
        {club && current && (
          <Link href={base} className="flex items-center gap-3 border-b border-border px-4 py-3 hover:bg-arena-2">
            <ClubBadge club={club} size={40} />
            <div className="min-w-0">
              <div className="truncate font-heading font-semibold">{club.name}</div>
              <div className="truncate text-[0.72rem] text-text-dim">{status}</div>
            </div>
          </Link>
        )}
        <nav className="flex-1 space-y-4 px-3 py-3" aria-label={t("mainNav")}>
          {groups()}
        </nav>
        <div className="flex items-center justify-between gap-2 border-t border-border px-4 py-3">
          <div className="min-w-0">
            <div className="truncate text-[0.8rem]">{current?.coach?.name ?? user?.displayName ?? t("userFallback")}</div>
            <div className="truncate text-[0.68rem] text-text-dim">{user?.email || t("userFallback")}</div>
          </div>
          <div className="flex shrink-0 items-center gap-1">
            <Link href="/settings" className="touch grid place-items-center rounded-md text-text-muted hover:bg-arena-3 hover:text-text" aria-label={t("settingsLink")} title={t("settingsLink")}>
              <IconSettings size={18} />
            </Link>
            <button type="button" onClick={() => void signOut()} className="touch grid place-items-center rounded-md text-text-muted hover:bg-arena-3 hover:text-text" aria-label={tc("actions.logout")} title={tc("actions.logout")}>
              <IconLogout size={18} />
            </button>
          </div>
        </div>
      </aside>

      <div className="min-w-0 lg:col-start-2">
        <header className="sticky top-0 z-40 flex h-14 items-center gap-3 border-b border-border bg-pitch/95 px-4 backdrop-blur lg:hidden">
          <Link href="/" aria-label={t("homeLink")} className="rounded-md focus-visible:outline-3 focus-visible:outline-rim">
            <BrandMark size={28} />
          </Link>
          {club && current ? (
            <Link href={base} className="flex min-w-0 flex-1 items-center gap-2">
              <ClubBadge club={club} size={28} />
              <span className="truncate font-heading text-sm font-semibold">{t("clubStatusShort", { club: club.shortName, record: current.record.summary, week: current.currentWeek })}</span>
            </Link>
          ) : (
            <span className="flex-1" />
          )}
          <button type="button" onClick={() => setMore(true)} className="touch grid place-items-center rounded-md text-text-soft hover:bg-arena-3" aria-label={t("openMenu")} aria-haspopup="dialog">
            <IconMenu />
          </button>
        </header>

        <main id="conteudo" className="mx-auto max-w-[1400px] px-4 py-4 pb-[calc(var(--bottombar-h)+24px)] sm:px-6 sm:py-6 lg:px-8 lg:pb-10">
          {children}
        </main>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-40 grid h-[var(--bottombar-h)] border-t border-border bg-pitch/95 backdrop-blur lg:hidden" style={{ gridTemplateColumns: `repeat(${mobileItems.length + 1}, minmax(0, 1fr))` }} aria-label={t("mobileNav")}>
        {mobileItems.map((s) => {
          const Icon = s.icon;
          const active = activeKey === s.key;
          return (
            <Link key={s.key} href={sectionHref(careerId, s)} aria-current={active ? "page" : undefined} className={cn("touch flex min-w-0 flex-col items-center justify-center gap-0.5 font-heading text-[0.6rem] uppercase tracking-[0.06em]", active ? "text-led" : "text-text-muted")}>
              <Icon size={22} />
              <span className="max-w-full truncate px-0.5">{t(s.key === "" ? "section.hubShort" : `section.${s.labelKey}`)}</span>
            </Link>
          );
        })}
        <button type="button" onClick={() => setMore(true)} className="touch flex flex-col items-center justify-center gap-0.5 font-heading text-[0.6rem] uppercase tracking-[0.06em] text-text-muted" aria-haspopup="dialog">
          <IconMenu size={22} />
          <span>{t("more")}</span>
        </button>
      </nav>

      <Dialog open={more} onClose={() => setMore(false)} title={t("management")} eyebrow={club?.name} size="md">
        <div className="space-y-4">
          {groups("dialog")}
          <button type="button" onClick={() => void signOut()} className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-[var(--radius-md)] border border-border-strong text-text-soft hover:bg-arena-3">
            <IconLogout size={18} /> {tc("actions.logout")}
          </button>
        </div>
      </Dialog>
    </div>
  );
}
