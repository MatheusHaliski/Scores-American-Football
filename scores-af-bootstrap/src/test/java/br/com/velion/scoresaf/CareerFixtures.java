package br.com.velion.scoresaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.velion.scoresaf.application.common.json.Jsons;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;

/** Chamadas de API comuns aos testes da carreira (bloco 6a): criar carreira na liga do mundo padrão e ler as respostas. */
final class CareerFixtures {
  private CareerFixtures() {}

  static MvcResult call(MockMvc mvc, MockHttpServletRequestBuilder req, String auth, Object body) throws Exception {
    req.header("Authorization", auth).header("Accept-Language", "pt-BR");
    if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(Jsons.MAPPER.writeValueAsString(body));
    return mvc.perform(req).andReturn();
  }

  static Map<String, Object> map(MvcResult r) throws Exception {
    return Jsons.MAPPER.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {});
  }

  static List<Map<String, Object>> list(MvcResult r) throws Exception {
    return Jsons.MAPPER.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {});
  }

  static Map<String, Object> ok(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isBetween(200, 299);
    return map(r);
  }

  static void assertProblem(MvcResult r, int status, String code) throws Exception {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(status);
    assertThat(map(r)).containsEntry("code", code).containsKeys("message", "correlationId", "details");
  }

  /** Id da franquia {@code shortName} na primeira liga do mundo {@code worldId}. */
  static UUID club(MockMvc mvc, String auth, UUID worldId, String shortName) throws Exception {
    List<Map<String, Object>> leagues = list(call(mvc, get("/api/v1/worlds/" + worldId + "/leagues"), auth, null));
    List<Map<String, Object>> clubs = list(call(mvc, get("/api/v1/leagues/" + leagues.get(0).get("id") + "/clubs"), auth, null));
    for (Map<String, Object> c : clubs) if (shortName.equals(c.get("shortName"))) return UUID.fromString((String) c.get("id"));
    throw new AssertionError("franquia " + shortName + " ausente");
  }

  static Map<String, Object> body(String name, UUID worldId, UUID clubId, Long seed) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("name", name);
    if (worldId != null) b.put("worldId", worldId);
    b.put("clubId", clubId);
    b.put("coachName", "Treinadora Teste");
    b.put("coachNationality", "BRA");
    b.put("coachStyle", "OFFENSIVE");
    b.put("difficulty", "NORMAL");
    if (seed != null) b.put("seed", seed);
    return b;
  }

  /** Cria a carreira e devolve o corpo da resposta 201. */
  static Map<String, Object> create(MockMvc mvc, String auth, Map<String, Object> body) throws Exception {
    MvcResult r = call(mvc, post("/api/v1/careers"), auth, body);
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    return map(r);
  }

  static String id(Map<String, Object> m) { return (String) m.get("id"); }
}
