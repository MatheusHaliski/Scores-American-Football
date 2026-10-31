/**
 * Tipos da carreira (bloco 6a): carreiras salvas (RF11), nova carreira (RF34), central da franquia (RF40), calendário (RF21),
 * elenco (RF4/RF56) e depth chart (RF60). Espelham os records `CareerDtos`, `RosterDtos` e `DepthChartDtos` do backend, já
 * normalizados por `live-career.ts`: campo anulável que a API omite vira `null`, nunca `undefined` nem zero. Dinheiro em centavos
 * de S$, datas em ISO-8601.
 */
import type { CardTier } from "@/lib/ratings/tier";
import type { Position } from "@/lib/ratings/overall";
import type { Difficulty, ISODate, UUID } from "./types";
import type { LiveMatchRef } from "./match-types";
import type { AnalysisSummary, MoraleSummary, NewsItem, UserPlayoff } from "./season-types";

export type { CardTier, Position };

export const DIFFICULTIES: Difficulty[] = ["EASY", "NORMAL", "HARD", "LEGEND"];
export type CareerStatus = "ACTIVE" | "ARCHIVED";
export type CoachStyle = "OFFENSIVE" | "DEFENSIVE" | "DEVELOPER" | "MOTIVATOR";
export const COACH_STYLES: CoachStyle[] = ["OFFENSIVE", "DEFENSIVE", "DEVELOPER", "MOTIVATOR"];
export type Unit = "OFFENSE" | "DEFENSE" | "SPECIAL_TEAMS";
export const UNITS: Unit[] = ["OFFENSE", "DEFENSE", "SPECIAL_TEAMS"];
/** `PRACTICE_SQUAD_WAIVERS`: saiu dos 53 rumo ao practice squad e está exposto a waivers até a semana avançar (Q-23, D-78). */
export type RosterStatus = "ACTIVE" | "INACTIVE" | "PRACTICE_SQUAD" | "IR" | "PUP" | "FREE_AGENT" | "WAIVED" | "PRACTICE_SQUAD_WAIVERS";
export const ROSTER_STATUSES: RosterStatus[] = ["ACTIVE", "INACTIVE", "PRACTICE_SQUAD", "IR", "PUP", "FREE_AGENT", "WAIVED", "PRACTICE_SQUAD_WAIVERS"];
/** Situações que a franquia escolhe por movimento (dispensar é outra rota). */
export type MovableStatus = "ACTIVE" | "INACTIVE" | "PRACTICE_SQUAD" | "IR";
export const MOVABLE_STATUSES: MovableStatus[] = ["ACTIVE", "INACTIVE", "PRACTICE_SQUAD", "IR"];
export type KickoffSlot = "THU_NIGHT" | "SUN_EARLY" | "SUN_LATE" | "SUN_NIGHT" | "MON_NIGHT";
/** Papéis do depth chart que não são posição. */
export type DepthRole = "KR" | "PR" | "H" | "THIRD_DOWN_BACK";
export const DEPTH_ROLES: DepthRole[] = ["KR", "PR", "H", "THIRD_DOWN_BACK"];
/** Profundidade máxima de uma vaga (`ChartSlot.MAX_DEPTH`). */
export const MAX_DEPTH = 10;
/** Carreiras ativas por conta (`CareerSave.MAX_ACTIVE_SLOTS`). */
export const MAX_ACTIVE_SLOTS = 5;
/** Limites do backend para os nomes (`CreateCareerUseCase`). */
export const CAREER_NAME = { min: 3, max: 40 } as const;
export const COACH_NAME = { min: 2, max: 40 } as const;

/** Campanha: vitórias, derrotas, empates e o texto "V-D" (ou "V-D-E" quando houver empate). */
export interface TeamRecord {
  wins: number;
  losses: number;
  ties: number;
  summary: string;
}

/** `CareerDto`: uma carreira salva. Campos da franquia, do head coach e da temporada ficam nulos sem emprego/temporada. */
export interface CareerSummary {
  id: UUID;
  name: string;
  /** slot 1–5 das ativas; nulo na arquivada */
  slot: number | null;
  status: CareerStatus;
  difficulty: Difficulty;
  seed: number;
  currentWeek: number;
  employmentStatus: string;
  worldId: UUID;
  sourceWorldId: UUID | null;
  sourceWorldName: string | null;
  leagueId: UUID | null;
  leagueName: string | null;
  ruleset: string | null;
  clubId: UUID | null;
  clubName: string | null;
  clubShortName: string | null;
  clubCity: string | null;
  clubPrimaryColor: string | null;
  clubSecondaryColor: string | null;
  coachId: UUID | null;
  coachName: string | null;
  coachNationality: string | null;
  coachStyle: CoachStyle | null;
  seasonId: UUID | null;
  seasonYear: number | null;
  seasonPhase: string | null;
  regularSeasonWeeks: number;
  record: TeamRecord;
  createdAt: ISODate;
  lastPlayedAt: ISODate | null;
  version: number;
}

/** `CareerListDto`: ativas por slot (máx. 5), os slots livres e as arquivadas. */
export interface CareerList {
  maxActiveSlots: number;
  activeCount: number;
  freeSlots: number[];
  careers: CareerSummary[];
}

/** POST /careers. Sem `worldId`, o servidor usa o mundo padrão; sem `slot`, o primeiro livre. */
export interface CreateCareerRequest {
  name: string;
  worldId?: UUID | null;
  leagueId?: UUID | null;
  clubId: UUID;
  coachName?: string | null;
  /** ISO de 3 letras (padrão BRA) */
  coachNationality?: string | null;
  coachStyle?: CoachStyle | null;
  difficulty?: Difficulty | null;
  slot?: number | null;
  seed?: number | null;
}

/** PATCH /careers/{id}: renomear, dificuldade, arquivar ou reativar; `version` opcional (409 se divergir). */
export interface UpdateCareerRequest {
  name?: string;
  difficulty?: Difficulty;
  status?: CareerStatus;
  version?: number;
}

/* ───────────────────────── Mundo (escolha da liga e da franquia) ───────────────────────── */

/** `WorldDto` (só o que a nova carreira usa). */
export interface WorldSummary {
  id: UUID;
  name: string;
  description: string | null;
  kind: string;
  defaultTemplate: boolean;
  leagues: number;
  clubs: number;
  players: number;
}

/** `LeagueDto` (só o que a nova carreira usa). */
export interface LeagueSummary {
  id: UUID;
  worldId: UUID;
  name: string;
  shortName: string;
  ruleset: string;
  teamCount: number;
  conferenceCount: number;
  divisionsPerConference: number;
  salaryCap: number;
  prestige: number;
  structureValid: boolean;
}

/** Franquia escolhível (`ClubDto` resumido). */
export interface ClubOption {
  id: UUID;
  name: string;
  shortName: string;
  city: string;
  primaryColor: string;
  secondaryColor: string;
  prestige: number;
  expectedWins: number | null;
  stadiumName: string | null;
}

export interface DivisionClubs {
  id: UUID | null;
  name: string;
  clubs: ClubOption[];
}

export interface ConferenceClubs {
  id: UUID | null;
  name: string;
  shortName: string;
  divisions: DivisionClubs[];
}

/** Franquias da liga agrupadas por conferência e divisão (estrutura + franquias). */
export interface LeagueClubs {
  leagueId: UUID;
  conferences: ConferenceClubs[];
  /** franquias da liga fora da estrutura (estrutura inválida) */
  unassigned: ClubOption[];
}

/* ───────────────────────── Central e calendário ───────────────────────── */

/** `ClubRefDto`: franquia citada em jogo, classificação e calendário. */
export interface ClubRef {
  id: UUID;
  name: string;
  shortName: string;
  city: string;
  primaryColor: string;
  secondaryColor: string;
  conference: string | null;
  division: string | null;
}

/** `FixtureDto`: jogo da temporada; placar nulo até ser jogado. */
export interface Fixture {
  id: UUID;
  week: number;
  date: ISODate;
  kickoffSlot: KickoffSlot | string;
  status: string;
  homeClubId: UUID;
  awayClubId: UUID;
  homeScore: number | null;
  awayScore: number | null;
  divisional: boolean;
  conferenceGame: boolean;
  userGame: boolean;
}

export interface CoachSummary {
  id: UUID;
  name: string;
  nationality: string;
  style: CoachStyle | null;
  overall: number;
  reputationLeague: number;
  careerGames: number;
  careerWins: number;
  careerTitles: number;
}

export interface NextGame {
  fixture: Fixture;
  home: boolean;
  opponent: ClubRef;
  opponentRecord: TeamRecord;
}

export interface StandingRow {
  rank: number;
  club: ClubRef;
  record: TeamRecord;
  winPct: number;
  pointsFor: number;
  pointsAgainst: number;
  userClub: boolean;
}

export interface PlayerHighlight {
  id: UUID;
  name: string;
  position: Position;
  jerseyNumber: number;
  overall: number;
  tier: CardTier;
}

export interface InjuryEntry {
  playerId: UUID;
  name: string;
  position: Position;
  rosterStatus: RosterStatus;
  weeksOut: number;
}

/** Teto salarial da temporada (centavos de S$). */
export interface CapSummary {
  salaryCap: number;
  capUsed: number;
  deadMoney: number;
  capSpace: number;
  /** dead money das dispensas desta temporada já lançado na seguinte (regra do 1º de junho, Q-24) */
  deadMoneyNextSeason: number;
}

export interface DepthChartAlert {
  version: number;
  valid: boolean;
  errors: number;
  warnings: number;
  codes: string[];
}

/**
 * `DashboardDto` (RF40). `inbox`, `unreadInbox` e `scoresPoints` chegam nulos até os blocos que os criam: a tela mostra "—" ou
 * esconde, nunca zero. `live` é a partida ao vivo em andamento (bloco 6b) ou null. Bloco 6c (D-106): `morale` (resumo da moral do
 * elenco), `analysis` (último relatório do analista, nulo antes do primeiro), `news` (até 3 notícias da última semana encerrada) e
 * `playoffs` (o usuário nos playoffs).
 */
export interface CareerDashboard {
  career: CareerSummary;
  club: ClubRef | null;
  coach: CoachSummary | null;
  currentWeek: number;
  nextGame: NextGame | null;
  byeWeek: boolean;
  record: TeamRecord;
  divisionRank: number | null;
  division: StandingRow[];
  upcomingDivisionGames: Fixture[];
  rosterHighlights: PlayerHighlight[];
  injuries: InjuryEntry[];
  cap: CapSummary | null;
  depthChart: DepthChartAlert | null;
  live: LiveMatchRef | null;
  morale: MoraleSummary | null;
  analysis: AnalysisSummary | null;
  news: NewsItem[];
  playoffs: UserPlayoff | null;
  inbox: unknown[] | null;
  unreadInbox: number | null;
  scoresPoints: number | null;
}

export interface CalendarWeek {
  number: number;
  kind: string;
  startsOn: ISODate;
  status: string;
  userBye: boolean;
  byeClubIds: UUID[];
  fixtures: Fixture[];
}

/** Rodada de playoff planejada (`PlayoffWeekDto`, D-105): semana, tipo, quinta prevista e situação (PENDING até a semana existir). */
export interface PlayoffWeek {
  number: number;
  kind: string;
  startsOn: ISODate;
  status: string;
}

/**
 * `CalendarDto` (RF21): franquias uma vez só e as semanas com jogos e folgas. As semanas de playoff entram em `weeks` quando a rodada
 * é criada (com `kind` PRELIMINARY…FINAL); `playoffWeeks` lista as rodadas planejadas pelas regras.
 */
export interface SeasonCalendar {
  seasonId: UUID;
  seasonYear: number;
  phase: string;
  currentWeek: number;
  regularSeasonWeeks: number;
  gamesPerTeam: number;
  userClubId: UUID;
  clubs: ClubRef[];
  weeks: CalendarWeek[];
  playoffWeeks: PlayoffWeek[];
}

/* ───────────────────────── Elenco ───────────────────────── */

export interface RosterRules {
  rosterMax: number;
  gameDayActives: number;
  practiceSquadMax: number;
  irMinWeeks: number;
  irReturnsPerSeason: number;
}

export interface RosterCounts {
  roster: number;
  active: number;
  inactive: number;
  practiceSquad: number;
  injuredReserve: number;
  irReturnsUsed: number;
  /** expostos a waivers rumo ao practice squad (fora dos 53 e do practice squad, com a vaga reservada) */
  waivers: number;
}

/** `PlayerCardDto`: card compacto com overall na posição natural, nível, macros da família e vagas no depth chart. */
export interface PlayerCard {
  id: UUID;
  name: string;
  jerseyNumber: number;
  position: Position;
  secondaryPosition: Position | null;
  unit: Unit;
  age: number;
  heightCm: number;
  weightKg: number;
  overall: number;
  tier: CardTier;
  family: string;
  /** macros na ordem do card (ex.: PRE BRA LEI MOB CAL LID) */
  macros: Record<string, number>;
  rosterStatus: RosterStatus;
  condition: number;
  morale: number;
  experienceYears: number;
  injuryWeeksOut: number;
  suspensionWeeks: number;
  capHit: number;
  contractEndSeason: number | null;
  contractType: string | null;
  /** vagas ("QB1", "KR2") */
  depthChart: string[];
}

export interface Roster {
  clubId: UUID;
  clubName: string;
  seasonYear: number;
  currentWeek: number;
  rules: RosterRules;
  counts: RosterCounts;
  cap: CapSummary;
  units: Record<Unit, PlayerCard[]>;
  practiceSquad: PlayerCard[];
  injuredReserve: PlayerCard[];
  /** expostos a waivers rumo ao practice squad (Q-23) */
  waivers: PlayerCard[];
}

/** `ContractDto` do editor (valores por temporada, a partir de `startSeason`). */
export interface PlayerContract {
  startSeason: number;
  endSeason: number;
  years: number;
  baseSalaryByYear: number[];
  signingBonus: number;
  guaranteed: number;
  capHitByYear: number[];
  deadMoneyIfCutByYear: number[];
  type: string;
  franchiseTag: boolean;
  totalValue: number;
}

export interface RosterMove {
  id: UUID;
  week: number;
  kind: string;
  fromStatus: string | null;
  toStatus: string | null;
  deadMoney: number;
  at: ISODate;
}

/** `PlayerDetailDto`: card, subatributos agrupados, overall por posição, contrato, dead money se dispensado agora e histórico. */
export interface PlayerDetail {
  card: PlayerCard;
  clubId: UUID | null;
  clubName: string | null;
  ownPlayer: boolean;
  nationality: string | null;
  college: string | null;
  birthDate: string | null;
  potential: number;
  attributes: Record<string, Record<string, number>>;
  overallByPosition: Partial<Record<Position, number>>;
  contract: PlayerContract | null;
  /** dead money na temporada atual se dispensado agora */
  deadMoneyIfReleased: number;
  /** o que a regra do 1º de junho jogaria para a temporada seguinte */
  deadMoneyNextSeasonIfReleased: number;
  irPlacedWeek: number | null;
  irEarliestReturnWeek: number | null;
  history: RosterMove[];
}

export interface RosterChange {
  player: PlayerCard;
  counts: RosterCounts;
  cap: CapSummary;
}

export interface ReleaseResult {
  playerId: UUID;
  name: string;
  rosterStatus: string;
  deadMoney: number;
  deadMoneyNextSeason: number;
  capHitRemoved: number;
  counts: RosterCounts;
  cap: CapSummary;
}

/* ───────────────────────── Depth chart ───────────────────────── */

export interface DepthEntry {
  depth: number;
  playerId: UUID;
  name: string;
  jerseyNumber: number;
  position: Position;
  overallAtSlot: number;
  overall: number;
  tier: CardTier;
  outOfPosition: boolean;
  available: boolean;
  rosterStatus: RosterStatus;
}

/** Vaga: posição (nula nos papéis KR, PR, H e THIRD_DOWN_BACK), mínimo para estar completo e jogadores por profundidade. */
export interface DepthSlot {
  slot: string;
  position: Position | null;
  role: boolean;
  minStarters: number;
  players: DepthEntry[];
}

export interface DepthUnit {
  unit: Unit;
  slots: DepthSlot[];
}

/** Problema: `DEPTH_CHART_*` nos erros, `OUT_OF_POSITION` nos avisos. */
export interface DepthIssue {
  code: string;
  slot: string | null;
  depth: number | null;
  playerId: UUID | null;
}

export interface DepthChart {
  id: UUID;
  clubId: UUID;
  version: number;
  valid: boolean;
  updatedAt: ISODate | null;
  gameDayActives: number;
  uniquePlayers: number;
  units: DepthUnit[];
  errors: DepthIssue[];
  warnings: DepthIssue[];
}

/** PUT /depth-chart: troca o depth chart inteiro (vagas ausentes ficam vazias). */
export interface SaveDepthChartRequest {
  version: number;
  slots: { slot: string; playerIds: UUID[] }[];
}

/** PATCH /players/{pid}/role. */
export interface SetRoleRequest {
  role: DepthRole;
  depth?: number;
  version?: number;
}
