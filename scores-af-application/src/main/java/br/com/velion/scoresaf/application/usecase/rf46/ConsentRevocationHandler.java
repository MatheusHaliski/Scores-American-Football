package br.com.velion.scoresaf.application.usecase.rf46;

import br.com.velion.scoresaf.domain.account.ConsentPurpose;
import java.util.UUID;

/**
 * Efeito em cascata da revogação de um consentimento (RF46 CA03). Ex.: revogar {@link ConsentPurpose#BIOMETRIC_AVATAR} apaga
 * foto, textura e modelo 3D do "Meu jogador" (RF44, bloco 9). Nenhum handler na plataforma. Executado na mesma transação da revogação.
 */
public interface ConsentRevocationHandler {
  ConsentPurpose purpose();
  void onRevoked(UUID userId);
}
