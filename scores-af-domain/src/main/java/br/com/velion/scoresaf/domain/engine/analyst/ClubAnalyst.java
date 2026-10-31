package br.com.velion.scoresaf.domain.engine.analyst;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analista do sistema (o "system AI analyst" do basquete, aqui só por regras e sem provedor externo, D-104), puro e sem sorteio:
 * pontos fortes e fracos por unidade contra a média da liga, os próximos adversários (confrontos-chave e chance de vitória),
 * trocas recomendadas no depth chart e ajustes do plano de jogo para o próximo jogo.
 *
 * <ul>
 *   <li><b>Unidades</b>: QB, linha ofensiva, RB, recebedores (3 WR + TE), linha defensiva, linebackers, secundária e times especiais
 *   (K e P), média do overall dos titulares na vaga. Forte/fraca: ±{@value #EDGE} ou mais da média da liga (até 3 de cada).</li>
 *   <li><b>Força da equipe</b>: média ponderada das unidades (QB 20 %, OL 15 %, RB 5 %, recebedores 15 %, DL 15 %, LB 10 %,
 *   secundária 15 %, especiais 5 %). <b>Chance de vitória</b>: logística da diferença de força, com 1,5 para o mandante (nada em
 *   campo neutro): {@code 1 / (1 + e^(−dif / 5))}.</li>
 *   <li><b>Depth chart</b>: titular superado por um jogador disponível fora da titularidade daquela vaga com overall na vaga
 *   {@value #EDGE} ou mais acima (até 5, maior ganho primeiro, cada jogador uma vez).</li>
 *   <li><b>Plano de jogo</b> (contra o próximo adversário): proporção de passe = 50 + 2 × (vantagem no passe − vantagem na corrida),
 *   de 35 a 70 em passos de 5; foco defensivo pela ameaça maior do adversário (passe ou corrida, diferença de 3); agressividade na
 *   4ª descida pela chance de vitória (abaixo de 40 %: agressivo; acima de 65 %: conservador). Só vira recomendação o que difere do
 *   plano salvo.</li>
 * </ul>
 */
public final class ClubAnalyst {
  private ClubAnalyst() {}

  public static final int EDGE = 3;
  public static final List<String> UNITS = List.of("QB", "OFFENSIVE_LINE", "RUNNING_BACK", "RECEIVERS", "DEFENSIVE_LINE", "LINEBACKERS", "SECONDARY",
      "SPECIAL_TEAMS");
  private static final double[] WEIGHTS = {0.20, 0.15, 0.05, 0.15, 0.15, 0.10, 0.15, 0.05};

  /** Média dos titulares por unidade, na ordem de {@link #UNITS}. */
  public record Units(int qb, int offensiveLine, int runningBack, int receivers, int defensiveLine, int linebackers, int secondary, int specialTeams) {
    public int[] values() { return new int[] {qb, offensiveLine, runningBack, receivers, defensiveLine, linebackers, secondary, specialTeams}; }
    public int overall() {
      int[] v = values();
      double s = 0;
      for (int i = 0; i < v.length; i++) s += v[i] * WEIGHTS[i];
      return (int) Math.round(s);
    }
    public static Units average(List<Units> all) {
      if (all.isEmpty()) return new Units(0, 0, 0, 0, 0, 0, 0, 0);
      int[] sum = new int[8];
      for (Units u : all) { int[] v = u.values(); for (int i = 0; i < 8; i++) sum[i] += v[i]; }
      int n = all.size();
      return new Units(r(sum[0], n), r(sum[1], n), r(sum[2], n), r(sum[3], n), r(sum[4], n), r(sum[5], n), r(sum[6], n), r(sum[7], n));
    }
    private static int r(int s, int n) { return (int) Math.round(s / (double) n); }
  }

  /** Próximo jogo: adversário, semana, mando, campo neutro e as unidades dele. */
  public record Upcoming(String fixtureId, String opponentId, int week, boolean home, boolean neutralSite, Units opponent) {}

  /** Jogador candidato numa vaga: profundidade atual (0 = não está na vaga), overall na vaga e se é titular em outra vaga. */
  public record Candidate(String slot, int depth, String playerId, String name, int overall, boolean starterElsewhere) {}

  /** Vaga do depth chart com o número de titulares e os candidatos (titulares atuais e disponíveis). */
  public record SlotInput(String slot, int starters, List<Candidate> candidates) {}

  /** Plano salvo: proporção de passe, foco defensivo e agressividade na 4ª descida. */
  public record Plan(int passRatio, String defensiveFocus, String fourthDownAggression) {}

  public record Finding(String unit, int rating, int leagueAverage, int diff) {}

  public record Matchup(String key, int ours, int theirs, int edge, String advantage) {}

  public record Outlook(String fixtureId, String opponentId, int week, boolean home, boolean neutralSite, int strength, int opponentStrength,
                        double winProbability, String verdict, List<Matchup> matchups, String biggestThreat) {}

  public record DepthChange(String slot, String inPlayerId, String inName, int inOverall, String outPlayerId, String outName, int outOverall, int gain) {}

  public record PlanAdvice(String field, String current, String recommended, String reason) {}

  public record Report(int strength, int leagueStrength, Units units, Units league, List<Finding> strengths, List<Finding> weaknesses,
                       List<Outlook> upcoming, List<DepthChange> depthChart, List<PlanAdvice> gamePlan, String summary, Map<String, Object> summaryParams) {}

  public static Report analyze(Units ours, List<Units> leagueTeams, List<Upcoming> upcoming, List<SlotInput> chart, Plan plan) {
    Units league = Units.average(leagueTeams);
    int[] o = ours.values(), l = league.values();
    List<Finding> all = new ArrayList<>();
    for (int i = 0; i < UNITS.size(); i++) all.add(new Finding(UNITS.get(i), o[i], l[i], o[i] - l[i]));
    List<Finding> strengths = all.stream().filter(f -> f.diff() >= EDGE).sorted(Comparator.comparingInt(Finding::diff).reversed()).limit(3).toList();
    List<Finding> weaknesses = all.stream().filter(f -> f.diff() <= -EDGE).sorted(Comparator.comparingInt(Finding::diff)).limit(3).toList();
    List<Outlook> outlooks = upcoming.stream().map(u -> outlook(ours, u)).toList();
    List<DepthChange> depth = depthChanges(chart);
    List<PlanAdvice> advice = upcoming.isEmpty() || plan == null ? List.of() : planAdvice(ours, upcoming.get(0), outlooks.get(0), plan);
    Map<String, Object> params = new LinkedHashMap<>();
    String summary;
    if (outlooks.isEmpty()) {
      summary = "SEASON_OVERVIEW";
    } else {
      Outlook next = outlooks.get(0);
      summary = "NEXT_GAME_" + next.verdict();
      params.put("opponentId", next.opponentId());
      params.put("winProbability", next.winProbability());
    }
    Finding best = all.stream().max(Comparator.comparingInt(Finding::diff)).orElse(null);
    Finding worst = all.stream().min(Comparator.comparingInt(Finding::diff)).orElse(null);
    if (best != null) params.put("bestUnit", best.unit());
    if (worst != null) params.put("worstUnit", worst.unit());
    return new Report(ours.overall(), league.overall(), ours, league, strengths, weaknesses, outlooks, depth, advice, summary, params);
  }

  static Outlook outlook(Units ours, Upcoming u) {
    Units t = u.opponent();
    double homeEdge = u.neutralSite() ? 0 : u.home() ? 1.5 : -1.5;
    double diff = ours.overall() - t.overall() + homeEdge;
    double p = Math.round(1000.0 / (1 + Math.exp(-diff / 5.0))) / 1000.0;
    String verdict = p >= 0.6 ? "FAVORITE" : p <= 0.4 ? "UNDERDOG" : "EVEN";
    List<Matchup> m = List.of(
        matchup("PASS_OFFENSE_VS_SECONDARY", avg(ours.qb(), ours.receivers()), t.secondary()),
        matchup("RUN_OFFENSE_VS_FRONT", avg(ours.runningBack(), ours.offensiveLine()), avg(t.defensiveLine(), t.linebackers())),
        matchup("OL_VS_DL", ours.offensiveLine(), t.defensiveLine()),
        matchup("SECONDARY_VS_PASS_OFFENSE", ours.secondary(), avg(t.qb(), t.receivers())),
        matchup("FRONT_VS_RUN_OFFENSE", avg(ours.defensiveLine(), ours.linebackers()), avg(t.runningBack(), t.offensiveLine())));
    int[] tv = t.values();
    int best = 0;
    for (int i = 1; i < tv.length; i++) if (tv[i] > tv[best]) best = i;
    return new Outlook(u.fixtureId(), u.opponentId(), u.week(), u.home(), u.neutralSite(), ours.overall(), t.overall(), p, verdict, m, UNITS.get(best));
  }

  static Matchup matchup(String key, int ours, int theirs) {
    int edge = ours - theirs;
    return new Matchup(key, ours, theirs, edge, edge >= EDGE ? "OURS" : edge <= -EDGE ? "THEIRS" : "EVEN");
  }

  static int avg(int a, int b) { return (int) Math.round((a + b) / 2.0); }

  static List<DepthChange> depthChanges(List<SlotInput> chart) {
    List<DepthChange> out = new ArrayList<>();
    for (SlotInput s : chart) {
      List<Candidate> starters = s.candidates().stream().filter(c -> c.depth() >= 1 && c.depth() <= s.starters())
          .sorted(Comparator.comparingInt(Candidate::depth)).toList();
      List<Candidate> bench = s.candidates().stream().filter(c -> (c.depth() == 0 || c.depth() > s.starters()) && !c.starterElsewhere())
          .sorted(Comparator.comparingInt(Candidate::overall).reversed().thenComparing(Candidate::name).thenComparing(Candidate::playerId)).toList();
      Set<String> usedBench = new HashSet<>();
      for (Candidate st : starters.stream().sorted(Comparator.comparingInt(Candidate::overall).thenComparing(Candidate::name).thenComparing(Candidate::playerId))
          .toList()) {
        for (Candidate b : bench) {
          if (usedBench.contains(b.playerId())) continue;
          if (b.overall() - st.overall() >= EDGE) {
            out.add(new DepthChange(s.slot(), b.playerId(), b.name(), b.overall(), st.playerId(), st.name(), st.overall(), b.overall() - st.overall()));
            usedBench.add(b.playerId());
          }
          break;
        }
      }
    }
    Set<String> seen = new HashSet<>();
    return out.stream().sorted(Comparator.comparingInt(DepthChange::gain).reversed().thenComparing(DepthChange::slot).thenComparing(DepthChange::inName))
        .filter(d -> seen.add(d.inPlayerId())).limit(5).toList();
  }

  static List<PlanAdvice> planAdvice(Units ours, Upcoming next, Outlook outlook, Plan plan) {
    Units t = next.opponent();
    int passEdge = avg(ours.qb(), ours.receivers()) - t.secondary();
    int runEdge = (int) Math.round(ours.runningBack() * 0.4 + ours.offensiveLine() * 0.6 - (t.defensiveLine() * 0.6 + t.linebackers() * 0.4));
    int ratio = 50 + 2 * (passEdge - runEdge);
    ratio = Math.max(35, Math.min(70, (int) Math.round(ratio / 5.0) * 5));
    int passThreat = avg(t.qb(), t.receivers()) - ours.secondary();
    int runThreat = (int) Math.round(t.runningBack() * 0.4 + t.offensiveLine() * 0.6 - (ours.defensiveLine() * 0.6 + ours.linebackers() * 0.4));
    String focus = passThreat - runThreat >= EDGE ? "STOP_PASS" : runThreat - passThreat >= EDGE ? "STOP_RUN" : "BALANCED";
    String aggression = outlook.winProbability() < 0.4 ? "AGGRESSIVE" : outlook.winProbability() > 0.65 ? "CONSERVATIVE" : "NORMAL";
    List<PlanAdvice> out = new ArrayList<>();
    if (Math.abs(ratio - plan.passRatio()) >= 5) {
      out.add(new PlanAdvice("passRatio", String.valueOf(plan.passRatio()), String.valueOf(ratio), passEdge >= runEdge ? "PASS_EDGE" : "RUN_EDGE"));
    }
    if (!focus.equals(plan.defensiveFocus())) {
      out.add(new PlanAdvice("defensiveFocus", plan.defensiveFocus(), focus, focus.equals("STOP_PASS") ? "PASS_THREAT" : focus.equals("STOP_RUN") ? "RUN_THREAT" : "BALANCED_THREAT"));
    }
    if (!aggression.equals(plan.fourthDownAggression())) {
      out.add(new PlanAdvice("fourthDownAggression", plan.fourthDownAggression(), aggression, "WIN_PROBABILITY"));
    }
    return out;
  }
}
