/**
 * Verificação de cobertura de tradução (RF46 CA06) — roda no CI (`npm run i18n:check`) e falha se:
 *  1. algum idioma não tiver exatamente as mesmas chaves do pt-BR (fonte da verdade) em cada namespace;
 *  2. alguma mensagem não for ICU MessageFormat válida;
 *  3. os argumentos ({name}, {n, plural, …}) divergirem entre idiomas;
 *  4. faltar no namespace `errors` algum código `error.*` do backend (messages.properties).
 * Também avisa (sem falhar) sobre valores do en-US idênticos ao pt-BR (es-ES tem muitos cognatos legítimos), candidatos a "esqueci de traduzir".
 */
import { readFileSync, readdirSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { parse, TYPE } from "@formatjs/icu-messageformat-parser";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../src/i18n/messages");
const SOURCE = "pt-BR";
const TARGETS = ["en-US", "es-ES"];
const backendBundle = resolve(here, "../../scores-af-application/src/main/resources/i18n/messages.properties");

const errors = [];
const warnings = [];
const load = (loc, ns) => JSON.parse(readFileSync(resolve(root, loc, `${ns}.json`), "utf8"));

function argsOf(ast, acc = new Set()) {
  for (const el of ast) {
    if (el.type === TYPE.argument || el.type === TYPE.number || el.type === TYPE.date || el.type === TYPE.time) acc.add(el.value);
    if (el.type === TYPE.plural || el.type === TYPE.select) {
      acc.add(el.value);
      for (const opt of Object.values(el.options)) argsOf(opt.value, acc);
    }
    if (el.type === TYPE.tag) argsOf(el.children, acc);
  }
  return acc;
}
function parseOrFail(loc, ns, key, msg) {
  try {
    return argsOf(parse(msg, { ignoreTag: true }));
  } catch (e) {
    errors.push(`${loc}/${ns}.json › ${key}: ICU inválido (${e.message})`);
    return null;
  }
}

const namespaces = readdirSync(resolve(root, SOURCE)).filter((f) => f.endsWith(".json")).map((f) => f.slice(0, -5));
let total = 0;
for (const ns of namespaces) {
  const src = load(SOURCE, ns);
  const srcArgs = {};
  for (const [k, v] of Object.entries(src)) {
    total++;
    srcArgs[k] = parseOrFail(SOURCE, ns, k, v);
  }
  for (const loc of TARGETS) {
    if (!existsSync(resolve(root, loc, `${ns}.json`))) {
      errors.push(`${loc}/${ns}.json ausente`);
      continue;
    }
    const tgt = load(loc, ns);
    for (const k of Object.keys(src)) if (!(k in tgt)) errors.push(`${loc}/${ns}.json › chave ausente: ${k}`);
    for (const k of Object.keys(tgt)) if (!(k in src)) errors.push(`${loc}/${ns}.json › chave sobrando: ${k}`);
    for (const [k, v] of Object.entries(tgt)) {
      if (!(k in src)) continue;
      const a = parseOrFail(loc, ns, k, v);
      const b = srcArgs[k];
      if (a && b) {
        const x = [...a].sort().join(","), y = [...b].sort().join(",");
        if (x !== y) errors.push(`${loc}/${ns}.json › ${k}: argumentos {${x}} ≠ pt-BR {${y}}`);
      }
      if (loc === "en-US" && v === src[k] && /[a-zà-ú]{4,}/i.test(v) && !/^[A-Z0-9 &·/%+()\-.:]+$/.test(v)) warnings.push(`${loc}/${ns}.json › ${k}: igual ao pt-BR ("${v}")`);
    }
  }
}

if (existsSync(backendBundle)) {
  const codes = readFileSync(backendBundle, "utf8").split("\n").map((l) => l.match(/^error\.([A-Z0-9_]+)=/)?.[1]).filter(Boolean);
  for (const loc of [SOURCE, ...TARGETS]) {
    const e = load(loc, "errors");
    for (const c of codes) if (!(c in e)) errors.push(`${loc}/errors.json › código do backend sem tradução: ${c}`);
  }
}

for (const w of warnings) console.warn(`aviso: ${w}`);
if (errors.length) {
  for (const e of errors) console.error(`erro: ${e}`);
  console.error(`\n✗ i18n: ${errors.length} problema(s) em ${namespaces.length} namespaces.`);
  process.exit(1);
}
console.log(`✓ i18n: ${total} chaves × ${TARGETS.length + 1} idiomas em ${namespaces.length} namespaces; ${warnings.length} aviso(s).`);
