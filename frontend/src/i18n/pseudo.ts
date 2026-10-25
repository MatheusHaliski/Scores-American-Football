/**
 * Pseudo-localização (qps-ploc): expande ~35 %, troca letras por acentuadas e envolve em colchetes,
 * preservando argumentos ICU (`{…}`), escapes `'…'` e tags. Mesma transformação do
 * `scripts/i18n-check.mjs --pseudo` (mantido em duplicidade proposital: o script não tem dependências).
 */
const MAP: Record<string, string> = {
  a: "á", b: "ƀ", c: "ç", d: "ď", e: "ẽ", f: "ƒ", g: "ĝ", h: "ĥ", i: "ï", j: "ĵ", k: "ķ", l: "ļ", m: "m̂", n: "ñ", o: "ø", p: "þ", q: "q̃", r: "ŕ", s: "š", t: "ŧ", u: "ü", v: "ṽ", w: "ŵ", x: "ẋ", y: "ý", z: "ž",
  A: "Å", B: "Ɓ", C: "Ç", D: "Đ", E: "Ë", F: "Ƒ", G: "Ĝ", H: "Ĥ", I: "Î", J: "Ĵ", K: "Ķ", L: "Ļ", M: "M̂", N: "Ñ", O: "Ö", P: "Þ", Q: "Q̃", R: "Ŕ", S: "Š", T: "Ŧ", U: "Û", V: "Ṽ", W: "Ŵ", X: "Ẋ", Y: "Ý", Z: "Ž",
};

export function pseudoize(message: string): string {
  let out = "";
  let depth = 0;
  let quoted = false;
  let letters = 0;
  for (let i = 0; i < message.length; i++) {
    const ch = message[i];
    if (ch === "'" && !quoted && /[{}#<']/.test(message[i + 1] ?? "")) {
      if (message[i + 1] === "'") {
        out += "''";
        i++;
        continue;
      }
      quoted = true;
      out += ch;
      continue;
    }
    if (ch === "'" && quoted) {
      quoted = false;
      out += ch;
      continue;
    }
    if (quoted) {
      out += ch;
      continue;
    }
    if (ch === "{") depth++;
    if (ch === "}") depth = Math.max(0, depth - 1);
    if (depth > 0 || ch === "{" || ch === "}") {
      out += ch;
      continue;
    }
    if (MAP[ch]) {
      out += MAP[ch];
      letters++;
    } else out += ch;
  }
  const pad = "~".repeat(Math.max(1, Math.round(letters * 0.35)));
  return `[${out} ${pad}]`;
}
