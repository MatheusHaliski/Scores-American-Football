package br.com.velion.scoresaf.web.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Corrida entre duas escritas (revisão do bloco 6b, D-91): a chave única que a segunda transação ainda bata (H2/PostgreSQL 23505,
 * MySQL 1062, dentro ou não do lote do Hibernate) e a trava que ela não consiga pegar viram 409 {@code CONCURRENT_UPDATE}; as outras
 * violações de integridade (chave estrangeira, não nulo) seguem como erro interno, sem serem engolidas.
 */
class ApiExceptionHandlerConcurrencyTest {
  @RestController
  static class Boom {
    static Supplier<RuntimeException> next;
    @GetMapping("/boom")
    String boom() { throw next.get(); }
  }

  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Boom()).setControllerAdvice(new ApiExceptionHandler(new StaticMessageSource())).build();

  private void expect(RuntimeException e, int status, String code) throws Exception {
    Boom.next = () -> e;
    mvc.perform(get("/boom")).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
  }

  private static DataIntegrityViolationException integrity(SQLException cause) {
    return new DataIntegrityViolationException("could not execute statement", cause);
  }

  @Test
  void chave_unica_repetida_vira_409_concurrent_update() throws Exception {
    expect(integrity(new SQLException("Unique index or primary key violation", "23505", 23505)), 409, "CONCURRENT_UPDATE");
    expect(integrity(new SQLException("Duplicate entry 'x' for key 'ux_match_session_fixture'", "23000", 1062)), 409, "CONCURRENT_UPDATE");
    // lote do Hibernate: a violação vem como a próxima exceção do BatchUpdateException
    BatchUpdateException batch = new BatchUpdateException("could not execute batch", "HY000", 0, new int[0]);
    batch.setNextException(new SQLException("Duplicate entry", "23000", 1062));
    expect(integrity(batch), 409, "CONCURRENT_UPDATE");
    expect(new DuplicateKeyException("duplicada"), 409, "CONCURRENT_UPDATE");
  }

  @Test
  void trava_nao_obtida_vira_409_concurrent_update() throws Exception {
    expect(new CannotAcquireLockException("Timeout trying to lock table"), 409, "CONCURRENT_UPDATE");
  }

  @Test
  void outras_violacoes_de_integridade_continuam_erro_interno() throws Exception {
    expect(integrity(new SQLException("Referential integrity constraint violation", "23506", 23506)), 500, "INTERNAL");
    expect(integrity(new SQLException("Cannot add or update a child row: a foreign key constraint fails", "23000", 1452)), 500, "INTERNAL");
    expect(integrity(new SQLException("Column 'x' cannot be null", "23000", 1048)), 500, "INTERNAL");
    expect(new DataIntegrityViolationException("sem causa SQL"), 500, "INTERNAL");
  }
}
