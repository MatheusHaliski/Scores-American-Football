/** Hash FNV-1a de 32 bits (determinístico, sem dependência). */
export function hashString(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** UUID v4 "de mentira", determinístico a partir de um prefixo e de uma semente (ids estáveis no mock). */
export function uuidFrom(prefix: string, n: number | string): string {
  const h = hashString(`${prefix}:${n}`).toString(16).padStart(8, "0");
  const h2 = hashString(`${n}:${prefix}`).toString(16).padStart(8, "0");
  return `${h}-${h2.slice(0, 4)}-4${h2.slice(4, 7)}-8${h.slice(1, 4)}-${h2}${h.slice(0, 4)}`;
}
