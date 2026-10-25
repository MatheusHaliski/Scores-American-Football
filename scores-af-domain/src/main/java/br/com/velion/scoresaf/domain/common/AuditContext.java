package br.com.velion.scoresaf.domain.common;

/** Quem está agindo e de onde (preenchido pela camada web via filtro). Sem Spring no domínio. */
public final class AuditContext {
  private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();
  private static final ThreadLocal<String> CORRELATION = new ThreadLocal<>();
  private static final ThreadLocal<String> IP = new ThreadLocal<>();
  private static final ThreadLocal<String> USER_AGENT = new ThreadLocal<>();
  private AuditContext() {}
  public static void setActor(String actor) { ACTOR.set(actor); }
  public static String actor() { String a = ACTOR.get(); return a == null ? "system" : a; }
  public static void setCorrelationId(String id) { CORRELATION.set(id); }
  public static String correlationId() { return CORRELATION.get(); }
  public static void setRequest(String ip, String userAgent) { IP.set(ip); USER_AGENT.set(userAgent); }
  public static String ip() { return IP.get(); }
  public static String userAgent() { return USER_AGENT.get(); }
  public static void clear() { ACTOR.remove(); CORRELATION.remove(); IP.remove(); USER_AGENT.remove(); }
}
