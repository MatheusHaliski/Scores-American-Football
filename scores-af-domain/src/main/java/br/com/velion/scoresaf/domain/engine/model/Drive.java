package br.com.velion.scoresaf.domain.engine.model;

import java.util.UUID;

/**
 * Um drive (posse de bola em scrimmage). Jardas absolutas como em {@link PlayEvent}; {@code yards} é o avanço líquido do ataque
 * (faltas incluídas), {@code plays} os snaps (faltas antes do snap não contam).
 */
public record Drive(int seq, UUID clubId, int quarter, int startClockSeconds, int startAbsYd, int endAbsYd, int plays, int yards,
                    int timeOfPossessionSeconds, boolean reachedRedZone, DriveResult result) {}
