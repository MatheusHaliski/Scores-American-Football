package br.com.velion.scoresaf.application.common.exception;

/** Arquivo enviado com formato/assinatura não aceitos ou acima do limite — 415/413 conforme {@link #tooLarge()}. */
public class UnsupportedMediaException extends ScoresException {
  private final boolean tooLarge;
  public UnsupportedMediaException(String code, String message, boolean tooLarge, Object... args) { super(code, message, args); this.tooLarge = tooLarge; }
  public boolean tooLarge() { return tooLarge; }
}
