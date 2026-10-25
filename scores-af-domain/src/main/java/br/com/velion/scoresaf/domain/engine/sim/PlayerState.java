package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.catalog.AttributeKey;
import br.com.velion.scoresaf.domain.engine.model.PlayerSnapshot;
import br.com.velion.scoresaf.domain.shared.Position;
import java.util.UUID;

/** Jogador durante a partida: atributos congelados, energia (fadiga) e contadores de estatística. */
final class PlayerState {
  final PlayerSnapshot snap;
  final UUID id;
  final Position position;
  final TeamState team;
  private final int[] attrs = new int[AttributeKey.values().length];
  final double moraleBonus;
  final double maxEnergy;
  double energy;
  final int[] stats = new int[Stat.ALL.length];

  PlayerState(PlayerSnapshot snap, TeamState team) {
    this.snap = snap;
    this.id = snap.id();
    this.position = snap.position();
    this.team = team;
    for (AttributeKey k : AttributeKey.values()) attrs[k.ordinal()] = snap.attr(k);
    // moral 60 é neutra; ±40 pontos de moral valem ±2 pontos de atributo
    this.moraleBonus = (snap.morale() - 60) / 20.0;
    // condição física: quem começa cansado tem menos energia
    this.maxEnergy = 60 + 0.4 * snap.condition();
    this.energy = maxEnergy;
  }

  int raw(AttributeKey k) { return attrs[k.ordinal()]; }

  /** Atributo efetivo na jogada: cansaço tira até 10 %, moral soma ou tira até 2 pontos. */
  double a(AttributeKey k) { return attrs[k.ordinal()] * (0.90 + 0.10 * energy / 100.0) + moraleBonus; }

  void add(Stat s, int v) {
    if (s.isMax()) stats[s.ordinal()] = Math.max(stats[s.ordinal()], v);
    else stats[s.ordinal()] += v;
  }

  int get(Stat s) { return stats[s.ordinal()]; }

  String name() { return snap.name(); }

  /** Custo de energia de um snap, pela posição e pela resistência. */
  void tire() {
    double base = switch (position.group()) {
      case QB -> 0.3; case RB -> 2.6; case FB -> 1.5; case WR -> 1.5; case TE -> 1.3; case OL -> 0.8; case DL -> 2.5; case LB -> 1.5;
      case CB -> 1.4; case S -> 1.2; default -> 0.5;
    };
    energy = Math.max(30, energy - base * (1.3 - 0.6 * raw(AttributeKey.STAMINA) / 100.0));
  }

  void rest(double amount) { energy = Math.min(maxEnergy, energy + amount); }
}
