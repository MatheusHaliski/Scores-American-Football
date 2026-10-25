import type { Metadata, Viewport } from "next";
import "@fontsource/anton/400.css";
import "@fontsource/oswald/400.css";
import "@fontsource/oswald/500.css";
import "@fontsource/oswald/600.css";
import "@fontsource/oswald/700.css";
import "@fontsource/inter/400.css";
import "@fontsource/inter/500.css";
import "@fontsource/inter/600.css";
import "@fontsource/inter/700.css";
import "./globals.css";
import { Providers } from "@/components/shell/Providers";
import { siteMetadata, siteViewport } from "./metadata";

export const metadata: Metadata = siteMetadata;
export const viewport: Viewport = siteViewport;

/** `lang`/`data-theme` iniciais (pt-BR, escuro); o `I18nProvider` atualiza o idioma no cliente sem reload. */
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR" data-theme="dark" suppressHydrationWarning>
      <body className="min-h-dvh antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
