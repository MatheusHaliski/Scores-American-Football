package br.com.velion.scoresaf.domain.engine.ratings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.catalog.PlayerAttributes;
import br.com.velion.scoresaf.domain.shared.Position;
import br.com.velion.scoresaf.domain.shared.PositionFamily;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.junit.jupiter.api.Test;

/**
 * Vetor de ouro Java ↔ TS do overall (D-5): vários jogadores × as 20 posições → overall, nível do card, aviso de fora de posição e
 * os macros de cada família, calculados por {@link OverallCalculator} e {@link MacroRatings}. O teste do front
 * ({@code frontend/tests/ratings/overall-golden.test.mts}) lê o MESMO arquivo e exige igualdade com o port em
 * {@code frontend/src/lib/ratings}: se os pesos mudarem de um lado só, um dos dois fica vermelho.
 *
 * <p>O arquivo é gravado pelo Java. Mudou a regra de propósito (nova versão de pesos)? Regenere com
 * {@code mvn -pl scores-af-domain test -Dtest=OverallGoldenVectorsTest -Dgolden.update=true} e rode {@code npm run test:unit}.
 */
class OverallGoldenVectorsTest {
  static final Path GOLDEN = Path.of("..", "docs", "validation", "golden", "overall-vectors.json");

  private record Case(String id, Position natural, Position secondary, PlayerAttributes attributes) {}

  static List<Case> cases() {
    List<Case> out = new ArrayList<>();
    Position[] all = Position.values();
    int[] highs = {92, 88, 79, 71, 66, 84, 77, 90, 63, 75, 86, 69, 81, 95, 73, 67, 89, 80, 74, 85};
    for (int i = 0; i < all.length; i++) {
      Position p = all[i];
      Position secondary = switch (p) {
        case CB -> Position.FS; case LT -> Position.RT; case DE -> Position.OLB; case RB -> Position.WR; default -> null;
      };
      out.add(new Case(p.name() + "-specialist", p, secondary, RatingProfiles.noisySpecialist(p, highs[i], 38, 7, 20260 + i)));
    }
    for (int v : new int[] {0, 64, 65, 74, 75, 84, 85, 89, 90, 100}) out.add(new Case("uniform-" + v, Position.QB, null, PlayerAttributes.uniform(v)));
    out.add(new Case("half-up-QB", Position.QB, null, PlayerAttributes.uniform(50).with(AttributeKey.SPEED, 70).with(AttributeKey.AGILITY, 55)));
    out.add(new Case("athlete-WR-CB", Position.WR, Position.CB, RatingProfiles.noisySpecialist(Position.WR, 82, 70, 4, 7L)));
    return out;
  }

  static String render() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"note\": \"Vetor de ouro do overall por posição (OVR v1), macros por família e nível do card. Gravado por OverallGoldenVectorsTest (Java); lido por frontend/tests/ratings/overall-golden.test.mts.\",\n");
    sb.append("  \"version\": \"").append(OverallCalculator.CURRENT.version()).append("\",\n");
    StringJoiner order = new StringJoiner(", ", "[", "]");
    for (AttributeKey k : AttributeKey.values()) order.add("\"" + k.key() + "\"");
    sb.append("  \"attributeOrder\": ").append(order).append(",\n");
    sb.append("  \"cases\": [\n");
    List<Case> cs = cases();
    for (int c = 0; c < cs.size(); c++) {
      Case k = cs.get(c);
      sb.append("    {\n");
      sb.append("      \"id\": \"").append(k.id()).append("\",\n");
      sb.append("      \"naturalPosition\": \"").append(k.natural()).append("\",\n");
      sb.append("      \"secondaryPosition\": ").append(k.secondary() == null ? "null" : "\"" + k.secondary() + "\"").append(",\n");
      sb.append("      \"attributes\": {\n");
      Map<String, Map<String, Integer>> grouped = k.attributes().asGroupedMap();
      int g = 0;
      for (Map.Entry<String, Map<String, Integer>> e : grouped.entrySet()) {
        StringJoiner vals = new StringJoiner(", ", "{", "}");
        e.getValue().forEach((name, v) -> vals.add("\"" + name + "\": " + v));
        sb.append("        \"").append(e.getKey()).append("\": ").append(vals).append(++g < grouped.size() ? ",\n" : "\n");
      }
      sb.append("      },\n");
      sb.append("      \"macros\": {\n");
      PositionFamily[] fams = PositionFamily.values();
      for (int f = 0; f < fams.length; f++) {
        StringJoiner vals = new StringJoiner(", ", "{", "}");
        MacroRatings.of(k.attributes(), fams[f]).values().forEach((code, v) -> vals.add("\"" + code + "\": " + v));
        sb.append("        \"").append(fams[f]).append("\": ").append(vals).append(f + 1 < fams.length ? ",\n" : "\n");
      }
      sb.append("      },\n");
      sb.append("      \"ratings\": [\n");
      Position[] ps = Position.values();
      for (int i = 0; i < ps.length; i++) {
        PositionRating r = OverallCalculator.rate(k.attributes(), k.natural(), k.secondary(), ps[i]);
        sb.append("        {\"position\": \"").append(ps[i]).append("\", \"overall\": ").append(r.overall()).append(", \"tier\": \"").append(r.tier())
            .append("\", \"outOfPosition\": ").append(r.outOfPosition()).append("}").append(i + 1 < ps.length ? ",\n" : "\n");
      }
      sb.append("      ]\n");
      sb.append("    }").append(c + 1 < cs.size() ? ",\n" : "\n");
    }
    sb.append("  ]\n}\n");
    return sb.toString();
  }

  @Test
  void vetor_de_ouro_bate_com_o_calculo_java() throws Exception {
    String now = render();
    if (Boolean.getBoolean("golden.update") || "1".equals(System.getenv("GOLDEN_UPDATE"))) {
      Files.createDirectories(GOLDEN.getParent());
      Files.writeString(GOLDEN, now);
    }
    assertThat(GOLDEN).as("vetor de ouro (rodar a partir do módulo; regenerar com -Dgolden.update=true)").exists();
    if (!Files.readString(GOLDEN).replace("\r\n", "\n").equals(now)) {
      fail("docs/validation/golden/overall-vectors.json diverge do OverallCalculator: regenere com -Dgolden.update=true e rode o teste do front");
    }
  }

  @Test
  void o_vetor_cobre_todas_as_posicoes_niveis_e_o_aviso_de_fora_de_posicao() {
    List<Case> cs = cases();
    assertThat(cs).hasSizeGreaterThanOrEqualTo(30);
    assertThat(cs.stream().map(Case::natural).distinct()).hasSize(Position.values().length);
    List<CardTier> tiers = new ArrayList<>();
    for (Case k : cs) tiers.add(CardTier.of(OverallCalculator.overall(k.attributes(), k.natural())));
    assertThat(tiers).contains(CardTier.values());
  }
}
