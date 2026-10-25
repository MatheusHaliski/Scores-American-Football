package br.com.velion.scoresaf.application.usecase.rf46;

import java.util.Map;
import java.util.UUID;

/**
 * Ponto de extensão da exportação dos dados do titular (RF3 CA04, RF46 CA07, LGPD Art. 18, II e V). A plataforma reúne conta,
 * configurações, consentimentos, preferências, sessões e atividade; cada módulo do jogo que guarda dado do titular (carreiras e
 * compras no bloco 6, "Meu jogador" no bloco 9) publica um bean desta interface e acrescenta as próprias seções, sem mudar a
 * plataforma (D-12). Lista vazia no bloco 3. A eliminação da conta usa o par {@link AccountErasureHandler}.
 *
 * <p>Regras: nunca incluir hash de senha ou de token, chave de arquivo nem dado de outro titular; os valores devem ser
 * serializáveis em JSON e achatáveis no CSV ({@code AccountDataCollector#toCsv}): listas de mapas, mapas ou records.
 */
public interface AccountDataContributor {
  /** Acrescenta as seções deste módulo em {@code export}; a chave de cada seção é o nome dela no arquivo. */
  void contribute(UUID userId, Map<String, Object> export);
}
