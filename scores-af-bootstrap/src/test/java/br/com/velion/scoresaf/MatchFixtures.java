package br.com.velion.scoresaf;

import static br.com.velion.scoresaf.CareerFixtures.call;
import static br.com.velion.scoresaf.CareerFixtures.ok;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;

/** Chamadas de API comuns aos testes da partida (bloco 6b): nível de chamada, jogar a semana, avançar e terminar a partida. */
final class MatchFixtures {
  private MatchFixtures() {}

  static void level(MockMvc mvc, String auth, String level) throws Exception {
    ok(call(mvc, put("/api/v1/me/settings"), auth, Map.of("playCallingLevel", level)));
  }

  static Map<String, Object> play(MockMvc mvc, String auth, String careerId) throws Exception {
    return ok(call(mvc, post("/api/v1/careers/" + careerId + "/weeks/current/play"), auth, null));
  }

  static Map<String, Object> state(MockMvc mvc, String auth, String mid) throws Exception {
    return ok(call(mvc, get("/api/v1/matches/" + mid), auth, null));
  }

  static Map<String, Object> advance(MockMvc mvc, String auth, String mid, String mode) throws Exception {
    return ok(call(mvc, post("/api/v1/matches/" + mid + "/advance"), auth, Map.of("mode", mode)));
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> live(Map<String, Object> state) { return (Map<String, Object>) state.get("live"); }

  @SuppressWarnings("unchecked")
  static Map<String, Object> decision(Map<String, Object> state) { return (Map<String, Object>) live(state).get("decision"); }

  static long version(Map<String, Object> state) { return ((Number) live(state).get("version")).longValue(); }

  /** Joga até o fim no modo pedido; decisão aberta é respondida com a opção que a IA tomaria ({@code autoOptionCode}). */
  static Map<String, Object> finish(MockMvc mvc, String auth, String mid, String mode) throws Exception {
    Map<String, Object> s = state(mvc, auth, mid);
    int guard = 0;
    while (!"FINISHED".equals(s.get("status"))) {
      if (++guard > 2000) throw new AssertionError("partida não terminou");
      if ("AWAITING_DECISION".equals(s.get("status"))) {
        Map<String, Object> d = decision(s);
        s = ok(call(mvc, post("/api/v1/matches/" + mid + "/play-calls/" + d.get("id")), auth,
            Map.of("optionCode", d.get("autoOptionCode"), "version", version(s))));
      } else {
        s = advance(mvc, auth, mid, mode);
      }
    }
    return s;
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> events(MockMvc mvc, String auth, String mid, int after, int limit) throws Exception {
    return (List<Map<String, Object>>) ok(call(mvc, get("/api/v1/matches/" + mid + "/events?after=" + after + "&limit=" + limit), auth, null)).get("events");
  }
}
