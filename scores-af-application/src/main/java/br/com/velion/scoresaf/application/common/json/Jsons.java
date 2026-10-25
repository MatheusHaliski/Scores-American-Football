package br.com.velion.scoresaf.application.common.json;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON do Scores (Jackson 3). No basquete ficava no domínio, junto dos conversores JSON das entidades; aqui o domínio não
 * importa Jackson (D-9) e nenhuma entidade da plataforma tem coluna JSON, então mora na aplicação. As mesmas regras valem para a API (o customizador do Spring chama
 * {@link #configure}) e para o que fica gravado: datas ISO-8601, propriedade desconhecida ignorada e {@code null} em
 * campo primitivo vira o valor padrão. O Jackson 3 liga {@code FAIL_ON_NULL_FOR_PRIMITIVES} por padrão. Ver
 * {@code JsonContractTest}.
 */
public final class Jsons {
  public static final JsonMapper MAPPER = configure(JsonMapper.builder()).build();

  public static JsonMapper.Builder configure(JsonMapper.Builder builder) {
    return builder
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
  }

  private Jsons() {}
}
