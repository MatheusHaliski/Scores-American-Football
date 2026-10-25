// Erro → reação da tela da temporada e do analista: 422 SEASON_NOT_FINISHED e 409 SEASON_ALREADY_ADVANCED recarregam e explicam
// (com a fase/semana e a temporada atual tirados de `details`), 429 vira espera com os segundos do servidor, 404 ANALYSIS_NOT_FOUND
// vira o estado vazio do analista e o resto é erro comum.
import { test } from "node:test";
import assert from "node:assert/strict";
import { ApiError, problemFromBody } from "@/lib/api/problem";
import { DEFAULT_RETRY_SECONDS, seasonReaction } from "@/lib/season/errors";
import { loadGolden } from "../golden.mts";

test("422 SEASON_NOT_FINISHED do golden: recarrega e mostra a fase e a semana", () => {
  const r = seasonReaction(new ApiError(problemFromBody(422, loadGolden("problem-422-season-not-finished.json"))));
  assert.deepEqual(r, { kind: "notFinished", reload: true, phase: "PLAYOFFS", week: 3 });
});

test("409 SEASON_ALREADY_ADVANCED do golden: recarrega e diz qual temporada já começou", () => {
  const r = seasonReaction(new ApiError(problemFromBody(409, loadGolden("problem-409-season-advance.json"))));
  assert.deepEqual(r, { kind: "alreadyAdvanced", reload: true, seasonNumber: 2 });
});

test("429 RATE_LIMITED: espera os segundos do servidor (arredondados para cima, mínimo 1) ou o padrão", () => {
  const golden = new ApiError(problemFromBody(429, loadGolden("problem-429.json")));
  assert.deepEqual(seasonReaction(golden), { kind: "rateLimited", reload: false, retryAfterSeconds: 3600 });
  assert.deepEqual(seasonReaction(new ApiError({ status: 429, code: "RATE_LIMITED", message: "", details: { retryAfterSeconds: 12.2 }, correlationId: null, locale: null })), { kind: "rateLimited", reload: false, retryAfterSeconds: 13 });
  assert.deepEqual(seasonReaction(new ApiError({ status: 429, code: "UNKNOWN", message: "", details: {}, correlationId: null, locale: null })), { kind: "rateLimited", reload: false, retryAfterSeconds: DEFAULT_RETRY_SECONDS });
});

test("404 ANALYSIS_NOT_FOUND vira o estado vazio; o resto (e o que não é da API) é erro comum, sem recarga", () => {
  assert.deepEqual(seasonReaction(ApiError.of(404, "ANALYSIS_NOT_FOUND")), { kind: "noAnalysis", reload: false });
  assert.deepEqual(seasonReaction(ApiError.of(409, "NO_NEXT_GAME")), { kind: "error", reload: false });
  assert.deepEqual(seasonReaction(new Error("x")), { kind: "error", reload: false });
  // detalhes ausentes não quebram: a tela mostra "—"
  assert.deepEqual(seasonReaction(ApiError.of(422, "SEASON_NOT_FINISHED")), { kind: "notFinished", reload: true, phase: null, week: null });
});
