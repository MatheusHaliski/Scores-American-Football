/**
 * Execução de um pedido do `useQuery`, sem React (testável em Node): avisa o início, entrega dados ou erro e o fim do carregando ao
 * destino (o estado do hook), e nada depois que o dono desmonta (`alive`).
 *
 * Cada chamada ganha um número (D-126): só o pedido mais recente aplica o resultado. Com dois pedidos no ar (troca de parâmetro e
 * refetch, ou duas trocas seguidas), a resposta do anterior é descartada mesmo que chegue depois — dados, erro e fim do carregando.
 * Com um pedido só, nada muda.
 */
export interface QuerySink<T> {
  start: () => void;
  data: (value: T) => void;
  error: (e: unknown) => void;
  settle: () => void;
}

export function queryRunner<T>(fn: () => Promise<T>, sink: QuerySink<T>, alive: () => boolean = () => true): () => Promise<void> {
  let last = 0;
  return async () => {
    const id = ++last;
    const current = () => alive() && id === last;
    sink.start();
    try {
      const result = await fn();
      if (current()) sink.data(result);
    } catch (e) {
      if (current()) sink.error(e);
    } finally {
      if (current()) sink.settle();
    }
  };
}
