# Scores: American Football

Gerente de futebol americano da VELION TECH CO., irmão do Scores (basquete). Mesma arquitetura (Spring Boot multi-módulo +
Next.js com 3D em React Three Fiber), domínio do futebol americano. O plano de construção fica em
[`docs/01-plano-american-football.md`](docs/01-plano-american-football.md).

## Como rodar (esqueleto, bloco 2)

Backend (JDK 21 ou 25, Maven 3.9):

```bash
mvn verify                                   # testes de todos os módulos
mvn -pl scores-af-bootstrap -am spring-boot:run   # API em http://localhost:8080
```

`GET /api/v1/meta` é público e devolve a identidade do jogo; `/actuator/health` é o único endpoint do actuator exposto. A
documentação OpenAPI fica em `/swagger-ui.html`. Qualquer outra rota responde 401 no corpo de erro padrão até o login chegar
no bloco 3.

Frontend (Node 22):

```bash
cd frontend
npm ci
npm run dev                                  # modo demonstração, sem servidor
NEXT_PUBLIC_API_MODE=live npm run dev        # contra a API local
npm run lint && npx tsc --noEmit && npm run i18n:check && npm run test:unit
```

As variáveis de ambiente ficam listadas só pelo nome em [`.env.example`](.env.example).

As amostras douradas em `docs/validation/golden/` são gravadas pelo backend (`ApiGoldenTest`) e lidas pelos testes do front.
Se o formato de uma resposta mudar, regenere com `-Dgolden.update=true` (o comando está no Javadoc do teste).
