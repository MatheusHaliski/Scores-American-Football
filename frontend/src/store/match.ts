"use client";
import { create } from "zustand";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import type { AdvanceMode, MatchAdjustment, MatchState, PlanTendencies, PlaybookWeights, PlayEvent, UUID } from "@/lib/api/types";
import { lastSeqOf, mergeEvents, reactionTo, type MatchNotice } from "@/lib/match/live";

/** Páginas do play-by-play ao carregar a partida (o servidor aceita até 1000 por vez). */
const PAGE = 1000;

interface MatchStore {
  matchId: UUID | null;
  state: MatchState | null;
  events: PlayEvent[];
  loading: boolean;
  busy: boolean;
  error: string | null;
  notice: MatchNotice;
  /** último ajuste do intervalo mandado nesta partida (o estado da API não traz o plano em vigor: Q-35) */
  adjusted: { matchId: UUID; plan?: PlanTendencies; playbook?: PlaybookWeights } | null;

  load: (matchId: UUID) => Promise<void>;
  refresh: () => Promise<void>;
  advance: (mode: AdvanceMode) => Promise<void>;
  callPlay: (optionCode: string) => Promise<void>;
  adjust: (adjustments: MatchAdjustment[]) => Promise<boolean>;
  timeout: () => Promise<void>;
  dismiss: () => void;
  reset: () => void;
}

/**
 * Partida ao vivo (RF27/RF29/RF62): estado da sessão, play-by-play incremental pelo cursor (`/events?after=`), avanço por modo,
 * chamada de jogada, ajustes do intervalo e tempo técnico. Toda escrita manda a versão do estado (`live.version`); os erros
 * esperados (decisão já fechada pela IA, versão velha, decisão aberta) recarregam o estado com um aviso em vez de um erro.
 */
export const useMatch = create<MatchStore>()((set, get) => {
  /** Busca os lances depois do último que a tela tem (sem duplicar) e junta os de `live.lastEvents`. */
  async function syncEvents(state: MatchState): Promise<void> {
    let events = mergeEvents(get().events, state.live.lastEvents);
    let after = lastSeqOf(events);
    for (let i = 0; i < 20; i++) {
      const page = await api.matches.events(state.matchId, after, PAGE);
      events = mergeEvents(events, page.events);
      if (!page.events.length || lastSeqOf(events) >= page.lastSeq) break;
      after = lastSeqOf(events);
    }
    if (get().matchId === state.matchId) set({ events });
  }

  async function apply(next: MatchState): Promise<void> {
    set({ state: next });
    await syncEvents(next);
  }

  async function fail(e: unknown): Promise<void> {
    const r = reactionTo(e);
    set({ notice: r.notice, error: r.showError ? describeError(e) : null });
    if (r.reload) await get().refresh();
  }

  async function write(fn: (s: MatchState) => Promise<MatchState>): Promise<boolean> {
    const s = get().state;
    if (!s || get().busy) return false;
    set({ busy: true, error: null, notice: null });
    try {
      await apply(await fn(s));
      return true;
    } catch (e) {
      await fail(e);
      return false;
    } finally {
      set({ busy: false });
    }
  }

  return {
    matchId: null,
    state: null,
    events: [],
    loading: false,
    busy: false,
    error: null,
    notice: null,
    adjusted: null,

    async load(matchId) {
      if (get().matchId !== matchId) set({ matchId, state: null, events: [], notice: null, error: null });
      set({ loading: true });
      try {
        await apply(await api.matches.get(matchId));
      } catch (e) {
        set({ error: describeError(e) });
      } finally {
        set({ loading: false });
      }
    },

    async refresh() {
      const id = get().matchId;
      if (!id) return;
      try {
        await apply(await api.matches.get(id));
      } catch (e) {
        set({ error: describeError(e) });
      }
    },

    async advance(mode) {
      await write((s) => api.matches.advance(s.matchId, { mode, version: s.live.version }));
    },

    async callPlay(optionCode) {
      await write((s) => {
        const d = s.live.decision;
        if (!d) throw new Error(describeError(null));
        return api.matches.playCall(s.matchId, d.id, { optionCode, version: s.live.version });
      });
    },

    async adjust(adjustments) {
      const ok = await write((s) => api.matches.decisions(s.matchId, { version: s.live.version, adjustments }));
      if (ok) {
        const id = get().matchId!;
        const prev = get().adjusted?.matchId === id ? get().adjusted! : { matchId: id };
        const next = { ...prev };
        for (const a of adjustments) {
          if (a.type === "GAME_PLAN") next.plan = a.gamePlan;
          if (a.type === "PLAYBOOK") next.playbook = a.playbook;
        }
        set({ adjusted: next });
      }
      return ok;
    },

    async timeout() {
      await write((s) => api.matches.decisions(s.matchId, { version: s.live.version, adjustments: [{ type: "TIMEOUT" }] }));
    },

    dismiss() {
      set({ notice: null, error: null });
    },

    reset() {
      set({ matchId: null, state: null, events: [], loading: false, busy: false, error: null, notice: null });
    },
  };
});
