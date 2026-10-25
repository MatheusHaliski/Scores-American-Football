package br.com.velion.scoresaf.web.fallback;

/** Validação de chaves de mídia (do storage-s3 do basquete): sem caminho absoluto, sem {@code ..}, até 512 caracteres. */
final class MediaKeys {
  private MediaKeys() {}
  static String sanitize(String key) {
    if (key == null || key.isBlank()) throw new IllegalArgumentException("chave de mídia vazia");
    String k = key.replace('\\', '/');
    while (k.startsWith("/")) k = k.substring(1);
    if (k.contains("..") || k.contains("//") || k.length() > 512) throw new IllegalArgumentException("chave de mídia inválida");
    return k;
  }
}
