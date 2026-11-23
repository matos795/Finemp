# HANDOFF_EXECUCAO — MVP contábil FINEMP

Estado operacional da execução. Curto: o detalhe está nas referências e nas Issues.
Atualizar a cada merge.

Referências (cópias fiéis, não editar o conteúdo):
[HANDOFF_CONTABIL](reference/HANDOFF_CONTABIL.md) (produto/protótipo) ·
[GAP_CONTABIL](reference/GAP_CONTABIL.md) (auditoria da `main` `2c18461`, backlog M1–M7 e C1–C14, pontos O1–O21) ·
[accounting-area-map](reference/accounting-area-map.md) (mapa tela → rota → API do shell contábil).

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
| M1 carteira e permissões de leitura | #435 | `feat/accounting-portfolio-access` | #438 | aguardando |

## Concluído

- Validação do ambiente da sessão (Java 21, Maven, Docker, Testcontainers via `mirror.gcr.io`, `verify` da `main` verde).
- Bootstrap de contexto: referências em `docs/reference/` e este arquivo.

## Bloqueios

- **Shell contábil (M2):** o patch `0001-feat-frontend-accounting-area-shell.patch` (referência visual do frontend João/Claude) não foi anexado à sessão e não existe em branch/PR do repositório. Sem ele não se reproduz o layout; não será inventado outro. Necessário: anexar o patch e, se houver, o HTML do protótipo.
- Telas da Central/Pacote: dependem de C1/C2 e das decisões O1–O21 do GAP.

## Ambiente de sessão (não versionar)

- Iniciar `dockerd` se o daemon não estiver ativo.
- Testcontainers: `~/.testcontainers.properties` com `hub.image.name.prefix=mirror.gcr.io/` (Docker Hub anônimo responde 429).
- `backend/mvnw` está sem bit executável no git: usar `sh ./mvnw`.
- Smoke tests Questor/Firebird real exigem `FINEMP_QUESTOR_SMOKE_TEST=true` e credencial real; pulados no gate local e na CI, não contam como executados.

## Próxima tarefa

Merge do M1 com CI verde. Em seguida, sem depender do shell: M3 (estado e solicitação de sincronização) no backend.
