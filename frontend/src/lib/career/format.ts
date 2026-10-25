/**
 * Utilidades puras da carreira: dinheiro da API (centavos de S$) para o formatador, país ISO-3 → ISO-2 (o `Intl.DisplayNames`
 * só entende ISO-2), contraste do texto sobre a cor da franquia e o "—" para valor ausente (nunca zero).
 */
import type { CardTier } from "@/lib/ratings/tier";
import type { Tier } from "@/components/ui/Chip";

/** Centavos de S$ (API) → S$ inteiros (formatador `money`). */
export const fromCents = (cents: number): number => cents / 100;

const ISO3_TO_ISO2: Record<string, string> = {
  BRA: "BR", USA: "US", MEX: "MX", CAN: "CA", ARG: "AR", COL: "CO", GER: "DE", DEU: "DE", NGA: "NG", ESP: "ES", POR: "PT", PRT: "PT", FRA: "FR", ITA: "IT",
  GBR: "GB", ENG: "GB", JPN: "JP", AUS: "AU", CHL: "CL", URY: "UY", PER: "PE", PRY: "PY", VEN: "VE", GHA: "GH", CMR: "CM", SEN: "SN", NZL: "NZ", SAM: "WS",
};
export const NATIONALITIES = ["BRA", "USA", "MEX", "CAN", "ARG", "COL", "ESP", "POR", "GER", "NGA"] as const;

/** Código de país para o `Intl.DisplayNames` (ISO-2); devolve o próprio texto quando não conhece. */
export function iso2(code: string | null | undefined): string {
  if (!code) return "";
  const up = code.toUpperCase();
  return up.length === 2 ? up : (ISO3_TO_ISO2[up] ?? up);
}

/** Texto legível (claro/escuro) sobre uma cor #RRGGBB, pela luminância relativa. */
export function inkOn(hex: string): string {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex ?? "");
  if (!m) return "#ffffff";
  const n = parseInt(m[1], 16);
  const lin = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  const l = 0.2126 * lin((n >> 16) & 255) + 0.7152 * lin((n >> 8) & 255) + 0.0722 * lin(n & 255);
  return l > 0.4 ? "#111111" : "#ffffff";
}

/** Nível do card → gradiente do `Chip` (Elite usa o do Ícone com brilho). */
export function chipTier(tier: CardTier | string): Tier {
  switch (tier) {
    case "SILVER":
      return "silver";
    case "GOLD":
      return "gold";
    case "ICON":
    case "ELITE":
      return "icon";
    default:
      return "bronze";
  }
}

/** "—" para ausente; o número formatado quando existe (zero é um valor, nulo não). */
export function orDash<T>(v: T | null | undefined, format: (x: T) => string = String): string {
  return v === null || v === undefined ? "—" : format(v);
}
