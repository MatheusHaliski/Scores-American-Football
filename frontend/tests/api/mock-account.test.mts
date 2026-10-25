// Modo mock (sem servidor): as mesmas regras de conta do backend — privacidade por padrão, idade mínima, exclusão com
// dupla confirmação e prazo de arrependimento. Em Node não há localStorage: o mock fica só em memória.
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { createMockApi } from "@/lib/api/mock";
import { ApiError } from "@/lib/api/problem";
import { describeError } from "@/lib/api/errors";

const api = createMockApi();
const email = `mock-${randomUUID().slice(0, 8)}@scores.test`;

test("cadastro valida como o backend e entra na hora", async () => {
  await assert.rejects(api.auth.register({ email: "sem-arroba", password: randomUUID(), displayName: "Ana" }), (e: unknown) => e instanceof ApiError && e.code === "VALIDATION" && !!e.fields.email);
  await assert.rejects(api.auth.register({ email, password: randomUUID().slice(0, 5), displayName: "Ana" }), (e: unknown) => e instanceof ApiError && !!e.fields.password);
  const res = await api.auth.register({ email, password: randomUUID(), displayName: "Ana Mock" });
  assert.equal(res.user.email, email);
  assert.equal((await api.auth.me()).displayName, "Ana Mock");
});

test("privacidade por padrão: nenhum consentimento ligado e perfil 'só eu'", async () => {
  const p = await api.account.privacy();
  assert.equal(p.profileVisibility, "ONLY_ME");
  assert.ok(p.consents.length > 0);
  assert.ok(p.consents.every((c) => !c.granted));
  const after = await api.account.updatePrivacy({ consents: { ANALYTICS: true }, source: "SETTINGS" });
  assert.equal(after.consents.find((c) => c.purpose === "ANALYTICS")?.granted, true);
  assert.equal((await api.account.consentHistory())[0].purpose, "ANALYTICS");
});

test("perfil: idade mínima de 13 anos com mensagem traduzida", async () => {
  const recent = new Date(Date.now() - 5 * 365 * 86_400_000).toISOString().slice(0, 10);
  await assert.rejects(api.account.updateProfile({ birthDate: recent }), (e: unknown) => e instanceof ApiError && e.code === "MIN_AGE" && /13/.test(describeError(e)));
});

test("configurações: mudar o idioma volta para o resumo da sessão", async () => {
  const s = await api.auth.updateSettings({ preferredLocale: "es-ES", theme: "LIGHT" });
  assert.equal(s.theme, "LIGHT");
  assert.equal((await api.auth.me()).locale, "es-ES");
});

test("exclusão: palavra de confirmação errada é recusada; certa agenda com 7 dias e dá para cancelar", async () => {
  await assert.rejects(api.account.deleteAccount({ currentPassword: randomUUID(), confirmation: "talvez" }), (e: unknown) => e instanceof ApiError && e.code === "DELETION_CONFIRMATION_MISMATCH");
  const st = await api.account.deleteAccount({ currentPassword: randomUUID(), confirmation: "eliminar" });
  assert.equal(st.status, "PENDING_DELETION");
  const days = (Date.parse(st.scheduledFor!) - Date.parse(st.requestedAt!)) / 86_400_000;
  assert.equal(days, 7);
  assert.equal((await api.account.cancelDeletion()).status, "ACTIVE");
});

test("recuperação de senha: resposta uniforme e redefinição exige 8+ caracteres", async () => {
  await api.auth.forgotPassword({ email: "qualquer@scores.test" });
  await assert.rejects(api.auth.resetPassword({ token: randomUUID(), newPassword: randomUUID().slice(0, 5) }), (e: unknown) => e instanceof ApiError && e.code === "WEAK_PASSWORD");
  await assert.rejects(api.auth.resetPassword({ token: " ", newPassword: randomUUID() }), (e: unknown) => e instanceof ApiError && e.code === "INVALID_TOKEN");
});

test("verificação de e-mail: token vazio é link inválido", async () => {
  await api.auth.verifyEmail(randomUUID());
  await assert.rejects(api.auth.verifyEmail(" "), (e: unknown) => e instanceof ApiError && e.code === "INVALID_TOKEN");
});

test("depois de sair, as rotas da conta respondem 401", async () => {
  await api.auth.logout();
  await assert.rejects(api.auth.me(), (e: unknown) => e instanceof ApiError && e.status === 401);
});
