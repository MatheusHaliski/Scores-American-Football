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

/** Amostras da carreira (bloco 6a) e a franquia do editor usada na nova carreira. */
export const CAREER_GOLDENS = ["careers-list.json", "career-dashboard.json", "career-calendar.json", "roster.json", "roster-player.json", "depth-chart.json", "problem-409-depth-chart.json", "world-editor-club.json"] as const;

/** Amostras do bloco 6b: plano de jogo, playbook, preparação, semana, partida ao vivo, play-by-play e relatório. */
export const MATCH_GOLDENS = ["game-plan.json", "playbook.json", "problem-409-game-plan.json", "preparation.json", "week.json", "match-state.json", "match-events.json", "match-report.json", "live-match.json"] as const;

/** Amostras do bloco 6c: classificação, playoffs e chave, notícias, resumo, avanço, troféus, moral, analista e os erros do avanço. */
export const SEASON_GOLDENS = ["standings.json", "week-news.json", "playoffs.json", "playoffs-bracket.json", "season-summary.json", "season-advance.json", "trophies.json", "morale.json", "analysis.json", "problem-422-season-not-finished.json", "problem-409-season-advance.json"] as const;
