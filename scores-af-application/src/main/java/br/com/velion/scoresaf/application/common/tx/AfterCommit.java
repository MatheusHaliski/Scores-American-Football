package br.com.velion.scoresaf.application.common.tx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * RNF1 (R6) — efeitos em armazenamentos sem transação com o MySQL (Cassandra, Redis, OpenSearch, S3) só acontecem
 * depois do COMMIT. Dentro de uma transação ativa, o efeito é agendado para {@code afterCommit}: se houver rollback, ele
 * é descartado e nada fica gravado fora do banco relacional. Fora de transação, executa na hora.
 *
 * <p>Uma falha no efeito não desfaz o commit (a fonte da verdade já está gravada): fica registrada como WARN e a projeção
 * é reconstruída pelo leitor (ex.: cache vazio → lê do MySQL). Por isso os efeitos agendados aqui devem ser idempotentes.
 */
public final class AfterCommit {
  private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

  private AfterCommit() {}

  /** Agenda {@code action} para depois do commit da transação corrente (ou executa já, sem transação). */
  public static void run(String what, Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive() && TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          safely(what, action);
        }
      });
    } else {
      safely(what, action);
    }
  }

  /**
   * Desfaz um efeito já feito fora do banco se a transação corrente sofrer rollback (ex.: apagar o arquivo novo que um
   * upload gravou no S3 antes de o registro ser salvo). Sem transação não há o que desfazer: não faz nada.
   */
  public static void onRollback(String what, Runnable undo) {
    if (!TransactionSynchronizationManager.isSynchronizationActive() || !TransactionSynchronizationManager.isActualTransactionActive()) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) safely(what, undo);
      }
    });
  }

  private static void safely(String what, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.warn("Efeito pós-commit falhou ({}): {}", what, e.toString());
    }
  }
}
