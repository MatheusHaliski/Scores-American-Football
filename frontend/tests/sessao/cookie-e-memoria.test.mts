// D-114 (herdado do basquete): o refresh token vive num cookie HttpOnly e o access token só em memória.
// Com os dois em localStorage, qualquer XSS levaria a sessão inteira (NIST SP 800-63B-4 §5). Tokens gerados na execução.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import { HttpClient, memoryTokenStorage } from "../../src/lib/api/client.ts";

const raiz = new URL("../../", import.meta.url);
const ler = (p: string) => readFileSync(fileURLToPath(new URL(p, raiz)), "utf8");
/** só o código: um comentário pode (e deve) explicar por que a sessão saiu do localStorage. */
const semComentarios = (fonte: string) => fonte.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");

test("nem o armazenamento de tokens nem a sessão tocam localStorage", () => {
  const api = ler("src/lib/api/index.ts");
  const sessao = ler("src/store/session.ts");
  for (const [nome, fonte] of [["lib/api/index.ts", api], ["store/session.ts", sessao]] as const) {
    assert.ok(!/localStorage|sessionStorage/.test(semComentarios(fonte)), `${nome} não pode guardar sessão no navegador`);
  }
  assert.ok(!/\bpersist\(/.test(semComentarios(sessao)), "a store da sessão não é persistida: ao recarregar, quem responde é o cookie");
  assert.ok(/BroadcastChannel/.test(sessao), "logout numa aba precisa alinhar as outras");
  // sem persist, quem marca `hydrated` é o restore(); sem a chamada inicial o RequireAuth fica no esqueleto para sempre
  assert.ok(/useSession\.getState\(\)\.restore\(\)/.test(semComentarios(sessao)), "a sessão precisa se restaurar ao carregar");
  assert.ok(/finally[\s\S]{0,80}hydrated: true/.test(sessao), "restore() marca hydrated mesmo quando não há sessão");
});

test("o token em memória não sobrevive a outra instância do armazenamento", () => {
  const token = randomUUID();
  memoryTokenStorage.set({ accessToken: token, accessExpiresAt: Date.now() + 60_000 });
  assert.equal(memoryTokenStorage.get()?.accessToken, token);
  memoryTokenStorage.set(null);
  assert.equal(memoryTokenStorage.get(), null);
});

test("o refresh vai pelo cookie, com o cabeçalho de CSRF e sem token no corpo", async () => {
  const novo = randomUUID();
  const pedidos: { url: string; init: RequestInit }[] = [];
  const original = globalThis.fetch;
  globalThis.fetch = (async (url: string | URL | Request, init: RequestInit = {}) => {
    pedidos.push({ url: String(url), init });
    return new Response(JSON.stringify({ accessToken: novo, accessExpiresAt: new Date(Date.now() + 900_000).toISOString() }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;
  try {
    const http = new HttpClient({ baseUrl: "https://api.scoresaf.test/api/v1", tokens: memoryTokenStorage });
    const tokens = await http.refresh();
    assert.equal(tokens?.accessToken, novo);
    assert.equal(tokens?.refreshToken ?? null, null, "o refresh nunca volta para o JavaScript");

    const [pedido] = pedidos;
    assert.match(pedido.url, /\/auth\/refresh$/);
    assert.equal(pedido.init.credentials, "include", "sem credentials o navegador não manda o cookie");
    const headers = pedido.init.headers as Record<string, string>;
    assert.equal(headers["X-Scores-Refresh"], "1", "o servidor só aceita o cookie com este cabeçalho");
    assert.equal(JSON.parse(String(pedido.init.body)).refreshToken, undefined, "nada de refresh token no corpo");
  } finally {
    globalThis.fetch = original;
    memoryTokenStorage.set(null);
  }
});

test("sem token em memória, uma resposta 401 ainda tenta renovar pelo cookie", async () => {
  const chamadas: string[] = [];
  const original = globalThis.fetch;
  globalThis.fetch = (async (url: string | URL | Request) => {
    const u = String(url);
    chamadas.push(u);
    if (u.endsWith("/auth/refresh")) {
      return new Response(JSON.stringify({ accessToken: randomUUID(), accessExpiresAt: new Date(Date.now() + 900_000).toISOString() }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    }
    // a primeira chamada ao recurso vem sem Authorization (aba recarregada): 401; depois do refresh, 200
    return chamadas.filter((c) => c.endsWith("/me")).length > 1
      ? new Response(JSON.stringify({ id: "u1" }), { status: 200, headers: { "content-type": "application/json" } })
      : new Response("", { status: 401 });
  }) as typeof fetch;
  try {
    memoryTokenStorage.set(null);
    const http = new HttpClient({ baseUrl: "https://api.scoresaf.test/api/v1", tokens: memoryTokenStorage });
    const eu = await http.get<{ id: string }>("/me");
    assert.equal(eu.id, "u1");
    assert.ok(chamadas.some((c) => c.endsWith("/auth/refresh")), "o 401 precisa tentar o refresh pelo cookie");
  } finally {
    globalThis.fetch = original;
    memoryTokenStorage.set(null);
  }
});
