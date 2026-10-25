package br.com.velion.scoresaf.application.meta;

import br.com.velion.scoresaf.domain.game.GameInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Identidade do jogo para o front e para quem monitora a API ({@code GET /api/v1/meta}). */
@Service
public class GetGameInfoUseCase {
  private final GameInfo info;

  public GetGameInfoUseCase(@Value("${scores.app.version:dev}") String version) { this.info = GameInfo.of(version); }

  public GameInfo execute() { return info; }
}
