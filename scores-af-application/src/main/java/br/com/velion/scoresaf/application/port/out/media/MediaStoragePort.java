package br.com.velion.scoresaf.application.port.out.media;

import java.util.Optional;

/**
 * Armazenamento de mídia. Na plataforma (bloco 3) só guarda os arquivos de exportação LGPD, que são privados. Implementação
 * padrão: filesystem local ({@code scores.persistence.media=filesystem}); S3/MinIO e os prefixos públicos (logos, uniformes,
 * modelos 3D, troféus) e o do "Meu jogador" entram com o adaptador storage-s3 nos blocos 8–9 (D-8).
 */
public interface MediaStoragePort {

  /** Arquivos de exportação LGPD (RF46). <b>Privado</b>: servido só ao titular autenticado até expirar. */
  String EXPORTS = "exports/";

  /** Prefixos que uma rota pública de mídia deve recusar. */
  static boolean isPrivateKey(String key) { return key != null && key.startsWith(EXPORTS); }

  /** Grava (sobrescrevendo) e retorna a URL pública. */
  String put(String key, byte[] content, String contentType);

  Optional<byte[]> get(String key);

  void delete(String key);

  String publicUrl(String key);
}
