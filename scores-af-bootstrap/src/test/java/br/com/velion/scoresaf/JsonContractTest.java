package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.velion.scoresaf.application.common.json.Jsons;
import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato JSON com o front (Jackson 3). Vale para o mapper da API (bean do Spring, com o application.yml) e para o
 * que é gravado no banco ({@link Jsons#MAPPER}):
 * <ul>
 *   <li>{@code null} em campo primitivo vira o valor padrão. O Jackson 3 liga {@code FAIL_ON_NULL_FOR_PRIMITIVES} por padrão,
 *       e o front manda {@code null} em campos opcionais;</li>
 *   <li>datas em ISO-8601, nunca timestamp numérico;</li>
 *   <li>propriedade desconhecida é ignorada (front mais novo que a API);</li>
 *   <li>enum pelo nome.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class JsonContractTest {
  enum Side { HOME, AWAY }
  record Payload(int minutes, boolean starter, double rating, String name, Side side, Instant at, LocalDate day) {}

  @Autowired JsonMapper apiMapper;

  static Stream<Arguments> mappers() { return Stream.of(Arguments.of("api"), Arguments.of("persistência")); }

  private JsonMapper mapper(String which) { return "api".equals(which) ? apiMapper : Jsons.MAPPER; }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mappers")
  void null_em_primitivo_vira_valor_padrao(String which) {
    Payload p = mapper(which).readValue("{\"minutes\":null,\"starter\":null,\"rating\":null,\"name\":\"Ana\"}", Payload.class);
    assertThat(p.minutes()).isZero();
    assertThat(p.starter()).isFalse();
    assertThat(p.rating()).isZero();
    assertThat(p.name()).isEqualTo("Ana");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mappers")
  void datas_em_iso_8601(String which) {
    String json = mapper(which).writeValueAsString(
        new Payload(1, true, 1.5, "x", Side.HOME, Instant.parse("2026-10-05T12:30:00Z"), LocalDate.parse("2026-10-05")));
    assertThat(json).contains("\"at\":\"2026-10-05T12:30:00Z\"").contains("\"day\":\"2026-10-05\"");
    Payload back = mapper(which).readValue(json, Payload.class);
    assertThat(back.at()).isEqualTo(Instant.parse("2026-10-05T12:30:00Z"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mappers")
  void propriedade_desconhecida_e_ignorada(String which) {
    Payload p = mapper(which).readValue("{\"minutes\":12,\"campoNovoDoFront\":{\"x\":1}}", Payload.class);
    assertThat(p.minutes()).isEqualTo(12);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mappers")
  void enum_pelo_nome(String which) {
    assertThat(mapper(which).writeValueAsString(new Payload(0, false, 0, null, Side.AWAY, null, null))).contains("\"side\":\"AWAY\"");
    assertThat(mapper(which).readValue("{\"side\":\"HOME\"}", Payload.class).side()).isEqualTo(Side.HOME);
  }
}
