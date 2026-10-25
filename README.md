# Scores: American Football

Gerente de futebol americano da VELION TECH CO., irmão do Scores (basquete). Mesma arquitetura (Spring Boot multi-módulo +
Next.js com 3D em React Three Fiber), domínio do futebol americano. O plano de construção fica em
[`docs/01-plano-american-football.md`](docs/01-plano-american-football.md).

## Como rodar (plataforma comum, bloco 3)

Backend (JDK 21 ou 25, Maven 3.9):

```bash
mvn verify                                   # testes de todos os módulos (H2 em memória)
mvn -Pit verify                              # + testes com MySQL 8 de verdade (Testcontainers, exige Docker)
mvn -pl scores-af-bootstrap -am spring-boot:run   # API em http://localhost:8080 (perfil dev: H2, chaves efêmeras)
```

`GET /api/v1/meta` é público e devolve a identidade do jogo; `/actuator/health` é o único endpoint do actuator exposto. A
documentação OpenAPI fica em `/swagger-ui.html`. A plataforma comum (bloco 3) traz cadastro, login com JWT RSA e refresh rotativo
em cookie `__Host-` HttpOnly (família de sessão), conta, configurações (RF10 com duração do quarto e nível de chamada de jogada),
LGPD (privacidade, consentimentos, exportação, exclusão com prazo, troca de e-mail, atividade), auditoria e limites por IP em
`/api/v1/auth/**` e `/api/v1/me/**`. O banco é MySQL 8 com Flyway (`V1__plataforma.sql`) no perfil `mysql`; em produção,
`SPRING_PROFILES_ACTIVE=mysql,prod`, e o `ProductionConfigGuard` não deixa subir sem os segredos.

Domínio do futebol americano (bloco 4): regras da liga em `LeagueRules` (PRO e COLLEGE), 20 posições, 49 subatributos, overall
por posição com pesos versionados ("OVR v1", `OverallWeights`), macros do card por família e níveis Bronze → Elite. Na subida, o
mundo padrão fictício (1 liga, 32 franquias em 2 conferências × 4 divisões, 53 + 16 jogadores por franquia) é gravado se ainda não
existir — determinístico pela semente; desligue com `SCORES_SEED_ENABLED=false`. O editor de mundos (RF5) fica em
`/api/v1/worlds/**`, `/api/v1/leagues/**`, `/api/v1/clubs/**` e `/api/v1/players/**`; o mundo padrão é só leitura, o usuário edita uma
cópia. O banco ganha `V2__dominio_af.sql`.

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
Se o formato de uma resposta mudar, regenere com `-Dgolden.update=true` (o comando está no Javadoc do teste). O vetor de ouro do
overall (`overall-vectors.json`) é gravado por `OverallGoldenVectorsTest` (scores-af-domain) e prende `frontend/src/lib/ratings` ao
cálculo Java: `mvn -pl scores-af-domain test -Dtest=OverallGoldenVectorsTest -Dgolden.update=true`, depois `npm run test:unit`.
