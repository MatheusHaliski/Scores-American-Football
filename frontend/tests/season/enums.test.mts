// Bloco 6c: todo valor que as telas da temporada traduzem por chave dinâmica (`td("season", ...)`) tem texto nos três idiomas — os
// enums do contrato e os valores que aparecem nas amostras douradas (critérios de desempate, rodadas, situações, prêmios, líderes,
// linhas de estatística, passos da entressafra, troféus, moral e analista).
import { test } from "node:test";
import assert from "node:assert/strict";
import { LOCALES } from "@/i18n/config";
import { MESSAGES } from "@/i18n/messages";
import { ANALYSIS_UNITS, AWARD_TYPES, CLINCHES, LEADER_CATEGORIES, MORALE_FACTORS, MORALE_LABELS, PLAYOFF_ROUNDS, TIEBREAKERS } from "@/lib/api/season-types";
import { loadGolden } from "../golden.mts";

type R = Record<string, unknown>;
const g = (f: string) => loadGolden(f) as R;
const arr = (v: unknown) => (v as R[]) ?? [];

test("chaves dinâmicas da temporada traduzidas nos 3 idiomas", () => {
  const keys = new Set<string>();
  const add = (prefix: string, xs: Iterable<unknown>) => {
    for (const x of xs) if (x != null) keys.add(`${prefix}.${x}`);
  };
  const st = g("standings.json");
  add("tiebreaker", TIEBREAKERS);
  add("tiebreaker", [...arr(st.divisionTiebreakers), ...arr(st.wildCardTiebreakers)] as unknown[]);
  add("standings.clinch", CLINCHES);
  add("playoffs.round", PLAYOFF_ROUNDS);
  add("playoffs.roundShort", PLAYOFF_ROUNDS);
  add("news.round", PLAYOFF_ROUNDS);
  add("playoffs.roundStatus", ["PROJECTED", "PENDING", "CURRENT", "COMPLETED", "UPCOMING", ...arr(g("playoffs-bracket.json").rounds).map((r) => r.status)]);
  add("playoffs.status", ["NOT_STARTED", "IN_PROGRESS", "FINISHED"]);
  add("playoffs.seedStatus", ["PROJECTED", "ALIVE", "ELIMINATED", "CHAMPION", ...arr(g("playoffs.json").conferences).flatMap((c) => arr(c.seeds).map((s) => s.status))]);
  add("playoffs.user", ["PROJECTED_IN", "PROJECTED_OUT", "BYE", "ALIVE", "ELIMINATED", "MISSED", "CHAMPION", "RUNNER_UP"]);
  const sum = g("season-summary.json");
  add("award", AWARD_TYPES);
  add("award", arr(sum.awards).map((a) => a.type));
  add("stat", arr(sum.awards).flatMap((a) => Object.keys(a.statLine as R)));
  add("leader", LEADER_CATEGORIES);
  add("leader", Object.keys(sum.leaders as R));
  add("result", ["CHAMPION", "RUNNER_UP", "CONFERENCE", "DIVISIONAL", "WILD_CARD", "MISSED", "IN_PROGRESS"]);
  const off = sum.offseason as R;
  const adv = g("season-advance.json");
  add("step", [...arr(off.steps), ...arr(adv.steps)].map((s) => s.phase));
  add("stepStatus", [...arr(off.steps), ...arr(adv.steps)].map((s) => s.status));
  add("handledBy", [...arr(off.steps), ...arr(adv.steps)].map((s) => s.handledBy));
  add("trophies.kind", ["CHAMPIONSHIP", "CONFERENCE", "DIVISION", ...arr(g("trophies.json").trophies).map((t) => t.kind)]);
  const mor = g("morale.json");
  add("morale.label", MORALE_LABELS);
  add("morale.trend", ["UP", "DOWN", "STABLE"]);
  add("morale.role", ["STARTER", "ROTATION", "BACKUP", "RESERVE", "SPECIALIST", ...arr(mor.players).map((p) => p.role)]);
  add("morale.factor", MORALE_FACTORS);
  add("morale.factor", arr(mor.players).flatMap((p) => Object.keys(p.factors as R)));
  const an = g("analysis.json");
  add("analysis.unit", ANALYSIS_UNITS);
  add("analysis.unit", arr(an.units).map((u) => u.unit));
  add("analysis.matchup", arr(an.upcoming).flatMap((o) => arr(o.matchups).map((m) => m.key)));
  add("analysis.verdict", ["FAVORITE", "EVEN", "UNDERDOG"]);
  add("analysis.reason", ["PASS_EDGE", "RUN_EDGE", "PASS_THREAT", "RUN_THREAT", "BALANCED_THREAT", "WIN_PROBABILITY", ...arr(an.gamePlan).map((p) => p.reason)]);
  add("analysis.field", arr(an.gamePlan).map((p) => p.field));
  add("analysis.summary", ["SEASON_OVERVIEW", "NEXT_GAME_FAVORITE", "NEXT_GAME_EVEN", "NEXT_GAME_UNDERDOG", an.summaryCode]);
  add("news.tone", ["POSITIVE", "NEGATIVE", "NEUTRAL", "DRAMATIC", ...arr(g("week-news.json").items).map((i) => i.tone)]);
  assert.ok(keys.size > 120);
  for (const loc of LOCALES) {
    const dict = MESSAGES[loc].season as Record<string, string>;
    assert.deepEqual([...keys].filter((k) => !(k in dict)), [], `${loc}: chaves da temporada sem texto`);
  }
  // valores de plano que o analista recomenda vêm dos enums do 6b
  for (const loc of LOCALES) {
    const enums = MESSAGES[loc].enums as Record<string, string>;
    for (const p of arr(an.gamePlan)) {
      if (p.field === "defensiveFocus") for (const v of [p.current, p.recommended]) assert.ok(`defensiveFocus.${v}` in enums);
      if (p.field === "fourthDownAggression") for (const v of [p.current, p.recommended]) assert.ok(`aggression.${v}` in enums);
    }
    assert.ok("seasonPhase.PLAYOFFS" in enums && "seasonPhase.OFFSEASON" in enums);
  }
});
