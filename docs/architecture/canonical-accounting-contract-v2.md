# Contrato canônico contábil v2 — especificação (C1 fase 2)

Issue: matos795/Finemp#457 (filha de #433). Relacionadas: #449 (valores negativos), O3/O7/O14 do [GAP](../reference/GAP_CONTABIL.md).
Evidência: [C1](../runbooks/questor-c1-discovery.md), [C1b](../runbooks/questor-c1b-discovery.md) e os comentários da #457 com os resultados reais.

**Status: especificação, nada implementado.**

- Este documento define o que o C2 vai construir.
- Não altera Connector, reader, transporte, Flyway, banco, API, frontend nem a DRE.
- Nomes de tipos e campos são a proposta adotada para o C2. Podem ser ajustados na revisão desta especificação, não durante a implementação.

Convenções:

- **FATO** — confirmado pelo código em `main` ou pela origem real (C1/C1b).
- **PROPOSTA** — decisão deste documento.
- **ABERTO** — continua sem evidência ou sem decisão.

## 1. Evidência base

Instalação Questor real: Firebird 2.5.8, sessão somente leitura, uma empresa. Detalhes nos comentários da #457.

### Plano

- `PLANOESPEC` é o universo completo: 1.851 contas.
  - `TIPOCONTA=1`: 303 contas. `TIPOCONTA=2`: 1.548.
  - Só 73 contas tiveram movimento no recorte original (83 no recorte C1b), todas `TIPOCONTA=2`.
- Hierarquia por `CLASSIFCONTA`:
  - toda conta abaixo da raiz tem exatamente um pai por prefixo;
  - as 242 contas com filhos não têm movimento.
- `CLASSIFCONTA` **não** é única (1.480 classificações para 1.851 contas). A PK é `(CODIGOEMPRESA, CONTACTB)`.

### Movimentos

- `LCTOCTB` tem PK `(CODIGOEMPRESA, CHAVELCTOCTB)`. A mesma `CHAVELCTOCTB` existe em outras empresas.
- Cada linha tem débito, crédito ou os dois. Nunca nenhum, e nunca débito = crédito.
- `VALORLCTOCTB` é NUMERIC(14,2) NOT NULL. **Nenhum negativo** de 2019 a 2025, no banco inteiro.
- **Agrupamento:**
  - `CHAVEORIGEM` estava preenchida em 1.083 de 1.083 linhas, e os 559 grupos fecham D = C, inclusive os que contêm linhas de um lado só.
  - `TRANSCTB` estava preenchida em 543 linhas, e os 34 grupos fecham.
- **Origem:** `CODIGOORIGLCTOCTB` é código de catálogo (`ORIGEMLCTOCTB`): `FI` = Fiscal, `FP` = Folha de Pagamento. `FP` **não** é encerramento.
- **Campos observados com valor único no recorte:** `TIPOLANCAMENTO` = `LN`, `ORIGEMDADO` = 1.
- **Sem uso no recorte:** `IDSYN` está vazio.

### Agregados D/C

Os agregados fecham 100% com os movimentos:

| Tabela | Comparação | Resultado |
| --- | --- | --- |
| `SALDOCTB` (`VALORDEB`/`VALORCRED`) | movimentos do mesmo dia | 676/676 |
| `SALDOCTBMENSAL` | movimentos do mês | 663/663 |
| `SALDOCTBMENSALHIST` | movimentos do mês + `CODIGOHISTCTB` | 663/663 |
| soma diária | mensal | 663/663 |

`SALDOCTBMENSAL` tem uma data por mês, sempre no dia 1. **Não há coluna de saldo acumulado.**

### Sem dados na empresa observada

- `LCTOCTBPARTICIPANTE`: zero linhas em 2023–2025.
- `CENTROCUSTO`, `PLESPECCENCUSRELAC`, `RATCENTROCUSTO`, `LCTOGER`, `LCTOGEREXCLUIDO`: zero.
- `LCTOCTBEXCLUIDO`: zero.
- `CFGZERAMENTOCTB`: zero.
- `PLANOESPECPLANODRE`: zero.

### Permissão de leitura

A leitura de `ORIGEMLCTOCTB` exigiu um `GRANT SELECT` que o usuário somente leitura não tinha.

## 2. Contrato atual (v1) — FATO pelo código

| Aspecto | Hoje |
| --- | --- |
| Registro canônico | `ImportedAccountingMovement(sourceEntryKey, accountingDate, externalAccountId, accountKey, accountDescription, side, amount, history)`: uma linha por **lado** |
| Leitura Questor | `FirebirdQuestorAccountingReader`: `UNION ALL` de crédito e débito com **inner join** em `PLANOESPEC`; `accountKey = CLASSIFCONTA || '.' || CONTACTB`; `history = COMPLHIST` |
| Transporte | `ConnectorBatch(observedIdentity, movements)`. O `ConnectorBatchDecoder` é estrito: campo desconhecido ou duplicado → `INVALID_PAYLOAD` |
| Validação | `CanonicalBatchValidator`: textos ≤ 255, decimal exato com escala ≤ 4 e 15 dígitos inteiros, data dentro do período, conta coerente no lote. **Preserva sinal.** Mesma identidade com conteúdo diferente → `DATA_CONFLICT` |
| Identidade | Conta: `(accounting_source_id, external_account_id)`. Movimento: `(accounting_source_id, source_entry_key, side)` (V19) |
| Publicação | Só insere. Linha idêntica já existente é ignorada; divergente → `DATA_CONFLICT`. Sem update/delete |
| Plano | Só as contas que aparecem em movimentos |
| Cobertura | DRE e telas usam a **última** SyncRun SUCCEEDED por fonte (`DreCoverageReader`), não acumulada |
| DRE | `DreMovementValueCalculator` usa o prefixo `1` do `accountKey` (vindo de `CLASSIFCONTA`) para o sinal; valor negativo → 422 (#451) |

## 3. Princípios do v2

1. **Núcleo universal pequeno + metadados opcionais + capabilities da fonte.** Nenhum DTO gigante com todos os campos do Questor dentro do movimento.
2. **Um registro de movimento por linha da origem**, não por lado. A linha do Questor com débito e crédito é **um** fato com dois lados.
   - A expansão em lados (`accounting_movements`) continua no servidor, para compatibilidade com DRE, De-Para e Composição.
   - Metadados ficam uma vez por linha, não duplicados por lado.
3. **Datasets separados** no mesmo lote, cada um com sua cobertura: plano, movimentos, totais diários, totais mensais, catálogo de origem; depois participante e rateio.
4. **Código da origem preservado cru.** Qualquer interpretação (por exemplo, sintética/analítica) é uma camada explícita, versionada e marcada como `INFERRED` quando vier de comportamento, não de documentação.
5. **Ausência ≠ zero ≠ desconhecido.** Campo opcional ausente é `null`. Total desconhecido nunca vira zero. Cobertura não é "dado atualizado".
6. **Invariantes da fonte vivem na fronteira da fonte.** O validador canônico continua genérico e não proíbe sinal, para não fechar a porta a fontes futuras.

## 4. Nomes: por que não `Balance`

A evidência prova **totais de débito e crédito de um período** (dia ou mês), que batem com a soma dos movimentos. Não prova saldo inicial, final ou acumulado.

- **`Balance` (rejeitado).** Em contabilidade, *balance* é saldo: posição num instante, acumulada desde a abertura. Usar o nome induziria a ler `VALORDEB − VALORCRED` como saldo, o que é falso: é só o movimento líquido do período.
- **`Turnover` (rejeitado).** É o termo técnico em inglês para movimentação D/C, mas no produto (pt-BR) "turnover/giro" colide com faturamento e com giro de estoque/capital.
- **`Aggregate` sozinho (rejeitado como nome final).** É correto, mas vago: não diz o que é agregado.
- **Adotado: `DebitCreditTotals`**, com o grão no nome do tipo:
  - `CanonicalAccountDailyDebitCreditTotals`;
  - `CanonicalAccountMonthlyDebitCreditTotals`.
  - Os campos são `debitTotal` e `creditTotal`, nunca `balance`, `opening` ou `closing`.

A palavra "saldo" do nome das tabelas do Questor (`SALDOCTB*`) fica só no campo de origem da matriz. Não entra no vocabulário canônico.

Nomes dos demais tipos:

| Tipo | Motivo do nome |
| --- | --- |
| `CanonicalAccountingAccount` | Conta do plano da origem. Sem "imported", porque o plano agora é completo, não só o observado |
| `CanonicalAccountingEntry` | Uma linha da origem com um ou dois lados. "Entry" (lançamento) e não "Movement", porque `AccountingMovement` já é o nome persistido **por lado**; trocar o significado de "movement" quebraria a leitura do código atual |
| `CanonicalEntrySourceMetadata` | Metadados técnicos de origem da linha, opcionais |
| `CanonicalEntryParticipants`, `CanonicalEntryCostAllocation` | Datasets opcionais, separados do núcleo |
| `CanonicalOriginCatalogEntry` | Catálogo de códigos de origem (`FI`, `FP`…) lido da fonte, nunca embutido no código |
| `AccountingDatasetCoverage` | Cobertura por dataset, não por SyncRun |

## 5. Records propostos (forma textual)

```text
CanonicalAccountingBatch v2                      // um por tentativa de SyncRun
├─ contractVersion = 2
├─ observedIdentity : ExternalCompanyIdentity    // igual ao v1
├─ capabilities     : SourceCapabilities         // o que esta fonte entrega neste lote
├─ chart?           : ChartSnapshot              // presente se capability CHART
│    ├─ asOf        : Instant (lado servidor: início da leitura)
│    └─ accounts[]  : CanonicalAccountingAccount
├─ entries?         : EntriesDataset             // presente se MOVEMENTS
│    ├─ period      : [start, end]  (do SyncRun; não vem do agente)
│    ├─ entries[]   : CanonicalAccountingEntry
│    └─ metadata[]? : CanonicalEntrySourceMetadata   (0..1 por entry)
├─ dailyTotals?     : CanonicalAccountDailyDebitCreditTotals[]   // se DAILY_TOTALS
├─ monthlyTotals?   : CanonicalAccountMonthlyDebitCreditTotals[] // se MONTHLY_TOTALS
├─ originCatalog?   : CanonicalOriginCatalogEntry[]              // se ORIGIN_CATALOG
├─ participants?    : CanonicalEntryParticipants[]               // futuro, opcional
└─ costAllocations? : CanonicalEntryCostAllocation[]             // futuro, opcional
```

```java
// PROPOSTA — formas, não código a implementar agora.

record SourceCapabilities(
    Set<Dataset> datasets,               // CHART, MOVEMENTS, DAILY_TOTALS, MONTHLY_TOTALS, ORIGIN_CATALOG, PARTICIPANTS, COST_ALLOCATIONS
    AmountSignPolicy amountSignPolicy,   // QUESTOR: NON_NEGATIVE_WITH_SIDE; outra fonte pode declarar SIGNED_WITH_SIDE
    boolean entryKeyScopedBySource) { }  // true para Questor: chave só é única dentro da empresa/fonte

record CanonicalAccountingAccount(
    String sourceAccountId,              // CONTACTB (texto do inteiro) — identidade
    String classificationCode,           // CLASSIFCONTA — NÃO é identidade
    String description,                  // DESCRCONTA
    SourceAccountCodes codes) { }        // códigos crus da origem, sem interpretação

record SourceAccountCodes(
    String accountType,                  // TIPOCONTA
    String calculationType,              // TIPOCONTACALC
    String balanceNature,                // NATURSALDO
    String balanceControl,               // CTRLSALDO
    String showInBalanceSheet,           // EMITIRBALANCO
    String nativeDreCode,                // CODIGODRE (nullable)
    String nativeDreAccount) { }         // CONTADRE  (nullable)

record CanonicalAccountingEntry(         // núcleo universal: uma linha da origem
    String sourceEntryKey,               // CHAVELCTOCTB — identidade dentro da fonte
    LocalDate accountingDate,            // DATALCTOCTB
    String debitAccountId,               // CONTACTBDEB (nullable)
    String creditAccountId,              // CONTACTBCRED (nullable)
    BigDecimal amount,                   // VALORLCTOCTB — decimal exato
    String establishmentCode,            // CODIGOESTAB
    String historyText) { }              // COMPLHIST (nullable)

record CanonicalEntrySourceMetadata(     // opcional, 0..1 por entry, só campos confirmados
    String sourceEntryKey,
    Instant sourceRecordedAt,            // DATAHORALCTOCTB — semântica ABERTA (ver matriz)
    String historyCode,                  // CODIGOHISTCTB
    String originCode,                   // CODIGOORIGLCTOCTB (FI, FP, …)
    String entryTypeCode,                // TIPOLANCAMENTO (LN observado)
    String dataOriginCode,               // ORIGEMDADO (1 observado)
    String batchCode,                    // CODIGOLOTECTB
    String documentNumber,               // NUMERODCTO
    String operationGroupKey,            // CHAVEORIGEM — agrupamento, NÃO identidade
    String transactionKey,               // TRANSCTB — agrupamento opcional
    String originEstablishmentCode) { }  // CODIGOESTABORI

record CanonicalAccountDailyDebitCreditTotals(
    String establishmentCode, String sourceAccountId, LocalDate day,
    String entryTypeCode,                // TIPOLANCAMENTO faz parte do grão da origem
    BigDecimal debitTotal, BigDecimal creditTotal) { }

record CanonicalAccountMonthlyDebitCreditTotals(
    String establishmentCode, String sourceAccountId, YearMonth month,   // DATASALDO = dia 1
    String entryTypeCode,
    BigDecimal debitTotal, BigDecimal creditTotal) { }

record CanonicalOriginCatalogEntry(String originCode, String description) { }

record CanonicalEntryParticipants(       // futuro; só códigos, nunca nome/documento
    String sourceEntryKey, String debitParticipantCode, String creditParticipantCode) { }

record CanonicalEntryCostAllocation(     // futuro; zero dados observados
    String sourceEntryKey, String sourceAccountId, String costCenterCode,
    String natureCode, BigDecimal amount) { }
```

`SALDOCTBMENSALHIST` (mensal por histórico) está **provado**, mas fica fora do v2 inicial: não há consumidor no FINEMP. Entra depois como dataset `MONTHLY_TOTALS_BY_HISTORY`, se algum relatório precisar.

## 6. Matrizes

Colunas: campo canônico | origem Questor | obrigatório? | semântica confirmada | identidade/escopo | persistir? | ponto aberto.

### 6.1 `CanonicalAccountingAccount`

| Campo | Origem | Obrig.? | Semântica | Identidade/escopo | Persistir? | Aberto |
| --- | --- | --- | --- | --- | --- | --- |
| `sourceAccountId` | `PLANOESPEC.CONTACTB` NUMERIC(10,0) | Sim | Código interno da conta (FATO: PK com empresa) | **Identidade** dentro da fonte: `(accounting_source_id, sourceAccountId)`, igual a `external_account_id` atual | Sim (já existe) | — |
| `classificationCode` | `CLASSIFCONTA` VARCHAR(30) NOT NULL | Sim | Código de classificação; hierarquia por prefixo **observada** (pai único em 100% dos casos) | Não é identidade (1.480 ≠ 1.851) | Sim (novo campo; hoje só dentro do `accountKey`) | Se pai único por prefixo vale em toda instalação |
| `description` | `DESCRCONTA` | Sim | Nome da conta | — | Sim (já existe) | — |
| `codes.accountType` | `TIPOCONTA` SMALLINT NOT NULL | Sim | Código cru. Interpretação **INFERRED**: 1 = agrupadora/sintética, 2 = lançável/analítica (303/1.548; filhos e movimentos só em 2). Não documentada pelo fornecedor | — | Sim, cru + interpretação versionada separada | Confirmação documental do significado |
| `codes.calculationType` | `TIPOCONTACALC` | Sim | Código cru, sem semântica confirmada | — | Sim (cru) | Significado |
| `codes.balanceNature` | `NATURSALDO` | Sim | Código cru; provável natureza devedora/credora, **não confirmado** | — | Sim (cru) | Significado; se substitui o prefixo `1` da DRE (decisão de produto) |
| `codes.balanceControl` | `CTRLSALDO` | Sim | Código cru | — | Sim (cru) | Significado |
| `codes.showInBalanceSheet` | `EMITIRBALANCO` | Sim | Código cru | — | Sim (cru) | Significado |
| `codes.nativeDreCode/Account` | `CODIGODRE`/`CONTADRE` (FK → `DRE`) | Não | Vínculo à DRE nativa do Questor | — | Sim (cru, nullable) | Relação com a DRE FINEMP (O2/O21). **Não** substitui o De-Para |
| `accountKey` (legado) | `CLASSIFCONTA || '.' || CONTACTB` | Sim (compat.) | Chave usada hoje pela DRE para o **sinal** (prefixo `1`) | Não é identidade | **Congelado** na primeira publicação da conta; nunca recalculado por snapshot posterior (ver 8.6) | Substituição futura pela natureza confirmada |
| `parentSourceAccountId` | **Derivado** no servidor a partir de `classificationCode` | Não | Hierarquia **INFERRED**; só preenchido quando há exatamente um pai | — | Sim, com marcador de derivação | Regra para pai ambíguo (não observado) |

### 6.2 `CanonicalAccountingEntry` (núcleo) e `CanonicalEntrySourceMetadata`

| Campo | Origem | Obrig.? | Semântica | Identidade/escopo | Persistir? | Aberto |
| --- | --- | --- | --- | --- | --- | --- |
| `sourceEntryKey` | `LCTOCTB.CHAVELCTOCTB` INTEGER | Sim | Chave da linha (PK com empresa) | **Identidade** só dentro da fonte (`entryKeyScopedBySource=true`): `(accounting_source_id, sourceEntryKey)`; lados: `+ side` (V19) | Sim (já existe) | Estabilidade diante de edição/exclusão (O3) |
| `accountingDate` | `DATALCTOCTB` DATE NOT NULL | Sim | Data contábil | — | Sim (já existe) | — |
| `debitAccountId` | `CONTACTBDEB` (FK → plano) | Um dos dois lados | Conta debitada | Referência a `sourceAccountId` | Sim (lado DEBIT) | — |
| `creditAccountId` | `CONTACTBCRED` (FK → plano) | Um dos dois lados | Conta creditada | Referência a `sourceAccountId` | Sim (lado CREDIT) | — |
| `amount` | `VALORLCTOCTB` NUMERIC(14,2) NOT NULL | Sim | Valor exato, **não negativo** na origem observada; o lado define D/C | — | Sim, escala 4 (já existe) | Sinal em outras instalações → falha fechada (#449) |
| `establishmentCode` | `CODIGOESTAB` SMALLINT NOT NULL | Sim para Questor; opcional no núcleo universal | Estabelecimento da empresa | Escopo dos totais D/C | Sim (novo) | Se a UI filtra por estabelecimento (decisão) |
| `historyText` | `COMPLHIST` VARCHAR(300) | Não | Complemento do histórico | — | Sim (já existe como `history`; limite sobe de 255 para 300) | Se o texto do histórico padrão (`HISTORICOCTB`) deve ser composto |
| `metadata.sourceRecordedAt` | `DATAHORALCTOCTB` TIMESTAMP NOT NULL | Não | Data/hora de gravação **provável**; não comparada com o log de alterações | — | Sim (nullable) | Semântica; uso para sync incremental (O3) |
| `metadata.historyCode` | `CODIGOHISTCTB` | Não | Código do histórico padrão; o agregado HIST fecha por ele | — | Sim | — |
| `metadata.originCode` | `CODIGOORIGLCTOCTB` VARCHAR(2) (FK → `ORIGEMLCTOCTB`) | Não | Origem técnica (`FI`, `FP`…). **Não** é encerramento | Referência ao catálogo da fonte | Sim | Outros códigos em outras instalações (o catálogo resolve) |
| `metadata.entryTypeCode` | `TIPOLANCAMENTO` CHAR(2) NOT NULL | Não | Código cru; só `LN` observado | Faz parte do grão dos totais | Sim | Significado dos demais valores |
| `metadata.dataOriginCode` | `ORIGEMDADO` SMALLINT NOT NULL | Não | Código cru; só `1` observado | — | Sim | Significado |
| `metadata.batchCode` | `CODIGOLOTECTB` (FK → `LOTECTB`) | Não | Lote contábil | Agrupamento, não identidade | Sim | — |
| `metadata.documentNumber` | `NUMERODCTO` VARCHAR(20) | Não | Número de documento informado | — | Sim (texto) | Política de exibição (pode conter dado de terceiro) |
| `metadata.operationGroupKey` | `CHAVEORIGEM` VARCHAR(25) | Não | Agrupa linhas de uma operação; 559/559 grupos fecham D = C | Escopo da fonte; **não** é identidade da linha; **não** define contrapartida | Sim | Preenchimento em outras instalações; uso para contrapartida (decisão) |
| `metadata.transactionKey` | `TRANSCTB` NUMERIC(10,0) | Não | Agrupamento parcial; 34/34 grupos fecham | Escopo da fonte; não identidade | Sim | Relação com `CHAVEORIGEM` |
| `metadata.originEstablishmentCode` | `CODIGOESTABORI` | Não | Estabelecimento de origem | — | Sim | Significado vs. `CODIGOESTAB` |
| — (excluído) | `IDSYN` | — | Vazio no recorte | — | Não | Reavaliar se aparecer preenchido |
| — (excluído) | `DESCRCONTACTBDEB/CRED` | — | Redundante com o plano | — | Não (o plano é a fonte) | — |
| — (excluído) | `COTACAOMOEDA`, `VALORLCTOMOEDA`, `CODIGOLCTOPROG`, `LCTOCONCIL*`, `CODIGOUSUARIO` | — | Não investigados | — | Não | Fora do v2 |

### 6.3 `CanonicalAccountDailyDebitCreditTotals` (`SALDOCTB`)

| Campo | Origem | Obrig.? | Semântica | Identidade/escopo | Persistir? | Aberto |
| --- | --- | --- | --- | --- | --- | --- |
| `establishmentCode` | `CODIGOESTAB` | Sim | Estabelecimento | Chave | Sim | — |
| `sourceAccountId` | `CONTACTB` | Sim | Conta | Chave | Sim | — |
| `day` | `DATASALDO` | Sim | Dia do movimento (676/676 pares conta/dia batem) | Chave | Sim | — |
| `entryTypeCode` | `TIPOLANCAMENTO` | Sim | Parte do grão da origem | Chave | Sim | Se outros tipos se somam |
| `debitTotal` | `VALORDEB` | Sim | **Total de débitos do dia** (FATO: bate com movimentos) | — | Sim | — |
| `creditTotal` | `VALORCRED` | Sim | **Total de créditos do dia** | — | Sim | — |

Chave proposta: `(accounting_source_id, establishmentCode, sourceAccountId, day, entryTypeCode)`. A unicidade na origem é **ABERTA** (PK da origem não registrada na #457). O validador recusa duplicata com `DATA_CONFLICT`.

### 6.4 `CanonicalAccountMonthlyDebitCreditTotals` (`SALDOCTBMENSAL`)

Mesmos campos do diário, com `month` (`DATASALDO`, sempre dia 1 → `YearMonth`).

- FATO: 663/663 batem com os movimentos do mês e com a soma diária.
- Chave: `(accounting_source_id, establishmentCode, sourceAccountId, month, entryTypeCode)`.
- **Não** contém saldo acumulado nem de abertura.
- Saldo inicial continua **ABERTO**. Só poderia vir de uma fórmula sobre totais desde a abertura da escrituração, e o início dessa escrituração não está comprovado. Desconhecido ≠ zero.

### 6.5 Participante (opcional)

| Campo | Origem | Obrig.? | Semântica | Identidade/escopo | Persistir? | Aberto |
| --- | --- | --- | --- | --- | --- | --- |
| `sourceEntryKey` | `LCTOCTBPARTICIPANTE.CHAVELCTOCTB` (FK → `LCTOCTB`) | Sim no dataset | Liga à linha | Escopo da fonte | Futuro | — |
| `debitParticipantCode` | `CODIGOPARTDEB` (FK → `PARTICIPANTE`) | Não | Código do participante do lado débito | — | Futuro, só código | Cobertura zero observada; cadastro do participante contém dado pessoal (O14) |
| `creditParticipantCode` | `CODIGOPARTCRED` | Não | Idem crédito | — | Futuro | Idem |

Participante **nunca** é requisito para publicar, nem define contrapartida.

### 6.6 Centro de custo / rateio (opcional)

| Campo | Origem | Obrig.? | Semântica | Identidade/escopo | Persistir? | Aberto |
| --- | --- | --- | --- | --- | --- | --- |
| `sourceEntryKey` | `LCTOGER.CHAVELCTOCTB` (FK → `LCTOCTB`) | Sim no dataset | Liga à linha | Escopo da fonte | Futuro | — |
| `sourceAccountId` | `LCTOGER.CONTACTB` | Sim no dataset | Conta rateada (lado) | — | Futuro | Como o lado é determinado (Q91 sem dados) |
| `costCenterCode` | `LCTOGER.CODIGOCENTROCUSTO` | Sim no dataset | Centro de custo | — | Futuro | Vínculo com `ResultCenter` FINEMP (O7) |
| `natureCode` | `NATURLCTOCTB` | Sim no dataset | Código cru | — | Futuro | Significado |
| `amount` | coluna de valor de `LCTOGER` (nome não confirmado) | Sim no dataset | Valor rateado | — | Futuro | Nome da coluna; se fecha com a linha por lado (T-F1 sem dados) |

A configuração conta → CC (`PLESPECCENCUSRELAC`, `RATCENTROCUSTO`) **não** é rateio efetivo e não entra no contrato de movimento. Zero dados observados: dataset fora do v2 inicial; **nunca** é requisito de publicação.

### 6.7 Metadados de origem (catálogo)

| Campo | Origem | Obrig.? | Semântica | Identidade/escopo | Persistir? | Aberto |
| --- | --- | --- | --- | --- | --- | --- |
| `originCode` | `ORIGEMLCTOCTB.CODIGOORIGLCTOCTB` | Sim no dataset | Código | Escopo da fonte | Sim | — |
| `description` | descrição do catálogo (`FI` = Fiscal, `FP` = Folha de Pagamento) | Sim no dataset | Texto do produto Questor | — | Sim | Leitura exige `GRANT SELECT` em `ORIGEMLCTOCTB` (falhou na primeira tentativa) |

O catálogo é capability opcional (`ORIGIN_CATALOG`). Sem permissão de leitura, o lote vai sem catálogo e a UI mostra só o código. **Nenhuma descrição fica fixa no código FINEMP.**

### 6.8 Identidade e sincronização

| Conceito | Representação | Status |
| --- | --- | --- |
| Escopo da fonte | `accounting_source_id` (FINEMP) ↔ empresa Questor (`CODIGOEMPRESA`), conferido pela identidade observada (CNPJ) antes de ler | FATO (v1) |
| Conta | `(accounting_source_id, sourceAccountId)` | FATO (V19) |
| Linha da origem | `(accounting_source_id, sourceEntryKey)` | PROPOSTA (hoje implícita) |
| Lado persistido | `(accounting_source_id, sourceEntryKey, side)` | FATO (V19), mantido |
| Operação | `operationGroupKey` (`CHAVEORIGEM`), atributo de agrupamento | PROPOSTA; não é identidade |
| Transação | `transactionKey` (`TRANSCTB`), opcional | PROPOSTA |
| Totais D/C | `(source, estab, conta, dia/mês, entryType)` | PROPOSTA; unicidade da origem ABERTA |
| Alteração na origem | v1: mesma chave + conteúdo diferente → `DATA_CONFLICT` (falha fechada) | Mantido até O3 |
| Exclusão na origem | Sem estratégia. `LCTOCTBEXCLUIDO` existe, mas tem zero linhas | **ABERTO (O3)**: sem tombstone, sem delete |
| Metadados em linha já publicada (v1) | **Anexo único:** se o núcleo é idêntico e o metadado está ausente, grava; se já existe e difere → `DATA_CONFLICT` | PROPOSTA |

### 6.9 Cobertura temporal

O modelo "última SyncRun" não representa o que está armazenado: um sync de agosto seguido de um de setembro deixa agosto fora da "cobertura", mas os dados existem (achado de revisão na #455). O v2 separa os conceitos:

```java
// PROPOSTA — tabela de intervalos, atualizada na mesma transação da publicação.
record AccountingDatasetCoverage(
    UUID accountingSourceId,
    Dataset dataset,                 // CHART | MOVEMENTS | DAILY_TOTALS | MONTHLY_TOTALS (| ORIGIN_CATALOG)
    String establishmentCode,        // null = todos os estabelecimentos lidos
    LocalDate coveredFrom,           // intervalo publicado com sucesso (fechado)
    LocalDate coveredTo,
    UUID lastSyncRunId,
    Instant publishedAt) { }
```

- **Intervalos, não só mínimo/máximo.** Cada publicação de dataset com período insere `[start, end]`, que é fundido com intervalos adjacentes ou sobrepostos da mesma `(source, dataset, establishment)`. Lacunas continuam visíveis.
  - Mínimo e máximo são derivados.
  - Mês sem movimento dentro da cobertura → "sem movimento".
  - Mês fora dela → "sem histórico importado".
- **`CHART` não tem período:** guarda só `publishedAt` (snapshot do plano).
- **Totais D/C:** cobertura por mês completo. Um mês parcial não conta como coberto para `MONTHLY_TOTALS`.
- **Cobertura ≠ atualizado.** Enquanto O3 estiver aberto, um intervalo coberto significa "importado ao menos uma vez sem conflito", não "igual à origem agora". A API expõe o significado no nome (`importedFrom` / `importedTo`) e nunca como "sincronizado até".
- **Dados v1 existentes:** a cobertura inicial é reconstruída das SyncRuns SUCCEEDED (início e fim de cada uma), dataset `MOVEMENTS`. Não há outro dado confiável.

## 7. Invariantes

### 7.1 Universais (validador canônico, todas as fontes)

1. `sourceEntryKey`, `sourceAccountId`, `classificationCode` e `description` não vazios. Textos dentro dos limites (≤ 255; `historyText` ≤ 300; `documentNumber` ≤ 40).
2. Uma entry tem **pelo menos um** lado e `debitAccountId ≠ creditAccountId`.
3. `amount` é decimal exato com escala ≤ 4 e até 15 dígitos inteiros. Nunca é arredondado.
4. **Sinal segue a capability:**
   - `NON_NEGATIVE_WITH_SIDE` → `amount ≥ 0`;
   - `SIGNED_WITH_SIDE` → qualquer sinal, preservado.
   - O validador **não** inverte lado nem muda valor.
5. `accountingDate`, `day` e `month` dentro do período do SyncRun.
6. Toda conta referenciada por entry ou total existe no `chart` do mesmo lote. Se a fonte não declara `CHART` (modo compat. v1), existe nos descritores do lote.
7. Identidades únicas no lote. Repetição idêntica é tolerada; divergente → `DATA_CONFLICT`.
8. Totais D/C: `debitTotal ≥ 0`, `creditTotal ≥ 0`. **Nenhum saldo é derivado na publicação.**
9. Datasets opcionais ausentes nunca falham a publicação, e nunca viram zero ou vazio "confirmado".

### 7.2 Fronteira Questor (reader + agente), decisão proposta para #449

- **Onde:** `FirebirdQuestorAccountingReader` (método de mapeamento de linha), ao ler `VALORLCTOCTB`. Vale para as duas rotas que usam o mesmo `readOn`: servidor (`VerifiedQuestorSnapshotReader`) e agente (`ConnectorAgentCycle`).
- **Regra:** `VALORLCTOCTB < 0` → `QuestorUnexpectedSourceDataException`.
  - A mensagem é constante e não traz valor, chave ou conta.
  - A leitura é abortada e **nada é publicado**.
  - Sem inverter lado, sem alterar valor, sem descartar a linha.
- **Propagação:**
  - `VerifiedQuestorSnapshotReader` relança a exceção antes do `catch` genérico (como `AccountingReadLimitExceededException`).
  - No servidor, vira `SyncFailureCode.INVALID_PAYLOAD`.
  - No agente, vai em `POST /fail {code: INVALID_PAYLOAD}`, código já aceito por `decodeFailure`. Hoje `ConnectorAgentCycle` só distingue identidade e limite de leitura; o tratamento de `ExecutionException` precisa reconhecer a nova exceção, senão ela cai no caso genérico.
- **Por que não no validador canônico:** ele é compartilhado. Fontes futuras podem ter sinal legítimo (`SIGNED_WITH_SIDE`). A regra é da **fonte Questor**, não do domínio.
- **Defesa em profundidade:** o validador v2 aplica a capability declarada (invariante 4), e a DRE mantém o 422 (#451) para dados v1 já persistidos.
- **Também na fronteira Questor (v2):**
  - lado vem só de qual coluna está preenchida;
  - linha sem nenhum lado ou com débito = crédito → mesma falha fechada;
  - conta da linha ausente do `PLANOESPEC` lido → mesma falha. Isso substitui o descarte silencioso do inner join atual. FATO: 0 ocorrências observadas, e a FK existe.

A correção mínima de #449 **não** faz parte desta fase. É a primeira PR do C2 (seção 10), com TDD.

## 8. Compatibilidade com o v1

1. **Wire versionado.**
   - O decoder v1 é estrito; o v2 vai em payload separado com `contractVersion: 2`, aceito pelo servidor **além** do v1.
   - A resposta de lease passa a informar as versões e os datasets aceitos.
   - Connector antigo continua publicando v1 até ser atualizado; connector novo só envia v2 se o servidor anunciar.
2. **Domínio.**
   - O v2 é convertido no servidor para as linhas por lado existentes (`accounting_movements`).
   - DRE, De-Para e Composição continuam funcionando sem mudança. O v1 é convertido para o núcleo v2 com metadados ausentes.
3. **Persistência aditiva.**
   - Colunas novas nullable (`establishment_code`) e tabelas novas: atributos de conta, metadados de linha, totais diário/mensal, catálogo de origem, cobertura.
   - Nenhuma coluna existente muda de significado. `history` só tem o limite ampliado.
4. **Plano completo.** Contas sem movimento passam a existir em `accounting_accounts`.
   - A tela De-Para hoje diz "contas importadas (não é o plano completo)". Com `CHART` coberto, o texto muda para "plano completo da fonte", e só então.
   - Fontes sem `CHART` mantêm o aviso.
5. **Dados antigos.** Linhas v1 não são reinterpretadas. Metadados chegam por re-sync, usando o anexo único (6.8). Sem backfill silencioso.
6. **DRE.** Sem mudança no C2. O prefixo `1` continua.
   - **Base histórica do sinal preservada.** Hoje a DRE lê o `accountKey` atual da conta para o sinal (`DreMovementValueCalculator`). Se um snapshot posterior pudesse mudar a classificação, totais de meses já publicados mudariam de sinal sem aviso.
   - Por isso, no C2:
     - o `accountKey` de uma conta já publicada é **congelado**;
     - uma **mudança de `classificationCode`** em conta existente vinda do `CHART` é `DATA_CONFLICT` (falha fechada, com a conta identificada no evento de SyncRun), não atualização silenciosa;
     - mudanças de `description` e dos códigos crus continuam permitidas e auditadas, porque não alteram o cálculo.
   - Aceitar reclassificação na origem exige decisão de produto e uma base de sinal versionada por período (por exemplo, classificação vigente por data). Fica fora do C2.
   - Substituir por `NATURSALDO` exige confirmar o significado do código e é decisão de produto.
   - A DRE nativa do Questor **não** substitui a classificação FINEMP.

## 9. Mudanças previstas para o C2 (lista exata)

| Camada | Mudança |
| --- | --- |
| **Reader Questor** | (a) #449: falha fechada para negativo, linha sem lado, débito = crédito e conta fora do plano. (b) Leitor de plano completo (`PLANOESPEC` por empresa). (c) Uma linha por `LCTOCTB` (sem `UNION ALL`), com os metadados da 6.2. (d) Leitores de `SALDOCTB` e `SALDOCTBMENSAL` no período. (e) Catálogo `ORIGEMLCTOCTB` opcional, tolerando falta de permissão sem falhar o lote. (f) Todos na mesma sessão `REPEATABLE_READ` somente leitura e com orçamento de linhas por dataset |
| **Connector (agente)** | Monta o lote v2 com capabilities; respeita as versões anunciadas pelo servidor; orçamento de bytes por dataset (plano com ~2 mil contas cabe; totais podem crescer com o período) |
| **Transporte** | Decoder v2 estrito (campos conhecidos por dataset); limites de tokens/bytes recalculados; v1 aceito em paralelo; resposta de lease com versões/datasets |
| **Validação** | Validador v2 com as invariantes 7.1 e a capability de sinal; conversão v2 → linhas por lado |
| **Persistência (PostgreSQL/Flyway)** | Tabelas: `accounting_account_source_attributes`, `accounting_entry_source_metadata`, `accounting_daily_debit_credit_totals`, `accounting_monthly_debit_credit_totals`, `accounting_origin_catalog`, `accounting_dataset_coverage`; coluna `establishment_code` nullable em `accounting_movements`; tudo escopado por `accounting_source_id` → Company, com constraints de identidade da seção 6 |
| **Publicação** | Datasets e cobertura na mesma transação atômica; anexo único de metadados; `CHART` como upsert de atributos por `sourceAccountId`: descrição e códigos crus atualizam com evento auditável; **mudança de classificação em conta já publicada é `DATA_CONFLICT`**, e o `accountKey` legado fica congelado (8.6) |
| **API** | Plano completo com códigos crus + interpretação `INFERRED`; metadados na busca de movimentos/Composição; totais D/C por conta/mês; cobertura por dataset (`importedFrom`/`importedTo`, intervalos) |
| **Frontend** | De-Para com plano completo e indicação sintética/analítica marcada "inferida"; Composição com origem, lote, documento, `CHAVEORIGEM`; DRE distinguindo "sem histórico importado" de "sem movimento" pela cobertura acumulada; nenhuma tela de saldo |

## 10. Divisão do C2 em Issues/PRs pequenos

| # | PR | Depende de | Observação |
| --- | --- | --- | --- |
| C2.0 | #449: invariantes da fronteira Questor no reader atual (negativo, sem lado, D = C, conta fora do plano) + mapeamento `INVALID_PAYLOAD` | — | Pequena; TDD com JDBC sintético; não muda contrato |
| C2.1 | Tipos v2 + `SourceCapabilities` + validador v2 + conversão v2 → linhas por lado (puro, sem wiring) | — | Testes unitários das invariantes 7.1 |
| C2.2 | Flyway + entidades + repositórios das tabelas novas; isolamento por fonte/Company | C2.1 | TDD de isolamento e constraints |
| C2.3 | Cobertura acumulada: tabela de intervalos, fusão, backfill a partir das SyncRuns v1, leitura na DRE (substitui `SyncRunDreCoverageReader`) | C2.2 | Resolve o achado da #455 |
| C2.4 | Reader Questor v2: plano completo + linha única com metadados (JDBC sintético) | C2.0, C2.1 | Sem totais ainda |
| C2.5 | Reader Questor: `SALDOCTB`/`SALDOCTBMENSAL` + catálogo opcional | C2.4 | Contrato sintético inclui "catálogo sem permissão" |
| C2.6 | Wire v2: decoder, budgets, negociação de versão na lease; agente envia v2 | C2.1, C2.4 | v1 continua aceito |
| C2.7 | Publicação v2 atômica: datasets + cobertura + anexo único | C2.2, C2.3, C2.6 | Contrato ponta a ponta do protocolo |
| C2.8 | API: plano completo, metadados, totais, cobertura | C2.7 | — |
| C2.9 | Frontend: De-Para completo, Composição com metadados, DRE com cobertura | C2.8 | Mantém o layout do protótipo |
| Depois | Participante, rateio, totais por histórico, exclusão/O3, encerramento, DRE nativa | Evidência/decisão | Fora do C2 |

## 11. Deliberadamente aberto

- **O3 — alteração/exclusão na origem.**
  - Não há dados em `LCTOCTBEXCLUIDO` nem semântica confirmada de `DATAHORALCTOCTB`.
  - Sem tombstone, delete ou sync incremental. Conflito continua falha fechada.
- **Significado dos códigos** `TIPOCONTA` (inferido, não documentado), `TIPOCONTACALC`, `NATURSALDO`, `CTRLSALDO`, `EMITIRBALANCO`, `TIPOLANCAMENTO` (só `LN`) e `ORIGEMDADO` (só `1`).
- **Saldo inicial/final:** não há coluna de saldo; só totais D/C. Qualquer saldo exige fórmula e início de escrituração comprovados.
- **Encerramento:** nenhum identificador comprovado. Não classificar por 31/12, `FP`, origem ou lote.
- **Contrapartida:** `CHAVEORIGEM`/`TRANSCTB` agrupam operações que fecham, mas não definem par débito ↔ crédito em grupos com várias linhas.
- **Participante e centro de custo/rateio:** cobertura zero; nomes de coluna de valor de `LCTOGER` não confirmados; dados pessoais do participante (O14); vínculo CC ↔ `ResultCenter` (O7).
- **DRE nativa do Questor:** existe (`DRE`, `DREDETALHE`, `CFGDRE`) mas sem vínculo para a empresa. A relação com a DRE FINEMP é decisão de produto (O2/O21).
- **Reclassificação de conta na origem:** hoje falha fechada (8.6). Aceitá-la exige decisão de produto e base de sinal da DRE versionada por período.
- **Unicidade da origem para os totais D/C:** PK de `SALDOCTB`/`SALDOCTBMENSAL` não registrada.
- **Generalização:** tudo foi observado em **uma** empresa de **uma** instalação. Nenhuma regra nova vira obrigatória sem falha fechada e mensagem clara quando a evidência não se repetir.
