package br.com.velion.scoresaf.web.fallback;

import br.com.velion.scoresaf.application.port.out.media.MediaStoragePort;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mídia em filesystem local (padrão: {@code scores.persistence.media=filesystem}), do {@code FilesystemMediaConfig} do basquete. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "scores.persistence.media", havingValue = "filesystem", matchIfMissing = true)
public class FallbackMediaConfig {
  @Bean
  public MediaStoragePort filesystemMediaStorageAdapter(
      @Value("${scores.persistence.media-local-dir:./data/media}") String dir,
      @Value("${scores.app.base-url:http://localhost:8080}") String baseUrl) {
    return new FilesystemMediaStorageAdapter(Path.of(dir), baseUrl);
  }
}
