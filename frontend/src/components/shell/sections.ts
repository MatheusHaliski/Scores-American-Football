import type { ComponentType } from "react";
import {
  IconBall,
  IconCalendar,
  IconEdit,
  IconFinance,
  IconFlag,
  IconHome,
  IconInbox,
  IconManager,
  IconMarket,
  IconPill,
  IconSearch,
  IconSettings,
  IconShield,
  IconStadium,
  IconStar,
  IconStore,
  IconTable,
  IconTarget,
  IconTrend,
  IconTrophy,
  IconUsers,
  IconWhistle,
} from "@/components/ui/icons";

export type SectionLabelKey =
  | "hub"
  | "roster"
  | "depthChart"
  | "playbook"
  | "match"
  | "calendar"
  | "standings"
  | "playoffs"
  | "inbox"
  | "market"
  | "draft"
  | "finance"
  | "injuries"
  | "stadium"
  | "manager"
  | "store"
  | "trophies"
  | "managers"
  | "search"
  | "season"
  | "editor"
  | "settings";

export interface Section {
  /** segmento da URL em /c/[careerId]/<key> ("" = central) */
  key: string;
  /** chave em `nav:section.<labelKey>` */
  labelKey: SectionLabelKey;
  rf: string;
  icon: ComponentType<{ size?: number; className?: string }>;
  /** pronta neste bloco? (senão, cai na página "em construção", só por URL) */
  ready: boolean;
  group: "jogo" | "gestao" | "mundo" | "conta";
  /** aparece na barra inferior do celular */
  mobile?: boolean;
  /** rota absoluta (fora da carreira), ex.: /settings */
  href?: string;
}

/** Seções da central da franquia (docs/02 §1): as do basquete adaptadas ao futebol americano, na ordem do mapa. */
export const SECTIONS: Section[] = [
  { key: "", labelKey: "hub", rf: "RF40", icon: IconHome, ready: true, group: "jogo", mobile: true },
  { key: "roster", labelKey: "roster", rf: "RF4/RF56", icon: IconUsers, ready: true, group: "jogo", mobile: true },
  { key: "depth-chart", labelKey: "depthChart", rf: "RF60", icon: IconTarget, ready: true, group: "jogo", mobile: true },
  { key: "playbook", labelKey: "playbook", rf: "RF61", icon: IconWhistle, ready: true, group: "jogo" },
  { key: "match", labelKey: "match", rf: "RF27/RF42/RF62", icon: IconBall, ready: true, group: "jogo" },
  { key: "calendar", labelKey: "calendar", rf: "RF21/RF36", icon: IconCalendar, ready: true, group: "jogo", mobile: true },
  { key: "standings", labelKey: "standings", rf: "RF16", icon: IconTable, ready: true, group: "jogo" },
  { key: "playoffs", labelKey: "playoffs", rf: "RF66", icon: IconTrophy, ready: true, group: "jogo" },
  { key: "inbox", labelKey: "inbox", rf: "RF13/RF24", icon: IconInbox, ready: false, group: "gestao" },
  { key: "market", labelKey: "market", rf: "RF19/RF65/RF50", icon: IconMarket, ready: false, group: "gestao" },
  { key: "draft", labelKey: "draft", rf: "RF51", icon: IconStar, ready: false, group: "gestao" },
  { key: "finance", labelKey: "finance", rf: "RF14/RF49", icon: IconFinance, ready: false, group: "gestao" },
  { key: "injuries", labelKey: "injuries", rf: "RF64", icon: IconPill, ready: false, group: "gestao" },
  { key: "stadium", labelKey: "stadium", rf: "RF12/RF26", icon: IconStadium, ready: false, group: "gestao" },
  { key: "manager", labelKey: "manager", rf: "RF8/RF9/RF37", icon: IconManager, ready: false, group: "gestao" },
  { key: "store", labelKey: "store", rf: "RF25/RF32/RF33", icon: IconStore, ready: false, group: "mundo" },
  { key: "trophies", labelKey: "trophies", rf: "RF17", icon: IconShield, ready: false, group: "mundo" },
  { key: "managers", labelKey: "managers", rf: "RF18", icon: IconFlag, ready: false, group: "mundo" },
  { key: "search", labelKey: "search", rf: "RF20", icon: IconSearch, ready: false, group: "mundo" },
  { key: "season", labelKey: "season", rf: "RF35", icon: IconTrend, ready: true, group: "mundo" },
  { key: "editor", labelKey: "editor", rf: "RF5/RF28", icon: IconEdit, ready: false, group: "conta" },
  { key: "settings", labelKey: "settings", rf: "RF10/RF46", icon: IconSettings, ready: true, group: "conta", href: "/settings" },
];

/** Só telas prontas aparecem na navegação; as demais continuam acessíveis por URL ("em construção"). */
export const NAV_SECTIONS: Section[] = SECTIONS.filter((s) => s.ready);

export const GROUPS = ["jogo", "gestao", "mundo", "conta"] as const;

export function sectionByKey(key: string): Section | undefined {
  return SECTIONS.find((s) => s.key === key);
}

/** A tela existe? Usado para esconder atalhos para telas ainda não implementadas. */
export function isSectionReady(key: string): boolean {
  return sectionByKey(key)?.ready ?? false;
}

export function sectionHref(careerId: string, s: Section): string {
  if (s.href) return s.href;
  return s.key ? `/c/${careerId}/${s.key}` : `/c/${careerId}`;
}
