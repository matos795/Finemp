# HANDOFF_EXECUCAO — MVP contábil FINEMP

Estado operacional da execução. Curto: o detalhe está nas referências e nas Issues.
Atualizar a cada merge.

Referências (cópias fiéis, não editar o conteúdo):
[HANDOFF_CONTABIL](reference/HANDOFF_CONTABIL.md) (produto/protótipo) ·
[GAP_CONTABIL](reference/GAP_CONTABIL.md) (auditoria da `main` `2c18461`, backlog M1–M7 e C1–C14, pontos O1–O21) ·
[accounting-area-map](reference/accounting-area-map.md) (mapa tela → rota → API do shell contábil) ·
[FRONTEND_CONTABIL_REFERENCE](reference/FRONTEND_CONTABIL_REFERENCE.md) (referência visual oficial do protótipo; o pacote com HTML/JS/capturas contém dados reais e **não** é versionado).

## Arquitetura (fixa)

- Login único. Depois dele: Área da Empresa ou Área da Contabilidade. Sem cadastro duplicado.
- Hierarquia `AccountingFirm → Client → Company → AccountingSource`.
- Administração / Onboarding SaaS é transversal à plataforma; não pertence a nenhuma das áreas.
- Questor/Firebird é **somente leitura**. O FINEMP mantém cópia sincronizada em PostgreSQL (SyncRun + Connector).
- Regras contábeis autoritativas ficam no backend; o React só apresenta.
- Selecionar uma Company na navegação **não** concede acesso aos dados dela: dados seguem `CompanyPermission` (`/api/auth/me`).
- Layout do módulo contábil = referência congelada do frontend João/Claude. Tela sem API oficial fica visível com "Em breve" e sem dado; nada de mock como dado real.

## Decisões aprovadas

- M1 (Issue #435): usuário comum vê só Companies com grant e seus ancestrais; `HIERARCHY_VIEW` lê toda a hierarquia da contabilidade sem mutação; `HIERARCHY_MANAGE` administra e satisfaz leitura; `ACCOUNTING_SOURCE_VIEW` lê fontes; `ACCOUNTING_SOURCE_MANAGE` administra e satisfaz leitura; SYSTEM_ADMIN global sem grant de contabilidade; carteira via `GET /api/auth/accounting-portfolio`, identidade só pela sessão.
- Ordem técnica: M1 → shell/M2 → M3 → M4 → M5 → M6 → M7; depois C1 → C2 → C3 → C4.
- Pontos O1–O21 do GAP continuam **DECISÃO DE PRODUTO NECESSÁRIA**, salvo o recorte de O1 decidido no M1.

## Em andamento

| Item | Issue | Branch | PR | CI |
| --- | --- | --- | --- | --- |
| Integração M3–M6 nas telas (De-Para, Composição, fontes/sincronização, DRE do pré-fechamento) | #454 | `feat/m2-depara-imported-accounts` | ver Issue | aguardando |

## Concluído (merge com CI verde)

| Item | Issue | PR | Entrega |
| --- | --- | --- | --- |
| M1 carteira e permissões de leitura | #435 | #438 | `GET /api/auth/accounting-portfolio`; `HIERARCHY_VIEW`, `ACCOUNTING_SOURCE_VIEW` |
| Bootstrap de contexto | — | #439 | `docs/reference/*`, este arquivo |
| M3 estado de sincronização | #440 | #441 | `GET /api/companies/{id}/accounting-sources` (QUESTOR_SYNC) |
| M4 plano importado paginado | #442 | #443 | `GET /api/companies/{id}/accounting-accounts` (`universe: IMPORTED_ACCOUNTS`) |
| M5 movimentos paginados | #444 | #445 | `GET .../accounting-movements/search` (valores exatos, totais, `notSynchronized`) |
| M6 DRE agregada no backend | #446 | #447 | `groups`, `classifiedTotal`, `coverage`; ordem determinística |
| M7 aceite vertical (backend) | #448 | #450 | login → carteira → fonte → SyncRun real → contas → movimentos → DRE → logout |
| DRE sem 500 (mitigação de #449) | #449 (aberta) | #451 | período invertido → 400; valor negativo → 422 `DRE_UNSUPPORTED_NEGATIVE_AMOUNT` |
| M2 Área da Contabilidade: shell fiel ao protótipo | #452 | #453 | CSS do protótipo escopado (`.ctbp`), menu/cabeçalho/telas do protótipo, carteira da sessão, cache por usuário, impressão por aba |

Todas as leituras novas são aditivas: telas existentes da Área da Empresa não mudaram.
Revisões automáticas (Codex) atendidas: snapshot único (`REPEATABLE_READ`) para página+totais; valores com sinal.

## Bloqueios

- **C1 (evidência da origem):** exige amostra sanitizada ou esquema de uma instalação Questor real (saldos, documento, encerramento, contrapartida, CC). Não há credencial nesta sessão; nomes de campos não serão inventados.
- **Valor negativo na DRE (#449):** canônico preserva negativos; a DRE responde 422 explícito (#451). **DECISÃO DE PRODUTO NECESSÁRIA** sobre a semântica.
- **Homologação da DRE (O21):** falta amostra aprovada para validar sinal/linhas/totais; ordem oficial (O2).
- Demais telas da Central/Pacote: dependem de C1/C2 e de O1–O21.

## Ambiente de sessão (não versionar)

- Iniciar `dockerd` se o daemon não estiver ativo.
- Testcontainers: `~/.testcontainers.properties` com `hub.image.name.prefix=mirror.gcr.io/` (Docker Hub anônimo responde 429).
- `backend/mvnw` está sem bit executável no git: usar `sh ./mvnw`.
- Smoke tests Questor/Firebird real exigem `FINEMP_QUESTOR_SMOKE_TEST=true` e credencial real; pulados no gate local e na CI, não contam como executados.

## Próxima tarefa

1. Merge da integração #454 com CI verde.
2. Com amostra/esquema da origem Questor: C1 → C2 → C3 → C4 (saldos, documento, encerramento, contrapartida, CC, plano completo). Desbloqueia Diagnóstico, De-Para completo, balancete e drilldown por contrapartida.
3. Com decisão em #449: semântica de valor negativo na DRE.
4. Com decisões O1–O21 do GAP: preparados, lotes Questor, congelamento, fiscal, folha, pacote.
