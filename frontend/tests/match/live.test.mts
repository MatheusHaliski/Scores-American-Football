// Estado ao vivo no front (RF27/RF62): mescla incremental do play-by-play sem duplicar, contagem regressiva até
// decisionDeadlineAt, quando dá para avançar, linhas de scrimmage e de primeira descida nos dois sentidos de ataque (geometria do
// campo), drive atual e a reação da tela a cada código de erro das rotas de partida. Usa os goldens match-state e match-events.
import { test } from "node:test";
import assert from "node:assert/strict";
import { matchEventsFromDto, matchStateFromDto } from "@/lib/api/live-match";
import { ApiError, problemFromBody } from "@/lib/api/problem";
import { describeError } from "@/lib/api/errors";
import { setRuntimeLocale } from "@/i18n/runtime";
import type { MatchState, PlayEvent } from "@/lib/api/match-types";
import { attacksUp, canAdvance, clockText, countdownTone, currentDrive, decisionOpen, isBreak, lastSeqOf, mergeEvents, reactionTo, scrimmageMarkers, secondsLeft, spotFor } from "@/lib/match/live";
import { loadGolden } from "../golden.mts";

const state = matchStateFromDto(loadGolden("match-state.json"));
const page = matchEventsFromDto(loadGolden("match-events.json"));
const deadline = Date.parse(state.decisionDeadlineAt!);

test("mescla do play-by-play: ordena por seq, ignora repetidos, mantém o array quando nada muda e corta no máximo", () => {
  const evs = page.events;
  const half = Math.floor(evs.length / 2);
  const first = mergeEvents([], evs.slice(0, half + 2));
  // a segunda página chega sobreposta (dois lances repetidos) e fora de ordem
  const all = mergeEvents(first, [...evs.slice(half)].reverse());
  assert.deepEqual(all.map((e) => e.seq), evs.map((e) => e.seq));
  assert.equal(new Set(all.map((e) => e.seq)).size, all.length, "sem duplicar");
  assert.equal(mergeEvents(all, evs.slice(0, 3)), all, "nada novo: mesmo array (sem render à toa)");
  assert.equal(mergeEvents(all, []), all);
  assert.equal(lastSeqOf(all), evs[evs.length - 1].seq, "cursor do próximo /events?after=");
  assert.equal(lastSeqOf([]), 0);
  const capped = mergeEvents([], evs, 5);
  assert.deepEqual(capped.map((e) => e.seq), evs.slice(-5).map((e) => e.seq), "guarda os mais novos");
  // os 20 últimos de live.lastEvents e a página de /events juntos também não duplicam
  const withLive = mergeEvents(all, state.live.lastEvents);
  assert.equal(new Set(withLive.map((e) => e.seq)).size, withLive.length);
});

test("contagem regressiva: segundos inteiros até o prazo, 0 quando vence, null sem prazo; tom por faixa", () => {
  assert.equal(secondsLeft(state.decisionDeadlineAt, deadline - 30_000), 30);
  assert.equal(secondsLeft(state.decisionDeadlineAt, deadline - 29_100), 30, "arredonda para cima");
  assert.equal(secondsLeft(state.decisionDeadlineAt, deadline - 1), 1);
  assert.equal(secondsLeft(state.decisionDeadlineAt, deadline), 0);
  assert.equal(secondsLeft(state.decisionDeadlineAt, deadline + 5_000), 0, "nunca negativo");
  assert.equal(secondsLeft(null, deadline), null);
  assert.equal(secondsLeft("não é data", deadline), null);
  assert.deepEqual([30, 11, 10, 6, 5, 0].map(countdownTone), ["ok", "ok", "warn", "warn", "urgent", "urgent"]);
});

test("decisão aberta no prazo bloqueia o avanço; vencida, o avanço volta (a IA chama, D-61); fim de jogo não avança", () => {
  assert.equal(decisionOpen(state, deadline - 10_000), true);
  assert.equal(canAdvance(state, deadline - 10_000), false);
  assert.equal(decisionOpen(state, deadline + 1), false);
  assert.equal(canAdvance(state, deadline + 1), true);
  const live: MatchState = { ...state, status: "LIVE", advanceModes: ["PLAY", "DRIVE", "QUARTER", "TO_DECISION", "FULL"], decisionDeadlineAt: null, live: { ...state.live, decision: null } };
  assert.equal(canAdvance(live, deadline), true);
  assert.equal(canAdvance({ ...live, status: "FINISHED", advanceModes: [] }, deadline), false);
  assert.deepEqual((["NOT_STARTED", "LIVE", "BREAK", "HALFTIME", "AWAITING_DECISION", "FINISHED"] as const).filter(isBreak), ["NOT_STARTED", "BREAK", "HALFTIME"]);
});

test("campo: a casa ataca para a jarda 100 e o visitante para a 0; scrimmage e primeira descida nos dois sentidos", () => {
  // golden: casa (VAU) com a bola na jarda 35, 4ª e 3 → primeira descida na 38
  assert.equal(attacksUp(state.live), true);
  assert.deepEqual(scrimmageMarkers(state.live.ballOnAbsYd, state.live.distance, true), { scrimmageAbsYd: 35, firstDownAbsYd: 38, goalToGo: false, direction: 1 });
  // visitante na jarda 30 (a 30 do gol da casa), 1ª e 10 → primeira descida na 20
  assert.deepEqual(scrimmageMarkers(30, 10, false), { scrimmageAbsYd: 30, firstDownAbsYd: 20, goalToGo: false, direction: -1 });
  // "and goal": a primeira descida passaria da linha de gol
  assert.deepEqual(scrimmageMarkers(96, 10, true), { scrimmageAbsYd: 96, firstDownAbsYd: 100, goalToGo: true, direction: 1 });
  assert.deepEqual(scrimmageMarkers(4, 4, false), { scrimmageAbsYd: 4, firstDownAbsYd: 0, goalToGo: true, direction: -1 });
  // posição de transmissão do ponto de vista de quem ataca
  assert.deepEqual(spotFor(35, true), { side: "OWN", yard: 35 });
  assert.deepEqual(spotFor(35, false), { side: "OPP", yard: 35 });
  assert.deepEqual(spotFor(80, false), { side: "OWN", yard: 20 });
  assert.deepEqual(spotFor(50, false), { side: "MID", yard: 50 });
  assert.equal(attacksUp({ ...state.live, possessionClubId: null }), null);
  assert.equal(clockText(226), "3:46");
  assert.equal(clockText(0), "0:00");
  assert.equal(clockText(900), "15:00");
});

test("drive atual: jogadas de scrimmage e jardas do drive em andamento do time com a bola; drive encerrado não conta", () => {
  const club = state.live.possessionClubId!;
  const ev = (seq: number, type: string, yards: number, extra: Partial<PlayEvent> = {}): PlayEvent => ({ ...state.live.lastEvents[0], seq, drive: 7, possessionClubId: club, type, yards, ballOnAbsYd: 20 + seq, driveEnded: null, ...extra });
  const events = [ev(1, "KICKOFF", 0), ev(2, "RUN", 4), ev(3, "PASS", 12), ev(4, "PASS", 0), ev(5, "TIMEOUT", 0)];
  assert.deepEqual(currentDrive(state.live, events), { number: 7, clubId: club, plays: 3, yards: 16, startAbsYd: 22 });
  assert.equal(currentDrive(state.live, [...events, ev(6, "PUNT", 40, { driveEnded: "PUNT" })]), null);
  assert.equal(currentDrive({ ...state.live, status: "FINISHED" }, events), null);
  assert.equal(currentDrive(state.live, []), null);
});

test("erro → reação: chamada atrasada avisa que a IA chamou e recarrega; versão velha recarrega; ajuste fora de hora mostra o motivo", () => {
  const err = (status: number, code: string) => new ApiError(problemFromBody(status, { code, message: `servidor: ${code}` }));
  assert.deepEqual(reactionTo(err(409, "DECISION_CLOSED")), { reload: true, notice: "aiCalled", showError: false });
  assert.deepEqual(reactionTo(err(409, "MATCH_VERSION_CONFLICT")), { reload: true, notice: "stale", showError: false });
  // escrita concorrente que o servidor recusou (trava ou chave única, D-91): a tela relê o estado, como na versão velha
  assert.deepEqual(reactionTo(err(409, "CONCURRENT_UPDATE")), { reload: true, notice: "stale", showError: false });
  assert.deepEqual(reactionTo(err(409, "DECISION_PENDING")), { reload: true, notice: "decisionOpen", showError: false });
  assert.deepEqual(reactionTo(err(409, "MATCH_FINISHED")), { reload: true, notice: "finished", showError: false });
  assert.deepEqual(reactionTo(err(404, "DECISION_NOT_FOUND")), { reload: true, notice: "stale", showError: false });
  assert.deepEqual(reactionTo(err(422, "PLAY_NOT_OFFERED")), { reload: true, notice: "notOffered", showError: true });
  assert.deepEqual(reactionTo(err(422, "ADJUSTMENT_NOT_ALLOWED")), { reload: true, notice: "notAdjustable", showError: true });
  assert.deepEqual(reactionTo(err(422, "TIMEOUT_NOT_ALLOWED")), { reload: true, notice: "noTimeout", showError: true });
  assert.deepEqual(reactionTo(err(500, "INTERNAL")), { reload: false, notice: null, showError: true });
  assert.deepEqual(reactionTo(new Error("rede")), { reload: false, notice: null, showError: true });
  // a mensagem: sem argumentos posicionais no corpo, vale a que o servidor traduziu; sem nenhuma, o catálogo
  setRuntimeLocale("pt-BR");
  assert.equal(describeError(err(422, "ADJUSTMENT_NOT_ALLOWED")), "servidor: ADJUSTMENT_NOT_ALLOWED");
  assert.equal(describeError(ApiError.of(409, "MATCH_FINISHED")), "A partida já terminou.");
  setRuntimeLocale("en-US");
  assert.equal(describeError(ApiError.of(422, "MATCH_NOT_FINISHED")), "The report is available when the game ends.");
  setRuntimeLocale("pt-BR");
});
