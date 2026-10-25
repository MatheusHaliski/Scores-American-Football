// Adaptadores de conta contra as respostas reais do backend (goldens do ApiGoldenTest, D-5): auth-login, me, me-settings e
// problem-429. A API omite campos nulos (spring.jackson.default-property-inclusion=non_null): o golden pode não trazer um campo
// anulável, mas nunca traz campo que o front não conhece, e os campos obrigatórios estão sempre lá.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  ACCOUNT_FIELDS,
  SETTINGS_FIELDS,
  TOKEN_RESPONSE_FIELDS,
  USER_SUMMARY_FIELDS,
  authFromDto,
  profileFromDto,
  settingsFromDto,
  userFromDto,
  type AccountDto,
  type SettingsDto,
  type TokenResponseDto,
} from "@/lib/api/live-account";
import { ApiError, problemFromBody } from "@/lib/api/problem";
import { describeError } from "@/lib/api/errors";
import { setRuntimeLocale } from "@/i18n/runtime";
import { MESSAGES } from "@/i18n/messages";
import { loadAccountGolden } from "../golden.mts";

const login = loadAccountGolden("auth-login.json") as TokenResponseDto;
const me = loadAccountGolden("me.json") as AccountDto;
const settings = loadAccountGolden("me-settings.json") as SettingsDto;
const tooMany = loadAccountGolden("problem-429.json") as Record<string, unknown>;

/** Todo campo do golden é conhecido do front e todo campo obrigatório (nunca nulo) está presente. */
function assertFields(o: object, known: readonly string[], nullable: readonly string[], what: string) {
  const present = Object.keys(o);
  assert.deepEqual(present.filter((k) => !known.includes(k)), [], `${what}: campo que o front não conhece`);
  assert.deepEqual(known.filter((k) => !nullable.includes(k) && !present.includes(k)), [], `${what}: campo obrigatório ausente`);
}

test("auth-login: o golden só tem campos conhecidos e traz os obrigatórios de TokenResponse/UserSummary que o front lê", () => {
  assertFields(login, TOKEN_RESPONSE_FIELDS, ["refreshToken"], "auth-login");
  assertFields(login.user, USER_SUMMARY_FIELDS, [], "auth-login.user");
});

test("auth-login: no navegador o refresh nunca vem no corpo (fica no cookie HttpOnly)", () => {
  assert.ok(login.refreshToken == null, "refreshToken ausente ou nulo");
  const auth = authFromDto(login);
  assert.equal(auth.tokens.refreshToken, null);
});

test("auth-login: vira sessão com o access token, a expiração em epoch ms e o resumo do usuário", () => {
  const auth = authFromDto(login);
  assert.equal(auth.tokens.accessToken, login.accessToken);
  assert.equal(auth.tokens.accessExpiresAt, Date.parse(login.accessExpiresAt));
  assert.ok(Number.isFinite(auth.tokens.accessExpiresAt));
  assert.equal(auth.user.id, login.user.id);
  assert.equal(auth.user.displayName, login.user.displayName);
  assert.deepEqual(auth.user.roles, login.user.roles);
  assert.equal(auth.user.role, login.user.roles.includes("ADMIN") ? "ADMIN" : "PLAYER");
});

test("me: o golden só tem campos conhecidos e traz os obrigatórios de AccountDto", () => {
  assertFields(me, ACCOUNT_FIELDS, ["avatarUrl", "birthDate", "pendingEmail", "deletionScheduledFor", "dataProcessingConsentAt", "lastLoginAt"], "me");
});

test("me: perfil e resumo da sessão saem do AccountDto sem perder dado", () => {
  const p = profileFromDto(me);
  assert.equal(p.id, me.id);
  assert.equal(p.email, me.email);
  assert.equal(p.displayName, me.displayName);
  assert.match(p.locale, /^(pt-BR|en-US|es-ES)$/);
  assert.equal(p.status, me.status);
  assert.equal(p.profileVisibility, me.profileVisibility);
  assert.equal(p.createdAt, me.createdAt);
  assert.equal(p.marketingConsent, me.marketingConsent);
  const u = userFromDto(me);
  assert.equal(u.email, me.email);
  assert.deepEqual(u.roles, [me.role]);
});

test("me: visibilidade desconhecida conta como 'só eu' (privacidade por padrão)", () => {
  assert.equal(profileFromDto({ ...me, profileVisibility: "EVERYONE" }).profileVisibility, "ONLY_ME");
  assert.equal(profileFromDto({ ...me, locale: "PT_BR" }).locale, "pt-BR");
});

test("me-settings: o golden só tem campos conhecidos e traz os obrigatórios de SettingsDto", () => {
  assertFields(settings, SETTINGS_FIELDS, ["backgroundArt", "schemeArt", "soundtrack", "fontFamily", "regionalFormat"], "me-settings");
  assert.ok(!("matchSpeed" in settings), "matchSpeed é do basquete (D-13)");
});

test("me-settings: enums e idioma do golden são aceitos como estão", () => {
  const s = settingsFromDto(settings);
  for (const k of ["quarterDurationMinutes", "playCallingLevel", "difficulty", "theme", "fontScale", "density", "simulationSpeed", "reducedMotion", "highContrast", "notificationsEnabled"] as const) {
    assert.equal(s[k], settings[k], `campo ${k}`);
  }
  assert.equal(s.preferredLocale, settings.preferredLocale);
  assert.equal(s.regionalFormat, settings.regionalFormat || settings.preferredLocale);
  for (const k of ["backgroundArt", "schemeArt", "soundtrack", "fontFamily"] as const) assert.equal(typeof s[k], "string", `${k} nulo vira texto vazio`);
});

test("me-settings: valor fora do enum ou da faixa cai no padrão em vez de quebrar a tela", () => {
  const s = settingsFromDto({ ...settings, theme: "NEON", playCallingLevel: "SOME", quarterDurationMinutes: 99, preferredLocale: "EN" });
  assert.equal(s.theme, "SYSTEM");
  assert.equal(s.playCallingLevel, "KEY_MOMENTS");
  assert.equal(s.quarterDurationMinutes, 3);
  assert.equal(s.preferredLocale, "en-US");
});

test("me-settings: futebol americano (D-10) troca o matchSpeed do basquete por minutos por quarto e chamada de jogadas", () => {
  assert.ok(!("matchSpeed" in settings));
  assert.equal(typeof settings.quarterDurationMinutes, "number");
  assert.ok(["KEY_MOMENTS", "ALL_PLAYS", "NONE"].includes(String(settings.playCallingLevel)));
});

test("problem-429: o corpo segue o contrato de erro e traz quando tentar de novo", () => {
  for (const k of ["code", "message", "details", "correlationId", "locale"]) assert.ok(k in tooMany, `falta ${k}`);
  const err = new ApiError(problemFromBody(429, tooMany));
  assert.equal(err.status, 429);
  assert.ok(["RATE_LIMITED", "TOO_MANY_ATTEMPTS"].includes(err.code), `código ${err.code}`);
  assert.equal(typeof err.retryAfterSeconds, "number");
  assert.ok((err.retryAfterSeconds ?? 0) > 0);
});

test("problem-429: o código tem tradução nos três idiomas e a mensagem sai no idioma da tela", () => {
  const err = new ApiError(problemFromBody(429, tooMany));
  for (const loc of ["pt-BR", "en-US", "es-ES"] as const) {
    assert.ok(err.code in MESSAGES[loc].errors, `${loc} sem errors:${err.code}`);
    setRuntimeLocale(loc);
    assert.equal(describeError(err), (MESSAGES[loc].errors as Record<string, string>)[err.code]);
  }
  setRuntimeLocale("pt-BR");
});
