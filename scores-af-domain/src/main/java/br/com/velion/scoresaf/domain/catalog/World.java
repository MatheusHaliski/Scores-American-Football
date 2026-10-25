package br.com.velion.scoresaf.domain.catalog;

import br.com.velion.scoresaf.domain.account.UserAccount;
import br.com.velion.scoresaf.domain.common.AuditableEntity;
import br.com.velion.scoresaf.domain.shared.WorldKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mundo = catálogo (ligas, franquias, jogadores, estádios, head coaches), RF5 editor e RF34 nova carreira. O mundo padrão
 * ({@code ownerUser = null}, TEMPLATE) é só leitura e nasce do {@code DefaultWorldFactory} (determinístico pela semente); o usuário
 * edita uma cópia (TEMPLATE com dono). Ao criar a carreira (bloco 6), o mundo é clonado num SAVE próprio: editar o catálogo nunca
 * corrompe uma carreira em andamento.
 */
@Entity
@Table(name = "world", indexes = {
    @Index(name = "ix_world_owner", columnList = "owner_user_id"),
    @Index(name = "ix_world_kind", columnList = "kind")})
@Getter
@Setter
@NoArgsConstructor
public class World extends AuditableEntity {

  /** Nulo = mundo padrão. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private UserAccount ownerUser;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_world_id")
  private World sourceWorld;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(32)")
  private WorldKind kind = WorldKind.TEMPLATE;

  /** Ano da temporada inicial. */
  @Column(nullable = false)
  private int baseYear = 2026;

  /** Semente que gerou o mundo padrão (mesma semente, mesmo mundo); nas cópias, a da origem. */
  @Column(nullable = false)
  private long seed;

  public boolean isDefaultTemplate() { return kind == WorldKind.TEMPLATE && ownerUser == null; }
}
