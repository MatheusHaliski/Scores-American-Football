package br.com.velion.scoresaf.domain.engine.sim;

import br.com.velion.scoresaf.domain.engine.model.DepthSlot;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import java.util.ArrayList;
import java.util.List;

/**
 * Os 11 de cada lado na jogada: o pessoal do ataque sai da formação (I = 2 RB/1 TE/2 WR, Singleback e Shotgun = 1/1/3, Pistol =
 * 1/2/2, Empty = 0/1/4, Goal line = 2/2/1) e o da defesa do pacote. Titulares pelo depth chart, com rodízio por fadiga em RB, WR e
 * linha defensiva; vaga sem ninguém cai para a posição vizinha (WR → TE → RB, CB → S, DT → DE, MLB ↔ OLB…).
 */
final class Lineups {
  private Lineups() {}

  record Offense(PlayerState qb, PlayerState rb, PlayerState fb, List<PlayerState> te, List<PlayerState> wr, List<PlayerState> ol,
                 List<PlayerState> all) {
    /** Recebedores elegíveis, na ordem de prioridade dos alvos (WR1, WR2, …, TE, RB, FB). */
    List<PlayerState> receivers() {
      List<PlayerState> out = new ArrayList<>(wr);
      out.addAll(te);
      if (rb != null) out.add(rb);
      if (fb != null) out.add(fb);
      return out;
    }
  }

  record Defense(DefensePackage pkg, List<PlayerState> dl, List<PlayerState> lb, List<PlayerState> cb, List<PlayerState> s, List<PlayerState> all) {
    List<PlayerState> backs() { List<PlayerState> out = new ArrayList<>(cb); out.addAll(s); return out; }
    List<PlayerState> front() { List<PlayerState> out = new ArrayList<>(dl); out.addAll(lb); return out; }
  }

  static Offense offense(TeamState t, Formation f) {
    int rbs = switch (f) { case EMPTY -> 0; default -> 1; };
    boolean fullback = f == Formation.I_FORM || f == Formation.GOAL_LINE;
    int tes = switch (f) { case PISTOL, GOAL_LINE -> 2; default -> 1; };
    int wrs = switch (f) { case I_FORM, PISTOL -> 2; case EMPTY -> 4; case GOAL_LINE -> 1; default -> 3; };
    List<PlayerState> used = new ArrayList<>();
    PlayerState qb = take(t, used, false, DepthSlot.QB);
    List<PlayerState> ol = new ArrayList<>();
    for (DepthSlot s : new DepthSlot[] {DepthSlot.LT, DepthSlot.LG, DepthSlot.C, DepthSlot.RG, DepthSlot.RT}) ol.add(take(t, used, false, s));
    PlayerState rb = rbs > 0 ? take(t, used, true, DepthSlot.RB, DepthSlot.FB, DepthSlot.WR) : null;
    PlayerState fb = fullback ? take(t, used, false, DepthSlot.FB, DepthSlot.RB, DepthSlot.TE) : null;
    List<PlayerState> te = new ArrayList<>();
    for (int i = 0; i < tes; i++) te.add(take(t, used, false, DepthSlot.TE, DepthSlot.FB, DepthSlot.WR));
    List<PlayerState> wr = new ArrayList<>();
    for (int i = 0; i < wrs; i++) wr.add(take(t, used, true, DepthSlot.WR, DepthSlot.TE, DepthSlot.RB));
    List<PlayerState> all = new ArrayList<>(used);
    return new Offense(qb, rb, fb, te, wr, ol, all);
  }

  static Defense defense(TeamState t, DefensePackage pkg) {
    int de = 2, dt, olb, mlb, cb, s = 2;
    switch (pkg) {
      case BASE_4_3 -> { dt = 2; olb = 2; mlb = 1; cb = 2; }
      case BASE_3_4 -> { dt = 1; olb = 2; mlb = 2; cb = 2; }
      case NICKEL -> { dt = 2; olb = 1; mlb = 1; cb = 3; }
      case DIME -> { dt = 2; olb = 0; mlb = 1; cb = 4; }
      default -> { dt = 3; olb = 2; mlb = 1; cb = 2; s = 1; }
    }
    List<PlayerState> used = new ArrayList<>();
    List<PlayerState> dl = new ArrayList<>();
    for (int i = 0; i < de; i++) dl.add(take(t, used, true, DepthSlot.DE, DepthSlot.DT, DepthSlot.OLB));
    for (int i = 0; i < dt; i++) dl.add(take(t, used, true, DepthSlot.DT, DepthSlot.DE, DepthSlot.MLB));
    List<PlayerState> lb = new ArrayList<>();
    for (int i = 0; i < olb; i++) lb.add(take(t, used, false, DepthSlot.OLB, DepthSlot.MLB, DepthSlot.DE));
    for (int i = 0; i < mlb; i++) lb.add(take(t, used, false, DepthSlot.MLB, DepthSlot.OLB, DepthSlot.SS));
    List<PlayerState> cbs = new ArrayList<>();
    for (int i = 0; i < cb; i++) cbs.add(take(t, used, false, DepthSlot.CB, DepthSlot.FS, DepthSlot.SS));
    List<PlayerState> ss = new ArrayList<>();
    if (s >= 2) ss.add(take(t, used, false, DepthSlot.FS, DepthSlot.SS, DepthSlot.CB));
    ss.add(take(t, used, false, DepthSlot.SS, DepthSlot.FS, DepthSlot.CB));
    return new Defense(pkg, dl, lb, cbs, ss, new ArrayList<>(used));
  }

  /** Primeiro disponível nas vagas, em ordem; se todas estiverem esgotadas, qualquer jogador ainda não escolhido. */
  private static PlayerState take(TeamState t, List<PlayerState> used, boolean rotate, DepthSlot... slots) {
    for (int i = 0; i < slots.length; i++) {
      PlayerState p = rotate && i == 0 ? t.rotate(slots[i], used) : t.first(slots[i], used);
      if (p != null) { used.add(p); return p; }
    }
    for (PlayerState p : t.players) if (!used.contains(p)) { used.add(p); return p; }
    throw new IllegalStateException("elenco de " + t.name() + " não tem 11 jogadores");
  }
}
