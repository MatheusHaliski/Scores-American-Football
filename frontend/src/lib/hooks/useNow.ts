"use client";
import { useEffect, useState } from "react";

/** Relógio da tela: `Date.now()` atualizado a cada `intervalMs` enquanto `active` (contagem regressiva da chamada de jogada). */
export function useNow(active: boolean, intervalMs = 250): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [active, intervalMs]);
  return now;
}
