package br.com.velion.scoresaf.domain.catalog;

/** Grupos dos subatributos (docs/01 §3.4), na ordem de exibição; o {@code key} é o do contrato JSON (editor, card, front). */
public enum AttributeGroup {
  PHYSICAL("physical"), PASSING("passing"), RUSHING("rushing"), RECEIVING("receiving"), BLOCKING("blocking"), DEFENSE("defense"),
  KICKING("kicking"), MENTAL("mental");

  private final String key;
  AttributeGroup(String key) { this.key = key; }
  public String key() { return key; }
}
