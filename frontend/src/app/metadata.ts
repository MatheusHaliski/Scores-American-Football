import type { Metadata, Viewport } from "next";

/** Metadata do app; o layout só importa. Ícones e OpenGraph com a marca chegam com o kit de marca (bloco 3). */
export const SITE_NAME = "Scores: American Football";
export const SITE_TITLE = "Scores: American Football — Manager de futebol americano";
export const SITE_DESCRIPTION = "Scores: American Football, da VELION TECH CO.: monte sua franquia, chame a jogada decisiva e leve o time ao título.";
const LOCAL_SITE = "http://localhost:3000";

/** URL http(s) válida, ou null para valor ausente ou inválido. */
function parseSiteUrl(raw: string | undefined): URL | null {
  try {
    const url = new URL(raw ?? "");
    if (url.protocol === "https:" || url.protocol === "http:") return url;
  } catch {
    // valor ausente ou inválido
  }
  return null;
}

/** URL pública para links absolutos (OpenGraph): NEXT_PUBLIC_SITE_URL ou, sem ela, o servidor local. */
export function siteUrl(raw: string | undefined = process.env.NEXT_PUBLIC_SITE_URL): URL {
  return parseSiteUrl(raw) ?? new URL(LOCAL_SITE);
}

export const siteMetadata: Metadata = {
  metadataBase: siteUrl(),
  title: { default: SITE_TITLE, template: `%s · ${SITE_NAME}` },
  description: SITE_DESCRIPTION,
  applicationName: SITE_NAME,
  openGraph: { type: "website", siteName: SITE_NAME, title: SITE_TITLE, description: SITE_DESCRIPTION, locale: "pt_BR" },
};

export const siteViewport: Viewport = {
  themeColor: "#15110c",
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};
