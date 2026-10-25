import type { SVGProps } from "react";

type P = SVGProps<SVGSVGElement> & { size?: number };
const base = (size: number | undefined, rest: SVGProps<SVGSVGElement>) => ({
  width: size ?? 20,
  height: size ?? 20,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.9,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
  ...rest,
});

export const IconHome = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M3 11 12 3l9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" /></svg>);
export const IconUsers = ({ size, ...r }: P) => (<svg {...base(size, r)}><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20a6.5 6.5 0 0 1 13 0" /><circle cx="17" cy="9" r="2.5" /><path d="M21.5 19a5 5 0 0 0-5-5" /></svg>);
/** Bola de futebol americano (oval com costura). */
export const IconBall = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4.6 19.4C2.8 15.2 4.2 9.3 8 6.5c3.1-2.3 7.7-2.6 11.4-1.9.7 3.7.4 8.3-1.9 11.4-2.8 3.8-8.7 5.2-12.9 3.4z" /><path d="M8.5 15.5l7-7M10.5 11.5l2 2M12.5 9.5l2 2M8.5 13.5l2 2" /></svg>);
export const IconCalendar = ({ size, ...r }: P) => (<svg {...base(size, r)}><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4" /></svg>);
export const IconTable = ({ size, ...r }: P) => (<svg {...base(size, r)}><rect x="3" y="4" width="18" height="16" rx="2" /><path d="M3 10h18M3 15h18M9 4v16" /></svg>);
export const IconMarket = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M3 7h18l-1.5 5H4.5zM5 12v7h14v-7M9 19v-4h6v4" /></svg>);
export const IconInbox = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M3 13l2.5-8h13L21 13v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z" /><path d="M3 13h5l1.5 3h5L16 13h5" /></svg>);
export const IconFinance = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4 19h16M6 16V9M10 16V5M14 16v-6M18 16v-9" /></svg>);
export const IconStadium = ({ size, ...r }: P) => (<svg {...base(size, r)}><ellipse cx="12" cy="8" rx="9" ry="3.5" /><path d="M3 8v6c0 1.9 4 3.5 9 3.5s9-1.6 9-3.5V8" /><path d="M7 11.5V17M17 11.5V17" /></svg>);
export const IconStore = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4 9l1.5-5h13L20 9M4 9v10a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1V9M4 9h16M9 20v-6h6v6" /></svg>);
export const IconTrophy = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M7 4h10v5a5 5 0 0 1-10 0z" /><path d="M7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3M12 14v3M8 21h8M9 17h6" /></svg>);
export const IconManager = ({ size, ...r }: P) => (<svg {...base(size, r)}><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0M9 14l3 3 3-3" /></svg>);
export const IconSearch = ({ size, ...r }: P) => (<svg {...base(size, r)}><circle cx="11" cy="11" r="6.5" /><path d="m20 20-4.2-4.2" /></svg>);
export const IconSettings = ({ size, ...r }: P) => (<svg {...base(size, r)}><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" /></svg>);
export const IconChevronRight = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="m9 6 6 6-6 6" /></svg>);
export const IconChevronDown = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="m6 9 6 6 6-6" /></svg>);
export const IconChevronLeft = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="m15 6-6 6 6 6" /></svg>);
export const IconClose = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M6 6l12 12M18 6 6 18" /></svg>);
export const IconMenu = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4 7h16M4 12h16M4 17h16" /></svg>);
export const IconPlay = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M7 5v14l11-7z" fill="currentColor" stroke="none" /></svg>);
export const IconPause = ({ size, ...r }: P) => (<svg {...base(size, r)}><rect x="6" y="5" width="4" height="14" rx="1" fill="currentColor" stroke="none" /><rect x="14" y="5" width="4" height="14" rx="1" fill="currentColor" stroke="none" /></svg>);
export const IconForward = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4 5v14l8-7zM12 5v14l8-7z" fill="currentColor" stroke="none" /></svg>);
export const IconSkip = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M5 5v14l9-7z" fill="currentColor" stroke="none" /><rect x="16" y="5" width="3" height="14" rx="1" fill="currentColor" stroke="none" /></svg>);
export const IconCheck = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="m5 12 5 5L20 7" /></svg>);
export const IconAlert = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M12 3 2.5 20h19z" /><path d="M12 9v5M12 17h.01" /></svg>);
export const IconInfo = ({ size, ...r }: P) => (<svg {...base(size, r)}><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7.5h.01" /></svg>);
export const IconPlus = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M12 5v14M5 12h14" /></svg>);
export const IconArrowUp = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M12 19V5M5 12l7-7 7 7" /></svg>);
export const IconArrowDown = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M12 5v14M5 12l7 7 7-7" /></svg>);
export const IconSwap = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M7 4v12M7 4 4 7M7 4l3 3M17 20V8M17 20l-3-3M17 20l3-3" /></svg>);
export const IconClock = ({ size, ...r }: P) => (<svg {...base(size, r)}><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>);
export const IconWhistle = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M3 14a5 5 0 1 0 10 0V9H3z" /><path d="M13 9h8l-1 3h-7M8 9V6" /></svg>);
export const IconShield = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z" /><path d="m9 12 2 2 4-4" /></svg>);
export const IconHand = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M8 13V5.5a1.5 1.5 0 0 1 3 0V11M11 11V4.5a1.5 1.5 0 0 1 3 0V11M14 11V6a1.5 1.5 0 0 1 3 0v7" /><path d="M17 13v2a6 6 0 0 1-12 0v-4a1.5 1.5 0 0 1 3 0" /></svg>);
export const IconBolt = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M13 2 4 14h7l-1 8 9-12h-7z" /></svg>);
export const IconFlag = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M5 21V4M5 4h11l-2 4 2 4H5" /></svg>);
export const IconCube = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9z" /><path d="M12 12 4 7.5M12 12l8-4.5M12 12v9" /></svg>);
export const IconLed = ({ size, ...r }: P) => (<svg {...base(size, r)}><rect x="3" y="5" width="18" height="12" rx="1.5" /><path d="M8 21h8M12 17v4M7 9h3M7 12h6M14 9h3" /></svg>);
export const IconHeart = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z" /></svg>);
export const IconTrend = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M3 17l6-6 4 4 8-8M15 7h6v6" /></svg>);
export const IconTag = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M3 12V4h8l9 9-8 8z" /><circle cx="7.5" cy="8.5" r="1.2" /></svg>);
export const IconPill = ({ size, ...r }: P) => (<svg {...base(size, r)}><rect x="3" y="8" width="18" height="8" rx="4" transform="rotate(-45 12 12)" /><path d="m9 9 6 6" /></svg>);
export const IconMessage = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4 5h16v11H9l-5 4z" /></svg>);
export const IconLogout = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M10 4H5v16h5M14 8l4 4-4 4M18 12H9" /></svg>);
export const IconTarget = ({ size, ...r }: P) => (<svg {...base(size, r)}><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1.5" fill="currentColor" /></svg>);
export const IconEdit = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4 20h4l11-11-4-4L4 16z" /><path d="m13 7 4 4" /></svg>);
export const IconTrash = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" /></svg>);
export const IconStar = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="m12 3 2.8 5.8 6.2.9-4.5 4.4 1.1 6.2L12 17.4l-5.6 2.9 1.1-6.2L3 9.7l6.2-.9z" /></svg>);
export const IconTimeout = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4 12h4l2-6 4 12 2-6h4" /></svg>);
export const IconLoop = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3M18 3v4h-4M6 21v-4h4" /></svg>);
export const IconUser = ({ size, ...r }: P) => (<svg {...base(size, r)}><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></svg>);
export const IconLock = ({ size, ...r }: P) => (<svg {...base(size, r)}><rect x="4" y="10" width="16" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" /></svg>);
export const IconPalette = ({ size, ...r }: P) => (<svg {...base(size, r)}><path d="M12 3a9 9 0 1 0 0 18c1.2 0 2-.8 2-1.8 0-.5-.2-.9-.5-1.3-.3-.3-.5-.8-.5-1.2 0-1 .8-1.7 1.8-1.7H17a4 4 0 0 0 4-4c0-4.4-4-8-9-8z" /><circle cx="7.5" cy="11" r="1.2" /><circle cx="10" cy="7" r="1.2" /><circle cx="15" cy="7.5" r="1.2" /></svg>);
export const IconGlobe = ({ size, ...r }: P) => (<svg {...base(size, r)}><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z" /></svg>);
export const IconDatabase = ({ size, ...r }: P) => (<svg {...base(size, r)}><ellipse cx="12" cy="5.5" rx="7.5" ry="2.5" /><path d="M4.5 5.5v13c0 1.4 3.4 2.5 7.5 2.5s7.5-1.1 7.5-2.5v-13M4.5 12c0 1.4 3.4 2.5 7.5 2.5s7.5-1.1 7.5-2.5" /></svg>);
