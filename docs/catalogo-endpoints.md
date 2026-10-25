# Catálogo de endpoints da API (por requisito)

✅ implementado no `scores-af-web` · ⛔ alvo (modelo) · — transversal.

| Estado | Método | Rota | Req | Caso de uso | Autorização | Descrição |
|---|---|---|---|---|---|---|
| ✅ | `POST` | `/api/v1/auth/register` | RF1 | `RegisterUserUseCase` | público · 10 por IP/hora | Cria UserAccount (e-mail AES-GCM + emailHash HMAC, senha Argon2id fora da transação, status ACTIVE), UserSettings padrão do futebol americano (jardas, controle KEY_MOMENTS) e consentimento de processamento; envia e-mail de verificação (token 24 h) |
| ✅ | `POST` | `/api/v1/auth/email/verify` | RF1 | `EmailChangeUseCases.verify` | público · 40 por IP/hora | Confirma a posse do e-mail (cadastro ou troca) com token de uso único |
| ✅ | `POST` | `/api/v1/auth/login` | RF2 | `LoginUseCase` | público · 30 por IP/10 min | Reserva atômica de tentativa por e-mail ANTES do hash (5 em 15 min); mesmo 401 AUTH_FAILED para todos os casos; access RS256 15 min no corpo (fica só em memória) + refresh opaco 7 dias em cookie __Host- HttpOnly Secure SameSite=Strict (D-5) |
| ✅ | `POST` | `/api/v1/auth/refresh` | RF2 | `RefreshTokenUseCase` | cookie __Host-refresh · Origin conferida · 120 por IP/10 min | Rotaciona o refresh (replacedBy) e regrava o cookie; reuso de token antigo revoga a família inteira |
| ✅ | `POST` | `/api/v1/auth/logout` | RF2 | `LogoutUseCase` | JWT | Revoga a família de sessão, apaga o cookie e avisa as outras abas (logout entre abas, D-5) |
| ✅ | `POST` | `/api/v1/auth/password/forgot` | RF2 | `ForgotPasswordUseCase` | público · 10 por IP/hora | Token de redefinição (30 min) por e-mail · 202 sempre (não revela existência) |
| ✅ | `POST` | `/api/v1/auth/password/reset` | RF2 | `ResetPasswordUseCase` | público · 20 por IP/hora | Novo hash fora da transação; limpa o bloqueio por e-mail; revoga todas as famílias de sessão |
| ✅ | `GET` | `/api/v1/me` | RF3 | `AccountUseCases.profile` | JWT | Perfil (e-mail decifrado só para o titular), locale, status, visibilidade |
| ✅ | `PUT` | `/api/v1/me` | RF3 | `AccountUseCases.updateProfile` | JWT | displayName, avatar, visibilidade, data de nascimento · SENSITIVE_DATA_CHANGED |
| ✅ | `PUT` | `/api/v1/me/email` | RF3 | `EmailChangeUseCases.request` | JWT · senha atual (step-up) · 30 por IP/h | pendingEmail + token; conclui em POST /auth/email/verify |
| ✅ | `PUT` | `/api/v1/me/password` | RF3 | `AccountUseCases.changePassword` | JWT · senha atual (step-up) · 20 por IP/h | Novo hash fora da transação; revoga as outras famílias de sessão |
| ✅ | `GET` | `/api/v1/me/sessions` | RF3 | `AccountUseCases.sessions` | JWT | Famílias de sessão ativas (dispositivo, IP, última atividade, atual) |
| ✅ | `DELETE` | `/api/v1/me/sessions/{id}` | RF3 | `AccountUseCases.revokeSession` | JWT | Revoga uma família do próprio usuário · SESSION_REVOKED |
| ✅ | `GET` | `/api/v1/me/consents` | RF3 | `PrivacyUseCases.view` | JWT | Consentimento por finalidade (ANALYTICS, MARKETING_EMAIL, PUBLIC_RANKING, AI_ANALYST, BIOMETRIC_AVATAR) |
| ✅ | `PUT` | `/api/v1/me/consents` | RF3 | `PrivacyUseCases.update` | JWT | Concede ou revoga (append-only); revogar biometria apaga a mídia do Meu jogador |
| ✅ | `POST` | `/api/v1/me/export` | RF3 | `DataExportUseCases.request` | JWT | Portabilidade assíncrona: DataExportJob (JSON + CSV, 24 h) |
| ✅ | `GET` | `/api/v1/me/export/{jobId}/download` | RF3 | `DataExportUseCases.download` | JWT · dono do job | Download do pacote · DATA_EXPORT_DOWNLOADED |
| ✅ | `DELETE` | `/api/v1/me` | RF3 | `AccountDeletionUseCases.request` | JWT · senha + palavra (step-up) · 10 por IP/h | PENDING_DELETION, sessões encerradas, 7 dias de arrependimento |
| ✅ | `POST` | `/api/v1/me/deletion/cancel` | RF3 | `AccountDeletionUseCases.cancel` | JWT | Cancela dentro do prazo |
| ⛔ alvo | `PUT` | `/api/v1/me/notification-preferences` | RF3 | `NotificationPreferenceUseCases.update` | JWT | Liga/desliga por tipo (resultado da semana, lesão, proposta, prazo de trocas, draft) |
| ✅ | `GET` | `/api/v1/careers/{id}/roster` | RF4 | `GetRosterUseCase` | JWT · dono do save | Cards compactos por unidade (unit=OFFENSE\|DEFENSE\|SPECIAL): posição, overall na posição, macros da família (D-6), condição, moral, lesão, rosterStatus, contrato e cap hit; contadores 53/46/16 |
| ✅ | `PATCH` | `/api/v1/careers/{id}/players/{pid}/roster-status` | RF4 | `ChangeRosterStatusUseCase` | JWT · dono do save | ACTIVE ↔ INACTIVE do dia de jogo (46 ativos) ou move ao practice squad |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/injured-reserve` | RF4 | `PlaceOnInjuredReserveUseCase` | JWT · dono do save | Coloca na IR (mínimo 4 semanas) e libera vaga no elenco de 53; retorno com POST …/activate |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/practice-squad/{pid}/elevate` | RF4 | `ElevatePracticeSquadPlayerUseCase` | JWT · dono do save | Sobe um jogador do practice squad para o jogo da semana (até 3 elevações por temporada) |
| ✅ | `POST` | `/api/v1/careers/{id}/players/{pid}/release` | RF4 | `ReleasePlayerUseCase` | JWT · dono do save | Dispensa: vai a waivers (RF65) e gera dead money no teto (RF49) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/consumables` | RF4 | `ApplyConsumableUseCase` | JWT · dono do save | Aplica consumível com efeito em atributos do futebol americano (RF25) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/message` | RF4 | `MessagePlayerUseCase` | JWT · dono do save | Mensagem ao jogador (elogio, cobrança, promessa de snaps) com efeito na moral |
| ✅ | `GET` | `/api/v1/worlds` | RF5 | `ListWorldsUseCase` | JWT | Mundo semente fictício de 32 times + mundos TEMPLATE do usuário |
| ✅ | `POST` | `/api/v1/worlds/{id}/clone` | RF5 | `CloneWorldUseCase` | JWT | Copia o mundo semente (ou outro TEMPLATE) para o usuário editar |
| ✅ | `POST` | `/api/v1/worlds/{id}/leagues` | RF5 | `SaveLeagueUseCase` | JWT · dono do mundo | Cria liga com ruleset PRO\|COLLEGE, LeagueRules AF (quarto, relógio de jogada, prorrogação, kickoff, 53/46/16), teto e piso salarial |
| ⛔ alvo | `PUT` | `/api/v1/worlds/{id}/leagues/{lid}` | RF5 | `SaveLeagueUseCase` | JWT · dono do mundo | Edita liga e regras (LeagueRules.version + 1) |
| ✅ | `PUT` | `/api/v1/worlds/{id}/leagues/{lid}/structure` | RF5 | `SaveLeagueStructureUseCase` | JWT · dono do mundo | Conferências (1–2), divisões por conferência e franquia de cada divisão; vagas de playoff por conferência |
| ⛔ alvo | `DELETE` | `/api/v1/worlds/{id}/leagues/{lid}` | RF5 | `DeleteCatalogItemUseCase` | JWT · dono do mundo | Exclui liga (sem franquias ou cascata confirmada) |
| ✅ | `POST` | `/api/v1/leagues/{id}/clubs` | RF5 | `SaveClubUseCase` | JWT · dono do mundo | Cria franquia (nome, cidade, divisão, cores, capacete, logo, prestígio) |
| ⛔ alvo | `PUT` | `/api/v1/leagues/{id}/clubs/{cid}` | RF5 | `SaveClubUseCase` | JWT · dono do mundo | Edita franquia, uniformes (HOME\|AWAY\|ALTERNATE) e capacete |
| ✅ | `POST` | `/api/v1/clubs/{id}/players` | RF5 | `SavePlayerUseCase` | JWT · dono do mundo | Cria jogador: posição AF, unidade, ≈40 subatributos, altura/peso, college, contrato |
| ⛔ alvo | `PUT` | `/api/v1/clubs/{id}/players/{pid}` | RF5 | `SavePlayerUseCase` | JWT · dono do mundo | Edita jogador · overall por posição recalculado (OVR v1) |
| ⛔ alvo | `DELETE` | `/api/v1/clubs/{id}/players/{pid}` | RF5 | `DeleteCatalogItemUseCase` | JWT · dono do mundo | Exclui jogador |
| ✅ | `PUT` | `/api/v1/clubs/{id}/stadium` | RF5 | `SaveStadiumUseCase` | JWT · dono do mundo | Estádio do catálogo: capacidade, cobertura, gramado, zona climática, arte da end zone |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/next-game/preparation` | RF6 | `GetGamePreparationUseCase` | JWT · dono do save | Próximo jogo da semana, adversário, 46 ativos disponíveis, depth chart e plano atuais, relatório de lesões, fit e sugestão do analista |
| ✅ | `GET` | `/api/v1/careers/{id}/depth-chart` | RF6 | `GetDepthChartUseCase` | JWT · dono do save | Slots por unidade e posição (depth 1, 2, 3) e papéis (KR, PR, 3RD_DOWN_BACK, HOLDER, SNAPPER) |
| ✅ | `PUT` | `/api/v1/careers/{id}/depth-chart` | RF6 | `SaveDepthChartUseCase` | JWT · dono do save | Salva depth chart validado (version otimista) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/depth-chart/auto` | RF6 | `AutoFillDepthChartUseCase` | JWT · dono do save | Preenche pelo overall na posição, condição e lesões (ClubAnalyst) |
| ✅ | `GET` | `/api/v1/careers/{id}/game-plan` | RF6 | `GetGamePlanUseCase` | JWT · dono do save | GamePlan atual e playbook ativo |
| ✅ | `PUT` | `/api/v1/careers/{id}/game-plan` | RF6 | `SaveGamePlanUseCase` | JWT · dono do save | runPassBalance, fourthDownAggression, tempo, foco ofensivo e defensivo, blitz, defesa base 4-3/3-4, jogadores-chave, tendências por situação |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/analysis` | RF6 | `GetAnalysisUseCase` | JWT · dono do save | Relatório do ClubAnalyst: depth chart sugerido, gamePlanFit, alertas (fora de posição, fadiga, lesão) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/next-game/preparation` | RF7 | `GetGamePreparationUseCase` | JWT · dono do save | Dados do modal: fixture, adversário, ativos, depth chart, plano, clima e modelos 3D |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/next-game/opponent-report` | RF7 | `GetOpponentReportUseCase` | JWT · dono do save | Relatório do adversário por unidade (ataque, defesa, especiais): tendências, formações e coberturas mais usadas, lesões e confrontos-chave WR × CB, OL × DL, TE × LB |
| ✅ | `PUT` | `/api/v1/careers/{id}/depth-chart` | RF7 | `SaveDepthChartUseCase` | JWT · dono do save | Salva o depth chart montado no painel LED (RF6) |
| ✅ | `PUT` | `/api/v1/careers/{id}/game-plan` | RF7 | `SaveGamePlanUseCase` | JWT · dono do save | Salva o plano de jogo (RF6) |
| ✅ | `GET` | `/api/v1/me/settings` | RF7 | `GetSettingsUseCase` | JWT | simQuarterMinutes, playCallingLevel, decisionTimeoutSeconds e reducedMotion aplicados à partida (RF10) |
| ✅ | `POST` | `/api/v1/careers/{id}/weeks/current/play` | RF7 | `PlayWeekUseCase` | JWT · dono do save | Jogar partida: cria MatchSession (seed derivada) e dispara a simulação dos outros jogos da semana |
| ✅ | `GET` | `/api/v1/matches/{mid}` | RF7 | `GetMatchStateUseCase` | JWT · dono do save | Estado vivo (quarto, relógio, descida e distância, posse, placar) para retomar a partida |
| ✅ | `GET` | `/api/v1/careers/{id}/weeks/{n}` | RF7 | `GetWeekBoardUseCase` | JWT · dono do save | Painel da semana com todos os placares (RF16/RF27) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/manager` | RF8 | `GetManagerPanelUseCase` | JWT · dono do save | Perfil, contrato ativo, metas (vitórias, vaga nos playoffs, título de divisão/conferência, campeão) e progresso, moral com a franquia e com a liga, histórico, propostas pendentes |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/morale` | RF8 | `GetManagerMoraleUseCase` | JWT · dono do save | Moral com a franquia e com a liga + fatores |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/manager/raise-request` | RF8 | `RequestSalaryRaiseUseCase` | JWT · dono do save | Pede aumento (valor e justificativa) · diretoria decide |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/manager/message-board` | RF8 | `MessageBoardUseCase` | JWT · dono do save | Mensagem à diretoria (assunto de lista + texto) · resposta no inbox |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/finance/budget-request` | RF8 | `RequestBudgetUseCase` | JWT · dono do save | Solicita verba operacional (estrutura, scouting, staff) · não mexe no teto salarial (RF14/RF49) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/message` | RF8 | `MessagePlayerUseCase` | JWT · dono do save | Mensagem a um jogador do elenco (elogio, cobrança, promessa de snaps) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/manager/resign` | RF8 | `ResignUseCase` | JWT · dono do save | Pede demissão: contrato RESIGNED, save UNEMPLOYED, registro de carreira fechado |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/job-offers` | RF9 | `ListJobOffersUseCase` | JWT · dono do save | Propostas PENDING com franquia, conferência e divisão, salário, duração, metas, prazo e motivo |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/job-offers/{jid}/respond` | RF9 | `RespondJobOfferUseCase` | JWT · dono do save | ACCEPT \| REJECT \| LATER · aceitar em transação única |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/manager` | RF9 | `GetManagerPanelUseCase` | JWT · dono do save | Contrato atual e multa para comparar |
| ✅ | `GET` | `/api/v1/me/settings` | RF10 | `GetSettingsUseCase` | JWT | UserSettings: simQuarterMinutes, playCallingLevel, decisionTimeoutSeconds, difficulty, units, theme, highContrast, fontScale, reducedMotion, notificationsEnabled |
| ✅ | `PUT` | `/api/v1/me/settings` | RF10 | `UpdateSettingsUseCase` | JWT | Salva preferências validadas |
| ⛔ alvo | `GET` | `/api/v1/me/inventory` | RF10 | `ListInventoryUseCase` | JWT | Temas, artes de card e trilhas compradas na loja (Purchase) para as galerias |
| ✅ | `GET` | `/api/v1/careers` | RF11 | `ListCareersUseCase` | JWT | Slots do usuário: nome, franquia, liga, temporada/semana, campanha (V-D-E), status, lastPlayedAt |
| ✅ | `POST` | `/api/v1/careers` | RF11 | `CreateCareerUseCase` | JWT | Nova carreira (assistente RF34): clona o mundo, cria head coach, temporada e contrato |
| ✅ | `GET` | `/api/v1/careers/{id}` | RF11 | `GetCareerUseCase` | JWT · dono do save | Detalhe do save |
| ✅ | `PATCH` | `/api/v1/careers/{id}` | RF11 | `UpdateCareerUseCase` | JWT · dono do save | Renomeia, muda slot, arquiva/reativa |
| ✅ | `DELETE` | `/api/v1/careers/{id}` | RF11 | `DeleteCareerUseCase` | JWT · dono do save | Exclui save, mundo SAVE e projeções (play-by-play, inbox, cache, índice) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/stadium` | RF12 | `GetStadiumUseCase` | JWT · dono do save | Estádio (capacidade, cobertura, gramado, zona climática, nível), setores, política de ingressos, previsão de público e renda por jogo em casa e efeito do clima |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/stadium` | RF12 | `UpdateStadiumUseCase` | JWT · dono do save | Nome, nível, troca de gramado (GRASS\|TURF) e de cobertura (OPEN\|DOME\|RETRACTABLE, só na offseason) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/stadium/sectors` | RF12 | `BuySectorUseCase` | JWT · dono do save | Compra setor (LOWER_BOWL, UPPER_BOWL, SIDELINE_CLUB, END_ZONE, LUXURY_SUITES) |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/stadium/sectors/{sid}` | RF12 | `UpdateSectorUseCase` | JWT · dono do save | Reforma (condição), amplia assentos, define preço |
| ⛔ alvo | `DELETE` | `/api/v1/careers/{id}/stadium/sectors/{sid}` | RF12 | `RemoveSectorUseCase` | JWT · dono do save | Exclui setor (não obrigatório e sem obra) |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/stadium/ticket-policy` | RF12 | `UpdateTicketPolicyUseCase` | JWT · dono do save | Carnê da temporada (season tickets), desconto de carnê, preço dinâmico por rival da divisão e playoffs, cota de camarotes |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/inbox` | RF13 | `ListInboxUseCase` | JWT · dono do save | Mensagens do save (Cassandra, cursor por createdAt) com remetente, tipo, opções, prazo em semanas e status |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/inbox/{mid}/respond` | RF13 | `RespondInboxMessageUseCase` | JWT · dono do save | Escolhe uma opção · aplica o efeito (moral, contrato, verba, troca, free agency) e gera a resposta |
| ⛔ alvo | `GET` | `/api/v1/me/notifications` | RF13 | `ListNotificationsUseCase` | JWT | Notificações do usuário (TTL 90 dias) para o sino |
| ⛔ alvo | `POST` | `/api/v1/me/notifications/read` | RF13 | `MarkNotificationsReadUseCase` | JWT | Marca notificações como lidas |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/finance` | RF14 | `GetFinanceOverviewUseCase` | JWT · dono do save | Caixa, receitas por jogo em casa, direitos de TV, despesas, empréstimos, pedidos de verba e ledger paginado (page, size, kind, week) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/finance/cap` | RF14 | `GetSalaryCapUseCase` | JWT · dono do save | SalaryCapLedger da temporada: teto, comprometido, dead money, espaço, rollover; cap hit por jogador (só leitura, gestão em RF49) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/finance/loans` | RF14 | `ContractBankLoanUseCase` | JWT · dono do save | Contrata empréstimo (principal, prazo em semanas) · juros por semana por prestígio e dívida · ledger LOAN |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/finance/loans/{lid}/settle` | RF14 | `SettleBankLoanUseCase` | JWT · dono do save | Quitação antecipada (desconto de juros) ou alongamento (juros +0,5 pp) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/finance/budget-request` | RF14 | `RequestBudgetUseCase` | JWT · dono do save | Pede verba operacional à diretoria (amount, reason) · decidida pela IA |
| ⛔ alvo | `GET` | `/api/v1/store/items` | RF15 | `ListStoreItemsUseCase` | JWT | kind=COACH_STYLE: OFFENSIVE, DEFENSIVE, DEVELOPER, MOTIVATOR com efeitos, preço em Scores Points ou meta de recompensa |
| ⛔ alvo | `POST` | `/api/v1/store/purchase` | RF15 | `PurchaseStoreItemUseCase` | JWT · Idempotency-Key | Compra estilo com Scores Points: débito idempotente na carteira + Purchase |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/goals` | RF15 | `ListGoalsUseCase` | JWT · dono do save | Metas que concedem estilo (ex.: 5 temporadas com ataque top-5 em jardas → OFFENSIVE) |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/manager/style` | RF15 | `ApplyCoachStyleUseCase` | JWT · dono do save | Ativa um estilo possuído no head coach do save (1 ativo) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/manager` | RF15 | `GetManagerPanelUseCase` | JWT · dono do save | Perfil com estilo ativo, overall e efeitos vigentes |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/standings` | RF16 | `GetStandingsUseCase` | JWT · dono do save | view=DIVISION\|CONFERENCE\|LEAGUE: V-D-E, % de vitórias (empate vale meio), recorde na divisão e na conferência, pontos pró e contra, sequência, últimos 5, seed e nota de desempate |
| ✅ | `GET` | `/api/v1/careers/{id}/weeks/{n}` | RF16 | `GetWeekPanelUseCase` | JWT · dono do save | Placares da semana n (passada: finais por quarto e prorrogação · atual: ao vivo · futura: agendados e horários), times de folga e destaques (líderes em passe, corrida, recepção e defesa, jogador da semana) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/playoffs` | RF16 | `GetPlayoffBracketUseCase` | JWT · dono do save | Chave com 7 seeds por conferência, folga do seed 1 e final em campo neutro (RF66) |
| ✅ | `GET` | `/api/v1/careers/{id}/calendar` | RF16 | `GetCalendarUseCase` | JWT · dono do save | Semanas futuras com jogos de divisão, quinta à noite e semana de folga (RF21) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/trophies` | RF17 | `GetTrophyRoomUseCase` | JWT · dono do save | Campeões por temporada (final, conferências, divisões) e meus troféus (títulos do head coach, prêmio COACH) com URL do troféu 3D · filtros season, kind |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/awards` | RF17 | `ListSeasonAwardsUseCase` | JWT · dono do save | Prêmios individuais por temporada (MVP, OPOY, DPOY, OROY, DROY, COACH) filtráveis por temporada e conferência |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/managers/world` | RF18 | `ListWorldManagersUseCase` | JWT · dono do save | Ranking (Redis ZSET) e esquemas compactos: nome, franquia, overall, estilo de head coach, campanha (V-D-E), vitórias em playoffs, títulos · sort=wins\|playoffWins\|titles\|overall\|moraleLeague |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/managers/world/{mid}` | RF18 | `GetManagerHistoryUseCase` | JWT · dono do save | Esquema ampliado: franquias por temporada (ManagerCareerRecord), campanhas, playoffs, série de overall, estilo, prêmios COACH |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/search` | RF18 | `SearchUseCase` | JWT · dono do save | type=MANAGER: busca por nome, nacionalidade ou franquia (OpenSearch) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/market/free-agents` | RF19 | `SearchFreeAgentsUseCase` | JWT · dono do save | Free agents com pedida (anos, valor, garantia), interesse na franquia e overall por posição · filtros position, unit, minOverall, age |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/market/free-agents/{pid}/offers` | RF19 | `OfferFreeAgentContractUseCase` | JWT · dono do save | FreeAgentOffer (anos, valor total, garantia, bônus de assinatura); o jogador decide pela oferta mais atraente até expiresAt |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/market/trades` | RF19 | `ProposeTradeUseCase` | JWT · dono do save | TradeProposal: jogadores e picks de draft (até 3 temporadas à frente) dos dois lados · impacto no teto calculado no backend |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/market/trades/{tid}/respond` | RF19 | `RespondTradeUseCase` | JWT · dono do save | ACCEPT a contraproposta \| REJECT \| COUNTER · aceite executa a troca em transação única |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/market/waivers` | RF19 | `ListWaiverWireUseCase` | JWT · dono do save | Jogadores dispensados nas últimas 24 h de jogo com contrato, cap hit e minha prioridade (ordem inversa da classificação) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/market/waivers/{pid}/claim` | RF19 | `ClaimWaiverUseCase` | JWT · dono do save | WaiverClaim PENDING · processado no fim do período pela prioridade |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/cap` | RF19 | `GetSalaryCapUseCase` | JWT · dono do save | Teto, comprometido, dead money e espaço da temporada (SalaryCapLedger) para validar a oferta |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/search` | RF20 | `SearchUseCase` | JWT · dono do save | q, type=PLAYER\|CLUB\|LEAGUE\|MANAGER, position, unit (OFFENSE\|DEFENSE\|SPECIAL), conference, division, rosterStatus, minOverall, college · facetas e paginação (OpenSearch, fallback MySQL) |
| ✅ | `GET` | `/api/v1/careers/{id}/players/{pid}` | RF20 | `GetPlayerExpandedUseCase` | JWT · dono do save | Esquema ampliado do jogador (RF22) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/clubs/{cid}` | RF20 | `GetClubSheetUseCase` | JWT · dono do save | Esquema ampliado da franquia: identidade, divisão, campanha, elenco por unidade, depth chart titular, head coach, estádio |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/standings` | RF20 | `GetStandingsUseCase` | JWT · dono do save | Esquema ampliado da liga: classificação por divisão e líderes estatísticos (jardas de passe, corrida, recepção, sacks) |
| ✅ | `GET` | `/api/v1/careers/{id}/calendar` | RF21 | `GetCalendarUseCase` | JWT · dono do save | Semanas da temporada (pré, 18 regulares, playoffs) com fixtures por horário (THU_NIGHT, SUN_EARLY, SUN_LATE, SUN_NIGHT, MON_NIGHT), bye, clima previsto, importance e CalendarInsight |
| ✅ | `GET` | `/api/v1/careers/{id}/weeks/{n}` | RF21 | `GetWeekBoardUseCase` | JWT · dono do save | Detalhe da semana ao clicar (placares, destaques ou prévia) |
| ✅ | `GET` | `/api/v1/careers/{id}/next-match/preparation` | RF21 | `GetNextMatchPreparationUseCase` | JWT · dono do save | Atalho para preparar o time a partir do próximo jogo (RF6/RF7) |
| ✅ | `GET` | `/api/v1/careers/{id}/players/{pid}` | RF22 | `GetPlayerExpandedUseCase` | JWT · dono do save | 6 macros pela família de posição (D-6), ≈40 subatributos agrupados, overall em cada posição com aviso fora de posição, contrato (cap hit, garantia, dead money), moral e fatores, lesão e InjuryReport, estatísticas da temporada pela posição, estilos, consumíveis, slot no depth chart e card (nível Bronze…Elite) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/players/{pid}/card-history` | RF22 | `GetPlayerCardHistoryUseCase` | JWT · dono do save | Versões do card por temporada, prêmio ou título |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/depth-chart` | RF22 | `MovePlayerInDepthChartUseCase` | JWT · dono do save | Atalho do RF60: posição ou papel (KR, PR, 3RD_DOWN_BACK) e profundidade |
| ✅ | `PATCH` | `/api/v1/careers/{id}/players/{pid}/roster-status` | RF22 | `ChangeRosterStatusUseCase` | JWT · dono do save | ACTIVE, PRACTICE_SQUAD ou IR dentro das regras 53 / 16 / IR |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/extend` | RF22 | `ExtendPlayerContractUseCase` | JWT · dono do save | Extensão: anos, salário por ano, bônus de assinatura (rateado), garantia · jogador IA aceita ou contrapropõe |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/franchise-tag` | RF22 | `ApplyFranchiseTagUseCase` | JWT · dono do save | Franchise tag de 1 ano no último ano de contrato (1 por franquia por temporada, RF49) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/trade-block` | RF22 | `ToggleTradeBlockUseCase` | JWT · dono do save | Entra ou sai do bloco de trocas (franquias IA podem propor, RF19) |
| ✅ | `POST` | `/api/v1/careers/{id}/players/{pid}/release` | RF22 | `ReleasePlayerUseCase` | JWT · dono do save | Dispensa: vai aos waivers e o dead money entra no teto |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/consumables` | RF22 | `ApplyConsumableUseCase` | JWT · dono do save | Aplica consumível (RF25) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/message` | RF22 | `MessagePlayerUseCase` | JWT · dono do save | Mensagem ao jogador (inbox, RF24) |
| ✅ | `GET` | `/api/v1/careers/{id}/players/{pid}` | RF23 | `GetPlayerExpandedUseCase` | JWT · dono do save | model3dUrl, posição, altura e peso, estilos ativos e animationId de cada um |
| ⛔ alvo | `GET` | `/api/v1/animations` | RF23 | `ListAnimationClipsUseCase` | JWT | Clipes PUBLISHED por target=POSITION\|PLAY_STYLE\|LOCOMOTION e code (locomoção herdada das 37 do basquete) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/clubs/{cid}/identity` | RF23 | `GetClubIdentityUseCase` | JWT · dono do save | Uniforme ativo e capacete (cores, faixa, decal, grade) para vestir o modelo |
| ⛔ alvo | `GET` | `/api/v1/me/inventory` | RF23 | `GetInventoryUseCase` | JWT | Animações 3D compradas na loja (RF32) aplicadas ao jogador |
| ✅ | `GET` | `/api/v1/me/settings` | RF23 | `GetUserSettingsUseCase` | JWT | reducedMotion (RNF9) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/inbox` | RF24 | `GetInboxUseCase` | JWT · dono do save | Mensagens paginadas por data (Cassandra) com status, remetente, opções e prazo em semanas |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/inbox/{mid}/respond` | RF24 | `RespondInboxMessageUseCase` | JWT · dono do save | Escolhe uma opção e aplica a consequência (moral, verba, proposta de troca, oferta de FA, entrevista) via RF41 |
| ⛔ alvo | `GET` | `/api/v1/me/notifications` | RF24 | `GetNotificationsUseCase` | JWT | Notificações do usuário (TTL 90 d) |
| ⛔ alvo | `POST` | `/api/v1/me/notifications/read` | RF24 | `MarkNotificationsReadUseCase` | JWT | Marca como lidas |
| ✅ | `GET` | `/api/v1/careers/{id}/roster` | RF25 | `GetRosterUseCase` | JWT · dono do save | Elenco com consumíveis ativos por jogador (ConsumableApplication) e semanas restantes |
| ⛔ alvo | `GET` | `/api/v1/store/catalog` | RF25 | `StoreCatalogService` | JWT | category=CONSUMABLE: efeito por atributo (ex.: +2 rota funda, +3 pass rush potência), posições compatíveis, duração em semanas, preço |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/consumables` | RF25 | `ApplyConsumableUseCase` | JWT · dono do save · Idempotency-Key | Debita S$ do caixa (não conta no teto) ou Scores Points da carteira do save, cria ConsumableApplication (appliedWeek, expiresWeek) e PlayerPlayStyle CONSUMABLE se carta de estilo |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/stadium` | RF26 | `GetStadiumUseCase` | JWT · dono do save | Capacidade (60–80 mil), cobertura (OPEN, DOME, RETRACTABLE), gramado ou sintético, zona climática, setores, arte da end zone, model3dUrl, manutenção por semana |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/stadium` | RF26 | `UpdateStadiumUseCase` | JWT · dono do save | Nome, arte da end zone e modelo 3D ativo |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/stadium/sectors` | RF26 | `AddStadiumSectorUseCase` | JWT · dono do save | Compra um setor novo (S$) · obra concluída em N semanas |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/stadium/sectors/{sid}` | RF26 | `UpgradeStadiumSectorUseCase` | JWT · dono do save | Amplia assentos, reforma (condition) ou muda o preço do ingresso |
| ⛔ alvo | `DELETE` | `/api/v1/careers/{id}/stadium/sectors/{sid}` | RF26 | `RemoveStadiumSectorUseCase` | JWT · dono do save | Remove setor (capacidade e manutenção caem) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/stadium/renovation` | RF26 | `StartStadiumRenovationUseCase` | JWT · dono do save | Obra grande: cobertura (OPEN → RETRACTABLE/DOME) ou superfície (GRASS ↔ TURF), só na offseason e com aprovação da diretoria |
| ⛔ alvo | `GET` | `/api/v1/store/catalog` | RF26 | `StoreCatalogService` | JWT | category=STADIUM_3D: estádios modulares (Scores Points) |
| ⛔ alvo | `POST` | `/api/v1/store/purchases` | RF26 | `PurchaseService` | JWT · Idempotency-Key | Compra o estádio modular (cosmético, RNF12) |
| ⛔ alvo | `POST` | `/api/v1/me/inventory/{iid}/apply` | RF26 | `ApplyInventoryItemUseCase` | JWT · dono do save | Aplica o modelo 3D comprado ao estádio da franquia |
| ✅ | `POST` | `/api/v1/careers/{id}/weeks/current/play` | RF27 | `PlayWeekUseCase` | JWT · dono do save | Inicia a semana: MatchSession do usuário e job das partidas da IA por horário (THU_NIGHT, SUN_EARLY, SUN_LATE, SUN_NIGHT, MON_NIGHT) |
| ✅ | `GET` | `/api/v1/careers/{id}/weeks/{n}` | RF27 | `GetWeekBoardUseCase` | JWT · dono do save | Placares da semana por horário com quarto, relógio, posse, descida e distância · byes · minha partida em destaque |
| ✅ | `GET` | `/api/v1/matches/{mid}` | RF27 | `GetMatchStateUseCase` | JWT · dono do save | Snapshot vivo: placar por quarto, relógio de jogo e de jogada, posse, descida, distância, ballOnAbsYd, drive atual, tempos técnicos, decisão pendente |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/plays` | RF27 | `GetPlayByPlayUseCase` | JWT · dono do save | Play-by-play incremental (?after=seq) do Cassandra |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/drives` | RF27 | `GetDrivesUseCase` | JWT · dono do save | Drives: jogadas, jardas, tempo de posse, resultado (TD, FG, PUNT, TURNOVER, DOWNS) |
| ✅ | `POST` | `/api/v1/matches/{mid}/advance` | RF27 | `AdvanceMatchUseCase` | JWT · dono do save | Avança PLAY (assistir), DRIVE, QUARTER ou FULL (simular), no ritmo de simQuarterMinutes |
| ⛔ alvo | `GET` | `/api/v1/clubs/{id}/identity` | RF28 | `ClubIdentityUseCases.get` | JWT · leitura do mundo | Identidade resolvida: primária, secundária, destaque, contraste do número, capacete e uniformes HOME/AWAY/ALTERNATE com versões |
| ⛔ alvo | `PUT` | `/api/v1/clubs/{id}/identity` | RF28 | `ClubIdentityUseCases.update` | JWT · dono do mundo modelo ou do save (só a franquia do save) | Cores e logo · 422 IDENTITY_INVALID com violações · 409 se expectedVersion divergir |
| ⛔ alvo | `POST` | `/api/v1/clubs/{id}/identity/preview` | RF28 | `ClubIdentityUseCases.preview` | JWT · leitura do mundo | Valida e renderiza a proposta sem gravar |
| ⛔ alvo | `PUT` | `/api/v1/clubs/{id}/helmet` | RF28 | `ClubIdentityUseCases.updateHelmet` | JWT · dono do mundo modelo ou do save | Cor da casca, faixa (estilo e cor), cor da grade, decal lateral, acabamento MATTE/GLOSS/CHROME |
| ⛔ alvo | `PUT` | `/api/v1/clubs/{id}/uniforms/{slot}` | RF28 | `ClubIdentityUseCases.updateUniform` | JWT · dono do mundo modelo ou do save | Camisa, calça, número e contorno, padrão · versão sobe quando muda o visual |
| ⛔ alvo | `POST` | `/api/v1/media/upload` | RF28 | `UploadMediaUseCase` | JWT | Upload do logo ou decal do capacete (RNF11) → mediaKey |
| ⛔ alvo | `GET` | `/api/v1/store/catalog` | RF28 | `StoreCatalogService` | JWT | category=HELMET\|UNIFORM\|CLUB_LOGO (RF32) |
| ⛔ alvo | `POST` | `/api/v1/me/inventory/{iid}/apply` | RF28 | `ApplyInventoryItemUseCase` | JWT · dono do save | Aplica capacete, uniforme ou logo comprado |
| ✅ | `GET` | `/api/v1/matches/{mid}` | RF29 | `GetMatchStateUseCase` | JWT · dono do save | Snapshot em BREAK ou HALFTIME: placar, tempos técnicos, estatísticas do time, fadiga, lesões e depth chart em uso |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/playbook` | RF29 | `GetMatchPlaybookUseCase` | JWT · dono do save | Plano de jogo vigente, pesos de formações e conceitos, coberturas e blitz, tendências do adversário no jogo (corrida/passe por descida, cobertura mais usada) e sugestões do analista |
| ⛔ alvo | `POST` | `/api/v1/matches/{mid}/adjustments` | RF29 | `SubmitPlaybookAdjustmentsUseCase` | JWT · dono do save | Ajustes validados: GAME_PLAN (corrida/passe, ritmo, 4ª descida), FORMATION_WEIGHTS, CONCEPT_WEIGHTS, COVERAGE_WEIGHTS, BLITZ_FREQUENCY, DEPTH_SWAP |
| ✅ | `POST` | `/api/v1/matches/{mid}/advance` | RF29 | `AdvanceMatchUseCase` | JWT · dono do save | mode=QUARTER: retoma com os ajustes no próximo snap ou kickoff |
| ✅ | `GET` | `/api/v1/careers/{id}/game-plan` | RF29 | `GetGamePlanUseCase` | JWT · dono do save | Plano padrão da franquia (RF61) para restaurar |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/standings` | RF30 | `GetStandingsUseCase` | JWT · dono do save | view=DIVISION\|CONFERENCE\|LEAGUE: V-D-E, winPct, campanha na divisão e conferência, pontos pró e contra, sequência, seeds 1–7 projetados e nota de desempate |
| ✅ | `GET` | `/api/v1/careers/{id}/weeks/{n}` | RF30 | `GetWeekBoardUseCase` | JWT · dono do save | Placares da semana n (passada, atual ou futura com prévia) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/weeks/{n}/news` | RF30 | `GetWeekNewsUseCase` | JWT · dono do save | Manchetes e comentários da semana (Cassandra) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/playoffs` | RF30 | `GetPlayoffBracketUseCase` | JWT · dono do save | Chave de 14 (7 por conferência, bye do seed 1), confrontos em jogo único, final em campo neutro |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/news` | RF30 | `GetNewsFeedUseCase` | JWT · dono do save | Feed de notícias da temporada paginado |
| ✅ | `GET` | `/api/v1/matches/{mid}/report` | RF31 | `GetMatchReportUseCase` | JWT · dono do save | attendance, revenue, ocupação e receita por setor, fatores (prestígio, campanha, rival de divisão, horário prime time, clima, importância, preço) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/stadium` | RF31 | `GetStadiumUseCase` | JWT · dono do save | Capacidade, cobertura, setores e preços usados no cálculo |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/finance` | RF31 | `GetFinanceOverviewUseCase` | JWT · dono do save | Ledger TICKETS dos jogos em casa e média da temporada |
| ⛔ alvo | `GET` | `/api/v1/store/catalog` | RF32 | `StoreCatalogService` | JWT · 20 por min | Catálogo paginado por cursor: categoria (STADIUM_3D, PLAYER_ANIMATION_3D, UNIFORM, HELMET, CLUB_LOGO, LEGENDARY_PLAYER, CONSUMABLE), coleção, busca, ordenação · ETag + marcação de posse |
| ⛔ alvo | `GET` | `/api/v1/store/products/{code}` | RF32 | `StoreCatalogService` | JWT | Detalhe: preço base e efetivo com motivo, compatibilidade (posição para animações, cobertura para estádios), assets READY |
| ⛔ alvo | `POST` | `/api/v1/store/purchases` | RF32 | `PurchaseService` | JWT · Idempotency-Key · 20 por min | Compra: preço só no backend · transação única (ledger + Purchase + direito + inventário + AuditLog STORE_PURCHASE) · mesma chave ⇒ mesma resposta |
| ⛔ alvo | `GET` | `/api/v1/me/purchases` | RF32 | `PurchaseService` | JWT | Histórico: item, data, preço, status |
| ⛔ alvo | `GET` | `/api/v1/me/wallet` | RF32 | `WalletService` | JWT | Carteira USER (cosméticos): saldo e lançamentos com balanceAfter |
| ⛔ alvo | `GET` | `/api/v1/me/inventory` | RF32 | `InventoryService` | JWT | O que possuo, inclusive itens retirados da loja |
| ⛔ alvo | `PUT` | `/api/v1/me/loadout/{slot}` | RF32 | `EquipmentService` | JWT | Equipar (confirmação se o slot está ocupado) |
| ⛔ alvo | `POST` | `/api/v1/admin/store/products` | RF32 | `StoreAdminService` | JWT · ADMIN | Criar, publicar, ocultar, retirar, agendar produto · nunca edita ledger |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/goals` | RF33 | `GetGoalsUseCase` | JWT · dono do save | Metas ativas e concluídas com progresso, recompensa e prazo (ex.: vencer 4 jogos de divisão, chegar aos playoffs, QB com 4 000 jardas) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/wallet` | RF33 | `WalletService` | JWT · dono do save | Carteira SAVE: saldo, lifetimeEarned e extrato com balanceAfter |
| ⛔ alvo | `GET` | `/api/v1/me/wallet` | RF33 | `WalletService` | JWT | Carteira USER (cosméticos) |
| ✅ | `GET` | `/api/v1/worlds` | RF34 | `ListWorldsUseCase` | JWT | Mundo padrão (32 franquias fictícias) e mundos editados do usuário (RF5) |
| ✅ | `GET` | `/api/v1/worlds/{id}/leagues` | RF34 | `ListLeaguesUseCase` | JWT | Ligas com ruleset PRO/COLLEGE, conferências, divisões, nº de franquias, teto e piso salarial |
| ✅ | `GET` | `/api/v1/leagues/{id}/clubs` | RF34 | `ListClubsUseCase` | JWT | Franquias por conferência e divisão (prestígio, cap space, vitórias esperadas, overall por unidade) |
| ✅ | `POST` | `/api/v1/leagues/{id}/clubs` | RF34 | `CreateClubUseCase` | JWT | Cria franquia (nome, cidade, cores, capacete, divisão) só em mundo editável |
| ⛔ alvo | `GET` | `/api/v1/catalog/coach-styles` | RF34 | `ListCoachStylesUseCase` | JWT | Estilos de head coach: OFFENSIVE, DEFENSIVE, DEVELOPER, MOTIVATOR (RF15) |
| ✅ | `POST` | `/api/v1/careers` | RF34 | `CreateCareerUseCase` | JWT | Cria CareerSave CREATING e agenda o bootstrap → 202 {saveId} |
| ✅ | `GET` | `/api/v1/careers/{id}` | RF34 | `GetCareerUseCase` | JWT · dono do save | Status da criação (CREATING → ACTIVE \| FAILED) para o poll do wizard |
| ✅ | `GET` | `/api/v1/careers` | RF34 | `ListCareersUseCase` | JWT | Carreiras salvas e slots livres (RF11) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/season/summary` | RF35 | `GetSeasonSummaryUseCase` | JWT · dono do save | Campeão (troféu 3D), classificação final por divisão e conferência, rankings, prêmios, narrativa, fase da entressafra e pendências |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/playoffs` | RF35 | `GetPlayoffBracketUseCase` | JWT · dono do save | Chave final: 7 por conferência, folga do 1º, final neutra (RF66) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/awards` | RF35 | `GetSeasonAwardsUseCase` | JWT · dono do save | MVP, OPOY, DPOY, OROY, DROY, COACH |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/contracts/expiring` | RF35 | `ListExpiringContractsUseCase` | JWT · dono do save | Contratos que vencem: renovar, aplicar franchise tag ou deixar ir (RF49) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/job-offers` | RF35 | `ListJobOffersUseCase` | JWT · dono do save | Propostas de head coach da janela pós-temporada (RF37) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/job-offers/{jid}/respond` | RF35 | `RespondJobOfferUseCase` | JWT · dono do save | Aceitar ou recusar proposta |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/season/advance` | RF35 | `AdvanceOffseasonPhaseUseCase` | JWT · dono do save | Avança a fase: OFFSEASON → FREE_AGENCY → DRAFT → PRESEASON n+1 · idempotente por (seasonId, fase) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/simulate` | RF36 | `StartSimulationUseCase` | JWT · dono do save | {until, targetFixtureId?, targetWeek?, speed} → 202 {runId} |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/simulate/active` | RF36 | `GetActiveSimulationUseCase` | JWT · dono do save | Execução ativa (retomar após recarregar) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/simulate/{runId}` | RF36 | `GetSimulationUseCase` | JWT · dono do save | Progresso (semana X/Y), motivo da pausa e resumo |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/simulate/{runId}/stop` | RF36 | `StopSimulationUseCase` | JWT · dono do save | Conclui a semana atual e encerra |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/simulate/{runId}/resume` | RF36 | `ResumeSimulationUseCase` | JWT · dono do save | Retoma depois de resolver a parada |
| ✅ | `GET` | `/api/v1/careers/{id}/dashboard` | RF37 | `GetClubDashboardUseCase` | JWT · dono do save | Painel espectador: classificação, placares da semana, notícias, propostas · sem cards de gestão |
| ✅ | `POST` | `/api/v1/careers/{id}/weeks/current/play` | RF37 | `PlayWeekUseCase` | JWT · dono do save | Espectador: simula a semana inteira e publica WeekCompleted |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/job-offers` | RF37 | `ListJobOffersUseCase` | JWT · dono do save | Propostas PENDING: franquia, divisão, salário, temporadas, metas, prazo e motivo |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/job-offers/{jid}/respond` | RF37 | `RespondJobOfferUseCase` | JWT · dono do save | ACCEPT em transação única · REJECT reduz a chance da franquia · LATER mantém |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/managers/world` | RF37 | `ListWorldManagersUseCase` | JWT · dono do save | Histórico do head coach: franquias, demissões, períodos sem emprego (RF18) |
| ⛔ alvo | `GET` | `/api/v1/admin/animations/coverage` | RF38 | `AnimationAdminUseCases.coverage` | JWT · ADMIN | Alvos sem animação publicada: ≈40 subatributos, 20 posições, estilos, locomoção (deve ser vazio) |
| ⛔ alvo | `POST` | `/api/v1/admin/animations/generate` | RF38 | `AnimationAdminUseCases.generate` | JWT · ADMIN | Rascunho de parâmetros gerado pela IA e validado |
| ⛔ alvo | `PUT` | `/api/v1/admin/animations/{id}` | RF38 | `AnimationAdminUseCases.update` | JWT · ADMIN | Ajusta parâmetros do rascunho |
| ⛔ alvo | `POST` | `/api/v1/admin/animations/{id}/publish` | RF38 | `AnimationAdminUseCases.publish` | JWT · ADMIN | Publica e arquiva a anterior |
| ⛔ alvo | `GET` | `/api/v1/catalog/animations` | RF38 | `AnimationCatalogUseCase` | JWT | Catálogo público (só PUBLISHED, ETag + cache) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/scouting/prospects` | RF39 | `ListProspectsUseCase` | JWT · dono do save | Classe do draft com filtros (posição, faculdade, rodada projetada) · potencial em faixa conforme os relatórios |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/scouting/prospects/{pid}` | RF39 | `GetProspectUseCase` | JWT · dono do save | Ficha: relatórios, nota, faixa de potencial, combine (se já houve) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/scouting/prospects/{pid}/reports` | RF39 | `ScoutProspectUseCase` | JWT · dono do save | Gasta pontos de scouting da semana: novo relatório com precisão pelo nível do departamento |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/scouting/board` | RF39 | `GetBigBoardUseCase` | JWT · dono do save | Quadro de prospectos ordenado pelo usuário |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/scouting/board` | RF39 | `UpdateBigBoardUseCase` | JWT · dono do save | Reordena o quadro (expectedVersion) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/scouting/invest` | RF39 | `InvestScoutingUseCase` | JWT · dono do save | Sobe Club.scoutingLevel (1–5): mais pontos por semana e mais precisão |
| ✅ | `GET` | `/api/v1/careers/{id}/dashboard` | RF40 | `GetClubDashboardUseCase` | JWT · dono do save | Head coach, franquia, próximo jogo ou bye, próximos adversários da divisão, partida ao vivo, últimas semanas, divisão, cap space, lesões, alertas IA, inbox |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/morale` | RF40 | `GetManagerMoraleUseCase` | JWT · dono do save | Moral com a franquia e com a liga + fatores (RF41) |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/live-card` | RF40 | `GetLiveMatchCardUseCase` | JWT · dono do save | Placar, quarto, relógio, posse e 'descida & distância at OPP yd' (Redis) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/morale` | RF41 | `GetManagerMoraleUseCase` | JWT · dono do save | moraleClub/moraleLeague, fatores, tendência, semanas em zona crítica, risco de demissão |
| ✅ | `GET` | `/api/v1/careers/{id}/players/{pid}` | RF41 | `GetPlayerSheetUseCase` | JWT · dono do save | Moral do jogador, fatores (snaps, contrato, vitórias, papel), pedido de troca, modificador de desempenho |
| ✅ | `POST` | `/api/v1/careers/{id}/weeks/current/play` | RF41 | `PlayWeekUseCase` | JWT · dono do save | Ao fechar a semana publica WeekCompleted → MoraleWeekJob |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/inbox/{mid}/respond` | RF41 | `RespondInboxMessageUseCase` | JWT · dono do save | InboxDecisionPolicy → deltas de moral, caixa e follow-up (renovação, promessa de snaps, troca) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/analysis` | RF41 | `GetAnalysisReportUseCase` | JWT · dono do save | Previsão de zona e risco de demissão (ClubAnalyst) |
| ✅ | `POST` | `/api/v1/careers/{id}/weeks/current/play` | RF42 | `PlayWeekUseCase` | JWT · dono do save | Inicia a semana: MatchSession do usuário (seed derivada) e jogos da IA em job |
| ✅ | `POST` | `/api/v1/matches/{mid}/advance` | RF42 | `AdvanceMatchUseCase` | JWT · dono do save | Avança PLAY \| DRIVE \| QUARTER \| TO_DECISION \| FULL até o próximo momento decisivo ou intervalo |
| ✅ | `GET` | `/api/v1/matches/{mid}` | RF42 | `GetMatchStateUseCase` | JWT · dono do save | Estado vivo: quarto, relógio, play clock, posse, down, distance, ballOnAbsYd, tempos, placar, decisão aberta, versão |
| ✅ | `POST` | `/api/v1/matches/{mid}/play-calls/{did}` | RF42 | `SubmitPlayCallUseCase` | JWT · dono do save | Chamada de jogada no momento decisivo: PlayDefinition escolhida (ou ir para a 4ª, FG, punt, 2 pontos) · expectedVersion |
| ⛔ alvo | `POST` | `/api/v1/matches/{mid}/timeouts` | RF42 | `CallTimeoutUseCase` | JWT · dono do save | Tempo técnico da franquia do usuário (máx. 3 por tempo) |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/plays` | RF42 | `GetPlayByPlayUseCase` | JWT · dono do save | Play-by-play paginado por seq (Cassandra) |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/drives` | RF42 | `GetDrivesUseCase` | JWT · dono do save | Drives: início, jogadas, jardas, tempo de posse, resultado |
| ✅ | `GET` | `/api/v1/matches/{mid}/report` | RF42 | `GetMatchReportUseCase` | JWT · dono do save | Box score por jogador e equipe (§3.6), fatores decisivos, líderes, confrontos-chave, público e renda |
| ✅ | `GET` | `/api/v1/matches/{mid}` | RF43 | `MatchQueryUseCases.state` | JWT · dono do save | Estado vivo + snapshot tático: 11 por lado, depth chart em campo, fadiga, lesões, uniformes e capacetes fixados, versão |
| ⛔ alvo | `GET` | `/api/v1/clubs/{cid}/playbook` | RF43 | `PlaybookQueryUseCase` | JWT · dono do save | Jogadas com diagrama (formação, rotas, bloqueios, cobertura) para desenhar no mini campo |
| ✅ | `POST` | `/api/v1/matches/{mid}/decisions` | RF43 | `AdvanceMatchUseCase.decisions` | JWT · dono do save | Lote do intervalo: DEPTH_SWAP, GAME_PLAN, PLAYBOOK_EMPHASIS · tudo ou nada · recusa com código |
| ✅ | `POST` | `/api/v1/matches/{mid}/advance` | RF43 | `AdvanceMatchUseCase` | JWT · dono do save | Confirma e inicia o próximo quarto (snapshotVersion confere) |
| ⛔ alvo | `GET` | `/api/v1/me/player` | RF44 | `CustomPlayerUseCases.get` | JWT | Meu jogador: dados, posição, atributos, estado do avatar |
| ⛔ alvo | `GET` | `/api/v1/me/player/rules` | RF44 | `CustomPlayerUseCases.rules` | JWT | Faixas de altura/peso e números por posição, orçamento por grupo dos ≈40 subatributos, limites de upload |
| ⛔ alvo | `PUT` | `/api/v1/me/player` | RF44 | `CustomPlayerUseCases.save` | JWT | Dados, posição e atributos (orçamento validado) |
| ⛔ alvo | `POST` | `/api/v1/me/player/photo` | RF44 | `CustomPlayerUseCases.uploadPhoto` | JWT · consentimento BIOMETRIC_AVATAR | Foto JPEG/PNG ≤ 5 MB, EXIF removido |
| ⛔ alvo | `PUT` | `/api/v1/me/player/avatar` | RF44 | `CustomPlayerUseCases.saveAvatar` | JWT · consentimento | Marcos faciais (do navegador) → parâmetros do rosto + quality gate |
| ⛔ alvo | `PUT` | `/api/v1/me/player/double` | RF44 | `DigitalDoubleUseCases.revise` | JWT · expectedRevision | Revisão N+1 dos traços · anula a aprovação |
| ⛔ alvo | `POST` | `/api/v1/me/player/double/approve` | RF44 | `DigitalDoubleUseCases.approve` | JWT | Aprovação humana da revisão atual |
| ⛔ alvo | `DELETE` | `/api/v1/me/player/avatar` | RF44 | `CustomPlayerUseCases.redo` | JWT | Refazer: apaga foto, textura, modelo e marcos |
| ⛔ alvo | `POST` | `/api/v1/online/rooms` | RF45 | `MultiplayerRoomUseCases.create` | JWT | Cria sala (formato, regras PRO/COLLEGE, 2–8 humanos, prazo 24 h) com código de 6 caracteres |
| ⛔ alvo | `POST` | `/api/v1/online/rooms/join` | RF45 | `MultiplayerRoomUseCases.join` | JWT | Entra pelo código e escolhe franquia livre |
| ⛔ alvo | `POST` | `/api/v1/online/rooms/{id}/start` | RF45 | `MultiplayerRoomUseCases.start` | JWT · host | Clona o mundo uma vez e cria um CareerSave por participante |
| ⛔ alvo | `POST` | `/api/v1/online/rooms/{id}/turns/{n}/confirm` | RF45 | `MultiplayerTurnUseCases.confirm` | JWT · participante | Confirma depth chart e plano da semana · simula quando todos confirmam ou o prazo vence |
| ⛔ alvo | `GET` | `/api/v1/ultimate/squad` | RF45 | `UltimateSquadUseCases.get` | JWT | Elenco online: rating, divisão, química, status |
| ⛔ alvo | `GET` | `/api/v1/ultimate/squad/cards` | RF45 | `UltimateSquadUseCases.cards` | JWT | Cartas (template, nível Bronze…Elite, contratos, slot) |
| ⛔ alvo | `PUT` | `/api/v1/ultimate/squad/depth-chart` | RF45 | `UltimateSquadUseCases.depthChart` | JWT | Depth chart de cartas: 53 no elenco, 46 ativas, titulares por posição e unidade · recalcula química |
| ⛔ alvo | `GET` | `/api/v1/ultimate/calendar` | RF45 | `OnlineCompetitionUseCases.calendar` | JWT | Temporada online, janelas e cotas (4 Divisões / 2 Copa) |
| ⛔ alvo | `POST` | `/api/v1/ultimate/divisions/enter` | RF45 | `OnlineCompetitionUseCases.enterDivisions` | JWT | Entrada numa corrida de Divisões (201 / 422 com motivo) |
| ⛔ alvo | `POST` | `/api/v1/ultimate/cup/enter` | RF45 | `OnlineCompetitionUseCases.enterCup` | JWT | Entrada na Copa com ingresso das divisões 1–2 |
| ⛔ alvo | `POST` | `/api/v1/ultimate/matches/{id}/accept` | RF45 | `OnlineMatchUseCases.accept` | JWT · participante | Aceite em ≤ 30 s |
| ⛔ alvo | `POST` | `/api/v1/ultimate/matches/{id}/play-calls/{did}` | RF45 | `OnlineMatchUseCases.playCall` | JWT · participante | Chamada de jogada nos momentos decisivos (os dois head coaches, prazo curto) |
| ⛔ alvo | `POST` | `/api/v1/ultimate/matches/{id}/heartbeat` | RF45 | `OnlineMatchUseCases.heartbeat` | JWT · participante | Batimento a cada 5 s (placar, quarto, descida) |
| ⛔ alvo | `POST` | `/api/v1/ultimate/matches/{id}/quit` | RF45 | `OnlineMatchUseCases.quit` | JWT · participante | Abandono ⇒ WO ao adversário |
| ⛔ alvo | `POST` | `/api/v1/ultimate/sanctions/{id}/appeal` | RF45 | `OnlineCompetitionUseCases.appeal` | JWT · dono | Recurso em até 72 h |
| ✅ | `PUT` | `/api/v1/me` | RF46 | `AccountUseCases.updateProfile` | JWT | Correção de dados (Art. 18, III) |
| ✅ | `PUT` | `/api/v1/me/email` | RF46 | `EmailChangeUseCases.request` | JWT · reautenticação | Troca com verificação de posse |
| ✅ | `PUT` | `/api/v1/me/password` | RF46 | `AccountUseCases.changePassword` | JWT · reautenticação | Encerra as outras sessões |
| ✅ | `GET` | `/api/v1/me/consents` | RF46 | `PrivacyUseCases.view` | JWT | Consentimento por finalidade |
| ✅ | `PUT` | `/api/v1/me/consents` | RF46 | `PrivacyUseCases.update` | JWT | Conceder ou revogar · biometria apaga mídia |
| ✅ | `GET` | `/api/v1/me/consents/history` | RF46 | `PrivacyUseCases.history` | JWT | Histórico append-only (versão do texto, origem, hash do IP) |
| ✅ | `GET` | `/api/v1/me/settings` | RF46 | `SettingsUseCases.get` | JWT | Preferências atuais |
| ✅ | `PUT` | `/api/v1/me/settings` | RF46 | `SettingsUseCases.update` | JWT | Tema, contraste, fonte, movimento, idioma, formato regional, unidade (jardas ou metros) |
| ✅ | `POST` | `/api/v1/me/export` | RF46 | `DataExportUseCases.request` | JWT | Exportação JSON + CSV (24 h) |
| ✅ | `GET` | `/api/v1/me/export/{jobId}/download` | RF46 | `DataExportUseCases.download` | JWT · dono do job | Download (DATA_EXPORT_DOWNLOADED) |
| ✅ | `DELETE` | `/api/v1/me` | RF46 | `AccountDeletionUseCases.request` | JWT · senha + palavra (step-up) | Exclusão com 7 dias de arrependimento |
| ✅ | `POST` | `/api/v1/me/deletion/cancel` | RF46 | `AccountDeletionUseCases.cancel` | JWT | Cancela dentro do prazo |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/players/{pid}/gauges` | RF47 | `GetPlayerGaugesUseCase` | JWT · dono do save | ?scope=SEASON\|LAST5\|CAREER&position= · velocímetros da família da posição com valor, escala, percentil na posição e amostra |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/players/{pid}/gauges/compare` | RF47 | `ComparePlayerGaugesUseCase` | JWT · dono do save | ?other={pid2} · mesmos velocímetros lado a lado (mesma família) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/stats/leaders` | RF47 | `GetStatLeadersUseCase` | JWT · dono do save | ?position=&metric= · líderes da liga (OpenSearch) |
| ⛔ alvo | `GET` | `/api/v1/catalog/gauges` | RF47 | `GaugeCatalogUseCase` | JWT | Definições por família: métrica, fórmula, escala mín./máx., sentido (maior ou menor é melhor), amostra mínima, versão |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/players/{pid}/attributes` | RF48 | `GetPlayerAttributesUseCase` | JWT · dono do save | ≈40 subatributos, macros da família, overall por posição, nível do card, efeitos de estilos e consumíveis ativos |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/players/{pid}/attributes/history` | RF48 | `GetAttributeHistoryUseCase` | JWT · dono do save | Evolução por temporada (progressão, idade, lesões) |
| ⛔ alvo | `GET` | `/api/v1/catalog/attributes` | RF48 | `AttributeCatalogUseCase` | JWT | Definições, grupos, macros por família (D-6), textos i18n, ETag |
| ⛔ alvo | `GET` | `/api/v1/catalog/overall-weights` | RF48 | `OverallWeightsQueryUseCase` | JWT | ?version= · pesos por posição (OVR v1 vigente) |
| ⛔ alvo | `POST` | `/api/v1/admin/overall-weights` | RF48 | `OverallWeightsAdminUseCase.propose` | JWT · ADMIN | Nova versão de pesos (rascunho) com vetor de ouro |
| ⛔ alvo | `POST` | `/api/v1/admin/overall-weights/{v}/activate` | RF48 | `OverallWeightsAdminUseCase.activate` | JWT · ADMIN | Ativa a versão e agenda o recálculo dos overalls |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/cap` | RF49 | `GetCapSheetUseCase` | JWT · dono do save | SalaryCapLedger da temporada e das 3 seguintes: teto, comprometido, dead money, espaço, rollover |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/contracts` | RF49 | `ListContractsUseCase` | JWT · dono do save | Contratos com cap hit por ano, bônus rateado, garantias e dead money se cortado |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/contract/negotiate` | RF49 | `NegotiateContractUseCase` | JWT · dono do save | Proposta de extensão (anos, salário por ano, bônus, garantias) → ACCEPT \| COUNTER \| REJECT |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/contract/restructure` | RF49 | `RestructureContractUseCase` | JWT · dono do save | Converte salário-base em bônus rateado: alivia o ano atual, pesa nos próximos |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/players/{pid}/release/preview` | RF49 | `PreviewReleaseUseCase` | JWT · dono do save | Dead money e economia, normal e pós-1º de junho |
| ✅ | `POST` | `/api/v1/careers/{id}/players/{pid}/release` | RF49 | `ReleasePlayerUseCase` | JWT · dono do save | Corta o jogador (waivers, RF65) e lança o dead money · expectedLedgerVersion |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/players/{pid}/franchise-tag` | RF49 | `ApplyFranchiseTagUseCase` | JWT · dono do save | Tag de 1 ano: valor = máx(média dos 5 maiores salários da posição, 120 % do salário anterior) |
| ⛔ alvo | `DELETE` | `/api/v1/careers/{id}/players/{pid}/franchise-tag` | RF49 | `RemoveFranchiseTagUseCase` | JWT · dono do save | Retira a tag antes de assinada (jogador vira agente livre) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/trade/assets` | RF50 | `ListTradeAssetsUseCase` | JWT · dono do save | Meus jogadores (contrato, cap hit, dead money se trocado) e picks (temporada, rodada, overall se conhecido, dono original) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/clubs/{cid}/trade-assets` | RF50 | `ListClubTradeAssetsUseCase` | JWT · dono do save | Ativos da outra franquia e necessidades por posição |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/trades/evaluate` | RF50 | `EvaluateTradeUseCase` | JWT · dono do save | Prévia: valor de cada lado (tabela de picks + valor do jogador), teto das duas franquias depois, elenco, chance de aceite |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/trades` | RF50 | `ProposeTradeUseCase` | JWT · dono do save | Envia a proposta (expira em 2 semanas ou no prazo de trocas) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/trades` | RF50 | `ListTradesUseCase` | JWT · dono do save | Enviadas e recebidas com status |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/trades/{tid}/respond` | RF50 | `RespondTradeUseCase` | JWT · dono do save | Recebida: ACCEPT \| REJECT \| COUNTER |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/trades/{tid}/withdraw` | RF50 | `WithdrawTradeUseCase` | JWT · dono do save | Retira proposta enviada ainda pendente |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/draft/class` | RF51 | `GetDraftClassUseCase` | JWT · dono do save | Classe do ano: status, prospectos com faixa de potencial (névoa conforme o scouting) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/draft/prospects/{pid}` | RF51 | `GetDraftProspectUseCase` | JWT · dono do save | Perfil, college, rodada projetada, combine e relatórios comprados |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/draft/prospects/{pid}/scouting` | RF51 | `BuyScoutingReportUseCase` | JWT · dono do save | Compra relatório (custo por nível de scout); estreita a faixa de potencial |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/draft/combine` | RF51 | `GetCombineResultsUseCase` | JWT · dono do save | Resultados do combine por posição (40 jardas, supino, vertical, distância, 3-cone, shuttle, entrevista) |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/draft/board` | RF51 | `SaveDraftBoardUseCase` | JWT · dono do save | Big board do usuário (ordem de preferência; usado na escolha automática) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/draft/picks/{pickId}/select` | RF51 | `MakeDraftPickUseCase` | JWT · dono do save | Escolhe o prospecto na vez; cria Player com contrato ROOKIE |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/draft/advance` | RF51 | `AdvanceDraftUseCase` | JWT · dono do save | IA escolhe até o próximo pick do usuário (ou até o fim) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/press` | RF52 | `ListPressConferencesUseCase` | JWT · dono do save | Coletivas abertas e histórico com impacto |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/press/{pcId}` | RF52 | `GetPressConferenceUseCase` | JWT · dono do save | Perguntas, opções de tom e prazo |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/press/{pcId}/answers` | RF52 | `AnswerPressQuestionUseCase` | JWT · dono do save | Responde uma pergunta (tom: confiante, cauteloso, crítico, evasivo) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/press/{pcId}/skip` | RF52 | `SkipPressConferenceUseCase` | JWT · dono do save | Falta à coletiva (penalidade da diretoria se repetir) |
| ⛔ alvo | `GET` | `/api/v1/rulesets` | RF53 | `ListRulesetsUseCase` | público | Presets PRO e COLLEGE com os valores padrão |
| ⛔ alvo | `GET` | `/api/v1/leagues/{id}/rules` | RF53 | `GetLeagueRulesUseCase` | JWT | Regras vigentes da liga (versão) |
| ⛔ alvo | `POST` | `/api/v1/leagues/{id}/rules/preview` | RF53 | `PreviewLeagueRulesUseCase` | JWT | Valida e mostra diferenças e impacto sem gravar |
| ⛔ alvo | `PUT` | `/api/v1/leagues/{id}/rules` | RF53 | `UpdateLeagueRulesUseCase` | JWT | Grava nova versão; vale na próxima temporada se houver save em curso |
| ⛔ alvo | `GET` | `/api/v1/catalog/play-events` | RF54 | `ListPlayEventTypesUseCase` | público | Tipos de jogada, resultados e faltas (jardas, 1ª descida automática, perda de descida) |
| ✅ | `GET` | `/api/v1/matches/{mid}/events` | RF54 | `ListMatchEventsUseCase` | JWT · dono do save | Play-by-play paginado (quarto, drive, tipo, só faltas, só pontuação) |
| ⛔ alvo | `POST` | `/api/v1/matches/{mid}/penalties/{seq}/decision` | RF54 | `DecidePenaltyUseCase` | JWT · dono do save | Aceita ou recusa a falta quando o nível de chamada pede (RF62) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/snap-plan` | RF55 | `GetSnapPlanUseCase` | JWT · dono do save | Porcentagem-alvo por jogador, rodízio da DL e snaps reais da temporada |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/snap-plan` | RF55 | `UpdateSnapPlanUseCase` | JWT · dono do save | Grava alvos e rodízio (expectedVersion) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/chemistry` | RF55 | `GetUnitChemistryUseCase` | JWT · dono do save | Química da OL (5 titulares juntos) e dos pares QB-WR e QB-TE |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/practice-squad` | RF56 | `GetPracticeSquadUseCase` | JWT · dono do save | Membros, elevações usadas, protegidos da semana |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/practice-squad` | RF56 | `SignPracticeSquadPlayerUseCase` | JWT · dono do save | Assina free agent ou jogador dispensado para o practice squad |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/practice-squad/{psId}/elevate` | RF56 | `ElevatePracticeSquadPlayerUseCase` | JWT · dono do save | Eleva para o próximo jogo (volta sozinho depois) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/practice-squad/{psId}/promote` | RF56 | `PromoteToActiveRosterUseCase` | JWT · dono do save | Promove ao elenco de 53 com contrato ativo |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/practice-squad/{psId}/protection` | RF56 | `ProtectPracticeSquadPlayerUseCase` | JWT · dono do save | Protege na semana (até 4) |
| ⛔ alvo | `DELETE` | `/api/v1/careers/{id}/practice-squad/{psId}` | RF56 | `ReleasePracticeSquadPlayerUseCase` | JWT · dono do save | Dispensa do practice squad |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/all-star` | RF57 | `GetAllStarEventUseCase` | JWT · dono do save | Status, elencos por conferência, desafios e resultado |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/all-star/votes` | RF57 | `VoteAllStarUseCase` | JWT · dono do save | Voto do head coach: 1 por posição, sem o próprio clube em mais de 3 posições |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/all-star/results` | RF57 | `GetAllStarResultsUseCase` | JWT · dono do save | Placar do jogo, vencedores dos desafios (40 jardas, precisão de passe, chute de distância) |
| ⛔ alvo | `GET` | `/api/v1/admin/licenses` | RF58 | `ListDigitalTwinLicensesUseCase` | JWT · ADMIN | Licenças por status e validade |
| ⛔ alvo | `POST` | `/api/v1/admin/licenses` | RF58 | `RegisterDigitalTwinLicenseUseCase` | JWT · ADMIN | Cadastra licença (licenciante, escopo, validade, atleta) |
| ⛔ alvo | `POST` | `/api/v1/admin/licenses/{lid}/revoke` | RF58 | `RevokeDigitalTwinLicenseUseCase` | JWT · ADMIN | Revoga; o jogador volta ao fictício |
| ⛔ alvo | `GET` | `/api/v1/players/{pid}/twin` | RF58 | `GetDigitalTwinUseCase` | JWT | Dados licenciados dentro do escopo, ou versão fictícia |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/plays/{seq}/diagram` | RF59 | `GetPlayDiagramUseCase` | JWT · dono do save | Formação, rotas, bloqueios, cobertura e trajetória real da jogada |
| ⛔ alvo | `GET` | `/api/v1/fixtures/{fid}/drives` | RF59 | `ListDrivesUseCase` | JWT · dono do save | Drives com jogadas para navegar no mini campo |
| ✅ | `GET` | `/api/v1/matches/{mid}/events` | RF59 | `ListMatchEventsUseCase` | JWT · dono do save | Jogadas do drive escolhido (RF54) |
| ✅ | `GET` | `/api/v1/careers/{id}/depth-chart` | RF60 | `GetDepthChartUseCase` | JWT · dono do save | Slots por unidade e posição, overall na posição, avisos |
| ✅ | `PUT` | `/api/v1/careers/{id}/depth-chart` | RF60 | `UpdateDepthChartUseCase` | JWT · dono do save | Grava slots (expectedVersion) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/depth-chart/auto` | RF60 | `AutoFillDepthChartUseCase` | JWT · dono do save | Sugestão pelo overall na posição e condição |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/depth-chart/validate` | RF60 | `ValidateDepthChartUseCase` | JWT · dono do save | Valida sem gravar |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/playbooks` | RF61 | `ListPlaybooksUseCase` | JWT · dono do save | Playbooks por esquema (West Coast, Air Raid, Spread, Power Run) e o ativo |
| ⛔ alvo | `GET` | `/api/v1/playbooks/{pbId}/plays` | RF61 | `ListPlayDefinitionsUseCase` | JWT | Jogadas com filtros (lado, formação, conceito, cobertura) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/playbooks` | RF61 | `CreateCustomPlaybookUseCase` | JWT · dono do save | Cria playbook próprio a partir de um esquema |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/playbooks/active` | RF61 | `SetActivePlaybookUseCase` | JWT · dono do save | Define o playbook ativo |
| ✅ | `GET` | `/api/v1/careers/{id}/game-plan` | RF61 | `GetGamePlanUseCase` | JWT · dono do save | Plano da semana |
| ✅ | `PUT` | `/api/v1/careers/{id}/game-plan` | RF61 | `UpdateGamePlanUseCase` | JWT · dono do save | Grava tendências, 4ª descida, ritmo, foco, blitz, jogadores-chave |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/game-plan/suggestion` | RF61 | `SuggestGamePlanUseCase` | JWT · dono do save | Sugestão contra o próximo adversário (fixtureId) |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/decisions/current` | RF62 | `GetOpenPlayCallUseCase` | JWT · dono do save | Decisão aberta: situação, descida e distância, 3 a 5 opções, prazo |
| ⛔ alvo | `POST` | `/api/v1/matches/{mid}/decisions/{did}` | RF62 | `SubmitPlayCallUseCase` | JWT · dono do save | Escolhe a jogada (expectedVersion da sessão) |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/stream` | RF62 | `MatchStreamUseCase` | JWT · dono do save | SSE: DecisionOpened, DecisionResolved, PlayResolved |
| ✅ | `PUT` | `/api/v1/me/settings` | RF62 | `UpdateUserSettingsUseCase` | JWT | Nível de chamada (KEY_MOMENTS, ALL_PLAYS, NONE) e tempo limite (RF10) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/special-teams` | RF63 | `GetSpecialTeamsUseCase` | JWT · dono do save | Unidades, chutador, holder, long snapper, retornadores, estatísticas |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/special-teams/{kind}` | RF63 | `UpdateSpecialTeamsUnitUseCase` | JWT · dono do save | Grava a unidade (KICKOFF, KICK_RETURN, PUNT, PUNT_RETURN, FG_PAT, ONSIDE) |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/special-teams/strategy` | RF63 | `UpdateSpecialTeamsStrategyUseCase` | JWT · dono do save | Fair catch, touchback, tabela de conversão de 2, onside por placar |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/injury-report` | RF64 | `GetInjuryReportUseCase` | JWT · dono do save | Relatório da semana do clube (week) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/injury-report/league` | RF64 | `GetLeagueInjuryReportUseCase` | JWT · dono do save | Relatórios publicados das outras franquias (week, clubId) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/injury-report/{rid}/ir` | RF64 | `PlaceOnInjuredReserveUseCase` | JWT · dono do save | Coloca na IR (mínimo de semanas) e abre vaga no 53 |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/injury-report/{rid}/activate` | RF64 | `ActivateFromInjuredReserveUseCase` | JWT · dono do save | Volta da IR (designações de retorno por temporada) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/free-agents` | RF65 | `SearchFreeAgentsUseCase` | JWT · dono do save | Busca com facetas (posição, idade, overall, pedida) |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/free-agents/{pid}/offers` | RF65 | `MakeFreeAgentOfferUseCase` | JWT · dono do save | Oferta: anos, valor total, garantias, bônus de assinatura |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/free-agent-offers` | RF65 | `ListFreeAgentOffersUseCase` | JWT · dono do save | Minhas ofertas e status |
| ⛔ alvo | `DELETE` | `/api/v1/careers/{id}/free-agent-offers/{oid}` | RF65 | `WithdrawFreeAgentOfferUseCase` | JWT · dono do save | Retira oferta pendente |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/roster/{pid}/release` | RF65 | `ReleasePlayerUseCase` | JWT · dono do save | Dispensa: vai a waivers e gera dead money |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/waivers` | RF65 | `ListWaiverWireUseCase` | JWT · dono do save | Jogadores em waivers, prazo e minha prioridade |
| ⛔ alvo | `POST` | `/api/v1/careers/{id}/waivers/{pid}/claims` | RF65 | `SubmitWaiverClaimUseCase` | JWT · dono do save | Pede o jogador (herda o contrato) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/playoffs/seeds` | RF66 | `GetPlayoffSeedsUseCase` | JWT · dono do save | Seeds por conferência (projeção na temporada, oficial no fim) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/playoffs/bracket` | RF66 | `GetPlayoffBracketUseCase` | JWT · dono do save | Chave por rodada (Wild Card, Divisional, Conferência, Final) |
| ⛔ alvo | `GET` | `/api/v1/careers/{id}/playoffs/matchups/{mid}` | RF66 | `GetPlayoffMatchupUseCase` | JWT · dono do save | Confronto: seeds, local, placar, vencedor |
| ✅ | `POST` | `/api/v1/careers` | RNF1 | `CreateCareerUseCase` | JWT | Clona o mundo TEMPLATE para um mundo SAVE e cria o salvamento numa só transação |
| ✅ | `GET` | `/api/v1/careers/{id}` | RNF1 | `CareerQueries` | JWT · dono do save | Retomar: lê o último commit do mundo SAVE |
| ⛔ alvo | `PUT` | `/api/v1/editor/players/{id}` | RNF1 | `WorldEditorUseCases` | JWT | Edita o database do jogo do usuário (TEMPLATE); carreiras existentes não mudam |
| ✅ | `PUT` | `/api/v1/careers/{id}/depth-chart` | RNF1 | `UpdateDepthChartUseCase` | JWT · dono do save | Escrita típica de carreira: DepthChart com @Version (409 em conflito) |
| ✅ | `POST` | `/api/v1/matches/{mid}/advance` | RNF1 | `AdvanceMatchUseCase` | JWT · dono do save | MatchSession com @Version · play-by-play no Cassandra e estado no Redis só depois do commit |
| ✅ | `PUT` | `/api/v1/me/settings` | RNF2 | `UpdateUserSettingsUseCase` | JWT | simQuarterMinutes (0 = instantâneo, 1–15) · playCallingLevel KEY_MOMENTS\|ALL_PLAYS\|NONE · decisionTimeoutSeconds |
| ✅ | `POST` | `/api/v1/careers/{id}/weeks/current/play` | RNF2 | `PlayWeekUseCase` | JWT · dono do save | Cria a MatchSession com orçamento de tempo congelado no snapshot das configurações |
| ✅ | `POST` | `/api/v1/matches/{mid}/advance` | RNF2 | `AdvanceMatchUseCase` | JWT · dono do save | PLAY \| DRIVE \| QUARTER \| FULL · para no próximo momento decisivo |
| ⛔ alvo | `POST` | `/api/v1/matches/{mid}/decisions/{did}` | RNF2 | `SubmitPlayCallUseCase` | JWT · dono do save | Chamada de jogada (4ª descida, 2 minutos, prorrogação) dentro do tempo limite |
| ✅ | `GET` | `/api/v1/matches/{mid}/events` | RNF2 | `GetMatchEventsUseCase` | JWT · dono do save | Play-by-play paginado (after=seq) no ritmo calculado |
| ✅ | `GET` | `/api/v1/careers/{id}/dashboard` | RNF3 | `GetClubDashboardUseCase` | JWT · dono do save | Recurso do save: CareerAccess.requireOwner(saveId, userId) no caso de uso |
| ✅ | `GET` | `/api/v1/me` | RNF3 | `AccountUseCases.profile` | JWT | Recurso do próprio usuário: sempre jwt.sub, nunca id vindo do cliente |
| ⛔ alvo | `GET` | `/api/v1/admin/audit` | RNF3 | `QueryAuditLogUseCase` | JWT · ADMIN | Rota administrativa: /api/v1/admin/** exige ADMIN |
| ✅ | `POST` | `/api/v1/auth/login` | RNF3 | `LoginUseCase` | público | Rota pública explícita na allowlist (/auth/**, /actuator/health, /api-docs/**, /error) |
| ✅ | `POST` | `/api/v1/auth/login` | RNF4 | `LoginUseCase` | público · rate limit por IP | Corpo: accessToken (memória). Set-Cookie: __Host-scores_af_rt HttpOnly Secure SameSite=Strict Path=/ Max-Age 7 d |
| ✅ | `POST` | `/api/v1/auth/refresh` | RNF4 | `RefreshTokenUseCase` | cookie __Host- · Origin conferido | Rotaciona o refresh na mesma família e devolve novo access · reuso revoga a família |
| ✅ | `POST` | `/api/v1/auth/logout` | RNF4 | `LogoutUseCase` | JWT | Revoga a família e apaga o cookie (Max-Age=0) · outras abas saem via BroadcastChannel |
| ✅ | `GET` | `/api/v1/me/sessions` | RNF4 | `AccountUseCases.sessions` | JWT | Famílias ativas (ip, userAgent, criada, expira, atual) |
| ✅ | `DELETE` | `/api/v1/me/sessions/{id}` | RNF4 | `AccountUseCases.revokeSession` | JWT | Revoga outra família do próprio usuário · SESSION_REVOKED |
| ✅ | `POST` | `/api/v1/auth/register` | RNF5 | `RegisterUserUseCase` | público | email → AES-GCM + emailHash HMAC-SHA256 · senha → Argon2id fora da transação, com teto de hashes simultâneos |
| ✅ | `PUT` | `/api/v1/me` | RNF5 | `AccountUseCases.updateProfile` | JWT | displayName recifrado · AuditLog SENSITIVE_DATA_CHANGED sem valores |
| ✅ | `GET` | `/api/v1/me` | RNF5 | `AccountUseCases.profile` | JWT | Decifra só para o titular |
| ⛔ alvo | `POST` | `/api/v1/admin/crypto/rotate` | RNF5 | `RotateEncryptionKeyUseCase` | JWT · ADMIN | Rotação por versão de chave (kid) com recifragem em lotes |
| ⛔ alvo | `GET` | `/api/v1/admin/backups` | RNF6 | `ListBackupsUseCase` | JWT · ADMIN | Backups (data, tamanho, checksum, verificação, último restore de teste) |
| ⛔ alvo | `POST` | `/api/v1/admin/backups/run` | RNF6 | `RunBackupUseCase` | JWT · ADMIN | Backup manual antes de migração ou deploy |
| ⛔ alvo | `POST` | `/api/v1/admin/backups/{id}/restore` | RNF6 | `RestoreBackupUseCase` | JWT · ADMIN | Restore com dupla confirmação e app em somente leitura |
| ⛔ alvo | `POST` | `/api/v1/admin/projections/rebuild` | RNF6 | `RebuildProjectionsUseCase` | JWT · ADMIN | Reconstrói Cassandra, Redis e OpenSearch a partir do MySQL (idempotente) |
| ⛔ alvo | `GET` | `/actuator/health` | RNF6 | `HealthIndicator` | público | backup.lastSuccessAt como indicador (alerta se > 26 h) |
| ✅ | `GET` | `/api/v1/me/activity` | RNF7 | `ActivityUseCases.list` | JWT | Atividades da própria conta |
| ✅ | `POST` | `/api/v1/auth/login` | RNF7 | `LoginUseCase` | público | LOGIN_OK / LOGIN_FAILED / LOGIN_LOCKED · o LOGIN_FAILED sobrevive ao rollback |
| ✅ | `POST` | `/api/v1/careers/{id}/weeks/current/play` | RNF7 | `PlayWeekUseCase` | JWT · dono do save | MATCH_SIMULATED com seed e placar · replay verificável |
| ⛔ alvo | `GET` | `/api/v1/admin/audit` | RNF7 | `QueryAuditLogUseCase` | JWT · ADMIN | Consulta paginada por actor, action, resource, período, correlationId |
| ✅ | `GET` | `/api/v1/me` | RNF8 | `AccountUseCases.profile` | JWT | Acesso: dados pessoais decifrados só para o titular |
| ✅ | `PUT` | `/api/v1/me/consents` | RNF8 | `PrivacyUseCases.update` | JWT | Concede ou revoga por finalidade (ConsentRecord append-only) · biometria revogada apaga a mídia |
| ✅ | `POST` | `/api/v1/me/export` | RNF8 | `DataExportUseCases.request` | JWT | Portabilidade: DataExportJob → JSON + CSV no S3 privado por 24 h |
| ✅ | `GET` | `/api/v1/me/export/{jobId}/download` | RNF8 | `DataExportUseCases.download` | JWT · dono do job | DATA_EXPORT_DOWNLOADED |
| ✅ | `DELETE` | `/api/v1/me` | RNF8 | `AccountDeletionUseCases.request` | JWT · senha (step-up) | PENDING_DELETION, sessões revogadas, 7 dias de arrependimento, depois anonimização |
| ✅ | `POST` | `/api/v1/me/deletion/cancel` | RNF8 | `AccountDeletionUseCases.cancel` | JWT | Cancela no prazo |
| ✅ | `GET` | `/api/v1/me/settings` | RNF9 | `GetUserSettingsUseCase` | JWT | reducedMotion, highContrast, fontScale (preferências de desempenho e acessibilidade) |
| ✅ | `GET` | `/api/v1/careers/{id}/dashboard` | RNF9 | `GetClubDashboardUseCase` | JWT · dono do save | Leitura crítica com p95 ≤ 300 ms (cache Redis + ETag) |
| ✅ | `GET` | `/api/v1/matches/{mid}/events` | RNF9 | `GetMatchEventsUseCase` | JWT · dono do save | Poll incremental leve (after=seq, 204 sem novidade) |
| ⛔ alvo | `GET` | `/api/v1/matches/{mid}/formation` | RNF9 | `GetPlayFormationUseCase` | JWT · dono do save | Posições dos 22 atletas no snap (coordenadas compactas para a cena 3D) |
| ⛔ alvo | `POST` | `/api/v1/me/push-subscriptions` | RNF10 | `RegisterPushSubscriptionUseCase` | JWT | Registra {endpoint, keys.p256dh, keys.auth, userAgent} · idempotente por endpoint |
| ⛔ alvo | `DELETE` | `/api/v1/me/push-subscriptions/{sid}` | RNF10 | `RevokePushSubscriptionUseCase` | JWT | Revoga a assinatura |
| ⛔ alvo | `PUT` | `/api/v1/me/notification-preferences` | RNF10 | `UpdateNotificationPreferencesUseCase` | JWT | Liga ou desliga por tipo |
| ⛔ alvo | `GET` | `/api/v1/me/notifications` | RNF10 | `GetNotificationsUseCase` | JWT | Central in-app (Cassandra, TTL 90 d) |
| ⛔ alvo | `POST` | `/api/v1/media/upload` | RNF11 | `UploadMediaUseCase` | JWT | multipart {file, purpose: CLUB_LOGO\|HELMET_DECAL\|END_ZONE_ART\|UNIFORM\|AVATAR\|MODEL_3D} → {mediaKey, url, width, height} |
| ⛔ alvo | `GET` | `/api/v1/media/{key}` | RNF11 | `ResolveMediaUseCase` | JWT | 302 para URL assinada (15 min) após conferir posse ou visibilidade |
| ⛔ alvo | `DELETE` | `/api/v1/media/{key}` | RNF11 | `DeleteMediaUseCase` | JWT | Remove mídia não referenciada (dono ou ADMIN) |
| ⛔ alvo | `PUT` | `/api/v1/careers/{id}/club/identity` | RNF11 | `UpdateClubIdentityUseCase` | JWT · dono do save | Consumidor: vincula mediaKey ao logo, decalque e end zone |
| ⛔ alvo | `GET` | `/api/v1/store/catalog` | RNF12 | `StoreCatalogUseCase` | JWT | Cada item expõe efeito (COSMETIC_ONLY ou GAME_EFFECT), preço e escopo da carteira |
| ⛔ alvo | `POST` | `/api/v1/store/purchases` | RNF12 | `PurchaseUseCase` | JWT · Idempotency-Key | Compra sob StorePolicy (escopo, limites, teto de efeito, idempotência, auditoria) |
| ⛔ alvo | `GET` | `/api/v1/me/wallet` | RNF12 | `WalletUseCases` | JWT | Saldo e ledger com balanceAfter |
| ⛔ alvo | `POST` | `/api/v1/admin/store/purchases/{id}/refund` | RNF12 | `RefundPurchaseUseCase` | JWT · ADMIN | Lançamento compensatório · nunca edita o ledger |
| ✅ | `GET` | `/api/v1/me/settings` | RNF13 | `SettingsUseCases.get` | JWT | Lê locale da conta, regionalFormat e units |
| ✅ | `PUT` | `/api/v1/me/settings` | RNF13 | `SettingsUseCases.update` | JWT | Salva locale (pt-BR \| en-US \| es-ES) · vale para e-mails e conteúdo gerado |
| — | `*` | `/api/v1/** (Accept-Language)` | RNF13 | `LocaleResolver + MessageSource` | — | Erro RFC 7807 e e-mail no idioma da conta ou do cabeçalho, com fallback pt-BR · campo locale no corpo |
