# C1b — Descoberta direcionada da origem Questor/Firebird

Issue: matos795/Finemp#457 (filha de #433). Relacionadas: #449 (valores negativos), O3/O7/O14 do [GAP](../reference/GAP_CONTABIL.md). Fase anterior: [C1 fase 1](questor-c1-discovery.md).

C1b é **investigação**. Não altera Connector, reader, persistência, Flyway, API, frontend nem cálculo da DRE. Não começa C2.

O kit responde só às perguntas que a fase 1 deixou com candidatos concretos. Não repete o catálogo inteiro:

- **SQL:** [`questor-c1b-discovery.sql`](questor-c1b-discovery.sql), consultas Q30–Q93.
- **Modelos:** T-B1, T-B2, T-E1, T-F1 e T-D1, preenchidos depois do bloco 0. Ficam neste runbook.

## Fato confirmado pela fase 1

A fase 1 foi executada em uma instalação real: Firebird 2.5.8, sessão somente leitura, uma empresa, período de 2025-01 a 2025-05. Os números abaixo vêm da saída sanitizada. Nenhum código de empresa, nome ou valor monetário é registrado aqui.

### Plano de contas (`PLANOESPEC`)

- **Volume e cobertura:**
  - 1.851 contas distintas na empresa; `CONTACTB` é única por empresa (PK `CODIGOEMPRESA, CONTACTB`).
  - 73 contas tiveram movimento no período. As 73 existem no plano, nenhuma conta movimentada está fora dele, e 1.778 contas não tiveram movimento.
  - O plano completo **não** pode ser deduzido de `LCTOCTB`.
- **Classificação:**
  - Há 1.480 classificações distintas para 1.851 contas: `CLASSIFCONTA` **não é única**.
  - Distribuição de comprimento × pontos:

    | Comprimento | Pontos | Contas |
    | --- | --- | --- |
    | 1 | 0 | 4 |
    | 3 | 1 | 15 |
    | 6 | 2 | 51 |
    | 10 | 3 | 243 |
    | 14 | 4 | 1.422 |
    | 18 | 5 | 116 |

- **Prefixo (hipótese de pai):** 242 contas são prefixo de outra classificação e **nenhuma** delas teve movimento no período. Isso é indício de conta agregadora; a regra ainda não está confirmada.
- **Colunas confirmadas:** `CODIGOEMPRESA` SMALLINT, `CONTACTB` NUMERIC(10,0), `TIPOCONTA`, `CLASSIFCONTA` VARCHAR(30), `DESCRCONTA`, `TIPOCONTACALC`, `EMITIRBALANCO`, `NATURSALDO`, `CTRLSALDO`, `CODIGODRE`, `CONTADRE`, entre outras.
- **Vínculo com DRE:** FK `(CODIGODRE, CONTADRE)` → `DRE`. `PLANOESPECPLANODRE` liga conta → `DRE`.

### Lançamentos (`LCTOCTB`)

- **Estrutura:**
  - 27 colunas; PK `(CODIGOEMPRESA, CHAVELCTOCTB)`.
  - `VALORLCTOCTB` é NUMERIC(14,2) NOT NULL.
  - `CONTACTBDEB` e `CONTACTBCRED` são NUMERIC(10,0) e anuláveis, com FK para `PLANOESPEC`.
- **Linhas no período:** 476 linhas, todas com chave distinta, uma linha por chave e uma data por chave.
  - Formato das linhas: 242 com débito e crédito, 98 só débito, 136 só crédito. Nenhuma sem conta e nenhuma com débito = crédito.
  - As 234 chaves de um lado só **não fecham** dentro da própria chave. Se há agrupamento de partidas, ele está em outro campo.
- **Escopo da chave:** as 476 chaves também aparecem em outra empresa. A chave não é global, e a identidade externa tem de ficar escopada por empresa/fonte.
- **Mecanismos de geração e exclusão (nomes vistos no banco):**
  - Triggers em `LCTOCTB`: `LCTOCTBBI`, `LCTOCTBBICHAVE`, `LCTOCTBBU`, `LCTOCTBAU`, `LCTOCTBAI`, `LCTOCTBBD` (antes de excluir).
  - Geradores `DYN_GENCHAVELCTOCTB_n`.
  - Sem o código dos triggers, o que eles fazem não está confirmado.
- **Histórico da empresa:** todos os meses de 2023-01 a 2025-05 têm movimento. Dezembro/2023: 31 linhas, todas em 31/12. Dezembro/2024: 104 linhas, 52 em 31/12. Isso é só **indício** de encerramento.

### Valores negativos (#449)

Nenhum `VALORLCTOCTB < 0`, nem na empresa nem no banco inteiro:

| Ano | Linhas | Negativas |
| --- | --- | --- |
| 2019 | 6 | 0 |
| 2020 | 4.733 | 0 |
| 2021 | 852 | 0 |
| 2022 | 33.561 | 0 |
| 2023 | 199.574 | 0 |
| 2024 | 86.214 | 0 |
| 2025 | 36.905 | 0 |

### Relacionamentos por FK (Q07)

| Tema | Tabela | Liga a | Leitura |
| --- | --- | --- | --- |
| Participante | `LCTOCTBPARTICIPANTE` | `LCTOCTB (CODIGOEMPRESA, CHAVELCTOCTB)`; `PARTICIPANTE` por `CODIGOPARTDEB` e `CODIGOPARTCRED` | Candidato forte; cobertura desconhecida |
| Saldo | `SALDOCTB`, `SALDOCTBMENSAL`, `SALDOCTBMENSALHIST` (+ variantes fiscal, moeda, societário) | `ESTAB (empresa, estab)`, `PLANOESPEC (empresa, conta)`; colunas conhecidas: `DATASALDO` (e `CODIGOHISTCTB` no HIST) | Colunas de valor **desconhecidas** |
| Centro de custo (configuração) | `PLESPECCENCUSRELAC`, `RATCENTROCUSTO`, `PLESPECCENCUS` | `PLANOESPEC` ↔ `CENTROCUSTO` | Configuração conta → CC; não prova CC do lançamento |
| Centro de custo (lançamento) | `LCTOGER` | `LCTOCTB (CODIGOEMPRESA, CHAVELCTOCTB)` + `CENTROCUSTO` + `PLANOESPEC (CONTACTB)`; colunas `NATURLCTOCTB`, `ORIGEMDADO` | **Candidato forte** de CC por lançamento; cobertura desconhecida |
| Exclusão | `LCTOCTBEXCLUIDO` (com `IDLCTOCTBEXCL`); `LCTOGEREXCLUIDO` → `LCTOCTBEXCLUIDO.IDLCTOCTBEXCL` | Mesmas FKs de `LCTOCTB` menos a PK | Candidato para exclusões (O3) |
| Lote/origem | `LCTOCTB` → `LOTECTB (empresa, lote)` e `ORIGEMLCTOCTB`; `LOTECTB` → `ORIGEMLCTOCTB` | `LOTECTB` tem `SITUACAOLOTECTB`, `DATAINICIALLOTECTB`, `DATAFINALLOTECTB`, `VALORLOTECTB` | Candidato de agrupamento |
| Encerramento | `CFGZERAMENTOCTB` | `CONTACTBCONPART` → `PLANOESPEC`; `CODIGOHISTCTBCONPART`, `CODIGOHISTCTBCONREST` → `HISTORICOCTB` | Configuração de zeramento; ainda não ligada aos lançamentos |
| DRE do Questor | `DRE (CODIGODRE, CONTADRE)` → `CFGDRE`; `DREDETALHE` → `DRE` | `DRE` tem `NATUREZACONTADRE`, `SALDOANTERIOR`, `TIPOSALDOCONTACTB` | Modelo de DRE da origem; relação com a DRE FINEMP não avaliada |

## Regras de segurança

Valem as mesmas regras da [fase 1](questor-c1-discovery.md#regras-de-segurança):

- Usuário somente leitura, de preferência em uma cópia restaurada.
- Senha só no ambiente da sessão.
- Nenhuma senha, URL JDBC, servidor ou caminho `.fdb` no resultado.

O arquivo abre `SET TRANSACTION READ ONLY ISOLATION LEVEL SNAPSHOT`, termina em `ROLLBACK` e contém apenas `SELECT`.

A saída traz só:

- nomes de tabela/coluna;
- códigos técnicos não pessoais (`TIPOCONTA`, `TIPOLANCAMENTO`, `CODIGOORIGLCTOCTB`, `ORIGEMDADO`, `NATURLCTOCTB`, `SITUACAOLOTECTB` etc.);
- datas mínima e máxima;
- contagens.

Comparações de valor (Q72, Q74, T-B1, T-B2, T-F1) devolvem **só** contagens: FECHA/NAO_FECHA, BATE/NAO_BATE, IGUAL/DIFERENTE. Nenhuma consulta seleciona:

- descrição de conta, histórico ou complemento;
- número de documento, `CHAVEORIGEM` ou nome/código de participante;
- descrição de lote ou valor monetário individual.

Antes de enviar o resultado, troque o código da empresa por `EMPRESA_A`.

## Passo a passo

### 1. Conferir o arquivo

```bash
grep -n -i -E '\b(insert|update|delete|merge|execute|create|alter|drop|recreate|grant|revoke|commit|truncate)\b' questor-c1b-discovery.sql
```

A saída deve ser **vazia**.

### 2. Rodar sem substituir (opcional)

Sem substituição, só o bloco 0 (metadados) retorna linhas. Todas as consultas de dados filtram a empresa `-1`.

### 3. Substituir os marcadores

Use a mesma empresa e um período de 12 meses que inclua um dezembro:

```bash
sed -e 's/CAST(-1 AS INTEGER) AS EMP/CAST(123 AS INTEGER) AS EMP/' -e "s/DATE '1900-01-01' AS INI/DATE '2024-06-01' AS INI/" -e "s/DATE '1900-01-31' AS FIM/DATE '2025-05-31' AS FIM/" questor-c1b-discovery.sql > c1b-local.sql
```

### 4. Executar

```bash
isql -q -m -i c1b-local.sql -o resultado-c1b.txt servidor:/caminho/base.fdb
```

Consultas mais pesadas:

- **Q85** compara pares de linhas do período.
- **Q62, Q75 e Q76** percorrem o histórico inteiro da empresa.
- **Q83** procura `IDSYN` em outras empresas.

Se alguma passar do tempo aceitável, remova-a e anote no retorno.

### 5. Preencher e rodar os modelos

Depois de ler Q30, preencha os modelos abaixo **somente** com nomes de coluna que apareceram em Q30. Rode cada um no mesmo arquivo, entre `SET TRANSACTION READ ONLY ISOLATION LEVEL SNAPSHOT;` e `ROLLBACK;`.

### 6. Sanitizar e devolver

Use o checklist e devolva `resultado-c1b.txt` sanitizado na #457.

## O que cada bloco responde

| Bloco | Consultas | Pergunta |
| --- | --- | --- |
| 0. Metadados | Q30–Q33 | Colunas, tipos, índices e triggers das tabelas candidatas. Quais tabelas têm `CODIGOCENTROCUSTO` junto com `CHAVELCTOCTB`, `CODIGOLOTECTB` ou `CONTACTB` |
| A. Plano e hierarquia | Q40–Q48 | Distribuição de `TIPOCONTA`, `TIPOCONTACALC`, `NATURSALDO`, `CTRLSALDO`, `EMITIRBALANCO`; preenchimento de `CODIGODRE`/`CONTADRE`; `TIPOCONTA` × comprimento × pontos; classificações repetidas; pai por prefixo (nenhum, único ou ambíguo); conta com filho × movimento; `PLANOESPECPLANODRE` × `PLANOESPEC`; tamanho dos modelos `DRE` usados |
| B. Saldos | Q50–Q54 + T-B1/T-B2 | Linhas, contas, estabelecimentos e primeira/última data por tabela; competências; duplicidade por empresa/estab/conta/data; contas com saldo × `TIPOCONTA` × movimento; saldo no dia ou no mês do movimento; se a variação do saldo bate com o líquido dos movimentos |
| C. Participante | Q60–Q62 | Registros por lançamento (0, 1 ou vários), preenchimento de `CODIGOPARTDEB`/`CODIGOPARTCRED` × lado da linha, cobertura por ano |
| D. Origem/lote/documento/encerramento | Q70–Q76 + T-D1 | Combinação `TIPOLANCAMENTO` × `CODIGOORIGLCTOCTB` × `ORIGEMDADO`; preenchimento de lote, documento, `CHAVEORIGEM`, `TRANSCTB`, `IDSYN`; **qual campo agrupa partidas** (grupos que fecham); linhas por lote; `LOTECTB` (situação, origem, se o valor fecha com **todas** as linhas do lote, inclusive fora do período); códigos em 31/12 × resto de dezembro × demais meses; uso da conta e dos históricos de `CFGZERAMENTOCTB` |
| E. Identidade/exclusão (O3) | Q80–Q85 + T-E1 | Volume de `LCTOCTBEXCLUIDO`, chave excluída que ainda existe em `LCTOCTB`, repetição da chave na exclusão, cardinalidade e escopo de `IDSYN`/`CHAVEORIGEM`, data de gravação × competência, chave crescente com a gravação |
| F. Centro de custo | Q90–Q93 + T-F1 | `LCTOGER` por lançamento (0, 1 ou vários) e por formato; conta de `LCTOGER` × lado da linha; `NATURLCTOCTB`; configuração (`PLESPECCENCUSRELAC`, `RATCENTROCUSTO`) × movimento (`LCTOGER`) por conta; se a soma dos CC fecha com a linha |

Cuidados de leitura:

- **Q44 e Q45 são teste de hipótese.** Só viram regra de hierarquia se `TIPOCONTA` e a ausência de movimento confirmarem o padrão. Mesmo assim, a regra é levada à #457 antes de qualquer código.
- **Q72 testa hipóteses de agrupamento.** Um campo em que os grupos com linhas de um lado só passam a fechar é candidato a agrupar partidas. Isso **não** define contrapartida.
- **Q75 e Q76 comparam distribuições.** Encerramento só é confirmado com código/origem técnica que separe 31/12 de forma consistente, ou com o uso da configuração de zeramento.

## Modelos (depois do bloco 0)

Substitua `<<...>>` pelo nome exato de coluna mostrado em Q30. Se a coluna não existir, o modelo não se aplica: anote "não aplicável".

### T-B1 — a variação do saldo bate com o líquido dos movimentos?

Use quando a tabela de saldo tiver **uma** coluna de saldo acumulado. Rode uma vez por tabela candidata (`SALDOCTBMENSAL`, `SALDOCTB`, `SALDOCTBMENSALHIST`).

**Pré-requisito: Q52.** A comparação é feita no grão `(empresa, estabelecimento, conta, data)`.

- Se Q52 mostrar mais de uma linha por chave, a tabela tem outra dimensão. `SALDOCTBMENSALHIST`, por exemplo, tem `CODIGOHISTCTB`.
- Nesse caso as linhas repetidas não são somadas nem escolhidas. Elas caem em `6_CHAVE_NAO_UNICA`.
- Se `6` dominar, anote "não aplicável (chave não única)" e devolva a saída de Q30 para essa tabela.

A consulta compara `saldo(data) − saldo(data anterior)` com débitos e créditos de `LCTOCTB` da mesma conta e do mesmo estabelecimento no intervalo `(data anterior, data]`. Devolve só a contagem de linhas de saldo em cada resultado:

- `1_BATE_DEB_MENOS_CRED`: a variação bate com débitos − créditos;
- `2_BATE_CRED_MENOS_DEB`: a variação bate com créditos − débitos;
- `3_BATE_SEM_MOVIMENTO`: sem movimento, sem variação;
- `4_NAO_COMPARAVEL`: sem saldo anterior;
- `5_NAO_BATE`: nenhuma das anteriores;
- `6_CHAVE_NAO_UNICA`: a chave atual ou a anterior aparece mais de uma vez.

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE),
R AS (
    SELECT C.CODIGOESTAB, C.CONTACTB, C.DATASALDO, C.<<COL_SALDO>> AS SALDO_ATUAL,
        (SELECT COUNT(*) FROM <<TABELA_SALDO>> A
          WHERE A.CODIGOEMPRESA = C.CODIGOEMPRESA AND A.CODIGOESTAB = C.CODIGOESTAB
            AND A.CONTACTB = C.CONTACTB AND A.DATASALDO = C.DATASALDO) AS REPETICOES_ATUAL,
        (SELECT MAX(A.DATASALDO) FROM <<TABELA_SALDO>> A
          WHERE A.CODIGOEMPRESA = C.CODIGOEMPRESA AND A.CODIGOESTAB = C.CODIGOESTAB
            AND A.CONTACTB = C.CONTACTB AND A.DATASALDO < C.DATASALDO) AS DATA_ANTERIOR
    FROM <<TABELA_SALDO>> C CROSS JOIN P
    WHERE C.CODIGOEMPRESA = P.EMP AND C.DATASALDO BETWEEN P.INI AND P.FIM
),
V AS (
    SELECT R.*,
        (SELECT COUNT(*) FROM <<TABELA_SALDO>> A CROSS JOIN P
          WHERE A.CODIGOEMPRESA = P.EMP AND A.CODIGOESTAB = R.CODIGOESTAB
            AND A.CONTACTB = R.CONTACTB AND A.DATASALDO = R.DATA_ANTERIOR) AS REPETICOES_ANTERIOR,
        (SELECT MAX(A.<<COL_SALDO>>) FROM <<TABELA_SALDO>> A CROSS JOIN P
          WHERE A.CODIGOEMPRESA = P.EMP AND A.CODIGOESTAB = R.CODIGOESTAB
            AND A.CONTACTB = R.CONTACTB AND A.DATASALDO = R.DATA_ANTERIOR) AS SALDO_ANTERIOR,
        (SELECT COALESCE(SUM(L.VALORLCTOCTB), 0) FROM LCTOCTB L CROSS JOIN P
          WHERE L.CODIGOEMPRESA = P.EMP AND L.CODIGOESTAB = R.CODIGOESTAB AND L.CONTACTBDEB = R.CONTACTB
            AND L.DATALCTOCTB > R.DATA_ANTERIOR AND L.DATALCTOCTB <= R.DATASALDO) AS MOV_DEB,
        (SELECT COALESCE(SUM(L.VALORLCTOCTB), 0) FROM LCTOCTB L CROSS JOIN P
          WHERE L.CODIGOEMPRESA = P.EMP AND L.CODIGOESTAB = R.CODIGOESTAB AND L.CONTACTBCRED = R.CONTACTB
            AND L.DATALCTOCTB > R.DATA_ANTERIOR AND L.DATALCTOCTB <= R.DATASALDO) AS MOV_CRED
    FROM R
)
SELECT
    CASE
        WHEN V.REPETICOES_ATUAL > 1 OR V.REPETICOES_ANTERIOR > 1 THEN '6_CHAVE_NAO_UNICA'
        WHEN V.DATA_ANTERIOR IS NULL OR V.SALDO_ANTERIOR IS NULL OR V.SALDO_ATUAL IS NULL THEN '4_NAO_COMPARAVEL'
        WHEN V.SALDO_ATUAL - V.SALDO_ANTERIOR = V.MOV_DEB - V.MOV_CRED
         AND V.SALDO_ATUAL - V.SALDO_ANTERIOR = V.MOV_CRED - V.MOV_DEB THEN '3_BATE_SEM_MOVIMENTO'
        WHEN V.SALDO_ATUAL - V.SALDO_ANTERIOR = V.MOV_DEB - V.MOV_CRED THEN '1_BATE_DEB_MENOS_CRED'
        WHEN V.SALDO_ATUAL - V.SALDO_ANTERIOR = V.MOV_CRED - V.MOV_DEB THEN '2_BATE_CRED_MENOS_DEB'
        ELSE '5_NAO_BATE'
    END AS RESULTADO,
    COUNT(*) AS QTD_LINHAS_SALDO
FROM V
GROUP BY 1
ORDER BY 1;
```

O `MAX` em `SALDO_ANTERIOR` só evita erro quando a chave anterior se repete. Esses casos já caem em `6` antes de qualquer comparação.

Se `5_NAO_BATE` dominar, repita sem a condição `L.CODIGOESTAB = R.CODIGOESTAB`: o saldo pode estar consolidado por empresa. A tabela só serve como fonte autoritativa de saldo inicial/final se quase tudo cair em `1`, `2` ou `3` e os casos `5` tiverem explicação. Isso continua sendo decisão da #457.

### T-B2 — colunas de débito/crédito do mês batem com os movimentos?

Use quando a tabela de saldo tiver colunas separadas de débito e crédito do período. O pré-requisito é o mesmo do T-B1: Q52 com uma linha por chave. Senão, anote "não aplicável (chave não única)".

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE),
V AS (
    SELECT C.<<COL_DEB>> AS COL_DEB, C.<<COL_CRED>> AS COL_CRED,
        (SELECT COALESCE(SUM(L.VALORLCTOCTB), 0) FROM LCTOCTB L
          WHERE L.CODIGOEMPRESA = C.CODIGOEMPRESA AND L.CODIGOESTAB = C.CODIGOESTAB AND L.CONTACTBDEB = C.CONTACTB
            AND EXTRACT(YEAR FROM L.DATALCTOCTB) = EXTRACT(YEAR FROM C.DATASALDO)
            AND EXTRACT(MONTH FROM L.DATALCTOCTB) = EXTRACT(MONTH FROM C.DATASALDO)) AS MOV_DEB,
        (SELECT COALESCE(SUM(L.VALORLCTOCTB), 0) FROM LCTOCTB L
          WHERE L.CODIGOEMPRESA = C.CODIGOEMPRESA AND L.CODIGOESTAB = C.CODIGOESTAB AND L.CONTACTBCRED = C.CONTACTB
            AND EXTRACT(YEAR FROM L.DATALCTOCTB) = EXTRACT(YEAR FROM C.DATASALDO)
            AND EXTRACT(MONTH FROM L.DATALCTOCTB) = EXTRACT(MONTH FROM C.DATASALDO)) AS MOV_CRED
    FROM <<TABELA_SALDO>> C CROSS JOIN P
    WHERE C.CODIGOEMPRESA = P.EMP AND C.DATASALDO BETWEEN P.INI AND P.FIM
)
SELECT
    CASE WHEN V.COL_DEB IS NULL THEN 'NAO_COMPARAVEL' WHEN V.COL_DEB = V.MOV_DEB THEN 'BATE' ELSE 'NAO_BATE' END AS DEBITO_DO_MES,
    CASE WHEN V.COL_CRED IS NULL THEN 'NAO_COMPARAVEL' WHEN V.COL_CRED = V.MOV_CRED THEN 'BATE' ELSE 'NAO_BATE' END AS CREDITO_DO_MES,
    COUNT(*) AS QTD_LINHAS_SALDO
FROM V
GROUP BY 1, 2
ORDER BY 1, 2;
```

### T-E1 — exclusões por competência e atraso da exclusão

Use se Q30 mostrar em `LCTOCTBEXCLUIDO` uma coluna de data do lançamento e uma de data/hora da exclusão.

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP FROM RDB$DATABASE)
SELECT
    EXTRACT(YEAR FROM E.<<COL_DATA_LCTO>>) AS ANO_COMPETENCIA,
    EXTRACT(MONTH FROM E.<<COL_DATA_LCTO>>) AS MES_COMPETENCIA,
    COUNT(*) AS LINHAS_EXCLUIDAS,
    SUM(CASE WHEN (EXTRACT(YEAR FROM E.<<COL_DATA_EXCLUSAO>>) * 12 + EXTRACT(MONTH FROM E.<<COL_DATA_EXCLUSAO>>))
                - (EXTRACT(YEAR FROM E.<<COL_DATA_LCTO>>) * 12 + EXTRACT(MONTH FROM E.<<COL_DATA_LCTO>>)) >= 1
        THEN 1 ELSE 0 END) AS EXCLUIDAS_APOS_O_MES,
    MIN(E.<<COL_DATA_EXCLUSAO>>) AS PRIMEIRA_EXCLUSAO,
    MAX(E.<<COL_DATA_EXCLUSAO>>) AS ULTIMA_EXCLUSAO
FROM LCTOCTBEXCLUIDO E
CROSS JOIN P
WHERE E.CODIGOEMPRESA = P.EMP
GROUP BY 1, 2
ORDER BY 1, 2;
```

### T-F1 — o rateio de `LCTOGER` fecha com a linha, lado a lado?

Use se Q30 mostrar uma coluna de valor em `LCTOGER`.

Uma linha com débito e crédito pode ter CC nos **dois** lados. Por isso a comparação é feita separadamente:

- débito: soma das linhas de `LCTOGER` cuja `CONTACTB` é a conta de débito × `VALORLCTOCTB`;
- crédito: o mesmo com a conta de crédito.

Linhas de `LCTOGER` com conta diferente dos dois lados já são contadas em Q91.

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE),
V AS (
    SELECT L.VALORLCTOCTB AS VALOR_LINHA, L.CONTACTBDEB, L.CONTACTBCRED,
        (SELECT COUNT(*) FROM LCTOGER G
          WHERE G.CODIGOEMPRESA = L.CODIGOEMPRESA AND G.CHAVELCTOCTB = L.CHAVELCTOCTB AND G.CONTACTB = L.CONTACTBDEB) AS CC_DEB_LINHAS,
        (SELECT SUM(G.<<COL_VALOR_LCTOGER>>) FROM LCTOGER G
          WHERE G.CODIGOEMPRESA = L.CODIGOEMPRESA AND G.CHAVELCTOCTB = L.CHAVELCTOCTB AND G.CONTACTB = L.CONTACTBDEB) AS CC_DEB_SOMA,
        (SELECT COUNT(*) FROM LCTOGER G
          WHERE G.CODIGOEMPRESA = L.CODIGOEMPRESA AND G.CHAVELCTOCTB = L.CHAVELCTOCTB AND G.CONTACTB = L.CONTACTBCRED) AS CC_CRED_LINHAS,
        (SELECT SUM(G.<<COL_VALOR_LCTOGER>>) FROM LCTOGER G
          WHERE G.CODIGOEMPRESA = L.CODIGOEMPRESA AND G.CHAVELCTOCTB = L.CHAVELCTOCTB AND G.CONTACTB = L.CONTACTBCRED) AS CC_CRED_SOMA
    FROM LCTOCTB L CROSS JOIN P
    WHERE L.CODIGOEMPRESA = P.EMP AND L.DATALCTOCTB BETWEEN P.INI AND P.FIM
)
SELECT
    CASE WHEN V.CONTACTBDEB IS NULL THEN 'SEM_LADO'
         WHEN V.CC_DEB_LINHAS = 0 THEN 'SEM_CC'
         WHEN V.CC_DEB_SOMA = V.VALOR_LINHA THEN CASE WHEN V.CC_DEB_LINHAS = 1 THEN 'UM_CC_IGUAL' ELSE 'VARIOS_CC_IGUAL' END
         ELSE 'SOMA_DIFERENTE' END AS LADO_DEBITO,
    CASE WHEN V.CONTACTBCRED IS NULL THEN 'SEM_LADO'
         WHEN V.CC_CRED_LINHAS = 0 THEN 'SEM_CC'
         WHEN V.CC_CRED_SOMA = V.VALOR_LINHA THEN CASE WHEN V.CC_CRED_LINHAS = 1 THEN 'UM_CC_IGUAL' ELSE 'VARIOS_CC_IGUAL' END
         ELSE 'SOMA_DIFERENTE' END AS LADO_CREDITO,
    COUNT(*) AS LINHAS
FROM V
GROUP BY 1, 2
ORDER BY 1, 2;
```

### T-D1 (opcional) — catálogo de origens usadas no período

`ORIGEMLCTOCTB` é catálogo do produto Questor, não dado de cliente. Ainda assim, rode **só** se a política interna permitir devolver descrições de catálogo. Esta é a única forma de dar significado aos códigos de `CODIGOORIGLCTOCTB` sem inventar semântica.

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE)
SELECT O.CODIGOORIGLCTOCTB, O.<<COL_DESCRICAO_ORIGEM>> AS DESCRICAO_DO_CATALOGO
FROM ORIGEMLCTOCTB O
WHERE EXISTS (SELECT 1 FROM LCTOCTB L CROSS JOIN P
              WHERE L.CODIGOEMPRESA = P.EMP AND L.DATALCTOCTB BETWEEN P.INI AND P.FIM
                AND L.CODIGOORIGLCTOCTB = O.CODIGOORIGLCTOCTB)
ORDER BY 1;
```

## Checklist de retorno

Na saída:

- [ ] Q30–Q33: saída completa (metadados).
- [ ] Q40–Q93: saída com a empresa trocada por `EMPRESA_A` e o período indicado como "12 meses até AAAA-MM".
- [ ] T-B1 para cada tabela de saldo com coluna de saldo, ou "não aplicável".
- [ ] T-B2, T-E1 e T-F1: saída ou "não aplicável", com o nome da coluna usada.
- [ ] T-D1: executado ou "não executado (política)".
- [ ] Consultas removidas por tempo, se houver.

Antes de enviar:

- [ ] Nenhuma linha com descrição, histórico, documento, `CHAVEORIGEM`, nome ou código de participante, CNPJ/CPF ou valor monetário.
- [ ] Nenhuma senha, usuário, servidor ou caminho de base.

## Matriz de evidências

| Dado desejado | Evidência C1 | Candidato | Evidência C1b necessária | Status |
| --- | --- | --- | --- | --- |
| Plano completo (inclusive sem movimento) | 1.851 contas no plano; 73 com movimento, todas no plano; 1.778 sem movimento | `PLANOESPEC` por `CODIGOEMPRESA` | Nenhuma para existência; Q40–Q41 para atributos | **Confirmado**: o plano completo vem de `PLANOESPEC`, não de `LCTOCTB` |
| Hierarquia / analítica × sintética | Padrões de comprimento/pontos; 1.480 classificações para 1.851 contas; 242 prefixos sem movimento | `CLASSIFCONTA` + `TIPOCONTA` (+ `TIPOCONTACALC`) | Q42–Q45 (pai único/ambíguo; `TIPOCONTA` × filho × movimento) | Não confirmado |
| Natureza / controle de saldo | Colunas existem | `NATURSALDO`, `CTRLSALDO` | Q40; significado dos códigos exige documentação/tela do Questor | Não confirmado |
| Vínculo conta → DRE da origem | FK `PLANOESPEC (CODIGODRE, CONTADRE)` → `DRE`; tabela `PLANOESPECPLANODRE` | `PLANOESPEC.CODIGODRE/CONTADRE`, `PLANOESPECPLANODRE`, `DRE`, `DREDETALHE`, `CFGDRE` | Q41, Q46–Q48 | Não confirmado; relação com a DRE FINEMP não avaliada |
| Saldo inicial / final | Tabelas e FKs existem; só `DATASALDO` conhecida | `SALDOCTBMENSAL`, `SALDOCTB`, `SALDOCTBMENSALHIST` | Q30–Q31 (colunas/PK), Q50–Q54 (cobertura/granularidade/duplicidade), T-B1/T-B2 (bate com movimentos) | Não confirmado; desconhecido ≠ zero |
| Débito / crédito / valor | Colunas usadas pelo reader; 242 com os dois lados, 98 só débito, 136 só crédito | `CONTACTBDEB`, `CONTACTBCRED`, `VALORLCTOCTB` | — | Confirmado |
| Valores negativos | 0 negativos na empresa e no banco (2019–2025) | `VALORLCTOCTB` NUMERIC(14,2) NOT NULL | — | **Confirmado** para a base consultada: D/C + valor não negativo. Recomendação fail-closed na #449 |
| Agrupamento de partidas múltiplas | Chave = 1 linha; 234 chaves de um lado só não fecham na chave | `CODIGOLOTECTB`, `TRANSCTB`, `CHAVEORIGEM`, data+`NUMERODCTO`, `DATAHORALCTOCTB` | Q72, Q73, Q74 | Não confirmado |
| Contrapartida explícita / participante | FKs de `LCTOCTBPARTICIPANTE` | `LCTOCTBPARTICIPANTE.CODIGOPARTDEB/CODIGOPARTCRED` | Q60–Q62 | Não confirmado; contrapartida **não** será inferida |
| Documento | Coluna existe | `NUMERODCTO` VARCHAR(20) | Q71 (preenchimento), Q72 (agrupa?) | Não confirmado |
| Lote / origem | FKs `LCTOCTB` → `LOTECTB`, `ORIGEMLCTOCTB` | `CODIGOLOTECTB`, `CODIGOORIGLCTOCTB`, `TIPOLANCAMENTO`, `ORIGEMDADO` | Q70, Q71, Q73, Q74, T-D1 | Não confirmado |
| Encerramento | Dez/2023: 31 de 31 linhas em 31/12; dez/2024: 52 de 104 | Códigos de `LCTOCTB` + `CFGZERAMENTOCTB` (`CONTACTBCONPART`, históricos) | Q75, Q76, T-D1 | Não confirmado; data sozinha **não** define |
| Identidade estável do lançamento | PK `(CODIGOEMPRESA, CHAVELCTOCTB)`; chave não global; trigger `LCTOCTBBICHAVE` e geradores `DYN_GENCHAVELCTOCTB_n` | `(empresa/fonte, CHAVELCTOCTB)`; `IDSYN`; `CHAVEORIGEM` | Q80–Q85, T-E1 | Não confirmado diante de edição/exclusão |
| Exclusão na origem | Tabela `LCTOCTBEXCLUIDO` com `IDLCTOCTBEXCL`; trigger `LCTOCTBBD` | `LCTOCTBEXCLUIDO`, `LCTOGEREXCLUIDO` | Q80, Q81, T-E1 | Não confirmado |
| CC configurado por conta | FKs `PLESPECCENCUSRELAC`/`RATCENTROCUSTO` → `PLANOESPEC` + `CENTROCUSTO` | `PLESPECCENCUSRELAC`, `RATCENTROCUSTO` | Q92, Q93 | Configuração existe; **não** é CC do lançamento |
| CC efetivo do lançamento / rateio | FK `LCTOGER` → `LCTOCTB` + `CENTROCUSTO` + `PLANOESPEC` | `LCTOGER` (`CODIGOCENTROCUSTO`, `CONTACTB`, `NATURLCTOCTB`) | Q33, Q90, Q91, Q92, T-F1 | Não confirmado |
| Ausência de histórico × mês sem movimento | Meses 2023-01..2025-05 com movimento na empresa | Q19 da fase 1; `SALDOCTBMENSAL` por competência | Q51, T-B1 | Não confirmado |

## Depois do retorno

1. Atualizar esta matriz e a #457 com o que foi confirmado.
2. C1 fase 2: contrato canônico ampliado **só** com campos confirmados (FATO / PROPOSTA / PONTO ABERTO), com O3/O7/O14 levados à decisão de produto.
3. C2 só depois disso.

## Como este kit foi validado

- **Execução em Firebird 2.5.9.** Q30–Q93 e os modelos T-B1, T-B2, T-E1, T-F1 e T-D1 foram executados sem erro (imagem `jacobalberty/firebird:2.5-ss`), sobre um esquema **sintético** local.
  - O esquema tinha só as colunas confirmadas pela fase 1, mais colunas fictícias `SYN_*` para preencher os modelos.
  - Os resultados conferem com os dados sintéticos. Por exemplo: T-B1 classifica como `1_BATE_DEB_MENOS_CRED` duas contas montadas para isso; Q44 marca como `SEM_PAI` uma classificação sem prefixo; Q76 liga a linha de 31/12 à configuração de zeramento.
- **Limitação do 2.5 corrigida.** O Firebird 2.5 não agrupa por expressão com subconsulta. Seis consultas foram reescritas com tabela derivada.
- **Sem marcadores.** Com os marcadores sem substituição, nenhuma consulta de dados retorna linhas.
- **Somente leitura.** A busca por palavras de mutação/DDL no `.sql` volta vazia, e nenhum alias passa de 31 caracteres.
- **Escopo da validação.** Isso valida sintaxe e execução, não a origem real.
