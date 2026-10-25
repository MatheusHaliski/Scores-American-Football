# Esquema do modelo de requisitos (fonte única dos diagramas)

Cada arquivo `model/*.py` exporta `REQUIREMENTS: list[dict]`. Um dict por RF/RNF, com as chaves abaixo.
Nomes de atores, stores e entidades DEVEM existir em `common.py` (`ACTORS`, `STORES`, `ENTITIES`); o gerador falha se não.

```python
{
  "id": "RF40", "kind": "RF", "sprint": 2,
  "title": "Central do clube",                       # curto
  "hu": "HU-RF40 — COMO … POSSO … PARA …",          # texto do card
  "actors": ["Manager", "Diretoria"],               # chaves de common.ACTORS
  "screen": "Central do clube (/c/{careerId})",
  "endpoints": [ {"method": "GET", "path": "/api/v1/careers/{id}/dashboard", "usecase": "GetClubDashboardUseCase",
                  "auth": "JWT · dono do save", "desc": "Painel completo"} ],
  "entities": ["CareerSave", "Club", "Fixture"],    # chaves de common.ENTITIES (usadas no diagrama de classes)
  "stores": ["MySQL", "Redis", "Cassandra"],        # chaves de common.STORES
  "gateways": [ {"name": "Dono do save?", "rule": "save.userId == jwt.sub", "onFalse": "403 + AuditLog ACCESS_DENIED"} ],
  # ---- atividade: lista de passos; lanes = actor. Passos: "start", "stop", "end", "texto", {"actor","do"},
  #      {"actor","if": "pergunta?", "yes": [...], "no": [...]}, {"actor","while": "cond?", "body": [...]},
  #      {"fork": [[...],[...]]}, {"note": "..."}, {"partition": "nome", "body": [...]}
  "activity": [ "start", {"actor": "Manager", "do": "Abre a central"}, {"actor": "Frontend", "do": "GET …"},
                {"actor": "Backend", "if": "dono do save?", "no": [{"actor": "Backend", "do": "403"}, "stop"]},
                {"actor": "Backend", "do": "…"}, {"actor": "Manager", "do": "vê painéis"}, "end" ],
  # ---- sequência: tuplas (de, para, mensagem[, "return"]) com nomes de common.PARTICIPANTS (Manager, Frontend, API,
  #      UseCase, AuditService, MoraleEngine, MatchSimulator, ClubAnalyst, SeasonEngine, MarketEngine, FinanceEngine,
  #      EventBus, MySQL, Redis, Cassandra, OpenSearch, S3, Claude, Email, Scheduler, Diretoria, ClubeIA, JogadorIA, Imprensa)
  #      e blocos {"alt": "cond", "then": [...], "else": [...]}, {"loop": "cond", "body": [...]}, {"par": "…", "body": [...]}, {"note": "…", "over": "UseCase"}
  "sequence": [ ("Manager", "Frontend", "abre central"), ("Frontend", "API", "GET /careers/{id}/dashboard"),
                ("API", "UseCase", "execute(query)"), ("UseCase", "MySQL", "save, club, fixtures, standings"),
                ("MySQL", "UseCase", "dados", "return"), ("UseCase", "API", "DashboardResult", "return"), ("API", "Frontend", "200 JSON", "return") ],
  # ---- componentes por camada (nomes livres) + wiring porta→adaptador + adapterStores
  "components": {"frontend": ["ClubCentralPage"], "web": ["CareerController"], "application": ["GetClubDashboardUseCase", "CareerAccess"],
                 "engine": ["ClubAnalyst"], "ports": ["CareerSaveRepository", "FixtureRepository", "TimelinePort", "CounterPort"],
                 "adapters": ["persistence-mysql", "persistence-cassandra", "cache-redis"],
                 "wiring": {"CareerSaveRepository": "persistence-mysql", "FixtureRepository": "persistence-mysql", "TimelinePort": "persistence-cassandra", "CounterPort": "cache-redis"},
                 "adapterStores": {"persistence-mysql": ["MySQL"], "persistence-cassandra": ["Cassandra"], "cache-redis": ["Redis"]}},
  # ---- classes: focus = entidades do catálogo; extra = classes não-entidade (casos de uso, portas, records) ; links = dependências (A ..> B)
  "classes": {"focus": ["CareerSave", "Club", "Fixture"],
              "extra": [{"name": "GetClubDashboardUseCase", "stereotype": "UseCase", "methods": ["execute(query): DashboardResult"]},
                        {"name": "CareerSaveRepository", "kind": "interface", "stereotype": "Port", "methods": ["findById(UUID)"]}],
              "links": [("GetClubDashboardUseCase", "CareerSaveRepository", "usa"), ("GetClubDashboardUseCase", "CareerSave", "lê")]},
  # ---- máquina de estados do sujeito principal do RF (entidade ou interação)
  "states": {"subject": "CareerSave.employmentStatus", "initial": "EMPLOYED",
             "states": [{"id": "EMPLOYED", "name": "EMPLOYED", "desc": ["clube definido", "contrato ativo"]}, "UNEMPLOYED", "SPECTATOR"],
             "transitions": [("EMPLOYED", "UNEMPLOYED", "demissão / pedido de demissão"), ("UNEMPLOYED", "EMPLOYED", "aceita proposta")],
             "final": []},
}
```

Regras de qualidade
- Atividade: sempre `start` … `end`/`stop`; todo gateway relevante do RF aparece como `if` **e** em `gateways`.
- Sequência: começa no ator, passa por Frontend → API → UseCase → (engines) → stores → retorno; mostre `alt` para erros (403/422) quando o RF tiver regra.
- Endpoints: os do mapa `docs/02-mapa-estrutura-basquete-af.md` §2; `auth` = "público" | "JWT" | "JWT · dono do save" | "JWT · ADMIN".
- Estados: use o ciclo de vida real da entidade quando existir (Fixture, TransferOffer, JobOffer, ManagerContract, InboxMessage, Season, MatchSession, Player.transferStatus, YouthProspect, BankLoan, Purchase, UserSession, UserAccount.status…); para RFs só de visualização, use a máquina da interação (Idle → Carregando → Carregado/Erro → Filtrando…).
- Classes: `focus` só com entidades do catálogo; casos de uso/portas/records em `extra`.
- Texto em PT-BR, curto; sem aspas duplas dentro de strings (use aspas simples).
