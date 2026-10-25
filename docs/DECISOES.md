# Decisões e perguntas — Scores: American Football

Decisões técnicas numeradas a partir de D-1. Perguntas ao PO a partir de Q-1. Uma decisão nunca é apagada; se mudar, ganha um
número novo que diz qual substitui.

## Decisões

### D-1 — Nomes de pacote e módulos
Pacote Java `br.com.velion.scoresaf` (Java não aceita hífen) e módulos Maven `scores-af-domain`, `scores-af-application`,
`scores-af-web`, `scores-af-infrastructure`, `scores-af-bootstrap`. O front continua em `frontend/`. A marca na interface é
"SCORES" com o subtítulo "American Football".

### D-2 — Numeração dos requisitos alinhada ao basquete
RF1–RF59 e RNF1–RNF13 têm o mesmo número e a mesma função do Scores de basquete. Assim, uma correção em um jogo se acha no outro
pelo número. Requisitos que só existem no futebol americano começam em RF60 (docs/01 §4).

### D-3 — Cópia sem histórico, plataforma primeiro
O código do basquete entra por cópia, sem o histórico git, em duas levas: o esqueleto (bloco 2) e a plataforma comum (bloco 3).
Nada do domínio do basquete (motor, posições, atributos, telas de partida) entra no repositório. O domínio do futebol americano
nasce no bloco 4 com a mesma estrutura de pacotes, para os dois jogos continuarem comparáveis.

### D-4 — Regras sempre em `LeagueRules`
Toda regra do esporte é um campo de `LeagueRules`, versionado por liga: duração do quarto, prorrogação, relógio de jogada,
tempos técnicos, kickoff, ponto extra, variante college. A resposta da Q-1 muda só os valores padrão e o mundo semente.

### D-5 — Lições do basquete valem desde o primeiro dia
1. Todo DTO lido pelo front tem uma amostra dourada gerada pelo backend com o mesmo mapper da API, e o adaptador do front é
   testado contra ela.
2. Um só vocabulário do motor até a tela (status, eventos, táticas). Onde houver tradução, ela tem teste.
3. Sessão com refresh token em cookie `__Host-` HttpOnly e access token só em memória desde o bloco 3.
4. Nenhuma credencial no código, nem de teste. Gitleaks roda antes de todo push, e o resultado é conferido.

### D-6 — Seis macros no card por família de posição
O card mostra seis macros escolhidos pela família da posição (QB; corredores e recebedores; linha ofensiva; defesa; chutadores),
listados no docs/01 §3.4. Os subatributos completos ficam no verso e no modal. Um card de QB e um de CB não comparam os mesmos
seis números, mas o overall é comparável porque cada posição tem seus pesos.

### D-7 — Diagramas por requisito em `docs/RF<n>/`
O gerador do basquete (`docs/diagramas/generator` de lá) vem para `docs/generator/` e grava direto em `docs/RF<n>/` e
`docs/RNF<n>/`. Cada pasta tem os diagramas de atividades, classes, componentes, sequência e máquina de estados, em `.puml`
(fonte) e `.svg`, mais um `README.md` com a história de usuário, os endpoints, as entidades e os pontos de decisão.
O modelo (`docs/generator/model/*.py`) é a fonte única. Os diagramas nunca são editados à mão: muda o modelo e roda o gerador.

### D-8 — Infraestrutura entra quando é usada pela primeira vez
O esqueleto (bloco 2) só tem domain, application, web e bootstrap. Cada adaptador do basquete (`persistence-mysql`,
`persistence-cassandra`, `cache-redis`, `search-opensearch`, `storage-s3`, `ai-providers`) entra no bloco do primeiro caso de uso que
precisa dele, dentro de `scores-af-infrastructure/` (docs/02 §3). O perfil Maven `it` já existe para os testes com banco real.

### D-9 — Replicar a estrutura do basquete pelo mapa `docs/02`
Pedido do PO (25/10): o futebol americano tem as mesmas telas, rotas, funções e arquivos de backend do basquete.
`docs/02-mapa-estrutura-basquete-af.md` lista, a partir do código do basquete, cada rota, seção, endpoint (149), pacote, módulo e
pasta de componentes, com o equivalente no futebol americano e o bloco em que entra. A ordem dos blocos (docs/01 §5) e a regra de
um PR por bloco continuam. O mapa é atualizado quando o basquete ganhar algo novo.

### D-10 — Ordem da offseason
Fim de temporada (RF35) na ordem da liga profissional: playoffs → renovações e franchise tag → free agency → draft → pré-temporada,
usando as fases de `Season.phase`. O simulador do calendário (RF36) para em cada uma.

### D-11 — Tipos de setor do estádio
`StadiumSectorType` = LOWER_BOWL, UPPER_BOWL, SIDELINE_CLUB, END_ZONE, LUXURY_SUITES (RF12, RF31), com política de ingressos por
setor e carnê de temporada no `Stadium`.

## Perguntas para o PO

### Q-1 — Que regras o jogo segue?
Opções: (a) liga fictícia com regras do futebol americano profissional (proposta, padrão do plano); (b) regras college; (c) as
duas, escolhidas na criação da liga. As duas variantes já cabem em `LeagueRules` (D-4).

### Q-2 — Quanto o manager controla durante a partida?
Proposta: plano de jogo antes da partida, ajustes entre quartos e no intervalo, e chamada de jogada só nos momentos decisivos
(4ª descida, últimos 2 minutos, prorrogação). Chamar todas as jogadas seria um modo opcional, mais longo. Confirma, ou prefere
outro nível?

### Q-3 — Tamanho e formato da liga
Proposta: 32 equipes em 2 conferências × 4 divisões, configurável de 8 a 32 como no basquete. Temporada de 17 semanas com 1 de
folga e playoffs com 7 por conferência. Confirma?

### Q-4 — Licença de nomes reais
Sem licença, nomes de atletas, clubes, ligas e marcas são todos fictícios. Isso inclui o nome do jogo final e logos parecidos com
os de ligas reais. Existe ou existirá licença?

### Q-5 — Conta e Scores Points compartilhados entre os jogos Scores?
A mesma conta Velion serve para os dois jogos? A carteira de Scores Points e o Ultimate Team são compartilhados ou separados?
Isso decide se a plataforma comum (bloco 3) vira um serviço único ou é copiada.

### Q-6 — Infraestrutura
Mesmo projeto no Railway e no GCP do basquete, ou projetos separados? Bancos separados em qualquer caso, proposto.

### Q-7 — Limites de elenco que o plano ainda não fixa
Os diagramas usam valores provisórios, todos em `LeagueRules` (D-4) para mudar sem código: até 4 protegidos no practice squad,
3 elevações por jogador por temporada, IR com mínimo de 4 semanas e 8 retornos por temporada, elenco de offseason maior que 53 e
rodízio de 6 a 9 jogadores na linha defensiva. Confirma esses números ou prefere outros?
