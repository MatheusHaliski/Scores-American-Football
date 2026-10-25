package br.com.velion.scoresaf.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI: todo endpoint documenta no summary o RF que o originou (ex.: "RF40 — Central do clube"). */
@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI scoresAfOpenApi() {
    return new OpenAPI()
        .info(new Info().title("Scores: American Football API — VELION TECH CO.").version("v1")
            .description("Gerente de futebol americano. Contrato REST consumido pelo frontend Next.js. "
                + "Cada operação cita o requisito funcional (RF) que a originou (docs/01 §4)."))
        .components(new Components().addSecuritySchemes("bearer",
            new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearer"));
  }
}
