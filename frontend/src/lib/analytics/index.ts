/**
 * Analytics do produto (docs/30 §18). Não há provedor (Q-64): os eventos vão para um coletor local, em memória, que não
 * envia nada para fora. Privado por padrão — só registra depois que o consentimento ANALYTICS (RF46) for concedido, e
 * descarta tudo quando ele é revogado. Propriedades são ids de jogo e contexto de tela; nunca dado pessoal.
 */
import type { PrivacySettings } from "@/lib/api/types";

export type AnalyticsEvent = "player_card_viewed" | "player_card_flipped" | "player_dna_viewed" | "attribute_opened" | "player_compared" | "player_card_shared" | "player_3d_opened";

export type AnalyticsProps = Record<string, string | number | boolean | null>;

export interface TrackedEvent {
  event: AnalyticsEvent;
  props: AnalyticsProps;
  at: number;
}

const MAX_EVENTS = 200;
/** chaves que nunca saem do componente, mesmo que alguém passe por engano */
const FORBIDDEN = /email|name|nome|phone|cpf|ip|address|birth/i;

const state: { consent: boolean; events: TrackedEvent[] } = { consent: false, events: [] };

export function setAnalyticsConsent(granted: boolean): void {
  state.consent = granted;
  if (!granted) state.events = [];
}

export function track(event: AnalyticsEvent, props: AnalyticsProps = {}): void {
  if (!state.consent) return;
  const safe: AnalyticsProps = {};
  for (const [k, v] of Object.entries(props)) if (!FORBIDDEN.test(k)) safe[k] = v;
  state.events.push({ event, props: safe, at: Date.now() });
  if (state.events.length > MAX_EVENTS) state.events.splice(0, state.events.length - MAX_EVENTS);
}

/** O usuário concedeu a finalidade ANALYTICS? Sem preferências carregadas, não (privado por padrão). */
export function analyticsGranted(privacy: Pick<PrivacySettings, "consents"> | null | undefined): boolean {
  return privacy?.consents.find((c) => c.purpose === "ANALYTICS")?.granted ?? false;
}

/**
 * O consentimento pertence ao usuário da sessão: zera na hora em toda troca de usuário e só volta a coletar depois de
 * carregar as preferências DESTE usuário. Se outro usuário entrar (ou sair) no meio da requisição, a resposta é descartada.
 */
export async function syncConsentForUser(userId: string | null, deps: { loadPrivacy: () => Promise<Pick<PrivacySettings, "consents">>; currentUserId: () => string | null }): Promise<void> {
  setAnalyticsConsent(false);
  if (!userId) return;
  try {
    const privacy = await deps.loadPrivacy();
    if (deps.currentUserId() === userId) setAnalyticsConsent(analyticsGranted(privacy));
  } catch {
    // sem as preferências, segue sem coletar (privado por padrão)
  }
}

/** Eventos coletados (para o painel de diagnóstico e testes). */
export function trackedEvents(): readonly TrackedEvent[] {
  return state.events;
}
