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

### Bloco 3 — plataforma comum (PR #3)
- **D-12 Domínio sem Spring.** O `DomainPurityTest` permite `jakarta.persistence` e `jakarta.validation` e proíbe Spring, servlet e Jackson. Quem decide entre persist e merge é o `AssignedIdJpaRepository`, na infraestrutura.
- **D-13 RF10 do futebol americano.** `quarterDurationMinutes` (1–15, padrão 3; fora disso, 400 `INVALID_QUARTER_DURATION`) e `playCallingLevel` (KEY_MOMENTS, ALL_PLAYS, NONE) no lugar do `matchSpeed` do basquete.
- **D-14 Nenhuma chave fixa.** Sem `DATA_ENCRYPTION_KEY`, a chave AES é aleatória e gerada na subida (o basquete derivava de uma semente no código); senhas de teste são geradas na execução.
- **D-15 Dados do jogo por extensão.** `AccountDataContributor` alimenta a exportação e a exclusão da conta; cada bloco do jogo pluga o seu.
- **D-16 Nomes próprios.** Cookie `__Host-scoresaf_rt`, JWT com issuer `scores-af` e audience `scores-af-api` (os dois jogos rodam em localhost e cookie não separa por porta).
- **D-17 Adaptadores provisórios.** Cache de sessão em memória e mídia em filesystem até o Redis e o S3 entrarem (D-8).
- **D-18 Campos nulos.** A API omite campos nulos (`non_null`); os testes do front aceitam campo anulável ausente e falham com campo desconhecido ou obrigatório ausente.
- Correções de revisão: revogação por reuso de refresh gravada mesmo com o 401, refresh consumido por UPDATE condicional (sem sessões irmãs), token de troca de e-mail preso ao endereço que o recebeu.

### Bloco 4 — domínio do futebol americano (PR #4)
- **D-19** Pesos do overall e dos macros em milésimos, conta inteira `(Σ peso×valor + 500) / 1000`: Java e TS dão o mesmo número. Mudar peso é publicar uma nova versão (OVR v2).
- **D-20** Formato da temporada em `LeagueRules`; teto e piso salarial em `League`.
- **D-21** ADMIN lê qualquer mundo, com auditoria (`ADMIN_WORLD_READ`); não edita mundo de outro usuário nem o padrão.
- **D-22** O long snapper usa os macros da linha ofensiva, mas fica na unidade de times especiais.
- **D-23** Mundo semente gravado por código (`DefaultWorldFactory` + `SeedRunner`), idempotente e determinístico.
- **D-24** Até 5 mundos modelo por usuário (`WORLD_LIMIT`).
- **D-25** 49 subatributos (a lista completa do docs/01 §3.4), em colunas `attr_*`.
- **D-26** Estrutura da liga: 1–2 conferências, divisões com tamanhos que diferem no máximo em 1, ids únicos no payload.
- **D-27** Bônus de assinatura rateado em `min(anos, bonusProrationMaxYears)`; a garantia é consumida do 1º ano em diante.
- **D-28** Nomes fictícios no mundo semente (cidades e mascotes inventados, nomes de jogadores sintéticos).

### Bloco 5 — motor por descida (PR #5)
- **D-29** Determinismo: geradores por finalidade (pré-snap, chamada de ataque, de defesa, resolução) derivados da semente e do cursor; a escolha do manager não desloca a resolução.
- **D-30** Campo em jarda absoluta, de 0 (gol do mandante) a 100.
- **D-31** As regras da liga são congeladas no início da partida (`RulesSnapshot`).
- **D-32** `DriveResult` ganhou `MISSED_FG`.
- **D-33** Momentos decisivos: 4ª descida, 2 minutos finais de cada tempo, prorrogação e conversão só no 4º quarto e na prorrogação; 2 a 5 opções; decisão vencida fica com a IA, e tudo automático dá o mesmo jogo da IA sozinha.
- **D-34** Aceitar ou recusar falta é automático; faltas só em jogadas de scrimmage.
- **D-35** O que o motor não gera vai como `null` ("—"), nunca como zero.
- **D-36** Textos dos lances por chave `match:event.*` / `match:play.*`, com parâmetros declarados em `EventKeys`.
- **D-37** Depth chart automático até o RF60 (posição natural, secundária, grupo, pelo overall); 46 ativos sempre com K, P e LS.
- **D-38** Plano de jogo padrão pelo estilo do head coach.
- **D-39** A plausibilidade é testada na aplicação, com o mundo semente (`EDGE_SCALE = 17`).
- **D-40** Prorrogação PRO (posse garantida para os dois salvo TD na primeira; empate só na temporada regular) e COLLEGE (posses alternadas da 25, 2 pontos obrigatórios da 2ª rodada).
- **D-41** Relógio: kickoff e punt param até o snap; bola fora para o relógio nos 2 min finais do 1º tempo e nos 5 finais do 2º; o tempo até o snap respeita o relógio de jogada da liga.
- **D-42** RNF2: ritmo por jogada = 4 × minutos × 60 s ÷ jogadas, entre 400 ms e 6 s.

### Bloco 6a — carreira e elenco
- **D-43** Criar carreira é síncrono (201) numa transação: cópia do mundo para SAVE (RNF1), head coach humano, temporada, calendário e depth chart padrão.
- **D-44** `DELETE /careers/{id}?confirm=<nome>` apaga de verdade (save, mundo SAVE, calendário, movimentos, depth chart); arquivar é `PATCH status=ARCHIVED`, que libera o slot.
- **D-45** Calendário da liga padrão pela fórmula profissional (6 jogos de divisão, 4+4 em rodízio, 2+1 por colocação), folgas nas semanas 5–14, um jogo de quinta, um de domingo à noite e um de segunda por semana; determinístico pela semente.
- **D-46** Teto salarial provisório (`CapSheet`): cap hit de 53 + practice squad + IR + dead money das dispensas da temporada; sem bloqueio `CAP_EXCEEDED` até o bloco 7.
- **D-47** Lesão mínima em `player.injury_weeks_out`; a IR exige lesão de pelo menos `irMinWeeks` e o retorno obedece `irReturnsPerSeason`.
- **D-48** Desempate provisório da tabela da divisão: saldo de pontos, depois prestígio (a cadeia completa é do RF16).
- **D-49** O papel `THIRD_DOWN_BACK` é guardado, mas ainda não tem vaga no motor; holder é `H`; long snapper é a posição `LS`.
- **D-50** ACTIVE e INACTIVE contam nos 53; o limite de 46 é aplicado no depth chart e no `MatchTeams`; uma rota só, `PATCH /roster-status`.
- **D-51** Practice squad: até 3 anos de experiência mais 6 vagas de veterano; mover de 53 para o practice squad mantém o contrato.
- **D-52** A dispensa deixa o jogador WAIVED, sem clube, com o contrato mantido (a fila de waivers é do bloco 7).
- **D-53** Só a franquia do usuário tem depth chart salvo; o PUT troca o depth chart inteiro e exige `version` (409 em conflito).
- **D-54** No dashboard, campos de blocos futuros saem como `null` explícito.
- **D-55** `ScoresException.withDetail` leva dados estruturados para `details` (409 com versões, 422 com `errors[]`/`warnings[]`).
- **D-56** Jogador de outra carreira responde 404; jogador do mesmo mundo mas de outra franquia é legível (`ownPlayer=false`) e as ações dão 422 `PLAYER_NOT_IN_ROSTER`.
- **D-57** A nova carreira do 6a usa a liga como está, temporada completa (sem franquia nova nem modo de temporada).

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

### Q-8 a Q-13 — domínio (bloco 4)
Q-8: o ADMIN pode editar o mundo padrão? (hoje não). Q-9: números da variante COLLEGE (elenco de 105, 70 relacionados, 12 jogos, sem practice squad, 6 sementes, sem draft). Q-10: faixas de altura e peso por posição; a camisa deve depender da posição? Q-11: valores de dinheiro (teto de S$ 255 mi, piso de S$ 227 mi, mínimo de S$ 900 mil). Q-12: limite de 5 mundos por usuário. Q-13: 49 subatributos em vez dos "≈40" do plano.

### Q-14 a Q-21 — motor (bloco 5)
Q-14: a conversão depois de TD abre decisão sempre ou só no 4º quarto e na prorrogação? Q-15: com 4+ minutos por quarto, o teto de 6 s por jogada encerra antes do orçamento; subir o teto? Q-16: faltas em times especiais e faltas compensadas, em que bloco? Q-17: aceitar ou recusar falta só em `ALL_PLAYS`? Q-18: a mais forte vence a mais fraca em ~90 %; a meta é ~85 %? Q-19: 1,6 % de empates na temporada regular é aceitável? Q-20: padrões de onside e da tabela de 2 pontos até o RF63. Q-21: o mandante vence 59 % dos jogos (referência ~55 %); fixar uma faixa testada e ajustar a vantagem de casa?

### Q-22 a Q-28 — carreira e elenco (bloco 6a)
Q-22: a IR deve exigir lesão? (o motor só gera lesões no RF64; até lá a IR não tem uso). Q-23: elegibilidade do practice squad (3 anos + 6 veteranos); mover de 53 para o practice squad passa por waivers? Q-24: dead money integral na temporada ou regra do 1º de junho; bloquear dispensa por `CAP_EXCEEDED`? Q-25: confirma o DELETE definitivo com confirmação por nome? Q-26: bloquear clonar o mundo SAVE de uma carreira como mundo modelo (RNF1)? Q-27: apagar os mundos modelo editados na eliminação da conta? Q-28: em ligas do editor, divisões de 7+ franquias jogando entre si uma vez só e ligas ímpares com 16 jogos?
