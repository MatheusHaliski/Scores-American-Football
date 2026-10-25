// Amostras douradas gravadas pelo backend (ApiGoldenTest, D-5). O front só lê.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DIR = path.resolve(HERE, "../../docs/validation/golden");
export const ACCOUNT_GOLDENS = ["auth-login.json", "me.json", "me-settings.json", "problem-429.json"] as const;

export function loadGolden(file: string): unknown {
  return JSON.parse(readFileSync(path.join(DIR, file), "utf8"));
}

/** Amostra de conta gravada pelo backend (ApiGoldenTest). */
export function loadAccountGolden(file: (typeof ACCOUNT_GOLDENS)[number]): unknown {
  return loadGolden(file);
}
