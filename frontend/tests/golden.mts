// Amostras douradas gravadas pelo backend (ApiGoldenTest, D-5). O front só lê.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../docs/validation/golden");

export function loadGolden(file: string): unknown {
  return JSON.parse(readFileSync(path.join(DIR, file), "utf8"));
}
