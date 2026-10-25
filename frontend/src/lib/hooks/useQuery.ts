"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { describeError } from "@/lib/api/errors";
import { queryRunner } from "./queryRunner";

export interface QueryState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
  refetch: () => Promise<void>;
  /** substitui os dados localmente (após uma mutação) */
  setData: (updater: T | ((prev: T | null) => T | null)) => void;
}

/**
 * Hook mínimo de consulta (sem react-query): executa `fn` quando `deps` mudam,
 * guarda dados/erro/carregando e evita atualizar após desmontar. Com dois pedidos
 * no ar, só o mais recente aplica o resultado (`queryRunner`, D-126).
 */
export function useQuery<T>(fn: () => Promise<T>, deps: unknown[], enabled = true): QueryState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(enabled);
  const alive = useRef(true);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  // um executor só por montagem: o número do pedido mais recente vive nele (D-126)
  const runner = useRef<(() => Promise<void>) | null>(null);
  const run = useCallback(() => {
    runner.current ??= queryRunner<T>(
      () => fnRef.current(),
      {
        start: () => {
          setLoading(true);
          setError(null);
        },
        data: (result) => setData(() => result),
        error: (e) => setError(describeError(e)),
        settle: () => setLoading(false),
      },
      () => alive.current,
    );
    return runner.current();
  }, []);

  useEffect(() => {
    alive.current = true;
    if (enabled) void run();
    else setLoading(false);
    return () => {
      alive.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, ...deps]);

  return { data, error, loading, refetch: run, setData: (u) => setData((prev) => (typeof u === "function" ? (u as (p: T | null) => T | null)(prev) : u)) };
}
