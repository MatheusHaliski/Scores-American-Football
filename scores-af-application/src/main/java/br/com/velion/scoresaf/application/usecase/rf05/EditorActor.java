package br.com.velion.scoresaf.application.usecase.rf05;

import java.util.UUID;

/** Quem chama o editor (RF5): o usuário autenticado e se ele tem o papel ADMIN (D-21). */
public record EditorActor(UUID userId, boolean admin) {
  public static EditorActor user(UUID userId) { return new EditorActor(userId, false); }
}
