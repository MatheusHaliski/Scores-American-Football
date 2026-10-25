package br.com.velion.scoresaf.web.config;

import br.com.velion.scoresaf.application.common.json.Jsons;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Jackson 3 da API: as mesmas regras do JSON gravado ({@link Jsons#configure}). Contrato coberto por JsonContractTest. */
@Configuration
public class WebJacksonConfig {
  @Bean
  public JsonMapperBuilderCustomizer scoresJsonContract() {
    return Jsons::configure;
  }
}
