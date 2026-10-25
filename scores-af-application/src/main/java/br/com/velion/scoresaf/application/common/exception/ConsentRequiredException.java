package br.com.velion.scoresaf.application.common.exception;

import br.com.velion.scoresaf.domain.account.ConsentPurpose;

/**
 * Operação que depende de consentimento ainda não concedido (ou revogado) — 403 com {@code code=CONSENT_REQUIRED} e a finalidade
 * em {@code purpose}, para o cliente abrir o fluxo de consentimento específico (RF44/RF46, LGPD Art. 8º e 11).
 */
public class ConsentRequiredException extends ScoresException {
  private final ConsentPurpose purpose;
  public ConsentRequiredException(ConsentPurpose purpose) {
    super("CONSENT_REQUIRED", "Esta ação exige o consentimento: " + purpose.name(), purpose.name());
    this.purpose = purpose;
  }
  public ConsentPurpose purpose() { return purpose; }
}
