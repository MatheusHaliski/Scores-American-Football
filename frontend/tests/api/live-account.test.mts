// Cliente live das rotas de conta: caminhos e corpos iguais aos do backend do basquete (que o futebol americano copia),
// usando os goldens como respostas do servidor. Sem rede: o fetch é substituído no teste.
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { createLiveApi } from "@/lib/api/live";
import { memoryTokenStorage } from "@/lib/api/client";
import { ApiError } from "@/lib/api/problem";
import { loadAccountGolden, loadGolden } from "../golden.mts";

const BASE = "https://api.scoresaf.test/api/v1";
const login = loadAccountGolden("auth-login.json");
const me = loadAccountGolden("me.json");
const settings = loadAccountGolden("me-settings.json");
const tooMany = loadAccountGolden("problem-429.json");

type Call = { method: string; url: string; body: unknown; headers: Record<string, string>; credentials?: RequestCredentials };

function fakeServer(routes: Record<string, (body: unknown) => Response>) {
  const calls: Call[] = [];
  const original = globalThis.fetch;
  globalThis.fetch = (async (url: string | URL | Request, init: RequestInit = {}) => {
    const u = String(url);
    const method = init.method ?? "GET";
    const body = typeof init.body === "string" ? JSON.parse(init.body) : undefined;
    calls.push({ method, url: u, body, headers: (init.headers ?? {}) as Record<string, string>, credentials: init.credentials });
    const route = routes[`${method} ${u.replace(BASE, "").split("?")[0]}`];
    if (!route) return new Response("", { status: 404 });
    return route(body);
  }) as typeof fetch;
  return { calls, restore: () => (globalThis.fetch = original) };
}

const json = (v: unknown, status = 200) => new Response(JSON.stringify(v), { status, headers: { "content-type": "application/json" } });
const problem = (v: unknown, status: number) => new Response(JSON.stringify(v), { status, headers: { "content-type": "application/problem+json" } });

test("login guarda o access token só em memória e devolve o usuário do golden", async () => {
  const srv = fakeServer({ "POST /auth/login": () => json(login) });
  try {
    memoryTokenStorage.set(null);
    const api = createLiveApi(BASE, memoryTokenStorage);
    const password = randomUUID();
    const res = await api.auth.login({ email: "golden@scores.test", password });
    assert.equal(res.user.id, (login as { user: { id: string } }).user.id);
    assert.equal(memoryTokenStorage.get()?.accessToken, (login as { accessToken: string }).accessToken);
    assert.deepEqual(srv.calls[0].body, { email: "golden@scores.test", password });
    assert.equal(srv.calls[0].credentials, "include", "o cookie HttpOnly do refresh precisa ir e voltar");
  } finally {
    srv.restore();
    memoryTokenStorage.set(null);
  }
});

test("cadastro: POST /auth/register (201 { userId }) e entra em seguida pelo login", async () => {
  const srv = fakeServer({
    "POST /auth/register": () => json({ userId: randomUUID() }, 201),
    "POST /auth/login": () => json(login),
  });
  try {
    const api = createLiveApi(BASE, memoryTokenStorage);
    const password = randomUUID();
    const res = await api.auth.register({ email: "novo@scores.test", password, displayName: "Novo", locale: "en-US" });
    assert.deepEqual(
      srv.calls.map((c) => `${c.method} ${c.url.replace(BASE, "")}`),
      ["POST /auth/register", "POST /auth/login"],
    );
    assert.deepEqual(srv.calls[0].body, { marketingConsent: false, email: "novo@scores.test", password, displayName: "Novo", locale: "en-US" });
    assert.equal(res.tokens.refreshToken, null);
  } finally {
    srv.restore();
    memoryTokenStorage.set(null);
  }
});

test("GET /me e /me/settings passam pelos adaptadores; Bearer e idioma vão em toda chamada", async () => {
  const srv = fakeServer({ "GET /me": () => json(me), "GET /me/settings": () => json(settings) });
  try {
    const token = randomUUID();
    memoryTokenStorage.set({ accessToken: token, accessExpiresAt: Date.now() + 600_000 });
    const api = createLiveApi(BASE, memoryTokenStorage);
    const profile = await api.account.profile();
    const s = await api.auth.settings();
    assert.equal(profile.email, (me as { email: string }).email);
    assert.equal(s.theme, (settings as { theme: string }).theme);
    for (const c of srv.calls) {
      assert.equal(c.headers.Authorization, `Bearer ${token}`);
      assert.ok(c.headers["Accept-Language"]);
    }
  } finally {
    srv.restore();
    memoryTokenStorage.set(null);
  }
});

test("rotas de conta/privacidade/dados usam os mesmos caminhos do backend", async () => {
  const ok = () => json({});
  const list = () => json([]);
  const srv = fakeServer({
    "PUT /me": () => json(me),
    "PUT /me/email": () => json({ status: "VERIFICATION_SENT" }, 202),
    "PUT /me/password": () => new Response(null, { status: 204 }),
    "GET /me/sessions": list,
    "GET /me/consents": ok,
    "PUT /me/consents": ok,
    "GET /me/consents/history": list,
    "POST /me/export": ok,
    "GET /me/export/jobs": list,
    "GET /me/activity": () => json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    "DELETE /me": ok,
    "GET /me/deletion": ok,
    "POST /me/deletion/cancel": ok,
    "POST /auth/password/forgot": () => json({ message: "ok" }, 202),
    "POST /auth/password/reset": () => new Response(null, { status: 204 }),
    "POST /auth/email/verify": () => new Response(null, { status: 204 }),
    "POST /auth/logout": () => new Response(null, { status: 204 }),
  });
  try {
    memoryTokenStorage.set({ accessToken: randomUUID(), accessExpiresAt: Date.now() + 600_000 });
    const api = createLiveApi(BASE, memoryTokenStorage);
    const pwd = randomUUID();
    await api.account.updateProfile({ displayName: "Golden" });
    await api.account.changeEmail({ newEmail: "x@scores.test", currentPassword: pwd });
    await api.account.changePassword({ currentPassword: pwd, newPassword: randomUUID() });
    await api.account.sessions();
    await api.account.privacy();
    await api.account.updatePrivacy({ consents: { ANALYTICS: true } });
    await api.account.consentHistory();
    await api.account.requestExport();
    await api.account.exportJobs();
    await api.account.activity(0, 20);
    await api.account.deleteAccount({ currentPassword: pwd, confirmation: "EXCLUIR" });
    await api.account.deletionStatus();
    await api.account.cancelDeletion();
    await api.auth.forgotPassword({ email: "x@scores.test" });
    await api.auth.resetPassword({ token: randomUUID(), newPassword: randomUUID() });
    const emailToken = randomUUID();
    await api.auth.verifyEmail(emailToken);
    await api.auth.logout();
    const paths = srv.calls.map((c) => `${c.method} ${c.url.replace(BASE, "")}`);
    assert.ok(paths.includes("GET /me/activity?page=0&size=20"));
    const verify = srv.calls.find((c) => c.method === "POST" && c.url.endsWith("/auth/email/verify"));
    assert.deepEqual(verify?.body, { token: emailToken }, "o link de troca de e-mail chama a verificação com o token");
    const consents = srv.calls.find((c) => c.method === "PUT" && c.url.endsWith("/me/consents"));
    assert.equal((consents?.body as { source: string }).source, "SETTINGS", "a origem do consentimento vai junto (prova, Art. 8º §2º)");
    assert.equal(memoryTokenStorage.get(), null, "logout limpa a memória");
  } finally {
    srv.restore();
    memoryTokenStorage.set(null);
  }
});

test("429 do backend vira ApiError com o código e o tempo de espera do golden", async () => {
  const srv = fakeServer({ "POST /auth/login": () => problem(tooMany, 429) });
  try {
    const api = createLiveApi(BASE, memoryTokenStorage);
    await assert.rejects(api.auth.login({ email: "a@scores.test", password: randomUUID() }), (e: unknown) => e instanceof ApiError && e.status === 429 && typeof e.retryAfterSeconds === "number");
  } finally {
    srv.restore();
  }
});

test("401 do backend (golden) sem sessão renovável avisa o handler e limpa a memória", async () => {
  let avisos = 0;
  const srv = fakeServer({
    "GET /me": () => problem(loadGolden("problem-401.json"), 401),
    "POST /auth/refresh": () => problem(loadGolden("problem-401.json"), 401),
  });
  try {
    memoryTokenStorage.set({ accessToken: randomUUID(), accessExpiresAt: Date.now() + 600_000 });
    const api = createLiveApi(BASE, memoryTokenStorage, () => avisos++);
    await assert.rejects(api.auth.me(), (e: unknown) => e instanceof ApiError && e.code === "UNAUTHENTICATED");
    assert.ok(avisos >= 1);
    assert.equal(memoryTokenStorage.get(), null);
  } finally {
    srv.restore();
    memoryTokenStorage.set(null);
  }
});

test("sem rede vira ApiError NETWORK (nunca um TypeError solto na tela)", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = (async () => {
    throw new TypeError("fetch failed");
  }) as typeof fetch;
  try {
    const api = createLiveApi(BASE, memoryTokenStorage);
    await assert.rejects(api.auth.login({ email: "a@scores.test", password: randomUUID() }), (e: unknown) => e instanceof ApiError && e.code === "NETWORK");
  } finally {
    globalThis.fetch = original;
  }
});
