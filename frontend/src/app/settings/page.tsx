import type { Metadata } from "next";
import { SettingsScreen } from "@/components/settings/SettingsScreen";

export const metadata: Metadata = { title: "Configurações" };

/** Tela de configurações (RF10/RF46): Conta, Privacidade, Aparência, Idioma e Seus dados. */
export default function SettingsPage() {
  return <SettingsScreen />;
}
