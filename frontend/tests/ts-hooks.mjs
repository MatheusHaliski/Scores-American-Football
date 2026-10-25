// Resolução para testes em Node (node --test --experimental-strip-types): alias "@/…" → src/ e imports sem extensão → .ts
import { statSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

const SRC = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../src");

function isFile(p) {
  try {
    return statSync(p).isFile();
  } catch {
    return false;
  }
}

function withTs(file) {
  for (const cand of [file, `${file}.ts`, `${file}.tsx`, path.join(file, "index.ts")]) if (isFile(cand)) return cand;
  return null;
}

export async function resolve(spec, ctx, next) {
  if (spec.startsWith("@/")) {
    const hit = withTs(path.join(SRC, spec.slice(2)));
    if (hit) return next(pathToFileURL(hit).href, ctx);
  }
  if ((spec.startsWith("./") || spec.startsWith("../")) && ctx.parentURL?.startsWith("file:")) {
    const base = path.dirname(fileURLToPath(ctx.parentURL));
    const hit = withTs(path.resolve(base, spec));
    if (hit) return next(pathToFileURL(hit).href, ctx);
  }
  return next(spec, ctx);
}

// JSON importado sem atributo (como faz o bundler): vira um módulo ES com `export default`.
export async function load(url, ctx, next) {
  if (url.startsWith("file:") && url.split("?")[0].endsWith(".json")) {
    const { readFile } = await import("node:fs/promises");
    const source = await readFile(new URL(url.split("?")[0]), "utf8");
    return { format: "module", source: `export default ${source};`, shortCircuit: true };
  }
  return next(url, ctx);
}
