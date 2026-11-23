# GAP CONTÁBIL — FINEMP

Auditoria da Issue #433 · 23/11/2026

## 1. Base, alcance e conclusão

**FATO CONFIRMADO.** A `main` consultada no GitHub é `2c184618f776cf94908db759ceee9eacc273bde7`, merge do PR #431, de runtime automático do Connector no Windows. A leitura do código foi feita em uma cópia desse commit. Foram lidos o `HANDOFF_CONTABIL.md` completo, seus anexos, a Issue #433 e seus comentários, além das Issues #50, #310 e #432. A #433 estava aberta e sem comentários. [Main auditada](https://github.com/matos795/Finemp/tree/2c184618f776cf94908db759ceee9eacc273bde7) · [Issue #433](https://github.com/matos795/Finemp/issues/433)

Arquivo de referência recebido em `C:/Users/Secretaria/Downloads/HANDOFF_CONTABIL.md`. SHA-256 `1A838249158E4DA13E1CC70475A23A279AB3EB8BCA041CEEB47EDA73DF95CC11`.

**FATO CONFIRMADO.** Existem identidade, hierarquia SaaS, autorização, movimentos contábeis sincronizados, classificação para DRE, DRE contábil e Connector. O caminho de go-live pode reutilizar essas fundações. A Central de Fechamento completa exige ampliar dados de origem e criar domínio de trabalho contábil preparado, documentos, aprovações e controles próprios.

**FATO CONFIRMADO.** A principal lacuna ocorre antes das telas. O contrato sincronizado não carrega saldo inicial, documento, flag de encerramento, centros de custo nem classificação patrimonial. Contrapartida não é um campo do contrato. O plano é formado pelas contas encontradas nos movimentos importados. Isso impede afirmar que existe um plano completo, um balancete completo ou um Diagnóstico de Bases completo.

**PROPOSTA.** Entregar primeiro o fluxo vertical de consulta e classificação contábil existente, com cobertura explícita. A primeira evolução canônica pode ser especificada em paralelo, mas sua implementação depende da identificação dos dados reais da origem e das decisões indicadas neste documento.

Os rótulos **FATO CONFIRMADO** referem-se ao código ou a evidência identificada. **FATO DO PROTÓTIPO** refere-se ao relato do handoff. O HTML contábil e a planilha ConsA não foram fornecidos nesta solicitação, portanto fórmulas, layouts e comportamentos descritos no handoff não receberam uma segunda verificação contra esses artefatos. **PROPOSTA** identifica backlog, prioridade e complexidade. **PONTO ABERTO** conserva decisões pendentes.

Esta foi uma inspeção estática de código, migrations, contratos e testes existentes. Os testes e o ambiente de produção não foram executados nesta auditoria. As evidências de homologação operacional citadas adiante provêm do registro da #310.

## 2. Fundações verificadas no código

Os identificadores E1–E12 referenciam evidências com links na seção 9.

| Fundação | Evidência e comportamento confirmado | Limite relevante |
|---|---|---|
| AccountingFirm → Client → Company → AccountingSource | `Client.accountingFirm` obrigatório, `Company.client`, `AccountingSource.company` obrigatório. APIs administrativas verificam a cadeia de pertencimento. E1 | `Company.client` permite nulo para empresas legadas/não atribuídas. A seleção operacional deve tratar essa situação explicitamente. |
| Login e sessão | `AuthenticationController`, sessão existente, `/auth/me`, CSRF e logout da configuração Spring Security. E2 | A área contábil usa a identidade atual. Perfis permissivos do artefato Claude não são uma política de acesso do FINEMP. |
| Permissões de plataforma | `ACCOUNTING_FIRM_CREATE`, `ACCOUNTING_FIRM_VIEW`, `UNASSIGNED_COMPANY_ASSIGN`, `PLATFORM_USER_MANAGE`, `SYSTEM_ADMIN`. E2 | SYSTEM_ADMIN global deriva acesso efetivo. Operadores comuns dependem de grants vigentes. |
| Permissões de contabilidade | `HIERARCHY_MANAGE`, `USER_ACCESS_MANAGE`, `ACCOUNTING_SOURCE_MANAGE`, `CONNECTOR_MANAGE`. E2 | Pertencer ao escritório não concede automaticamente acesso a movimentos ou DRE de todas as Companies. |
| Permissões de Company | `ACCOUNTING_MOVEMENT_VIEW`, `ACCOUNT_CLASSIFICATION_VIEW`, `ACCOUNT_CLASSIFICATION_MANAGE`, `DRE_VIEW`, `QUESTOR_SYNC`, entre outras. Gate no backend e método fresh para operações que o utilizam. E2 | `CompanyPermissionAuthorizationService` exige scope ALL no caminho comum. Scope de autorização não representa cadastro nem rateio de centro de custo contábil. |
| Seleção hierárquica | `/auth/organization-access` lista escritórios acessíveis. APIs paginadas listam clientes, Companies e fontes. A tela administrativa já contém seletores. E1/E2/E10 | GET de clientes/Companies exige `HIERARCHY_MANAGE`; GET de fontes exige `ACCOUNTING_SOURCE_MANAGE`. `/auth/me` lista Companies, mas sua projeção não inclui Client/AccountingFirm. Falta definir consulta hierárquica para analista sem gestão cadastral. |
| AccountingAccount | Persistido com source, externalAccountId, accountKey e description; unicidade source + externalAccountId. E3/E7 | Sem parentId, natureza patrimonial, grupo/subgrupo, flag sintética/analítica ou status da conta de origem. Não há leitor dedicado de todo PLANOESPEC. |
| AccountingMovement | Persistido por conta/source com sourceEntryKey, side, accountingDate, amount e history. Valor `BigDecimal`/`NUMERIC(19,4)`. E3 | Identidade única source + sourceEntryKey + side. Não suporta representar genericamente várias partidas distintas do mesmo lado sob a mesma chave. |
| Classificação de contas | Relação individual conta → classificação DRE/DRE ABERTA/EBITDA; catálogo `DreClassification`; PUT verifica que a conta pertence à Company. E4 | O catálogo é global no esquema atual, sem escopo de escritório/cliente. Sem prefixos, patrimonial, vigência, histórico de versões ou auditoria específica no serviço de classificação atual. Não prova a política futura de modelos gerenciais. |
| DRE contábil | `/api/companies/{companyId}/dre` consulta AccountingMovement no PostgreSQL, associa classificação e soma por trio DRE/DRE ABERTA/EBITDA. Informa quantidade de contas não classificadas. E5 | Exclui da soma classificações incompletas. Não há filtro de encerramento, saldo inicial, balancete, margens oficiais ou drill-down por conta/contrapartida nesse contrato. A consulta agrega todas as fontes da Company. |
| Questor/Firebird read-only | SQL parametrizado sobre LCTOCTB + PLANOESPEC, lados débito/crédito por UNION ALL. Sessão readOnly, REPEATABLE_READ, rollback ao fechar. Identidade observada validada antes da publicação. E6 | O código não comprova permissões do usuário de cada instalação. Homologação e credenciais somente leitura continuam sendo controle operacional. |
| FINEMP Connector | Autenticação machine, outbound HTTPS, claim, lease/heartbeat, fencing, complete/fail/result, confirmação por recibo e leitura sem sobreposição. Runtime headless e scripts Windows. E8 | Heartbeat de tentativa não comprova presença contínua de um Connector ocioso. Manager local e painel de presença/versão são escopo aberto da #432. |
| SyncRun e publicação | Request idempotente, histórico/tentativas, lock por fonte, autorização fresh, validação de lote e publicação atômica no PostgreSQL. Reexecução igual não duplica. E7 | Executor/publicador suportam somente INITIAL. INCREMENTAL e RECONCILIATION existem no enum, mas são rejeitados como UNSUPPORTED_MODE. Dados já publicados que divergirem causam DATA_CONFLICT. Não há política implementada de atualização/exclusão da origem. |

**FATO CONFIRMADO EM REGISTRO OPERACIONAL.** O comentário de 18/11/2026 na #310 registra homologação real do fluxo de leitura, identidade, HTTPS, publicação e reexecução idempotente. A Issue permanece aberta para aceite operacional restante. O PR #431 implementa o runtime Windows, mas seu merge isolado não comprova encerramento desse aceite. A #432 propõe Manager local separado do runtime. [Registro da #310](https://github.com/matos795/Finemp/issues/310#issuecomment-6988667419) · [Issue #432](https://github.com/matos795/Finemp/issues/432)

## 3. Cobertura do modelo canônico

| Dado exigido pelo handoff | Situação na main | Consequência e evolução necessária |
|---|---|---|
| Conta, código reduzido/classificado e descrição | `externalAccountId`, `accountKey`, `accountDescription` no import; reader compõe `CLASSIFCONTA + '.' + CONTACTB`. | Preservar os identificadores de origem. Não deduzir um layout de exportação a partir do texto do código. |
| Plano completo, inclusive contas sem movimento | Contas são criadas a partir dos descritores do lote de movimentos. | Criar leitura/catalogação completa se o aceite exigir todas as contas. O plano visível atual corresponde ao universo importado. |
| Chave do lançamento | `sourceEntryKey` persistida e devolvida no endpoint. | Contagem de lançamentos deve distinguir source/chave de quantidade de movimentos. Validar granularidade de múltiplas partidas antes de ampliar o contrato. |
| Data, D/C, valor e histórico | Presentes no reader, contrato, banco e API. | Reutilizar com precisão decimal. A apresentação em duas casas do protótipo não autoriza arredondar o armazenamento de quatro casas. |
| Saldo inicial | Ausente do contrato, das entidades contábeis e do reader. | Definir data-base, fonte e continuidade histórica. `opening_balance` de conta bancária financeira não é saldo de conta contábil. Saldo inicial desconhecido não pode virar zero. |
| Contrapartida | Ausente como campo ou relação. | A mesma source/chave permite correlacionar lados simples como hipótese técnica. Não garante contrapartida completa, rateios ou “múltipla”. Validar na origem e ampliar identidade/contrato antes de usar em documentos, adiantamentos e drill-down. |
| Documento | Não há campo no ImportedAccountingMovement, AccountingMovement, DTO de leitura ou SELECT. | Investigar campo/tabela e cardinalidade na origem. Histórico não deve ser tratado como número de documento. |
| Encerramento | Sem flag, valor separado ou filtro no reader/DRE. | Ingestão precisa distinguir encerramento e preservar sua proveniência antes de excluí-lo de análises ou reconciliar DRE × balanço. |
| Movimento mensal D/C | Data, lado e valor permitem agregação dos movimentos disponíveis. Não há endpoint contábil mensal por conta para essa visão. | ADAPTAR consulta server-side. O agregado só representa a janela carregada e não produz automaticamente saldos nem exclusão de encerramentos. |
| Patrimonial AC/ANC/PC/PNC/PL/RES, subgrupo e natureza | Ausentes de AccountingAccount e da classificação atual. | Criar classificação patrimonial e contrato de estrutura, após decisão sobre escopo, vigência e classificação original. Não usar prefixo “1” da regra de sinal da DRE como ontologia patrimonial. |
| Centros de custo e rateios D/C | Ausentes do contrato sincronizado. | Há ResultCenter no financeiro e CostCenterScope em acesso. É necessário definir vínculo/código Questor, rateio por lado e relação com ResultCenter antes de reutilizar cadastros. |
| Lançamentos preparados | Ausentes como domínio contábil gravável com rascunho/aprovação/exportação. | Criar domínio próprio de preparação e proveniência. FinancialEntry, Settlement e recorrência financeira possuem outra semântica. |
| Participante/fornecedor | Nenhum vínculo contábil canônico com participante. BusinessPartner existe no financeiro. | Definir identidade e eventual vínculo com parceiro existente. Heurística de nome/CNPJ embutido no nome da conta não é relacionamento garantido. |
| Cobertura da base | SyncRun registra período pedido, status, contagens e tentativas. | A maior data de movimento não comprova mês completo, especialmente em meses vazios. Falta contrato de cobertura contínua e atualização/exclusão da origem. |

**FATO CONFIRMADO.** A ampliação precisa atravessar reader, snapshot, `ImportedAccountingMovement`, encoder do agente, decoder estrito, validador, migrations, publicador e DTOs de leitura. O decoder rejeita campos desconhecidos. Alterar apenas o banco ou a tela não entrega a informação de ponta a ponta. A unicidade `(source, sourceEntryKey, side)` precisa ser reavaliada tecnicamente caso a origem forneça múltiplas partidas no mesmo lado. E3/E6/E7/E8.

**FATO CONFIRMADO.** A DRE atual aplica débito positivo/crédito negativo em contas cujo código começa com “1” e inverte os demais códigos. O handoff declara D − C para razão e ajustes de natureza na exibição. São contratos que precisam ser reconciliados por casos de teste. Não aplicar a função de sinal da DRE ao futuro balancete sem validação. E5.

**FATO CONFIRMADO.** O frontend contábil atual soma subtotais por grupos usando `number` em `group-dre-lines.ts`. Os totais das linhas vêm do backend. Para a entrega contábil autoritativa, a proposta é fornecer também agregações necessárias no backend e fazer a UI apenas apresentá-las. E10.

## 4. Catálogo de endpoints existentes

As abreviações abaixo são usadas na matriz. “Nenhum” significa que não foi encontrado endpoint para a função da tela no código auditado. Endpoints citados como base não oferecem automaticamente paridade com a tela.

| ID | Endpoint existente | Cobertura e restrição |
|---|---|---|
| A | `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`, `GET /api/auth/organization-access`, `GET /api/auth/access-catalog` | Sessão, Companies permitidas, escritórios e catálogo de acesso. E2. |
| H | `GET /api/accounting-firms/{firmId}/clients`; `GET /api/accounting-firms/{firmId}/clients/{clientId}/companies` | Hierarquia paginada, requer HIERARCHY_MANAGE. E1. |
| S | `GET /api/accounting-firms/{firmId}/clients/{clientId}/companies/{companyId}/accounting-sources` | Fontes paginadas, requer ACCOUNTING_SOURCE_MANAGE. E1. |
| Q | `GET/POST /api/accounting-sources/{sourceId}/sync-runs`; `GET/DELETE .../sync-runs/{runId}`; `GET .../{runId}/events`; `GET .../{runId}/attempts` | QUESTOR_SYNC, flags próprias, período e idempotência. DELETE cancela somente REQUESTED. E7. |
| K | `GET/POST /api/admin/accounting-firms/{firmId}/connectors`; `POST .../{credentialId}/rotate`; `DELETE .../{credentialId}` | Administração de credenciais. Não é API de presença online do runtime. E8. |
| C | `GET /api/companies/{companyId}/accounting-accounts/classifications`; `GET /api/dre-classifications`; `PUT /api/companies/{companyId}/accounting-accounts/{accountId}/classification` | Lista de contas importadas, catálogo e classificação individual. Lista sem paginação server-side. E4. |
| M | `GET /api/companies/{companyId}/accounting-movements?startDate=AAAA-MM-DD&endDate=AAAA-MM-DD` | Movimentos por Company e período. Retorna lista completa da janela, sem paginação ou filtro server-side de conta/contrapartida. E3. |
| D | `GET /api/companies/{companyId}/dre?startDate=AAAA-MM-DD&endDate=AAAA-MM-DD` | Linhas agrupadas classificadas e unclassifiedAccountCount. E5. |
| P | `GET /api/companies/{companyId}/profile` | Identidade básica da Company. Não contém toda INFO do pacote. E11. |
| T | `GET/POST /api/companies/{companyId}/action-plans`; `GET/POST /api/companies/{companyId}/planning/meetings` | Ações/reuniões existentes, sem equivalência completa com coleção ata do handoff. E11. |
| F | `/api/companies/{companyId}/financial-reports/operational-dre`, `/financial-reports/diagnostics`, `/financial-periods/{period}`, `/result-centers`, `/assets`, `/debts`, `/service-invoices` | Domínios financeiros/gestão já existentes. São referências de integração, não endpoints contábeis substitutos. E11. |

O protocolo machine existente usa `/api/connector/v1/session`, `/work/claim`, `/work/{attemptId}/heartbeat`, `/work/{attemptId}/complete`, `/fail` e `/result`. Sua credencial e suas chamadas pertencem ao Connector. O navegador usa A/H/S/Q/K conforme autorização. E8.

## 5. Matriz de todas as telas

Todas as prioridades P0/P1/P2 abaixo são **PROPOSTAS DO HANDOFF**. S = adaptação pequena de contrato existente; M = evolução com contrato/persistência/testes próprios; L = domínio novo ou cadeia multissistema. São estimativas relativas, sem compromisso de prazo. “Aberto” indica dependência da seção 7.

### 5.1 Central de Fechamento e Sucesso do Cliente

| TELA | PRIORIDADE DO HANDOFF | REUTILIZAR / ADAPTAR / CRIAR / PONTO ABERTO | BACKEND EXISTENTE | ENDPOINT EXISTENTE | DADOS AUSENTES | DOMÍNIO NOVO NECESSÁRIO | DEPENDÊNCIAS | COMPLEXIDADE S/M/L |
|---|---|---|---|---|---|---|---|---|
| Cockpit `cfcock` | P1 proposta | CRIAR | SyncRun como insumo | Q parcial; nenhum cockpit | Estados dos nove passos, pendências | Agregação de fechamento | Documentos, preparados, fiscal/folha, checklist; O18 | M |
| Roteiro `cfrot` | P1 proposta | CRIAR + PONTO ABERTO | Identidade/auditoria genéricas | A; nenhum roteiro | Responsáveis, prazos, revisão, etapas | Workflow por competência | O1, O13, O18; links às etapas | M |
| Checklist `checklist` | P0 proposta | CRIAR + PONTO ABERTO | Acesso/auditoria; T parcial | Nenhum checklist | 33 itens, evidências, assinatura, validações | Checklist contábil e revisão | Diagnóstico, saldos, suportes, Ata; O1/O15/O17 | L |
| Suportes dos saldos, bloco `checklist` | P1 proposta | CRIAR + PONTO ABERTO | Nenhum suporte contábil | Nenhum | Arquivo/caminho, saldo comprovado, conciliação | Evidência documental por saldo | Saldos, storage; O8/O15 | L |
| Documentos e Classificação `cfdocs` | P0 proposta | CRIAR + PONTO ABERTO | C/M como insumos | C/M; nenhum documento preparado | Arquivos, extração, participante, regras, partidas | Documentos, extração, classificação de documento | Preparados, plano/CC, O7/O8/O9/O14/O15 | L |
| Base de Classificação `cfbase` | P1 proposta | CRIAR + ADAPTAR | C/M | C/M parciais | Contrapartida, fornecedor, regras aprendidas, templates | Sugestões e regras documentais | Canônico ampliado, documentos, preparados; O7/O9 | L |
| Recorrentes e Planilhas `cfrec` | P0 proposta | CRIAR + PONTO ABERTO | Recorrência financeira como referência de engenharia | Nenhum equivalente contábil | Modelos D/C, geração por competência, imports | Recorrência contábil preparada | Preparados, plano, CC; O7/O9/O15 | L |
| Antecipadas `cfantecip` | P1 proposta | CRIAR + PONTO ABERTO | M/C como insumos | M/C parciais | Saldo do ativo, cobertura, apropriações | Apropriação contábil | Saldos, contrapartida, preparados, O9/O14 | L |
| Adiantamentos `cfadiant` | P2 proposta | CRIAR + PONTO ABERTO | M; parceiros financeiros separados | M parcial | Vínculo participante, saldo/baixas, documentos posteriores | Conciliação contábil de adiantamentos | Canônico, preparados; O9/O14 | L |
| Pré-fechamento `cfpre` | P0 proposta | ADAPTAR + CRIAR | DRE D e movimentos M | D/M parciais | Balancete, saldos, seis meses comparáveis, preparados | Projeção contábil pré-importação e alertas | Canônico, patrimonial, preparados, reconhecimento de lotes; O3/O6/O9/O12 | L |
| Lotes e Exportação `cflanc` | P0 proposta | CRIAR + PONTO ABERTO | Padrões de idempotência/auditoria | Nenhum lote de saída | Aprovação, lote, arquivo, CC, recibo de importação | Preparados, lote e exportador | O1/O6/O7/O9/O15; layout oficial | L |
| Lalur/Lacs `cflalur` | P1 proposta | CRIAR + PONTO ABERTO | DRE D como insumo parcial | D; nenhum Lalur | Regime, Parte A/B, prejuízo, deduções, provisões | Apuração fiscal homologada | O10/O11/O14; saldos/pré-fechamento/lotes | L |
| Lucro Presumido `cfpres` | P1 proposta | CRIAR + PONTO ABERTO | DRE D parcial | D; nenhum validador | Regime, atividades, declarado, regras por vigência | Validação fiscal | O10/O14/O15; classificação e origem do declarado | L |
| ISS/PIS/COFINS `cfimp` | P1 proposta | CRIAR + PONTO ABERTO | DRE D parcial | D; nenhum validador | Créditos/contrapartidas, declarado, municípios, vigência | Validação fiscal | O10/O14/O15; regime e saldos | L |
| Folha `cffolha` | P1 proposta | CRIAR + PONTO ABERTO | D/M parciais | D/M; nenhum DP/folha | Totais, colaboradores, provisões, relatórios | Importação/validação DP e ajustes | O8/O10/O11/O14/O15; canônico e preparados | L |
| Períodos Congelados `cfcong` | P0 proposta | CRIAR + PONTO ABERTO | SyncRun; fechamento financeiro distinto | Q parcial; nenhum congelamento contábil | Snapshot mensal, diferenças, aceite e corte | Revisão de base contábil congelada | O1/O3/O4/O12; canônico mensal | L |
| Diagnóstico `diag` | P0 proposta | ADAPTAR + CRIAR + PONTO ABERTO | D/M/Q; diagnóstico financeiro distinto | D/M/Q parciais | Saldos, patrimonial, encerramento, inconsistências ERP | Diagnóstico de integridade contábil | Canônico ampliado, De-Para; O3/O5/O12 | L |
| De-Para `depara` | P0 proposta | ADAPTAR + CRIAR + PONTO ABERTO | Classificação individual C | C | Patrimonial, prefixos, original, vigência, sugestão | Regras de mapeamento gerencial | Plano completo; O2/O7/O14 | L |
| Volumetria `cfvol` | P2 proposta | ADAPTAR + CRIAR + PONTO ABERTO | Chave/lado/data em M | M parcial; nenhum agregado de volumetria | DP mensal, contrato, séries completas | Volumetria comercial | O11/O16; cobertura e identidade do lançamento | L |
| Sucesso do Cliente `sucesso` | P2 proposta | ADAPTAR + CRIAR + PONTO ABERTO | ActionPlan/PlanningMeeting existentes | T parcial | Cobranças, andamentos, e-mail, agregado entre empresas | Extensão de ações e acompanhamento | O1/O17/O19; reutilizar gestão existente | L |

### 5.2 Pacote de Consolidação — todas as 20 folhas/telas descritas

A estrutura de 59 folhas do modelo ConsA é uma fonte citada pelo handoff. As 20 telas abaixo são as detalhadas na seção 7. Nenhum endpoint específico do pacote foi encontrado. A eventual promoção de P2 para P0 para um cliente específico depende de O20.

| TELA | PRIORIDADE DO HANDOFF | REUTILIZAR / ADAPTAR / CRIAR / PONTO ABERTO | BACKEND EXISTENTE | ENDPOINT EXISTENTE | DADOS AUSENTES | DOMÍNIO NOVO NECESSÁRIO | DEPENDÊNCIAS | COMPLEXIDADE S/M/L |
|---|---|---|---|---|---|---|---|---|
| Estrutura e Mapa `gcmapa` | P2 proposta | CRIAR + PONTO ABERTO | Nenhum motor de pacote | Nenhum | Modelo versionado e cobertura | Catálogo de pacote | ConsA; O20 | M |
| Balanço BS `gcbs` | P2 proposta | CRIAR | M/C parciais | M/C como insumo | Saldos, patrimonial, códigos | Balanço do pacote | Canônico, De-Para, O20 | L |
| Resultado PL `gcpl` | P2 proposta | ADAPTAR + CRIAR | D | D parcial | Funções, códigos e fórmulas PL | Projeção PL do pacote | De-Para, O20 | L |
| Custos por Natureza EXPS `gcexps` | P2 proposta | ADAPTAR + CRIAR | D/M | D/M parciais | Natureza do pacote, contrapartidas | Mapeamento EXPS | Canônico, O20 | L |
| Patrimônio Líquido SS `gcss` | P2 proposta | CRIAR | M | M parcial | Saldos e mutações de PL | Demonstração de mutações | Patrimonial, O20 | L |
| Imobilizado TFA/GROSS1 `gctfa` | P2 proposta | ADAPTAR + CRIAR | M e módulo de bens | M; F assets auxiliar | Classes/códigos, conciliação contábil bruto/líquido | Projeção TFA | Saldos, vínculo bens-contas, O20 | L |
| Intangível IFA `gpifa` | P2 proposta | CRIAR | M | M parcial | Classes, saldos e fórmulas | Projeção IFA | Patrimonial, O20 | L |
| Empréstimos BB `gcbb` | P2 proposta | ADAPTAR + CRIAR | M e dívidas financeiras | M; F debts auxiliar | Saldos CP/LP e códigos | Projeção BB | Saldos, conciliação com dívidas, O20 | L |
| Fluxo indireto CF `gccf` | P2 proposta | CRIAR | M/D | M/D como insumo | Variações patrimoniais e ajustes não caixa | Fluxo indireto contábil | Balanço, pacote, O20 | L |
| Informações CF_INFO `gpcfinfo` | P2 proposta | CRIAR | M | M parcial | Aplicações/resgates/rendimentos identificados | Projeção CF_INFO | Contrapartida, saldos, O14/O20 | L |
| Investimentos INVEST `gpinvest` | P2 proposta | CRIAR | M | M parcial | Aportes, equivalência, dividendos, baixas classificados | Movimentação de investimentos | Patrimonial, O14/O20 | L |
| Arrendamentos LEASE `gplease` | P2 proposta | CRIAR + PONTO ABERTO | Sem fonte homologada equivalente | Nenhum | Operações, cronogramas e ajustes | Arrendamentos do pacote | O20 | L |
| Outros resultados PLDTL `gppldtl` | P2 proposta | ADAPTAR + CRIAR | D/M | D/M parciais | Mapeamento e detalhe da folha | Projeção PLDTL | De-Para, O20 | M |
| Perdas de rentabilidade MGT `gpmgt` | P2 proposta | CRIAR + PONTO ABERTO | Sem fonte equivalente | Nenhum | Regras e valores da folha parcial | Projeção MGT | O20 | L |
| Câmbio EXCNG `gpexcng` | P2 proposta | CRIAR + PONTO ABERTO | Sem moeda/câmbio no canônico | Nenhum | Operações, moedas, taxas | Operações cambiais do pacote | O20 | L |
| Bônus BONUS `gpbonus` | P2 proposta | CRIAR + PONTO ABERTO | Sem fonte equivalente | Nenhum | Apuração e origem dos valores | Projeção de bônus | O20 | L |
| Informações INFO `gpinfo` | P2 proposta | ADAPTAR + CRIAR + PONTO ABERTO | Company/Profile | P parcial | Contatos, aprovador e demais campos manuais | Complemento cadastral do pacote | O20 e autorização cadastral | M |
| Intercompany INT_BS/INT_PL `gpint` | P2 proposta | CRIAR + PONTO ABERTO | Hierarquia e M/D | H/M/D parciais | Contraparte societária e mapeamento | Relações/intercompany do pacote | O20; validar sem heurística por nome | L |
| Projeção PL_F `gpfcst` | P2 proposta | ADAPTAR + CRIAR + PONTO ABERTO | D para realizado | D parcial | Fonte e regra de projeção | Projeção do pacote | O20; futuro vazio no protótipo | L |
| Orçamento PL_B/EXPS_B `gpbud` | P2 proposta | CRIAR + PONTO ABERTO | Sem orçamento equivalente | Nenhum | Versões, valores e origem orçamentária | Orçamento do pacote | O20 | L |

### 5.3 Telas referenciadas fora do bloco contábil e componentes comuns

O handoff não detalha contratos dessas telas como detalha a Central. A complexidade estima a extensão contábil, não a reconstrução de módulos já existentes. A posição no menu permanece pendente onde o handoff a deixa aberta.

| TELA | PRIORIDADE DO HANDOFF | REUTILIZAR / ADAPTAR / CRIAR / PONTO ABERTO | BACKEND EXISTENTE | ENDPOINT EXISTENTE | DADOS AUSENTES | DOMÍNIO NOVO NECESSÁRIO | DEPENDÊNCIAS | COMPLEXIDADE S/M/L |
|---|---|---|---|---|---|---|---|---|
| Balanço `bal` | Não atribuída | CRIAR + PONTO ABERTO | M/C | M/C parciais | Saldos/patrimonial | Balanço contábil | Canônico, O2/O17 | L |
| Composição dos Saldos `comp` | Não atribuída | CRIAR + PONTO ABERTO | M | M parcial | Saldo e composição comprovada | Composição contábil | Saldos, documentos, O15/O17 | L |
| Balancete Analítico `analitico` | Não atribuída | ADAPTAR + CRIAR | M | M parcial | Inicial, D/C agregados, final e natureza | Consulta de balancete | Canônico, O2/O3/O17 | L |
| DRE `dre` | Não atribuída | REUTILIZAR + ADAPTAR | DRE contábil E5 | D | Totalizações oficiais adicionais, mês a mês e drill-down | Extensões do contrato existente | Classificação/cobertura; O2/O21 | M |
| Faturamento `fat` | Não atribuída | ADAPTAR + PONTO ABERTO | Billing/Sales existentes | F service-invoices; `/api/companies/{companyId}/sales` como insumos | Conciliação receita contábil × faturamento | Integração entre módulos | O17; preservar módulo existente | L |
| Custos e Despesas `cdesp` | Não atribuída | ADAPTAR + CRIAR + PONTO ABERTO | D/M; financeiro existente | D/M parciais | Reclassificação fornecedor/conta com vigência | Ajuste gerencial contábil | Contrapartida, O2/O14/O17 | L |
| Custo de Pessoal `rhcusto` | Não atribuída | ADAPTAR + CRIAR + PONTO ABERTO | D/M parciais | D/M | DP e mapeamento de pessoal | Análise de pessoal | Folha, O10/O11/O17 | L |
| Estrutura Societária `soc` | Não atribuída | CRIAR + PONTO ABERTO | Client/Company | H como insumo | Participações e vínculos societários | Estrutura societária | O17/O20; hierarquia SaaS não prova sociedade | L |
| Ata `ata` | Não atribuída | ADAPTAR + PONTO ABERTO | Reuniões e ações de gestão | T | Equivalência de campos, cobranças e andamentos | Extensão do domínio existente se validada | O17/O19 | M |
| Variações — imobilizado | Não atribuída | ADAPTAR + CRIAR | Bens/M | F assets; M | Conciliação por conta/tipo | Variações contábeis | Saldos, vínculo bens-contas; O17 | L |
| Variações — empréstimos | Não atribuída | ADAPTAR + CRIAR | Dívidas/M | F debts; M | Conciliação CP/LP | Variações contábeis | Saldos e contas; O17 | L |
| Variações — investimentos | Não atribuída | CRIAR | M | M parcial | Tipos de movimento e saldos | Variações contábeis | Patrimonial, O14/O17 | L |
| Variações — estoques | Não atribuída | CRIAR | M | M parcial | Composição e movimento de estoque | Variações/composição contábeis | Canônico, fonte a confirmar; O17 | L |
| Variações — impostos | Não atribuída | CRIAR + PONTO ABERTO | M; retenções financeiras separadas | M parcial | Créditos, compensações, tributos e vigência | Variações fiscais homologadas | O10/O14/O17 | L |
| Variações — folha | Não atribuída | CRIAR + PONTO ABERTO | M | M parcial | Saldos e eventos DP | Variações da folha | O10/O11/O17 | L |
| Usuários e Acessos | Transversal, sem P | REUTILIZAR + ADAPTAR | Sessão, grants e administração | A; APIs administrativas E2 | Política das novas ações contábeis | Permissões incrementais | O1; preservar isolamento | M |
| Seletor e barra de competência | Transversal, sem P | REUTILIZAR + ADAPTAR | Hierarquia e Companies permitidas | A/H/S | Consulta hierárquica de leitura, cobertura da base | Contexto operacional | O1/O3/O18 | M |
| Drill-down universal | Transversal, sem P | ADAPTAR + CRIAR | M/D | M/D parciais | Filtro por conta/linha, contrapartida, agregados | Consultas contábeis rastreáveis | Canônico e classificação | M |
| Imprimir/Excel | Transversal, sem P | ADAPTAR + CRIAR | Dados nas APIs | Nenhum exportador contábil universal | Contrato de exportação consistente | Exportação dos relatórios homologados | Aceite de layout/escopo; O21 | M |
| GOB, Débitos, Certidões, PER/DCOMP, caixa postal | Fora do escopo | PONTO ABERTO apenas de dependência | Nenhum avaliado | Fora desta auditoria | Evidência para itens p7/p9/p13 | Nenhum nesta entrega | Checklist deve indicar validação indisponível/manual | — |

## 6. Backlogs propostos e ordem de Issues

Os IDs M1–M7 e C1–C14 são referências deste documento, não Issues criadas no GitHub. A #433 permanece a análise-mãe. As estimativas abrangem a entrega descrita e podem ser divididas em PRs após decisão de produto.

### 6.1 MVP CONTÁBIL DE GO-LIVE

| Ordem/Issue proposta | Entrega e aceite | Reutilização | Dependências | Complexidade |
|---|---|---|---|---|
| M1 — Contrato de acesso e seleção operacional | Definir como analista consulta Firm → Client → Company sem ganhar gestão cadastral. Formalizar grants de leitura, classificação e sync. Testar 403 entre escritórios/Companies e revogação. | A/H/S, CompanyPermission e sessão | O1; #433 | M |
| M2 — Área da Contabilidade e contexto | Usar login atual, seletor hierárquico autorizado, estado vazio e acesso negado. Reutilizar páginas de contas/movimentos/DRE e cliente HTTP existente. | Router, auth, CompanyAccess, administração | M1; O18 para preferência persistida | M |
| M3 — Estado e solicitação de sincronização | Mostrar fonte, período, status, contagens, falha sanitizada, eventos e tentativas. Solicitar INITIAL com idempotência e autorização. Mostrar cobertura real e limitações. | Q/K, Connector, componente administrativo | M1, configuração operacional; evidência #310 | M |
| M4 — Plano de contas e classificação existente | Apresentar contas carregadas, pendências e catálogo. Classificar individualmente. Definir se aceite exige plano completo e então acrescentar leitor dedicado de contas. Paginação/busca server-side e isolamento. | C, AccountingAccount, classificação | M1/M3; O2/O21 para alcance de catálogo/plano | M para universo importado; L com plano completo |
| M5 — Movimentos contábeis utilizáveis | Consulta paginada por Company, fonte/conta e período, ordenação determinística, precisão monetária e origem. Explicitar ausência dos campos ainda não sincronizados. | M e repositório | M1/M3; ajuste compatível do contrato e cliente | M |
| M6 — DRE contábil homologável | Reutilizar cálculo D, informar cobertura e não classificadas. Fornecer agregações necessárias no backend, validar sinal e total por linha contra referência aprovada. Testar múltiplas fontes e ausência de dupla contagem. | D/C; tela `/dre` | M4/M5; O2/O21 e amostra aprovada | M |
| M7 — Aceite vertical e operação | Sessão → hierarquia → fonte → SyncRun → contas → classificação → movimentos → DRE → logout. Cobrir usuário comum, revogação, Company proibida, reexecução e falha de leitura. Registrar limites e runbook. | Testes HTTP/PostgreSQL/Connector existentes | M2–M6; aceite operacional restante da #310 | M |

**PROPOSTA de corte.** O MVP pode operar com INITIAL controlado e classificação individual já existente se o negócio aceitar o universo sincronizado e a cobertura da DRE. Plano completo é evolução obrigatória se “plano de contas” significar todo o cadastro, inclusive contas sem movimento. Esse aceite precisa acontecer antes da implementação do recorte. O21.

**PONTO ABERTO.** DRE com lançamentos de encerramento exige enriquecimento canônico ou um recorte formalmente homologado que não os contenha. O MVP não deve apresentar zero não classificado como prova de integridade patrimonial. O21.

**PROPOSTA de dependência.** A #432 pode seguir como melhoria operacional independente. O Manager local não é pré-requisito técnico para usar o runtime e os scripts existentes. Presença online, versão observada e última conexão precisam de contrato próprio antes de serem exibidas como fatos. Runtime fazendo polling não equivale a agendamento automático de novas solicitações SyncRun.

### 6.2 CENTRAL DE FECHAMENTO

| Ordem/Issue proposta | Entrega e aceite | Dependências reais | Decisões bloqueantes | Complexidade |
|---|---|---|---|---|
| C1 — Evidência da origem e contrato canônico ampliado | Identificar campos/tabelas de saldos, documento, encerramento, contrapartida/múltiplas partidas, plano completo e CC. Amostra sanitizada e contratos de compatibilidade. | M3 e reader read-only | O3/O7/O14; dúvidas técnicas de origem precisam de evidência, sem nomes inventados | L |
| C2 — Transporte e persistência dos dados ampliados | Evoluir reader → agente → decoder → validador → PostgreSQL → DTO, identidade de partidas, idempotência e atomicidade. Migrar sem reinterpretar dados antigos silenciosamente. | C1 | O3, incluindo alteração/exclusão na origem | L |
| C3 — De-Para completo | Patrimonial, estrutura de resultado, classificação original, regras por prefixo e aplicação determinística, vigência e rastreabilidade. | Plano completo de C2; C existente | O2/O14 e permissões de O1 | L |
| C4 — Saldos, movimento mensal e Diagnóstico | Consulta server-side de inicial/D/C/final, encerramentos e cobertura; diagnósticos A=P+PL e DRE×resultado com critérios aprovados. | C2/C3 | O3/O5/O12 | L |
| C5 — Períodos Congelados | Snapshot/revisão auditáveis e comportamento diante de nova publicação conforme decisão explícita. Testar concorrência com sync. | C2/C4 | O1/O4/O12 | L |
| C6 — Fundação de lançamentos preparados | Documento/lote/origem, linhas D/C, CC, valores exatos, competência, idempotência, versões, trilha e transições aprovadas. | Plano/CC C2; acesso M1 | O1/O6/O7/O9 | L |
| C7 — Documentos e classificação documental | Storage seguro, upload/extração aprovada, conferência humana, vínculo com preparados e deduplicação. IA é fatia opt-in após decisão. | C6; C2 para conta/participante | O8/O9/O14/O15 | L |
| C8 — Recorrentes e importações homologadas | Modelos contábeis, geração repetível por competência, arredondamento aprovado e relatório de erros de importação. | C6 e contratos de planilhas | O9/O15 | L |
| C9 — Pré-fechamento | Projeção consistente de base + preparados, rastreabilidade dos efeitos, balancete/DRE e comparação histórica. Garantir ausência de dupla contagem de lote já importado. | C4/C6; C7/C8 como origens progressivas | O6/O9/O12; O4 se mês congelado | L |
| C10 — Lotes e exportação Questor | Revisão/aprovação, lote estável, arquivo conforme layout homologado, recibo e trilha. Separar arquivo gerado de importação reconhecida. | C6 e layout oficial; integração de retorno C2 | O1/O6/O7/O15 | L |
| C11 — Suportes e Checklist progressivo | Evidências por saldo, itens manuais/aplicáveis, revisão identificada, automações apenas com fonte disponível. Não marcar dependência ausente como OK. | C4/C5/C7/C9/C10 para checklist completo | O1/O15/O17 | L |
| C12 — Roteiro e Cockpit | Compor estados reais dos módulos disponíveis e prazos aprovados; distinguir etapa não implementada de concluída. | C6–C11; etapas fiscais futuras podem ficar indisponíveis | O13/O18 | M |
| C13 — Classificação aprendida, antecipadas e adiantamentos | Evoluções separadas de documentos/preparados, com regras e vínculos homologados. | C2/C3/C6/C7/C9 | O9/O14 | L por domínio |
| C14 — Fiscal, folha, volumetria, pacote e sucesso | Criar Issues independentes somente após definição de fontes, escopo e regras. Reutilizar ações/bens/dívidas onde houver integração validada. | Fundações anteriores conforme matriz | O10/O11/O16/O17/O19/O20 | L por domínio |

Ordem técnica proposta para abrir Issues após a análise aprovada

1. M1 → M2. M3 pode avançar junto de M2 após M1.
2. M4 e M5 após M3. M6 → M7 fecha o fluxo de go-live.
3. C1 pode ser investigada em paralelo com o MVP. C2 → C3 → C4 habilita Diagnóstico e De-Para completos.
4. C5 segue C4 somente após decisão de congelamento. C6 pode avançar independentemente de C5, desde que a interação com meses congelados não seja implementada por suposição.
5. C7 e C8 seguem C6 e podem ser entregues separadamente. C10 pode começar após C6 e homologação do layout, sem esperar todas as origens futuras.
6. C9 depende de C4/C6 e da estratégia de reconhecimento de importação. C11 fecha a evidência e revisão do ciclo. C12 agrega o que estiver realmente disponível.
7. C13 e C14 são extensões dependentes dos dados/regras respectivos. Um bloqueio fiscal não bloqueia o MVP de consulta.

A prioridade P0 do handoff não significa que Diagnóstico, Documentos, Recorrentes, Pré-fechamento, Congelamento, Lotes e Checklist possam ser implementados simultaneamente sem as fundações acima. A sugestão de “menos de uma semana” da #433 não é estimativa validada por esta auditoria.

## 7. Decisões e impedimentos explícitos

Todos os itens desta seção que bloqueiam comportamento de produto estão marcados com a frase exigida. Uma consulta técnica da origem pode avançar sem resolver a decisão, desde que não a substitua por uma hipótese.

| ID | Ponto aberto e decisão necessária | Bloqueia |
|---|---|---|
| O1 | **DECISÃO DE PRODUTO NECESSÁRIA** — acesso de consulta hierárquica do analista, alcance por cliente/Company, quem classifica, aprova/exporta, congela/aceita/descongela e assina revisão; segregação de funções. Reutilizar autenticação/grants. Não conceder HIERARCHY_MANAGE ou SYSTEM_ADMIN apenas para viabilizar navegação. | M1; novas mutações da Central |
| O2 | **DECISÃO DE PRODUTO NECESSÁRIA** — estrutura gerencial do produto/escritório/cliente, herança de classificações, vigência, retroatividade e restauração da original. O catálogo global existente é fato técnico, não resposta à decisão. | De-Para completo e reclassificação |
| O3 | **DECISÃO DE PRODUTO NECESSÁRIA** — cobertura exigida, data-base de saldo inicial, política de sincronização, tratamento de alterados/excluídos, frequência e janelas. INITIAL atual fornece uma capacidade concreta, sem resolver essas políticas. Investigar tecnicamente campos e identidade de múltiplas partidas. | C1/C2/C4; promessa de base atualizada completa |
| O4 | **DECISÃO DE PRODUTO NECESSÁRIA** — congelamento bloqueia publicação, sinaliza diferenças ou mantém versão oficial separada? Quais relatórios usam qual versão? Qual relação com bloqueio Questor e descongelamento? | C5 e interação sync/fechamento |
| O5 | **DECISÃO DE PRODUTO NECESSÁRIA** — verificações ERP gravadas, gravidades, tolerâncias, critério de integridade e destino das verificações dos lançamentos manuais retirados da interface. | Diagnóstico completo |
| O6 | **DECISÃO DE PRODUTO NECESSÁRIA** — reconhecimento lote→razão, reexportação, cancelamento/estorno e reabertura de exportados. Como impedir soma dupla de preparados já importados? Layout oficial com/sem CC requer artefato e homologação, além de decisão de escopo. | Lotes e pré-fechamento após exportação |
| O7 | **DECISÃO DE PRODUTO NECESSÁRIA** — identidade/cadastro de centro de custo, vínculo com Questor e ResultCenter, rateio por lado e exigência por conta. | Prepared entries/CC e exportação |
| O8 | **DECISÃO DE PRODUTO NECESSÁRIA** — armazenamento, retenção, acesso aos arquivos, limites/tipos, uso de IA, provedor/custo/auditoria e deduplicação de documentos. Política não deve ser herdada automaticamente dos 20 MB e formatos do runtime. | Documentos, suportes e leitura da folha |
| O9 | **DECISÃO DE PRODUTO NECESSÁRIA** — transições e validade dos preparados, substituir lançamentos anteriores de documento, editar recorrente já gerado, arredondar rateios/parcelas, baixas parciais de adiantamento e reconhecimento das gerações. | C6/C7/C8/C13 |
| O10 | **DECISÃO DE PRODUTO NECESSÁRIA** — fonte do regime tributário, regras fiscais e vigência, obrigações acessórias, município/retenções, Lucro Real anual, ECF e CBS/IBS. Fórmulas e percentuais do protótipo exigem validação especializada antes de virar regra backend. | Fiscal |
| O11 | **DECISÃO DE PRODUTO NECESSÁRIA** — reabertura fiscal, geração direta aprovada do Lalur, alcance da integração DP/folha e exceções de encargos. Tabelas DP e relatórios precisam ser identificados por evidência técnica. | Lalur, folha, DP |
| O12 | **DECISÃO DE PRODUTO NECESSÁRIA** — homologação e configurabilidade dos limiares de alerta e tolerâncias, inclusive congelamento e pré-fechamento. Os números fixos do protótipo não definem política do produto. | Alertas e aceites automáticos |
| O13 | **DECISÃO DE PRODUTO NECESSÁRIA** — calendário/feriados, etapas configuráveis, responsável como usuário ou texto e fluxo de revisão. | Roteiro |
| O14 | **DECISÃO DE PRODUTO NECESSÁRIA** — participante contábil e vínculo com BusinessPartner; identificação explícita de adiantamentos, antecipadas, retidos, folha e provisões; mapeamento conta→código do pacote. Heurística de nome exige validação. | Sugestões, fiscal/folha, adiantamentos e pacote |
| O15 | **DECISÃO DE PRODUTO NECESSÁRIA** — tolerância dos suportes e contratos de importação, colunas obrigatórias, erro parcial/atômico. Os quatro modelos de planilha e regras detalhadas precisam ser obtidos do artefato. | Suportes e imports |
| O16 | **DECISÃO DE PRODUTO NECESSÁRIA** — cláusula real de reajuste, pesos/franquia e armazenamento do honorário. A fonte DP é dependência técnica adicional. | Volumetria comercial |
| O17 | **DECISÃO DE PRODUTO NECESSÁRIA** — fronteira entre contábil/gestão para Balanço, Composição, Balancete, Variações, reclassificação, Ata e Sucesso. Validar vínculo com reuniões/ações já existentes antes de criar persistência paralela. | Integrações e navegação dessas telas |
| O18 | **DECISÃO DE PRODUTO NECESSÁRIA** — competência de fechamento persistida por usuário ou empresa e padrão quando a cobertura for incompleta. A seleção Firm→Client→Company foi solicitada neste pedido para o MVP; a falta de autorização de consulta é tratada em O1. | Preferências/contexto de fechamento |
| O19 | **DECISÃO DE PRODUTO NECESSÁRIA** — cobrança por e-mail, agendamento, destinatários e fronteira de auditoria. SMTP existente é de identidade, sem fluxo de cobrança de ações. | Sucesso do Cliente |
| O20 | **DECISÃO DE PRODUTO NECESSÁRIA** — pacote padrão ou customização, primeiro cliente, fórmulas/modelo ConsA versionados, formato de entrega e fontes de LEASE/MGT/EXCNG/BONUS/INFO/projeção/orçamento/intercompany. | Todo o pacote e sua prioridade |
| O21 | **DECISÃO DE PRODUTO NECESSÁRIA** — alcance do go-live: plano completo ou contas importadas, período e amostra de DRE aceitos, tratamento de encerramentos, múltiplas fontes, detalhe/exportação exigidos e limites de cobertura. | Aceite final M4/M6/M7 |

Correspondência com os 30 pontos da seção 15 do handoff, para preservar sua rastreabilidade

| Itens do handoff | Tratamento nesta auditoria |
|---|---|
| 1 | Hierarquia do MVP solicitada agora; consulta autorizada O1 e contexto O18. |
| 2–5 | O20, O17, O17, O2 respectivamente. |
| 6–12 | O3, O4, O6, O5, O11, O14, O14 respectivamente. |
| 13–17 | O6, O6/O11, O11, O10, O10 respectivamente. |
| 18–22 | O12, O13, O8, O16, O20 respectivamente. |
| 23–25 | O5, O10, O15 respectivamente. |
| 26–30 | O1, O8, O8, O19, O18 respectivamente. |

**HIPÓTESE TÉCNICA A VALIDAR.** Partidas simples podem permitir obter contrapartida a partir de source/chave. É insuficiente para prometer suporte a partidas múltiplas ou confirmar importação de lote. A análise exige amostra da origem e revisão de identidade, sem inventar campos Questor.

## 8. Critérios propostos para a próxima implementação

- Preservar PostgreSQL como fonte interna de consultas e autorização no backend. Manter leitura Firebird com identidade validada e usuário read-only.
- Evoluir contratos existentes, o cliente HTTP e a sessão atual. Reutilizar domínios financeiros somente por integração explícita. Fechamento financeiro, DRE operacional, conciliação bancária e recorrência financeira não implementam o domínio da Central.
- Testar source/Company/escritório, revogação, CSRF, valores decimais, paginação, idempotência, concorrência, falha de leitura e rollback atômico. Ampliar testes PostgreSQL e HTTP nas entregas correspondentes.
- Para contrato canônico novo, testar campos ausentes em dados antigos, compatibilidade agente/API, valores nulos versus desconhecidos, encerramentos, múltiplas partidas e lote parcial. Migração não pode produzir saldo inicial ou classificação por suposição.
- Para DRE, confrontar sinal, linhas, total e contas não classificadas com amostra homologada. Para Diagnóstico, usar saldo inicial e movimentos/encerramentos verificáveis. Ausência de dados deve ser visível.
- Para congelamento e preparados, somente implementar as transições, efeitos de sincronização e políticas aprovadas nos itens O4/O6/O9.
- Confirmar flags de `finemp.sync.control`, `finemp.sync.executor` e/ou `finemp.connector.protocol`, administração `finemp.connector` e configuração Questor conforme o caminho escolhido. Existência do código não comprova que a feature está ligada em um ambiente.

## 9. Evidências de código na main auditada

Todos os links fixam o commit auditado. Os caminhos adicionais citados em cada linha pertencem ao mesmo commit.

| ID | Fontes verificadas |
|---|---|
| E1 | `Client`, `Company`, `AccountingSource`, controller e serviço SaaS. [SaasAdministrationService](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/java/com/finemp/administration/SaasAdministrationService.java) |
| E2 | Enums e serviços `PlatformPermission`, `AccountingFirmPermission`, `CompanyPermission`, `CompanyPermissionAuthorizationService`; `CurrentUserQueryService`, `OrganizationAccessQueryService`, `CurrentUserCompanyAccessResponse`, `AuthenticationController`. [SecurityConfiguration](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/java/com/finemp/security/SecurityConfiguration.java) |
| E3 | `AccountingAccount`, `AccountingMovement`, DTO/controller/query de movimentos. [ImportedAccountingMovement](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/java/com/finemp/accounting/ingestion/ImportedAccountingMovement.java) |
| E4 | `AccountingAccountClassification`, `DreClassification`, controllers, serviço e query repository de classificação. [Serviço de classificação](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/java/com/finemp/accounting/classification/AccountingAccountClassificationService.java) |
| E5 | `DreController`, `DreCalculationService`, `DreMovementRepository`, `DreMovementValueCalculator`. [Cálculo DRE](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/java/com/finemp/dre/DreCalculationService.java) |
| E6 | `FirebirdQuestorAccountingReader`, `QuestorReadOnlySession`, `VerifiedQuestorSnapshotReader`, `FirebirdQuestorCompanyIdentityReader`. [Reader Questor](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/java/com/finemp/integration/questor/FirebirdQuestorAccountingReader.java) |
| E7 | `SyncRun`, `SyncRunMode`, `SyncControlService`, `JpaSyncExecutionLifecycle`, `CanonicalBatchValidator`, `ValidatedAccountingBatch`, migration V19 de unicidade e ownership. [Publicação atômica](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/java/com/finemp/accounting/sync/publication/AccountingPublicationTransaction.java) |
| E8 | Controllers de credencial, session/work/completion, `ConnectorBatchDecoder`, `ConnectorAgentCycle`, `ConnectorRuntime`, `ConnectorCredentialResponse`; scripts `connector-windows-install/run/control.ps1`. [Decoder estrito](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/java/com/finemp/connector/completion/ConnectorBatchDecoder.java) |
| E9 | Contratos e flags. [OpenAPI](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/docs/openapi/finemp-api-v1.yaml) · [application.yml](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/resources/application.yml) |
| E10 | Router, `accounting-api.ts`, páginas accounting-accounts/accounting-movements, DrePage, `group-dre-lines.ts`, platform-admin-page e ConnectorAdministrationSection. [Agrupamento DRE no frontend](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/frontend/src/features/dre/lib/group-dre-lines.ts) |
| E11 | `ActionPlanController/Response`, `PlanningMeetingController`, `CompanyProfileController`, `AssetController`, `DebtAgreementController`, `ServiceInvoiceController`, `ResultCenterController`, `FinancialPeriodController`, `ConsistencyDiagnosticsController`, migrations financeiras. [Árvore de domínios](https://github.com/matos795/Finemp/tree/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/main/java/com/finemp) |
| E12 | Testes existentes `AccountingReadPermissionHttpIntegrationTest`, `AccountingMutationPermissionHttpIntegrationTest`, `DreCalculationServiceIntegrationTest`, `QuestorSnapshotJdbcContractTest`, `SyncRunExecutorIntegrationTest`, `ConnectorProtocolEndToEndTest`. Inspecionados como evidência de contratos, sem reexecução nesta análise. [Teste ponta a ponta do Connector](https://github.com/matos795/Finemp/blob/2c184618f776cf94908db759ceee9eacc273bde7/backend/src/test/java/com/finemp/connector/agent/ConnectorProtocolEndToEndTest.java) |

Entrega encerrada na análise. Este documento contém a matriz, os backlogs propostos, a ordem de Issues, as dependências e as complexidades. Nenhuma branch de feature, implementação ou commit foi criado para esta auditoria.
