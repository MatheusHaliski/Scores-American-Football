/**
 * Nível do card pelo overall (docs/01 §3.4) — port de `CardTier` (Java): Bronze ≤ 64, Prata 65–74, Ouro 75–84, Ícone 85–89,
 * Elite ≥ 90.
 */
export const CARD_TIERS = [
  { tier: "BRONZE", min: 0 },
  { tier: "SILVER", min: 65 },
  { tier: "GOLD", min: 75 },
  { tier: "ICON", min: 85 },
  { tier: "ELITE", min: 90 },
] as const;

export type CardTier = (typeof CARD_TIERS)[number]["tier"];

export function cardTier(overall: number): CardTier {
  let tier: CardTier = "BRONZE";
  for (const t of CARD_TIERS) if (overall >= t.min) tier = t.tier;
  return tier;
}
