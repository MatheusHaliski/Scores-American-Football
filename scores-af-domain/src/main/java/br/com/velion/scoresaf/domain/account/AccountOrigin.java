package br.com.velion.scoresaf.domain.account;

/**
 * Origem da conta (bloco 2 de segurança, D-23). Contas {@link #TEST_SEED} e {@link #DEMO} ficam fora de rankings
 * compartilhados, métricas oficiais e Scores Points oficiais, e só elas podem ser apagadas pelo reset de dados de teste.
 * Uma conta {@link #REAL} excluída é anonimizada, nunca apagada.
 */
public enum AccountOrigin {
  REAL, TEST_SEED, DEMO, SYSTEM;

  public boolean isTest() { return this == TEST_SEED || this == DEMO; }
}
