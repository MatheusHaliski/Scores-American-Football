package br.com.velion.scoresaf.application.common.tx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** RNF1 R6/CA6 — efeito fora do MySQL só depois do commit; rollback descarta; sem transação executa na hora. */
class AfterCommitTest {

  /** Gerenciador mínimo: a sincronização (afterCommit/afterCompletion) é do AbstractPlatformTransactionManager. */
  static final class PseudoTx extends AbstractPlatformTransactionManager {
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
    @Override protected void doCommit(DefaultTransactionStatus status) { }
    @Override protected void doRollback(DefaultTransactionStatus status) { }
  }

  private final TransactionTemplate tx = new TransactionTemplate(new PseudoTx());

  @Test
  void runsOnlyAfterCommit() {
    AtomicInteger effects = new AtomicInteger();
    tx.executeWithoutResult(st -> {
      AfterCommit.run("teste", effects::incrementAndGet);
      assertThat(effects).hasValue(0);
    });
    assertThat(effects).hasValue(1);
  }

  @Test
  void discardedOnRollback() {
    AtomicInteger effects = new AtomicInteger();
    tx.executeWithoutResult(st -> {
      AfterCommit.run("teste", effects::incrementAndGet);
      st.setRollbackOnly();
    });
    assertThat(effects).hasValue(0);
  }

  @Test
  void discardedWhenTheTransactionThrows() {
    AtomicInteger effects = new AtomicInteger();
    try {
      tx.executeWithoutResult(st -> {
        AfterCommit.run("teste", effects::incrementAndGet);
        throw new IllegalStateException("falha no meio da operação");
      });
    } catch (IllegalStateException expected) {
      // rollback
    }
    assertThat(effects).hasValue(0);
  }

  @Test
  void undoRunsOnlyOnRollback() {
    AtomicInteger undone = new AtomicInteger();
    tx.executeWithoutResult(st -> AfterCommit.onRollback("teste", undone::incrementAndGet));
    assertThat(undone).as("commit: nada a desfazer").hasValue(0);
    tx.executeWithoutResult(st -> {
      AfterCommit.onRollback("teste", undone::incrementAndGet);
      st.setRollbackOnly();
    });
    assertThat(undone).as("rollback: desfaz").hasValue(1);
    AfterCommit.onRollback("sem transação", undone::incrementAndGet);
    assertThat(undone).as("sem transação não há o que desfazer").hasValue(1);
  }

  @Test
  void anEffectScheduledFromAnotherEffectStillRuns() {
    // ex.: o editor de mundos agenda a auditoria para depois do commit e o audit.ok agenda de novo (D-91): no afterCommit a
    // transação ainda parece ativa, e uma sincronização registrada ali nunca seria chamada
    AtomicInteger nested = new AtomicInteger();
    tx.executeWithoutResult(st -> AfterCommit.run("fora", () -> AfterCommit.run("dentro", nested::incrementAndGet)));
    assertThat(nested).hasValue(1);
  }

  @Test
  void runsImmediatelyWithoutTransaction() {
    AtomicInteger effects = new AtomicInteger();
    AfterCommit.run("teste", effects::incrementAndGet);
    assertThat(effects).hasValue(1);
  }

  @Test
  void aFailingEffectDoesNotBreakTheCommit() {
    AtomicInteger after = new AtomicInteger();
    tx.executeWithoutResult(st -> {
      AfterCommit.run("quebra", () -> { throw new IllegalStateException("cassandra fora do ar"); });
      AfterCommit.run("segue", after::incrementAndGet);
    });
    assertThat(after).hasValue(1);
  }
}
