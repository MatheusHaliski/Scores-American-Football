import { test } from "node:test";
import assert from "node:assert/strict";
import { MOCK_META, MetaContractError, fetchMeta, metaFromDto } from "@/lib/api/meta";
import { ApiError } from "@/lib/api/problem";
import { loadGolden } from "../golden.mts";

const golden = loadGolden("meta.json") as Record<string, unknown>;

test("o adaptador lê a resposta real do backend (golden do ApiGoldenTest)", () => {
  assert.deepEqual(metaFromDto(golden), { name: "Scores: American Football", version: "0.0.0-golden", ruleset: "PRO", apiVersion: "v1" });
});

test("o golden tem exatamente os campos que o adaptador conhece", () => {
  assert.deepEqual(Object.keys(golden).sort(), ["apiVersion", "defaultRuleset", "name", "sport", "version"]);
});

test("recusa a API de outro jogo, outra versão ou regras desconhecidas", () => {
  assert.throws(() => metaFromDto({ ...golden, sport: "BASKETBALL" }), MetaContractError);
  assert.throws(() => metaFromDto({ ...golden, apiVersion: "v2" }), MetaContractError);
  assert.throws(() => metaFromDto({ ...golden, defaultRuleset: "ARENA" }), MetaContractError);
  assert.throws(() => metaFromDto(null), MetaContractError);
});

test("modo mock não chama a rede", async () => {
  const meta = await fetchMeta({ mode: "mock", fetchImpl: () => assert.fail("chamou a rede") });
  assert.deepEqual(meta, MOCK_META);
});

test("modo live chama /meta com o idioma e devolve o meta", async () => {
  let seen: { url: string; lang: string | undefined } | null = null;
  const fetchImpl = (async (url: string, init?: RequestInit) => {
    seen = { url, lang: (init?.headers as Record<string, string>)["Accept-Language"] };
    return new Response(JSON.stringify(golden), { status: 200 });
  }) as unknown as typeof fetch;
  const meta = await fetchMeta({ mode: "live", baseUrl: "http://api.test/api/v1", fetchImpl, locale: "en-US" });
  assert.equal(meta.ruleset, "PRO");
  assert.deepEqual(seen, { url: "http://api.test/api/v1/meta", lang: "en-US" });
});

test("resposta de erro vira ApiError com o código do backend; sem rede vira NETWORK", async () => {
  const problem = loadGolden("problem-401.json");
  const fail = (async () => new Response(JSON.stringify(problem), { status: 401 })) as unknown as typeof fetch;
  await assert.rejects(fetchMeta({ mode: "live", fetchImpl: fail }), (e: unknown) => e instanceof ApiError && e.code === "UNAUTHENTICATED");
  const down = (async () => {
    throw new TypeError("fetch failed");
  }) as unknown as typeof fetch;
  await assert.rejects(fetchMeta({ mode: "live", fetchImpl: down }), (e: unknown) => e instanceof ApiError && e.code === "NETWORK");
});
