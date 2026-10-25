/**
 * Estado do modo mock da plataforma: o usuário "logado" e helpers de armazenamento local. Só o modo mock usa
 * `localStorage` (é o "servidor" de mentira); a sessão real (modo live) nunca guarda nada no navegador.
 */
import type { UserAccount } from "../types";

const KEY_USER = "scoresaf.mock.user.v1";

export function storage(): Storage | null {
  try {
    if (typeof window === "undefined") return null;
    return window.localStorage;
  } catch {
    return null;
  }
}

export function readJson<T>(key: string): T | null {
  const s = storage();
  if (!s) return null;
  try {
    const raw = s.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export function writeJson(key: string, value: unknown): boolean {
  const s = storage();
  if (!s) return false;
  try {
    s.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    /* quota / modo privado: mantém só em memória */
    return false;
  }
}

const memory = { user: null as UserAccount | null };

export function getUser(): UserAccount | null {
  if (!memory.user) memory.user = readJson<UserAccount>(KEY_USER);
  return memory.user;
}

export function setUser(u: UserAccount | null): void {
  memory.user = u;
  if (u) writeJson(KEY_USER, u);
  else storage()?.removeItem(KEY_USER);
}
