# Catálogo de gateways (pontos de decisão)

| Req | Gateway | Regra | Se falso |
|---|---|---|---|
| RF1 | Dentro do limite da rota? | AuthRateLimitFilter: POST /auth/register ≤ 10 por IP confiável (ClientIpResolver, nunca X-Forwarded-For cru; IPv6 por /64) em 1 h | 429 RATE_LIMITED + Retry-After, corpo com correlationId |
| RF1 | Payload válido? | e-mail RFC 5322 (domínios reservados recusados), senha ≥ 8 caracteres, displayName 3–40, locale pt-BR\|en-US\|es-ES, idade ≥ 13 anos | 422 VALIDATION_ERROR com details por campo |
| RF1 | Nome não é reservado? | ReservedNames.isReserved(displayName) == false (admin, scores, velion, suporte… normalizados) | 422 NAME_RESERVED |
| RF1 | Consentimento LGPD marcado? | dataProcessingConsent == true (obrigatório) · marketing opcional e nunca pré-marcado | 422 CONSENT_REQUIRED |
| RF1 | Teto de hashes livre? | PasswordHasher: semáforo de hashes simultâneos (Argon2id ~19 MiB cada), espera limitada | 503 AUTH_BUSY + Retry-After (nunca 500) |
| RF1 | E-mail disponível? | não existe UserAccount com emailHash == HMAC(e-mail normalizado), checado na escrita curta depois do hash | 409 EMAIL_ALREADY_REGISTERED (mensagem genérica) |
| RF1 | E-mail de verificação enviado? | EmailPort.send (em dev o adaptador mascara o e-mail e nunca loga o token) | conta fica ACTIVE sem e-mail verificado; reenvio no login |
| RF1 | Token de verificação válido? | existe, não expirou (24 h) e não foi usado | 422 TOKEN_INVALID + opção de reenvio |
| RF1 | Admin inicial só pelo cofre? | AdminBootstrap roda só com SCORES_AF_ADMIN_EMAIL e senha forte vindos do cofre (D-5: nenhuma credencial no código, nem de teste) | não cria admin e loga aviso sem valores |
| RF2 | Dentro do limite da rota? | AuthRateLimitFilter por IP confiável + método + rota: login 30/10 min · refresh 120/10 min · forgot 10/h · reset 20/h | 429 RATE_LIMITED + Retry-After |
| RF2 | Tentativa reservada para o e-mail? | PasswordAttemptGuard.tryLogin(emailHash): tryAcquire(auth:login:{emailHash}, 5, 15 min) antes de ler a conta ou calcular hash | 401 AUTH_FAILED idêntico + AuditLog LOGIN_LOCKED |
| RF2 | Teto de hashes livre? | PasswordHasher: semáforo com espera limitada | 503 AUTH_BUSY + Retry-After (libera a reserva) |
| RF2 | Conta existe, ativa e senha confere? | findByEmailHash · status ACTIVE ou PENDING_DELETION (cancela) · Argon2id.verify fora da transação (hash fictício se inexistente) | 401 AUTH_FAILED sem distinguir motivo · AuditLog LOGIN_FAILED (REQUIRES_NEW) |
| RF2 | Família de sessão ativa? | SessionActiveFilter: claim sid tem UserSession não revogada (cache Redis + MySQL) | 401 UNAUTHENTICATED mesmo com assinatura válida |
| RF2 | Origem do refresh confiável? | cookie __Host-refresh presente · Origin == origem do app (cookie SameSite=Strict não basta sozinho) | 401 · cookie apagado |
| RF2 | Refresh válido e não rotacionado? | hash existe · revokedAt == null · replacedBy == null · expiresAt > now | 401 · reuso revoga a família + AuditLog TOKEN_REUSE_DETECTED |
| RF2 | Token de redefinição válido? | hash existe, < 30 min, não usado | 422 TOKEN_INVALID |
| RF2 | Nova senha atende à política? | ≥ 8 caracteres, diferente da atual | 422 WEAK_PASSWORD |
| RF3 | Autenticado e conta ativa? | JWT RS256 válido · família de sessão sid ativa (SessionActiveFilter) · status != DELETED | 401 UNAUTHENTICATED {code, message, details, correlationId} |
| RF3 | Senha atual confere (step-up)? | PasswordAttemptGuard.verifyStepUp: reserva atômica (5 em 15 min) antes do hash; verify fora da transação | 401 AUTH_FAILED · 6º chute: 429 TOO_MANY_ATTEMPTS + Retry-After 900 |
| RF3 | Novo e-mail disponível e não reservado? | emailHash(novo) não existe em outra conta; domínio não reservado | 409 EMAIL_ALREADY_REGISTERED (genérico) |
| RF3 | Nome não é reservado? | ReservedNames.isReserved == false | 422 NAME_RESERVED |
| RF3 | Sessão pertence ao usuário? | session.user == jwt.sub | 404 NOT_FOUND (não revela) + ACCESS_DENIED |
| RF3 | Finalidade válida? | ConsentPurpose conhecido; DATA_PROCESSING não é revogável (equivale a excluir a conta) | 422 INVALID_CONSENT_PURPOSE |
| RF3 | Exportação dentro do limite? | nenhum DataExportJob REQUESTED/READY nas últimas 24 h | 429 EXPORT_RATE_LIMITED com o job vigente |
| RF3 | Exclusão confirmada? | senha (step-up) + palavra EXCLUIR\|DELETE\|ELIMINAR | 422 DELETION_CONFIRMATION_MISMATCH |
| RF3 | Trilhas preservadas? | FKs de consentimento, compras, carteira e ledger são RESTRICT: a exclusão anonimiza, nunca apaga a conta | DELETE físico recusado pelo banco |
| RF4 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF4 | Jogador do meu clube? | save.employmentStatus == EMPLOYED && player.clubId == save.clubId (inclui practice squad e IR) | 403 NOT_YOUR_PLAYER |
| RF4 | Ativos do dia de jogo ≤ 46? | count(ACTIVE) ≤ rules.gameDayActives (46) e ≥ mínimo por posição (2 QB, 1 K, 1 P, 1 LS) | 422 GAME_DAY_ACTIVES_INVALID |
| RF4 | Elenco ≤ 53? | count(ACTIVE + INACTIVE) ≤ rules.rosterMax (53) · IR e practice squad não contam | 422 ROSTER_FULL |
| RF4 | Vaga no practice squad? | count(PRACTICE_SQUAD) < rules.practiceSquadMax (16) e jogador elegível (experienceYears ≤ 3 ou vaga de veterano) | 422 PRACTICE_SQUAD_FULL |
| RF4 | Elevação permitida? | practiceSquadMember.elevationsUsed < 3 na temporada e ativos < 46 (elevado conta como ativo só no jogo) | 422 ELEVATION_LIMIT |
| RF4 | IR permitida? | player.injury != null && injury.weeksOut ≥ 4 | 422 IR_NOT_ALLOWED |
| RF4 | Retorno da IR liberado? | semanas na IR ≥ 4 e jogador recuperado (injury == null ou weeksOut == 0) e elenco < 53 | 422 IR_RETURN_NOT_ALLOWED |
| RF4 | Dead money cabe no teto? | CapEngine: capSpace − deadMoneyIfCut ≥ 0 na temporada (ou dividido em 2 se após o 1º de junho do jogo) | 422 CAP_EXCEEDED (dispensa bloqueada) |
| RF4 | Consumível aplicável? | item do usuário não aplicado, sem aplicação ativa da mesma categoria, efeito compatível com a posição | 422 CONSUMABLE_NOT_APPLICABLE |
| RF5 | Mundo editável pelo usuário? | world.ownerUser == jwt.sub ou (world.ownerUser == null && role == ADMIN) | 403 + ACCESS_DENIED |
| RF5 | Mundo semente sem ser ADMIN? | world.ownerUser == null && role != ADMIN | edita diretamente |
| RF5 | Mundo é TEMPLATE? | world.kind == TEMPLATE (mundos SAVE são cópias congeladas) | 422 WORLD_NOT_EDITABLE |
| RF5 | Regras da liga válidas? | ruleset PRO\|COLLEGE · quarterMinutes 5–15 · playClock 40/25 · overtimeMinutes 0–15 · kickoffYardLine 30–40 · rosterMax ≥ gameDayActives · practiceSquadMax 0–16 · salaryFloor ≤ salaryCap | 422 RULES_INVALID com campos |
| RF5 | Estrutura válida? | teamCount 8–32 · conferências 1–2 · cada franquia em exatamente 1 divisão · divisões equilibradas (±1) · vagas de playoff ≤ franquias da conferência | 422 STRUCTURE_INVALID |
| RF5 | Jogador válido? | posição no catálogo AF (QB…LS) · unidade coerente · subatributos 0–100 · altura e peso na faixa da posição | 422 VALIDATION_ERROR |
| RF5 | Nome único no mundo? | liga ou franquia sem homônimo no mesmo mundo | 409 NAME_TAKEN |
| RF5 | Elenco cabe no rosterMax? | players(club) < league.rules.rosterMax (53) ao criar jogador | 422 ROSTER_FULL |
| RF5 | Mídia válida? | logo, decalque do capacete e textura do uniforme PNG\|SVG ≤ 1 MB, ≤ 1024 px, conteúdo verificado (RNF11) | 422 MEDIA_INVALID |
| RF5 | Exclusão segura? | liga sem franquias / franquia sem jogadores (ou cascata confirmada) e nenhuma carreira usando o mundo | 409 IN_USE |
| RF6 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF6 | Há jogo nesta semana? | existe Fixture SCHEDULED do clube na semana atual (Week.byeClubs não contém o clube) | semana de folga: edita para a próxima semana (aviso BYE_WEEK) · fim de temporada → 409 NO_NEXT_GAME (RF35) |
| RF6 | Depth chart completo? | depth 1 em todas as posições da formação base do ataque (11) e da defesa base (11) + K, P, LS, KR, PR · QB com depth ≥ 2 | 422 DEPTH_CHART_INCOMPLETE (slots destacados) |
| RF6 | Titulares disponíveis? | nenhum jogador OUT, IR ou suspenso em depth 1 · QUESTIONABLE/DOUBTFUL geram aviso | 422 STARTER_UNAVAILABLE |
| RF6 | Só os 46 ativos? | todo jogador do depth chart tem rosterStatus ACTIVE (≤ gameDayActives) | 422 NOT_GAME_DAY_ACTIVE |
| RF6 | Fora de posição? | OverallByPosition.outOfPosition(player, slot.position) | sem aviso · se sim: aviso com o overall na posição, não bloqueia |
| RF6 | Plano de jogo coerente? | tempo HURRY_UP exige profundidade ≥ 2 em WR e RB · runPassBalance 0–100 · jogadores-chave entre os ativos | aviso de fit reduzido (não bloqueia) · jogador-chave inativo = 422 KEY_PLAYER_INVALID |
| RF6 | Versão otimista confere? | DepthChart.version (ou GamePlan) == versão enviada | 409 CONFLICT · recarrega |
| RF7 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF7 | Head coach empregado? | save.employmentStatus == EMPLOYED && save.club != null | 403 SPECTATOR_MODE (só leitura, RF37) |
| RF7 | Jogo SCHEDULED nesta semana? | fixture do clube na semana atual com status SCHEDULED | semana de folga: Jogar vira Simular semana (só jogos da IA) · fim de temporada: 409 NO_NEXT_GAME |
| RF7 | Partida já em andamento? | existe MatchSession não FINISHED para a fixture | cria nova sessão |
| RF7 | Semana anterior concluída? | todas as fixtures da semana anterior FINISHED (job da IA terminou) | 409 WEEK_NOT_COMPLETED · aguarda job |
| RF7 | Depth chart pronto? | DepthChart.validated == true (RF6), sem titular OUT/IR | 422 DEPTH_CHART_INVALID · Jogar desabilitado |
| RF7 | Modelos 3D disponíveis? | player.model3dUrl e stadium.model3dUrl resolvem no S3 | fallback: tokens 2D com número e campo padrão |
| RF7 | Nível de controle da partida? | settings.playCallingLevel: NONE → simula sem paradas · KEY_MOMENTS → para em 4ª descida, 2 minutos e prorrogação (RF62) · ALL_PLAYS → chama toda jogada (Q-2) | padrão KEY_MOMENTS |
| RF7 | Movimento reduzido? | settings.reducedMotion == true ou prefers-reduced-motion | LED com animações e transições |
| RF8 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF8 | Empregado com contrato ACTIVE? | save.employmentStatus == EMPLOYED && contract.status == ACTIVE | 409 NOT_EMPLOYED (painel só leitura + propostas RF9) |
| RF8 | Pedido de aumento permitido? | ≥ 4 semanas desde o último pedido, nenhum pendente e moralClub ≥ 50 · fora dos playoffs em andamento | 422 RAISE_NOT_ALLOWED |
| RF8 | Diretoria aceita o aumento? | score = 0,5·moralClub + 0,3·(vitórias − expectedWins)·10 + 0,2·(playoffs e títulos) ≥ 65 e caixa cabe | score ≥ 50 → contraproposta (metade) · < 50 → recusa e moralClub −5 |
| RF8 | Mensagem à diretoria válida? | assunto ∈ lista (metas, elenco, teto salarial, estádio, apoio), texto ≤ 500 chars, 1 por semana | 422 MESSAGE_INVALID |
| RF8 | Diretoria concede a verba? | amount ≤ 20 % do caixa × moralClub/100, metas em dia e sem pedido nas últimas 4 semanas | BudgetRequest REJECTED (ou PARTIAL) com resposta no inbox |
| RF8 | Jogador do meu clube? | player.clubId == save.clubId (inclui practice squad e IR) | 403 NOT_YOUR_PLAYER |
| RF8 | Demissão confirmada? | confirm == true e ciente da multa (releaseClause se restam ≥ 2 temporadas) | cancela sem efeito |
| RF9 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF9 | Janela de contratação aberta? | propostas só entre o fim da temporada regular da franquia (eliminada) e o início da FREE_AGENCY, ou após demissão de head coach IA na temporada | sem propostas nesta semana |
| RF9 | Franquia IA quer o head coach? | franquia sem head coach forte, prestígio ≤ f(moralLiga), sem penalidade de recusa (50 % por 4 semanas), sorteio por seed | nenhuma proposta nesta semana |
| RF9 | Proposta PENDING e no prazo? | offer.status == PENDING && offer.expiresWeek ≥ save.currentWeek | 409 OFFER_NOT_PENDING |
| RF9 | Diretoria libera o head coach? | se EMPLOYED: oferta cobre a multa (salary × seasons ≥ releaseClause) ou moralClub < 40 | 422 BOARD_BLOCKS_TRANSFER · proposta REJECTED |
| RF9 | Elenco pesa na decisão? | moral média do elenco ≥ 70 → aviso: jogadores pedem para ficar | sem alerta |
| RF9 | Transação única ok? | novo contrato ACTIVE, antigo TRANSFERRED, save.club, club.manager, head coach IA demitido, demais ofertas EXPIRED · versões conferem | rollback · 409 CONFLICT |
| RF10 | Autenticado e conta ativa? | JWT RS256 válido · família de sessão sid ativa (SessionActiveFilter) · status != DELETED | 401 UNAUTHENTICATED {code, message, details, correlationId} |
| RF10 | Valores válidos? | simQuarterMinutes 1–15 (tempo real por quarto, RNF2) · playCallingLevel ∈ KEY_MOMENTS\|ALL_PLAYS\|NONE · decisionTimeoutSeconds 10–60 · difficulty ∈ EASY\|NORMAL\|HARD\|LEGEND · units ∈ YARDS\|METERS · fontScale na lista | 422 VALIDATION_ERROR |
| RF10 | Item visual possuído? | tema, arte ou trilha é padrão ou existe Purchase do usuário para o StoreItem | 422 ITEM_NOT_OWNED |
| RF10 | Partida ao vivo em andamento? | existe MatchSession LIVE do usuário | aplica já · se sim: duração e nível de controle valem a partir do próximo jogo (o snapshot guarda os da partida) |
| RF10 | Dificuldade afeta carreiras existentes? | save.difficulty é fixada na criação · só novas carreiras usam o valor | aviso na tela (não bloqueia) |
| RF10 | Movimento reduzido forçado pelo SO? | prefers-reduced-motion: reduce | usa reducedMotion salvo |
| RF11 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF11 | Há slot livre? | saves ACTIVE do usuário < 5 | 422 SLOT_LIMIT (arquive ou exclua uma carreira) |
| RF11 | Nome válido e único? | 3–40 chars e sem homônimo entre os saves do usuário | 422 NAME_INVALID / 409 NAME_TAKEN |
| RF11 | Sem partida ao vivo? | nenhuma MatchSession LIVE\|HALFTIME\|DECISION do save | 409 MATCH_IN_PROGRESS |
| RF11 | Exclusão confirmada? | confirm == nome do save digitado | 422 CONFIRMATION_REQUIRED |
| RF11 | Versão otimista confere? | CareerSave.version == versão enviada | 409 CONFLICT |
| RF12 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF12 | Head coach empregado? | save.employmentStatus == EMPLOYED && save.club != null | 403 SPECTATOR_MODE (só leitura, RF37) |
| RF12 | Caixa cobre o custo? | club.cash ≥ custo (setor novo = assentos × preço-base do tipo · reforma = upgradeCost × (100 − condição)/100 · cobertura retrátil ou domo = custo fixo alto) | 422 INSUFFICIENT_CASH (sugere empréstimo RF14) |
| RF12 | Capacidade dentro da faixa? | 60 000 ≤ Σ seats ≤ min(80 000, 60 000 + 5 000 × (level − 1)) | 422 CAPACITY_LIMIT |
| RF12 | Obra de cobertura na offseason? | troca de roof só com season.phase ∈ OFFSEASON\|DRAFT\|FREE_AGENCY (sem jogos em casa) | 422 ROOF_CHANGE_IN_SEASON |
| RF12 | Cota de camarotes e carnês ok? | LUXURY_SUITES ≤ 5 % dos assentos · carnês ≤ 70 % · desconto de carnê 0–30 % | 422 POLICY_INVALID |
| RF12 | Preço dentro da faixa? | ticketPrice entre min e max do tipo (LUXURY_SUITES > SIDELINE_CLUB > LOWER_BOWL > END_ZONE > UPPER_BOWL) e ≤ 3× média da liga | 422 PRICE_OUT_OF_RANGE |
| RF12 | Setor removível? | type != LOWER_BOWL, capacidade continua ≥ 60 000 e sem obra em andamento | 422 SECTOR_REQUIRED |
| RF12 | Obra em andamento? | setor com semanas de obra > 0 | aplica imediatamente |
| RF12 | Diretoria autoriza obra grande? | custo > 30 % do caixa exige moralClub ≥ 40 | 422 BOARD_REFUSED + mensagem no inbox |
| RF12 | Clima afeta o jogo em casa? | roof == OPEN e climateZone fria ou chuvosa → Fixture.weather sorteado por seed (vento, chuva, neve) entra no motor (RF42) · DOME → clima neutro | clima neutro |
| RF13 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF13 | Mensagem aberta? | message.saveId == id && status ∈ UNREAD\|READ | 409 MESSAGE_CLOSED |
| RF13 | Opção válida? | chosenOption ∈ message.options | 422 OPTION_INVALID |
| RF13 | Prazo respeitado? | deadlineWeek == null ou deadlineWeek ≥ save.currentWeek (prazo de trocas e free agency conferidos na fase da temporada) | 409 DEADLINE_PASSED · Scheduler expira com a opção padrão |
| RF13 | Resposta muda outro agregado? | kind ∈ TRADE_PROPOSAL (RF50) · FA_OFFER (RF65) · CONTRACT_REQUEST (RF49) · JOB_OFFER (RF9) · BUDGET_RESPONSE (RF14) · INJURY_UPDATE (RF64) | efeito só de moral e relacionamento |
| RF13 | Reação positiva? | MoraleEngine: score(opção, moral, personalidade, seed) ≥ limiar | resposta negativa · moral −3 a −8 · possível nova mensagem |
| RF13 | Narrativa por IA disponível? | AiNarrativePort responde no tempo e o usuário tem consentimento AI_ANALYST | texto por template (fallback) |
| RF13 | Notificação do usuário? | notification.userId == jwt.sub | id ignorado (idempotente) |
| RF14 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF14 | Head coach empregado? | save.employmentStatus == EMPLOYED && save.club != null | 403 SPECTATOR_MODE (só leitura, RF37) |
| RF14 | Dinheiro não vira espaço no teto? | empréstimo e verba só alteram club.cash · capSpace = league.salaryCap − committed − deadMoney + rollover (SalaryCapLedger, RF49) | regra fixa: nenhuma operação de caixa mexe no teto |
| RF14 | Empréstimo dentro do limite? | principal + Σ saldo devedor ≤ 2 × receita da temporada anterior · ≤ 2 empréstimos ativos · prazo 8–72 semanas | 422 LOAN_LIMIT_EXCEEDED |
| RF14 | Parcela cabe na receita? | installment ≤ 35 % da receita média semanal (jogos em casa + TV + patrocínio) | 422 INSTALLMENT_TOO_HIGH (sugere prazo maior) |
| RF14 | Quitação antecipada possível? | club.cash ≥ saldo devedor × 0,97 | 422 INSUFFICIENT_CASH · oferece alongamento |
| RF14 | Pedido de verba permitido? | nenhum BudgetRequest PENDING · último há ≥ 4 semanas · amount ≤ 20 % do caixa × moralClub/100 | 422 BUDGET_REQUEST_NOT_ALLOWED |
| RF14 | Diretoria concede a verba? | FinanceEngine: moralClub ≥ 50, metas em dia, caixa projetado ≥ 0 → APPROVED · 40–49 → PARTIAL (50 %) · < 40 → REJECTED | REJECTED/PARTIAL + boardResponse no inbox · moralClub −2 se REJECTED |
| RF14 | Parcela debitada na semana? | WeekCompleted: para cada BankLoan ativo, cash −= installment · remainingWeeks − 1 | caixa negativo → ledger FINE (2 %) e alerta da diretoria (fator financeiro RF41) |
| RF15 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF15 | Estilo vendável? | StoreItem.kind == COACH_STYLE && ManagerStyle.rewardGoal == null (estilos por meta não são vendidos) | 422 STYLE_REWARD_ONLY |
| RF15 | Já possui o estilo? | existe Purchase do usuário para o mesmo StoreItem (ou recompensa concedida) | segue a compra (Idempotency-Key) |
| RF15 | Saldo suficiente? | wallet.balance ≥ priceScoresPoints (débito atômico + PointsTransaction PURCHASE) | 422 INSUFFICIENT_POINTS |
| RF15 | Meta concluída concede estilo? | Goal.status == COMPLETED && ManagerStyle.rewardGoal == goal.code | só Scores Points (RF33) |
| RF15 | Estilo possuído pelo usuário? | Purchase do usuário (compra ou recompensa) para o código do estilo | 422 STYLE_NOT_OWNED |
| RF15 | Troca de estilo permitida? | só na PRESEASON ou OFFSEASON (Season.phase), no máximo 1 troca por temporada | 422 STYLE_CHANGE_WINDOW_CLOSED |
| RF16 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RF16 | Liga pertence ao mundo do save? | league.worldId == save.worldId | 404 LEAGUE_NOT_FOUND |
| RF16 | Semana válida? | 1 ≤ n ≤ total de semanas da temporada (regular + playoffs) | 404 WEEK_NOT_FOUND |
| RF16 | Semana já jogada? | todas as Fixtures da semana FINISHED | atual: placares LIVE e parciais sem destaques · futura: agenda e favoritos |
| RF16 | Empate na classificação? | mesmo winPct → TiebreakerPolicy em ordem: confronto direto, recorde na divisão, jogos em comum, recorde na conferência, força das vitórias, força da tabela, saldo de pontos, sorteio por seed | ordem direta por winPct |
| RF16 | Seed de playoff definido? | campeões de divisão nos seeds 1–4 da conferência e 3 wild cards nos 5–7 · seed 1 com folga | mostra posição provisória (em disputa) |
| RF17 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF17 | Liga do mundo do save? | league.worldId == save.worldId | 404 LEAGUE_NOT_FOUND |
| RF17 | Qual troféu? | FINAL: vencedor do PlayoffMatchup FINAL · CONFERENCE: vencedor da final de conferência · DIVISION: divisionRank == 1 ao fim da temporada regular | sem troféu na categoria |
| RF17 | Há títulos do head coach? | ManagerCareerRecord.titles > 0 ou título de divisão/conferência ou SeasonAward COACH do manager humano | estante vazia com metas de troféu (RF33) |
| RF17 | Asset 3D disponível? | GLB do troféu por tipo (final, conferência, divisão) existe no S3 | troféu genérico (GLB padrão) |
| RF18 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF18 | Ranking em cache? | ZSET managers:rank:{worldId}:{metric} existe (atualizado no WeekCompleted e no fim de temporada) | recalcula do MySQL e repovoa o ZSET |
| RF18 | Head coach do mundo do save? | manager.worldId == save.worldId (IA ou humano do save) | 404 MANAGER_NOT_FOUND |
| RF18 | Moral disponível? | humano: ManagerMorale do save · IA: reputationLeague e campanha × expectedWins da franquia | exibe só campanha, títulos e overall |
| RF19 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF19 | Fase permite a operação? | free agency: season.phase ∈ {FREE_AGENCY, PRESEASON, REGULAR} · troca: currentWeek ≤ tradeDeadlineWeek e fora dos playoffs · waivers: temporada regular ou pré-temporada | 409 MARKET_CLOSED (prazo de trocas ou fase) |
| RF19 | Não há compra entre clubes? | jogador com contrato só muda de franquia por TradeProposal; FreeAgentOffer só para rosterStatus FREE_AGENT; claim só para WAIVED | 422 PLAYER_NOT_AVAILABLE |
| RF19 | Cabe no teto rígido? | SalaryCapLedger.space ≥ cap hit do ano corrente (salário + bônus rateado) · troca: capImpact dos dois lados ≥ 0 de espaço | 422 CAP_SPACE_EXCEEDED |
| RF19 | Elenco tem vaga? | ativos < LeagueRules.rosterMax (53) ou saída prevista na própria troca | 422 ROSTER_FULL (corte ou practice squad antes) |
| RF19 | Jogador aceita a oferta? | MarketEngine: valor anual, garantia, prestígio, chance de título, tempo de jogo previsto pelo depth chart, moral do head coach com a liga · u(seed) < playerInterest | FreeAgentOffer REJECTED ou outra franquia vence · moral −1 |
| RF19 | Franquia IA aceita a troca? | MarketEngine.tradeValue: valor dos jogadores (overall por posição, idade, contrato) + tabela de valor de picks · ganho ≥ 0 e necessidade de posição · senão COUNTER pedindo pick ou jogador | TradeProposal REJECTED ou COUNTERED (prazo 1 semana) |
| RF19 | Contraproposta ainda válida? | status == COUNTERED e prazo ≥ semana atual e antes do prazo de trocas | 409 OFFER_NOT_PENDING (EXPIRED) |
| RF19 | Prioridade de waiver? | entre claims PENDING do mesmo jogador vence a menor priority (pior campanha) · claim vencido vai ao fim da fila | WaiverClaim LOST |
| RF19 | Transação única ok? | @Version: contratos, rosterStatus, picks (ownerClub), SalaryCapLedger dos dois clubes, status da proposta e propostas concorrentes EXPIRED | rollback · 409 CONFLICT |
| RF20 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF20 | Consulta válida? | q com 2–60 caracteres ou ao menos um filtro · type e position no vocabulário único · size ≤ 50 | 422 VALIDATION_ERROR |
| RF20 | Índice disponível? | scores.persistence.search=opensearch e cluster saudável | fallback no MySQL do mundo do save com aviso no header |
| RF20 | Resultado do mundo do save? | filtro obrigatório worldId == save.worldId na query | descartado (isolamento entre saves) |
| RF20 | Índice atualizado? | RosterChanged, TradeCompleted, CatalogChanged e MatchFinished reindexados AFTER_COMMIT (idempotente) | retry com backoff · MySQL prevalece no esquema ampliado |
| RF21 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF21 | Semana de bye? | save.club ∈ week.byeClubs (1 bye por franquia entre as semanas 5 e 14) | mostra o jogo da semana |
| RF21 | Insights das próximas semanas gerados? | CalendarInsight existe para as fixtures das próximas 3 semanas (gerado no WeekCompleted e no início da temporada) | gera por template agora e agenda narrativa assíncrona |
| RF21 | Clássico de divisão? | fixture.isDivisional (6 jogos por temporada contra os 3 rivais da divisão) | segue a classificação por importância |
| RF21 | Jogo decisivo? | a partir da semana 14: os dois disputam título de divisão ou vaga de seed 1–7 (PlayoffSeed projetado) ou é jogo de playoff | PRIMETIME se THU/SUN/MON_NIGHT · REVENGE se perdeu o último confronto · TRAP antes do bye ou após prime time · senão REGULAR |
| RF21 | Narrativa via Claude? | scores.ai.narrative=claude, rate-limit ok e circuito fechado | template determinístico pela seed · AuditLog AI_CALL só quando chamou o provedor |
| RF21 | Arte dinâmica disponível? | imageKey por importância e cores das franquias existe no S3 | SVG padrão no frontend com as cores do capacete |
| RF22 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF22 | Jogador do mundo do save? | player.worldId == save.worldId | 404 PLAYER_NOT_FOUND |
| RF22 | Jogador do meu elenco? | player.club == save.club (ativo, practice squad ou IR) | modo leitura: só Ver e Propor troca / Oferta de FA (RF19) |
| RF22 | Fora de posição? | slot do depth chart ≠ player.position → overall da posição do slot | sem aviso · se fora: selo e overall da posição usada |
| RF22 | Mudança de status permitida? | ACTIVE ≤ rosterMax 53 · PRACTICE_SQUAD ≤ 16 e experienceYears ≤ 2 (ou vaga de veterano) · IR só com lesão de weeksOut ≥ 4 | 422 ROSTER_RULE_VIOLATION (motivo) |
| RF22 | Extensão cabe no teto? | CapEngine: cap hit dos anos futuros ≤ espaço projetado de cada temporada | 422 CAP_SPACE_EXCEEDED |
| RF22 | Jogador aceita a extensão? | MarketEngine: valor anual e garantia ≥ pedida(overall, idade, posição) × (1 − bônus de moral) | 422 EXTENSION_REJECTED com contraproposta · moral −2 |
| RF22 | Franchise tag possível? | contract.endSeason == season.year · nenhuma tag usada pela franquia nesta temporada · espaço para o valor da tag | 422 FRANCHISE_TAG_NOT_ALLOWED |
| RF22 | Confirmou o dead money? | release exige confirmDeadMoney = true após ver deadMoneyIfCut | 200 com prévia do dead money, sem dispensar |
| RF23 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF23 | WebGL disponível? | canvas.getContext('webgl2') != null | fallback 2D: arte do card e descrição dos estilos |
| RF23 | Jogador tem modelo próprio? | player.model3dUrl != null | modelo genérico pelo biotipo da posição (OL pesado, WR esguio) e altura/peso |
| RF23 | Grade do capacete por posição? | QB/WR/DB: 2 barras · RB/LB/TE: intermediária · OL/DL: gaiola · K/P: 1 barra | grade padrão |
| RF23 | Há clipe da posição? | AnimationClip PUBLISHED com target POSITION e code == player.position | locomoção genérica (andar, acelerar, parar) |
| RF23 | Animação da loja aplicada? | Purchase do item PLAYER_ANIMATION_3D aplicada a player.id | clipe natural do estilo ou da posição |
| RF23 | reducedMotion ativo? | settings.reducedMotion == true | loop da animação · se ativo: pose estática e botão Reproduzir uma vez |
| RF23 | Asset em cache? | GLB no cache do navegador ou CDN (ETag) | download do S3 com barra de progresso |
| RF24 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF24 | Mensagem pede decisão? | message.options não vazio | só marca READ |
| RF24 | Ainda pendente? | status ∈ {UNREAD, READ} e chosenOption == null | 409 MESSAGE_ALREADY_ANSWERED |
| RF24 | Dentro do prazo? | save.currentWeek ≤ message.deadlineWeek | EXPIRED · consequência padrão (ex.: holdout continua, diretoria assume recusa, moral −3) |
| RF24 | Opção válida? | option ∈ message.options | 422 INVALID_OPTION |
| RF24 | Quem é o remetente? | BOARD → Diretoria · PLAYER (mais snaps, holdout, pedido de troca) → JogadorIA · CLUB / AGENT → MarketEngine · PRESS → Imprensa · LEAGUE → multa/suspensão | — |
| RF24 | Resposta exige recurso? | consequência debita caixa ou mexe em contrato (cabe no teto, RF49) | só moral · se exige: 422 INSUFFICIENT_CASH / CAP_SPACE_EXCEEDED |
| RF24 | Resposta abre negociação? | refType ∈ {TradeProposal, FreeAgentOffer} e opção COUNTER | encerra o tópico · se abre: atualiza a proposta (RF19) |
| RF25 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF25 | Jogador do elenco do save? | player.club == save.club (ativo ou practice squad) | 404 NOT_FOUND |
| RF25 | Consumível existe e está ativo? | Consumable.code no catálogo do mundo do save | 404 ITEM_NOT_FOUND |
| RF25 | Compatível com a posição? | carta de estilo: player.position ∈ PlayStyle.positions (ex.: Deep Threat só WR/TE) · foco de treino: atributo relevante para a posição | 422 CONSUMABLE_INCOMPATIBLE_POSITION |
| RF25 | Recuperação permitida? | RECOVERY reduz 1 semana de Injury.weeksOut · nunca tira do IR antes do mínimo · sem efeito em lesão encerrada | 422 RECOVERY_NOT_ALLOWED |
| RF25 | Teto de efeito (RNF12)? | ≤ 1 ativo por categoria por jogador · ≤ 3 estilos ativos · teto de bônus por unidade (ataque, defesa, especiais) | 409 CONSUMABLE_ALREADY_ACTIVE / 422 PLAYSTYLE_LIMIT |
| RF25 | Moeda e saldo? | priceCash: caixa do clube ≥ preço → FinanceLedgerEntry CONSUMABLE · priceScoresPoints: PointsService.debit na carteira do save (@Version) | 422 INSUFFICIENT_FUNDS / INSUFFICIENT_POINTS |
| RF25 | Expirou? | WeekCompleted: expiresWeek < currentWeek → active = false e estilo CONSUMABLE removido | segue ativo |
| RF26 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF26 | Head coach empregado? | save.employmentStatus == EMPLOYED | somente visualização 3D |
| RF26 | Sem partida ao vivo? | nenhuma MatchSession LIVE ou BREAK da franquia | 409 STADIUM_LOCKED_DURING_MATCH |
| RF26 | Obra grande na offseason? | cobertura, superfície ou +5 mil lugares só com season.phase ∈ {OFFSEASON, DRAFT, FREE_AGENCY} | 409 OFFSEASON_ONLY |
| RF26 | Capacidade dentro da faixa? | 60 000 ≤ capacidade final ≤ 80 000 e ≤ capMax(stadium.level) | 422 CAPACITY_OUT_OF_RANGE |
| RF26 | Caixa cobre a obra? | club.cash ≥ custo (S$ do caixa, fora do teto salarial) | 422 INSUFFICIENT_CASH · sugere empréstimo (RF14) |
| RF26 | Diretoria aprova obra grande? | custo ≤ 25 % do caixa ou reputationBoard ≥ 60 | 422 BOARD_VETO com mensagem no inbox |
| RF26 | Setor removível? | restam ≥ 2 setores, setor ≠ LOWER_BOWL e capacidade final ≥ 60 000 | 422 MIN_SECTORS |
| RF26 | Preço plausível? | 0 < ticketPrice ≤ 5 × preço médio da liga | 422 TICKET_PRICE_OUT_OF_RANGE |
| RF26 | Item da loja: possuído, saldo e compatível? | sem Purchase anterior · saldo ≥ preço · modelo compatível com roof (DOME exige cobertura) | 409 ITEM_ALREADY_OWNED / 422 INSUFFICIENT_POINTS / 422 STADIUM_MODEL_INCOMPATIBLE |
| RF27 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF27 | Semana já iniciada? | existe MatchSession da semana ou job WeekSimulation RUNNING/DONE | botão Jogar semana (POST /weeks/current/play) |
| RF27 | Franquia joga nesta semana? | employmentStatus == EMPLOYED, save.club ∉ week.byeClubs e Fixture.isUserFixture | bye ou espectador (RF37): só placares, sem controles |
| RF27 | Snapshot vivo no Redis? | GET match:{fid}:snapshot != null | fallback MySQL (MatchSession do último fim de drive) e recria a chave |
| RF27 | Placar clicado é o meu? | fixture.isUserFixture | painel somente leitura da partida da IA (placar, drives, líderes) |
| RF27 | Chamada de jogada pendente? | PlayCallDecision OPEN (4ª descida, 2 minutos finais, prorrogação · ou ALL_PLAYS) | avança · se pendente: 409 DECISION_PENDING até escolher ou o prazo vencer (escolha automática, RF62) |
| RF27 | Modo de avanço permitido? | DRIVE, QUARTER e FULL só sem decisão pendente e fora de BREAK · PLAY sempre | 422 ADVANCE_MODE_NOT_ALLOWED |
| RF27 | Há jogadas novas? | PlayEvent com seq > after | 204 · próximo poll em 2 s |
| RF27 | Fim de quarto ou intervalo? | clockSeconds == 0 no 1º, 2º ou 3º quarto | segue LIVE · se sim: BREAK e playbook (RF29) |
| RF28 | Pode ler o mundo? | mundo padrão ou world.ownerUser == jwt.sub | 403 |
| RF28 | Franquia editável? | mundo modelo do usuário ou, na carreira, club.id == save.club do próprio save | 422 WORLD_READ_ONLY / 403 + AuditLog ACCESS_DENIED |
| RF28 | Versão atual? | expectedVersion igual à de club / helmet / uniform | 409 IDENTITY_VERSION_CONFLICT |
| RF28 | Identidade válida? | #RRGGBB · número ≥ 4,5:1 sobre a camisa · contorno ≥ 3:1 · padrão no vocabulário único | 422 IDENTITY_INVALID + violações |
| RF28 | Capacete válido? | StripeStyle no vocabulário · decal ≥ 3:1 sobre a casca · grade distinguível da casca | 422 HELMET_INVALID |
| RF28 | Casa e fora distinguíveis? | KitClashPolicy: HOME × AWAY com ΔE ≥ 30 (AWAY claro por padrão) | 422 KIT_CLASH |
| RF28 | Logo válido (RNF11)? | PNG/WebP/SVG saneado, ≤ 2 MB, magic bytes, antivírus, ≤ 1024 px | 422 MEDIA_INVALID / MEDIA_INFECTED |
| RF28 | Item da loja possuído? | Purchase(user, item) existe | 422 ITEM_NOT_OWNED (ir à loja RF32) |
| RF29 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF29 | Bola parada? | session.status ∈ {BREAK, HALFTIME} (fim do 1º e 3º quarto, intervalo, antes da prorrogação) | 422 ADJUSTMENT_NOT_ALLOWED |
| RF29 | Versão do snapshot confere? | command.version == session.version | 409 STALE_SNAPSHOT (recarrega o painel) |
| RF29 | Jogada existe no playbook? | formação e conceito pertencem ao Playbook da franquia (offensiveScheme e defensiveScheme) | 422 PLAY_NOT_IN_PLAYBOOK |
| RF29 | Pesos válidos? | runPassBalance 0–100 · pesos de formação, conceito e cobertura ≥ 0 e somam 100 · blitz no vocabulário Emphasis | 422 WEIGHTS_INVALID |
| RF29 | Troca no depth chart elegível? | jogador entre os 46 ativos do jogo, sem lesão OUT, posição compatível (ou aviso de fora de posição) | 422 DEPTH_SWAP_INVALID |
| RF29 | Unidades completas? | 11 por unidade: QB e 5 OL no ataque base · K, holder e LS no FG/PAT · retornador no KR/PR | 422 DEPTH_CHART_INCOMPLETE |
| RF29 | Lesão de titular no jogo? | titular saiu lesionado no quarto anterior | sem aviso · se sim: substituto do depth chart já entrou e o painel destaca a posição |
| RF29 | Prorrogação? | fim do 4º empatado (e LeagueRules permite OT) | sem BREAK extra · se sim: BREAK antes do OT com cara ou coroa |
| RF30 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF30 | Semana concluída? | todas as Fixtures da semana FINISHED, incluindo o jogo de segunda à noite | tabela provisória com placares parciais (badge Em andamento) |
| RF30 | Seeds da conferência? | seeds 1–4: campeões de divisão por campanha · seeds 5–7: wild cards (melhores não campeões) · empate conta meia vitória no winPct | — |
| RF30 | Empate na tabela? | mesmo winPct → confronto direto → campanha na divisão → jogos em comum → campanha na conferência → força das vitórias → força da tabela → saldo de pontos | ordem direta · tiebreakerNote explica o critério usado |
| RF30 | Notícias da semana geradas? | existe NewsItem(season, week) na timeline | gera no WeekCompleted: template determinístico pela seed, Claude opcional |
| RF30 | Narrativa via Claude? | scores.ai.narrative == claude e circuito fechado | template determinístico + AuditLog AI_CALL com falha |
| RF30 | Fase de playoffs? | season.phase == PLAYOFFS | tabela com seeds projetados · se playoffs: chave + tabela final congelada |
| RF30 | Semana existe? | 1 ≤ n ≤ regularSeasonWeeks + semanas de playoffs | 404 WEEK_NOT_FOUND |
| RF31 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF31 | Partida finalizada? | fixture.status == FINISHED | estimativa do kickoff (badge Estimativa) |
| RF31 | Campo neutro? | fixture.neutralSite (final) | segue · se neutro: renda da liga, sem mandante, só público |
| RF31 | Minha franquia é a mandante? | fixture.home == save.club | só público e atmosfera (renda é do mandante) |
| RF31 | Clima pesa? | stadium.roof == OPEN e Fixture.weather ∈ {frio intenso, chuva, neve} → demanda −5 a −15 % · DOME imune · RETRACTABLE fecha | sem ajuste de clima |
| RF31 | Lotação? | demanda ≥ capacidade (demanda = f(prestígio, campanha, rival de divisão, slot, clima, importância, preço)) | público = demanda · se lotado: público = capacidade e alerta de preço abaixo do mercado |
| RF31 | Preço dinâmico ativo? | ticketPolicy.dynamicPricing | preço fixo por setor · se ativo: ±20 % pela importância (DIVISIONAL, DECISIVE, PRIMETIME) |
| RF31 | Setor vendável? | setor fora de obra ou reforma | setor excluído do público e da renda |
| RF32 | Autenticado? | JWT válido + família de sessão ativa | 401 UNAUTHENTICATED |
| RF32 | Limite da loja? | /api/v1/store/** ≤ 20 por minuto por IP | 429 RATE_LIMITED |
| RF32 | Idempotência? | Idempotency-Key UNIQUE na compra · mesma chave e payload ⇒ 200 com a compra original · payload diferente ⇒ 422 IDEMPOTENCY_CONFLICT | sem chave: 400 IDEMPOTENCY_KEY_REQUIRED |
| RF32 | Produto publicado e disponível? | status PUBLISHED, dentro da janela, asset READY | 409 PRODUCT_UNAVAILABLE |
| RF32 | Preço confiável? | cliente envia só productCode · backend resolve preço base, promoção e preço efetivo · price no payload é ignorado e auditado | — |
| RF32 | Compatível? | animação: posição do jogador alvo · estádio: cobertura · capacete/uniforme: franquia do save | 422 INCOMPATIBLE_ITEM |
| RF32 | Já possui? | cosmético: direito único por usuário e produto | 409 ALREADY_OWNED |
| RF32 | Afeta o motor (RNF12)? | LEGENDARY_PLAYER e CONSUMABLE são GAME_EFFECT: só carteira SAVE, save fora de rankings · lendário precisa de vaga no elenco (53) e espaço no teto | COSMETIC_ONLY: carteira USER, nunca altera atributos |
| RF32 | Carteira e saldo? | SELECT FOR UPDATE + @Version · balance ≥ preço efetivo | 422 INSUFFICIENT_POINTS (saldo e preço) |
| RF32 | Transação única? | ledger PURCHASE + Purchase + direito + inventário + AuditLog na mesma transação | ROLLBACK total |
| RF33 | Dono do save? | save.userId == jwt.sub (CareerAccess.requireOwner) | 403 + AuditLog ACCESS_DENIED |
| RF33 | Evento elegível? | MatchFinished (vitória), WeekCompleted, SeasonEnded (título de divisão, vaga, vitórias de playoff, título), marcas individuais na temporada, ONLINE_* e MULTIPLAYER_* | ignora o evento |
| RF33 | Meta ativa para o modo? | goal ACTIVE e vale para o seasonMode do save | sem progresso |
| RF33 | Progresso atingiu o alvo? | goal.progress ≥ goal.target | só atualiza progresso |
| RF33 | Já recompensada? | existe PointsTransaction(reason, refId) · credit idempotente por refId | ignora (reprocessamento seguro) |
| RF33 | Teto diário (RNF12)? | pontos ganhos hoje + recompensa ≤ 500 | trunca ao teto |
| RF33 | Quem concede ≠ quem consome? | GameRewardService concede · PurchaseService consome · mesma tabela de lançamentos, classes separadas | — |
| RF34 | Autenticado? | JWT válido e conta ACTIVE | 401 |
| RF34 | Slot de carreira livre? | saves ACTIVE do usuário < 5 | 422 CAREER_SLOTS_FULL (arquivar ou excluir em RF11) |
| RF34 | Mundo acessível e liga do mundo? | world.ownerUser nulo (padrão) ou == jwt.sub · league.worldId == world.id | 403 WORLD_FORBIDDEN / 422 LEAGUE_NOT_IN_WORLD |
| RF34 | Franquia existente ou nova válida? | clubId da liga ou newClub com nome único, sigla de 2–4 letras, cores com contraste, divisão com vaga · mundo editável | 422 CLUB_REQUIRED / 409 CLUB_NAME_TAKEN / 422 CLUB_INVALID |
| RF34 | Formato da liga válido? | 8 ≤ teamCount ≤ 32 · teamCount = conferências × divisões × franquias por divisão (Q-3) · ruleset PRO ou COLLEGE (Q-1) | 422 LEAGUE_LAYOUT_INVALID |
| RF34 | Franquia do usuário sobrevive ao recorte? | ao reduzir franquias, a do usuário e a divisão dela ficam · saem as de menor prestígio mantendo divisões equilibradas | — |
| RF34 | Modo, estilo e dificuldade válidos? | seasonMode ∈ {FULL_SEASON, PLAYOFFS_ONLY} · coachStyle ∈ ManagerStyle · difficulty ∈ {EASY, NORMAL, HARD, LEGEND} | 422 com código do campo |
| RF34 | Nome do head coach válido? | 2–40 caracteres, sem HTML, nacionalidade ISO | 422 MANAGER_NAME_INVALID |
| RF34 | Elenco cabe no teto? | SalaryCapLedger.committed ≤ league.salaryCap para cada franquia depois do clone (cortes automáticos da IA nas franquias IA) | IA corta contratos até caber (nunca a franquia do usuário sem aviso) |
| RF34 | Bootstrap concluído? | job CareerBootstrap DONE (idempotente por requestId) | save fica CREATING · retry · FAILED após 3 tentativas |
| RF35 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF35 | Temporada encerrada? | PlayoffMatchup FINAL com winner (ou última semana em PLAYOFFS_ONLY) | 409 SEASON_NOT_FINISHED |
| RF35 | Resumo já computado? | SeasonSummary(seasonId) materializado | job SeasonClose computa (idempotente por seasonId) |
| RF35 | Prêmio com amostra mínima? | MVP/OPOY: ≥ 12 jogos · QB ≥ 224 passes ou não-QB por snaps · DPOY por sacks, INT, tackles e pressões ponderados · OROY/DROY só calouros | candidato excluído |
| RF35 | Contrato do head coach venceu? | contract.endSeason == season.number | segue · se venceu: diretoria propõe renovação (moralClub ≥ 50) ou EXPIRED |
| RF35 | Metas cumpridas? | vitórias ≥ meta · playoffs/título quando exigidos · caixa ≥ 0 | moralClub −15 · possível demissão (RF41) |
| RF35 | Decisões de contrato no prazo? | OFFSEASON: cada contrato vencendo da franquia do usuário tem decisão (renovar, tag, deixar ir) até o fim da fase | não decididos viram agentes livres ao abrir FREE_AGENCY |
| RF35 | Teto da nova temporada respeitado? | SalaryCapLedger(n+1).space ≥ 0 com rollover do espaço não usado | 422 CAP_OVER_LIMIT (cortar ou reestruturar antes de avançar, RF49) |
| RF35 | Pendências resolvidas? | JobOffer PENDING respondidas ou expiradas · FreeAgentOffer do usuário concluídas ao fechar FREE_AGENCY · draft concluído (RF51) | 422 PENDING_DECISIONS (lista o que falta) |
| RF35 | Narrativa via Claude? | scores.ai.narrative == claude e circuito fechado | template determinístico (AuditLog AI_CALL nos dois casos) |
| RF35 | Fase já avançada? | season.phase já é a fase pedida (idempotência) | 200 sem reprocessar |
| RF36 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF36 | Alvo válido? | until ∈ {NEXT_FIXTURE, WEEK_END, REGULAR_SEASON_END, SEASON_END, FIXTURE, WEEK} e alvo no futuro | 422 INVALID_SIMULATION_TARGET |
| RF36 | Sem execução ativa? | uma SimulationRun QUEUED/RUNNING/PAUSED por carreira | 409 SIMULATION_ACTIVE |
| RF36 | Depth chart jogável? | titulares em todas as posições das três unidades, sem IR nem OUT · senão a IA preenche e avisa | IA monta o depth chart da semana e registra no resumo |
| RF36 | Parada obrigatória? | pauseReason ∈ {DECISION, DRAFT, FREE_AGENCY, TRADE_DEADLINE, INJURY (titular fora 3+ semanas), OFFER} | processa a próxima semana |
| RF36 | Parada pedida? | stopRequested == true | segue até o alvo |
| RF36 | Execução pausada? | resume só em PAUSED | 409 SIMULATION_NOT_PAUSED |
| RF37 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF37 | Save continua após demissão? | ManagerDismissed/Resigned: save ACTIVE, employmentStatus UNEMPLOYED, club nulo (nunca ARCHIVED) | nunca ocorre |
| RF37 | Modo espectador? | employmentStatus ∈ {UNEMPLOYED, SPECTATOR} | central normal (RF40) |
| RF37 | Há franquias com vaga elegíveis? | sem head coach forte e tercil de prestígio permitido pela moral com a liga (< 30 inferior · 30–69 inferior e médio · ≥ 70 qualquer) · janela pós-temporada dobra a chance | 0 propostas · notícia mercado frio |
| RF37 | Franquia faz a proposta? | u(seed, semana) < 0,35 + 0,5·moralLiga/100 · máx. 3 · sem penalidade de recusa ativa | franquia pula a semana |
| RF37 | Proposta PENDING e no prazo? | status PENDING e expiresWeek ≥ semana atual | 409 OFFER_NOT_PENDING |
| RF37 | Aceite em transação única? | contrato ACTIVE, save.club, EMPLOYED, head coach IA dispensado, demais ofertas EXPIRED, moral clube 60, ManagerCareerRecord aberto · @Version | rollback · 409 CONFLICT |
| RF37 | Recusa penaliza a franquia? | REJECT → chance × 0,5 por 5 semanas (Redis com TTL) | LATER: segue PENDING até o prazo |
| RF38 | É administrador? | role == ADMIN | 403 |
| RF38 | Alvo conhecido? | target ∈ {ATTRIBUTE, POSITION, PLAY_STYLE, LOCOMOTION} e targetCode no catálogo (≈40 subatributos, 20 posições, estilos) | 422 ANIMATION_UNKNOWN_TARGET |
| RF38 | Parâmetros dentro dos limites biomecânicos? | kind válido (stance, dropback, throw, catch, cut, block, tackle, kick, celebrate) · speed 0,3–2 · crouch 0–0,8 · stance 2 ou 3 pontos · throwArc 0–1 · cutAngle 0–90 · lean −0,5–0,5 · kickSwing 0–1 · accent #rrggbb | 422 ANIMATION_INVALID_PARAMS (ou clamp sinalizado) |
| RF38 | Equipamento respeitado? | clipe não atravessa capacete e ombreiras (colisores do rig AF) | 422 ANIMATION_GEAR_CLIP |
| RF38 | IA disponível? | circuit breaker fechado | TemplateAnimationAdapter determinístico |
| RF38 | É rascunho? | edição só em DRAFT | 409 ANIMATION_NOT_DRAFT |
| RF39 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF39 | Head coach empregado? | save.employmentStatus == EMPLOYED | 409 NOT_EMPLOYED |
| RF39 | Classe do draft gerada? | PRESEASON: DraftClass SCOUTING com 250–300 prospectos pela seed (SeedPolicy.derive(save.seed, 'draft', season)) · trueOverall e potencial ocultos | scouting indisponível até a pré-temporada |
| RF39 | Prospecto da classe atual? | prospect.draftClass == classe da temporada e draftedBy nulo | 404 PROSPECT_NOT_FOUND |
| RF39 | Pontos de scouting na semana? | pontos restantes ≥ custo (relatório 1 ponto, visita 3) · pontos por semana = 4 × scoutingLevel | 422 SCOUTING_POINTS_EXHAUSTED |
| RF39 | Precisão do relatório? | accuracy = 0,5 + 0,08 × scoutingLevel + 0,05 × relatórios anteriores (máx. 0,95) · faixa de potencial encolhe com a precisão | — |
| RF39 | Prospecto de destaque? | nota ≥ 1ª rodada e potencial alto → inbox do olheiro | sem aviso |
| RF39 | Investimento permitido? | scoutingLevel < 5 · caixa ≥ custo · 1 investimento por temporada | 422 SCOUTING_MAX_LEVEL / INSUFFICIENT_CASH / INVEST_COOLDOWN |
| RF39 | Quadro na versão atual? | expectedVersion == board.version | 409 BOARD_VERSION_CONFLICT |
| RF40 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF40 | Head coach empregado? | save.employmentStatus == EMPLOYED | painel em modo espectador (RF37) |
| RF40 | Há próximo jogo? | Fixture SCHEDULED da franquia na temporada | semana de bye: card Semana de folga · fim da temporada: card Avançar (RF35) |
| RF40 | Partida ao vivo? | MatchSession da franquia com status LIVE, BREAK ou AWAITING_PLAY_CALL | card do próximo jogo |
| RF40 | Depth chart com titular indisponível? | slot depth 1 com InjuryReport OUT/IR ou suspenso | sem alerta |
| RF40 | Relatório IA atual? | AiAnalysisReport.week == save.currentWeek | agenda recomputação do ClubAnalyst |
| RF41 | Semana concluída e não processada? | WeekCompleted(saveId, week) AFTER_COMMIT · SETNX morale:processed:{saveId}:{week} | job ignora |
| RF41 | Head coach empregado? | save.employmentStatus == EMPLOYED | só moraleLeague evolui · JobOfferPolicy (RF37) |
| RF41 | Campanha acima da expectativa? | vitórias × vitórias esperadas pelo overall das unidades · margem do jogo × esperada (mando 2,5) · surpresa = tanh(...) | contribuição negativa × rigor da diretoria por dificuldade |
| RF41 | Delta dentro dos limites? | EMA (memória 6 semanas, temporada curta) · deltaClub e deltaLeague em [−10, +6] | clamp e [0, 100] |
| RF41 | Zona crítica com histerese? | moraleClub < 30 → weeksUnderCritical++ · ≥ 35 zera · 30–44 Atenção | Confortável (≥ 45) |
| RF41 | Demitir? | weeksUnderCritical ≥ paciência (EASY 4 · NORMAL 3 · HARD 2 · LEGEND 2) · nunca durante a semana de jogo | aviso da diretoria |
| RF41 | Snaps abaixo do esperado para o papel? | snaps% × esperado por posição e profundidade (QB titular 95 %, OL 95 %, DL em rodízio 60–70 %, RB 50–60 %, WR3 45 %, especialistas não contam) | fator neutro |
| RF41 | Jogador pede troca? | moral < 35 por 3 semanas e (snaps abaixo do esperado ou contrato no último ano sem extensão) | jogador estável (moral < 50 só ouve rumores) |
| RF41 | Outra franquia propõe troca pelo jogador? | TradeInterestPolicy: pedido de troca, contrato, overall, necessidade da posição · antes do prazo de trocas | sem proposta |
| RF41 | Decisão do inbox suportada? | option ∈ opções do kind (ACCEPT, REJECT, NEGOTIATE, REASSURE, PROMISE_SNAPS, CONFRONT…) | 422 OPTION_NOT_SUPPORTED |
| RF41 | Moral entra no RF42? | ContextFactors.forMatch(moralClub, moralLiga, moralJogador) → ±3 % nas chances por unidade · performanceModifier até ±5 % por jogador | neutro (moral 50) |
| RF42 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF42 | Depth chart válido? | 11 titulares no ataque e 11 na defesa para a formação base · K, P, LS, holder, KR e PR · nenhum IR, OUT ou suspenso · 46 ativos (LeagueRules.gameDayActives) | 422 DEPTH_CHART_INVALID (lista as vagas) |
| RF42 | Momento decisivo para o humano? | playCallingLevel ALL_PLAYS, ou KEY_MOMENTS e (4ª descida, últimos 2 min de cada tempo, prorrogação, conversão após TD) com posse ou defesa da franquia do usuário | PlayCaller IA chama pelo GamePlan (tendências por situação) |
| RF42 | Chamada dentro do prazo? | now ≤ decision.deadlineAt (UserSettings.decisionTimeoutSeconds) | decisão automática do PlayCaller IA (auto = true) |
| RF42 | Chamada válida e versão atual? | PlayDefinition do Playbook da franquia, lado correto, opção permitida na situação (FG só ao alcance do K), expectedVersion == session.version | 422 PLAY_CALL_INVALID / 409 STALE_VERSION |
| RF42 | Falta antes do snap? | u < P(false start \| ruído do estádio, disciplina) ou P(offside/encroachment \| pass rush, disciplina) | snap normal |
| RF42 | Turnover na jogada? | sack-fumble, fumble do portador (proteção da bola × impacto) ou INT (precisão × cobertura × pressão) | segue com o resultado em jardas |
| RF42 | Falta durante a jogada? | u < P(holding, DPI, OPI, roughing, facemask… \| confronto, disciplina) | resultado da jogada vale |
| RF42 | Aceitar a falta? | a equipe ofendida aceita se o resultado com a penalidade for melhor (jardas, 1ª descida automática, perda de descida) · humano decide só se for momento decisivo | recusa: vale a jogada |
| RF42 | Primeira descida? | jardas ganhas ≥ distance ou penalidade com 1ª descida automática | down + 1 · na 4ª sem conversão → posse troca (turnover on downs) |
| RF42 | Pontuou? | TD (bola cruza a end zone) · FG convertido · safety | segue o drive |
| RF42 | Relógio para? | incompleto, fora de campo, falta, pontuação, troca de posse, tempo técnico, aviso dos 2 minutos · COLLEGE: também na 1ª descida | relógio corre (play clock 40 s) |
| RF42 | Fim do quarto? | clockSeconds == 0 com bola morta | próxima jogada |
| RF42 | Empate ao fim do 4º? | homeScore == awayScore | FINISHED |
| RF42 | Prorrogação decidida? | PRO: as duas posses salvo TD na primeira, depois quem pontuar · COLLEGE: posses alternadas da 25 · fim dos 10 min empatado só se regularSeasonTiesAllowed | segue a prorrogação |
| RF42 | Determinístico? | mesma seed + mesmo decisionsLog ⇒ mesmos PlayEvent e mesmo placar (SplittableRandom com rngCursor no snapshot · teste com amostra dourada) | build falha (teste de determinismo) |
| RF42 | Plausível? | em 1 000 jogos IA × IA: ≈21–24 pts, 320–360 jardas, 11–12 drives, 63–66 % passes completos, 2–3 sacks, 0,7–1,0 INT, 0,4–0,6 fumble perdido, 38–42 % na 3ª descida, ≈84 % FG por equipe e jogo | build falha (teste de plausibilidade) |
| RF43 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF43 | Em intervalo? | MatchSession.status == BREAK (fim de quarto ou intervalo) | painel não abre · 409 DECISIONS_ONLY_AT_BREAK |
| RF43 | Snapshot íntegro? | 22 atletas únicos = 11 por lado do depth chart em campo · nenhum reserva ou lesionado em campo · uniforme e capacete lidos do snapshot | aviso e confirmar desabilitado |
| RF43 | Formação legal? | 7 na linha de scrimmage no ataque, elegíveis nas pontas, 11 por lado · rotas e zonas dentro do campo de 53,3 jardas | diagrama marcado como ilustrativo (não bloqueia) |
| RF43 | WebGL disponível e rápido? | WebGL + primeiros quadros < 120 ms em média + PerformanceMonitor ≥ 24 fps | sem WebGL: visão 2D do campo (SVG) · lento: qualidade baixa |
| RF43 | Movimento reduzido? | prefers-reduced-motion ou preferência | rotas animadas · modo Calmo anima só o selecionado |
| RF43 | Decisão válida? | troca no depth chart com jogador ativo, não lesionado, posição compatível (fora de posição com aviso) · GamePlan nos limites · versão do snapshot | motor recusa com código · estado íntegro · decisão inválida removida e avisada |
| RF43 | Identidade canônica? | avatarFor(playerId) + Uniform e HelmetDesign fixados no snapshot · número legível (contraste) | o mesmo atleta mudaria entre telas |
| RF43 | Orçamento de desempenho? | campo 120 × 53,3 com linhas, hash marks, end zones e traves · 22 figuras com geometria compartilhada · arquibancada instanciada com LOD | qualidade baixa automática |
| RF44 | Consentimento biométrico ativo? | ConsentRecord BIOMETRIC_AVATAR concedido no texto vigente (LGPD Art. 11) | 403 CONSENT_REQUIRED · só avatar manual |
| RF44 | Posição de futebol americano? | position ∈ {QB, RB, FB, WR, TE, LT, LG, C, RG, RT, DE, DT, OLB, MLB, CB, FS, SS, K, P, LS} | 422 POSITION_INVALID |
| RF44 | Altura e peso na faixa da posição? | faixa por posição (ex.: OL 188–203 cm e 135–165 kg · CB 175–190 cm e 80–95 kg · K/P livres dentro do geral) | 422 BODY_OUT_OF_RANGE (mostra a faixa) |
| RF44 | Número permitido? | faixas por posição (OL 50–79, QB/K/P 1–19, WR 0–49 e 80–89…) | 422 JERSEY_NUMBER_INVALID |
| RF44 | Orçamento de atributos? | ≈40 subatributos em 8 grupos · cada um 40–80 · soma pelo orçamento da posição (D-6) | 422 CUSTOM_PLAYER_INVALID |
| RF44 | Foto válida e boa? | JPEG/PNG, ≤ 5 MB, ≥ 256 px · rosto frontal único, luz, nitidez, sem oclusão (gate no aparelho e no servidor) | 422 PHOTO_* / AVATAR_QUALITY_REJECTED com dicas |
| RF44 | Revisão esperada é a atual? | expectedRevision = última revisão | 409 REVISION_CONFLICT |
| RF44 | Aprovação da revisão atual? | approve(expectedRevision) = revisão atual | 409 STALE_APPROVAL |
| RF44 | Retenção vencida? | captura + 180 dias ou aprovação + 30 dias | mantém |
| RF45 | Código válido e sala em LOBBY com vaga? | room.status LOBBY e humanos < maxHumans | 404 ROOM_NOT_FOUND / 409 ROOM_FULL |
| RF45 | Turno pronto? | todos os participantes ACTIVE confirmaram ou o prazo de 24 h venceu | aguarda · no prazo, IA monta depth chart e plano de quem faltou |
| RF45 | Conta elegível e idade mínima? | conta ACTIVE, e-mail verificado, idade ≥ 13 | 422 ACCOUNT_NOT_ELIGIBLE / UNDERAGE |
| RF45 | Sem sanção que bloqueia o modo? | nenhuma OnlineSanction ativa para o modo | 422 SANCTIONED |
| RF45 | Depth chart de cartas válido? | ≥ 46 cartas ativas · titulares QB, RB, WR×3, TE, OL×5 · DL×4, LB×3, CB×2, S×2 · K, P, LS · contratos > 0 | 422 SQUAD_INVALID (vagas listadas) |
| RF45 | Química calculada? | ligações por divisão e conferência (ClubCard e LeagueCard) e por unidade (OL e QB-WR) · fora de posição reduz · 0–100 | química 0 (não bloqueia) |
| RF45 | Janela aberta e cota da temporada? | CompetitionWindow OPEN antes do corte · Divisões runsUsed < 4 − penalidade · Copa cupEntriesUsed < 2 | 422 WINDOW_CLOSED / QUOTA_EXHAUSTED |
| RF45 | Sem entrada ativa e reentrada permitida? | nenhuma DivisionRun/CupEntry ativa na janela · não saiu por desistência ou sanção | 422 ALREADY_ENTERED / REENTRY_BLOCKED |
| RF45 | Tem ingresso da Copa? | Copa: CupTicket AVAILABLE | 422 NO_CUP_TICKET |
| RF45 | Tentativas sob controle? | < 5 recusas em 10 min | 422 TOO_MANY_ATTEMPTS + cooldown 30 min |
| RF45 | Partida aceita em 30 s? | os dois aceitam | VOID sem consumir tentativa |
| RF45 | Chamada online no prazo? | cada head coach responde a sua decisão em ≤ 20 s | PlayCaller IA decide por ele (sem penalidade) |
| RF45 | Batimento em dia? | heartbeat a cada 5 s · silêncio > 90 s ⇒ PAUSED_DISCONNECT | pausa e tolerância de 60 s |
| RF45 | Reconectou a tempo? | volta em ≤ 60 s e no máximo 2 quedas | WO ao adversário (placar atual se ele vencia, senão 2×0) |
| RF45 | Fim da temporada online? | dia 42 da temporada | segue |
| RF46 | Reautenticado (step-up)? | reserva atômica (5 em 15 min) antes do hash · e-mail, senha e exclusão | 401 AUTH_FAILED · 6ª tentativa: 429 + Retry-After 900 |
| RF46 | Idade mínima? | birthDate ≥ 13 anos (Art. 14) | 422 MIN_AGE |
| RF46 | Finalidade válida? | ANALYTICS \| MARKETING_EMAIL \| PUBLIC_RANKING \| AI_ANALYST \| BIOMETRIC_AVATAR | 422 INVALID_CONSENT_PURPOSE |
| RF46 | Revogou biometria? | BIOMETRIC_AVATAR: concedido → revogado | só registra |
| RF46 | Idioma suportado? | pt-BR \| en-US \| es-ES · termos do esporte em inglês quando é o uso do Brasil (touchdown, snap, sack) (RNF13) | 422 INVALID_LOCALE |
| RF46 | Unidade válida? | units ∈ {YARDS, METERS} · jardas é o padrão do esporte | 422 INVALID_UNITS |
| RF46 | Palavra de confirmação? | EXCLUIR \| DELETE \| ELIMINAR | 422 DELETION_CONFIRMATION_MISMATCH |
| RF46 | Prazo de arrependimento vencido? | 7 dias após o pedido | pode cancelar |
| RF47 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF47 | Família da posição? | QB · RB/FB · WR/TE · OL · DL · LB · DB · K/P pela posição escalada (DepthChartSlot) ou natural | usa a posição natural e avisa fora de posição |
| RF47 | Métrica gerada pelo motor? | a métrica existe em PlayerMatchStats ou TeamMatchStats (ex.: pressões e snaps sim · rota corrida por jogada não) | velocímetro mostra '—', nunca zero (§3.6) |
| RF47 | Amostra mínima? | QB ≥ 14 passes por jogo do escopo · RB ≥ 6,25 corridas · WR/TE ≥ 1,5 alvo · DL/LB/DB ≥ 20 snaps · K ≥ 1 tentativa | velocímetro cinza com selo Amostra pequena |
| RF47 | Escala versionada? | GaugeScale v1 por métrica (ex.: rating do QB 0–158,3 · Y/A 0–12 · FG% 0–100) com vetor de ouro Java ↔ TS | build falha (teste de contrato) |
| RF47 | Mesma família para comparar? | família(pid) == família(pid2) | 422 GAUGE_FAMILY_MISMATCH |
| RF47 | Cache válido? | Redis gauges:{saveId}:{pid}:{scope} com versão da semana | recalcula e grava (invalidado por MatchFinished) |
| RF47 | Movimento reduzido? | prefers-reduced-motion ou preferência | ponteiro animado (≤ 600 ms) |
| RF48 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF48 | Atributos completos? | todos os ≈40 subatributos em [1, 99] | erro de consistência · 500 com correlationId e alerta |
| RF48 | Macros da família? | 6 macros por família (QB: PRE, BRA, LEI, MOB, CAL, LID · corredores e recebedores: VEL, AGI, MAO, ROT, QBT, FOR · OL: BLP, BLC, FOR, TEC, AGI, CON · defesa: TAC, IMP, COB, PRS, VEL, LEI · chutadores: POT, PRE, CON) | — |
| RF48 | Fora de posição? | overall na posição escalada < overall na posição natural − 5 | sem aviso |
| RF48 | Efeitos temporários? | estilos (PlayerPlayStyle) e consumíveis ativos somam ao valor base com teto 99 e mostram a origem | só valor base |
| RF48 | Versão de pesos vigente e íntegra? | OverallWeights ACTIVE · soma dos pesos por posição = 1 · vetor de ouro Java ↔ TS passa | 422 WEIGHTS_INVALID (admin) / build falha |
| RF48 | É administrador? | role == ADMIN | 403 |
| RF48 | Nível do card? | Bronze ≤ 64 · Prata 65–74 · Ouro 75–84 · Ícone 85–89 · Elite ≥ 90 | — |
| RF49 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF49 | Head coach empregado? | save.employmentStatus == EMPLOYED | 409 NOT_EMPLOYED |
| RF49 | Janela permitida? | extensão em qualquer fase fora de partida ao vivo · tag só em OFFSEASON até o prazo · reestruturação com ≥ 2 anos restantes · corte fora de partida ao vivo | 422 CONTRACT_WINDOW_CLOSED |
| RF49 | Contrato bem formado? | 1–5 anos (calouro pela escala do pick) · bônus rateado em até 5 anos e ≤ anos do contrato · garantias ≤ total · salário ≥ mínimo da liga pela experiência | 422 CONTRACT_INVALID |
| RF49 | Cabe no teto? | SalaryCapLedger.space após a operação ≥ 0 na temporada atual (teto rígido) · projeções futuras só avisam | 422 CAP_EXCEEDED (mostra quanto falta) |
| RF49 | Jogador aceita? | ContractValuation: valor por ano e garantias × mercado da posição, idade, overall, moral, papel no depth chart, estilo do head coach · u(seed) decide perto do limite | COUNTER (nova proposta) ou REJECT · moral −3 |
| RF49 | Tag permitida? | 1 tag por franquia por temporada · jogador com contrato vencendo · espaço para o valor da tag | 422 FRANCHISE_TAG_NOT_ALLOWED |
| RF49 | Corte com dead money confirmado? | preview visto (hash do preview no comando) · dead money = bônus rateado restante + garantias não pagas · pós-1º de junho divide em 2 temporadas | 422 RELEASE_PREVIEW_REQUIRED |
| RF49 | Ledger na versão esperada? | expectedLedgerVersion == ledger.version (lock otimista) | 409 CAP_LEDGER_CONFLICT |
| RF49 | Piso salarial cumprido? | fim da temporada: gasto em dinheiro ≥ salaryFloor | diferença paga aos jogadores do elenco · inbox da diretoria |
| RF49 | Espaço não usado vira rollover? | fim da temporada: space > 0 soma ao teto seguinte (rolloverFromPrevious) | — |
| RF50 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF50 | Head coach empregado? | save.employmentStatus == EMPLOYED | 409 NOT_EMPLOYED |
| RF50 | Trocas abertas? | REGULAR até Season.tradeDeadlineWeek · OFFSEASON, FREE_AGENCY e DRAFT (picks da classe atual) liberados · PLAYOFFS e partida ao vivo bloqueados | 422 TRADE_WINDOW_CLOSED |
| RF50 | Ativos pertencem a cada lado? | jogadores no elenco ativo (não IR com retorno marcado, não recém-contratados há < 1 semana) · picks com ownerClub == franquia, season ≤ atual + 3, não usados | 422 TRADE_ASSET_INVALID |
| RF50 | Teto das duas franquias depois? | space ≥ 0 nos dois ledgers após a troca · quem envia acelera o bônus rateado restante do jogador como dead money | 422 TRADE_CAP_EXCEEDED (lado e valor) |
| RF50 | Elenco dentro do limite? | ≤ 53 no elenco ativo após a troca (ou corte obrigatório em 24 h de jogo) · ≥ 1 QB e K e P | 422 ROSTER_LIMIT |
| RF50 | IA aceita? | TradeValuation: valor recebido ≥ valor enviado × (1 + margem por dificuldade) · ajuste por necessidade da posição, idade, contrato e janela de título · u(seed) perto do limite | REJECT ou COUNTER com o ativo que falta |
| RF50 | Não é spam? | mesma proposta (mesmos ativos) para a mesma franquia no máx. 1 vez por semana · ≤ 10 propostas abertas | 429 TRADE_RATE_LIMITED |
| RF50 | Proposta ainda válida? | status PENDING, não expirada e ativos sem mudança desde o envio (version) | 409 TRADE_STALE · proposta INVALIDATED |
| RF50 | Execução atômica? | uma transação: jogadores trocam de Club, contratos seguem, DraftPick.ownerClub muda, ledgers e depth charts atualizados, TradeProposal EXECUTED | rollback · 409 CONFLICT |
| RF51 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF51 | Fase do draft permite? | scouting e compra em SCOUTING ou COMBINE · seleção só em LIVE | 422 DRAFT_PHASE_INVALID |
| RF51 | Verba de scouting? | custo do relatório ≤ verba de scouting da temporada (club.scoutingLevel define a precisão) | 422 SCOUTING_BUDGET_EXCEEDED |
| RF51 | Pick do usuário na vez? | pick.ownerClub == save.clubId e pick.overall == próximo pick do relógio | 409 NOT_ON_THE_CLOCK |
| RF51 | Prospecto disponível? | prospect.draftedBy == null | 409 PROSPECT_TAKEN |
| RF51 | Tempo da escolha esgotado? | relógio do pick vence (configurável) | segue aguardando |
| RF52 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF52 | Coletiva aberta? | status OPEN e semana atual ≤ prazo (antes do kickoff ou até a semana seguinte) | 422 PRESS_CLOSED |
| RF52 | Resposta válida? | pergunta pertence à coletiva, ainda sem resposta, tom entre as opções | 422 ANSWER_INVALID |
| RF52 | Claude disponível? | chave configurada e resposta em ≤ 5 s | perguntas e manchete por template (fallback) |
| RF52 | Falta tolerada? | ≤ 1 falta a cada 4 semanas | diretoria −reputação e notícia negativa |
| RF53 | Mundo editável? | world.ownerUser == jwt.sub e world.kind != PADRAO (ADMIN edita o padrão) | 403 / 422 WORLD_READ_ONLY |
| RF53 | Valores nos limites? | quarterMinutes 5–15, playClock 25–40, timeouts 1–3, overtime 0–15, rosterMax 45–53, gameDayActives ≤ rosterMax, practiceSquadMax 0–16 | 422 RULES_INVALID + campos |
| RF53 | Combinação coerente? | COLLEGE liga clockStopsOnFirstDown e oneFootInbounds; PRO desliga (pode sobrescrever com aviso) | aviso na prévia |
| RF53 | Temporada em curso? | algum save do mundo com Season.phase REGULAR ou PLAYOFFS | vale já |
| RF53 | Versão atual? | expectedVersion == rules.version | 409 RULES_VERSION_CONFLICT |
| RF54 | Dono do save? | fixture do save de jwt.sub | 403 |
| RF54 | Evento coerente? | tipo no catálogo, ballOnAbsYd 0–100, descida 1–4, placar só sobe por pontuação válida | erro de motor (teste de invariante falha) |
| RF54 | Houve falta? | sorteio pela semente × disciplina e pressão | segue sem falta |
| RF54 | Aceitar a falta? | o time ofendido escolhe o melhor resultado (automático) ou o manager decide no nível ALL_PLAYS | resultado da jogada vale |
| RF54 | Meia distância? | jardas da falta > metade da distância até o gol | jardas cheias |
| RF54 | Muda a posse? | TD, FG, safety, turnover, 4ª descida falhada ou punt | nova descida ou 1ª descida |
| RF55 | Dono do save? | save.userId == jwt.sub | 403 |
| RF55 | Alvos coerentes? | por posição a soma dos alvos cobre as vagas (ex.: 4 DL em campo = 400 %), cada alvo 0–100 % | 422 SNAP_PLAN_INVALID |
| RF55 | Rodízio da DL? | 6 a 9 linemen no rodízio | 422 DL_ROTATION_INVALID |
| RF55 | Jogador ativo? | rosterStatus ACTIVE e não OUT no relatório de lesões | 422 PLAYER_NOT_ACTIVE |
| RF55 | Fadiga alta? | alvo > limite pela resistência e condição | sem aviso |
| RF55 | Partida em andamento? | MatchSession LIVE para o clube | vale no próximo jogo |
| RF56 | Dono do save? | save.userId == jwt.sub | 403 |
| RF56 | Vaga no practice squad? | membros < LeagueRules.practiceSquadMax (16) | 422 PRACTICE_SQUAD_FULL |
| RF56 | Elegível? | experienceYears ≤ 2 ou vaga de veterano (até 6) | 422 NOT_ELIGIBLE |
| RF56 | Elevação permitida? | elevationsUsed < 3 na temporada e ≤ 2 elevações por jogo | 422 ELEVATION_LIMIT |
| RF56 | Vaga no 53? | ativos < rosterMax e cap space para o contrato ativo | 422 ROSTER_FULL / CAP_EXCEEDED |
| RF56 | Protegidos da semana? | ≤ 4 protegidos | 422 PROTECTION_LIMIT |
| RF57 | Dono do save? | save.userId == jwt.sub | 403 |
| RF57 | Votação aberta? | AllStarEvent.status VOTING | 422 VOTING_CLOSED |
| RF57 | Voto válido? | 1 por posição, jogador ativo, até 3 do próprio clube | 422 VOTE_INVALID |
| RF57 | Convocado lesionado? | InjuryReport OUT ou IR na semana do evento | segue |
| RF58 | Recurso habilitado? | flag digitalTwins.enabled (Q-4 decidida) | 404 FEATURE_DISABLED |
| RF58 | É ADMIN? | jwt.role == ADMIN | 403 + AuditLog ACCESS_DENIED |
| RF58 | Licença válida? | status ACTIVE e hoje entre validFrom e validUntil | mostra a versão fictícia |
| RF58 | Escopo cobre o uso? | scope inclui NOME, IMAGEM ou ESTATISTICA pedido | campo fora do escopo fica fictício |
| RF58 | Venceu a validade? | job diário: validUntil < hoje | segue ACTIVE |
| RF59 | Dono do save? | fixture do save de jwt.sub | 403 |
| RF59 | Jogada existe? | PlayEvent (fixtureId, seq) gravado | 404 PLAY_NOT_FOUND |
| RF59 | Tem diagrama? | PlayDefinition.diagram e trajetória gravados | desenho genérico da formação com o resultado |
| RF59 | Movimento reduzido? | UserSettings.reducedMotion | anima a jogada |
| RF60 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF60 | Versão atual? | expectedVersion == depthChart.version | 409 DEPTH_CHART_VERSION_CONFLICT |
| RF60 | Slots obrigatórios? | 11 titulares no ataque e na defesa, K, P, LS, holder, KR e PR | 422 DEPTH_CHART_INCOMPLETE |
| RF60 | Jogador disponível? | ACTIVE (ou elevado do practice squad), sem suspensão, não OUT nem IR | 422 PLAYER_UNAVAILABLE |
| RF60 | Fora de posição? | overall na posição < overall na posição natural − 5 | sem aviso |
| RF60 | 46 ativos no jogo? | jogadores únicos no depth chart ≤ gameDayActives | 422 TOO_MANY_ACTIVES |
| RF60 | Partida em andamento? | MatchSession LIVE | grava |
| RF61 | Dono do save? | save.userId == jwt.sub | 403 |
| RF61 | Playbook mínimo? | jogadas por situação: corrida, passe curto e fundo, red zone, goal line, 2 minutos, punt, FG | 422 PLAYBOOK_INCOMPLETE |
| RF61 | Plano nos limites? | runPassBalance 0–100, enums válidos, até 5 jogadores-chave do elenco | 422 GAME_PLAN_INVALID |
| RF61 | Personnel cabe no elenco? | agrupamentos usados (12, 21…) têm jogadores no depth chart | aviso: jogadas desativadas |
| RF61 | Partida em andamento? | MatchSession LIVE | vale no kickoff |
| RF62 | Dono do save? | fixture do save de jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF62 | Momento decisivo? | KEY_MOMENTS: 4ª descida, últimos 2 minutos de cada tempo ou prorrogação · ALL_PLAYS: toda jogada · NONE: nunca | PlayCaller escolhe pelo plano de jogo |
| RF62 | Decisão aberta? | status OPEN e now < deadlineAt | 409 DECISION_CLOSED |
| RF62 | Opção válida? | playId ∈ options | 422 PLAY_NOT_OFFERED |
| RF62 | Versão da sessão? | expectedVersion == MatchSession.version | 409 MATCH_VERSION_CONFLICT |
| RF62 | Prazo esgotado? | now ≥ deadlineAt sem escolha | aguarda |
| RF63 | Dono do save? | save.userId == jwt.sub | 403 |
| RF63 | Unidade completa? | 11 jogadores; K ou P, holder e LS definidos quando a unidade pede | 422 UNIT_INCOMPLETE |
| RF63 | Jogador disponível? | ativo no dia de jogo, não OUT nem IR | 422 PLAYER_UNAVAILABLE |
| RF63 | Onside permitido? | onsideAllowed e regra da liga (aviso ao adversário conforme LeagueRules) | kickoff normal |
| RF63 | Tenta 2 pontos? | tabela de conversão por diferença no placar e tempo restante | PAT da linha de 15 |
| RF63 | FG ao alcance? | distância ≤ alcance pela potência do chutador e clima | punt ou tentativa na 4ª descida (RF62) |
| RF64 | Dono do save? | save.userId == jwt.sub | 403 |
| RF64 | Status pela lesão? | weeksOut ≥ 1 e DNP → OUT · LIMITED e gravidade média → DOUBTFUL · LIMITED leve → QUESTIONABLE | sai do relatório (saudável) |
| RF64 | Pode ir à IR? | weeksOut ≥ 4 | 422 IR_MIN_WEEKS |
| RF64 | Retorno disponível? | semanas mínimas cumpridas e designações de retorno < 8 na temporada | 422 IR_RETURN_LIMIT |
| RF64 | Vaga no 53? | ativos < rosterMax ao ativar | 422 ROSTER_FULL (dispense alguém) |
| RF64 | Titular fora? | jogador OUT ou IR está num slot do depth chart | segue |
| RF65 | Dono do save? | save.userId == jwt.sub | 403 + AuditLog ACCESS_DENIED |
| RF65 | Fase permite? | Season.phase FREE_AGENCY ou temporada regular antes do prazo; NEGOTIATION só com os próprios jogadores | 422 MARKET_CLOSED |
| RF65 | Cabe no teto? | cap hit do 1º ano ≤ SalaryCapLedger.space | 422 CAP_EXCEEDED |
| RF65 | Vaga no elenco? | ativos < rosterMax (offseason: limite ampliado da fase) | 422 ROSTER_FULL |
| RF65 | Oferta mínima? | salário ≥ mínimo da liga por experiência | 422 BELOW_MINIMUM |
| RF65 | Jogador aceita? | JogadorIA compara valor, garantias, chance de título e papel no depth chart | REJECTED (ou outra oferta vence) |
| RF65 | Claim vence? | menor priority entre os claims do jogador (ordem inversa da classificação; pré-temporada: ordem do draft) | LOST |
| RF66 | Dono do save? | save.userId == jwt.sub | 403 |
| RF66 | Temporada regular encerrada? | todas as Fixtures REGULAR FINISHED | mostra só a projeção |
| RF66 | Desempate resolvido? | confronto direto, divisão, adversários comuns, conferência, força da tabela, sorteio pela semente | aplica o próximo critério |
| RF66 | Seed 1 folga? | seed == 1 → hasBye na Wild Card (com 7 por conferência) | joga a Wild Card |
| RF66 | Rodada concluída? | todos os PlayoffMatchup da rodada com winner | aguarda |
| RF66 | Final neutra? | League.neutralSiteFinal | mando do melhor seed |
| RF66 | Usuário eliminado? | clube do save perdeu | segue na chave |
| RNF1 | Mundo é modelo do usuário ou o padrão? | World.kind = TEMPLATE e (padrão ou ownerUser = jwt.sub) | 403 FORBIDDEN / 422 WORLD_NOT_TEMPLATE |
| RNF1 | Todos os passos ok? | clone completo + franquia na liga + slot livre + contrato + moral + plano de jogo + temporada | ROLLBACK total: nenhum mundo SAVE nem salvamento |
| RNF1 | Elenco do clone consistente? | cada franquia com ≤ 53 ativos, ≤ 16 no practice squad e depth chart válido para as posições | ROLLBACK · 422 WORLD_INCONSISTENT |
| RNF1 | Versão otimista confere? | @Version lida == versão gravada (DepthChart, MatchSession, CareerSave) | 409 CONCURRENT_UPDATE · cliente relê |
| RNF1 | Commit ok? | COMMIT no MySQL sem erro | efeitos pós-commit descartados: nada em Cassandra, Redis, OpenSearch ou S3 |
| RNF1 | Toda FK tem ON DELETE explícito? | política gerada (CASCADE nos filhos do save e do mundo, RESTRICT nas trilhas, SET NULL nas opcionais) e testada em H2 e MySQL 8 real | ForeignKeyPolicyTest vermelho no CI |
| RNF1 | Migration válida? | versões únicas · migration aplicada nunca é editada · FOREIGN_KEY_CHECKS nunca desligado | CI vermelho |
| RNF2 | Orçamento de tempo definido? | timeBudget = 4 × simQuarterMinutes × 60 s (+ prorrogação proporcional) · congelado no início da partida | usa o padrão (3 min por quarto) |
| RNF2 | Instantâneo? | simQuarterMinutes = 0 → advance(FULL) em < 5 s, sem exibição jogada a jogada | reprodução por quarto |
| RNF2 | Quarto simulado dentro do SLA? | MatchSimulator.advance(QUARTER) ≤ 500 ms p95 (~38 jogadas em memória, sem I/O por jogada) | métrica match.quarter.duration alerta |
| RNF2 | Ritmo cabe no orçamento? | msPorJogada = (timeBudget − pausas de decisão) / jogadas restantes (~150 no jogo) · mínimo 400 ms · máximo 6 s | frontend acelera: resume jogadas sem lance relevante |
| RNF2 | Momento decisivo? | playCallingLevel KEY_MOMENTS: 4ª descida, últimos 2 min de cada tempo, prorrogação · ALL_PLAYS: todo snap | jogada escolhida pelo PlayCaller conforme o GamePlan |
| RNF2 | Decisão dentro do tempo limite? | resposta antes de deadlineAt (decisionTimeoutSeconds) · o relógio do orçamento pausa durante a decisão | decisão automática do PlayCaller (auto = true) |
| RNF2 | Persistência fora do caminho crítico? | jogadas → Cassandra em lote AFTER_COMMIT · snapshot → Redis · MySQL no fim do quarto | alerta de latência |
| RNF3 | Rota pública? | allowlist: /api/v1/auth/** · /actuator/health · /api-docs/** · /swagger-ui/** · /error | exige Bearer JWT |
| RNF3 | Actuator além do health? | /actuator/** → denyAll, só health exposto e sem detalhes | segue; se sim: 403 mesmo para ADMIN |
| RNF3 | JWT válido? | RS256 com a chave pública, iss, aud, exp · roles no claim roles | 401 UNAUTHENTICATED RFC 7807 {type, title, status, code, message, details, correlationId, locale} |
| RNF3 | Família de sessão ativa? | SessionActiveFilter: claim sid → UserSession não revogada (Redis, fallback MySQL) | 401 UNAUTHENTICATED |
| RNF3 | Perfil exigido pela rota? | /api/v1/admin/** → hasRole(ADMIN) · demais → authenticated | 403 FORBIDDEN + AuditLog ACCESS_DENIED |
| RNF3 | Dono do recurso? | CareerAccess.requireOwner: save.user == jwt.sub | 403 + AuditLog ACCESS_DENIED (transação própria) |
| RNF3 | Recurso filho pertence ao save? | jogador, partida, proposta e mensagem carregados por (saveId, id) | 404 NOT_FOUND (não revela existência) |
| RNF3 | Conta ativa? | status != DELETED · PENDING_DELETION só cancela, exporta ou sai | 403 ACCOUNT_BLOCKED |
| RNF3 | Admin inicial seguro? | AdminBootstrap só com e-mail e senha forte vindos do Cofre · nunca senha padrão | nenhum admin criado (aviso no log) |
| RNF4 | Chaves RSA carregadas do Cofre? | JWT_PRIVATE_KEY_PEM e JWT_PUBLIC_KEY_PEM vindas do Cofre (≥ 2048 bits, par casado) · nunca no repositório | produção recusa subir citando só o nome da variável |
| RNF4 | Access token válido? | RS256 · iss · aud · exp 15 min · claim sid | 401 → frontend tenta /auth/refresh uma vez |
| RNF4 | Família ativa? | SessionActiveFilter: sid com UserSession sem revokedAt (Redis, fallback MySQL) | 401 mesmo com assinatura válida |
| RNF4 | Cookie __Host- presente e Origin confiável? | cookie __Host-scores_af_rt (Secure, Path=/, sem Domain) + Origin ou Sec-Fetch-Site same-origin | 401 · 403 CSRF_REJECTED se Origin estranho |
| RNF4 | Refresh válido e não rotacionado? | hash existe · revokedAt nulo · replacedBy nulo · expiresAt > agora | 401 · reuso: revoga a família + TOKEN_REUSE_DETECTED |
| RNF4 | Rotação atômica? | UPDATE antiga SET replacedBy = nova WHERE replacedBy IS NULL (1 linha) · INSERT nova na mesma família | corrida → 401 e revoga a família |
| RNF4 | Access só em memória? | access fica em variável do módulo de sessão · nada em localStorage ou sessionStorage (teste de guarda no front) | teste vermelho no CI |
| RNF4 | Recuperação de sessão possível? | ao abrir o app sem access em memória: POST /auth/refresh silencioso com o cookie | tela de entrada |
| RNF5 | Chave de cifra vinda do Cofre? | SCORES_AF_ENCRYPTION_KEY Base64 de 256 bits lida do Cofre na subida | subida recusada citando só o nome da variável |
| RNF5 | Campo marcado como sensível? | AesGcmConverter em email, displayName, pendingEmail · nonce de 96 bits por escrita | grava em claro (dado de jogo) |
| RNF5 | Hash de busca coerente? | emailHash = HMAC-SHA256(chave, lower(trim(email))) · unique · login sem decifrar | 409 EMAIL_ALREADY_REGISTERED / 401 AUTH_FAILED |
| RNF5 | Senha com Argon2id sob teto? | PasswordHasher único ponto de hash · semáforo de hashes simultâneos | 503 AUTH_BUSY + Retry-After |
| RNF5 | Decifragem autorizada? | só o titular (sub == userId) ou processo interno (e-mail transacional) | 403 + ACCESS_DENIED |
| RNF5 | Segredo fora de logs e auditoria? | LogSafe.maskEmail e redact (JWT, tokens, cookies, senhas) | LogSafeTest vermelho |
| RNF5 | Segredo fora do repositório? | gitleaks no histórico inteiro com --redact no CI e no pre-push · senhas de teste geradas na execução · .gitleaksignore só com fingerprints | CI vermelho · o segredo é rotacionado no Cofre, nunca só removido |
| RNF5 | TLS em trânsito? | HTTPS + HSTS 1 ano · bancos com TLS em produção | conexão recusada |
| RNF6 | Janela de backup? | cron 03:00 UTC · lock distribuído no Redis (TTL 2 h) · sem migração Flyway em andamento | reagenda em 30 min |
| RNF6 | Dump consistente? | mysqldump --single-transaction --routines --triggers + binlog habilitado | falha · alerta · retry 1× |
| RNF6 | Integridade verificada? | SHA-256 · gzip -t · restore em MySQL efêmero e COUNT das tabelas críticas == origem | backup FAILED · alerta · mantém o anterior |
| RNF6 | Enviado ao armazenamento durável? | bucket scores-af-backups cifrado e versionado · credenciais do Cofre · retenção 7 diários, 4 semanais, 6 mensais | retry com backoff · alerta após 3 falhas |
| RNF6 | RPO atendido? | lastSuccessAt ≤ 24 h · binlog a cada 15 min | alerta crítico · health DOWN(backup) |
| RNF6 | Restore autorizado? | ADMIN + dupla confirmação + app em somente leitura | negado + AuditLog |
| RNF6 | Restore dentro do RTO? | dump + binlog até o ponto ≤ 2 h · Flyway validate ok | plano B: promove réplica |
| RNF6 | Projeções reconstruídas? | play-by-play refeito pela seed da partida (motor determinístico), inbox e notícias reprojetadas, reindex no OpenSearch, contadores no Redis | serviço degradado com fallback em memória |
| RNF7 | correlationId presente e seguro? | aceita X-Correlation-Id só sem CR/LF e com tamanho limitado · senão gera UUID · devolvido no cabeçalho e no corpo RFC 7807 | valor injetado trocado por UUID |
| RNF7 | IP confiável? | IP do socket ou cabeçalho assinado pelo proxy · nunca X-Forwarded-For cru | XFF forjado é ignorado |
| RNF7 | Evento obrigatório? | REGISTER, LOGIN_*, LOGOUT, TOKEN_*, SESSION_REVOKED, PASSWORD_*, SENSITIVE_DATA_CHANGED, CONSENT_*, DATA_EXPORT_*, ACCOUNT_DELETION_*, ACCESS_DENIED, STORE_PURCHASE, MATCH_SIMULATED, PLAY_CALLED, ADMIN_* | só log estruturado |
| RNF7 | Payload contém dado proibido? | senha, token, cookie, campo cifrado, chave | grava · se contém: LogSafe.redact antes |
| RNF7 | Auditoria sobrevive ao rollback sem prender o pool? | AuditService em REQUIRES_NEW próprio · auditar depois da escrita curta e fora do hash de senha | teste de concorrência vermelho (60 logins paralelos sem 500) |
| RNF7 | Consulta por ADMIN? | jwt.roles contém ADMIN | 403 + ACCESS_DENIED |
| RNF7 | Retenção vencida? | timestamp < hoje − 12 meses | mantém · se vencida: arquiva e expurga |
| RNF8 | Autenticado? | JWT válido e conta ativa | 401 |
| RNF8 | Finalidade válida e não pré-marcada? | ConsentPurpose conhecido · nada pré-marcado · texto versionado | 422 INVALID_CONSENT_PURPOSE |
| RNF8 | Reautenticação (step-up)? | exclusão, troca de e-mail e de senha exigem a senha atual (5 tentativas em 15 min) | 401 AUTH_FAILED / 429 |
| RNF8 | Exportação recente? | pedido nas últimas 24 h | agenda job · se recente: 429 EXPORT_RATE_LIMITED com link vigente |
| RNF8 | Conta é o último ADMIN? | role ADMIN e único admin ativo | segue · se sim: 422 LAST_ADMIN |
| RNF8 | Carência de 7 dias vencida? | agora ≥ deletionRequestedAt + 7 d e não cancelada | aguarda · se vencida: anonimiza |
| RNF8 | Trilhas preservadas? | consentimento, compra, carteira e ledger com FK RESTRICT: a conta é anonimizada, nunca apagada | DELETE físico recusado pelo banco |
| RNF9 | reducedMotion? | prefers-reduced-motion do SO ou settings.reducedMotion | animações completas · se sim: sem loops 3D, câmera fixa, transições instantâneas |
| RNF9 | Viewport móvel? | largura < 768 px | layout desktop · se sim: coluna única, placar grande com descida e distância, gutter 16 px |
| RNF9 | GPU/WebGL suficiente? | webgl2 e deviceMemory ≥ 4 | campo 2D em SVG com as 22 posições |
| RNF9 | Cena 3D dentro do orçamento? | 22 atletas com InstancedMesh por time · LOD 3 níveis · ≤ 150 draw calls · ≤ 300 mil triângulos · texturas KTX2 · ≥ 30 fps | rebaixa LOD, desliga sombras e torcida · abaixo de 20 fps: fallback 2D |
| RNF9 | Orçamento de bundle respeitado? | rota inicial ≤ 250 kB gz · three/R3F/drei em chunk lazy | CI vermelho (size-limit) |
| RNF9 | Cache válido? | ETag confere ou chave Redis viva | recalcula · se válido: 304 ou resposta do cache |
| RNF9 | p95 dentro do orçamento? | http.server.requests p95 ≤ 300 ms nas rotas do ciclo | alerta operacional |
| RNF9 | Rede lenta ou offline? | navigator.onLine e RTT < 1 s | skeleton, retry com backoff e banner Sem conexão |
| RNF10 | Navegador suporta Push? | serviceWorker e PushManager disponíveis | só notificações in-app |
| RNF10 | Permissão concedida? | Notification.permission granted após gesto do usuário | não insiste · guarda o estado |
| RNF10 | Assinatura válida? | endpoint https do push service · p256dh e auth em base64url | 422 PUSH_SUBSCRIPTION_INVALID |
| RNF10 | Tipo habilitado? | notificationsEnabled e NotificationPreference(tipo).enabled · MARKETING exige consentimento (RNF8) | só in-app |
| RNF10 | Usuário ativo na aba? | heartbeat < 60 s no Redis | envia push · se ativo: só in-app |
| RNF10 | Limite de frequência? | ≤ 10 push por hora por usuário · collapse key por evento | agrupa em resumo |
| RNF10 | Push service respondeu 404/410? | assinatura expirada ou revogada | entregue · se 404/410: remove a assinatura |
| RNF10 | Chave VAPID privada no Cofre? | VAPID_PRIVATE_KEY só no Cofre · pública exposta ao front | produção recusa subir |
| RNF11 | Autenticado? | JWT válido | 401 |
| RNF11 | Rate limit de upload? | ≤ 10 uploads por hora por usuário | 429 UPLOAD_RATE_LIMITED |
| RNF11 | Cota do usuário? | bytes guardados + arquivo ≤ 50 MB (PLAYER) / 500 MB (ADMIN) | 413 QUOTA_EXCEEDED |
| RNF11 | Tamanho permitido? | logo, decalque e avatar ≤ 2 MB · uniforme e end zone ≤ 5 MB · GLB ≤ 15 MB | 413 FILE_TOO_LARGE |
| RNF11 | Tipo permitido por magic bytes? | PNG, JPEG, WebP (SVG proibido) · model/gltf-binary só ADMIN · extensão confere com o conteúdo | 415 UNSUPPORTED_MEDIA_TYPE |
| RNF11 | Dimensões válidas? | 64 a 2048 px · 1:1 para logo, decalque e avatar · end zone 4:1 | 422 IMAGE_DIMENSIONS |
| RNF11 | Antivírus limpo? | ClamAV retorna OK | 422 MEDIA_INFECTED + quarentena + AuditLog MEDIA_REJECTED |
| RNF11 | Recodificação ok? | decodifica e recodifica em WebP, remove EXIF e ICC | 422 IMAGE_CORRUPT |
| RNF11 | Mídia referenciada? | mediaKey usado por Club, Uniform, HelmetDesign, Stadium ou CustomPlayer | job diário remove órfãs > 24 h · se referenciada: DELETE 409 MEDIA_IN_USE |
| RNF12 | Pontos só ganhos jogando? | crédito só por meta, vitória, título, prêmio, temporada concluída ou online · ADMIN_ADJUST auditado | nenhuma rota vende pontos |
| RNF12 | Cosmético nunca altera o motor? | COSMETIC_ONLY (capacete, uniforme, end zone, comemoração) sem efeito em atributos · raridade só visual | revisão de game design antes de publicar |
| RNF12 | Item afeta o motor? | GAME_EFFECT (consumível, estilo de jogo, estilo de head coach): carteira do save · save fora de rankings · relatório do adversário mostra | carteira do usuário |
| RNF12 | Teto de efeito? | ≤ 1 consumível por categoria por jogador · ≤ 3 estilos por jogador · teto por time | 409 / 422 EFFECT_CAP |
| RNF12 | Saldo nunca negativo? | transação única com SELECT FOR UPDATE + @Version · CHECK balance ≥ 0 · 50 compras paralelas com saldo para 1 → 1 sucesso, 49 INSUFFICIENT_POINTS, nenhum 500 | ROLLBACK · 422 INSUFFICIENT_POINTS |
| RNF12 | Limite diário? | ≤ 10 compras por dia · ≤ 500 pontos ganhos por dia | 429 DAILY_PURCHASE_LIMIT |
| RNF12 | Idempotente? | Idempotency-Key única · mesma chave → mesma resposta | 422 IDEMPOTENCY_CONFLICT |
| RNF12 | Sem loot box? | item e preço conhecidos antes da compra · sem sorteio pago | item não é publicado |
| RNF13 | Idioma suportado? | locale ∈ {pt-BR, en-US, es-ES} | 400 VALIDATION · mantém o idioma atual |
| RNF13 | Chave existe no idioma? | catálogo do idioma tem a chave | fallback es-ES → en-US → pt-BR · no CI a falta de chave reprova o build |
| RNF13 | Termo do esporte no glossário? | SportGlossary: keepEnglish em pt-BR (touchdown, snap, sack, field goal, kickoff, punt, safety, blitz, red zone) · teste compara o catálogo com o glossário | glossary-check vermelho no CI |
| RNF13 | Conteúdo gerado tem chave? | PlayEvent, inbox e notícia gravados como chave + parâmetros (MessageRef) | texto exibido como está |
| RNF13 | Descida e distância formatadas? | pt-BR 3ª e 7 na 34 do adversário · en-US 3rd & 7 at OPP 34 · es-ES 3.ª y 7 en la 34 rival | teste de formatação vermelho |
