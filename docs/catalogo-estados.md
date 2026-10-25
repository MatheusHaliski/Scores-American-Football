# Catálogo de máquinas de estado

| Req | Sujeito | Estados | Transições |
|---|---|---|---|
| RF1 | UserAccount.status (+ verificação de e-mail) | ACTIVE, ACTIVE + bloqueio por e-mail, PENDING_DELETION, DELETED | 5 |
| RF2 | UserSession (família de sessão sid) | ACTIVE, ROTATED, REVOKED (família), EXPIRED | 4 |
| RF2 | Tentativas de login por e-mail | Livre, Tentativa reservada, Bloqueado (15 min) | 5 |
| RF3 | UserAccount.status (ciclo de vida da conta) | ACTIVE, PENDING_DELETION, DELETED | 3 |
| RF3 | DataExportJob.status | REQUESTED, READY, EXPIRED, FAILED | 3 |
| RF4 | Player.rosterStatus (elenco do usuário) | ACTIVE, INACTIVE, PRACTICE_SQUAD, IR, WAIVED, FREE_AGENT | 12 |
| RF5 | World no editor (kind × ownerUser) | Mundo semente, TEMPLATE do usuário, Formulário aberto, Salvando, Cópia SAVE | 7 |
| RF6 | DepthChart na preparação (validated × edição) | Carregando, Salvo, Editando, Salvando, Inválido, Desatualizado, Pronto para jogar | 12 |
| RF7 | Modal Preparar time → partida (interação) × Fixture.status | Fechado, Carregando, Preparando (LED 3D), Depth chart inválido, Pronto, Iniciando, Em partida (RF27), Semana de folga, Espectador | 14 |
| RF8 | ManagerContract.status | PROPOSED, ACTIVE, RENEGOTIATING, RESIGNED, DISMISSED, EXPIRED, TRANSFERRED | 9 |
| RF9 | JobOffer.status | PENDING, ACCEPTED, REJECTED, EXPIRED | 4 |
| RF10 | Tela de configurações (interação) | Carregando, Exibindo, Editando com prévia, Salvando, Salvo, Erro | 9 |
| RF11 | CareerSave.status | Em criação (RF34), ACTIVE, ARCHIVED, Excluído | 6 |
| RF12 | StadiumSector (obra e operação) | Comprado, Em obra, Operacional, Degradado, Em reforma, Removido | 8 |
| RF12 | Stadium.roof (troca de cobertura) | OPEN, RETRACTABLE, DOME, Em obra (offseason) | 6 |
| RF13 | InboxMessage.status | UNREAD, READ, ANSWERED, EXPIRED, ARCHIVED | 8 |
| RF14 | BankLoan (ciclo do empréstimo) × BudgetRequest.status | ACTIVE, OVERDUE, RENEGOTIATED, SETTLED, BudgetRequest PENDING, BudgetRequest APPROVED / PARTIAL, BudgetRequest REJECTED | 9 |
| RF15 | ManagerStyle para o usuário | Disponível, Bloqueado por meta, Adquirido, Ativo no head coach, Substituído | 6 |
| RF16 | Classificação & semanas (interação) | Carregando, Erro, Tabela carregada, Semana passada, Semana atual, Semana futura, Chave de playoffs | 13 |
| RF17 | Sala de troféus (interação) | Carregando, Erro, Campeoes, Aba Meus troféus, Filtrando, Troféu 3D aberto | 10 |
| RF18 | Head coaches do mundo (interação) | Carregando, Erro, Ranking, Reordenando, Buscando, Esquema ampliado | 9 |
| RF19 | FreeAgentOffer.status | PENDING, ACCEPTED, REJECTED, WITHDRAWN, EXPIRED | 4 |
| RF19 | TradeProposal.status | PENDING, COUNTERED, ACCEPTED, REJECTED, EXPIRED | 7 |
| RF19 | WaiverClaim.status | PENDING, AWARDED, LOST | 2 |
| RF20 | Aba Buscar (interação) | Vazio, Buscando, Resultados, Resultados (fallback), Sem resultados, Erro, Esquema ampliado | 11 |
| RF21 | CalendarInsight de uma Fixture (geração e exibição) | Sem insight, Classificada, Insight por template, Insight narrado (Claude), Exibido no calendário, Fixture jogada | 7 |
| RF22 | Player.rosterStatus (ações do modal) | ACTIVE, INACTIVE, PRACTICE_SQUAD, IR, WAIVED, FREE_AGENT, Trocado | 10 |
| RF23 | Viewer 3D do jogador (interação) | Fechado, Verificando, Fallback 2D, Carregando asset, Pose estática, Reproduzindo, Crossfade, Erro de asset | 14 |
| RF24 | InboxMessage.status | UNREAD, READ, ANSWERED, EXPIRED, ARCHIVED | 7 |
| RF25 | ConsumableApplication | Comprado, Ativo, Expirado, Estornado | 4 |
| RF26 | StadiumSector (ciclo de vida de um setor) | Planejado, Em obra, Ativo, Degradado, Em reforma, Removido | 8 |
| RF26 | Obra de cobertura ou superfície (Stadium.roof / surface) | Solicitada, Vetada, Em obra, Concluída | 3 |
| RF27 | MatchSession.status visto pelo painel da semana | NOT_STARTED, LIVE, DECISION, BREAK, HALFTIME, OVERTIME, FINISHED, Espectador / bye | 12 |
| RF28 | Modal Editar franquia (interação) | Fechado, Carregando, Editando, Enviando logo/decal, Salvando, Salvo, Erro | 13 |
| RF29 | MatchSession.status com foco no playbook (BREAK / HALFTIME) | LIVE, BREAK, HALFTIME, Validando ajuste, Ajuste aplicado, Ajuste rejeitado, BREAK antes do OT, FINISHED | 14 |
| RF30 | Painel Classificação e semanas (interação) | Idle, Carregando, Tabela provisória, Tabela consolidada, Chave dos playoffs, Semana n±1, Erro | 12 |
| RF31 | Painel Detalhes da partida (interação) | Fechado, Carregando, Estimativa, Consolidado, Visão de visitante ou campo neutro, Erro | 10 |
| RF32 | Compra → direito → inventário → equipado | Selecionado, Validando, Debitando, COMPLETED, Equipado, Rejeitado, Reembolsado | 10 |
| RF32 | Produto da loja (status de publicação) | DRAFT, PUBLISHED, HIDDEN, RETIRED | 5 |
| RF33 | Goal.status | ACTIVE, IN_PROGRESS, COMPLETED, REWARDED, EXPIRED | 7 |
| RF34 | CareerSave (criação) — status | Rascunho (wizard), Validando, CREATING, Semeando temporada, ACTIVE, FAILED, Rejeitada | 9 |
| RF35 | Season.phase (fim de temporada e entressafra) | REGULAR, PLAYOFFS, OFFSEASON, FREE_AGENCY, DRAFT, PRESEASON (n+1) | 6 |
| RF36 | SimulationRun.status | QUEUED, RUNNING, PAUSED, DONE, CANCELLED | 6 |
| RF37 | CareerSave.employmentStatus × JobOffer.status | EMPLOYED, UNEMPLOYED, SPECTATOR, JobOffer PENDING, JobOffer ACCEPTED, JobOffer REJECTED, JobOffer EXPIRED | 10 |
| RF38 | AnimationClip.status | DRAFT, PUBLISHED, ARCHIVED | 3 |
| RF39 | DraftProspect (visão do clube) | Não avaliado, Observado, Avaliado, No big board, Medido no combine, Draftado, Não draftado | 9 |
| RF40 | Central do clube (interação) × CareerSave.employmentStatus | Carregando, Erro, Central (empregado), Semana de folga, Partida ao vivo, Central (espectador), Fim de temporada | 13 |
| RF41 | ManagerMorale.moraleClub — zonas com histerese | Confortável (≥ 45), Atenção (30–44), Crítica (< 30), Recuperando (30–34), Demitido | 9 |
| RF41 | Player.morale (pedido de troca) | Estável (≥ 50), Insatisfeito (35–49), Pede troca, Trocado (RF50) | 5 |
| RF42 | MatchSession.status (partida ao vivo) | NOT_STARTED, LIVE, AWAITING_PLAY_CALL, BREAK, OVERTIME, FINISHED, ABANDONED | 13 |
| RF42 | Ciclo da jogada (dentro do motor) | PRE_SNAP, Falta pré-snap, SNAP, Bola viva, Falta na jogada, Bola morta, Pontuação | 10 |
| RF42 | PlayCallDecision.status | OPEN, CHOSEN, AUTO, APPLIED, REJECTED | 6 |
| RF42 | Drive.result | Em andamento, TD, FG, PUNT, TURNOVER, DOWNS, SAFETY, END_HALF | 8 |
| RF43 | MiniField3D (cena do intervalo) | Oculta, Detectando, Qualidade alta, Qualidade baixa, Visão 2D, Atleta selecionado, Prévia de ajuste | 13 |
| RF43 | Decisão do intervalo (lote) | Rascunho, Enviada, Aplicada, Recusada, Deixou de valer | 6 |
| RF44 | Editor do Meu jogador | SemJogador, Dados e posição, Atributos, Consentimento, Foto e marcos, Insuficiente, Prévia, Aprovado | 11 |
| RF45 | DivisionRun.status | ENTERED, ACTIVE, COMPLETED, WITHDRAWN, CLOSED_BY_WINDOW, VOID | 7 |
| RF45 | MultiplayerRoom.status | LOBBY, RUNNING, FINISHED, CANCELLED | 5 |
| RF45 | MultiplayerParticipant.status | ACTIVE, LEFT, AI_TAKEOVER | 3 |
| RF45 | OnlineMatch.status | MATCHMAKING, ACCEPT_PENDING, LIVE, PAUSED_DISCONNECT, FINISHED, WALKOVER, VOID | 9 |
| RF45 | CupEntry.status | ENTERED, ACTIVE, ELIMINATED, CHAMPION, WITHDRAWN, CLOSED_BY_WINDOW | 6 |
| RF45 | OnlineSanction.appealStatus | NONE, REQUESTED, UPHELD, OVERTURNED | 3 |
| RF46 | UserAccount.status | ACTIVE, BLOCKED, PENDING_DELETION, DELETED | 5 |
| RF46 | DataExportJob.status | REQUESTED, READY, EXPIRED, FAILED | 3 |
| RF46 | ConsentRecord (finalidade) | NaoConsentido, Concedido, Desatualizado, Revogado | 6 |
| RF47 | Aba de velocímetros (interação) | Idle, Carregando, Carregado, Amostra pequena, Trocando escopo, Comparando, Erro | 11 |
| RF48 | Aba de atributos (interação) | Idle, Carregando, Grupos e macros, Overall por posição, Histórico, Erro | 8 |
| RF48 | OverallWeights.status | DRAFT, VERIFIED, ACTIVE, RETIRED | 4 |
| RF49 | PlayerContract (ciclo de vida) | ACTIVE, Em negociação, Reestruturado, Último ano, Franchise tag, Cortado, Vencido | 13 |
| RF49 | SalaryCapLedger (espaço na temporada) | Com espaço, No limite, Operação recusada, Fechado | 7 |
| RF50 | TradeProposal.status | DRAFT, PENDING, COUNTERED, ACCEPTED, EXECUTED, REJECTED, EXPIRED, WITHDRAWN, INVALIDATED | 11 |
| RF50 | DraftPick (propriedade) | Próprio, Adquirido, Numerado, Usado no draft (RF51) | 6 |
| RF51 | DraftClass.status | SCOUTING, COMBINE, LIVE, DONE | 4 |
| RF51 | DraftProspect (destino) | DISPONIVEL, ESCOLHIDO, ROOKIE_CONTRATADO, FREE_AGENT | 3 |
| RF52 | PressConference (ciclo) | AGENDADA, ABERTA, RESPONDENDO, ENCERRADA, FALTOU | 6 |
| RF53 | LeagueRules (versão) | VIGENTE, EM_EDICAO, INVALIDA, AGENDADA | 6 |
| RF54 | PlayEvent (jogada) | ALINHADA, EM_JOGO, BOLA_MORTA, FALTA_SINALIZADA, REGISTRADA | 6 |
| RF55 | UnitChemistry.level (OL, QB-WR) | NOVA, EM_FORMACAO, ENTROSADA, ELITE | 6 |
| RF55 | SnapPlan (edição) | VIGENTE, EDITANDO, INVALIDO, PENDENTE_INTERVALO | 6 |
| RF56 | PracticeSquadMember (vínculo) | NO_PRACTICE_SQUAD, PROTEGIDO, ELEVADO, PROMOVIDO, LEVADO_POR_OUTRO, DISPENSADO | 8 |
| RF57 | AllStarEvent.status | VOTING, ROSTERS_SET, SKILLS, GAME, DONE | 5 |
| RF58 | DigitalTwinLicense.status | DRAFT, ACTIVE, SUSPENDED, EXPIRED, REVOKED | 7 |
| RF59 | Mini campo (interação) | Fechado, Carregando, Animando, Pausado, Concluido, Erro | 9 |
| RF60 | DepthChart (validade) | VALIDO, EDITANDO, INVALIDO, TRAVADO | 8 |
| RF61 | GamePlan (semana) | PADRAO, EDITADO, FIXADO, AJUSTADO_INTERVALO, ARQUIVADO | 8 |
| RF62 | PlayCallDecision.status | OPEN, CHOSEN, AUTO, RESOLVED | 4 |
| RF62 | MatchSession.status (chamada) | LIVE, AWAITING_DECISION, BREAK, FINISHED | 5 |
| RF63 | Jogada de chute (PlayEvent de times especiais) | ALINHADA, SNAP, BLOQUEADO, NO_AR, RETORNO, BOLA_MORTA | 8 |
| RF64 | InjuryReport.status | SAUDAVEL, QUESTIONABLE, DOUBTFUL, OUT, IR | 9 |
| RF65 | WaiverClaim.status | PENDING, AWARDED, LOST | 2 |
| RF65 | FreeAgentOffer.status | PENDING, ACCEPTED, REJECTED, EXPIRED, WITHDRAWN | 4 |
| RF66 | Chave de playoffs (rodada) | PROJECAO, WILD_CARD, DIVISIONAL, CONFERENCE, FINAL, CAMPEAO | 6 |
| RF66 | PlayoffMatchup (confronto) | AGUARDANDO, AGENDADO, EM_JOGO, PRORROGACAO, DECIDIDO | 5 |
| RNF1 | Dados de uma carreira (mundo SAVE) | Copiando, Descartada, Salva, Gravando, Projetando | 7 |
| RNF2 | Reprodução da partida (orçamento de tempo) | Planejada, Simulando quarto, Reproduzindo, Decidindo jogada, Acelerando, Instantânea, Concluída | 10 |
| RNF3 | Requisição (decisão de acesso) | Recebida, Pública, Autenticada, Autorizada (perfil), Dono do recurso, 401, 403 + auditoria, 404, Executada | 11 |
| RNF4 | UserSession (família sid, refresh em cookie HttpOnly) | ACTIVE, ROTATED, REVOKED (família), EXPIRED | 5 |
| RNF5 | Dado sensível (ciclo de cifra) | Cifrado, Decifrado em memória, Recifrado, Segredo detectado | 6 |
| RNF6 | Job de backup e restore | Agendado, Dumpando, VERIFYING, Enviando ao S3, OK, FAILED, Restaurando, Reprojetando, Restaurado | 12 |
| RNF7 | AuditLog (pipeline de um registro) | Capturado, Sanitizado, Persistido, Falha de escrita, Consultável, Arquivado | 5 |
| RNF8 | UserAccount.status × pedidos LGPD | ACTIVE, Exportação pendente, Exportação pronta, PENDING_DELETION, DELETED (anonimizada) | 6 |
| RNF9 | Tela da partida (carregamento e degradação) | Skeleton, Carregando, Cena 3D, Cena 3D reduzida, Campo 2D, Reduced motion, Offline | 10 |
| RNF10 | PushSubscription.status | Pedindo permissão, Negada, ACTIVE, FAILING, REVOKED | 6 |
| RNF11 | Upload de mídia (MediaAsset.status) | Recebido, Validando, Escaneando, QUARANTINED, Processando, STORED, Vinculado, Rejeitado, DELETED | 10 |
| RNF12 | ScoresPointsWallet (escopo USER ou SAVE) | Zerada, Com saldo, Debitando, Divergente | 7 |
| RNF13 | Idioma da sessão | Detectando, Ativo, Trocando, Fallback | 5 |
