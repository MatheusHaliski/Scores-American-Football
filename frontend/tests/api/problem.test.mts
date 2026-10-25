import { test } from "node:test";
import assert from "node:assert/strict";
import { problemFromBody } from "@/lib/api/problem";
import { loadGolden } from "../golden.mts";

test("lê o corpo de erro real do backend (golden do ApiGoldenTest)", () => {
  assert.deepEqual(problemFromBody(401, loadGolden("problem-401.json")), {
    status: 401,
    code: "UNAUTHENTICATED",
    message: "Faça login para continuar",
    details: {},
    correlationId: "golden-401",
    locale: "pt-BR",
  });
});

test("o golden traz os campos do contrato de erro (D-5)", () => {
  const body = loadGolden("problem-401.json") as Record<string, unknown>;
  for (const k of ["code", "message", "details", "correlationId", "locale"]) assert.ok(k in body, `falta ${k}`);
});

test("corpo fora do formato nunca lança: vira UNKNOWN", () => {
  assert.equal(problemFromBody(502, "<html>bad gateway</html>").code, "UNKNOWN");
  assert.equal(problemFromBody(500, null).correlationId, null);
});
