# Área da Contabilidade — mapa de telas, rotas e APIs (incremento 1)

Mapa exigido antes de qualquer integração de dados. Fonte das telas: [HANDOFF_CONTABIL.md](HANDOFF_CONTABIL.md). Contratos: [frontend-api-contract.md](../frontend-api-contract.md) e [OpenAPI](../openapi/finemp-api-v1.yaml).

Convenções: **FATO CONFIRMADO**, **DECISÃO**, **PROPOSTA**, **PONTO ABERTO** ([engineering-playbook](../engineering-playbook.md)).

## 1. Decisões deste incremento

- **DECISÃO** (pedido do produto): o FINEMP usa o login existente; depois dele há **Área da Empresa** ou **Área da Contabilidade**, conforme o acesso. Não há segundo login nem cadastro duplicado de empresa.
- **DECISÃO:** a Área da Contabilidade usa a hierarquia `AccountingFirm → Client → Company`. Ver [saas-hierarchy](../architecture/saas-hierarchy.md).
- **DECISÃO:** todas as telas do módulo contábil ficam visíveis no menu, com os nomes e códigos do protótipo e do HANDOFF. Tela sem API oficial mostra "Em breve" e **nenhum dado**: não há mock nem regra contábil no React.
- **DECISÃO:** o Administration / Onboarding SaaS continua na Área da Empresa (módulo "Administração"), transversal à plataforma. Ele não foi movido.
- **DECISÃO:** o módulo financeiro do cliente só ganhou o seletor de área no cabeçalho. Rotas, menu e telas dele não mudaram.

## 2. Shell e contexto: APIs usadas

| Uso | API | Situação |
| --- | --- | --- |
| Sessão, usuário e grants por empresa | `GET /api/auth/me` | **FATO CONFIRMADO**: implementada e já usada |
| Contabilidades do usuário (membro explícito) | `GET /api/auth/organization-access` | **FATO CONFIRMADO**: implementada (OpenAPI `getCurrentOrganizationAccess`); usada pela primeira vez no frontend |
| Clientes da contabilidade | `GET /api/accounting-firms/{accountingFirmId}/clients` | **FATO CONFIRMADO**: implementada; exige `HIERARCHY_MANAGE` na contabilidade. 403 aparece como "Sem permissão para listar clientes" |
| Empresas do cliente | `GET /api/accounting-firms/{accountingFirmId}/clients/{clientId}/companies` | **FATO CONFIRMADO**: implementada; exige `HIERARCHY_MANAGE` |

Regras de navegação (só UX; o backend autoriza cada dado):

- **PROPOSTA:** a Área da Contabilidade aparece para quem é membro de ao menos uma contabilidade **ativa** em `organization-access`. Permissão de plataforma (inclusive `SYSTEM_ADMIN`) não torna o usuário membro.
- **PROPOSTA:** depois do login, quem tem empresas continua indo para `/dashboard` (comportamento anterior). Só quem não tem empresa e é membro de contabilidade vai para `/contabilidade`.
- **FATO CONFIRMADO:** selecionar uma empresa na Área da Contabilidade não concede acesso a dados. Quando houver telas com dados, vale o grant da empresa em `/api/auth/me` (`companyGrant` no contexto).
- **PONTO ABERTO:** não existe endpoint de "carteira" (clientes e empresas atendidos) para analistas **sem** `HIERARCHY_MANAGE`. Hoje eles veem a contabilidade, mas não a lista de clientes. Não foi inventado endpoint.
- **PONTO ABERTO:** a competência em fechamento e o filtro de período ainda não são exibidos, porque dependem de "base contábil até" (dado de sincronização sem contrato de tela).
- **PROPOSTA:** o contexto selecionado (contabilidade, cliente, empresa) fica no estado do shell, com a última escolha lembrada como preferência visual. Ele não vai na URL, diferente das rotas `/contabil/empresas/{empresaId}/...` propostas no HANDOFF. Revisar quando houver dados.

## 3. Mapa TELA → rota React → API → estado

Todas as rotas ficam sob o shell `/contabilidade`; `/contabilidade` redireciona para o Cockpit. "Em breve" = página da tela com a estrutura do protótipo (bloco de título e painéis), sem dado.

| Código (HANDOFF) | Tela | Rota React | API oficial FINEMP disponível? | Estado |
| --- | --- | --- | --- | --- |
| `cfcock` | Cockpit de Fechamento | `/contabilidade/central-de-fechamento/cockpit` | Não | Em breve |
| `cfrot` | Roteiro do Analista | `/contabilidade/central-de-fechamento/roteiro` | Não | Em breve |
| `checklist` | Checklist de Fechamento | `/contabilidade/central-de-fechamento/checklist` | Não | Em breve |
| `cfdocs` | Documentos e Classificação | `/contabilidade/central-de-fechamento/documentos` | Não | Em breve |
| `cfbase` | Base de Classificação | `/contabilidade/central-de-fechamento/base-de-classificacao` | Não | Em breve |
| `cfrec` | Lançamentos Recorrentes e Planilhas | `/contabilidade/central-de-fechamento/recorrentes` | Não | Em breve |
| `cfantecip` | Despesas Antecipadas | `/contabilidade/central-de-fechamento/despesas-antecipadas` | Não | Em breve |
| `cfadiant` | Adiantamentos (Fornecedores e Clientes) | `/contabilidade/central-de-fechamento/adiantamentos` | Não | Em breve |
| `cfpre` | Pré-fechamento (Balancete e DRE) | `/contabilidade/central-de-fechamento/pre-fechamento` | Não. Balancete/Balanço (`.../reporting/balance-sheet`) é **PROPOSTA**; `GET .../accounting-movements` existe (razão sem paginação) mas não basta para a tela. | Em breve |
| `cflanc` | Lotes e Exportação Questor | `/contabilidade/central-de-fechamento/lotes-questor` | Não | Em breve |
| `cflalur` | IRPJ e CSLL · Lalur (Lucro Real) | `/contabilidade/central-de-fechamento/fiscal/lalur` | Não | Em breve |
| `cfpres` | Validador de Lucro Presumido | `/contabilidade/central-de-fechamento/fiscal/lucro-presumido` | Não | Em breve |
| `cfimp` | Validador de ISS, PIS e COFINS | `/contabilidade/central-de-fechamento/fiscal/iss-pis-cofins` | Não | Em breve |
| `cffolha` | Validador e Conciliador da Folha | `/contabilidade/central-de-fechamento/folha/validador` | Não | Em breve |
| `cfcong` | Períodos Congelados | `/contabilidade/central-de-fechamento/qualidade/periodos-congelados` | Não. `financial-periods` (fechar/reabrir) é do módulo financeiro da empresa e não equivale ao congelamento contábil. | Em breve |
| `diag` | Diagnóstico das Bases | `/contabilidade/central-de-fechamento/qualidade/diagnostico` | Parcial (#530): verificações de integridade da base sobre contratos oficiais — cobertura MOVEMENTS com lacunas e `unclassifiedAccountCount` (`GET /dre`), recusa `DRE_UNSUPPORTED_NEGATIVE_AMOUNT`, contas importadas pendentes (`GET /accounting-accounts`), plano completo (`GET /accounting-chart`), última SyncRun recusada (`GET /accounting-sources`). Ativo = Passivo + PL e DRE × balanço exigem balanço/saldos/encerramentos: **não publicados**. | Parcial |
| `depara` | De-Para do Plano de Contas | `/contabilidade/central-de-fechamento/qualidade/de-para` | Parcial: `GET /api/companies/{companyId}/accounting-accounts/classifications`, `PUT .../{accountId}/classification` e `GET /api/dre-classifications` cobrem só a classificação DRE por conta (sem regras por prefixo nem grupos do balanço). Não conectado: equivalência com a tela **PONTO ABERTO**. | Em breve |
| `cfvol` | Volumetria do Contrato | `/contabilidade/central-de-fechamento/qualidade/volumetria` | Não | Em breve |
| `gcmapa` | Estrutura e Mapa | `/contabilidade/pacote-de-consolidacao/mapa` | Não | Em breve |
| `gcbs` | Balanço (BS) | `/contabilidade/pacote-de-consolidacao/bs` | Não. Balanço contábil é **PONTO ABERTO** no roadmap. | Em breve |
| `gcpl` | Resultado (PL) | `/contabilidade/pacote-de-consolidacao/pl` | Não. `GET /api/companies/{companyId}/dre` (DRE contábil inicial) existe, mas não no formato do pacote. | Em breve |
| `gcexps` | Custos por Natureza (EXPS) | `/contabilidade/pacote-de-consolidacao/exps` | Não | Em breve |
| `gcss` | Patrimônio Líquido (SS) | `/contabilidade/pacote-de-consolidacao/ss` | Não | Em breve |
| `gctfa` | Imobilizado (TFA) | `/contabilidade/pacote-de-consolidacao/tfa` | Não | Em breve |
| `gpifa` | Intangível (IFA) | `/contabilidade/pacote-de-consolidacao/ifa` | Não | Em breve |
| `gcbb` | Empréstimos (BB) | `/contabilidade/pacote-de-consolidacao/bb` | Não | Em breve |
| `gccf` | Fluxo de Caixa (CF) | `/contabilidade/pacote-de-consolidacao/cf` | Não | Em breve |
| `gpcfinfo` | Informações do Fluxo de Caixa (CF_INFO) | `/contabilidade/pacote-de-consolidacao/cf-info` | Não | Em breve |
| `gpinvest` | Investimentos (INVEST) | `/contabilidade/pacote-de-consolidacao/invest` | Não | Em breve |
| `gplease` | Arrendamentos (LEASE) | `/contabilidade/pacote-de-consolidacao/lease` | Não | Em breve |
| `gppldtl` | Detalhe de Outros Resultados (PLDTL) | `/contabilidade/pacote-de-consolidacao/pldtl` | Não | Em breve |
| `gpmgt` | Perdas de Rentabilidade (MGT) | `/contabilidade/pacote-de-consolidacao/mgt` | Não | Em breve |
| `gpexcng` | Câmbio (EXCNG) | `/contabilidade/pacote-de-consolidacao/excng` | Não | Em breve |
| `gpbonus` | Bônus (BONUS) | `/contabilidade/pacote-de-consolidacao/bonus` | Não | Em breve |
| `gpinfo` | Informações da Empresa (INFO) | `/contabilidade/pacote-de-consolidacao/info` | Não | Em breve |
| `gpint` | Intercompany (INT_BS / INT_PL) | `/contabilidade/pacote-de-consolidacao/intercompany` | Não | Em breve |
| `gpfcst` | Projeção (PL_F) | `/contabilidade/pacote-de-consolidacao/projecao` | Não | Em breve |
| `gpbud` | Orçamento (PL_B / EXPS_B) | `/contabilidade/pacote-de-consolidacao/orcamento` | Não | Em breve |
| `sucesso` | Acompanhamento das Ações | `/contabilidade/sucesso-do-cliente/acoes` | Não. Ata `/api/clients/{clientId}/decisions` é **PROPOSTA**; o "Plano de ação" de Planejamento (`PLANNING_*`) não foi confirmado como a mesma entidade. | Em breve |
## 4. Telas contábeis que já existem na Área da Empresa

| Tela atual | Rota | API | Situação |
| --- | --- | --- | --- |
| Demais › Contabilidade (razão) | `/accounting-movements` | `GET /api/companies/{companyId}/accounting-movements` | Funcional; não foi movida |
| Cadastros › Plano de contas da contabilidade | `/accounting-accounts` | classificações de conta + catálogo DRE | Funcional; não foi movida |
| Demais › DRE contábil | `/dre` | `GET /api/companies/{companyId}/dre` | Funcional; não foi movida |

**PONTO ABERTO:** se essas telas passam para a Área da Contabilidade (por exemplo, como base do De-Para e do drilldown). Movê-las mudaria o menu do módulo do cliente, o que este incremento não fez.

## 5. Critério para trocar "Em breve" por "Funcional"

Uma tela só muda de `status: "soon"` para `"available"` em `accounting-screens.ts` quando:

1. o endpoint estiver implementado e publicado no OpenAPI;
2. a equivalência entre o contrato e a tela do HANDOFF estiver confirmada (campos, filtros, permissões);
3. as regras contábeis estiverem no backend, com o React apenas apresentando valores.
