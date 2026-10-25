package br.com.velion.scoresaf;

import static br.com.velion.scoresaf.CareerFixtures.body;
import static br.com.velion.scoresaf.CareerFixtures.call;
import static br.com.velion.scoresaf.CareerFixtures.club;
import static br.com.velion.scoresaf.CareerFixtures.create;
import static br.com.velion.scoresaf.CareerFixtures.id;
import static br.com.velion.scoresaf.CareerFixtures.list;
import static br.com.velion.scoresaf.CareerFixtures.ok;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Temporada curta para os testes do bloco 6c: o mundo padrão clonado no editor com a liga em {@code games} jogos por franquia (sem
 * folga) — as mesmas 32 franquias, conferências, divisões e regras de playoff (7 por conferência, folga da 1ª, final neutra) —, a
 * carreira nesse mundo e a temporada jogada até o fim pela API (cada jogo do usuário pela partida ao vivo, em FULL).
 */
final class SeasonFixtures {
  private SeasonFixtures() {}

  record Career(String auth, String id, String base, UUID world) {}

  static Career shortCareer(MockMvc mvc, String auth, UUID defaultWorld, String name, String club, long seed, int games) throws Exception {
    MatchFixtures.level(mvc, auth, "NONE");
    UUID world = UUID.fromString(id(ok(call(mvc, post("/api/v1/worlds/" + defaultWorld + "/clone"), auth, Map.of("name", "Temporada curta")))));
    List<Map<String, Object>> leagues = list(call(mvc, get("/api/v1/worlds/" + world + "/leagues"), auth, null));
    ok(call(mvc, put("/api/v1/leagues/" + leagues.get(0).get("id")), auth,
        Map.of("rules", Map.of("regularSeasonWeeks", games, "gamesPerTeam", games, "byeWeeksPerTeam", 0))));
    Map<String, Object> c = create(mvc, auth, body(name, world, club(mvc, auth, world, club), seed));
    return new Career(auth, id(c), "/api/v1/careers/" + id(c), world);
  }

  /** Joga a semana atual: a partida do usuário até o fim, ou fecha a semana sem jogo dele. Devolve o painel ou {@code null} no fim. */
  static Map<String, Object> playWeek(MockMvc mvc, Career c) throws Exception {
    MvcResult r = call(mvc, post(c.base() + "/weeks/current/play"), c.auth(), null);
    if (r.getResponse().getStatus() == 409) return null;
    Map<String, Object> board = ok(r);
    if (board.get("matchId") != null) MatchFixtures.finish(mvc, c.auth(), (String) board.get("matchId"), "FULL");
    return board;
  }

  /** Joga até a final (o {@code /weeks/current/play} responde 409 NO_NEXT_GAME); devolve os painéis das semanas. */
  static List<Map<String, Object>> playToEnd(MockMvc mvc, Career c) throws Exception {
    List<Map<String, Object>> boards = new ArrayList<>();
    for (int guard = 0; guard < 40; guard++) {
      Map<String, Object> b = playWeek(mvc, c);
      if (b == null) return boards;
      boards.add(b);
    }
    throw new AssertionError("a temporada não terminou");
  }
}
