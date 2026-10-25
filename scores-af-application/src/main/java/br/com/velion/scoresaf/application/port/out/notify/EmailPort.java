package br.com.velion.scoresaf.application.port.out.notify;

/** E-mail transacional (recuperação de senha, exportação LGPD). Implementação padrão: log (dev). */
public interface EmailPort {
  void send(String toEmail, String subject, String body);
}
