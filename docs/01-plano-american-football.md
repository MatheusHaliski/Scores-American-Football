# Scores: American Football — plano de construção

> Bloco 1 (só documentação). Mapeia cada requisito do Scores (basquete, repositório `MatheusHaliski/scores-velion`) para
> o futebol americano. Para cada um, diz se fica **igual**, se precisa **adaptar** ou se é **novo**, e fixa o domínio do esporte que
> os blocos seguintes vão construir. Decisões em [`DECISOES.md`](DECISOES.md) (D-1 em diante), perguntas ao PO
> (Q-1 em diante). Diagramas por requisito em `docs/RF<n>/` e `docs/RNF<n>/`, gerados por `docs/generator/`.

## 1. Princípio

**Mesma arquitetura, domínio novo.** O Scores de basquete levou meses para acertar a plataforma: conta, segurança, sessão, LGPD,
i18n, persistência poliglota, CI, design system, cards, 3D e governança de documentos. Tudo isso vem para cá como ponto de partida.
O que é do esporte é refeito do zero: regras, posições, atributos, motor de partida, estatísticas, tática, telas de partida
e modelos 3D. O código copiado não carrega histórico do outro repositório (D-3).

Lições do basquete aplicadas desde o primeiro dia (D-5):
- **Front e back testados juntos.** O modo mock escondeu oito contratos quebrados no basquete (docs/39 §3 de lá). Aqui, todo
  DTO lido pelo front tem uma **amostra dourada gerada pelo backend**, e o adaptador do front é testado contra ela.
- **Vocabulário único.** Status, tipos de evento e táticas têm um só vocabulário, do motor até a tela. Onde precisar traduzir,
  o adaptador é testado.
- **Sessão segura desde o início.** Refresh token em cookie `__Host-` HttpOnly, access token só em memória e logout entre abas.
- **Nenhuma credencial no código**, nem de teste: senhas de teste são geradas na execução. Gitleaks antes de todo push, com o
  resultado conferido.

## 2. Arquitetura (copiada do basquete, renomeada)

| Camada | Basquete | American Football |
|---|---|---|
| Domínio (Java 21, puro) | `scores-domain` (`br.com.velion.scores.domain`) | `scores-af-domain` (`br.com.velion.scoresaf.domain`) |
| Aplicação (casos de uso, portas) | `scores-application` | `scores-af-application` |
| Web (REST, segurança, OpenAPI) | `scores-web` | `scores-af-web` |
| Infraestrutura (MySQL/Flyway, Redis, Cassandra, OpenSearch, S3, IA) | `scores-infrastructure/*` | `scores-af-infrastructure/*` |
| Bootstrap (Spring Boot 4.1, temurin 25) | `scores-bootstrap` | `scores-af-bootstrap` |
| Front (Next.js 16, React 19, three/R3F/drei) | `frontend/` | `frontend/` |
| ML/3D offline (Python) | `src/`, `scripts/` | depois do bloco 8, só se o 3D pedir |
| Docs, diagramas, planilha | `docs/` | `docs/` (`docs/RF<n>/` direto, sem `diagramas/`) |

Persistência poliglota com os mesmos fallbacks em memória: MySQL é a fonte da verdade; Redis guarda a partida ao vivo e os
contadores; Cassandra, a timeline (play-by-play, inbox, notícias); OpenSearch, a busca; S3, a mídia.

## 3. O esporte

Todas as regras ficam em `LeagueRules`, versionado por liga. A resposta da Q-1 só muda os valores padrão, não o código (D-4).

### 3.1 Regras (padrão profissional, liga fictícia)
- **Tempo:** 4 quartos de 15 min. Relógio de jogada de 40 s (25 s depois de paradas administrativas). Aviso dos 2 minutos em cada tempo.
  3 tempos técnicos por tempo.
- **Prorrogação:** 10 min. As duas equipes têm a posse ao menos uma vez, a menos que a primeira marque TD. Depois, quem pontuar
  primeiro vence. Pode terminar empatada na temporada regular (configurável).
- **Descidas:** 4 tentativas para avançar 10 jardas. Campo de 100 jardas, mais 2 end zones de 10.
- **Pontos:** touchdown 6, ponto extra 1 (chute da linha de 15), conversão de 2, field goal 3, safety 2.
- **Kickoff:** da linha de 35, com touchback configurável.
- **Faltas (catálogo inicial):** false start, offside, encroachment, holding (ofensivo e defensivo), pass interference
  (ofensiva e defensiva), roughing the passer, unnecessary roughness, delay of game, illegal formation, facemask e
  intentional grounding. Cada uma tem jardas, possível primeira descida automática e perda de descida.
- **Variante college** (`ruleset=COLLEGE`, Q-1): relógio para na primeira descida enquanto as correntes são reposicionadas; prorrogação
  com posses alternadas a partir da linha de 25; um pé dentro na recepção.

### 3.2 Liga, elenco e calendário
- **Liga padrão:** 32 equipes em 2 conferências × 4 divisões, configurável de 8 a 32 como no basquete (Q-3).
- **Temporada regular:** 17 semanas e 1 semana de folga (bye) por equipe. Playoffs com 7 por conferência; o 1º de cada conferência
  folga na primeira rodada. Final em campo neutro.
- **Elenco:** 53 jogadores, com 46 relacionados por jogo. Time de treino (practice squad) de 16. Lista de lesionados (IR).
- **Teto salarial rígido**, piso salarial, contratos com bônus de assinatura, garantias e cortes com dead money (bloco 7).
- **Draft:** 7 rodadas, ordem inversa da classificação e combine (bloco 7).

### 3.3 Posições e unidades
- **Ataque:** QB, RB, FB, WR, TE, OL (LT, LG, C, RG, RT).
- **Defesa:** DE, DT (DL), OLB, MLB (LB), CB, FS, SS (S).
- **Times especiais:** K, P, LS, mais os retornadores KR e PR (papel, não posição).
- **Depth chart (escalação por profundidade):** titulares e reservas por posição e por unidade. Substitui o "quinteto, banco e
  reservas" do basquete.

### 3.4 Atributos, macros e overall
- **Cerca de 40 subatributos:**
  - Físicos: velocidade, aceleração, agilidade, força, impulsão, resistência, durabilidade.
  - Passe: precisão curta, média e longa, força de braço, passe em movimento, play-action, leitura de defesa, calma sob pressão.
  - Corrida e posse: visão de corrida, quebra de tackle, juke, spin, stiff arm, proteção da bola, carregar.
  - Recepção: mãos, recepção em tráfego, recepção espetacular, corrida de rota curta, média e funda, soltura.
  - Bloqueio: de passe, de corrida, técnica, finesse e potência.
  - Defesa: tackle, poder de impacto, perseguição, leitura de jogada, cobertura individual e por zona, press, movimentos de pass rush (finesse e potência), desvencilhar-se do bloqueio.
  - Chute: potência e precisão de chute, chute de retorno.
  - Mental: consciência, liderança.
- **Seis macros no card, por família de posição** (D-6):
  - QB: PRE, BRA, LEI, MOB, CAL, LID.
  - Corredores e recebedores: VEL, AGI, MAO, ROT, QBT, FOR.
  - Linha ofensiva: BLP, BLC, FOR, TEC, AGI, CON.
  - Defesa: TAC, IMP, COB, PRS, VEL, LEI.
  - Chutadores: POT, PRE, CON.
- **Overall por posição** com pesos oficiais e versionados, como no basquete (OVR v1 → v2). O mesmo jogador tem um overall diferente
  em cada posição; o card avisa quando ele está fora de posição.
- **Níveis do card:** Bronze ≤ 64, Prata 65–74, Ouro 75–84, Ícone 85–89, Elite ≥ 90, cada nível com ícone próprio. Cards
  especiais ganham selo; a moldura do nível continua.

### 3.5 Motor de partida (determinístico por semente)
- **Unidade de simulação: a jogada.**
  - O ataque escolhe a formação (I, Singleback, Shotgun, Pistol, Empty, Goal line) e o conceito:
    - corrida por dentro ou por fora;
    - passe curto, médio ou fundo, screen, play-action;
    - QB sneak, chute (punt ou FG).
  - A defesa escolhe:
    - pacote: base 4-3 ou 3-4, nickel, dime, goal line;
    - cobertura: Cover 0, 1, 2, 3, 4 ou 6, individual ou zona;
    - blitz e espião.
- **Resultado da jogada:** distribuição sorteada pelos confrontos (bloqueio × pass rush, rota × cobertura, corredor × tackle), mais
  fadiga, moral e clima. Pode sair ganho de jardas, incompleto, sack, turnover, falta ou pontuação.
- **Gestão de relógio:** quanto tempo cada jogada consome, se o relógio para (incompleto, fora de campo, falta), uso de tempo
  técnico, aviso dos 2 minutos e jogadas de relógio (spike, kneel).
- **Controle do manager (Q-2, padrão proposto):**
  - plano de jogo antes da partida: tendência corrida/passe, agressividade na 4ª descida, ritmo, foco defensivo e jogadores-chave;
  - ajustes entre quartos e no intervalo (o painel do intervalo vira **playbook**);
  - **momentos decisivos com chamada de jogada:** 4ª descida, últimos 2 minutos e prorrogação, com tempo limite e decisão
    automática se o tempo acabar;
  - chamar todas as jogadas é um modo opcional (Q-2).
- **Plausibilidade testada** contra médias profissionais, por equipe e por jogo, com tolerância. Tudo isso entra como teste:
  - cerca de 21–24 pontos, 320–360 jardas, 11–12 drives;
  - passes completos em torno de 63–66 %;
  - 2–3 sacks, 0,7–1,0 interceptação e 0,4–0,6 fumble perdido;
  - 3ª descida convertida em torno de 38–42 %;
  - FG certo em torno de 84 %.

### 3.6 Estatísticas
- **Por jogador:**
  - Passe: completos/tentados, jardas, TD, INT, rating, sacks sofridos.
  - Corrida: tentativas, jardas, média, TD, fumbles.
  - Recepção: alvos, recepções, jardas, YAC, TD, drops.
  - Defesa: tackles (solo e assistido), TFL, sacks, QB hits, INT, passes defendidos, fumbles forçados e recuperados.
  - Chute: FG por distância, PAT, punts com média e dentro da 20.
  - Retornos.
- **Por equipe:** primeiras descidas, 3ª e 4ª descidas, red zone, tempo de posse, turnovers, faltas e jardas de falta.
- **O que o motor não gera** aparece como "—", nunca como zero, como no basquete.

### 3.7 3D
- **Estádio:** campo de 120 × 53,3 jardas com linhas a cada 5, números a cada 10, hash marks, end zones pintadas com o nome
  do time, traves em Y e redes atrás.
- **Atletas:** capacete com grade por posição, ombreiras e uniforme do clube com o mesmo sistema de identidade (cores, contraste e
  número legível).
- **Animações:** alinhamento e snap, drop do QB, passe, recepção, corrida com quebra de tackle, tackle, bloqueio, chute,
  comemoração. As 37 animações do basquete servem de base para a locomoção (andar, acelerar, parar, mudar de direção, saltar,
  aterrissar).
- **Câmeras e painéis:** câmera de transmissão lateral, aérea e "atrás do QB". Painéis LED com placar, quarto, relógio e descida
  e distância ("3rd & 7 at OPP 34"). Linha de scrimmage azul e linha de primeira descida amarela, como na TV.
- **Mini campo no intervalo**, no lugar da mini quadra do RF43: formações, rotas e coberturas desenhadas sobre o campo.

## 4. Requisito a requisito

Mesmo número quer dizer mesma função do basquete (D-2), o que facilita comparar os dois jogos. Requisitos que só existem no
futebol americano começam em RF60.

| RF | Basquete | Destino | O que muda no futebol americano |
|---|---|---|---|
| RF1 | Cadastro | igual | — |
| RF2 | Login e recuperação | igual | — |
| RF3 | Gerenciar conta | igual | — |
| RF4 | Elenco | adaptar | 53 jogadores por unidade (ataque, defesa, especiais), practice squad, IR |
| RF5 | Editor de ligas e clubes | adaptar | conferências e divisões, 8–32 times, regras da liga (`LeagueRules` AF) |
| RF6 | Escalação e táticas | adaptar | depth chart por posição + plano de jogo (tendências, 4ª descida, ritmo) |
| RF7 | Preparar time e jogar | adaptar | relatório do adversário por unidade, confrontos-chave (WR × CB, OL × DL) |
| RF8 | Contrato do manager e diretoria | igual | metas em vitórias, playoffs e título |
| RF9 | Propostas de outros clubes | igual | — |
| RF10 | Configurações de jogo | adaptar | duração de quarto simulada, nível de controle de jogada (Q-2) |
| RF11 | Carreiras salvas | igual | — |
| RF12 | Estádio, setores, ingressos | adaptar | estádio aberto ou coberto, clima, capacidade 60–80 mil |
| RF13 | Inbox e notificações | igual | — |
| RF14 | Orçamento e empréstimo | adaptar | teto salarial rígido e receitas por jogo em casa (8–9 por temporada) |
| RF15 | Estilos de manager | adaptar | estilos de head coach: ofensivo, defensivo, desenvolvedor, motivador |
| RF16 | Classificação e rodadas | adaptar | semanas, divisões, conferências, critérios de desempate |
| RF17 | Sala de troféus | igual | troféus de divisão, conferência e campeão |
| RF18 | Managers do mundo | igual | — |
| RF19 | Comprar/emprestar jogador | adaptar | não há compra entre clubes: free agency, trocas e dispensas (waivers) |
| RF20 | Busca | igual | filtros por posição e unidade |
| RF21 | Calendário com IA | adaptar | semanas com bye, quinta à noite e rivais da divisão |
| RF22 | Esquema ampliado do jogador | adaptar | modal do jogador com macros por família de posição |
| RF23 | Animação 3D do jogador | adaptar | animações do futebol americano (§3.7) |
| RF24 | Responder inbox | igual | — |
| RF25 | Consumíveis | adaptar | efeitos por atributo do futebol americano |
| RF26 | Estádio 3D e loja modular | adaptar | campo, traves, arquibancadas abertas ou cobertura |
| RF27 | Painel da rodada | adaptar | partida ao vivo: play-by-play, descida e distância, drive atual |
| RF28 | Editar escudo, cores, uniforme | adaptar | capacete (cor, faixa, logo lateral), uniforme de casa, fora e alternativo |
| RF29 | Painel do intervalo | adaptar | **playbook**: ajustes de plano, formações e coberturas |
| RF30 | Classificação e notícias (IA) | igual | — |
| RF31 | Público e renda | adaptar | 8–9 jogos em casa e preço por setor |
| RF32 | Loja (Scores Points) | igual | — |
| RF33 | Scores Points por metas | igual | — |
| RF34 | Nova carreira | adaptar | escolhe a liga AF, o clube e o estilo de head coach |
| RF35 | Fim de temporada | adaptar | playoffs, draft, free agency, renovações |
| RF36 | Simular pelo calendário | igual | paradas também em draft, free agency e prazo de trocas |
| RF37 | Desempregado e ofertas | igual | — |
| RF38 | Animações 3D por IA | adaptar | por atributo e por posição do futebol americano |
| RF39 | Categoria de base | adaptar | vira **scouting universitário** (a base está no draft) |
| RF40 | Central do clube + manager | igual | painéis com descida e distância e próximos adversários da divisão |
| RF41 | Motor de moral | igual | fatores: tempo de jogo por posição, contrato, vitórias |
| RF42 | Simulação da partida | **novo motor** | por descida (§3.5) |
| RF43 | Mini quadra 3D | adaptar | **mini campo** no intervalo |
| RF44 | Meu jogador por foto | igual | posição de futebol americano e altura/peso pela posição |
| RF45 | Multiplayer + Ultimate Team | adaptar | Ultimate Team com depth chart e química por divisão e conferência |
| RF46 | Configurações LGPD + i18n | igual | — |
| RF47 | Estatísticas em velocímetros | adaptar | indicadores por posição (rating do QB, jardas/tentativa, pressões) |
| RF48 | Sub-atributos ampliados | adaptar | ≈40 subatributos (§3.4) |
| RF49 | Teto salarial e contratos | adaptar | **teto rígido**, dead money, garantias, franchise tag |
| RF50 | Trocas com jogadores e picks | adaptar | trocas com picks de draft de até 3 anos à frente |
| RF51 | Draft, scouting e combine | adaptar | 7 rodadas, combine (40 jardas, supino, salto), entrevistas |
| RF52 | Sala de Imprensa | igual | — |
| RF53 | Regulamentos reais e copas | adaptar | regras profissional × college (Q-1); sem copa |
| RF54 | Eventos de partida completos | adaptar | catálogo de jogadas, faltas e eventos (§3.1, §3.5) |
| RF55 | Minutos-alvo e química | adaptar | rodízio da linha defensiva, snaps por jogador, química OL e QB-WR |
| RF56 | Time de desenvolvimento | adaptar | **practice squad** (16) |
| RF57 | All-Star weekend | adaptar | jogo das estrelas + desafios de habilidade |
| RF58 | Gêmeos digitais licenciados | igual | depende de licença (Q-4) |
| RF59 | Mini quadra com jogo real | adaptar | mini campo com a jogada real desenhada |
| RF60 | — | **novo** | **Depth chart** por posição e unidade, com papéis (KR, PR, 3rd-down back) |
| RF61 | — | **novo** | **Playbook e plano de jogo** (formações, conceitos, tendências por situação) |
| RF62 | — | **novo** | **Chamada de jogada** em momentos decisivos (4ª descida, 2 minutos, prorrogação) |
| RF63 | — | **novo** | **Times especiais** (kickoff, punt, FG, PAT, retornos, onside) |
| RF64 | — | **novo** | **Relatório de lesões semanal** (questionável, duvidoso, fora; IR) |
| RF65 | — | **novo** | **Free agency e waivers** (fase de free agency, dispensas, claims) |
| RF66 | — | **novo** | **Playoffs com chave** (7 por conferência, folga do 1º, final neutra) |

| RNF | Destino |
|---|---|
| RNF1 ACID usuários/carreiras/database do jogo | igual |
| RNF2 Partida dentro do tempo configurado | adaptar (relógio de jogo do futebol americano: ~150 jogadas por partida) |
| RNF3 Controle de acesso por perfil | igual |
| RNF4 JWT RSA, refresh rotativo | igual (com cookie `__Host-` desde o início, D-5) |
| RNF5 Criptografia de dados sensíveis | igual |
| RNF6 Backup e recuperação | igual |
| RNF7 Auditoria e logging | igual |
| RNF8 LGPD | igual |
| RNF9 Desempenho e usabilidade | adaptar (3D com 22 atletas e campo maior) |
| RNF10 Notificações Web Push | igual |
| RNF11 Uploads | igual |
| RNF12 Scores Points e loja | igual |
| RNF13 Traduções | igual (pt-BR, en-US, es-ES; termos do esporte em inglês ficam em inglês quando é o uso do Brasil: touchdown, snap, sack) |

## 5. Ordem dos blocos (um PR por bloco, CI verde antes do próximo)

| # | Bloco | Conteúdo |
|---|---|---|
| 1 | **Plano e diagramas** (este) | este documento, `DECISOES.md`, gerador de diagramas e `docs/RF<n>/` de todos os RFs e RNFs |
| 2 | **Esqueleto** | Maven multi-módulo renomeado, Next.js, CI (backend JDK 21/25, frontend, gitleaks, imagem), Dependabot, testes de guarda; a app sobe com uma página inicial e `/actuator/health` |
| 3 | **Plataforma comum** | RF1–RF3, RF46, RNF3–RNF8, RNF13: conta, sessão com cookie, LGPD, configurações, i18n, auditoria, gate de dev, com os testes do basquete |
| 4 | **Domínio AF** | `LeagueRules`, posições, ≈40 subatributos, overall por posição (vetor de ouro Java ↔ TS), entidades, Flyway V1, mundo semente fictício de 32 times |
| 5 | **Motor de partida** | RF42 por descida, faltas, relógio, times especiais, plausibilidade, determinismo, amostra dourada do estado ao vivo |
| 6 | **Carreira e partida** | RF34, RF4, RF60, RF61, RF7, RF27, RF29 (playbook), RF62, RF16, RF21, RF40, RF41, RF35, RF66 |
| 7 | **Mercado e contratos** | RF49, RF19/RF65, RF50, RF51, RF8, RF9, RF13/RF24, RF37 |
| 8 | **3D** | RF26, RF23, RF43 (mini campo), RF28, painéis LED e câmeras |
| 9 | **Cards, modal e estatísticas** | RF22, RF47, RF48, RF44 |
| 10 | **Ultimate Team e loja** | RF45, RF32, RF33, RNF12 |
| 11 | Restante | RF12, RF14, RF17, RF18, RF20, RF25, RF30, RF31, RF36, RF38, RF39, RF52–RF59, RF63, RF64 |

Blocos grandes se dividem em sub-blocos (6a, 6b…), cada um com o próprio PR.

## 6. Regras de trabalho (as mesmas do basquete)
- Teste junto: cada controle nasce com um teste que falharia sem ele, e o PR registra que ficou vermelho.
- Decisão nova vira `D-n`; dúvida para o PO vira `Q-n`. Nunca assuma em silêncio.
- Um PR por bloco, com Conventional Commits. CI verde antes do próximo. Não juntar blocos.
- Nunca: `SET FOREIGN_KEY_CHECKS=0`; pular, desativar ou colocar em quarentena um teste; force-push em branch alheia; editar uma
  migration já aplicada.
- Nunca imprimir, ler ou commitar o valor de um segredo. Docs, `.env.example` e PRs citam só o nome da variável.
- Regenerar os diagramas, o catálogo de endpoints e a planilha quando mudar entidade, endpoint ou migration.
