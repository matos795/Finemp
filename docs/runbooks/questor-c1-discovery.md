# C1 — Descoberta da origem Questor/Firebird (fase 1)

Issue: matos795/Finemp#457 (filha de #433). Relacionadas: #449 (valores negativos na DRE) e O3/O7/O14 do [GAP](../reference/GAP_CONTABIL.md).

Este runbook é um **kit de investigação**. Ele não descreve campos do Questor além dos que o código atual já lê. Tudo o que não está em "Fato confirmado" continua **não confirmado** até o resultado sanitizado destas consultas voltar.

Fase 1 não muda nada no FINEMP (entidades, migrations, reader, Connector e API). O contrato canônico ampliado (C1 fase 2) e o transporte (C2) só começam com evidência.

## Fato confirmado (código em `main`)

| Origem | Colunas lidas hoje | Onde |
| --- | --- | --- |
| `LCTOCTB` | `CODIGOEMPRESA`, `CHAVELCTOCTB`, `DATALCTOCTB`, `CONTACTBDEB`, `CONTACTBCRED`, `VALORLCTOCTB`, `COMPLHIST` | `FirebirdQuestorAccountingReader` |
| `PLANOESPEC` | `CODIGOEMPRESA`, `CONTACTB`, `CLASSIFCONTA`, `DESCRCONTA` | idem |

Como o reader usa essas colunas:

- **Dois lados por linha.** Cada linha de `LCTOCTB` gera até duas linhas canônicas, por `UNION ALL`:
  - CREDIT, com a conta em `CONTACTBCRED`;
  - DEBIT, com a conta em `CONTACTBDEB`.

  As duas levam o mesmo `VALORLCTOCTB` e a mesma `CHAVELCTOCTB`.
- **Join interno com o plano.** O join com `PLANOESPEC` usa `CODIGOEMPRESA` + `CONTACTB` e é **interno**. Um lado com conta nula, ou ausente de `PLANOESPEC`, não é importado. Isso hoje acontece sem aviso.
- **Sessão somente leitura.** A sessão é `readOnly` + `REPEATABLE_READ` e faz rollback ao fechar (`QuestorReadOnlySession`).
- **Cobertura.** A cobertura que a DRE informa é só a da última SyncRun SUCCEEDED de cada fonte, não acumulada.

Tudo o mais é hipótese: outras colunas, outras tabelas, semântica de chave, saldo, documento, encerramento e centro de custo (CC).

## Regras de segurança

- **Somente leitura.**
  - O arquivo SQL contém apenas `SELECT` e abre `SET TRANSACTION READ ONLY ISOLATION LEVEL SNAPSHOT`. Dentro dessa transação o Firebird recusa qualquer escrita (`attempted update during read-only transaction`, conferido em Firebird 2.5.9).
  - O arquivo termina em `ROLLBACK`.
  - Nunca acrescente `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `EXECUTE BLOCK`, DDL ou `COMMIT`.
- **Preferir uma cópia.** Rode em uma cópia restaurada de backup (`gbak`) ou em horário sem uso. Use um usuário Firebird **somente leitura**. Nunca rode como `SYSDBA` na base de produção.
- **Credenciais.**
  - Senha via variável de ambiente da sessão (`ISC_USER`/`ISC_PASSWORD`) ou digitada. Nunca em arquivo, histórico, Issue, PR ou chat.
  - Não registrar caminho de rede que identifique o cliente.
- **O que a fase 1 devolve.** Só nomes de tabela/coluna, tipos, índices, contagens e datas mínima/máxima. Não devolve:
  - histórico (`COMPLHIST`) nem descrição de conta (`DESCRCONTA`);
  - documento, nome, CNPJ/CPF;
  - valores monetários.
- **Antes de enviar o resultado:**
  - substitua o código da empresa por `EMPRESA_A`;
  - confira que nenhuma linha traz texto livre;
  - não envie a base, o backup nem capturas de tela de dados.

## Pré-requisitos

- `isql` do Firebird 2.5 (ou superior) com acesso à base.
- Código interno da empresa no Questor (`CODIGOEMPRESA`) e um período de 12 meses com movimento.
- Este diretório copiado para a máquina do operador.

## Passo a passo

### 1. Conferir o arquivo antes de rodar

```bash
grep -n -i -E '\b(insert|update|delete|merge|execute|create|alter|drop|recreate|grant|revoke|commit|truncate)\b' questor-c1-discovery.sql
```

A saída deve ser **vazia**.

### 2. Rodar só metadados (opcional, mais seguro)

As seções Q00–Q09 não leem dados de negócio. O arquivo inteiro sem substituição também é seguro: todas as consultas de dados dependem do marcador da empresa (`-1`) e retornam vazio. Isso vale inclusive para Q13, que percorre todas as empresas só depois da substituição.

### 3. Substituir os marcadores

Troque `123`, `2025-01-01` e `2025-12-31` pelos valores reais. Esses valores ficam só na máquina do operador:

```bash
sed -e 's/CAST(-1 AS INTEGER) AS EMP/CAST(123 AS INTEGER) AS EMP/' -e "s/DATE '1900-01-01' AS INI/DATE '2025-01-01' AS INI/" -e "s/DATE '1900-01-31' AS FIM/DATE '2025-12-31' AS FIM/" questor-c1-discovery.sql > c1-local.sql
```

### 4. Executar

```bash
isql -q -m -i c1-local.sql -o resultado-c1.txt servidor:/caminho/base.fdb
```

Q13 e Q18 percorrem `LCTOCTB` de todas as empresas. Em bases grandes, rode fora do horário. Se demorarem, remova-as e anote no retorno.

### 5. Sanitizar e devolver

Use o checklist abaixo. Devolva `resultado-c1.txt` sanitizado e a matriz preenchida, como comentário na #457 ou arquivo anexo.

## O que cada consulta responde

| ID | Pergunta | Leitura esperada (sem concluir antes do resultado) |
| --- | --- | --- |
| Q00 | Versão do motor, ODS e dialeto | Confirma compatibilidade (2.5 x 3.0+) |
| Q01–Q03 | Quais tabelas e colunas têm nomes com os padrões procurados (`SALD`, `INICIAL`, `ANTERIOR`, `DOC`, `NUM`, `HIST`, `ENCERR`, `FECH`, `LOTE`, `CENTRO`, `CUST`, `PART`, `FORNE`, `CLIENT`, `CONTR`, `CHAVE` e auxiliares) | Lista de **candidatos**. Nome parecido não prova significado |
| Q04 | Todas as colunas de `LCTOCTB` e `PLANOESPEC` | Base para escolher colunas a perfilar nos modelos T1–T4 |
| Q05–Q06 | PK, UNIQUE e índices | Se `CHAVELCTOCTB` é única (sozinha ou com `CODIGOEMPRESA`) |
| Q07 | FKs declaradas | Tabelas ligadas a `LCTOCTB`/`PLANOESPEC` (rateio, partidas, saldos). Ausência de FK não prova ausência de vínculo |
| Q08–Q09 | Triggers e geradores (só nomes) | Se a chave vem de gerador (identidade técnica estável?) |
| Q10 | Formato das linhas no período | Quantas linhas têm os dois lados, só débito, só crédito ou nenhum |
| Q11–Q13 | Sinal de `VALORLCTOCTB` | Existência, volume e época de negativos (#449) |
| Q14–Q15 | Linhas por `CHAVELCTOCTB` e composição | Se uma chave é partida simples (1 linha com os dois lados) ou agrupa vários débitos/créditos |
| Q16 | Chaves com linhas de um lado só: débitos = créditos? | Indício de lançamento com múltiplas partidas balanceado pela chave |
| Q17–Q18 | Chave com mais de uma data / em outra empresa | Escopo da identidade da chave |
| Q19 | Meses com movimento em todo o histórico e linhas em 31/12 | Lacunas de histórico. Linhas em 31/12 são apenas **indício** de encerramento |
| Q20–Q21 | Tamanho do plano e unicidade de `CONTACTB` | Se o join atual pode duplicar linhas |
| Q22 | Plano x contas observadas | Contas sem movimento e contas com movimento fora do plano |
| Q23 | Lados descartados pelo join interno atual | Perda silenciosa no reader atual |
| Q24–Q25 | Formato de `CLASSIFCONTA` e hipótese de prefixo | **Hipótese**: classificação prefixo de outra indica conta agregadora. Não marcar contas como analíticas/sintéticas sem campo confirmado |

## Fase 1b — modelos para candidatos (depois de Q01–Q04)

Use **somente** com colunas que apareceram em Q03/Q04. Substitua `<<...>>` pelo nome exato. Cada modelo retorna apenas contagens e datas e também roda dentro de `SET TRANSACTION READ ONLY ISOLATION LEVEL SNAPSHOT;` ... `ROLLBACK;`.

### T1 — perfil de tabela candidata a saldo

Para uma tabela com coluna de empresa, conta, data e valor:

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP FROM RDB$DATABASE)
SELECT COUNT(*) AS LINHAS, MIN(T.<<COL_DATA>>) AS PRIMEIRA_DATA, MAX(T.<<COL_DATA>>) AS ULTIMA_DATA,
       COUNT(DISTINCT T.<<COL_DATA>>) AS DATAS_DISTINTAS, COUNT(DISTINCT T.<<COL_CONTA>>) AS CONTAS_DISTINTAS,
       SUM(CASE WHEN T.<<COL_VALOR>> IS NULL THEN 1 ELSE 0 END) AS VALOR_NULO,
       SUM(CASE WHEN T.<<COL_VALOR>> < 0 THEN 1 ELSE 0 END) AS VALOR_NEGATIVO,
       SUM(CASE WHEN T.<<COL_VALOR>> = 0 THEN 1 ELSE 0 END) AS VALOR_ZERO
FROM <<TABELA>> T CROSS JOIN P WHERE T.<<COL_EMPRESA>> = P.EMP;
```

T1b: as contas dessa tabela existem em `PLANOESPEC`?

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP FROM RDB$DATABASE)
SELECT
  SUM(CASE WHEN EXISTS (SELECT 1 FROM PLANOESPEC PE WHERE PE.CODIGOEMPRESA = P.EMP AND PE.CONTACTB = T.<<COL_CONTA>>) THEN 1 ELSE 0 END) AS LINHAS_COM_CONTA_NO_PLANO,
  SUM(CASE WHEN NOT EXISTS (SELECT 1 FROM PLANOESPEC PE WHERE PE.CODIGOEMPRESA = P.EMP AND PE.CONTACTB = T.<<COL_CONTA>>) THEN 1 ELSE 0 END) AS LINHAS_SEM_CONTA_NO_PLANO
FROM <<TABELA>> T CROSS JOIN P WHERE T.<<COL_EMPRESA>> = P.EMP;
```

Saldo desconhecido **nunca** vira zero. Se nenhuma tabela candidata existir, a resposta é "não encontrado".

### T2 — coluna candidata em `LCTOCTB`

Use para documento, número, lote, CC, partida, origem etc.

Preenchimento e cardinalidade:

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE)
SELECT COUNT(*) AS LINHAS, COUNT(L.<<COLUNA>>) AS PREENCHIDAS, COUNT(DISTINCT L.<<COLUNA>>) AS VALORES_DISTINTOS
FROM LCTOCTB L CROSS JOIN P WHERE L.CODIGOEMPRESA = P.EMP AND L.DATALCTOCTB BETWEEN P.INI AND P.FIM;
```

T2b: quantos valores distintos por chave. Mostra se o atributo é da linha ou do lançamento.

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE)
SELECT G.VALORES_POR_CHAVE, COUNT(*) AS QTD_CHAVES FROM (
  SELECT L.CHAVELCTOCTB, COUNT(DISTINCT L.<<COLUNA>>) AS VALORES_POR_CHAVE
  FROM LCTOCTB L CROSS JOIN P WHERE L.CODIGOEMPRESA = P.EMP AND L.DATALCTOCTB BETWEEN P.INI AND P.FIM
  GROUP BY L.CHAVELCTOCTB) G GROUP BY 1 ORDER BY 1;
```

T2c: quantas chaves por valor. Mostra se o atributo agrupa lançamentos, como um lote.

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE)
SELECT G.CHAVES_POR_VALOR, COUNT(*) AS QTD_VALORES FROM (
  SELECT L.<<COLUNA>>, COUNT(DISTINCT L.CHAVELCTOCTB) AS CHAVES_POR_VALOR
  FROM LCTOCTB L CROSS JOIN P WHERE L.CODIGOEMPRESA = P.EMP AND L.DATALCTOCTB BETWEEN P.INI AND P.FIM AND L.<<COLUNA>> IS NOT NULL
  GROUP BY L.<<COLUNA>>) G GROUP BY 1 ORDER BY 1;
```

### T3 — código de baixa cardinalidade

Candidatos: tipo, origem, encerramento.

Só use em coluna numérica pequena ou `CHAR` de até 3 posições, com no máximo 20 valores distintos em T2. **Nunca** use em coluna de texto livre: ela devolveria conteúdo.

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE)
SELECT L.<<COL_CODIGO>> AS CODIGO, COUNT(*) AS LINHAS, MIN(L.DATALCTOCTB) AS PRIMEIRA_DATA, MAX(L.DATALCTOCTB) AS ULTIMA_DATA,
  SUM(CASE WHEN EXTRACT(MONTH FROM L.DATALCTOCTB) = 12 AND EXTRACT(DAY FROM L.DATALCTOCTB) = 31 THEN 1 ELSE 0 END) AS LINHAS_EM_31_12
FROM LCTOCTB L CROSS JOIN P WHERE L.CODIGOEMPRESA = P.EMP AND L.DATALCTOCTB BETWEEN P.INI AND P.FIM
GROUP BY 1 ORDER BY 2 DESC;
```

O significado de cada código só é confirmado pela documentação do Questor ou pela tela do sistema. Não deduza a partir da contagem.

### T4 — tabela filha ligada ao lançamento

Candidatas: rateio de CC, partidas, documento. Use quando Q07 ou Q03 indicarem uma coluna que referencia `CHAVELCTOCTB`.

```sql
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE)
SELECT G.FILHAS_POR_CHAVE, COUNT(*) AS QTD_CHAVES FROM (
  SELECT K.CHAVELCTOCTB,
    (SELECT COUNT(*) FROM <<TABELA_FILHA>> F WHERE F.<<COL_CHAVE_FILHA>> = K.CHAVELCTOCTB
        AND F.<<COL_EMPRESA_FILHA>> = P.EMP  -- remova esta linha se a filha não tiver coluna de empresa
    ) AS FILHAS_POR_CHAVE
  FROM (SELECT DISTINCT L.CHAVELCTOCTB FROM LCTOCTB L CROSS JOIN P
        WHERE L.CODIGOEMPRESA = P.EMP AND L.DATALCTOCTB BETWEEN P.INI AND P.FIM) K CROSS JOIN P) G
GROUP BY 1 ORDER BY 1;
```

## Fase 2 (opcional) — exemplo estrutural de chaves com várias linhas

Só execute se Q14/Q15 mostrarem chaves com mais de uma linha e a estrutura não ficar clara pelas contagens.

A consulta devolve até 5 chaves:

- **Grupo:** número local, 1 a 5.
- **Contas:** rótulos locais dentro do grupo (1, 2, 3…), não códigos reais.
- **Valor:** só o sinal.
- **Data:** só se coincide com a da primeira linha.

Os rótulos são posições locais, de propósito. Não use `HASH()` do Firebird para mascarar códigos: no 2.5 ele preserva a proximidade entre códigos e é reversível na prática.

```sql
SET TRANSACTION READ ONLY ISOLATION LEVEL SNAPSHOT;
WITH P AS (SELECT CAST(-1 AS INTEGER) AS EMP, DATE '1900-01-01' AS INI, DATE '1900-01-31' AS FIM FROM RDB$DATABASE),
K AS (
    SELECT FIRST 5 L.CHAVELCTOCTB AS CHAVE
    FROM LCTOCTB L CROSS JOIN P
    WHERE L.CODIGOEMPRESA = P.EMP AND L.DATALCTOCTB BETWEEN P.INI AND P.FIM
    GROUP BY L.CHAVELCTOCTB
    HAVING COUNT(*) > 1
),
A AS (
    SELECT L.CHAVELCTOCTB AS CHAVE, L.CONTACTBDEB AS CONTA
    FROM LCTOCTB L CROSS JOIN P JOIN K ON K.CHAVE = L.CHAVELCTOCTB
    WHERE L.CODIGOEMPRESA = P.EMP AND L.CONTACTBDEB IS NOT NULL
    UNION
    SELECT L.CHAVELCTOCTB, L.CONTACTBCRED
    FROM LCTOCTB L CROSS JOIN P JOIN K ON K.CHAVE = L.CHAVELCTOCTB
    WHERE L.CODIGOEMPRESA = P.EMP AND L.CONTACTBCRED IS NOT NULL
)
SELECT
    (SELECT COUNT(*) FROM K K2 WHERE K2.CHAVE < L.CHAVELCTOCTB) + 1 AS GRUPO,
    CASE WHEN L.CONTACTBDEB IS NOT NULL AND L.CONTACTBCRED IS NOT NULL THEN 'DEB_E_CRED'
         WHEN L.CONTACTBDEB IS NOT NULL THEN 'SO_DEB'
         WHEN L.CONTACTBCRED IS NOT NULL THEN 'SO_CRED'
         ELSE 'SEM_CONTA' END AS FORMATO,
    CASE WHEN L.CONTACTBDEB IS NULL THEN NULL
         ELSE (SELECT COUNT(*) FROM A WHERE A.CHAVE = L.CHAVELCTOCTB AND A.CONTA < L.CONTACTBDEB) + 1 END AS CONTA_DEB_N,
    CASE WHEN L.CONTACTBCRED IS NULL THEN NULL
         ELSE (SELECT COUNT(*) FROM A WHERE A.CHAVE = L.CHAVELCTOCTB AND A.CONTA < L.CONTACTBCRED) + 1 END AS CONTA_CRED_N,
    SIGN(L.VALORLCTOCTB) AS SINAL,
    CASE WHEN L.DATALCTOCTB = (SELECT MIN(L2.DATALCTOCTB) FROM LCTOCTB L2
                               WHERE L2.CODIGOEMPRESA = L.CODIGOEMPRESA AND L2.CHAVELCTOCTB = L.CHAVELCTOCTB)
         THEN 'MESMA_DATA_DA_1A' ELSE 'OUTRA_DATA' END AS DATA_REL
FROM LCTOCTB L
CROSS JOIN P
JOIN K ON K.CHAVE = L.CHAVELCTOCTB
WHERE L.CODIGOEMPRESA = P.EMP
ORDER BY 1, 2, 3, 4;
ROLLBACK;
```

Exemplo de leitura com dados **sintéticos**, sem relação com nenhum cliente:

| GRUPO | FORMATO | CONTA_DEB_N | CONTA_CRED_N | SINAL | DATA_REL |
| --- | --- | --- | --- | --- | --- |
| 1 | SO_DEB | 1 | — | 1 | MESMA_DATA_DA_1A |
| 1 | SO_CRED | — | 2 | 1 | MESMA_DATA_DA_1A |
| 1 | SO_CRED | — | 3 | 1 | MESMA_DATA_DA_1A |

Nesse exemplo, o grupo seria "um débito contra dois créditos". A contrapartida de cada crédito **não** pode ser inferida dessa forma sem regra confirmada.

## Checklist de evidências para devolver

Na saída:

- [ ] Q00: versão do Firebird/ODS.
- [ ] Q01–Q09: saída completa (só metadados).
- [ ] Q10–Q25: saída com o código da empresa trocado por `EMPRESA_A` e o período indicado como "12 meses até AAAA-MM".
- [ ] Q13/Q18: executadas ou anotadas como "não executadas (tempo)".
- [ ] T1–T4: para cada candidato escolhido, o nome da coluna/tabela e a saída.
- [ ] Fase 2: só se necessária, com rótulos locais.
- [ ] Documentação ou tela do Questor que confirme o significado de algum campo: referência descrita em texto, sem captura com dados.

Antes de enviar:

- [ ] Nenhuma linha com histórico, descrição de conta, documento, nome, CNPJ/CPF ou valor monetário.
- [ ] Nenhuma senha, usuário, servidor ou caminho de base.

## Matriz de evidências

Preencher com o resultado. Hoje nenhuma linha além das já lidas pelo reader está confirmada.

| Dado desejado | Evidência encontrada? | Tabela/campo candidato | Status |
| --- | --- | --- | --- |
| Lançamento: data | Sim (código atual) | `LCTOCTB.DATALCTOCTB` | Confirmado |
| Lançamento: conta débito / crédito | Sim (código atual) | `LCTOCTB.CONTACTBDEB` / `CONTACTBCRED` | Confirmado (uso); semântica de linha com um lado só: Q10/Q15 |
| Lançamento: valor | Sim (código atual) | `LCTOCTB.VALORLCTOCTB` | Confirmado (uso); sinal negativo: Q11–Q13 (#449) |
| Lançamento: histórico | Sim (código atual) | `LCTOCTB.COMPLHIST` | Confirmado (uso); se é o histórico completo ou só o complemento: não confirmado |
| Plano: conta, classificação, descrição | Sim (código atual) | `PLANOESPEC.CONTACTB`, `CLASSIFCONTA`, `DESCRCONTA` | Confirmado (uso) |
| Plano completo, inclusive contas sem movimento | Pendente | `PLANOESPEC` (Q20–Q22) | Não confirmado |
| Conta analítica/sintética, ativa/inativa, natureza, nível | Pendente | Q03 (padrões `ANALIT`, `SINTET`, `INATIV`, `SITUAC`, `NATUR`, `NIVEL`, `GRAU`); Q25 é só hipótese | Não confirmado |
| Saldo inicial / anterior | Pendente | Q02/Q03 (`SALD`, `INICIAL`, `ANTERIOR`) → T1 | Não confirmado; desconhecido ≠ zero |
| Documento | Pendente | Q03 (`DOC`, `NUM`) → T2 | Não confirmado |
| Lote / número do lançamento | Pendente | Q03 (`LOTE`, `NUM`, `SEQ`) → T2/T2c | Não confirmado |
| Identificação de encerramento | Pendente | Q03 (`ENCERR`, `FECH`) → T3; Q19 (31/12) é só indício | Não confirmado |
| Contrapartida / múltiplas partidas | Pendente | Q14–Q16, Q03 (`PART`, `CONTR`), fase 2 | Não confirmado; não inferir |
| Identidade estável da linha/partida | Pendente | Q05–Q06 (PK/índices de `CHAVELCTOCTB`), Q08–Q09, Q17–Q18 | Não confirmado |
| Alteração/exclusão na origem | Pendente | Q03 (`EXCLU`, `CANCEL`, `ESTORN`, `ATUALIZ`) | Não confirmado |
| Centro de custo e rateio | Pendente | Q02/Q03 (`CENTRO`, `CUST`, `CCUSTO`) → T2/T4 | Não confirmado |
| Participante (fornecedor/cliente) | Pendente | Q03 (`FORNE`, `CLIENT`) | Não confirmado; fora do MVP |
| Valores negativos | Pendente | Q11–Q13 | Não confirmado; decide #449 |
| Ausência de histórico x mês sem movimento | Pendente | Q19 (meses com movimento); Q02/Q03 (`FECH`) para períodos fechados | Não confirmado; hoje a cobertura do FINEMP é só a última SyncRun |
| Perda no join atual (conta fora do plano) | Pendente | Q23 | Não confirmado |

## Depois do retorno

A fase 1 já foi executada. O kit direcionado seguinte é o [C1b](questor-c1b-discovery.md).

1. Atualizar a matriz na #457 com o que foi confirmado.
2. C1 fase 2: especificar o contrato canônico ampliado só com campos confirmados, separando FATO / PROPOSTA / PONTO ABERTO, e levar à decisão de produto o que depender de O3/O7/O14.
3. Só então C2 (reader → agente → decoder → persistência → API), com testes sintéticos.

## Como este kit foi validado

- **Sintaxe e execução.** As consultas Q00–Q25, os modelos T1–T4 e a consulta da fase 2 foram executados sem erro em Firebird **2.5.9** (imagem `jacobalberty/firebird:2.5-ss`), sobre um esquema **sintético** local.
  - O esquema tinha só as colunas confirmadas acima, mais tabelas/colunas fictícias com prefixo `SYN_` para exercitar os modelos.
  - Isso valida sintaxe e execução, não a origem real.
- **Sem marcadores.** Com os marcadores sem substituição, todas as consultas de dados (inclusive Q13) retornam vazio.
- **Transação somente leitura.** A tentativa de escrita dentro da transação foi recusada pelo motor.
