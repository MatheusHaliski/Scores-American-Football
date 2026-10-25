"use client";
import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import type { CareerDashboard } from "@/lib/api/types";

interface CareerState {
  /** última carreira aberta (conveniência do aparelho; o servidor é a fonte de verdade) */
  activeCareerId: string | null;
  /** cache leve do dashboard para a barra lateral e o topo (nunca persistido) */
  dashboard: CareerDashboard | null;
  setActiveCareer: (id: string | null) => void;
  setDashboard: (d: CareerDashboard | null) => void;
}

/** Carreira ativa + dashboard em memória para o shell da franquia. */
export const useCareer = create<CareerState>()(
  persist(
    (set) => ({
      activeCareerId: null,
      dashboard: null,
      setActiveCareer: (activeCareerId) => set({ activeCareerId }),
      setDashboard: (dashboard) => set({ dashboard }),
    }),
    { name: "scoresaf.career", storage: createJSONStorage(() => localStorage), partialize: (s) => ({ activeCareerId: s.activeCareerId }) },
  ),
);
