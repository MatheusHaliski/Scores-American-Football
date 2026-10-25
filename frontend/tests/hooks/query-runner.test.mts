// useQuery (D-126): com dois pedidos no ar (a troca de parâmetro e um refetch, ou duas trocas seguidas), vale a resposta do último
// pedido, mesmo que a do anterior chegue depois; erro e fim do carregando de um pedido superado também são descartados.
import { test } from "node:test";
import assert from "node:assert/strict";
import { queryRunner, type QuerySink } from "@/lib/hooks/queryRunner";

interface Deferred<T> {
  promise: Promise<T>;
  resolve: (v: T) => void;
  reject: (e: unknown) => void;
}

function deferred<T>(): Deferred<T> {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** Estado do hook simulado: dados, erro e carregando, como o `useQuery` guarda. */
function sink<T>() {
  const state = { data: null as T | null, error: null as unknown, loading: false, applied: [] as T[] };
  const s: QuerySink<T> = {
    start: () => {
      state.loading = true;
      state.error = null;
    },
    data: (v) => {
      state.data = v;
      state.applied.push(v);
    },
    error: (e) => {
      state.error = e;
    },
    settle: () => {
      state.loading = false;
    },
  };
  return { state, s };
}

test("respostas fora de ordem: vence a do último pedido", async () => {
  const pending = [deferred<string>(), deferred<string>()];
  let i = 0;
  const { state, s } = sink<string>();
  const run = queryRunner(() => pending[i++].promise, s);
  const first = run(); // temporada antiga (?number=1)
  const second = run(); // temporada nova, depois do avanço
  pending[1].resolve("nova");
  await second;
  assert.equal(state.data, "nova");
  assert.equal(state.loading, false);
  pending[0].resolve("antiga"); // a resposta antiga chega por último
  await first;
  assert.equal(state.data, "nova", "a resposta antiga não sobrescreve a tela");
  assert.deepEqual(state.applied, ["nova"]);
});

test("pedido superado não apaga o carregando nem mostra erro do anterior", async () => {
  const pending = [deferred<string>(), deferred<string>()];
  let i = 0;
  const { state, s } = sink<string>();
  const run = queryRunner(() => pending[i++].promise, s);
  const first = run();
  const second = run();
  pending[0].reject(new Error("falhou o antigo"));
  await first;
  assert.equal(state.error, null);
  assert.equal(state.loading, true, "o pedido mais recente ainda está no ar");
  pending[1].resolve("nova");
  await second;
  assert.equal(state.data, "nova");
  assert.equal(state.loading, false);
});

test("um pedido só, como nas outras telas: aplica dados, erro e fim do carregando", async () => {
  const { state, s } = sink<number>();
  await queryRunner(async () => 42, s)();
  assert.equal(state.data, 42);
  assert.equal(state.loading, false);
  const err = sink<number>();
  await queryRunner<number>(async () => {
    throw new Error("x");
  }, err.s)();
  assert.ok(err.state.error instanceof Error);
  assert.equal(err.state.loading, false);
});

test("depois de desmontar, nada é aplicado", async () => {
  const d = deferred<string>();
  let alive = true;
  const { state, s } = sink<string>();
  const p = queryRunner(() => d.promise, s, () => alive)();
  alive = false;
  d.resolve("tarde");
  await p;
  assert.equal(state.data, null);
});
