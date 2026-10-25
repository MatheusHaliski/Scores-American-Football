package br.com.velion.scoresaf.application.security;

import br.com.velion.scoresaf.application.common.exception.AuthBusyException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Único ponto que calcula hash de senha (Argon2id, bloco 3). Um semáforo limita os hashes simultâneos: cada um usa ~19 MiB e
 * segura uma conexão do banco enquanto a transação está aberta. Sem o teto, uma rajada de logins esgotava o pool e virava
 * HTTP 500. Permits = max(1, min(max(2, núcleos), pool do banco / 2)); quem espera mais de 2 s recebe 503 AUTH_BUSY.
 */
@Component
public class PasswordHasher {
  /** Hash Argon2id válido de uma senha aleatória: usado para gastar o mesmo tempo quando a conta não existe. */
  static final String DUMMY_HASH = "$argon2id$v=19$m=19456,t=2,p=1$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

  private final PasswordEncoder encoder;
  private final Semaphore permits;
  private final int maxConcurrent;
  private final long waitMillis;
  private final AtomicLong evaluations = new AtomicLong();

  public PasswordHasher(PasswordEncoder encoder,
      @Value("${scores.security.hash.max-concurrent:0}") int configured,
      @Value("${spring.datasource.hikari.maximum-pool-size:10}") int dbPool,
      @Value("${scores.security.hash.wait-ms:2000}") long waitMillis) {
    this.encoder = encoder;
    this.maxConcurrent = configured > 0 ? configured : defaultPermits(Runtime.getRuntime().availableProcessors(), dbPool);
    this.permits = new Semaphore(maxConcurrent, true);
    this.waitMillis = waitMillis;
  }

  static int defaultPermits(int cores, int dbPool) { return Math.max(1, Math.min(Math.max(2, cores), dbPool / 2)); }

  public boolean matches(String raw, String hash) {
    return guarded(() -> encoder.matches(raw == null ? "" : raw, hash));
  }

  public String encode(String raw) { return guarded(() -> encoder.encode(raw)); }

  /** Mesmo custo de um login com conta existente, para a resposta não revelar se o e-mail tem conta. */
  public void spendDummy(String raw) { matches(raw, DUMMY_HASH); }

  /** Parâmetros do Argon2id mudaram desde que o hash foi gerado: regravar no próximo login correto. */
  public boolean needsRehash(String hash) { return encoder.upgradeEncoding(hash); }

  public int maxConcurrent() { return maxConcurrent; }

  /** Quantos hashes foram calculados (testes de concorrência). */
  public long evaluations() { return evaluations.get(); }

  private <T> T guarded(java.util.function.Supplier<T> work) {
    boolean acquired;
    try { acquired = permits.tryAcquire(waitMillis, TimeUnit.MILLISECONDS); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AuthBusyException(); }
    if (!acquired) throw new AuthBusyException();
    try { evaluations.incrementAndGet(); return work.get(); }
    finally { permits.release(); }
  }
}
