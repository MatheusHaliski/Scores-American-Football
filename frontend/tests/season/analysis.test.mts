// Resumo do analista (D-104) montado no idioma da tela por `summaryCode` + `summaryParams` (unidades traduzidas, adversário tirado dos
// próximos jogos), com o resumo do servidor como reserva.
import { test } from "node:test";
import assert from "node:assert/strict";
import { analysisFromDto } from "@/lib/api/live-season";
import { analysisSummaryText, unitLabel } from "@/lib/season/analysis";
import { loadGolden } from "../golden.mts";

const a = analysisFromDto(loadGolden("analysis.json"));

test("pt-BR reproduz o resumo do servidor; en-US e es-ES saem do catálogo com o nome do adversário e a chance", () => {
  assert.equal(analysisSummaryText(a, "pt-BR"), a.summary);
  const en = analysisSummaryText(a, "en-US");
  assert.match(en, /^Favorite against Centauros de Cerro Ventania \(62% chance\): lean on offensive line and protect quarterback\.$/);
  assert.match(analysisSummaryText(a, "es-ES"), /Favorito contra Centauros de Cerro Ventania \(62\s?% de probabilidad\): apuesta por línea ofensiva/);
});

test("sem próximo jogo: SEASON_OVERVIEW só com as unidades", () => {
  const over = { ...a, summaryCode: "SEASON_OVERVIEW", summaryParams: { bestUnit: "SECONDARY", worstUnit: "QB" }, upcoming: [] };
  assert.equal(analysisSummaryText(over, "pt-BR"), "Sem próximo jogo na temporada: ponto forte em secundária, ponto fraco em quarterback.");
});

test("reserva: código novo, unidade ausente ou adversário fora dos próximos jogos usam o resumo do servidor", () => {
  assert.equal(analysisSummaryText({ ...a, summaryCode: "NEW_CODE" }, "en-US"), a.summary);
  assert.equal(analysisSummaryText({ ...a, summaryParams: { ...a.summaryParams, bestUnit: 3 } }, "en-US"), a.summary);
  assert.equal(analysisSummaryText({ ...a, upcoming: [] }, "en-US"), a.summary);
  assert.equal(unitLabel("NEW_UNIT", "pt-BR"), "NEW_UNIT");
  assert.equal(unitLabel("RECEIVERS", "es-ES"), "receptores");
});
