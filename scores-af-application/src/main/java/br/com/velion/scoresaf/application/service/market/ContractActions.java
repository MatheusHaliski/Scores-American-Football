package br.com.velion.scoresaf.application.service.market;

import br.com.velion.scoresaf.application.dto.MarketDtos;
import br.com.velion.scoresaf.application.port.out.persistence.ContractEventRepository;
import br.com.velion.scoresaf.application.port.out.persistence.PlayerRepository;
import br.com.velion.scoresaf.application.service.CapSheet;
import br.com.velion.scoresaf.application.service.MoraleService;
import br.com.velion.scoresaf.application.usecase.support.CareerSupport;
import br.com.velion.scoresaf.domain.career.CareerSave;
import br.com.velion.scoresaf.domain.catalog.LeagueRules;
import br.com.velion.scoresaf.domain.catalog.Player;
import br.com.velion.scoresaf.domain.catalog.PlayerContract;
import br.com.velion.scoresaf.domain.market.ContractDemand;
import br.com.velion.scoresaf.domain.market.ContractEvent;
import br.com.velion.scoresaf.domain.market.ContractMath;
import br.com.velion.scoresaf.domain.season.Season;
import br.com.velion.scoresaf.domain.shared.ContractType;
import br.com.velion.scoresaf.domain.shared.RosterStatus;
import br.com.velion.scoresaf.domain.shared.SeasonPhase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Janelas das ações de contrato da franquia do usuário (RF49, bloco 7a; D-133 a D-135) e o pedido do jogador:
 *
 * <ul>
 *   <li><b>Renovar</b> (D-133): só no último ano do contrato ({@code LeagueRules} não define outra janela), nos 53 ou na IR (o
 *   practice squad volta pela free agency). Motivos: NOT_OWN, NO_CONTRACT, PRACTICE_SQUAD, NOT_LAST_YEAR.</li>
 *   <li><b>Reestruturar</b> (D-134): com a temporada em andamento (não na entressafra), 2+ anos pela frente e salário do ano acima do
 *   mínimo. Motivos: NOT_OWN, NO_CONTRACT, SEASON_COMPLETED, TOO_FEW_YEARS, AT_MINIMUM.</li>
 *   <li><b>Franchise tag</b> (D-135): só na entressafra antes da free agency (a temporada terminou e ainda não avançou), no último ano do
 *   contrato, uma por temporada. Motivos: NOT_OWN, NO_CONTRACT, PRACTICE_SQUAD, NOT_OFFSEASON, NOT_LAST_YEAR, TAG_USED.</li>
 * </ul>
 */
@Component
public class ContractActions {
  private final PlayerRepository players; private final ContractEventRepository events; private final MoraleService morale;

  public ContractActions(PlayerRepository players, ContractEventRepository events, MoraleService morale) {
    this.players = players; this.events = events; this.morale = morale;
  }

  /** Contexto de uma leitura: ano, temporada, se a tag da temporada já foi usada. */
  public record Context(CareerSave save, Season season, int year, boolean completed, boolean offseason, boolean tagUsed, LeagueRules rules) {}

  public Context context(CareerSave save) {
    Season s = save.getSeason();
    boolean completed = s != null && s.isCompleted();
    boolean tagUsed = s != null && save.getClub() != null
        && events.existsBySeasonIdAndClubIdAndKind(s.getId(), save.getClub().getId(), ContractEvent.Kind.FRANCHISE_TAG);
    return new Context(save, s, CareerSupport.seasonYear(save), completed, completed && s.getPhase() == SeasonPhase.OFFSEASON, tagUsed,
        CareerSupport.rules(save));
  }

  private static boolean own(Context c, Player p) {
    return c.save().getClub() != null && p.getClub() != null && p.getClub().getId().equals(c.save().getClub().getId());
  }

  public static String renewBlocked(Context c, Player p) {
    PlayerContract k = p.getContract();
    if (!own(c, p)) return "NOT_OWN";
    if (k == null || k.years() == 0) return "NO_CONTRACT";
    if (p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD || p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD_WAIVERS || k.getType() == ContractType.PRACTICE) {
      return "PRACTICE_SQUAD";
    }
    if (k.endSeason() != c.year()) return "NOT_LAST_YEAR";
    return null;
  }

  public static String restructureBlocked(Context c, Player p) {
    PlayerContract k = p.getContract();
    if (!own(c, p)) return "NOT_OWN";
    if (k == null || k.years() == 0) return "NO_CONTRACT";
    if (c.completed()) return "SEASON_COMPLETED";
    int y = k.yearIndexOf(c.year());
    if (y < 0 || k.years() - y < 2) return "TOO_FEW_YEARS";
    if (ContractMath.maxRestructure(k, y) <= 0) return "AT_MINIMUM";
    return null;
  }

  public static String tagBlocked(Context c, Player p) {
    PlayerContract k = p.getContract();
    if (!own(c, p)) return "NOT_OWN";
    if (k == null || k.years() == 0) return "NO_CONTRACT";
    if (p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD || p.getRosterStatus() == RosterStatus.PRACTICE_SQUAD_WAIVERS || k.getType() == ContractType.PRACTICE) {
      return "PRACTICE_SQUAD";
    }
    if (!c.offseason()) return "NOT_OFFSEASON";
    if (k.endSeason() != c.year()) return "NOT_LAST_YEAR";
    if (c.tagUsed()) return "TAG_USED";
    return null;
  }

  /** Valor da franchise tag do jogador: média dos maiores cap hits da posição na liga (temporada atual), mínimo 120 % do dele. */
  public long tagValue(CareerSave save, Player p) {
    LeagueRules rules = CareerSupport.rules(save);
    int year = CareerSupport.seasonYear(save);
    List<Long> hits = players.findByWorldId(save.getWorld().getId()).stream()
        .filter(x -> x.getClub() != null && x.getRosterStatus().requiresClub() && x.getPosition() == p.getPosition())
        .map(x -> CapSheet.capHit(x, year, rules)).toList();
    return ContractMath.tagValue(hits, CapSheet.capHit(p, year, rules));
  }

  public MarketDtos.ContractActionsDto actions(Context c, Player p) {
    String renew = renewBlocked(c, p), restructure = restructureBlocked(c, p), tag = tagBlocked(c, p);
    long max = restructure == null ? ContractMath.maxRestructure(p.getContract(), p.getContract().yearIndexOf(c.year())) : 0;
    Long tagValue = tag == null ? tagValue(c.save(), p) : null;
    return new MarketDtos.ContractActionsDto(renew == null, renew, restructure == null, restructure, max, tag == null, tag, tagValue, p.isTradeBlock());
  }

  /** Moral e papel de cada jogador da franquia do usuário agora (RF41), para o pedido da renovação. */
  public Map<UUID, Morale> morale(CareerSave save) {
    if (save.getSeason() == null || save.getClub() == null) return Map.of();
    java.util.Map<UUID, Morale> out = new java.util.HashMap<>();
    morale.compute(save).squad().players().forEach(r -> out.put(r.playerId(), new Morale(r.morale(), r.role())));
    return out;
  }

  public record Morale(int morale, String role) {}

  /** O pedido do jogador: renovação (com moral e papel, sem desconto) para o jogador do usuário; agente livre com o desconto do dia. */
  public static ContractDemand.Profile renewalProfile(Player p, Morale m) {
    return MarketService.profile(p, m == null ? p.getMorale() : m.morale(), m == null ? null : m.role());
  }

  /** Mercado do jogador na ficha: valor de mercado, o pedido de agora (se houver) e as ações. */
  public MarketDtos.PlayerMarketDto playerMarket(CareerSave save, Player p, int discountPct) {
    Context c = context(save);
    long market = ContractDemand.marketValue(MarketService.profile(p, null, null));
    MarketDtos.OfferDto asking = null;
    if (own(c, p) && renewBlocked(c, p) == null) asking = MarketAssembler.offer(ContractDemand.ask(renewalProfile(p, morale(save).get(p.getId())), 0));
    else if (p.getRosterStatus() == RosterStatus.FREE_AGENT) asking = MarketAssembler.offer(ContractDemand.ask(MarketService.profile(p, null, null), discountPct));
    return new MarketDtos.PlayerMarketDto(market, asking, own(c, p) ? actions(c, p) : null);
  }
}
