/**
 * Tipos da temporada (bloco 6c): classificação (RF16), playoffs e chave (RF66), notícias da semana (RF21), resumo, avanço e sala de
 * troféus (RF35/RF17), moral do elenco (RF41) e analista do sistema (D-104). Espelham `SeasonDtos`, `MoraleDtos` e `AnalysisDtos` do
 * backend, já normalizados por `live-season.ts`: campo anulável que a API omite vira `null`, nunca `undefined` nem zero. Enums como
 * texto (o front conhece os valores, mas aceita um novo sem quebrar); dinheiro em centavos de S$; datas em ISO-8601.
 */
import type { ClubRef, Fixture, TeamRecord } from "./career-types";
import type { CareerSummary } from "./career-types";
import type { ISODate, UUID } from "./types";

/* ───────────────────────── RF16 — classificação ───────────────────────── */

export const TIEBREAKERS = ["HEAD_TO_HEAD", "DIVISION", "COMMON_GAMES", "CONFERENCE", "STRENGTH_OF_VICTORY", "STRENGTH_OF_SCHEDULE", "POINT_DIFFERENTIAL", "PROJECTION", "DRAW"] as const;
export type Tiebreaker = (typeof TIEBREAKERS)[number];
/** O que a franquia já garantiu (`clinch`): folga na 1ª rodada, título da divisão ou vaga. */
export type Clinch = "BYE" | "DIVISION" | "PLAYOFFS";
export const CLINCHES: Clinch[] = ["BYE", "DIVISION", "PLAYOFFS"];

/** `StandingRowDto`: linha da tabela da divisão ou da conferência. */
export interface StandingsRow {
  rank: number;
  club: ClubRef;
  record: TeamRecord;
  winPct: number;
  pointsFor: number;
  pointsAgainst: number;
  pointDiff: number;
  /** "W3", "L1", "T1" ou "—" sem jogos */
  streak: string;
  /** últimos resultados, do mais antigo ao mais recente ("W" | "L" | "T") */
  lastFive: string[];
  home: TeamRecord;
  away: TeamRecord;
  division: TeamRecord;
  conference: TeamRecord;
  /** semente atual (projeção durante a temporada regular, oficial depois) ou null fora da zona */
  seed: number | null;
  clinch: Clinch | string | null;
  eliminated: boolean;
  /** critério que pôs a franquia à frente das empatadas logo abaixo (null sem empate) */
  tiebreaker: Tiebreaker | string | null;
  userClub: boolean;
}

export interface DivisionTable {
  id: UUID;
  name: string;
  rows: StandingsRow[];
}

/** Conferência: divisões e a tabela da conferência inteira (sementes primeiro, depois a disputa dos wild cards). */
export interface ConferenceTable {
  id: UUID;
  name: string;
  shortName: string;
  divisions: DivisionTable[];
  rows: StandingsRow[];
}

/** `StandingsDto` (RF16). `official` só depois da temporada regular: antes, as sementes são projeção. */
export interface Standings {
  seasonId: UUID;
  seasonYear: number;
  phase: string;
  throughWeek: number;
  regularSeasonWeeks: number;
  official: boolean;
  seedsPerConference: number;
  byeSeeds: number;
  userClubId: UUID | null;
  divisionTiebreakers: string[];
  wildCardTiebreakers: string[];
  conferences: ConferenceTable[];
}

/* ───────────────────────── RF66 — playoffs e chave ───────────────────────── */

export const PLAYOFF_ROUNDS = ["WILD_CARD", "DIVISIONAL", "CONFERENCE", "FINAL"] as const;
export type PlayoffRound = (typeof PLAYOFF_ROUNDS)[number];
export type PlayoffsStatus = "NOT_STARTED" | "IN_PROGRESS" | "FINISHED";
export type SeedStatus = "PROJECTED" | "ALIVE" | "ELIMINATED" | "CHAMPION";
/** Situação do usuário nos playoffs (antes: PROJECTED_*; durante: BYE, ALIVE, ELIMINATED, MISSED; depois: CHAMPION, RUNNER_UP…). */
export type UserPlayoffStatus = "PROJECTED_IN" | "PROJECTED_OUT" | "BYE" | "ALIVE" | "ELIMINATED" | "MISSED" | "CHAMPION" | "RUNNER_UP";

export interface PlayoffSeed {
  seed: number;
  club: ClubRef;
  record: TeamRecord;
  divisionChampion: boolean;
  /** folga na primeira rodada */
  bye: boolean;
  status: SeedStatus | string;
  tiebreaker: string | null;
}

export interface ConferenceSeeds {
  id: UUID;
  name: string;
  shortName: string;
  seeds: PlayoffSeed[];
}

/** `UserPlayoffDto`: o usuário nos playoffs (também na central). */
export interface UserPlayoff {
  clubId: UUID;
  seed: number | null;
  status: UserPlayoffStatus | string;
  /** rodada atual, quando o usuário segue vivo */
  round: PlayoffRound | string | null;
  nextGame: Fixture | null;
}

/** `PlayoffsDto` (RF66). */
export interface PlayoffSituation {
  seasonId: UUID;
  status: PlayoffsStatus | string;
  /** sementes projetadas pela classificação (antes do fim da temporada regular) */
  projection: boolean;
  currentRound: PlayoffRound | string | null;
  seedsPerConference: number;
  byeSeeds: number;
  neutralSiteFinal: boolean;
  /** rodadas planejadas pelas regras, na ordem */
  rounds: string[];
  conferences: ConferenceSeeds[];
  user: UserPlayoff | null;
  champion: ClubRef | null;
  runnerUp: ClubRef | null;
}

export interface BracketTeam {
  club: ClubRef;
  seed: number;
}

/** Jogo da chave: o do calendário (placar, prorrogação, vencedor) ou projetado (sem `fixtureId`, status PROJECTED). */
export interface BracketGame {
  fixtureId: UUID | null;
  /** sigla da conferência; null na final */
  conference: string | null;
  home: BracketTeam;
  away: BracketTeam;
  homeScore: number | null;
  awayScore: number | null;
  overtime: boolean;
  winnerClubId: UUID | null;
  neutralSite: boolean;
  status: string;
  date: ISODate | null;
  kickoffSlot: string | null;
  userGame: boolean;
}

/** Rodada: semana do calendário (null se ainda não criada) e situação (PROJECTED, PENDING ou a da semana: CURRENT, COMPLETED…). */
export interface BracketRound {
  index: number;
  kind: PlayoffRound | string;
  week: number | null;
  status: string;
  games: BracketGame[];
  byes: BracketTeam[];
}

export interface Bracket {
  seasonId: UUID;
  status: PlayoffsStatus | string;
  userClubId: UUID | null;
  rounds: BracketRound[];
  champion: ClubRef | null;
}

/* ───────────────────────── RF21 — notícias ───────────────────────── */

export type NewsTone = "POSITIVE" | "NEGATIVE" | "NEUTRAL" | "DRAMATIC";
/** Valor de parâmetro de notícia/analista: texto ou número (o backend manda `Map<String, Object>`). */
export type ParamValue = string | number | boolean;

/** `NewsDto`: notícia gerada por regras (D-103), com a chave do texto e os parâmetros para o front traduzir. */
export interface NewsItem {
  id: UUID;
  week: number;
  kind: string;
  tone: NewsTone | string;
  /** manchete e texto no idioma do pedido (reserva quando o front não conhece a chave) */
  headline: string;
  body: string;
  /** "news.USER_WIN" → `season:news.USER_WIN.headline` / `.body` */
  textKey: string;
  params: Record<string, ParamValue>;
  clubId: UUID | null;
  playerId: UUID | null;
  fixtureId: UUID | null;
}

export interface WeekNews {
  seasonId: UUID;
  week: number;
  weekKind: string | null;
  completed: boolean;
  items: NewsItem[];
}

/* ───────────────────────── RF35 — resumo, avanço e troféus ───────────────────────── */

export const AWARD_TYPES = ["MVP", "OPOY", "DPOY", "OROY", "DROY", "COACH"] as const;
export const LEADER_CATEGORIES = ["PASS_YARDS", "PASS_TOUCHDOWNS", "RUSH_YARDS", "RUSH_TOUCHDOWNS", "RECEIVING_YARDS", "RECEPTIONS", "SACKS", "INTERCEPTIONS", "TACKLES"] as const;
export type PlayoffResult = "CHAMPION" | "RUNNER_UP" | "CONFERENCE" | "DIVISIONAL" | "WILD_CARD" | "MISSED" | "IN_PROGRESS";

/** Prêmio: jogador (ou head coach, sem `playerId`/`position`), franquia, nota e a linha de estatísticas. */
export interface Award {
  type: string;
  playerId: UUID | null;
  name: string;
  position: string | null;
  club: ClubRef | null;
  score: number;
  statLine: Record<string, number>;
}

export interface SeasonLeader {
  playerId: UUID;
  name: string;
  position: string;
  club: ClubRef | null;
  value: number;
  games: number;
}

export interface Campaign {
  record: TeamRecord;
  pointsFor: number;
  pointsAgainst: number;
  divisionRank: number | null;
  conferenceRank: number | null;
  seed: number | null;
  playoffResult: PlayoffResult | string | null;
}

export interface ExpiringContract {
  playerId: UUID;
  name: string;
  position: string;
  age: number;
  overall: number;
  endSeason: number;
  capHit: number;
}

/** Passo da entressafra (D-10): fase, situação (PENDING, DONE, SKIPPED) e quem cuida (SEASON_ADVANCE ou BLOCK_7). */
export interface OffseasonStep {
  phase: string;
  status: string;
  handledBy: string;
}

export interface Offseason {
  canAdvance: boolean;
  steps: OffseasonStep[];
  expiringContracts: ExpiringContract[];
}

/** `SeasonSummaryDto` (RF35): prêmios provisórios até a final. */
export interface SeasonSummary {
  seasonId: UUID;
  number: number;
  year: number;
  phase: string;
  completed: boolean;
  champion: ClubRef | null;
  runnerUp: ClubRef | null;
  userClubId: UUID | null;
  campaign: Campaign | null;
  provisionalAwards: boolean;
  awards: Award[];
  leaders: Record<string, SeasonLeader[]>;
  offseason: Offseason | null;
}

/**
 * `SeasonAdvanceDto` (D-99, D-120): a temporada nova, os passos, os contratos que viraram agentes livres (`expiredContracts`), os
 * renovados sozinhos pelas franquias da IA (`aiRenewals`), os da franquia do usuário que terminaram (pendência do bloco 7), a progressão
 * e o dead money levado.
 */
export interface SeasonAdvanceResult {
  previousSeasonId: UUID;
  seasonId: UUID;
  seasonNumber: number;
  seasonYear: number;
  phase: string;
  currentWeek: number;
  steps: OffseasonStep[];
  /** contratos que viraram agentes livres (os do usuário e os que a IA não renovou) */
  expiredContracts: number;
  /** contratos renovados automaticamente pelas franquias da IA (D-120) */
  aiRenewals: number;
  userExpiredContracts: ExpiringContract[];
  improved: number;
  declined: number;
  deadMoneyCarried: number;
  career: CareerSummary;
}

/** POST /season/advance: `seasonId` é a temporada que o cliente quer fechar (409 SEASON_ALREADY_ADVANCED se já foi fechada). */
export interface SeasonAdvanceRequest {
  seasonId?: UUID | null;
}

export type TrophyKind = "CHAMPIONSHIP" | "CONFERENCE" | "DIVISION";

export interface Trophy {
  seasonId: UUID;
  seasonNumber: number;
  seasonYear: number;
  kind: TrophyKind | string;
  club: ClubRef | null;
  conference: string | null;
  division: string | null;
  userClub: boolean;
}

export interface SeasonHistory {
  seasonId: UUID;
  number: number;
  year: number;
  completed: boolean;
  champion: ClubRef | null;
  runnerUp: ClubRef | null;
  mvp: Award | null;
  userRecord: TeamRecord | null;
  userResult: PlayoffResult | string | null;
}

export interface Trophies {
  userClubId: UUID | null;
  championships: number;
  conferenceTitles: number;
  divisionTitles: number;
  playoffAppearances: number;
  trophies: Trophy[];
  seasons: SeasonHistory[];
}

/* ───────────────────────── RF41 — moral ───────────────────────── */

export const MORALE_LABELS = ["HIGH", "GOOD", "NEUTRAL", "LOW", "CRITICAL"] as const;
export type MoraleLabel = (typeof MORALE_LABELS)[number];
export type MoraleTrend = "UP" | "DOWN" | "STABLE";
export type MoraleRole = "STARTER" | "ROTATION" | "BACKUP" | "RESERVE" | "SPECIALIST";
export const MORALE_FACTORS = ["BASELINE", "ROLE", "CONTRACT", "AGE", "RESULT", "SNAPS", "STREAK", "WEEK_LIMIT"] as const;

export interface PlayerMorale {
  playerId: UUID;
  name: string;
  position: string;
  jerseyNumber: number;
  rosterStatus: string;
  morale: number;
  label: MoraleLabel | string;
  trend: MoraleTrend | string;
  lastDelta: number;
  role: MoraleRole | string;
  /** fatias de snaps (0–1); null para especialistas e para quem está fora do depth chart */
  expectedSnapShare: number | null;
  lastSnapShare: number | null;
  seasonSnapShare: number | null;
  contractFinalYear: boolean;
  contractYearsLeft: number;
  tradeRequest: boolean;
  /** calculado, mas fora do motor neste bloco (D-102) */
  performanceModifier: number;
  /** fatores da última semana, em pontos de moral */
  factors: Record<string, number>;
}

/** `SquadMoraleDto`: moral do elenco (os 53) até a última semana encerrada. */
export interface SquadMorale {
  seasonId: UUID;
  throughWeek: number;
  average: number;
  label: MoraleLabel | string;
  trend: MoraleTrend | string;
  lastDelta: number;
  distribution: Record<string, number>;
  tradeRequests: number;
  teamStreak: string | null;
  record: TeamRecord;
  players: PlayerMorale[];
}

/** `MoraleSummaryDto` da central: `low` = quantos estão em LOW ou CRITICAL. */
export interface MoraleSummary {
  average: number;
  label: MoraleLabel | string;
  trend: MoraleTrend | string;
  low: number;
  tradeRequests: number;
}

/* ───────────────────────── analista (D-104) ───────────────────────── */

export const ANALYSIS_UNITS = ["QB", "OFFENSIVE_LINE", "RUNNING_BACK", "RECEIVERS", "DEFENSIVE_LINE", "LINEBACKERS", "SECONDARY", "SPECIAL_TEAMS"] as const;
export type AnalysisUnit = (typeof ANALYSIS_UNITS)[number];
export type Verdict = "FAVORITE" | "EVEN" | "UNDERDOG";

export interface UnitRating {
  unit: AnalysisUnit | string;
  rating: number;
  leagueAverage: number;
  diff: number;
}

export interface AnalystMatchup {
  key: string;
  ours: number;
  theirs: number;
  edge: number;
  advantage: string;
}

export interface Outlook {
  fixtureId: UUID;
  week: number;
  opponent: ClubRef;
  home: boolean;
  neutralSite: boolean;
  strength: number;
  opponentStrength: number;
  /** 0–1 */
  winProbability: number;
  verdict: Verdict | string;
  biggestThreat: string | null;
  matchups: AnalystMatchup[];
}

/** Troca recomendada no depth chart: quem entra e quem sai da titularidade da vaga. */
export interface DepthChange {
  slot: string;
  inPlayerId: UUID;
  inName: string;
  inOverall: number;
  outPlayerId: UUID;
  outName: string;
  outOverall: number;
  gain: number;
}

/** Ajuste do plano de jogo: campo, valor salvo, recomendado e o motivo (código). */
export interface PlanAdvice {
  field: string;
  current: string;
  recommended: string;
  reason: string;
}

/** `AnalysisDto`: o relatório mais recente do analista por regras. */
export interface Analysis {
  id: UUID;
  seasonId: UUID;
  week: number;
  source: string;
  generatedAt: ISODate;
  summaryCode: string;
  /** resumo no idioma do pedido (reserva) */
  summary: string;
  summaryParams: Record<string, ParamValue>;
  strength: number;
  leagueStrength: number;
  units: UnitRating[];
  strengths: UnitRating[];
  weaknesses: UnitRating[];
  upcoming: Outlook[];
  depthChart: DepthChange[];
  gamePlan: PlanAdvice[];
}

/** `AnalysisSummaryDto` da central (nulo antes do primeiro relatório). */
export interface AnalysisSummary {
  id: UUID;
  week: number;
  generatedAt: ISODate;
  summaryCode: string;
  summary: string;
  nextWinProbability: number | null;
}
