/**
 * Campo 3D ou 2D no painel da partida, sem React (testável em node). O 3D é o padrão; vira 2D quando o aparelho não tem WebGL,
 * quando a cena falhou (erro de render ou contexto perdido) ou quando o usuário escolheu o 2D. A escolha do usuário fica no
 * `localStorage` deste navegador (só uma conveniência de tela, não vai para o servidor); ler ou gravar pode falhar (modo
 * privado, cota, armazenamento bloqueado) e aí vale o padrão.
 */
export type FieldView = "3d" | "2d";

export const VIEW_PREF_KEY = "scoresaf.match.fieldView.v1";

/** O que obrigou o 2D (aviso na tela); null quando é escolha do usuário ou é o 3D. */
export type FieldFallback = "noWebgl" | "error" | "contextLost" | null;

export interface ViewInput {
  /** null enquanto a detecção não rodou (antes da hidratação) */
  webgl: boolean | null;
  pref: FieldView | null;
  fallback: FieldFallback;
}

/** null = ainda detectando (a tela mostra o esqueleto e não baixa o pacote 3D). */
export function chooseView({ webgl, pref, fallback }: ViewInput): FieldView | null {
  if (webgl === null) return null;
  if (!webgl) return "2d";
  if (fallback) return "2d";
  return pref ?? "3d";
}

type PrefStorage = Pick<Storage, "getItem" | "setItem">;

export function readViewPref(storage: PrefStorage | null | undefined): FieldView | null {
  try {
    const v = storage?.getItem(VIEW_PREF_KEY);
    return v === "3d" || v === "2d" ? v : null;
  } catch {
    return null;
  }
}

/** Grava a escolha; false quando o armazenamento recusou (a escolha vale só nesta tela). */
export function writeViewPref(storage: PrefStorage | null | undefined, view: FieldView): boolean {
  try {
    if (!storage) return false;
    storage.setItem(VIEW_PREF_KEY, view);
    return true;
  } catch {
    return false;
  }
}

/** `localStorage` quando existe e é acessível (o acesso em si pode lançar exceção). */
export function browserStorage(): PrefStorage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage;
  } catch {
    return null;
  }
}
