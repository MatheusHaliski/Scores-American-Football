/**
 * Situação da semana atual na central e na preparação (bloco 6c), sem React: temporada regular, rodada de playoff com jogo do
 * usuário, rodada em que ele folga (semente com bye), rodada em que ele não joga (eliminado ou fora: "jogar" só fecha a semana, como a
 * folga da D-92) ou temporada encerrada (depois da final, `seasonPhase` OFFSEASON).
 */
import type { CareerDashboard } from "@/lib/api/career-types";

export type WeekSituation = "regular" | "playoffGame" | "playoffBye" | "playoffOut" | "seasonOver";

export function weekSituation(d: Pick<CareerDashboard, "currentWeek" | "nextGame" | "byeWeek" | "career">): WeekSituation {
  if (d.career.seasonPhase === "OFFSEASON") return "seasonOver";
  if (d.currentWeek <= d.career.regularSeasonWeeks) return "regular";
  if (d.byeWeek) return "playoffBye";
  return d.nextGame && d.nextGame.fixture.week === d.currentWeek ? "playoffGame" : "playoffOut";
}
