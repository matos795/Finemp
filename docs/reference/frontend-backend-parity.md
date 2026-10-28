# FINEMP — matriz integral de paridade funcional

## Checkpoint de implementação — 28/10/2026

- **FATO CONFIRMADO:** #36 e #37 integradas: identidade, sessões, administração
  nos três escopos, grants explícitos e auditoria. RESTRICTED permanece negado
  até haver filtro real; não é funcionalidade afirmada pronta.
- **FATO CONFIRMADO:** controle/executor SyncRun, publicação atômica, leases,
  retries e credenciais scoped foram integrados. PRs #97–#117 acrescentam
  protocolo HTTPS, identidade observada/snapshot verificado, cliente/ciclo
  outbound e runtime opt-in. #117 validou contrato composto com PostgreSQL,
  resposta perdida, fonte errada, cancelamento REQUESTED, fence substituído e
  período inválido; não é homologação de Firebird/rede operacional.
- **FATO CONFIRMADO:** #118/PR119 e #120/PR121 integradas: contas financeiras e
  parceiros comerciais por Empresa, permissões explícitas, versões, concorrência
  e contratos PostgreSQL/HTTP. Referências operacionais reais dependem da #39.
- **FATO CONFIRMADO:** #122/PR123 integrada: centros hierárquicos e exigência
  configurável para novos lançamentos, com contratos PostgreSQL e concorrência.
- **FATO CONFIRMADO:** #124/PR125 e #126/PR127 integradas: classificação bruta
  e efetiva consistente e plano padrão com paridade integral, rollback e concorrência.
  #38 concluída; uso operacional dos cadastros continua nos módulos dependentes.
- **FATO CONFIRMADO:** #129/PR130 integrada: calendário confirmado de séries,
  sem acoplamento a uma política monetária presumida.
- **FATO CONFIRMADO:** #131/PR132 integrada: persistência canônica de
  FinancialEntry e consultas reais de uso dos cadastros, sem writer de captura.
- **FATO CONFIRMADO:** #133/PR134 integrada: consulta financeira paginada,
  filtros homologados, projeções atuais, permissão vigente e moeda exata;
  contratos PostgreSQL/HTTP e CI verdes. Não libera escrita.
- **FATO CONFIRMADO:** #135/PR137 integrada: comandos internos CREATE/UPDATE,
  recibo idempotente, versão atual/esperada e auditoria atômica; 13 contratos PG.
- **FATO CONFIRMADO:** #136/PR138 integrada: API individual CREATE/UPDATE,
  sessão/CSRF, autorização antes do corpo, JSON estrito, limites por rota
  autorizada inclusive URI codificado e opt-in separado; nove contratos PG/HTTP.
- **FATO CONFIRMADO:** #139/PR140 integrada: baixa/estorno internos canônicos,
  versão atual/esperada, recibo idempotente e auditoria atômica; onze contratos PG.
- **EM IMPLEMENTAÇÃO:** #39, API de baixa/estorno na #141, com JSON estrito,
  autorização atual, CSRF, corpo limitado por rota autorizada e contrato próprio.
  #128 registra
  diferenças binárias em parcelas antes de decidir a política monetária.
  A matriz original abaixo
  preserva o baseline do inventário; este checkpoint registra avanços posteriores.
- **PONTO ABERTO:** ambiente/mapeamento/permissões Firebird, distribuição/cofre
  definitivos e contratos incremental/retificação/cursor. #50 permanece aberta;
  essas dependências isoladas não bloqueiam os módulos independentes.

## Autoridade da referência

**DECISÃO — 26/10/2026:** o negócio homologou as funcionalidades, comportamentos e cálculos executáveis de `FINEMP_web_1.zip`. Eles são requisitos funcionais. A arquitetura continua sendo o monólito modular Java/Spring/PostgreSQL existente.

Essa decisão supera a classificação anterior de fórmulas deste novo produto como meras propostas. O inventário anterior do HTML de análise contábil permanece histórico: é outra referência. Não se devem confundir as duas fontes.

| Evidência | Identificação |
| --- | --- |
| ZIP | SHA-256 `da04355d907b4b108a997278f34ebb5e3e06fbd3cd0ed334eb168d15d0bae200` |
| `web/index.html` | SHA-256 `7f8342a08bcc77fbdc76f6160dd7fcec766f83f2c89f4d5b2dd919040bfc7d51` |
| Navegação | `TABS`, linha 569; `MENU`, linhas 1828–1840; administração, linha 5754 |
| Cobertura | 58 telas base + 3 administrativas = 61 entradas de navegação |
| Extração | AST do JavaScript: 562 declarações de funções nomeadas; callbacks não contam como funcionalidades adicionais |
| Persistência observada | `SEC_KEYS`/`SEC_ABAS`, linhas 5748–5750; adapters demo/Supabase, linhas 5785–5905 |
| Backend comparado | `main`, commit `13b2015929dcbddf73adaf15c61012db3b871199` |
| Issue de inventário | [#35](https://github.com/matos795/Finemp/issues/35) |

O arquivo de referência permanece fora do Git: contém dados e configurações que não devem ser publicados. O inventário contém apenas estrutura, regras e identificadores de código. Exemplos e fixtures serão sintéticos.

## Convenções e estado real

- **FATO CONFIRMADO:** comportamento observado no código da referência ou no backend identificado acima.
- **DECISÃO:** comportamento homologado que será preservado ou diretriz explícita do projeto.
- **PROPOSTA:** nomes de módulos, DTOs, endpoints e permissions para implementar o comportamento.
- **PONTO ABERTO:** apenas dependência efetivamente ausente, divergência ainda não reproduzida ou operação externa indisponível. A implementação observável do ZIP não depende de nova aprovação de negócio.

Estados: **existente** = implementado no `main`; **parcial** = capacidade próxima, mas sem paridade integral; **pendente** = ainda não implementado; **UI** = apresentação local, com dados de negócio fornecidos pela API.

Todos os caminhos e permissions das tabelas abaixo são **PROPOSTA**, exceto os endpoints já existentes explicitamente identificados. Não representam APIs disponíveis hoje. As linhas indicadas referem-se ao `web/index.html` original.

## Domínio e identidade

```text
Platform → AccountingFirm → Client → Company → AccountingSource
                                      ├── AccountingMovement (fonte contábil)
                                      └── FinancialEntry (operação financeira)
```

- **DECISÃO:** `Client` significa cliente SaaS da Contabilidade; `BusinessPartner` significa cliente/fornecedor comercial da Empresa.
- **DECISÃO:** CNPJ é atributo cadastral, e UUID é identidade técnica. CNPJ não é identidade global do tenant.
- **DECISÃO:** DRE contábil usa `AccountingMovement`; DRE operacional usa `FinancialEntry`, competência e efeitos patrimoniais. A seleção/reconciliação de origens será explícita, sem mistura física dos dados.
- **FATO CONFIRMADO:** Company, AccountingSource, AccountingAccount, AccountingMovement, catálogo/vínculo DRE, sessão/CSRF e grants por Empresa já existem. AccountingFirm, Client e permissions de Plataforma/Contabilidade foram integrados pelos PRs #31 e #34.
- **FATO CONFIRMADO:** o CRUD administrativo da Issue #32 está em rascunho local e não é contado como entregue nesta matriz.
- **DECISÃO:** acesso à Contabilidade administra o escopo autorizado; dados financeiros continuam exigindo grants e permissions por Empresa.
- **DECISÃO:** `view`/`edit` por tela na referência será traduzido para permissions explícitas. Papel de administrador não concede acesso financeiro implícito.

## Dicionário das persistências observadas

As siglas abaixo documentam onde o protótipo guarda dados, não propõem schema do backend. No modo demo os dados vão ao navegador; no modo conectado são seções por Empresa com JSON e versionamento. Anexos usam IndexedDB/storage.

| Sigla | Conteúdo observado | Entidades relacionais inferidas para o FINEMP |
| --- | --- | --- |
| BASE | dados da empresa, contas bancárias, grupos/subcategorias, centros, configuração contábil | CompanyProfile, BankAccount, FinancialCategory, ResultCenter, AccountingExportConfiguration |
| PAR | `parceiros` | BusinessPartner, contato/endereço e papéis comerciais |
| FIN | `lanc`, `transf`, `extr`, `extratos`, `regras` | FinancialEntry, série/parcela, Settlement, BankTransfer, Statement, StatementMovement, Reconciliation, MatchingRule |
| CTR | `contratos`, `nfs` | Contract, AdjustmentHistory, BillingOccurrence, ServiceInvoiceDraft |
| DIV | `dividas`, `divCfg` | DebtAgreement, DebtInstallment, DebtConfiguration |
| ORC | `orc` por ano | BudgetPlan, BudgetCell, BudgetRule, Scenario, FixedCostPlan, VariableCostPlan, WorkforcePlan |
| PLN | `plan` | PlanningMeeting, Goal, Measurement, ActionPlan, DiagnosticSnapshot |
| FUN | `func`, `funcCfg` | Employee, SalaryHistory, EmployeeEventConfiguration |
| EST | `prod`, `estAj`, `nfe`, `precoCfg` | Product, InventoryAdjustment, InvoiceImport, InvoiceItem, PricingConfiguration |
| CRM | `crm` | Opportunity, StageHistory, SalesActivity, OpportunityConversion |
| BAL | `balItens` | BalanceAdjustment com vigência |
| BEM | `bens` | Asset, Maintenance, Insurance, AssetDisposal, vínculos de origem |
| REL | `ncgCfg`, `pme`, `map` | ReportConfiguration, FinancialClassificationMapping |
| IAM | perfis, acessos, clientes, log | UserAccount, grants, permissions, Invitation, PasswordResetToken, AuditEvent, quotas/estado do cliente |

## Matriz por tela

Cada linha informa finalidade/dados, entidades e dependências (pelas siglas acima), filtros, ações/CRUD, cálculo/fórmula e fonte, contrato/permission propostos, testes e estado/Issue. A persistência observada de cada tela é a das siglas citadas.

### Fluxo de caixa — 7 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `painel` — saldos, entradas/saídas, vencidos, projeção e saúde financeira | FIN, BASE, REL | mês; abrir detalhe/diagnóstico | `renderPainel` 593–651, `painelRP` 652–676, `saldoTotal`, `projecao` | `GET /companies/{id}/finance/dashboard`; `FINANCE_DASHBOARD_VIEW` | totals/previsto-realizado/projeção; pendente; #41 |
| `lanc` — operações financeiras | FIN, PAR, BASE; anexos/estoque/CRM | período, busca, tipo, conta, categoria, status, centro, origem; criar/editar/duplicar, excluir/cancelar, baixar/estornar, lote, exportar | `filtrarLanc` 685–701; `salvarLanc` 775–818; `confirmarBaixa` 833–839 | `/companies/{id}/financial-entries`; `FINANCIAL_ENTRY_VIEW/MANAGE` | parcelas, recorrência, valor efetivo e transação; pendente; #39 |
| `agenda` — pagar/receber por faixa de prazo | FIN, PAR | data/faixa; abrir lançamento e baixa | `renderAgenda` 842–869 | `GET /companies/{id}/finance/agenda`; `FINANCIAL_ENTRY_VIEW` | vencido/hoje/próximos, limites de data; pendente; #41 |
| `ext` — extratos e conciliação | FIN, BASE | conta, status; importar OFX/planilha, aceitar sugestão, conciliar/desconciliar, ignorar, gerar lançamento/regra | 1591–1700; `candidatos` 1637–1642 | `/companies/{id}/bank-statements`, `/bank-reconciliations`; `BANK_RECONCILIATION_MANAGE` | dedup, candidatos e vínculo único; pendente; #40 |
| `mensal` — caixa mensal por grupo/categoria | FIN, BASE, REL | ano, modo real/previsto/ambos, centro/subárvore, meses/trimestres/semestres, total/média; expandir e abrir títulos | `agregarAno` 538–545; `matrizAno` 873–880; `renderMensal_` 2041–2114 | `GET /companies/{id}/finance/cash-flow/monthly`; `CASH_FLOW_VIEW` | 12 meses, órfãos, natureza e filtros; pendente; #41 |
| `diario` — fluxo diário | FIN, BASE, REL | mês, centro, modo/período; expandir dias | `renderDiario_` 887–915 | `GET /companies/{id}/finance/cash-flow/daily`; `CASH_FLOW_VIEW` | saldo inicial/final e movimento diário; pendente; #41 |
| `bancos` — contas e saldos realizados | FIN, BASE | ano/período/conta; conferir saldo de extrato, transferir e cancelar transferência | `saldoConta` 529–534, `renderBancos` 918–944, `salvarTransf` 950–954 | `/companies/{id}/bank-accounts`, `/bank-transfers`; `BANK_ACCOUNT_VIEW/MANAGE` | origem/destino diferentes e soma neutra; pendente; #38/#39 |

### Relatórios — 9 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `relRec` — contas a receber em aberto | FIN, PAR, BASE | data-base, centro, faixa, agrupamento; baixa e exportação | `titulosAbertos`, `faixaDe`, `renderTitulos` 2184–2219 | `GET /companies/{id}/finance/open-receivables`; `FINANCIAL_ENTRY_VIEW` | abertura histórica/aging/subtotais; pendente; #41 |
| `relPag` — contas a pagar em aberto | FIN, PAR, BASE | mesmos filtros, lado pagar; baixa/exportação | mesmo código com tipo S | `GET /companies/{id}/finance/open-payables`; `FINANCIAL_ENTRY_VIEW` | mesmas fronteiras de data e tipo; pendente; #41 |
| `dre` — DRE operacional por competência | FIN, BASE, REL, BEM | ano, período, centro, expandir categorias; exportar | `DRE_EST` 2118–2127, `dreEfeitos` 2128–2132, `dreAno` 2133–2139 | `GET /companies/{id}/operational-dre`; `DRE_VIEW` | competência + diferença de baixa + depreciação/alienação; pendente; #41. DRE contábil existente não equivale a esta tela |
| `conc` — ponte resultado → caixa | FIN, REL, BEM | meses inicial/final; detalhes de ajustes | `conciliar_` 2222–2241 | `GET /companies/{id}/finance/cash-dre-reconciliation`; `CASH_FLOW_VIEW` | resultado + ajustes = variação; pendente; #41 |
| `ciclo` — PMR/PMP/PME e ciclo financeiro | FIN, PAR, REL | período/base; alterar PME; detalhe por parceiro | `mediaPond`, `cicloPeriodo` 2360–2395; sugestões 2396–2418 | `GET /companies/{id}/finance/working-capital-cycle`; `INDICATORS_VIEW` | ponderação, sem denominador e fallback concedido; pendente; #41 |
| `ncg` — capital de giro, tesouraria e Fleuriet | FIN, DIV, REL | meses/data-base; estoque informado e configurações | `ncgEm` 2933–2949, `ncgResumo`, `diretrizesNcg` | `/companies/{id}/finance/working-capital`; `INDICATORS_VIEW` | ACO/PCO, NCG/T/CDG e seis tipos; pendente; #41 |
| `bal` — balanço operacional | FIN, EST, BEM, DIV, BAL | mês/data-base; criar/editar/remover ajuste de vigência, exportar | `balancoEm` 4219–4233; `renderBal` 4234–4298 | `/companies/{id}/finance/balance-sheet`, `/balance-adjustments`; `BALANCE_SHEET_VIEW/MANAGE` | ativo/passivo/PL, CP/LP, dupla contagem; pendente; #41 |
| `func` — cadastro e eventos de funcionários | FUN, BASE; orçamento/anexos | busca, centro, tipo e situação; CRUD/inativar, histórico salarial, exportar, enviar ao orçamento | 3806–3923; aniversário/admissão/demissão e calendário | `/companies/{id}/employees`; `EMPLOYEE_VIEW/MANAGE` | datas, salário restrito e integração orçamento; pendente; #45 |
| `consist` — verificações de consistência | FIN, BASE, REL, BEM | ano; abrir ocorrência | `verificacoes` 2277–2342 | `GET /companies/{id}/finance/diagnostics`; `FINANCE_DIAGNOSTICS_VIEW` | cada verificação com positivo/negativo sintético; pendente; #41 |

### Endividamento — 6 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `divPainel` — visão geral das dívidas | DIV, FIN, BASE | faixas/alertas, referência operacional; importar/exportar e abrir origem | `resumoDividas` 2544–2559; `renderDivPainel` 2773–2817 | `GET /companies/{id}/debts/summary`; `DEBT_VIEW` | saldo, quitação, juros, taxa/prazo ponderados; pendente; #43 |
| `divBanco` — dívidas bancárias | DIV, FIN, PAR | busca/situação; CRUD, gerar/desvincular parcelas, ver cronograma | `renderDivLista` 2620–2653; 2657–2742 | `/companies/{id}/debts?origin=BANK`; `DEBT_VIEW/MANAGE` | SAC/fixas, captação, lançamento associado; pendente; #43 |
| `divForn` — dívidas com fornecedores | DIV, FIN, PAR | busca/situação; mesmos comandos com origem fornecedor | mesmos métodos parametrizados | `/companies/{id}/debts?origin=SUPPLIER`; `DEBT_VIEW/MANAGE` | modalidade e credor corretos; pendente; #43 |
| `divImp` — parcelamentos de impostos | DIV, FIN | busca/situação; mesmos comandos com origem imposto | mesmos métodos parametrizados | `/companies/{id}/debts?origin=TAX`; `DEBT_VIEW/MANAGE` | principal/juros/guias; pendente; #43 |
| `divTerc` — mútuos e terceiros | DIV, FIN, PAR | busca/situação; mesmos comandos com origem terceiro | mesmos métodos parametrizados | `/companies/{id}/debts?origin=THIRD_PARTY`; `DEBT_VIEW/MANAGE` | vínculo e calendário; pendente; #43 |
| `divCron` — cronograma de pagamentos | DIV, FIN | período/agrupamento; exportar | `tabelaDiv` 2506–2520; `situacaoDiv` 2522–2543; 2818–2834 | `GET /companies/{id}/debts/schedule`; `DEBT_VIEW` | resíduo final, vencidos, faixas 12/18 meses; pendente; #43 |

### Comercial — 4 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `crmFunil` — oportunidades e pipeline | CRM, PAR, CTR, FIN | busca/responsável/etapa; CRUD, mover, ganhar/perder/reabrir, atividade, converter em contrato/venda | 4316–4414 | `/companies/{id}/opportunities`; `CRM_VIEW/MANAGE` | probabilidade, histórico e conversão idempotente; pendente; #48 |
| `crmAtiv` — follow-ups | CRM, PAR | prazo/conclusão; criar, editar, concluir, reagendar e excluir/cancelar | 4417–4475 | `/companies/{id}/sales-activities`; `CRM_VIEW/MANAGE` | atrasada/hoje e conclusão; pendente; #48 |
| `crmCli` — carteira e ficha do cliente comercial | PAR, CRM, FIN, CTR | cliente/busca; ficha, cadastrar parceiro, abrir oportunidade/contrato | `dadosCliente` 4478–4487, 4488–4515 | `GET /companies/{id}/business-partners/{partnerId}/sales-profile`; `CRM_VIEW` | agregação sem confundir Client SaaS; pendente; #48 |
| `crmInd` — indicadores comerciais | CRM, CTR, FIN | período/ordenação; drill-down | 4518–4548 | `GET /companies/{id}/sales/indicators`; `CRM_VIEW` | pipeline ponderado, conversão e valores; pendente; #48 |

### Contratos — 3 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `ctr` — contratos e reajustes | CTR, PAR, FIN, BASE, índices | busca/status/tipo; CRUD, gerar cobranças, reajustar, consultar histórico e índices | 1191–1381; `calcNF`, `competencias`, `vencDe`, `aplicarReajuste` | `/companies/{id}/contracts`; `CONTRACT_VIEW/MANAGE` | líquido, limite de mês, repetição e reajuste; pendente; #42 |
| `fatMes` — faturamento mensal | CTR, FIN, PAR | competência/busca; adicional/extra, 13º, geração e exportação | `valorVigente`, `avos13`, `valor13`, `linhasFaturamento` 1472–1587 | `GET /companies/{id}/billing`; `/billing-extras`; `BILLING_VIEW/MANAGE` | vigência histórica, adicional e 13º; pendente; #42 |
| `nf` — notas fiscais de serviço | CTR, FIN, PAR, perfil da Empresa | mês/status; preparar texto, editar, marcar emitida, cancelar, exportar, abrir portal | 1385–1469 | `/companies/{id}/service-invoices`; `BILLING_VIEW/MANAGE` | retenções separadas; não há emissão fiscal via API no ZIP, apenas preparação/registro; pendente; #42 |

### Estoque e patrimônio — 5 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `estProd` — produtos e saldos | EST, FIN, PAR | busca/situação/produto; CRUD/inativar, ajustes, importar XML, exportar | `estMovs`, `estProcessar`, `estValidar` 3931–3960; 3989–4084 | `/companies/{id}/products`, `/inventory-adjustments`; `INVENTORY_VIEW/MANAGE` | custo médio, bloqueio negativo, histórico retroativo; pendente; #46 |
| `estMov` — kardex | EST, FIN | produto, datas, origem; detalhar, ajustar e exportar | 4087–4107 | `GET /companies/{id}/inventory/movements`; `INVENTORY_VIEW` | ordenação entrada antes de saída, quantidade 3 casas; pendente; #46 |
| `estNfe` — importação de NF-e XML | EST, PAR, FIN, BEM | documento/tipo; ler XML, mapear produto, confirmar, detalhar e remover/cancelar | `lerNfeXml`, `prepararNfe`, `confirmarNfe` 4112–4185 | `/companies/{id}/invoice-imports`; `INVENTORY_IMPORT` | duplicidade por chave, rateio de custo, estoque e financeiro atômicos; pendente; #46 |
| `estPreco` — formação de preço | EST, FIN, REL | parâmetros percentuais; simular e aplicar sugerido | 4188–4214 | `/companies/{id}/pricing`; `INVENTORY_VIEW/MANAGE` | markup divisor, margem efetiva, denominador <=0; pendente; #46 |
| `bens` — patrimônio | BEM, FIN, EST, FUN, PAR, BASE | busca/situação/categoria/ano; CRUD, manutenção/seguro, baixa/venda/reversão, cadastrar de origem, exportar | 4557–4734 | `/companies/{id}/assets`; `ASSET_VIEW/MANAGE` | depreciação, residual, mês da baixa, ganho/perda, origem única; pendente; #47 |

### Planejamento — 3 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `plnCron` — cronograma de encontros | PLN, relatórios | etapa; CRUD encontro, restaurar modelo, exportar agenda e abrir área | 4976–5020, 5080–5145 | `/companies/{id}/planning/meetings`; `PLANNING_VIEW/MANAGE` | datas/etapas e diagnóstico capturado; pendente; #49 |
| `plnMetas` — metas e indicadores | PLN, FIN, DIV, ORC, REL | status/meta; CRUD, medir, fotografar diagnóstico e remover fotografia | `valorKpi` 5051–5064; `situacaoMeta` 5067–5074; 5148–5201 | `/companies/{id}/goals`, `/measurements`, `/diagnostic-snapshots`; `PLANNING_VIEW/MANAGE` | sentido up/down, prazo, teto120%, margem15%; pendente; #49 |
| `plnAcoes` — plano de ação / 5W2H | PLN | status/responsável/meta/lista ou kanban; CRUD, mover/concluir e exportar | 5205–5259 | `/companies/{id}/action-plans`; `PLANNING_VIEW/MANAGE` | custo/responsável/conclusão/histórico; pendente; #49 |

### Orçamento — 10 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `orc` — painel do orçamento | ORC, FIN, CTR, FUN, BASE | ano, base/período/centro; abrir detalhe/exportar | `orcCalc` 3170–3184; 3396–3425 | `GET /companies/{id}/budgets/{year}/dashboard`; `BUDGET_VIEW` | totais e previsto×realizado; pendente; #44 |
| `orcPlano` — plano por centro e categoria | ORC, BASE, CTR, FUN | ano/centro/período/linhas com valor; editar células/regras, repetir, criar conta, média/realizado, exportar | 3233–3340 | `/companies/{id}/budgets/{year}/cells`, `/rules`; `BUDGET_MANAGE` | precedência manual/regra/auxiliares/contratos; pendente; #44 |
| `orcPes` — planejamento de pessoal | ORC, FUN, BASE | ano/centro/período/regime; CRUD, duplicar, benefícios, parâmetros e vincular contas | `custoPessoa` 3570–3596; 3620–3714 | `/companies/{id}/workforce-plans`; `WORKFORCE_PLAN_VIEW/MANAGE` | CLT/APR/PRO/EST/PJ, caixa/provisão; pendente; #45 |
| `orcFix` — gastos fixos auxiliares | ORC, BASE | ano/centro; CRUD item, periodicidade/reajuste/manual, trazer do plano | `valorItemFixo` 3604–3609; `renderAuxItens` 3718–3800 | `/companies/{id}/budgets/{year}/fixed-costs`; `BUDGET_MANAGE` | meses ativos, referência e reajuste; pendente; #44 |
| `orcVar` — gastos variáveis auxiliares | ORC, BASE | ano/centro/base; CRUD item, percentual ou valores | `valorItemVar` 3611–3614; 3718–3800 | `/companies/{id}/budgets/{year}/variable-costs`; `BUDGET_MANAGE` | base total/centro/categoria e vínculo; pendente; #44 |
| `orcDre` — DRE orçada×realizada | ORC, FIN, BEM, BASE | ano, mês, modo, visão, percentuais/centro/período; expandir/exportar | `realCompetencia` 3189–3197; `classeConta`, `gerencial`; 3359–3392 | `GET /companies/{id}/budgets/{year}/dre-comparison`; `BUDGET_VIEW` | DRE gerencial e caixa fora do resultado; pendente; #44 |
| `orcFat` — faturamento orçado | ORC, FIN, CTR | ano/base/centro; detalhar | 3428–3442 | `GET /companies/{id}/budgets/{year}/revenue`; `BUDGET_VIEW` | receita bruta e mensalidade; pendente; #44 |
| `orcRank` — ranking de gastos | ORC, FIN, BASE | base/período/centro; ordenar/detalhar | 3445–3460 | `GET /companies/{id}/budgets/{year}/expense-ranking`; `BUDGET_VIEW` | ranking e participação; pendente; #44 |
| `orcPe` — ponto de equilíbrio | ORC, FIN, BASE | ano/período/centro; análise de faturamento/meta | 3463–3482 | `GET /companies/{id}/budgets/{year}/break-even`; `BUDGET_VIEW` | margem de contribuição e divisões sem base; pendente; #44 |
| `orcCen` — cenários | ORC, FUN, BASE | ano/cenário; CRUD, ajustar percentuais e aplicar | `cenarioG` 3486–3492; 3493–3520 | `/companies/{id}/budgets/{year}/scenarios`; `BUDGET_MANAGE` | receita/variável encadeados, pessoal/fixo, PE; pendente; #44 |

### Cadastros — 6 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `empresas` — empresas acessíveis | BASE, IAM | empresa; criar, selecionar | 2006–2025 | hierarquia `/accounting-firms/{firm}/clients/{client}/companies`; `HIERARCHY_MANAGE` | limite/escopo e concessão explícita; parcial persistência; #32/#37 |
| `cadEmp` — dados da empresa | BASE | empresa; editar nome/CNPJ/responsável/município/UF/IM/regime/portal fiscal/padrões | `renderCad` 5444–5493; `salvarEmpresa` | `/companies/{id}/profile`; `COMPANY_PROFILE_MANAGE` | validação/locking; parcial Company; #32/#38 |
| `parc` — clientes e fornecedores comerciais | PAR | busca/papel/situação; CRUD, PF/PJ, contatos/endereço, consulta CNPJ e histórico | 959–1141 | `/companies/{id}/business-partners`; `BUSINESS_PARTNER_VIEW/MANAGE` | CPF/CNPJ, papéis e fallback externo; pendente; #38/#52 |
| `cadContas` — contas bancárias e saldo inicial | BASE, FIN | empresa; CRUD/inativar e restrição de exclusão vinculada | 5444–5502 | `/companies/{id}/bank-accounts`; `BANK_ACCOUNT_MANAGE` | saldo inicial, data e vínculos; pendente; #38 |
| `cadPlano` — categorias financeiras gerenciais | BASE, REL, ORC, FIN | grupo/subcategoria; CRUD/mapeamento natureza/DRE/fixo-variável | `planoPadrao` 453–468; 5496–5506 | `/companies/{id}/financial-categories`; `FINANCIAL_CATEGORY_MANAGE` | exclusão referenciada e mapeamento; pendente; #38. Distinto do AccountingAccount/PlanoQ existente |
| `cr` — centros de resultado | BASE, FIN, CTR, ORC | árvore; CRUD, sugerir, obrigatoriedade | 1969–2003 | `/companies/{id}/result-centers`; `RESULT_CENTER_MANAGE` | subárvore, ciclo, centro obrigatório; pendente; #38 |

### Ajuda e demais — 5 telas

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `faq` — perguntas frequentes | conteúdo estático | busca/grupo; abrir tutorial/área/suporte | 4931–4973 | conteúdo versionado; acesso autenticado | UI, validar links/consulta; #53 |
| `tutor` — tutorial guiado e voz | UI, consultas dos domínios | tutorial/velocidade/voz; iniciar/pausar/avançar | 4736–4928 | frontend mantém apresentação; dados via APIs | UI, sem persistência financeira local; #53 |
| `ctb` — exportação contábil do financeiro | BASE, FIN, PAR | datas/competência, formato e colunas; importar plano, mapear, preview/exportar | `gerarPartidas` 1768–1794; 1708–1810 | `/companies/{id}/accounting-exports`; `ACCOUNTING_EXPORT_MANAGE` | partidas D/C, datas/layout e preview; pendente; #52. Não escreve no Questor |
| `impexp` — importação/exportação | BASE, PAR, FIN, demais domínios | tipo/arquivo; modelo, importar planilhas e exportar | 1940–1949; 5526–5652 | `/companies/{id}/imports`, `/exports`; `DATA_IMPORT/EXPORT` | validação/preview/dedup/atomicidade; pendente; #52 |
| `backup` — backup/restauração e demonstração | todos os domínios, anexos | empresa; gerar/restaurar backup, simular, excluir/inativar empresa | 1950–1961; 5512–5525, 5653–5657 | jobs de exportação/importação autorizados; `TENANT_BACKUP_MANAGE` | isolamento, integridade e round-trip sintético; pendente; #52/#53. Exclusão de array não autoriza delete físico financeiro |

### Administração — 3 telas e autenticação transversal

| Tela / finalidade e dados | Entidades / dependências | Filtros e ações / CRUD | Regra executável / evidência | API e permission propostas | Paridade / estado / Issue |
| --- | --- | --- | --- | --- | --- |
| `admUsers` — usuários e acessos | IAM, BASE | cliente/empresa; convidar, ativar/desativar, conceder/remover acesso, view/edit por tela, senha inicial/recuperação | 6162–6236 e `admin-users.ts` | `/users`, `/invitations`, `/access-grants`; `USER_ACCESS_MANAGE` dentro do escopo | ativo em um cliente não perde acesso aos demais; limites, expiração/replay e escalation; parcial login/grants; #36/#37 |
| `admLog` — registro de atividades | IAM, todos domínios | empresa/usuário/ação/data; consultar | 6237–6249 | `GET /accounting-firms/{firm}/audit-events`; `AUDIT_VIEW` | escopo, paginação e sanitização; pendente; #51 |
| `admClientes` — clientes FINEMP | IAM, Client, Company | cliente; criar/editar, limites de usuários/empresas, bloquear/entrar | 6250–6281 e `admin-users.ts` | hierarquia/admin, limites do Client; permissions explícitas de plataforma | quotas, bloqueio e múltiplas organizações; parcial persistência; #32/#37 |
| Transversal — login/logout/esqueci senha/nova senha/sessão/meus acessos | IAM | e-mail/senha, link seguro, organização/empresa | 6008–6117 | auth já existente; completar convites/reset | enumeração, token uso único/hash, rate limit, revogação; parcial; #36/#37 |

## Catálogo inicial de fórmulas homologadas

Esta seção registra o comportamento do código; não oferece aconselhamento contábil, tributário ou trabalhista. Percentuais configuráveis da referência são parâmetros do produto, não taxas legais universais.

| ID | Fórmula / comportamento a preservar | Fonte / casos de paridade |
| --- | --- | --- |
| F01 | Valor efetivo = pago informado quando existe baixa; senão valor nominal. Saldo = inicial + entradas efetivas − saídas efetivas + transferências recebidas − enviadas até a data. | `valorEf`, `saldoConta` 529–536; sem baixa/com diferença/múltiplas contas |
| F02 | Realizado é agrupado pela data de pagamento; previsto inclui somente abertos pela data de vencimento. | `agregarAno`, `totaisMes` 538–550; baixa fora da competência |
| F03 | Projeção parte do saldo realizado de hoje e inclui vencidos abertos no primeiro dia; depois vencimentos de cada dia, até o limite inclusivo. | `projecao` 551–562; vencido/hoje/futuro |
| F04 | Parcelas: `floor(valor/n*100)/100` nas primeiras n−1; a última recebe o resíduo. Recorrência repete o valor e desloca competência/documento mensalmente. | `salvarLanc` 775–818; 100/3, fim de mês e 2..120 |
| F05 | A baixa encerra o título com valor efetivo; diferença nominal é juros/desconto. O ZIP não cria automaticamente um saldo residual de pagamento parcial. | `confirmarBaixa` 833–839; nominal≠efetivo |
| F06 | DRE operacional: valor nominal com sinal na competência; diferença de baixa no mês do pagamento, em RFIN quando positiva e DFIN quando negativa. Categoria NAO não entra na DRE. | `dreEfeitos` 2128–2132; receita/despesa/NAO e virada de ano |
| F07 | RL=REC+DED; LB=RL+CUS; EBITDA=LB+DCOM+DADM+DPES+DTRI+OUT; EBIT=EBITDA+DEP; LAIR=EBIT+RFIN+DFIN; LL=soma das linhas exceto NAO. Margens dividem pela base da receita líquida usada na tela. | `DRE_EST`, `renderDRE_` 2118–2174; base zero e sinais |
| F08 | Ponte caixa: resultado + ajustes de competência + operacional fora de DRE + reclassificação; investimentos e financiamentos em blocos próprios; diferença contra saldos inicial/final. | `conciliar_` 2222–2241; depreciação/alienação sem caixa duplicado |
| F09 | Prazos são médias ponderadas pelo valor; PMR/PMP usam prazo real quando disponível e concedido caso contrário; ciclo operacional=PME+PMR; financeiro=PME+PMR−PMP; NCG de ciclo=receita diária×ciclo financeiro. | `cicloPeriodo` 2361–2395; sem base, atrasos, antecipações |
| F10 | ACO=receber+estoque; PCO=fornecedores+pessoal+impostos+outros; NCG=ACO−PCO; T=caixa−finCP; CDG=NCG+T. Estoque informado prevalece, senão compras diárias×PME. | `ncgEm` 2933–2949; seis classificações Fleuriet e CP/LP |
| F11 | Taxa anual→mensal por `(1+taxa/100)^(1/12)−1`; taxa implícita por bisseção com 200 iterações no intervalo [0,1]. | `taxaMes` 2494, `taxaImplicita` 2495–2500; taxa zero/limites |
| F12 | SAC arredonda amortização e juros por parcela; última amortiza saldo restante. Parcelas fixas usam taxa implícita quando possível; última parcela ajusta saldo/juros. | `tabelaDiv` 2506–2520; fechamento do principal e datas |
| F13 | Retenções: cada tributo arredondado a 2 casas; ISS integra retenções somente quando retido; líquido=bruto−soma das retenções. | `calcNF` 1191–1196; todos tributos/sem retenção |
| F14 | Índice acumulado = produto de `(1+mensal/100)` menos1; se falta mês, total é nulo e parcial é informado. | `acumulado` 1152–1156; índice negativo e mês ausente |
| F15 | Contrato respeita periodicidade desde início e fim inclusive no mês; vencimento pode ser no mês seguinte e é limitado ao último dia. Cobrança comum deduplica por contrato+competência. | `competencias`, `vencDe`, `gerarCobrancas` 1199–1341 |
| F16 | 13º contratual usa avos inclusivos, valor vigente/periodicidade, novembro/dezembro e resíduo na parcela final. | `valorVigente`, `avos13`, `valor13` 1472–1485 |
| F17 | Orçamento final combina manual sem regra/vínculo, contratos, regras percentuais e auxiliares de pessoal/fixos/variáveis; evita somar manual quando já vinculado. | `orcCalc` 3170–3184; todos caminhos e centros |
| F18 | DRE gerencial agrupa FAT/VAR/FIX/OREC/DEP/FIN/IR e apresenta INV/FINANC/ONAO fora do resultado pelo caixa; MC=FAT+VAR; LL e CX seguem `GER_EST`. | `classeConta`, `gerencial`, `GER_EST` 3198–3218 |
| F19 | Cenários: VAR varia com FAT e fator próprio; pessoal/fixos separados; financeiro negativo reajustado; PE=−(FIX+min(0,FIN))/margem de contribuição quando positiva. O KPI PE usa também OREC: preservar os dois contextos. | `cenarioG` 3486–3492 e `valorKpi` 5051–5064 |
| F20 | CLT/APR: provisão mensal de 13º=s/12, férias=s/12, terço=s/36 e encargos sobre a base; modo caixa concentra férias/13º. APR usa FGTS2%; PRO/EST/PJ têm componentes próprios. Quantidade, meses e dissídio afetam valores. | `custoPessoa` 3570–3596; cada regime e modo |
| F21 | Benefício por dia×dias; VT desconta6% do salário básico de CLT/APR com piso0; demais por mês. Componentes são arredondados no fim da agregação mensal por quantidade. | mesmo método; benefício padrão/override/desativado |
| F22 | Estoque ordena data e, no mesmo dia, maior quantidade primeiro (entradas antes de saídas). Entrada recalcula média usando saldo anterior não negativo; custo ausente/zero reaproveita média; saída usa média vigente; quantidade arredondada a3 casas. | `estProcessar` 3938–3950; média, zero e retroativo |
| F23 | Validação bloqueia qualquer saldo mínimo negativo dos produtos afetados, inclusive ao editar/remover uma origem passada. Valor de estoque=max(saldo,0)×média. ABC por receita de saídas dos últimos365 dias e acumulado80%/95%. | `estValidar`, `curvaABC` 3953–3988 |
| F24 | Markup divisor=1−(impostos+comissão+taxas+fixas+lucro)/100; preço=custo/divisor, se positivo; margem real desconta custos e percentuais do preço. | `renderEstPreco` 4197–4213; divisor0/negativo |
| F25 | Depreciação mensal=valor×(1−residual%)/vida em meses, arredondada a2 casas; inclui mês inicial e mês da baixa. Acumulada limitada à base depreciável e vida útil. | `depMensal`, `depBem`, `depAcum` 4557–4567 |
| F26 | Valor contábil=arredondar(valor−depreciação acumulada); ganho/perda=valor da baixa−valor contábil; efeitos na DRE incluem DEP negativo e resultado da alienação em OUT. | `valorContabil`, `efeitosBens` 4568–4575 |
| F27 | Balanço operacional: caixa+receber+estoque+outros circulantes; imobilizado líquido e ajustes; pagar e amortizações de dívidas CP/LP; PL=ativo−passivo. Origens já ligadas a bens não são contadas novamente. | `balancoEm` 4219–4233 |
| F28 | Meta: progresso=(atual−base)/(alvo−base), limitado a0..1,2; sentido up/down; comparação temporal tolera15p.p.; estados atingida/não atingida/sem dado/caminho/risco. | `situacaoMeta` 5067–5074 |
| F29 | Conciliação aceita diferença de valor menor que0,011; pagos da mesma conta em ±3dias; abertos em ±15dias; pagos têm prioridade, depois menor distância. | `candidatos` 1637–1642; usados excluídos e ambiguidades |
| F30 | CRM: ticket=soma dos valores das ganhas/quantidade; ciclo comercial=média dos dias entre criação e ganho; conversão=ganhas/(ganhas+perdidas); anualização multiplica valor mensal por12. Pipeline previsto pondera valor×probabilidade/100 e traz atrasadas para o mês atual. | `valorOp`, `anualOp`, `renderCrmInd` 4306–4309 e4518–4541 |
| F31 | Funil usa a maior etapa alcançada no histórico; MRR soma valor/periodicidade de contratos de receita vigentes por mês; churn usa encerramentos dos últimos365dias e a base definida no código. | `renderCrmInd` 4518–4541; ganho/perda e contrato encerrado |
| F32 | PE operacional usa fixos líquidos de outras receitas e financeiro negativo; PE com lucro-alvo divide por margem de contribuição menos lucro-alvo; margem de segurança=faturamento−PE. | `renderOrcPe` 3463–3482; denominação não é idêntica ao PE do cenário F19 |
| F33 | Indicadores do balanço: corrente=AC/PC; seca=(AC−estoque)/PC; imediata=caixa/PC; giro líquido=AC−PC; endividamento=P/A; composição=PC/P; imobilização=ANC/PL. Denominador com módulo<=0,004 resulta nulo; faixas de status são as do código. | `renderBal` 4234–4277 |
| F34 | NF-e de compra rateia o custo unitário por `vNF/vProd` quando ambos positivos; classifica entrada/saída pelo documento do emissor versus Empresa; vincula produto por código do fornecedor, EAN, código próprio ou nome. | `prepararNfe` 4122–4146; duplicidade por chave e rateio |
| F35 | Exportação contábil usa regime caixa ou competência; NAO segue caixa; provisão no último dia da competência; baixa trata diferença em contas de juros/desconto configuradas; transferências geram débito destino/crédito origem. Erros de mapeamento entram no preview e histórico é limitado a200caracteres. | `gerarPartidas` 1768–1794 |
| F36 | Diagnóstico compara saldos/fluxos e ponte DRE com tolerância<0,01; diferenças de parcelas/NF-e usam0,05. Confere F+V, operação+investimento+financiamento, soma por centro, vínculos órfãos, duplicatas de cobrança, estoque negativo, patrimônio, CRM e conciliação de extratos. | `verificacoes` 2277–2342; cada ramo precisa de fixture positiva/negativa |

## Integrações e limites observados

- Consulta cadastral CNPJ: BrasilAPI, função `buscarCNPJ` 1085–1101, timeout9s, preenchimento manual continua disponível em falha.
- Índices BCB: IPCA433, IGP-M189, INPC188, INCC192, IGP-DI190; últimos48 registros por série, timeout12s, falha por série preserva sucessos e permite entrada manual (`atualizarIndicesBCB`, 1161–1178). Criar port, cache e adapter com testes; não acoplar o domínio ao HTTP.
- Portal NFSe é link de navegação; o ZIP prepara dados e registra emissão, sem implementar emissão fiscal governamental.
- NF-e é leitura/importação de XML, não comunicação autorizadora com SEFAZ; parser backend deve bloquear entidades externas/DTD.
- Exportação contábil gera arquivo/partidas e preview. Nenhuma escrita no Questor é autorizada.
- Anexos: referência aceita até15MB por arquivo; formatos observados incluem imagem/PDF/XML/TXT/OFX; imagens grandes podem ser reduzidas. Backend valida tamanho, assinatura/MIME/extensão e escopo privado (#51).
- Limites de usuários/empresas e bloqueio do cliente são comportamento do produto. O modelo relacional deve representá-los sem copiar o boolean universal `superadmin`.
- PWA, voz, layout, menus, modais, impressão e formatação ficam no consumidor. O backend fornece dados e direitos necessários.
- Concorrência da referência usa versão de seção e mescla de três vias por registro (`mesclar` 5931–5942). O backend preservará a proteção contra sobrescrita silenciosa com `@Version`/versão do DTO e409; conflitos não serão resolvidos por last-write-wins implícito.

## Estratégia de golden master / paridade

1. Fixar o hash da referência e extrair somente funções de cálculo selecionadas pelo AST; não inicializar o aplicativo nem seus adapters.
2. Fornecer dados sintéticos mínimos, datas/relógio explícitos e dependências em memória.
3. Capturar entrada e saída do JavaScript executável, incluindo casos de fronteira e ausência de base.
4. Portar cada caso para testes Java com `BigDecimal`, separando normalização/rounding da fórmula.
5. Comparar todos os campos materiais, sinais, datas, agrupamentos e estados, além do total final.
6. Executar PostgreSQL/Testcontainers para persistência, atomicidade, idempotência, concorrência e isolamento; golden master sozinho não cobre esses riscos.

### Escalas e comportamento numérico

**DECISÃO:** entradas monetárias em decimal textual, persistência `NUMERIC`, cálculo oficial em `BigDecimal`. Quantidades de estoque usam3 casas; valores monetários apresentados/lançamentos usam2; taxas e divisões intermediárias têm precisão maior e arredondamento explícito por algoritmo.

`Math.round` em JavaScript e ponto flutuante binário podem diferir de `HALF_UP`, especialmente em empates negativos e valores como1,005. Não substituir silenciosamente. O oracle deve registrar o resultado legado; testes Java devem distinguir equivalência decimal material de estabilização numérica deliberada. Toda divergência será documentada com entrada, saída legada, saída decimal e motivo. A política final de cada método será parte do PR correspondente.

### Conjunto mínimo por domínio

- Financeiro: nominal/efetivo, juros/desconto, vencido, parcelas100/3, recorrência e último dia do mês, transferência, datas em anos diferentes.
- Reporting: competência versus caixa, NAO, depreciação/alienação, base zero, centro/subárvore, NCG/Fleuriet e balanço fechado.
- Contratos: todas retenções, índice ausente/negativo, periodicidade, fevereiro/ano bissexto, 13º, reajuste histórico e cobrança repetida.
- Dívidas: SAC, fixa, taxa zero/anual/implícita, saldo final/resíduo, vencidos e parcelas já geradas/pagas.
- Orçamento/pessoal: precedência/vínculos, todos regimes/modos, avos, dissídio, VT, benefícios, cenários e base zero.
- Estoque: múltiplas entradas, saídas, mesma data, custo zero, retirada retroativa, custo médio e bloqueio negativo.
- Bens: mês inicial/baixa, residual, vida útil encerrada, ganho/perda e ausência de dupla contagem.
- CRM/planejamento: conversão repetida, ganho/perda/reabertura, atividades, progresso/sentido/prazo e snapshots.
- Acesso: 401/403, Cliente/Empresa/Contabilidade cruzados, view/edit, quotas, usuário multi-organização, revogação, reset expirada/replay e upload inválido.

## Encadeamento de entregas

| Ordem | Bloco | Issue / dependência |
| --- | --- | --- |
| 1 | Inventário integral e oracle | #35 |
| 2 | Convites/ativação/reset; hardening e grants | #36, #37 |
| 3 | Administração SaaS | #32; persistência #30 e escopos #33 já concluídos |
| 4 | Cadastros financeiros e operações | #38, #39 |
| 5 | Bancos/conciliação | #40 |
| 6 | Reporting operacional; DRE contábil preservada | #41 |
| 7 | Contratos | #42 |
| 8 | Endividamento | #43 |
| 9 | Orçamento | #44 |
| 10 | Pessoal | #45 |
| 11 | Estoque/NF-e | #46 |
| 12 | Bens | #47 |
| 13 | CRM | #48 |
| 14 | Planejamento | #49 |
| 15 | Questor/porta ERP/Connector e classificação | #50, #27 e incrementos de PlanoQ |
| 16 | Anexos/auditoria e adapters externos/importações | #51, #52; infraestrutura reutilizável pode antecipar dependências |
| 17 | OpenAPI/client e staging sintético | #53; contrato acompanha cada PR |
| 18 | Paridade/isolamento/performance completos | critérios transversais de todas as Issues |

Issues de domínio são frentes de entrega; serão divididas em incrementos de cálculo, persistência e API quando a implementação exigir. Nenhum PR deve conter um domínio inteiro apenas para encerrar a Issue.

## Critério de conclusão

### Evidência inicial do oracle

O runner em `tools/reference` verificou 18 casos sintéticos contra o hash fixado: retenções com/sem ISS retido; média ponderada, ordenação no mesmo dia e histórico negativo de estoque; depreciação no mês/apos baixa e acumulada; SAC com resíduo e taxa implícita zero; CLT/APR; custo fixo periódico; prazo ponderado e base vazia; diferença de baixa na DRE; dois casos de arredondamento legado.

Esses resultados comprovam a captura da referência. Ainda não significam que os domínios correspondentes já tenham sido implementados no backend. A porta Java deve consumir os mesmos casos e acrescentar testes PostgreSQL e de segurança.

Cada tela só muda para **concluída** após regra portada, dados persistidos quando necessários, endpoint documentado, autorização server-side, paridade sintética, testes pertinentes e CI verde. Menus renderizados, entidades isoladas ou endpoints vazios não constituem paridade funcional.

**PONTOS ABERTOS de ambiente:** provedor SMTP e storage de produção, DNS/TLS, identidade/provisionamento de Connector e secrets de cada fonte são configuráveis; não bloqueiam implementações locais e testes com doubles. Acesso operacional real só será necessário na homologação da integração correspondente.
