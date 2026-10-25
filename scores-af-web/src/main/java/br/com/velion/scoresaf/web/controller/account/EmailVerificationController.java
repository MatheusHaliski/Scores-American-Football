package br.com.velion.scoresaf.web.controller.account;

import br.com.velion.scoresaf.application.usecase.rf46.EmailChangeUseCases;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF46 CA02 — confirmação da posse do novo e-mail pelo link recebido (público: o token é a prova). */
@RestController
@RequestMapping("/api/v1/auth/email")
@Tag(name = "RF46 — Verificação de e-mail")
public class EmailVerificationController {
  public record VerifyRequest(@NotBlank String token) {}
  private final EmailChangeUseCases emailChange;
  public EmailVerificationController(EmailChangeUseCases emailChange) { this.emailChange = emailChange; }

  @PostMapping("/verify") @Operation(summary = "RF46 CA02 — Confirmar novo e-mail (Art. 7º, V)")
  public ResponseEntity<Void> verify(@Valid @RequestBody VerifyRequest r) { emailChange.verify(r.token()); return ResponseEntity.noContent().build(); }
}
