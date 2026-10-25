package br.com.velion.scoresaf.application.usecase.rf46;

import java.util.UUID;

/** Participante da eliminação definitiva da conta (RF46 CA07, LGPD Art. 18, VI): apaga dados e mídia do titular no seu módulo. */
public interface AccountErasureHandler {
  void erase(UUID userId);
}
