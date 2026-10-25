/**
 * Validação dos formulários de conta, com as mesmas regras dos records do backend (AuthController): e-mail válido,
 * senha de 8 a 128 no cadastro e na redefinição (no login só exige a senha), nome de 2 a 80. Pura, testada em Node.
 */
export type AuthMode = "login" | "register" | "forgot";

export interface AuthForm {
  email: string;
  password: string;
  displayName: string;
  marketingConsent: boolean;
}

type Key = "validation.email" | "validation.password" | "validation.passwordRequired" | "validation.displayName" | "validation.mismatch";

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 128;

const passwordOk = (p: string) => p.length >= PASSWORD_MIN && p.length <= PASSWORD_MAX;

export function validateAuth(mode: AuthMode, f: AuthForm, t: (k: Key) => string): Record<string, string> {
  const errs: Record<string, string> = {};
  if (!EMAIL.test(f.email.trim())) errs.email = t("validation.email");
  if (mode === "login" && !f.password) errs.password = t("validation.passwordRequired");
  if (mode === "register") {
    if (!passwordOk(f.password)) errs.password = t("validation.password");
    const n = f.displayName.trim().length;
    if (n < 2 || n > 80) errs.displayName = t("validation.displayName");
  }
  return errs;
}

export function validateReset(password: string, confirm: string, t: (k: Key) => string): Record<string, string> {
  const errs: Record<string, string> = {};
  if (!passwordOk(password)) errs.password = t("validation.password");
  else if (password !== confirm) errs.confirm = t("validation.mismatch");
  return errs;
}
