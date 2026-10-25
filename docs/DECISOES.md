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

### Bloco 6b — playbook, plano de jogo, preparação e partida ao vivo
- **D-58** Playbook (RF61) são pesos, não jogadas: peso de cada conceito (INSIDE_RUN, OUTSIDE_RUN, QB_SNEAK, SCREEN, SHORT_PASS, MEDIUM_PASS, DEEP_PASS, PLAY_ACTION) em cinco situações (EARLY_DOWN, THIRD_DOWN, THIRD_AND_LONG, RED_ZONE, TWO_MINUTE) e de cada cobertura, de 0 a 200 (100 = frequência padrão do motor, 0 = desativado), que multiplicam os pesos do `PlayCaller`. O plano de jogo decide corrida × passe; o playbook decide qual corrida, qual passe e qual cobertura (fora das chamadas com blitz). Tudo em 100 dá exatamente o mesmo jogo de antes (os sorteios não mudam; plausibilidade, determinismo e `live-match.json` intactos). Playbook mínimo: toda situação com uma corrida (por dentro ou por fora) e um passe, e pelo menos 2 coberturas ativas (422 PLAYBOOK_INCOMPLETE); peso fora da faixa, item ou situação desconhecidos ou repetidos = 422 PLAYBOOK_INVALID. No motor o `Playbook` viaja dentro do `GamePlan`; no banco, `playbook` + `playbook_weight`. Catálogo de jogadas (`PlayDefinition`), formações e esquemas (West Coast, Air Raid…) do diagrama RF61 ficam fora; as opções da chamada de jogada (RF62) continuam as fixas do motor (D-33), sem filtro pelo playbook.
- **D-59** BOLA nas rotas de partida: `/matches/{mid}/**` de outro usuário responde 404 `NOT_FOUND`, igual a id inexistente, e grava `ACCESS_DENIED`. As rotas `/careers/{id}/**` novas (game-plan, playbook, next-match/preparation, weeks) seguem o `CareerAccess` do 6a (403 auditado), sem mudar o comportamento já testado da carreira.
- **D-60** Ajustes do intervalo (RF29) entram no snapshot: `MatchSimulator.adjust` troca o plano (com o playbook) da franquia no `MatchSetup` do snapshot, sobe a versão e grava `PLAN_CHANGE` no log de decisões; o log sozinho não reproduz o plano (o snapshot é a fonte), e retomar do snapshot dá o mesmo jogo. Só em NOT_STARTED, BREAK e HALFTIME (422 ADJUSTMENT_NOT_ALLOWED); não mudam o plano salvo da franquia; não avançam a partida (o front chama `/advance`). Tempo técnico (`TIMEOUT`) pelo mesmo `/decisions`, com a bola morta (`MatchSimulator.apply`, sem avançar; 422 TIMEOUT_NOT_ALLOWED).
- **D-61** Prazo da chamada de jogada (RF62): 30 s fixos (`MatchSupport.DECISION_TIMEOUT`), contados pela API quando a decisão abre (`match_session.decision_deadline_at`). Sem job em segundo plano: vencido o prazo, a IA decide (`CallSource.AUTO`) no próximo `/advance`, ou na chamada atrasada, que responde 409 DECISION_CLOSED com a decisão automática já gravada (exceção que não desfaz a transação).
- **D-62** `MatchSession` guarda o `MatchSnapshot` inteiro em JSON (`MEDIUMTEXT`) a cada avanço, sem Redis nem Cassandra (D-8/D-17). A situação da sessão vem do motor, mais BREAK/HALFTIME quando o último lance é um fim de período e o jogo segue. A versão do snapshot é a versão otimista das escritas: opcional no `/advance`, obrigatória em `/decisions` e `/play-calls` (um código só, 409 MATCH_VERSION_CONFLICT, no lugar de STALE_SNAPSHOT/MATCH_VERSION_CONFLICT dos diagramas). O nível de chamada de jogada da conta (RF10) é congelado no kickoff, como as regras (D-31).
- **D-63** Fechamento da semana síncrono: os outros jogos da semana são simulados inteiros quando ela fecha (ao fim da partida do usuário; na hora, numa semana de folga), na mesma transação; a semana passa a COMPLETED, a próxima a CURRENT e a semana atual da temporada e da carreira avança. Sem próxima semana, a temporada regular termina com a semana atual depois da última e `/weeks/current/play` responde 409 NO_NEXT_GAME (playoffs no 6c).
- **D-64** Todo jogo do calendário (usuário e IA) ganha `MatchReport` (placar, semente, versões do motor e das regras, e o box score completo, drives, jogadas de pontuação e decisões em JSON, como o motor entregou) e linhas de `PlayerMatchStats` (colunas só com o que o motor gera, D-35). A tabela da divisão continua calculada dos placares gravados (D-48), sem tabela materializada. O play-by-play completo existe só nas partidas do usuário (no snapshot); os jogos da IA ficam com o relatório.
- **D-65** Plano de jogo (RF61): `passRatio` de 20 a 80 (a faixa que o motor aceita; o diagrama dizia 0–100) e defesa base só 4-3 ou 3-4; valores fora = 422 GAME_PLAN_INVALID com `errors[]`. Plano e playbook nascem na primeira leitura, como o depth chart (D-53), para a versão otimista existir desde o GET (`source` COACH_STYLE até o primeiro PUT, depois SAVED). Gravar com partida em andamento é aceito e vale no próximo kickoff (o diagrama tinha 422 MATCH_LIVE). Jogadores-chave (até 5) ficam fora: o motor não os usa.
- **D-66** Correção no `MatchTeams.team` (bloco 5/6a): os ativos do dia de jogo são escolhidos por id. O `equals` da entidade não reconhece o próprio proxy do Hibernate, e um jogador do depth chart salvo lido antes na mesma transação entrava duas vezes nos ativos (o box score do jogo repetia o jogador e a gravação quebrava na chave única).
- **D-67** Estado ao vivo da API (`match-state.json`): a sessão (situação, franquia do usuário, nível, prazo, modos de avanço aceitos, se aceita ajustes) mais o mesmo `LiveMatchDto` do `live-match.json` em `live`, com os 20 últimos lances (a amostra do motor usa 12). O play-by-play sai num objeto com cursor (`after`, `lastSeq`, `finished`, `events`), não numa lista crua como no basquete. `/advance` sem corpo avança uma jogada (`PLAY`). O relatório aceita o id da partida ou do jogo do calendário.
- **D-68** Rotas do 6b pelo mapa `docs/02` (D-9) mais `/play-calls/{decisionId}`. As rotas alvo dos diagramas com outros nomes (`/matches/{mid}/plays`, `/drives`, `/playbook`, `/adjustments`, `/decisions/current`, `/decisions/{did}`, `/stream`, `/careers/{id}/playbooks*`, `/game-plan/suggestion`, `/next-game/opponent-report`) não entram; o que elas fariam está nas rotas do mapa (`/events`, `/report`, `/decisions`, `/play-calls`, `/next-match/preparation`).
- **D-69** Preparação (RF7): titulares = depth chart efetivo que o motor vai usar (o salvo, completado pelo automático; o automático no adversário) entre os ativos do dia de jogo. Confrontos-chave pelos dois lados: WR1–3 × CB1–3, OL (5) × DL (DE1–2, DT1–2), QB × pass rush (DE1–2, OLB1), média do overall na vaga, vantagem com diferença de 3 ou mais. Clima = o mesmo sorteio da partida (estádio do mandante + semente do jogo).

### Bloco 6b — frontend
- **D-70** A rota `/c/[careerId]/match` não tem o id da partida no caminho (mapa `docs/02`): a tela acha a partida por `?m=<matchId>` (o "Jogar" da preparação e o "Continuar partida" da central já mandam), senão por `dashboard.live.matchId`, senão pelo `matchId` de `GET /weeks/{semana atual}`; achando, troca a URL para `?m=`. Sem partida, mostra o estado vazio com o CTA "Preparar e jogar". Depois do fim (a semana fecha e a central avança), a tela continua na partida pelo `?m=`.
- **D-71** O modo mock do 6b tem um simulador de demonstração (`lib/api/mock/match.ts`), não um port do motor Java: descidas, corridas, passes (curto/médio/longo/screen/play-action pelos pesos do playbook), punts, field goals, pontos extras, kickoffs, relógio por ritmo, decisão do usuário só na 4ª descida (nível KEY_MOMENTS, ataque e defesa), sem prorrogação (empate possível), adversário com elenco gerado por semente e os outros jogos da semana com placares sorteados ao fechar a semana. Os `textKey`/`params`, os códigos de erro, a versão otimista, o prazo de 30 s e os modos de avanço seguem o backend; o plano padrão por estilo do mock é próprio (o do backend é o D-38).
- **D-72** Painel do intervalo (RF29): como o estado da API não traz o plano e o playbook em vigor na partida, o painel parte do último ajuste mandado nesta partida (guardado na store da tela) ou, sem ajuste, do plano e do playbook salvos da franquia; manda `GAME_PLAN` com todos os campos (o backend valida o plano inteiro) e `PLAYBOOK` completo, só o que mudou em relação ao ponto de partida.
- **D-73** 409 de plano/playbook (`*_VERSION_CONFLICT`): a tela relê a versão atual, reaplica por cima as mudanças do usuário (campo a campo / peso a peso) e avisa quantas foram reaplicadas e quais também mudaram na outra sessão (fica o valor do usuário); não grava sozinha, o usuário confere e salva de novo. 422 marca cada problema de `details.errors` no campo (`passRatio`, `EARLY_DOWN.DEEP_PASS`, `coverages.COVER_3`, a situação inteira). A tela também valida antes (mesmas regras do `GamePlanPolicy`) e só libera "Salvar" sem problemas locais.
- **D-74** Chamada de jogada (RF62): a contagem regressiva roda no navegador contra `decisionDeadlineAt` (sem correção de relógio entre cliente e servidor); no zero, a tela relê `GET /matches/{mid}` (o servidor passa a aceitar o avanço) e mostra "Prazo encerrado: a IA vai chamar X" com "Continuar". `DECISION_CLOSED`, `MATCH_VERSION_CONFLICT`, `DECISION_PENDING`, `DECISION_NOT_FOUND` e `MATCH_FINISHED` viram aviso + recarga (não erro); `PLAY_NOT_OFFERED`, `ADJUSTMENT_NOT_ALLOWED` e `TIMEOUT_NOT_ALLOWED` recarregam e mostram a mensagem do servidor (`lib/match/live.ts#reactionTo`).
- **D-75** Textos novos: namespace i18n novo `game` (playbook, plano e preparação); a partida e o relatório entram em `match` (`live.*`, `call.*`, `break.*`, `stats.*`, `box.*`, `report.*`); enums novos em `enums`. Os códigos de erro do backend usam `{0}`/`{1}` e o corpo do erro não traz os argumentos: a tela mostra a `message` que o servidor já traduziu (o catálogo vale no mock). Placar no formato "visitante @ casa"; no campo 2D a casa defende a end zone da esquerda (jarda 0), como o motor.
- **D-76** Calendário: link "Relatório" nos jogos do usuário encerrados (`status` FINISHED) e "Continuar" no jogo ao vivo; os relatórios dos jogos da IA (o backend aceita o id do jogo do calendário) ficam acessíveis pelos placares de "Outros jogos da semana" na tela da partida.
- **D-77** Central: com `dashboard.live` preenchido, card "Partida em andamento" (placar e "Continuar partida") e o CTA do próximo jogo vira "Continuar partida"; sem partida, "Preparar e jogar" abre o `PrepareTeamModal`; em semana de folga, "Avançar a semana de folga" chama `POST /weeks/current/play` (D-63) e recarrega a central.

### Bloco 6b — correções da revisão
- **D-90** Play-by-play da tela (`store/match.ts`): o cursor de `/events?after=` sai dos lances que a tela já tem, antes de juntar os 20 de `live.lastEvents`; a busca pagina até o `lastSeq` da partida e só depois junta os `lastEvents` (sem duplicar, por `seq`). Abrir a partida no meio traz o jogo todo e um avanço longo não deixa buraco entre o que a tela tinha e os 20 últimos (o drive atual e o campo 2D dependem disso).
- **D-91** Escritas concorrentes na partida: avançar, chamar a jogada e ajustar leem a `MatchSession` travada (`PESSIMISTIC_WRITE`, `MatchSessionRepository#findByIdForUpdate`) como primeira leitura da transação, e `POST /weeks/current/play` trava a carreira (`CareerSaveRepository#findByIdForUpdate`) antes de tudo. A segunda escrita espera a primeira confirmar e lê o estado já gravado: dois `FULL` com a mesma versão dão 200 + 409 (`MATCH_FINISHED`/`MATCH_VERSION_CONFLICT`); dois `play` da mesma semana criam uma partida só (o segundo devolve a mesma, idempotente). O que ainda escapar vira 409 `CONCURRENT_UPDATE` no `ApiExceptionHandler`: chave única repetida (SQLState 23505 ou erro 1062/1586 do MySQL, também dentro do lote do Hibernate) e trava não obtida (`PessimisticLockingFailureException`, tempo de espera); as outras violações de integridade continuam 500. O `audit.ok` passa a gravar depois de a transação de quem chamou confirmar (`AfterCommit.run`, em transação própria; sem transação, na hora): a escrita desfeita não deixa sucesso no log; um efeito agendado de dentro de outro efeito pós-commit (o editor de mundos já agendava a auditoria) roda na hora, em vez de se perder; `denied`/`failed` seguem imediatos e sobrevivem ao rollback (RNF7). No front, `CONCURRENT_UPDATE` recarrega o estado com o aviso de estado velho, como `MATCH_VERSION_CONFLICT`.
- **D-92** "Jogar" na semana de folga: a preparação mostra o próximo jogo (semana N+1), mas `POST /weeks/current/play` age sobre a semana atual N. Quando a resposta vem sem `matchId` com `userBye` e a semana avançou, o "Jogar" chama de novo (no máximo 2 folgas seguidas, `lib/match/play.ts#playToMatch`) e entra na partida do jogo que a preparação mostrava; a folga encerrada vira aviso (`game:prepare.byeClosed`) na tela da partida (`?bye=N`) ou no próprio modal, se a segunda chamada falhar (ex.: 422 do depth chart). Escolhido em vez de recarregar a preparação porque o usuário clicou "Jogar" no jogo que via: um clique, com o aviso do que aconteceu no caminho.
- **D-93** Resposta atrasada não volta o estado da partida: o store ignora um estado de outra partida (a tela já trocou) ou de `live.version` menor que a da tela (a mesma versão vale: prazo e modos podem mudar sem lance), e a tela não dispara o refresh do fim do prazo enquanto há escrita em andamento (a resposta dela já traz o estado novo).
- **D-94** Log de decisões: `TIMEOUT` e `PLAN_CHANGE` não têm `decisionId` (nem situação) e a API omite os nulos; o shape `decisionRecord` trata `decisionId` como anulável (`null` no front, não string vazia) e o mock grava `null` como o backend. As linhas do relatório usam a posição na lista na chave de render (`lib/match/live.ts#decisionKeys`): dois ajustes no mesmo lance não repetem a chave.

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

### Q-29 a Q-34 — partida (bloco 6b)
- **Q-29** Carreira de outro usuário: manter o 403 auditado do 6a nas rotas `/careers/{id}/**` ou passar para 404, como nas rotas de partida (D-59)?
- **Q-30** Prazo da chamada de jogada: 30 s fixos (D-61) ou configurável no RF10 (`decisionTimeoutSeconds` aparece nos diagramas, mas não existe em `UserSettings`)?
- **Q-31** Jogadores-chave do plano de jogo (até 5, RF61): o motor deve usá-los (mais alvos e mais carregadas)? Hoje ficam fora (D-65).
- **Q-32** Playbook por pesos (D-58) basta, ou o PO quer o catálogo de jogadas e esquemas do diagrama RF61 (playbook próprio, jogadas por formação e personnel)? As opções da chamada de jogada devem respeitar o playbook?
- **Q-33** Os outros jogos da semana: simular ao fechar a semana (D-63, como ficou) ou ao jogar, como no basquete, com os placares deles no painel enquanto o usuário joga?
- **Q-34** `passRatio` do plano de 20 a 80 (a faixa do motor) em vez de 0 a 100: confirma?

### Q-35 a Q-37 — partida, front (bloco 6b)
- **Q-35** O estado da partida (`MatchStateDto`) não traz o plano de jogo e o playbook em vigor nem as cores das franquias (o `TeamLine` só tem id, nome e sigla). O front busca as cores no painel da semana e parte do plano salvo ou do último ajuste da sessão (D-72): dá para o backend incluir `gamePlan`/`playbook` da franquia do usuário e `primaryColor`/`secondaryColor` em `live.home/away`?
- **Q-36** Prazo da chamada de jogada com relógio do cliente (D-74): o backend pode mandar também a hora do servidor (ou os segundos restantes) para corrigir relógio adiantado/atrasado do aparelho?
- **Q-37** Em BREAK/HALFTIME, `live.quarter` já é o próximo quarto (no mock é) ou ainda o que terminou? A tela trata os dois casos (coluna do próximo quarto em "–" enquanto zerada), mas o contrato devia dizer.
