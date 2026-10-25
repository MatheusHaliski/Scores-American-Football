// Validação local dos formulários de conta: mesmas regras dos records do AuthController (senha 8–128, nome 2–80).
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { validateAuth, validateReset, type AuthForm } from "@/components/auth/validate";

const t = (k: string) => k;
const pwd = (n: number) => randomBytes(n).toString("base64url").slice(0, n);
const form = (p: Partial<AuthForm> = {}): AuthForm => ({ email: "ana@scores.test", password: pwd(12), displayName: "Ana", marketingConsent: false, ...p });

test("login só exige e-mail válido e alguma senha", () => {
  assert.deepEqual(validateAuth("login", form({ password: pwd(3) }), t), {});
  assert.deepEqual(validateAuth("login", form({ email: "ana", password: "" }), t), { email: "validation.email", password: "validation.passwordRequired" });
});

test("cadastro: senha de 8 a 128 e nome de 2 a 80", () => {
  assert.deepEqual(validateAuth("register", form(), t), {});
  assert.equal(validateAuth("register", form({ password: pwd(7) }), t).password, "validation.password");
  assert.equal(validateAuth("register", form({ password: pwd(129) }), t).password, "validation.password");
  assert.equal(validateAuth("register", form({ displayName: " A " }), t).displayName, "validation.displayName");
  assert.equal(validateAuth("register", form({ displayName: "x".repeat(81) }), t).displayName, "validation.displayName");
});

test("esqueci a senha só pede o e-mail", () => {
  assert.deepEqual(validateAuth("forgot", form({ password: "" }), t), {});
  assert.equal(validateAuth("forgot", form({ email: "x@y" }), t).email, "validation.email");
});

test("redefinição: senha forte o bastante e igual à confirmação", () => {
  const p = pwd(10);
  assert.deepEqual(validateReset(p, p, t), {});
  assert.deepEqual(validateReset(p, `${p}x`, t), { confirm: "validation.mismatch" });
  assert.deepEqual(validateReset(pwd(5), "", t), { password: "validation.password" });
});
