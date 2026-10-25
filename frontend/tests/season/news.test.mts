// Notícias da semana (RF21, D-103) traduzidas pelo `textKey` e pelos `params` do backend nos 3 idiomas, com a manchete/texto do
// servidor como reserva para tipo desconhecido ou parâmetro faltando; todo tipo de notícia do backend tem texto no catálogo do front.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { weekNewsFromDto } from "@/lib/api/live-season";
import { newsText } from "@/lib/season/news";
import { LOCALES } from "@/i18n/config";
import { MESSAGES } from "@/i18n/messages";
import { loadGolden } from "../golden.mts";

const week = weekNewsFromDto(loadGolden("week-news.json"));
const HERE = path.dirname(fileURLToPath(import.meta.url));
const bundle = readFileSync(path.resolve(HERE, "../../../scores-af-application/src/main/resources/i18n/messages.properties"), "utf8");
const BACKEND_KINDS = [...new Set([...bundle.matchAll(/^news\.([A-Z_]+)\.headline=/gm)].map((m) => m[1]))];

test("todo tipo de notícia do backend tem manchete e texto no catálogo season nos 3 idiomas", () => {
  assert.ok(BACKEND_KINDS.length >= 20, `tipos lidos do backend: ${BACKEND_KINDS.length}`);
  for (const loc of LOCALES) {
    const dict = MESSAGES[loc].season as Record<string, string>;
    for (const k of BACKEND_KINDS) for (const part of ["headline", "body"]) assert.ok(`news.${k}.${part}` in dict, `${loc}: falta news.${k}.${part}`);
  }
});

test("as notícias do golden saem do catálogo do front (não da reserva) nos 3 idiomas, com os parâmetros do backend", () => {
  for (const loc of LOCALES) {
    for (const item of week.items) {
      const t = newsText(item, loc);
      assert.equal(t.translated, true, `${loc}: ${item.kind} caiu na reserva`);
      assert.ok(!/\{[a-zA-Z]/.test(t.headline + t.body), `${loc}: ${item.kind} ficou com argumento sem valor`);
      for (const v of Object.values(item.params)) if (typeof v === "string" && v.length > 3) assert.ok((t.headline + t.body).includes(v) || item.kind === "PASSING" || item.kind === "DEFENSE", `${loc}: ${item.kind} sem "${v}"`);
    }
  }
});

test("em pt-BR o texto do front bate com o do servidor nas notícias sem plural", () => {
  for (const item of week.items.filter((i) => ["USER_WIN", "BLOWOUT", "PASSING", "RUSHING", "RECEIVING", "USER_SEED", "TOP_SEED"].includes(i.kind))) {
    const t = newsText(item, "pt-BR");
    assert.equal(t.headline, item.headline, item.kind);
    assert.equal(t.body, item.body, item.kind);
  }
  const en = newsText(week.items[0], "en-US");
  assert.match(en.headline, /beat/);
  const es = newsText(week.items[0], "es-ES");
  assert.match(es.headline, /vence a/);
});

test("prorrogação, plural e ano: overtime 1 muda o texto e o ano (número) não ganha separador de milhar", () => {
  const win = week.items.find((i) => i.kind === "USER_WIN")!;
  assert.match(newsText({ ...win, params: { ...win.params, overtime: 1 } }, "pt-BR").body, /na prorrogação/);
  assert.doesNotMatch(newsText(win, "pt-BR").body, /prorrogação/);
  const champ = { ...win, textKey: "news.CHAMPION", params: { team: "Harpias", opponent: "Arraias", winnerScore: 23, loserScore: 21, year: 2026 } };
  for (const loc of LOCALES) assert.match(newsText(champ, loc).headline, /2026/);
  const thriller = week.items.find((i) => i.kind === "THRILLER")!;
  assert.equal(newsText({ ...thriller, params: { ...thriller.params, margin: 1 } }, "pt-BR").body, "Decidido por 1 ponto no fim.");
});

test("reserva: tipo que o front não conhece ou parâmetro faltando usa a manchete e o texto do servidor", () => {
  const item = week.items[0];
  const unknown = newsText({ ...item, textKey: "news.NEW_KIND", headline: "H", body: "B" }, "en-US");
  assert.deepEqual(unknown, { headline: "H", body: "B", translated: false });
  const missing = newsText({ ...item, params: { team: "X" }, headline: "H2", body: "B2" }, "en-US");
  assert.deepEqual(missing, { headline: "H2", body: "B2", translated: false });
  // textKey sem o prefixo também vale
  assert.equal(newsText({ ...item, textKey: "USER_WIN" }, "pt-BR").translated, true);
});
