package br.com.velion.scoresaf.application.security;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Gerenciador de transação vazio para testes unitários de casos de uso que usam TransactionTemplate. */
public class NoOpTransactionManager extends AbstractPlatformTransactionManager {
  @Override protected Object doGetTransaction() { return new Object(); }
  @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
  @Override protected void doCommit(DefaultTransactionStatus status) {}
  @Override protected void doRollback(DefaultTransactionStatus status) {}
}
