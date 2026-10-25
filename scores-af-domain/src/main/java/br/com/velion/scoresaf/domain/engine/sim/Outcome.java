package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.PlayResult;
import br.com.velion.scoresaf.domain.engine.model.PlayType;
import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import br.com.velion.scoresaf.domain.shared.PenaltyCode;
import br.com.velion.scoresaf.domain.shared.PlayConcept;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado de uma jogada de scrimmage antes de valer: o motor decide a falta (aceita ou recusada) e só então credita as
 * estatísticas ({@link #deltas}) — jogada anulada por falta aceita não conta para ninguém.
 */
final class Outcome {
  PlayType type;
  PlayConcept concept;
  Formation formation;
  DefensePackage pkg;
  Coverage coverage;
  boolean blitz;

  PlayResult result = PlayResult.GAIN;
  /** Jardas do ataque a partir da linha de scrimmage (sack: negativo). */
  int yards;
  int airYards;
  boolean passAttempt;
  boolean completion;
  boolean incomplete;
  boolean sack;
  boolean scramble;
  boolean rush;
  boolean outOfBounds;
  boolean interception;
  boolean fumbleLost;
  boolean fumble;
  /** Jarda (relativa ao ataque) onde a bola mudou de mãos, já com o retorno da defesa descontado. */
  int turnoverSpot;
  int returnYards;
  int seconds;

  PlayerState passer;
  PlayerState carrier;
  PlayerState receiver;
  PlayerState tackler;
  PlayerState defender;

  // chutes (KickResolver)
  /** A bola fica com o time que chutou (onside recuperado, muff, fumble no retorno, FG/PAT não se aplica). */
  boolean kickingTeamKeeps;
  /** Jarda de partida do time que fica com a bola, relativa a ele. */
  int newYd;
  /** Retorno de chute até a end zone. */
  boolean returnTd;
  /** FG ou PAT convertido. */
  boolean good;

  PenaltyCall penalty;
  /** Grounding no lugar do sack: a falta já vem do resolvedor. */
  boolean groundingForced;
  String key;
  final Map<String, String> params = new LinkedHashMap<>();
  final List<PlayerState> involved = new ArrayList<>();
  final List<Delta> deltas = new ArrayList<>();

  record Delta(PlayerState player, Stat stat, int value) {}

  /** Falta sorteada no lance: código, quem cometeu, jogador e ponto (faltas no local). */
  record PenaltyCall(PenaltyCode code, boolean byDefense, PlayerState player, int spotYd, boolean preSnap) {}

  void credit(PlayerState p, Stat s, int v) { if (p != null) deltas.add(new Delta(p, s, v)); }

  void involve(PlayerState p) { if (p != null && !involved.contains(p)) involved.add(p); }

  void param(String k, Object v) { params.put(k, String.valueOf(v)); }

  void named(String k, PlayerState p) { if (p != null) { params.put(k, p.name()); involve(p); } }

  void apply() { for (Delta d : deltas) d.player().add(d.stat(), d.value()); }
}
