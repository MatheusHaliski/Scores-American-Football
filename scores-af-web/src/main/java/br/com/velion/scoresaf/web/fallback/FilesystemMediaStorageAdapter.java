package br.com.velion.scoresaf.web.fallback;

import br.com.velion.scoresaf.application.port.out.media.MediaStoragePort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Fallback em filesystem (padrão do basquete, módulo storage-s3): grava em {@code scores.persistence.media-local-dir}. Na plataforma
 * só guarda as exportações LGPD, que são privadas e servidas pela rota autenticada; a rota pública {@code /media/**} e o S3 entram
 * com o storage-s3 (D-8). Em produção com mais de uma instância, use um volume compartilhado ou o S3.
 */
public class FilesystemMediaStorageAdapter implements MediaStoragePort {
  private final Path root; private final String baseUrl;
  public FilesystemMediaStorageAdapter(Path root, String baseUrl) {
    this.root = root.toAbsolutePath().normalize(); this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    try { Files.createDirectories(this.root); } catch (IOException e) { throw new UncheckedIOException(e); }
  }
  private Path resolve(String key) {
    Path p = root.resolve(MediaKeys.sanitize(key)).normalize();
    if (!p.startsWith(root)) throw new IllegalArgumentException("chave fora do diretório de mídia");
    return p;
  }
  @Override public String put(String key, byte[] content, String contentType) {
    Path p = resolve(key);
    try { Files.createDirectories(p.getParent()); Files.write(p, content); } catch (IOException e) { throw new UncheckedIOException(e); }
    return publicUrl(key);
  }
  @Override public Optional<byte[]> get(String key) {
    Path p = resolve(key);
    try { return Files.isRegularFile(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty(); } catch (IOException e) { throw new UncheckedIOException(e); }
  }
  @Override public void delete(String key) { try { Files.deleteIfExists(resolve(key)); } catch (IOException e) { throw new UncheckedIOException(e); } }
  @Override public String publicUrl(String key) { return baseUrl + "/media/" + MediaKeys.sanitize(key); }
}
