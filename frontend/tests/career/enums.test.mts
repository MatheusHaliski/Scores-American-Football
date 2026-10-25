// Todo valor de enum que as telas da carreira traduzem por chave dinâmica (td("enums", …)) tem texto nos três idiomas.
import { test } from "node:test";
import assert from "node:assert/strict";
import { MESSAGES } from "@/i18n/messages";
import { ATTRIBUTE_GROUPS, POSITIONS } from "@/lib/ratings/overall";
import { MACRO_DEFINITIONS } from "@/lib/ratings/macros";
import { CARD_TIERS } from "@/lib/ratings/tier";
import { COACH_STYLES, DEPTH_ROLES, DIFFICULTIES, UNITS } from "@/lib/api/career-types";
import { readFileSync } from "node:fs";
import { loadGolden } from "../golden.mts";

const ROSTER_STATUSES = ["ACTIVE", "INACTIVE", "PRACTICE_SQUAD", "IR", "PUP", "FREE_AGENT", "WAIVED"];
const KICKOFFS = ["THU_NIGHT", "SUN_EARLY", "SUN_LATE", "SUN_NIGHT", "MON_NIGHT"];

function keys(): string[] {
  const k: string[] = [];
  for (const p of POSITIONS) k.push(`position.${p}`, `slotShort.${p}`);
  for (const r of DEPTH_ROLES) k.push(`slot.${r}`, `slotShort.${r}`);
  for (const u of UNITS) k.push(`unit.${u}`);
  for (const s of ROSTER_STATUSES) k.push(`rosterStatus.${s}`);
  for (const s of COACH_STYLES) k.push(`coachStyle.${s}`, `coachStyle.${s}.hint`);
  for (const d of DIFFICULTIES) k.push(`difficulty.${d}`);
  for (const s of KICKOFFS) k.push(`kickoff.${s}`, `kickoffShort.${s}`);
  for (const t of CARD_TIERS) k.push(`tier.${t.tier}`);
  for (const [fam, defs] of Object.entries(MACRO_DEFINITIONS)) for (const [code] of defs) k.push(`macro.${fam}.${code}`, `macroAbbr.${fam}.${code}`, `family.${fam}`);
  for (const [g, attrs] of Object.entries(ATTRIBUTE_GROUPS)) {
    k.push(`attrGroup.${g}`);
    for (const a of attrs) k.push(`attr.${a}`);
  }
  for (const c of ["ROOKIE", "VETERAN", "PRACTICE"]) k.push(`contractType.${c}`);
  for (const m of ["STATUS_CHANGE", "RELEASE"]) k.push(`moveKind.${m}`);
  k.push("seasonPhase.REGULAR", "rosterStatusShort.IR", "slotLabel", "tierLabel");
  return k;
}

test("enums do jogo traduzidos nos três idiomas", () => {
  for (const loc of ["pt-BR", "en-US", "es-ES"] as const) {
    const dict = MESSAGES[loc].enums as Record<string, string>;
    assert.deepEqual(keys().filter((k) => !(k in dict)), [], loc);
  }
});

test("as vagas e os códigos do golden de depth chart têm rótulo e mensagem", () => {
  const dc = loadGolden("depth-chart.json") as { units: { slots: { slot: string }[] }[] };
  const enums = MESSAGES["pt-BR"].enums as Record<string, string>;
  for (const u of dc.units) for (const s of u.slots) assert.ok(`slotShort.${s.slot}` in enums, s.slot);
  const roster = MESSAGES["pt-BR"].roster as Record<string, string>;
  for (const code of ["DEPTH_CHART_INCOMPLETE", "DEPTH_CHART_PLAYER_UNAVAILABLE", "DEPTH_CHART_DUPLICATE_PLAYER", "DEPTH_CHART_TOO_MANY_ACTIVES", "OUT_OF_POSITION"]) assert.ok(`depth.issue.${code}` in roster, code);
});

test("seções da central: todas as do mapa (docs/02 §1) com rótulo; prontas só as do bloco 6a", () => {
  // sections.ts importa os ícones (.tsx): o teste lê a tabela como texto
  const src = readFileSync(new URL("../../src/components/shell/sections.ts", import.meta.url), "utf8");
  const rows = [...src.matchAll(/\{ key: "([^"]*)", labelKey: "(\w+)",[^}]*ready: (true|false)[^}]*\}/g)].map((m) => ({ key: m[1], labelKey: m[2], ready: m[3] === "true", href: m[0].includes("href:") }));
  const nav = MESSAGES["pt-BR"].nav as Record<string, string>;
  for (const s of rows) assert.ok(`section.${s.labelKey}` in nav, s.labelKey);
  assert.deepEqual(rows.filter((s) => s.ready && !s.href).map((s) => s.key), ["", "roster", "depth-chart", "calendar"]);
  assert.equal(rows.length, 22);
});
