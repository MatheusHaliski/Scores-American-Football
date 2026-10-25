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

test("seções da central: todas as do mapa (docs/02 §1) com rótulo; prontas só as dos blocos 6a e 6b", () => {
  // sections.ts importa os ícones (.tsx): o teste lê a tabela como texto
  const src = readFileSync(new URL("../../src/components/shell/sections.ts", import.meta.url), "utf8");
  const rows = [...src.matchAll(/\{ key: "([^"]*)", labelKey: "(\w+)",[^}]*ready: (true|false)[^}]*\}/g)].map((m) => ({ key: m[1], labelKey: m[2], ready: m[3] === "true", href: m[0].includes("href:") }));
  const nav = MESSAGES["pt-BR"].nav as Record<string, string>;
  for (const s of rows) assert.ok(`section.${s.labelKey}` in nav, s.labelKey);
  assert.deepEqual(rows.filter((s) => s.ready && !s.href).map((s) => s.key), ["", "roster", "depth-chart", "playbook", "match", "calendar"]);
  assert.equal(rows.length, 22);
});

// Bloco 6b: todo valor de enum que as telas de playbook, preparação, partida e relatório traduzem por chave dinâmica.
test("enums do 6b (plano, playbook, preparação, partida e relatório) traduzidos nos três idiomas", async () => {
  const { BASE_DEFENSES, BLITZ_FREQUENCIES, COVERAGES, DEFENSIVE_FOCUSES, FOURTH_DOWN_AGGRESSIONS, PLAYBOOK_SITUATIONS, TEMPOS } = await import("@/lib/api/match-types");
  const prep = loadGolden("preparation.json") as { weather: { condition: string; roof: string; surface: string }; keyMatchups: { key: string; side: string; advantage: string }[]; units: { unit: string }[]; playCallingLevel: string };
  const book = loadGolden("playbook.json") as { runConcepts: string[]; passConcepts: string[] };
  const report = loadGolden("match-report.json") as { leaders: { category: string }[]; drives: { result: string }[]; weather: string; kickoffSlot: string };
  const state = loadGolden("match-state.json") as { live: { decision: { situation: string; options: { defensePackage?: string; coverage?: string; formation?: string }[] } } };
  const k: string[] = [];
  const hinted = (prefix: string, xs: readonly string[]) => xs.forEach((x) => k.push(`${prefix}.${x}`, `${prefix}.${x}.hint`));
  hinted("aggression", FOURTH_DOWN_AGGRESSIONS);
  hinted("tempo", TEMPOS);
  hinted("defensiveFocus", DEFENSIVE_FOCUSES);
  hinted("blitz", BLITZ_FREQUENCIES);
  hinted("baseDefense", BASE_DEFENSES);
  hinted("situation", PLAYBOOK_SITUATIONS);
  PLAYBOOK_SITUATIONS.forEach((s) => k.push(`situationShort.${s}`));
  [...book.runConcepts, ...book.passConcepts].forEach((c) => k.push(`concept.${c}`));
  COVERAGES.forEach((c) => k.push(`coverage.${c}`));
  for (const w of ["INDOOR", "CLEAR", "RAIN", "SNOW", "WIND", prep.weather.condition, report.weather]) k.push(`weather.${w}`);
  for (const r of ["OPEN", "DOME", "RETRACTABLE", prep.weather.roof]) k.push(`roof.${r}`);
  for (const s of ["GRASS", "TURF", prep.weather.surface]) k.push(`surface.${s}`);
  for (const m of prep.keyMatchups) k.push(`matchup.${m.key}`, `matchupSide.${m.side}`, `advantage.${m.advantage}`);
  for (const u of prep.units) k.push(`unit.${u.unit}`);
  for (const l of ["KEY_MOMENTS", "ALL_PLAYS", "NONE", prep.playCallingLevel]) k.push(`playCalling.${l}`);
  for (const s of ["FOURTH_DOWN", "TWO_MINUTE", "OVERTIME", "CONVERSION", "ALL_PLAYS", state.live.decision.situation]) k.push(`decisionSituation.${s}`);
  for (const o of state.live.decision.options) {
    if (o.defensePackage) k.push(`defensePackage.${o.defensePackage}`);
    if (o.coverage) k.push(`coverage.${o.coverage}`);
    if (o.formation) k.push(`formation.${o.formation}`);
  }
  for (const f of ["I_FORM", "SINGLEBACK", "SHOTGUN", "PISTOL", "EMPTY", "GOAL_LINE"]) k.push(`formation.${f}`);
  for (const p of ["BASE_4_3", "BASE_3_4", "NICKEL", "DIME", "GOAL_LINE"]) k.push(`defensePackage.${p}`);
  for (const l of [...report.leaders.map((x) => x.category), "PASSING", "RUSHING", "RECEIVING", "TACKLES", "SACKS"]) k.push(`leader.${l}`);
  for (const r of [...report.drives.map((d) => d.result), "TD", "FG", "MISSED_FG", "PUNT", "TURNOVER", "DOWNS", "SAFETY", "END_HALF"]) k.push(`driveResult.${r}`);
  k.push(`kickoff.${report.kickoffSlot}`);
  for (const loc of ["pt-BR", "en-US", "es-ES"] as const) {
    const dict = MESSAGES[loc].enums as Record<string, string>;
    assert.deepEqual([...new Set(k)].filter((x) => !(x in dict)), [], loc);
    const match = MESSAGES[loc].match as Record<string, string>;
    for (const s of ["NOT_STARTED", "LIVE", "BREAK", "HALFTIME", "AWAITING_DECISION", "FINISHED"]) assert.ok(`live.status.${s}` in match, `${loc}: live.status.${s}`);
    for (const n of ["aiCalled", "stale", "decisionOpen", "finished", "notAdjustable", "noTimeout", "notOffered"]) assert.ok(`live.notice.${n}` in match, `${loc}: live.notice.${n}`);
    for (const t of ["passing", "rushing", "receiving", "defense", "kicking", "punting", "returns"]) assert.ok(`report.tab.${t}` in match, `${loc}: report.tab.${t}`);
    for (const c of ["PASSING", "RUSHING", "RECEIVING", "TACKLES", "SACKS"]) assert.ok(`report.leaderValue.${c}` in match, `${loc}: report.leaderValue.${c}`);
    const game = MESSAGES[loc].game as Record<string, string>;
    for (const f of ["passRatio", "fourthDownAggression", "tempo", "defensiveFocus", "blitzFrequency", "baseDefense"]) assert.ok(`plan.field.${f}` in game, `${loc}: plan.field.${f}`);
    for (const c of ["REQUIRED", "UNKNOWN_VALUE", "PASS_RATIO_OUT_OF_RANGE", "UNKNOWN_SITUATION", "DUPLICATE_SITUATION", "UNKNOWN_ITEM", "DUPLICATE_ITEM", "WEIGHT_OUT_OF_RANGE", "NO_RUN_CONCEPT", "NO_PASS_CONCEPT", "NO_COVERAGE"]) assert.ok(`issue.${c}` in game, `${loc}: issue.${c}`);
  }
});
