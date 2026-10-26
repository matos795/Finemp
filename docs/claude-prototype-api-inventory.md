# Inventário do protótipo Claude para a API FINEMP

> Documento de descoberta técnica. O HTML analisado é uma referência funcional/visual e não é fonte de persistência, autorização ou regra contábil.

## 1. Objetivo e limites

Este inventário traduz as necessidades aparentes do protótipo para capacidades de backend. Ele não incorpora automaticamente fórmulas, agrupamentos ou rótulos encontrados no JavaScript.

Convenções usadas:

- **FATO CONFIRMADO** — já comprovado no produto, na descoberta ou no código atual;
- **DECISÃO** — orientação já aprovada para o produto;
- **PROPOSTA** — desenho reversível para discussão/implementação posterior;
- **PONTO ABERTO** — depende de validação de negócio ou investigação técnica.

Não foram copiados nomes, CNPJs, valores ou demais dados reais existentes no artefato.

## 2. Fundamentos confirmados

- **DECISÃO:** o Questor continua sendo o sistema contábil e o FINEMP é a camada gerencial e de análise.
- **DECISÃO:** requisições de tela consultam o PostgreSQL FINEMP; não consultam Firebird nem Excel.
- **FATO CONFIRMADO:** a integração atual normaliza dados do Questor e persiste contas e movimentos no FINEMP.
- **FATO CONFIRMADO:** já existem sessão autenticada, grants por empresa, permissões, catálogo de classificação, vínculo conta/classificação, consulta de movimentos e cálculo inicial da DRE.
- **FATO CONFIRMADO:** a classificação gerencial possui os níveis `DRE`, `DRE ABERTA` e `EBITDA`.
- **DECISÃO:** cálculos financeiros oficiais pertencem ao backend e usam `BigDecimal`.
- **DECISÃO:** estado compartilhado de negócio não permanece em `localStorage`; preferências exclusivamente visuais podem permanecer no frontend.
- **PONTO ABERTO:** regras completas de Balanço, DFC, DMPL, DuPont, indicadores e diagnósticos ainda não foram homologadas.
- **PONTO ABERTO:** os significados operacionais de `0`, `N/A` e `(-) Verificar` não foram confirmados; esses valores devem ser preservados como dados, sem semântica inventada.

## 3. Filtros transversais observados

O protótipo oferece filtros de empresa, período, grupo DRE, categoria, classe, origem e status de lançamento manual.

| Filtro | Classificação | Tratamento de contrato sugerido |
| --- | --- | --- |
| Empresa | **FATO CONFIRMADO** para consultas individuais | Identificador sempre validado contra o grant do usuário. |
| Período (`de`/`até`) | **FATO CONFIRMADO** | Datas ISO 8601, intervalo inclusivo e validação `início <= fim`. |
| Grupo DRE | **PROPOSTA** | Filtro opcional por identificador estável, não pelo texto exibido. |
| Categoria e classe | **PONTO ABERTO** | Não há taxonomia homologada suficiente para contrato definitivo. |
| Origem | **PROPOSTA** | Identificador de `AccountingSource`; nunca credenciais ou detalhes internos do ERP. |
| Status manual | **PROPOSTA** | Enumeração a definir junto ao fluxo de lançamentos manuais. |
| Visão consolidada | **DECISÃO** como evolução | Seleção explícita das empresas participantes; nunca assumir todas as empresas do cliente. |

## 4. Inventário por tela

Os endpoints na coluna “Contrato sugerido” são propostas de evolução. Endpoints já disponíveis são marcados como **EXISTENTE**.

| Tela | Dados consumidos | Filtros e ações observados | Cálculos/comportamentos observados | Fonte aparente atual | Contrato sugerido | Permissão | Estado |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Painel | indicadores executivos, receita, EBITDA, custos, estrutura patrimonial, caixa e inconsistências | empresa, período; abrir diagnóstico | cartões, séries, composição e narrativa executiva | agregações geradas no protótipo | `GET /api/companies/{companyId}/reporting/dashboard` | `DASHBOARD_VIEW` | **PROPOSTA**; fórmulas e narrativa são **PONTO ABERTO** |
| Ata de Decisões | decisões, próximas ações, responsável, prazo, tipo, status, ROI, origem e datas | filtrar pendentes/concluídas/todas; buscar; criar/editar; exportar | sugestões a partir do diagnóstico | `localStorage` e coleção do artefato | `/api/clients/{clientId}/decisions` com paginação e auditoria | nova permissão a definir | Persistência backend: **DECISÃO**; escopo/permissionamento: **PONTO ABERTO** |
| DRE | linhas hierárquicas, totais e quantidade de contas pendentes | empresa, período; níveis Resumida/Grupos/Contas/Cliente-fornecedor/Histórico; expandir | agrupamento e drill-down | movimentos + classificação gerencial | **EXISTENTE:** `GET /api/companies/{companyId}/dre`; extensões de drill-down em contrato próprio | `DRE_VIEW` | Totais iniciais: **FATO CONFIRMADO**; hierarquia oficial: **PONTO ABERTO** |
| DRE Comparativa | DRE mensal/acumulada, períodos e variações | empresa, períodos; acumulado/mês; níveis resumo/grupos/contas | diferença absoluta e percentual | agregação no JavaScript | `GET /api/companies/{companyId}/reporting/dre-comparison` | `DRE_VIEW` | **PROPOSTA**; regras de comparação: **PONTO ABERTO** |
| Balanço | ativo, passivo, patrimônio e contas | data-base/período; níveis Grupos/N2/N3/N4/Todas | saldos e estrutura patrimonial | movimentos e mapeamentos aparentes | `GET /api/companies/{companyId}/reporting/balance-sheet` | `BALANCE_SHEET_VIEW` | **PONTO ABERTO** |
| Composição | composição de saldos por lado, grupo, conta e item; histórico de fotografias | data-base, lado, busca, níveis; registrar fotografia; exportar; histórico | participação e abertura do saldo | cálculos do protótipo e snapshots locais | `GET /api/companies/{companyId}/reporting/account-composition`; recurso separado para snapshots | permissão específica a definir | Consulta: **PROPOSTA**; snapshot/histórico: **PONTO ABERTO** |
| Fluxo de Caixa | atividades, entradas/saídas, linhas, contrapartes e históricos | direto/indireto; empresa; período; níveis | fluxo direto/indireto | regras JavaScript | `GET /api/companies/{companyId}/reporting/cash-flow` | `CASH_FLOW_VIEW` | **PONTO ABERTO** |
| DMPL | movimentação do patrimônio líquido | empresa, período e abertura | saldos inicial/final e mutações | regras JavaScript | `GET /api/companies/{companyId}/reporting/equity-changes` | permissão a definir | **PONTO ABERTO** |
| DuPont | decomposição do retorno e comparações | período anterior ou mesmo período do ano anterior; níveis efeito/componentes/grupos/contas | decomposição e efeitos | regras JavaScript | `GET /api/companies/{companyId}/reporting/dupont` | `INDICATORS_VIEW` | **PONTO ABERTO** |
| Lançamentos manuais | competência, data, conta, contrapartida, descrição, valor, débito/crédito, status e observação | buscar/filtrar; criar/editar; importar/exportar | validações e impacto aparente em relatórios | `localStorage` e coleção do artefato | `/api/companies/{companyId}/manual-entries` | `MANUAL_ENTRY_MANAGE` | Persistência backend: **DECISÃO**; workflow e contabilização: **PONTO ABERTO** |
| Analítico | movimentos, conta, histórico, origem e referência; comparação aparente com balancete | empresa, período, busca; paginação; CSV | separação débito/crédito e filtros | movimentos FINEMP e dados simulados | **EXISTENTE:** `GET /api/companies/{companyId}/accounting-movements`; evoluir para paginação server-side | acesso à empresa hoje; permissão específica a avaliar | Consulta: **FATO CONFIRMADO**; comparação com balancete: **PONTO ABERTO** |
| Indicadores | margens, séries mensais, índices econômicos e rentabilidade | empresa, período e indicador | margens e comparações com índices | cálculos JavaScript e dados de referência | `GET /api/companies/{companyId}/reporting/indicators` | `INDICATORS_VIEW` | **PONTO ABERTO**; fontes externas também abertas |
| Diagnóstico | inconsistências e verificações aprovadas | empresa, período; abrir detalhe; aprovar/registrar ação | regras automáticas de verificação | JavaScript do protótipo | `GET /api/companies/{companyId}/reporting/diagnostics`; ações auditáveis separadas | `INDICATORS_VIEW` inicialmente, granularidade a avaliar | **PONTO ABERTO** |
| Premissas | parâmetros e explicações usadas nos relatórios | consultar e possivelmente versionar | metadados de cálculo | conteúdo estático no protótipo | `GET /api/reporting/rules` ou metadados versionados por relatório | autenticado | Publicar regras homologadas: **PROPOSTA**; conteúdo atual: **PONTO ABERTO** |

## 5. Capacidades já presentes no backend

| Capacidade | Endpoint atual | Observação |
| --- | --- | --- |
| Obter token CSRF | `GET /api/auth/csrf` | Público; necessário antes de requisições mutáveis. |
| Login por sessão | `POST /api/auth/login` | Cookie de sessão `HttpOnly`; senha não é persistida no cliente. |
| Usuário e empresas acessíveis | `GET /api/auth/me` | Retorna role, permissions e escopo de centro de custo por empresa. |
| Logout | `POST /api/auth/logout` | Implementado pela configuração de segurança. |
| Movimentos contábeis por período | `GET /api/companies/{companyId}/accounting-movements` | Ainda retorna lista sem paginação server-side. |
| Contas e estado de classificação | `GET /api/companies/{companyId}/accounting-accounts/classifications` | Estados atuais `PENDING` e `CLASSIFIED`. |
| Classificar/reclassificar conta | `PUT /api/companies/{companyId}/accounting-accounts/{accountId}/classification` | Requer evolução de autorização conforme Issue existente. |
| Catálogo DRE | `GET /api/dre-classifications` | Catálogo global FINEMP. |
| DRE inicial | `GET /api/companies/{companyId}/dre` | Agrega `DRE` → `DRE ABERTA` → `EBITDA`; drill-down oficial segue aberto. |
| Importação Questor controlada | `POST /api/accounting-sources/{id}/questor-import` | Disponível somente quando a integração está habilitada; será substituída/evoluída pela sincronização e Connector. |

## 6. Contratos transversais necessários

### 6.1 Paginação e busca

**DECISÃO:** listagens potencialmente grandes usarão paginação, busca e filtros server-side. Contrato proposto:

- `page`: índice iniciado em zero;
- `size`: tamanho limitado pelo servidor;
- `sort`: campos permitidos explicitamente;
- resposta com `items`, `page`, `size`, `totalItems` e `totalPages`.

O endpoint atual de movimentos é uma exceção temporária e deve ser evoluído sem quebra abrupta do consumidor.

### 6.2 Dinheiro e percentuais

- **DECISÃO:** o backend calcula com `BigDecimal`.
- Valores monetários são números JSON com escala documentada; o frontend apenas formata.
- Percentuais devem declarar se usam fração (`0.125`) ou pontos percentuais (`12.5`); esse padrão será definido antes dos endpoints de indicadores.

### 6.3 Hierarquia e drill-down

**PROPOSTA:** relatórios hierárquicos retornam nós com `id`, `code`, `label`, `level`, `amount`, `hasChildren` e, quando solicitado, `children`. O identificador não deve depender do rótulo exibido.

**PONTO ABERTO:** a primeira hierarquia oficial da DRE ainda precisa ser validada. Os níveis vistos no protótipo não são considerados regra.

### 6.4 Segurança e isolamento

- 401 quando não autenticado;
- 403 quando autenticado sem grant/permissão/escopo;
- validação de acesso no backend para cada `companyId`;
- nenhuma consulta pode confiar no filtro de empresa enviado pelo frontend sem aplicar o grant;
- filtros consolidados devem intersectar a seleção com empresas autorizadas.

## 7. Estado que deve sair do protótipo

| Estado | Destino | Justificativa |
| --- | --- | --- |
| Ata de Decisões | PostgreSQL + API auditável | Dado compartilhado de negócio. |
| Lançamentos manuais | PostgreSQL + API segura | Dado financeiro compartilhado e potencialmente material. |
| Fotografias de composição, se aprovadas | PostgreSQL + histórico | Compartilhadas e necessárias para rastreabilidade. |
| Preferência de menu, tema ou expansão de linhas | Frontend | Estado visual sem significado de negócio. |
| Filtros temporários da sessão | URL/estado do frontend | Reproduzibilidade sem criar dado de negócio. |

## 8. Posições deliberadamente não tomadas

Este inventário não decide:

- fórmulas oficiais de Balanço, DFC, DMPL, DuPont ou indicadores;
- semântica de `0`, `N/A` e `(-) Verificar`;
- influência de `BDGER`, `AnoAnt` ou `ConciAnterior`;
- hierarquia oficial de drill-down da DRE;
- workflow de aprovação e efeito contábil dos lançamentos manuais;
- política de snapshots de composição;
- granularidade final de permissões para Ata, Composição, DMPL, Analítico e Diagnóstico;
- forma final de consolidação além da decisão de selecionar explicitamente as empresas participantes.

Esses itens permanecem desacoplados para que o roadmap avance sem antecipar regra de negócio.


