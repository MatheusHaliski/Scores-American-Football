// Textos do play-by-play do motor (RF54, bloco 5): o backend grava a chave i18n e os parâmetros de cada lance, nunca a frase.
// `match-i18n-keys.json` (MatchI18nKeysGoldenTest) traz um lance real de cada chave, os parâmetros que o motor sempre manda e os
// códigos de opção e de falta; `live-match.json` (LiveMatchJsonTest) é o estado ao vivo parado numa decisão de 4ª descida. Aqui cada
// texto existe nos três idiomas, só usa parâmetros garantidos pelo motor e formata sem sobrar `{…}`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { parse, TYPE, type MessageFormatElement } from "@formatjs/icu-messageformat-parser";
import { LOCALES } from "@/i18n/config";
import { rawMessage, splitRefKey, translateIn } from "@/i18n/runtime";
import { loadGolden } from "../golden.mts";

interface Example {
  textKey: string;
  required: string[];
  params: Record<string, string>;
}
interface Sample {
  events: Example[];
  options: string[];
  penalties: string[];
}
interface LiveEvent {
  textKey: string;
  params: Record<string, string>;
}
interface LiveMatch {
  status: string;
  down: number;
  decision: { situation: string; options: { code: string; labelKey: string }[]; autoOptionCode: string };
  lastEvents: LiveEvent[];
}

const sample = loadGolden("match-i18n-keys.json") as Sample;
const live = loadGolden("live-match.json") as LiveMatch;

function args(ast: MessageFormatElement[], acc = new Set<string>()): Set<string> {
  for (const el of ast) {
    if (el.type === TYPE.argument || el.type === TYPE.number || el.type === TYPE.date || el.type === TYPE.time) acc.add(el.value);
    if (el.type === TYPE.plural || el.type === TYPE.select) {
      acc.add(el.value);
      for (const opt of Object.values(el.options)) args(opt.value, acc);
    }
  }
  return acc;
}

test("todo texto de lance existe nos três idiomas e só usa parâmetros que o motor sempre manda", () => {
  assert.ok(sample.events.length >= 40, "amostra com as chaves do motor");
  for (const ex of sample.events) {
    const { ns, key } = splitRefKey(ex.textKey);
    assert.equal(ns, "match", ex.textKey);
    for (const loc of LOCALES) {
      const raw = rawMessage(loc, "match", key);
      assert.ok(raw, `${loc}: falta ${ex.textKey}`);
      for (const a of args(parse(raw!, { ignoreTag: true }))) assert.ok(ex.required.includes(a), `${loc} ${ex.textKey}: {${a}} não é garantido pelo motor`);
      const out = translateIn(loc, "match", key, ex.params);
      assert.ok(!out.includes("{") && !out.startsWith("match:"), `${loc} ${ex.textKey}: ${out}`);
    }
  }
});

test("opções de chamada de jogada e faltas têm rótulo nos três idiomas", () => {
  for (const loc of LOCALES) {
    for (const code of sample.options) assert.ok(rawMessage(loc, "match", `play.${code}`), `${loc}: play.${code}`);
    for (const code of sample.penalties) {
      assert.ok(rawMessage(loc, "match", `penalty.${code}`), `${loc}: penalty.${code}`);
      // o texto do lance de falta traduz o código (não mostra a constante crua)
      assert.ok(!translateIn(loc, "match", "event.penalty", { code }).includes(code), `${loc}: event.penalty ${code}`);
    }
  }
});

test("estado ao vivo dourado: decisão de 4ª descida com opções rotuladas e lances traduzíveis", () => {
  assert.equal(live.status, "AWAITING_DECISION");
  assert.equal(live.down, 4);
  assert.equal(live.decision.situation, "FOURTH_DOWN");
  assert.ok(live.decision.options.some((o) => o.code === live.decision.autoOptionCode));
  for (const loc of LOCALES) {
    for (const o of live.decision.options) {
      const { ns, key } = splitRefKey(o.labelKey);
      assert.equal(ns, "match");
      assert.ok(rawMessage(loc, "match", key), `${loc}: ${o.labelKey}`);
    }
    for (const e of live.lastEvents) {
      const out = translateIn(loc, "match", splitRefKey(e.textKey).key, e.params);
      assert.ok(!out.includes("{") && !out.startsWith("match:"), `${loc} ${e.textKey}: ${out}`);
    }
  }
});
