# Mapa da estrutura: Scores de basquete → Scores: American Football

Pedido do PO: replicar no futebol americano as mesmas telas, rotas, funções e arquivos de backend do basquete. Este mapa lista
tudo o que existe no basquete (levantado do código em 25/10/2026: 149 endpoints em 20 controllers, 16 rotas de front, ≈660
arquivos Java de produção, 21 migrations) e diz o equivalente no futebol americano e o bloco do plano (docs/01 §5) em que entra.
Mesmo número de RF = mesma função (D-2). Um PR por bloco, empilhados, com CI verde antes do próximo.

## 1. Telas e rotas do front

| Basquete | Futebol americano | RF | Bloco |
|---|---|---|---|
| `/` | `/ (início com campo 3D)` | — | 2 ✅ |
| `/gate` | `/gate (gate de dev com PIN)` | — | 3 |
| `/auth` | `/auth (cadastro, login, senha)` | RF1–RF2 | 3 |
| `/settings` | `/settings (conta, aparência, idioma, privacidade, dados)` | RF3, RF10, RF46 | 3 |
| `/careers` | `/careers (carreiras salvas)` | RF11 | 6 |
| `/careers/new` | `/careers/new (liga AF, franquia, estilo de head coach)` | RF34 | 6 |
| `/c/[careerId]` | `/c/[careerId] (central da franquia)` | RF40 | 6 |
| `/c/[careerId]/squad` | `/c/[careerId]/roster (53 + practice squad + IR, por unidade)` | RF4, RF56 | 6 |
| `/c/[careerId]/match` | `/c/[careerId]/match (ao vivo: descida e distância, drive, play-by-play)` | RF27, RF42, RF62 | 5/6 |
| `/c/[careerId]/match/[fixtureId]/report` | `/c/[careerId]/match/[fixtureId]/report (box score AF)` | RF27 | 6 |
| `/c/[careerId]/players/[playerId]` | `/c/[careerId]/players/[playerId] (modal/esquema com macros por família)` | RF22, RF47, RF48 | 9 |
| `/c/[careerId]/[section]` | `/c/[careerId]/[section] (seções abaixo)` | várias | 6–11 |
| `/me/player` | `/me/player (Meu jogador por foto, posição AF)` | RF44 | 9 |
| `/lab/assets, /lab/dd, /lab/identity` | `/lab/* (laboratórios 3D: atleta com capacete e ombreiras, identidade)` | RF23, RF28 | 8 |
| `/[placeholder]` | `/[placeholder] (telas ainda não prontas)` | — | 3 |

### Seções da central (`sections.ts`)

| Seção basquete | Seção AF | RF | Bloco |
|---|---|---|---|
| hub | hub | RF40 | 6 |
| squad | roster | RF4 | 6 |
| — | depth-chart | RF60 | 6 |
| — | playbook | RF61 | 6 |
| match | match | RF27 | 5/6 |
| calendar | calendar (semanas, bye, quinta à noite) | RF21/RF36 | 6/11 |
| standings | standings (divisão, conferência, desempate) | RF16 | 6 |
| — | playoffs (chave 7 por conferência) | RF66 | 6 |
| inbox | inbox | RF13/RF24 | 7 |
| market | market → free agency, waivers e trocas | RF19/RF65/RF50 | 7 |
| — | draft (scouting e combine) | RF51 | 7 |
| finance | finance + teto salarial | RF14/RF49 | 7/11 |
| — | injuries (relatório semanal) | RF64 | 11 |
| stadium | stadium | RF12/RF26 | 8/11 |
| manager | manager | RF8/RF9/RF37 | 7 |
| store | store | RF25/RF32/RF33 | 10/11 |
| trophies | trophies | RF17 | 11 |
| managers | managers | RF18 | 11 |
| search | search (posição e unidade) | RF20 | 11 |
| season | season (playoffs, draft, free agency) | RF35 | 6 |
| editor | editor (conferências e divisões, capacete) | RF5/RF28 | 4/8 |
| myPlayer | myPlayer | RF44 | 9 |
| settings | settings | RF10/RF46 | 3 |

## 2. Endpoints REST (controller a controller)

Rotas do futebol americano seguem as do basquete, trocando rodada por semana (`/rounds` → `/weeks`), táticas por plano de jogo (`/tactics` →
`/game-plan`) e quinteto por depth chart (`/lineup` → `/depth-chart`). Não há lista de transferência entre clubes: vira trade block.

| Controller basquete | Método | Rota basquete | Rota AF | Controller AF | Bloco |
|---|---|---|---|---|---|
| AccountController | `GET` | `/api/v1/me` | `/api/v1/me` | AccountController | 3 |
| AccountController | `PUT` | `/api/v1/me` | `/api/v1/me` | AccountController | 3 |
| AccountController | `PUT` | `/api/v1/me/email` | `/api/v1/me/email` | AccountController | 3 |
| AccountController | `PUT` | `/api/v1/me/password` | `/api/v1/me/password` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/sessions` | `/api/v1/me/sessions` | AccountController | 3 |
| AccountController | `DELETE` | `/api/v1/me/sessions/{id}` | `/api/v1/me/sessions/{id}` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/consents` | `/api/v1/me/consents` | AccountController | 3 |
| AccountController | `PUT` | `/api/v1/me/consents` | `/api/v1/me/consents` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/consents/history` | `/api/v1/me/consents/history` | AccountController | 3 |
| AccountController | `POST` | `/api/v1/me/export` | `/api/v1/me/export` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/export` | `/api/v1/me/export` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/export/jobs` | `/api/v1/me/export/jobs` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/export/{jobId}` | `/api/v1/me/export/{jobId}` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/export/{jobId}/download` | `/api/v1/me/export/{jobId}/download` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/activity` | `/api/v1/me/activity` | AccountController | 3 |
| AccountController | `DELETE` | `/api/v1/me` | `/api/v1/me` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/deletion` | `/api/v1/me/deletion` | AccountController | 3 |
| AccountController | `POST` | `/api/v1/me/deletion/cancel` | `/api/v1/me/deletion/cancel` | AccountController | 3 |
| AccountController | `GET` | `/api/v1/me/settings` | `/api/v1/me/settings` | AccountController | 3 |
| AccountController | `PUT` | `/api/v1/me/settings` | `/api/v1/me/settings` | AccountController | 3 |
| AuthController | `POST` | `/api/v1/auth/register` | `/api/v1/auth/register` | AuthController | 3 |
| AuthController | `POST` | `/api/v1/auth/login` | `/api/v1/auth/login` | AuthController | 3 |
| AuthController | `POST` | `/api/v1/auth/refresh` | `/api/v1/auth/refresh` | AuthController | 3 |
| AuthController | `POST` | `/api/v1/auth/logout` | `/api/v1/auth/logout` | AuthController | 3 |
| AuthController | `POST` | `/api/v1/auth/password/forgot` | `/api/v1/auth/password/forgot` | AuthController | 3 |
| AuthController | `POST` | `/api/v1/auth/password/reset` | `/api/v1/auth/password/reset` | AuthController | 3 |
| EmailVerificationController | `POST` | `/api/v1/auth/email/verify` | `/api/v1/auth/email/verify` | EmailVerificationController | 3 |
| AnimationAdminController | `GET` | `/api/v1/admin/animations` | `/api/v1/admin/animations` | AnimationAdminController | 11 |
| AnimationAdminController | `GET` | `/api/v1/admin/animations/coverage` | `/api/v1/admin/animations/coverage` | AnimationAdminController | 11 |
| AnimationAdminController | `GET` | `/api/v1/admin/animations/{id}` | `/api/v1/admin/animations/{id}` | AnimationAdminController | 11 |
| AnimationAdminController | `POST` | `/api/v1/admin/animations/generate` | `/api/v1/admin/animations/generate` | AnimationAdminController | 11 |
| AnimationAdminController | `POST` | `/api/v1/admin/animations` | `/api/v1/admin/animations` | AnimationAdminController | 11 |
| AnimationAdminController | `PUT` | `/api/v1/admin/animations/{id}` | `/api/v1/admin/animations/{id}` | AnimationAdminController | 11 |
| AnimationAdminController | `POST` | `/api/v1/admin/animations/{id}/publish` | `/api/v1/admin/animations/{id}/publish` | AnimationAdminController | 11 |
| AnimationAdminController | `POST` | `/api/v1/admin/animations/{id}/archive` | `/api/v1/admin/animations/{id}/archive` | AnimationAdminController | 11 |
| AnimationAdminController | `GET` | `/api/v1/admin/animations/{id}/diff/{otherId}` | `/api/v1/admin/animations/{id}/diff/{otherId}` | AnimationAdminController | 11 |
| AnalysisController | `GET` | `/api/v1/careers/{id}/analysis` | `/api/v1/careers/{id}/analysis` | AnalysisController | 6 |
| AnalysisController | `POST` | `/api/v1/careers/{id}/analysis/refresh` | `/api/v1/careers/{id}/analysis/refresh` | AnalysisController | 6 |
| CareerController | `GET` | `/api/v1/careers` | `/api/v1/careers` | CareerController | 6 |
| CareerController | `POST` | `/api/v1/careers` | `/api/v1/careers` | CareerController | 6 |
| CareerController | `GET` | `/api/v1/careers/{id}` | `/api/v1/careers/{id}` | CareerController | 6 |
| CareerController | `PATCH` | `/api/v1/careers/{id}` | `/api/v1/careers/{id}` | CareerController | 6 |
| CareerController | `DELETE` | `/api/v1/careers/{id}` | `/api/v1/careers/{id}` | CareerController | 6 |
| CareerController | `GET` | `/api/v1/careers/{id}/dashboard` | `/api/v1/careers/{id}/dashboard` | CareerController | 6 |
| CareerController | `GET` | `/api/v1/careers/{id}/morale` | `/api/v1/careers/{id}/morale` | CareerController | 6 |
| InboxController | `GET` | `/api/v1/careers/{id}/inbox` | `/api/v1/careers/{id}/inbox` | InboxController | 7 |
| InboxController | `POST` | `/api/v1/careers/{id}/inbox/{mid}/read` | `/api/v1/careers/{id}/inbox/{mid}/read` | InboxController | 7 |
| InboxController | `POST` | `/api/v1/careers/{id}/inbox/{mid}/respond` | `/api/v1/careers/{id}/inbox/{mid}/respond` | InboxController | 7 |
| OfferController | `GET` | `/api/v1/careers/{id}/offers` | `/api/v1/careers/{id}/offers` | OfferController | 7 |
| OfferController | `POST` | `/api/v1/careers/{id}/offers/{oid}/respond` | `/api/v1/careers/{id}/offers/{oid}/respond` | OfferController | 7 |
| AnimationCatalogController | `GET` | `/api/v1/catalog/animations` | `/api/v1/catalog/animations` | AnimationCatalogController | 11 |
| ClubIdentityController | `GET` | `/api/v1/clubs/{id}/identity` | `/api/v1/clubs/{id}/identity` | ClubIdentityController (capacete + uniformes) | 8 |
| ClubIdentityController | `PUT` | `/api/v1/clubs/{id}/identity` | `/api/v1/clubs/{id}/identity` | ClubIdentityController (capacete + uniformes) | 8 |
| ClubIdentityController | `POST` | `/api/v1/clubs/{id}/identity/preview` | `/api/v1/clubs/{id}/identity/preview` | ClubIdentityController (capacete + uniformes) | 8 |
| ClubIdentityController | `PUT` | `/api/v1/clubs/{id}/uniforms/{slot}` | `/api/v1/clubs/{id}/uniforms/{slot}` | ClubIdentityController (capacete + uniformes) | 8 |
| WorldEditorController | `GET` | `/api/v1/worlds` | `/api/v1/worlds` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `GET` | `/api/v1/worlds/{id}` | `/api/v1/worlds/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `POST` | `/api/v1/worlds/{id}/clone` | `/api/v1/worlds/{id}/clone` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `POST` | `/api/v1/worlds` | `/api/v1/worlds` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `PUT` | `/api/v1/worlds/{id}` | `/api/v1/worlds/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `DELETE` | `/api/v1/worlds/{id}` | `/api/v1/worlds/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `GET` | `/api/v1/worlds/{id}/leagues` | `/api/v1/worlds/{id}/leagues` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `POST` | `/api/v1/worlds/{id}/leagues` | `/api/v1/worlds/{id}/leagues` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `PUT` | `/api/v1/leagues/{id}` | `/api/v1/leagues/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `DELETE` | `/api/v1/leagues/{id}` | `/api/v1/leagues/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `GET` | `/api/v1/leagues/{id}/clubs` | `/api/v1/leagues/{id}/clubs` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `POST` | `/api/v1/leagues/{id}/clubs` | `/api/v1/leagues/{id}/clubs` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `GET` | `/api/v1/clubs/{id}` | `/api/v1/clubs/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `PUT` | `/api/v1/clubs/{id}` | `/api/v1/clubs/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `DELETE` | `/api/v1/clubs/{id}` | `/api/v1/clubs/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `GET` | `/api/v1/clubs/{id}/stadium` | `/api/v1/clubs/{id}/stadium` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `PUT` | `/api/v1/clubs/{id}/stadium` | `/api/v1/clubs/{id}/stadium` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `GET` | `/api/v1/clubs/{id}/players` | `/api/v1/clubs/{id}/players` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `POST` | `/api/v1/clubs/{id}/players` | `/api/v1/clubs/{id}/players` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `GET` | `/api/v1/players/{id}` | `/api/v1/players/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `PUT` | `/api/v1/players/{id}` | `/api/v1/players/{id}` | WorldEditorController (conferências e divisões) | 4 |
| WorldEditorController | `DELETE` | `/api/v1/players/{id}` | `/api/v1/players/{id}` | WorldEditorController (conferências e divisões) | 4 |
| MatchController | `POST` | `/api/v1/careers/{id}/rounds/current/play` | `/api/v1/careers/{id}/weeks/current/play` | MatchController (motor por descida) | 5/6 |
| MatchController | `GET` | `/api/v1/careers/{id}/rounds/{n}` | `/api/v1/careers/{id}/weeks/{n}` | MatchController (motor por descida) | 5/6 |
| MatchController | `GET` | `/api/v1/matches/{mid}` | `/api/v1/matches/{mid}` | MatchController (motor por descida) | 5/6 |
| MatchController | `POST` | `/api/v1/matches/{mid}/advance` | `/api/v1/matches/{mid}/advance` | MatchController (motor por descida) | 5/6 |
| MatchController | `POST` | `/api/v1/matches/{mid}/decisions` | `/api/v1/matches/{mid}/decisions` | MatchController (motor por descida) | 5/6 |
| MatchController | `GET` | `/api/v1/matches/{mid}/events` | `/api/v1/matches/{mid}/events` | MatchController (motor por descida) | 5/6 |
| MatchController | `GET` | `/api/v1/matches/{mid}/report` | `/api/v1/matches/{mid}/report` | MatchController (motor por descida) | 5/6 |
| MediaLinkController | `GET` | `/api/v1/media-links/{token}` | `/api/v1/media-links/{token}` | MediaLinkController | 9 |
| UltimateTeamController | `GET` | `/api/v1/ultimate/squad` | `/api/v1/ultimate/squad` | UltimateTeamController | 10 |
| UltimateTeamController | `PUT` | `/api/v1/ultimate/squad` | `/api/v1/ultimate/squad` | UltimateTeamController | 10 |
| UltimateTeamController | `GET` | `/api/v1/ultimate/squad/cards` | `/api/v1/ultimate/squad/cards` | UltimateTeamController | 10 |
| UltimateTeamController | `PUT` | `/api/v1/ultimate/squad/lineup` | `/api/v1/ultimate/squad/depth-chart` | UltimateTeamController | 10 |
| UltimateTeamController | `GET` | `/api/v1/ultimate/calendar` | `/api/v1/ultimate/calendar` | UltimateTeamController | 10 |
| UltimateTeamController | `POST` | `/api/v1/ultimate/divisions/enter` | `/api/v1/ultimate/divisions/enter` | UltimateTeamController | 10 |
| UltimateTeamController | `POST` | `/api/v1/ultimate/divisions/runs/{id}/withdraw` | `/api/v1/ultimate/divisions/runs/{id}/withdraw` | UltimateTeamController | 10 |
| UltimateTeamController | `GET` | `/api/v1/ultimate/divisions/runs/{id}` | `/api/v1/ultimate/divisions/runs/{id}` | UltimateTeamController | 10 |
| UltimateTeamController | `GET` | `/api/v1/ultimate/divisions/table` | `/api/v1/ultimate/divisions/table` | UltimateTeamController | 10 |
| UltimateTeamController | `POST` | `/api/v1/ultimate/cup/enter` | `/api/v1/ultimate/cup/enter` | UltimateTeamController | 10 |
| UltimateTeamController | `GET` | `/api/v1/ultimate/matches/{id}` | `/api/v1/ultimate/matches/{id}` | UltimateTeamController | 10 |
| UltimateTeamController | `POST` | `/api/v1/ultimate/matches/{id}/accept` | `/api/v1/ultimate/matches/{id}/accept` | UltimateTeamController | 10 |
| UltimateTeamController | `POST` | `/api/v1/ultimate/matches/{id}/heartbeat` | `/api/v1/ultimate/matches/{id}/heartbeat` | UltimateTeamController | 10 |
| UltimateTeamController | `POST` | `/api/v1/ultimate/matches/{id}/quit` | `/api/v1/ultimate/matches/{id}/quit` | UltimateTeamController | 10 |
| UltimateTeamController | `GET` | `/api/v1/ultimate/sanctions` | `/api/v1/ultimate/sanctions` | UltimateTeamController | 10 |
| UltimateTeamController | `POST` | `/api/v1/ultimate/sanctions/{id}/appeal` | `/api/v1/ultimate/sanctions/{id}/appeal` | UltimateTeamController | 10 |
| UltimateTeamController | `GET` | `/api/v1/ultimate/attempts` | `/api/v1/ultimate/attempts` | UltimateTeamController | 10 |
| CustomPlayerController | `GET` | `/api/v1/me/player` | `/api/v1/me/player` | CustomPlayerController | 9 |
| CustomPlayerController | `PUT` | `/api/v1/me/player` | `/api/v1/me/player` | CustomPlayerController | 9 |
| CustomPlayerController | `PUT` | `/api/v1/me/player/avatar` | `/api/v1/me/player/avatar` | CustomPlayerController | 9 |
| CustomPlayerController | `POST` | `/api/v1/me/player/photo` | `/api/v1/me/player/photo` | CustomPlayerController | 9 |
| CustomPlayerController | `POST` | `/api/v1/me/player/texture` | `/api/v1/me/player/texture` | CustomPlayerController | 9 |
| CustomPlayerController | `POST` | `/api/v1/me/player/model` | `/api/v1/me/player/model` | CustomPlayerController | 9 |
| CustomPlayerController | `POST` | `/api/v1/me/player/confirm` | `/api/v1/me/player/confirm` | CustomPlayerController | 9 |
| CustomPlayerController | `DELETE` | `/api/v1/me/player/avatar` | `/api/v1/me/player/avatar` | CustomPlayerController | 9 |
| CustomPlayerController | `DELETE` | `/api/v1/me/player` | `/api/v1/me/player` | CustomPlayerController | 9 |
| CustomPlayerController | `GET` | `/api/v1/me/player/3d` | `/api/v1/me/player/3d` | CustomPlayerController | 9 |
| CustomPlayerController | `GET` | `/api/v1/me/player/{kind:photo|texture|model}` | `/api/v1/me/player/{kind:photo|texture|model}` | CustomPlayerController | 9 |
| CustomPlayerController | `GET` | `/api/v1/me/player/rules` | `/api/v1/me/player/rules` | CustomPlayerController | 9 |
| DigitalDoubleController | `GET` | `/api/v1/me/player/double` | `/api/v1/me/player/double` | DigitalDoubleController | 9 |
| DigitalDoubleController | `GET` | `/api/v1/me/player/double/revisions` | `/api/v1/me/player/double/revisions` | DigitalDoubleController | 9 |
| DigitalDoubleController | `PUT` | `/api/v1/me/player/double` | `/api/v1/me/player/double` | DigitalDoubleController | 9 |
| DigitalDoubleController | `POST` | `/api/v1/me/player/double/approve` | `/api/v1/me/player/double/approve` | DigitalDoubleController | 9 |
| DigitalDoubleController | `GET` | `/api/v1/me/player/double/references` | `/api/v1/me/player/double/references` | DigitalDoubleController | 9 |
| DigitalDoubleController | `POST` | `/api/v1/me/player/double/references` | `/api/v1/me/player/double/references` | DigitalDoubleController | 9 |
| DigitalDoubleController | `DELETE` | `/api/v1/me/player/double/references/{id}` | `/api/v1/me/player/double/references/{id}` | DigitalDoubleController | 9 |
| DigitalDoubleController | `POST` | `/api/v1/me/player/double/references/{id}/link` | `/api/v1/me/player/double/references/{id}/link` | DigitalDoubleController | 9 |
| SeasonController | `GET` | `/api/v1/careers/{id}/standings` | `/api/v1/careers/{id}/standings` | SeasonController | 6 |
| SeasonController | `GET` | `/api/v1/careers/{id}/calendar` | `/api/v1/careers/{id}/calendar` | SeasonController | 6 |
| SeasonController | `GET` | `/api/v1/careers/{id}/rounds/{n}/news` | `/api/v1/careers/{id}/weeks/{n}/news` | SeasonController | 6 |
| SeasonController | `GET` | `/api/v1/careers/{id}/playoffs` | `/api/v1/careers/{id}/playoffs` | SeasonController | 6 |
| SeasonController | `GET` | `/api/v1/careers/{id}/trophies` | `/api/v1/careers/{id}/trophies` | SeasonController | 6 |
| SeasonController | `GET` | `/api/v1/careers/{id}/season/summary` | `/api/v1/careers/{id}/season/summary` | SeasonController | 6 |
| SeasonController | `POST` | `/api/v1/careers/{id}/season/advance` | `/api/v1/careers/{id}/season/advance` | SeasonController | 6 |
| SimulationController | `POST` | `/api/v1/careers/{id}/simulate` | `/api/v1/careers/{id}/simulate` | SimulationController | 11 |
| SimulationController | `GET` | `/api/v1/careers/{id}/simulate/active` | `/api/v1/careers/{id}/simulate/active` | SimulationController | 11 |
| SimulationController | `GET` | `/api/v1/careers/{id}/simulate/{runId}` | `/api/v1/careers/{id}/simulate/{runId}` | SimulationController | 11 |
| SimulationController | `POST` | `/api/v1/careers/{id}/simulate/{runId}/stop` | `/api/v1/careers/{id}/simulate/{runId}/stop` | SimulationController | 11 |
| SimulationController | `POST` | `/api/v1/careers/{id}/simulate/{runId}/resume` | `/api/v1/careers/{id}/simulate/{runId}/resume` | SimulationController | 11 |
| SquadController | `GET` | `/api/v1/careers/{id}/squad` | `/api/v1/careers/{id}/roster` | RosterController | 6 |
| SquadController | `GET` | `/api/v1/careers/{id}/players/{pid}` | `/api/v1/careers/{id}/players/{pid}` | RosterController | 6 |
| SquadController | `PATCH` | `/api/v1/careers/{id}/players/{pid}/role` | `/api/v1/careers/{id}/players/{pid}/role` | RosterController | 6 |
| SquadController | `POST` | `/api/v1/careers/{id}/players/{pid}/list` | `/api/v1/careers/{id}/players/{pid}/trade-block` | RosterController | 6 |
| SquadController | `POST` | `/api/v1/careers/{id}/players/{pid}/unlist` | `/api/v1/careers/{id}/players/{pid}/trade-block/remove` | RosterController | 6 |
| SquadController | `POST` | `/api/v1/careers/{id}/players/{pid}/negotiate-salary` | `/api/v1/careers/{id}/players/{pid}/restructure` | RosterController | 6 |
| SquadController | `POST` | `/api/v1/careers/{id}/players/{pid}/renew` | `/api/v1/careers/{id}/players/{pid}/renew` | RosterController | 6 |
| SquadController | `POST` | `/api/v1/careers/{id}/players/{pid}/release` | `/api/v1/careers/{id}/players/{pid}/release` | RosterController | 6 |
| SquadController | `POST` | `/api/v1/careers/{id}/players/{pid}/consumables` | `/api/v1/careers/{id}/players/{pid}/consumables` | RosterController | 6 |
| SquadController | `POST` | `/api/v1/careers/{id}/players/{pid}/message` | `/api/v1/careers/{id}/players/{pid}/message` | RosterController | 6 |
| TacticsController | `GET` | `/api/v1/careers/{id}/tactics` | `/api/v1/careers/{id}/game-plan` | DepthChartController + GamePlanController | 6 |
| TacticsController | `PUT` | `/api/v1/careers/{id}/tactics` | `/api/v1/careers/{id}/game-plan` | DepthChartController + GamePlanController | 6 |
| TacticsController | `GET` | `/api/v1/careers/{id}/lineup` | `/api/v1/careers/{id}/depth-chart` | DepthChartController + GamePlanController | 6 |
| TacticsController | `PUT` | `/api/v1/careers/{id}/lineup` | `/api/v1/careers/{id}/depth-chart` | DepthChartController + GamePlanController | 6 |
| TacticsController | `GET` | `/api/v1/careers/{id}/next-match/preparation` | `/api/v1/careers/{id}/next-match/preparation` | DepthChartController + GamePlanController | 6 |

Situação do bloco 6 (6a, 6b e 6c): todas as rotas de bloco 6 desta tabela existem, menos as ações de elenco `trade-block`,
`trade-block/remove`, `restructure`, `renew` e `message`, que vão para o bloco 7 (contratos e mercado), e `consumables`, que vai para o
bloco 10 (loja) (D-107). No 6c, `GET /careers/{id}/morale` traz a moral do elenco e dos jogadores (no basquete, a do manager; Q-43) e
o analista é só por regras, sem provedor externo (D-104).

### Endpoints novos, só do futebol americano

| Método | Rota | RF | Bloco |
|---|---|---|---|
| `GET/PUT` | `/api/v1/careers/{id}/depth-chart` | RF60 | 6 |
| `GET/PUT` | `/api/v1/careers/{id}/playbook` | RF61 | 6 |
| `POST` | `/api/v1/matches/{mid}/play-calls/{decisionId}` | RF62 | 6 |
| `GET/PUT` | `/api/v1/careers/{id}/special-teams` | RF63 | 11 |
| `GET` | `/api/v1/careers/{id}/injury-report` | RF64 | 11 |
| `GET/POST` | `/api/v1/careers/{id}/free-agency` | RF65 | 7 |
| `GET/POST` | `/api/v1/careers/{id}/waivers` | RF65 | 7 |
| `GET` | `/api/v1/careers/{id}/playoffs/bracket` | RF66 | 6 |
| `GET/POST` | `/api/v1/careers/{id}/draft` | RF51 | 7 |
| `GET` | `/api/v1/careers/{id}/cap` | RF49 | 7 |
| `GET/POST` | `/api/v1/careers/{id}/trades` | RF50 | 7 |
| `GET/PUT` | `/api/v1/clubs/{id}/helmet` | RF28 | 8 |

## 3. Backend: módulos e pacotes

| Basquete | Futebol americano | Bloco | Observação |
|---|---|---|---|
| scores-domain/account, common | scores-af-domain/account, common | 3 | sem o avatar (RF44 vai no bloco 9) |
| scores-domain/catalog (+identity) | scores-af-domain/catalog | 4 (identity no 8) | LeagueRules AF, Conference, Division, posições, ≈40 subatributos |
| scores-domain/shared | scores-af-domain/shared | 4 | enums do futebol americano (Position, Unit, Formation, Coverage, PenaltyCode…) |
| scores-domain/engine/ratings | scores-af-domain/engine/ratings | 4 | overall por posição, vetor de ouro Java ↔ TS |
| scores-domain/engine/sim + model | scores-af-domain/engine/sim + model | 5 | motor novo por descida (não é cópia) |
| scores-domain/engine/morale | scores-af-domain/engine/morale | 6 | fatores: snaps por posição, contrato, vitórias |
| scores-domain/engine/analyst | scores-af-domain/engine/analyst | 6 | relatório por unidade e confrontos-chave |
| scores-domain/career, season, match, events, analysis | scores-af-domain/career, season, match, events, analysis | 6 | semanas, divisões, playoffs, drives |
| scores-domain/market, finance, inbox | scores-af-domain/market, finance, inbox | 7 | free agency, waivers, draft, teto salarial |
| scores-domain/card | scores-af-domain/card | 9 | cards com 6 macros por família (D-6) |
| scores-domain/online, store | scores-af-domain/online, store | 10 | Ultimate Team com depth chart |
| scores-application/audit, common, security, usecase/rf01–03, rf10, rf46 | scores-af-application (mesmos pacotes) | 3 |  |
| scores-application/port/out/* | scores-af-application/port/out/* | 3 em diante | cada porta entra com o primeiro caso de uso que a usa |
| scores-application/usecase/rf05, seed | scores-af-application/usecase/rf05, seed | 4 | mundo semente fictício de 32 times |
| scores-application/usecase/rf42, service/round | scores-af-application/usecase/rf42, service/week | 5 |  |
| scores-application/usecase/rf04, 06, 11, 16, 21, 22, 34, 35, 40, 41 | scores-af-application (mesmos números) + rf60, rf61, rf62, rf66 | 6 |  |
| scores-application/usecase/rf19, rf24, rf37 | scores-af-application/usecase/rf19→rf65, rf24, rf37, rf49, rf50, rf51 | 7 |  |
| scores-application/usecase/rf28, rf38 | scores-af-application/usecase/rf28 (8), rf38 (11) | 8/11 |  |
| scores-application/usecase/rf44 | scores-af-application/usecase/rf44 | 9 |  |
| scores-application/usecase/rf45, rf25 | scores-af-application/usecase/rf45 (10), rf25 (11) | 10/11 |  |
| scores-application/usecase/rf36, service/simulation | scores-af-application/usecase/rf36 | 11 | paradas em draft, free agency e prazo de trocas |
| scores-web/config, error, security, support, controller/account | scores-af-web (mesmos pacotes) | 2–3 | o esqueleto já tem error, support, config, security |
| scores-web/controller/* | scores-af-web/controller/* (tabela da §2) | 4–11 |  |
| scores-infrastructure/persistence-mysql | scores-af-infrastructure/persistence-mysql | 3 | Flyway V1 só com a plataforma; o jogo ganha migrations próprias no bloco 4 (D-8) |
| scores-infrastructure/persistence-cassandra | scores-af-infrastructure/persistence-cassandra | 5/6 | play-by-play (PlayEvent), inbox e notícias |
| scores-infrastructure/cache-redis | scores-af-infrastructure/cache-redis | 5 | estado vivo da partida |
| scores-infrastructure/search-opensearch | scores-af-infrastructure/search-opensearch | 11 | busca RF20 |
| scores-infrastructure/storage-s3 | scores-af-infrastructure/storage-s3 | 8/9 | capacetes, uniformes, GLB, fotos do RF44 |
| scores-infrastructure/ai-providers | scores-af-infrastructure/ai-providers | 6/11 | narrativa e animações por IA |

## 4. Front: pastas de componentes e lib

| Basquete | Futebol americano | Bloco |
|---|---|---|
| components/ui, shell, settings | iguais (marca AF) | 3 |
| components/backgrounds, brand | artes e marca do futebol americano | 3/8 |
| components/tactics (LineupBoard, TacticsForm, PrepareTeamModal) | components/depth-chart, game-plan, PrepareTeamModal (confrontos WR×CB, OL×DL) | 6 |
| components/match (Scoreboard, EventFeed, BoxScoreTable, QuarterBreakPanel, MiniCourt2D, TacticalCourt) | components/match (placar com descida e distância, drive, box score AF, painel do intervalo = playbook, MiniField2D, TacticalField) | 5/6/8 |
| components/squad | components/roster (practice squad, IR, waivers) | 6/7 |
| components/gauges | components/gauges (rating do QB, jardas/tentativa, pressões) | 9 |
| components/player-card | components/player-card (6 macros por família, níveis Bronze→Elite) | 9 |
| components/schemes | components/schemes (franquia, liga com conferências, jogador) | 9 |
| components/three (Arena, CourtFloor, Crowd, PlayerFigure, MiniCourt3D, LedPanelScene) | components/three (Stadium, FieldScene ✅ bloco 2, Crowd, PlayerFigure com capacete e ombreiras, MiniField3D, painéis LED) | 8 |
| components/my-player | components/my-player | 9 |
| lib/api, lib/devgate, store/session, store/prefs | iguais | 3 |
| lib/match, store/match | lib/match (estado ao vivo por descida) | 5/6 |
| lib/player-card, lib/avatar, lib/club, lib/color, lib/brand | equivalentes AF | 8/9 |
| lib/field ✅ | geometria oficial do campo (bloco 2) | 2 |

## 5. Banco de dados

O basquete tem um `V1__baseline.sql` grande e mais 20 migrations. No futebol americano, que ainda não tem nada aplicado em produção, as
migrations nascem por bloco: `V1__plataforma.sql` (bloco 3), `V2__dominio_af.sql` + `V3__mundo_semente.sql` (bloco 4) e uma por bloco
seguinte. Uma migration aplicada nunca é editada.

