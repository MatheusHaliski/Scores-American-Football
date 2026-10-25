// Catálogos de i18n (RF46): namespaces declarados = catálogos carregados, mesmas chaves nos 3 idiomas e toda chave literal
// usada nas telas (`t("…")` de um `useT("ns")`) existe no catálogo pt-BR. O `npm run i18n:check` confere ICU e argumentos.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { LOCALES, NAMESPACES } from "@/i18n/config";
import { MESSAGES } from "@/i18n/messages";
import { translateIn } from "@/i18n/runtime";

const SRC = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../src");

test("NAMESPACES e MESSAGES andam juntos nos três idiomas", () => {
  for (const loc of LOCALES) assert.deepEqual(Object.keys(MESSAGES[loc]).sort(), [...NAMESPACES].sort(), loc);
});

test("en-US e es-ES têm exatamente as chaves do pt-BR", () => {
  for (const ns of NAMESPACES) {
    const base = Object.keys(MESSAGES["pt-BR"][ns]).sort();
    for (const loc of ["en-US", "es-ES"] as const) assert.deepEqual(Object.keys(MESSAGES[loc][ns]).sort(), base, `${loc}/${ns}`);
  }
});

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((f) => {
    const p = path.join(dir, f);
    return statSync(p).isDirectory() ? files(p) : /\.tsx?$/.test(f) ? [p] : [];
  });
}

test("toda chave literal usada nas telas existe no catálogo", () => {
  const missing: string[] = [];
  for (const file of files(SRC)) {
    const src = readFileSync(file, "utf8");
    for (const m of src.matchAll(/const (\w+) = useT\("(\w+)"\)/g)) {
      const [, fn, ns] = m;
      const dict = (MESSAGES["pt-BR"] as unknown as Record<string, Record<string, string>>)[ns];
      assert.ok(dict, `${file}: namespace ${ns} inexistente`);
      for (const k of src.matchAll(new RegExp(`\\b${fn}\\("([^"]+)"`, "g"))) if (!(k[1] in dict)) missing.push(`${path.relative(SRC, file)}: ${ns}:${k[1]}`);
    }
  }
  assert.deepEqual(missing, []);
});

// "carreira"/"career" saiu da lista no bloco 6a: o modo carreira passou a existir (RF11, /careers).
test("marca e termos do esporte: nada de basquete nos textos da plataforma", () => {
  for (const loc of LOCALES)
    for (const ns of NAMESPACES)
      for (const [k, v] of Object.entries(MESSAGES[loc][ns] as Record<string, string>)) assert.ok(!/basquete|basketball|baloncesto|quadra|cancha|posse de bola/i.test(v), `${loc}/${ns}:${k} = ${v}`);
  assert.equal(translateIn("en-US", "common", "app.sport"), "American Football");
});
