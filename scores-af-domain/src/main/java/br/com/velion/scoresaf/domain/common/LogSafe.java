package br.com.velion.scoresaf.domain.common;

import java.util.regex.Pattern;

/**
 * O que pode ir para o log. Log nunca leva senha, token ou código; e-mail vai sempre mascarado.
 * Use {@link #maskEmail} em todo log, auditoria ou texto que cite um e-mail, e {@link #redact} antes de registrar um
 * texto livre que possa conter um link com token ou um código de verificação.
 */
public final class LogSafe {
  /** Valor de parâmetro sensível em URL ou texto: {@code token=…}, {@code code=…}, {@code password=…} etc. */
  private static final Pattern SECRET_PARAM =
      Pattern.compile("(?i)\\b(token|code|codigo|código|password|senha|secret|key)=([^\\s&\"'<>]+)");
  /** JWT (cabeçalho Base64URL começa com {@code eyJ}), sequências longas com cara de token e códigos de 4 a 8 dígitos. */
  private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+(\\.[A-Za-z0-9_\\-]*)?");
  private static final Pattern OPAQUE = Pattern.compile("\\b[A-Za-z0-9_\\-]{24,}(\\.[A-Za-z0-9_\\-]+){0,2}\\b");
  private static final Pattern NUMERIC_CODE = Pattern.compile("\\b\\d{4,8}\\b");
  public static final String REDACTED = "[omitido]";

  private LogSafe() {}

  /** {@code ana.souza@exemplo.com} → {@code a***@e***.com}. Nulo ou inválido → {@code ***}. */
  public static String maskEmail(String email) {
    if (email == null) return "***";
    String e = email.trim();
    int at = e.lastIndexOf('@');
    if (at < 1 || at == e.length() - 1) return "***";
    String domain = e.substring(at + 1);
    int dot = domain.lastIndexOf('.');
    String host = dot > 0 ? domain.substring(0, dot) : domain;
    String tld = dot > 0 ? domain.substring(dot) : "";
    return e.charAt(0) + "***@" + host.charAt(0) + "***" + tld;
  }

  /** Troca parâmetros sensíveis, sequências opacas e códigos numéricos por {@value #REDACTED}. */
  public static String redact(String text) {
    if (text == null) return null;
    String out = SECRET_PARAM.matcher(text).replaceAll("$1=" + REDACTED);
    out = JWT.matcher(out).replaceAll(REDACTED);
    out = OPAQUE.matcher(out).replaceAll(REDACTED);
    return NUMERIC_CODE.matcher(out).replaceAll(REDACTED);
  }
}
