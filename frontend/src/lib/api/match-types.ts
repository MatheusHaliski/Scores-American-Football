/**
 * Tipos do bloco 6b: plano de jogo e playbook (RF61), preparação (RF7), painel da semana e partida ao vivo (RF27/RF29/RF62) e
 * relatório pós-jogo. Espelham `GamePlanDtos`, `MatchDtos` e `LiveMatchDto` do backend, já normalizados por `live-match.ts`: campo
 * anulável que a API omite vira `null`. Lances, drives, box score e decisões vêm no vocabulário do motor (D-5, D-67).
 */
import type { CardTier, ClubRef, DepthIssue, Fixture, Position, TeamRecord } from "./career-types";
import type { ISODate, UUID } from "./types";

/* ───────────────────────── RF61 plano de jogo e playbook ───────────────────────── */

export type FourthDownAggression = "CONSERVATIVE" | "NORMAL" | "AGGRESSIVE";
export type Tempo = "HURRY_UP" | "NORMAL" | "MILK_CLOCK";
export type DefensiveFocus = "STOP_RUN" | "BALANCED" | "STOP_PASS";
export type BlitzFrequency = "LOW" | "NORMAL" | "HIGH";
export type BaseDefense = "BASE_4_3" | "BASE_3_4";

export const FOURTH_DOWN_AGGRESSIONS: FourthDownAggression[] = ["CONSERVATIVE", "NORMAL", "AGGRESSIVE"];
export const TEMPOS: Tempo[] = ["HURRY_UP", "NORMAL", "MILK_CLOCK"];
export const DEFENSIVE_FOCUSES: DefensiveFocus[] = ["STOP_RUN", "BALANCED", "STOP_PASS"];
export const BLITZ_FREQUENCIES: BlitzFrequency[] = ["LOW", "NORMAL", "HIGH"];
export const BASE_DEFENSES: BaseDefense[] = ["BASE_4_3", "BASE_3_4"];

/** Valores aceitos no PUT do plano (`GamePlanOptionsDto`): o front monta os seletores daqui. */
export interface GamePlanOptions {
  minPassRatio: number;
  maxPassRatio: number;
  fourthDownAggression: FourthDownAggression[];
  tempo: Tempo[];
  defensiveFocus: DefensiveFocus[];
  blitzFrequency: BlitzFrequency[];
  baseDefense: BaseDefense[];
}

/** As tendências do plano que o motor usa (o corpo do PUT sem a versão; também o ajuste GAME_PLAN do intervalo). */
export interface PlanTendencies {
  passRatio: number;
  fourthDownAggression: FourthDownAggression;
  tempo: Tempo;
  defensiveFocus: DefensiveFocus;
  blitzFrequency: BlitzFrequency;
  baseDefense: BaseDefense;
}

export const PLAN_FIELDS: (keyof PlanTendencies)[] = ["passRatio", "fourthDownAggression", "tempo", "defensiveFocus", "blitzFrequency", "baseDefense"];

/** `GamePlanDto`: `source` COACH_STYLE até o primeiro PUT (padrão do estilo do head coach), depois SAVED (D-65). */
export interface GamePlan extends PlanTendencies {
  id: UUID;
  clubId: UUID;
  version: number;
  coachStyle: string | null;
  source: "COACH_STYLE" | "SAVED";
  editable: boolean;
  updatedAt: ISODate | null;
  options: GamePlanOptions;
}

export interface SaveGamePlanRequest extends PlanTendencies {
  version: number;
}

export type PlaybookSituationKey = "EARLY_DOWN" | "THIRD_DOWN" | "THIRD_AND_LONG" | "RED_ZONE" | "TWO_MINUTE";
export const PLAYBOOK_SITUATIONS: PlaybookSituationKey[] = ["EARLY_DOWN", "THIRD_DOWN", "THIRD_AND_LONG", "RED_ZONE", "TWO_MINUTE"];
export type CoverageKey = "COVER_0" | "COVER_1" | "COVER_2" | "COVER_3" | "COVER_4" | "COVER_6";
export const COVERAGES: CoverageKey[] = ["COVER_0", "COVER_1", "COVER_2", "COVER_3", "COVER_4", "COVER_6"];
/** Corridas que contam para o mínimo do playbook (o QB sneak não conta, `GamePlanPolicy`). */
export const REQUIRED_RUNS = ["INSIDE_RUN", "OUTSIDE_RUN"] as const;
/** Coberturas ativas mínimas (`GamePlanPolicy.MIN_ACTIVE_COVERAGES`). */
export const MIN_ACTIVE_COVERAGES = 2;

/** Peso de um conceito ou cobertura: 0–200, 100 = padrão da liga, 0 = desativado (D-58). */
export interface WeightEntry {
  item: string;
  weight: number;
}

export interface PlaybookSituation {
  situation: string;
  concepts: WeightEntry[];
}

/** `PlaybookDto`. */
export interface Playbook {
  id: UUID;
  clubId: UUID;
  version: number;
  minWeight: number;
  maxWeight: number;
  neutralWeight: number;
  runConcepts: string[];
  passConcepts: string[];
  situations: PlaybookSituation[];
  coverages: WeightEntry[];
  customWeights: number;
  updatedAt: ISODate | null;
}

/** Pesos do playbook (corpo do PUT sem a versão; também o ajuste PLAYBOOK do intervalo). */
export interface PlaybookWeights {
  situations: PlaybookSituation[];
  coverages: WeightEntry[];
}

export interface SavePlaybookRequest extends PlaybookWeights {
  version: number;
}

/** Item de `details.errors` do 422 GAME_PLAN_INVALID / PLAYBOOK_INVALID / PLAYBOOK_INCOMPLETE (`GamePlanPolicy.Issue`). */
export interface PlanIssue {
  code: string;
  /** `passRatio`, `EARLY_DOWN.DEEP_PASS`, `coverages.COVER_3`, `EARLY_DOWN` (situação inteira), `coverages`, `situations` */
  field: string;
  value: string | null;
}

/* ───────────────────────── RF7 preparação ───────────────────────── */

export interface PlayerRef {
  id: UUID;
  name: string;
  position: Position;
  jerseyNumber: number;
  overall: number;
  tier: CardTier;
}

export interface UnitComparison {
  unit: string;
  ours: number;
  theirs: number;
  edge: number;
}

export type MatchupKey = "WR_VS_CB" | "OL_VS_DL" | "QB_VS_PASS_RUSH";
export type MatchupSide = "OUR_OFFENSE" | "THEIR_OFFENSE";
export type Advantage = "OURS" | "THEIRS" | "EVEN";

export interface Matchup {
  key: MatchupKey | string;
  side: MatchupSide | string;
  ours: number;
  theirs: number;
  edge: number;
  advantage: Advantage | string;
  ourPlayers: PlayerRef[];
  theirPlayers: PlayerRef[];
}

export interface Readiness {
  ready: boolean;
  depthChartVersion: number;
  errors: DepthIssue[];
  warnings: DepthIssue[];
}

export interface WeatherForecast {
  condition: string;
  stadium: string | null;
  roof: string | null;
  surface: string | null;
}

/** `PreparationDto` (RF7, D-69). `club` é a franquia do usuário; `matchId` quando a partida já foi iniciada. */
export interface Preparation {
  fixture: Fixture;
  home: boolean;
  club: ClubRef;
  opponent: ClubRef;
  record: TeamRecord;
  opponentRecord: TeamRecord;
  weather: WeatherForecast;
  units: UnitComparison[];
  keyMatchups: Matchup[];
  keyPlayers: PlayerRef[];
  opponentKeyPlayers: PlayerRef[];
  readiness: Readiness;
  gamePlan: GamePlan;
  playbookCustomWeights: number;
  playCallingLevel: string;
  matchId: UUID | null;
}

/* ───────────────────────── RF27 painel da semana ───────────────────────── */

export type MatchSessionStatus = "NOT_STARTED" | "LIVE" | "BREAK" | "HALFTIME" | "AWAITING_DECISION" | "FINISHED";
export const MATCH_SESSION_STATUSES: MatchSessionStatus[] = ["NOT_STARTED", "LIVE", "BREAK", "HALFTIME", "AWAITING_DECISION", "FINISHED"];

export interface LiveLine {
  matchId: UUID;
  status: MatchSessionStatus;
  quarter: number;
  clockSeconds: number;
  possessionClubId: UUID | null;
  down: number;
  distance: number;
  ballOnAbsYd: number;
  homeScore: number;
  awayScore: number;
}

export interface WeekGame {
  fixture: Fixture;
  home: ClubRef;
  away: ClubRef;
  live: LiveLine | null;
}

/** `WeekBoardDto`: resposta de `POST /weeks/current/play` e de `GET /weeks/{n}`. */
export interface WeekBoard {
  seasonId: UUID;
  week: number;
  regularSeasonWeeks: number;
  currentWeek: number;
  kind: string | null;
  status: string | null;
  startsOn: ISODate | null;
  completed: boolean;
  userBye: boolean;
  userFixtureId: UUID | null;
  matchId: UUID | null;
  byeClubIds: UUID[];
  games: WeekGame[];
}

/* ───────────────────────── RF27/RF29/RF62 partida ao vivo ───────────────────────── */

/** Modos de avanço (`AdvanceMode`): todos param na próxima decisão do manager e no fim (D-67). */
export type AdvanceMode = "PLAY" | "DRIVE" | "QUARTER" | "TO_DECISION" | "FULL";
export const ADVANCE_MODES: AdvanceMode[] = ["PLAY", "DRIVE", "QUARTER", "TO_DECISION", "FULL"];

export type CallSource = "AI" | "USER" | "AUTO";

export interface Penalty {
  code: string;
  againstClubId: UUID;
  playerId: UUID | null;
  yards: number;
  accepted: boolean;
  automaticFirstDown: boolean;
  lossOfDown: boolean;
  preSnap: boolean;
}

/** Lance do motor (`PlayEvent`): o texto é a chave i18n `textKey` com `params`, nunca a frase pronta (RF54). */
export interface PlayEvent {
  seq: number;
  drive: number;
  quarter: number;
  clockSeconds: number;
  possessionClubId: UUID | null;
  down: number;
  distance: number;
  ballOnAbsYd: number;
  type: string | null;
  concept: string | null;
  formation: string | null;
  defensePackage: string | null;
  coverage: string | null;
  blitz: boolean;
  yards: number;
  result: string | null;
  firstDown: boolean;
  turnover: boolean;
  points: number;
  scoringClubId: UUID | null;
  penalty: Penalty | null;
  playerIds: UUID[];
  textKey: string;
  params: Record<string, string>;
  homeScore: number;
  awayScore: number;
  clockStopped: boolean;
  driveEnded: string | null;
  callSource: CallSource | null;
}

export interface Drive {
  seq: number;
  clubId: UUID;
  quarter: number;
  startClockSeconds: number;
  startAbsYd: number;
  endAbsYd: number;
  plays: number;
  yards: number;
  timeOfPossessionSeconds: number;
  reachedRedZone: boolean;
  result: string | null;
}

export interface PlayOption {
  code: string;
  side: "OFFENSE" | "DEFENSE" | string;
  concept: string | null;
  formation: string | null;
  defensePackage: string | null;
  coverage: string | null;
  blitz: boolean;
  labelKey: string;
}

/** Decisão aberta (RF62): o manager escolhe uma das `options` até `decisionDeadlineAt`; vencido o prazo, vale `autoOptionCode`. */
export interface PendingDecision {
  id: string;
  situation: string;
  side: "OFFENSE" | "DEFENSE" | string;
  clubId: UUID;
  quarter: number;
  clockSeconds: number;
  down: number;
  distance: number;
  ballOnAbsYd: number;
  homeScore: number;
  awayScore: number;
  options: PlayOption[];
  autoOptionCode: string;
  openedAtSeq: number;
}

export interface TeamBoxScore {
  clubId: UUID;
  points: number;
  pointsByQuarter: number[];
  firstDowns: number;
  firstDownsRushing: number;
  firstDownsPassing: number;
  firstDownsPenalty: number;
  thirdDownConversions: number;
  thirdDownAttempts: number;
  fourthDownConversions: number;
  fourthDownAttempts: number;
  redZoneTouchdowns: number;
  redZoneTrips: number;
  offensivePlays: number;
  totalYards: number;
  yardsPerPlay: number;
  rushAttempts: number;
  rushYards: number;
  passCompletions: number;
  passAttempts: number;
  passYards: number;
  sacksAllowed: number;
  sackYardsLost: number;
  interceptionsThrown: number;
  fumbles: number;
  fumblesLost: number;
  turnovers: number;
  penalties: number;
  penaltyYards: number;
  timeOfPossessionSeconds: number;
  drives: number;
  punts: number;
  fieldGoalsMade: number;
  fieldGoalAttempts: number;
  defensiveSacks: number;
  timeoutsUsed: number;
}

export interface PassingLine { completions: number; attempts: number; yards: number; touchdowns: number; interceptions: number; sacked: number; sackYards: number; rating: number; longest: number }
export interface RushingLine { attempts: number; yards: number; average: number; touchdowns: number; fumbles: number; fumblesLost: number; longest: number }
export interface ReceivingLine { targets: number; receptions: number; yards: number; yardsAfterCatch: number; touchdowns: number; drops: number; longest: number }
/** `assistedTackles` e `qbHits` o motor não gera (D-35): sempre null, a tela mostra "—". */
export interface DefenseLine { soloTackles: number; assistedTackles: number | null; tacklesForLoss: number; sacks: number; qbHits: number | null; interceptions: number; passesDefended: number; forcedFumbles: number; fumbleRecoveries: number; touchdowns: number }
export interface KickingLine { fgMade: number; fgAttempts: number; fgMade0to39: number; fgAttempts0to39: number; fgMade40to49: number; fgAttempts40to49: number; fgMade50plus: number; fgAttempts50plus: number; longest: number; patMade: number; patAttempts: number }
export interface PuntingLine { punts: number; yards: number; average: number; inside20: number; touchbacks: number; longest: number }
export interface ReturnLine { kickReturns: number; kickReturnYards: number; puntReturns: number; puntReturnYards: number; touchdowns: number; longest: number }

/** Linha do box score de um jogador: cada grupo só existe se o jogador fez algo nele. */
export interface PlayerBoxScore {
  playerId: UUID;
  clubId: UUID;
  name: string;
  position: Position;
  jerseyNumber: number;
  snaps: number;
  passing: PassingLine | null;
  rushing: RushingLine | null;
  receiving: ReceivingLine | null;
  defense: DefenseLine | null;
  kicking: KickingLine | null;
  punting: PuntingLine | null;
  returns: ReturnLine | null;
}

export interface TeamLine {
  clubId: UUID;
  name: string;
  shortName: string;
  score: number;
  quarters: number[];
  timeouts: number;
  stats: TeamBoxScore;
  players: PlayerBoxScore[];
}

/** `LiveMatchDto`: estado do motor (o mesmo JSON de `live-match.json`). Bola em jarda absoluta: 0 = gol da casa, 100 = do visitante. */
export interface LiveMatch {
  fixtureId: UUID;
  version: number;
  engineVersion: number;
  rulesVersion: number;
  ruleset: string;
  weather: string;
  status: string;
  quarter: number;
  clockSeconds: number;
  playClockSeconds: number;
  possessionClubId: UUID | null;
  down: number;
  distance: number;
  ballOnAbsYd: number;
  home: TeamLine;
  away: TeamLine;
  decision: PendingDecision | null;
  lastEvents: PlayEvent[];
  lastDrive: Drive | null;
  driveCount: number;
}

/** `MatchStateDto` (D-67): a sessão mais o estado do motor em `live`. A versão otimista é `live.version`. */
export interface MatchState {
  matchId: UUID;
  careerId: UUID;
  fixtureId: UUID;
  week: number;
  status: MatchSessionStatus;
  userClubId: UUID;
  userHome: boolean;
  playCallingLevel: string;
  /** o playbook do intervalo aceita ajustes agora (NOT_STARTED, BREAK, HALFTIME) */
  adjustable: boolean;
  /** modos aceitos agora; vazio com decisão aberta no prazo ou com a partida encerrada */
  advanceModes: AdvanceMode[];
  decisionDeadlineAt: ISODate | null;
  decisionTimeoutSeconds: number;
  live: LiveMatch;
}

/** Play-by-play com cursor (`MatchEventsDto`): lances com `seq > after`. */
export interface MatchEvents {
  matchId: UUID;
  after: number;
  lastSeq: number;
  finished: boolean;
  events: PlayEvent[];
}

export interface AdvanceRequest {
  mode?: AdvanceMode;
  version?: number;
}

/** Ajuste do intervalo (RF29) ou tempo técnico, pelo `POST /matches/{mid}/decisions`. */
export type MatchAdjustment = { type: "GAME_PLAN"; gamePlan: PlanTendencies } | { type: "PLAYBOOK"; playbook: PlaybookWeights } | { type: "TIMEOUT" };

export interface DecisionsRequest {
  version: number;
  adjustments: MatchAdjustment[];
}

export interface PlayCallRequest {
  optionCode: string;
  version: number;
}

/* ───────────────────────── relatório pós-jogo ───────────────────────── */

export interface ReportSide {
  club: ClubRef;
  score: number;
  quarters: number[];
  stats: TeamBoxScore;
  players: PlayerBoxScore[];
}

export type LeaderCategory = "PASSING" | "RUSHING" | "RECEIVING" | "TACKLES" | "SACKS";

export interface Leader {
  category: LeaderCategory | string;
  clubId: UUID;
  playerId: UUID;
  name: string;
  position: Position;
  value: number;
}

/** Registro do log de decisões: chamadas do manager, automáticas, `TIMEOUT` e `PLAN_CHANGE` (ajuste do intervalo). */
export interface DecisionRecord {
  /** id da chamada de jogada; null em `TIMEOUT` e `PLAN_CHANGE` (o backend omite o campo) */
  decisionId: string | null;
  situation: string | null;
  clubId: UUID | null;
  optionCode: string;
  auto: boolean;
  atSeq: number;
}

/** `MatchReportDto`: de qualquer jogo do calendário (o id da partida ou do jogo). */
export interface MatchReport {
  fixtureId: UUID;
  matchId: UUID | null;
  week: number;
  date: ISODate | null;
  kickoffSlot: string | null;
  userGame: boolean;
  seed: number;
  engineVersion: number;
  rulesVersion: number;
  weather: string | null;
  home: ReportSide;
  away: ReportSide;
  overtime: boolean;
  winnerClubId: UUID | null;
  playCount: number;
  leaders: Leader[];
  drives: Drive[];
  scoringPlays: PlayEvent[];
  decisions: DecisionRecord[];
}

/** `LiveMatchRefDto`: partida em andamento na central (`dashboard.live`). */
export interface LiveMatchRef {
  matchId: UUID;
  fixtureId: UUID;
  week: number;
  status: MatchSessionStatus;
  homeScore: number;
  awayScore: number;
}
