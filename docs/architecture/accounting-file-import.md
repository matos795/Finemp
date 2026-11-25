# Fonte contábil por arquivo — arquitetura inicial

Issue: #466. Relacionadas: #433, #467 (C2.1) e contrato canônico v2.

**Status: especificação. Nenhum parser de Razão real foi implementado nesta etapa.**

## FATO CONFIRMADO

Hoje `AccountingSourceType` possui somente `QUESTOR`.

O FINEMP já possui padrões reutilizáveis para importação de arquivo financeiro:

- prévia sem persistência;
- SHA-256 do arquivo;
- confirmação separada;
- reprocessamento do arquivo na confirmação;
- idempotência por comando/fingerprint;
- publicação atômica;
- nenhum arquivo bruto persistido por padrão;
- erros e logs sem conteúdo do arquivo.

O contrato canônico contábil v2 separa a origem do formato persistido. Portanto uma empresa sem Questor não precisa de uma "página estática" paralela: o arquivo pode alimentar os mesmos domínios contábeis.

## DECISÃO — nome da origem e do dataset

### `AccountingSourceType.FILE_IMPORT`

Adotado como proposta para a nova origem.

Motivo:

- `AccountingSource` já dá o contexto contábil, então `ACCOUNTING_FILE` seria redundante;
- `FILE_IMPORT` descreve o mecanismo de entrada;
- não amarra a fonte a CSV, XLSX, TXT ou PDF;
- não finge sincronização automática.

### `AccountingFileDataset.GENERAL_LEDGER`

Representa o **Razão**.

O nome do dataset fica separado do canal. Isso permite, no futuro, outros datasets por arquivo sem criar novos tipos de fonte para cada um.

## DECISÃO — arquivo é uma origem push, não um `AccountingSourceReader`

`AccountingSourceReader` modela uma fonte que o FINEMP pode consultar quando executa uma SyncRun.

Um arquivo anexado pelo usuário não funciona assim: o conteúdo chega no request e não existe uma fonte externa a ser consultada depois.

Portanto:

```text
QUESTOR
AccountingSourceReader
    ↓
Canonical contract
    ↓
publication

FILE_IMPORT
upload / preview / confirm
    ↓
layout adapter
    ↓
Canonical contract
    ↓
same publication
```

O compartilhamento ocorre **a partir do contrato canônico e da publicação**, não forçando arquivo a fingir que é JDBC/ERP.

## Fluxo

```text
Company
  ↓
AccountingSource(type = FILE_IMPORT)
  ↓
Upload GENERAL_LEDGER
  ↓
Preview temporária
  ↓
Confirmação explícita
  ↓
Reprocessar exatamente o mesmo arquivo
  ↓
Validar hash + layout + período
  ↓
Converter para contrato canônico
  ↓
Publicação atômica
  ↓
Receipt + cobertura
  ↓
Plano observado / Movimentos / De-Para / DRE
```

## Preview

A prévia não grava movimentos nem contas.

Entrada proposta:

- sourceId;
- dataset = `GENERAL_LEDGER`;
- arquivo;
- layoutProfile explícito;
- parâmetros estritamente necessários ao perfil.

Saída mínima:

```text
fileSha256
format
layoutProfile
detectedPeriod
rowCount
validRowCount
invalidRowCount
warnings[]
errors[]
sampleRows[]
observedAccountCount
```

`sampleRows` é devolvido somente ao usuário autorizado dentro do FINEMP. Seu conteúdo nunca entra em log, Issue, fixture pública ou mensagem de erro sanitizada.

O backend deve impor limites de tamanho, quantidade de linhas e campos antes de materializar estruturas grandes.

## Confirmação

A confirmação é uma operação separada.

O usuário reapresenta:

- o arquivo;
- `fileSha256` recebido na prévia;
- o mesmo `layoutProfile`;
- o período/escopo confirmado;
- `Idempotency-Key`.

O servidor:

1. recalcula SHA-256;
2. rejeita se não coincidir;
3. reprocessa o arquivo;
4. não aceita valores canônicos enviados pelo frontend;
5. valida autorização e vínculo Company/source;
6. converte para o contrato canônico;
7. publica atomicamente;
8. grava receipt na mesma transação da publicação.

## Idempotência e receipt

Identidade principal do comando:

```text
(accounting_source_id, idempotency_key)
```

O receipt armazena fingerprint técnico, não o arquivo:

```text
dataset
fileSha256
layoutProfile
coveredFrom
coveredTo
rowCount
publishedCount
skippedCount
status
createdBy
createdAt
```

A repetição da mesma chave exige fingerprint idêntico.

Mesma chave com arquivo/escopo diferente → conflito.

Repetição exata após sucesso devolve o receipt existente e não duplica movimentos.

## DECISÃO — política inicial de sobreposição

O primeiro MVP **não substitui silenciosamente** um período já importado.

Política inicial:

- período sem sobreposição → pode publicar;
- mesma importação/fingerprint → idempotente;
- arquivo diferente cobrindo período já publicado → conflito explícito.

Motivo: substituir um Razão implica decidir o que fazer com linhas que desapareceram, classificações históricas, auditoria e cobertura. Fazer delete implícito seria equivalente a inventar a semântica de O3 para outra fonte.

### Futuro: `REPLACE_PERIOD`

Uma operação explícita poderá substituir um intervalo, mas deverá:

- exigir confirmação específica;
- remover somente dados pertencentes à fonte/receipts substituídos;
- publicar o novo snapshot na mesma transação;
- preservar histórico/auditoria;
- nunca atingir outra Company/source;
- recalcular cobertura.

Fica fora da primeira fatia.

## Formatos

Prioridade:

1. XLSX;
2. CSV;
3. TXT delimitado.

PDF não entra na primeira fatia.

Motivo: Razão em PDF exige reconstrução de tabela e aumenta risco de interpretar valores/linhas incorretamente. Se um cliente só conseguir PDF, isso vira uma Issue separada com amostra real e critérios próprios.

## Layout profiles

Não haverá parser "inteligente" que tente adivinhar qualquer Razão.

Contrato proposto:

```java
interface GeneralLedgerLayoutProfile {
    String id();
    Set<AccountingFileFormat> formats();
    GeneralLedgerPreview preview(...);
    CanonicalAccountingBatch parse(...);
}
```

Um registry escolhe **perfil explícito**.

Cada novo layout real recebe:

- amostra sanitizada;
- fixture sintética equivalente;
- parser determinístico;
- testes próprios.

Nenhum LLM é etapa operacional obrigatória.

## Contrato mínimo do GENERAL_LEDGER

A primeira amostra real definirá os campos efetivamente disponíveis.

O adapter deve ser capaz de produzir, quando presentes:

- data contábil;
- conta débito/crédito ou conta + lado;
- valor decimal exato;
- histórico;
- documento;
- chave externa;
- descrição/código de conta.

Não inventar:

- contrapartida quando o arquivo não a informa;
- saldo inicial/final;
- classificação DRE;
- centro de custo;
- participante.

## Integração com o contrato canônico

A fonte de arquivo deve produzir os mesmos tipos canônicos usados pelas demais origens.

Primeira implementação pode depender de C2.1:

```text
layout parser
→ CanonicalAccountingEntry / CanonicalAccountingAccount
→ validador v2
→ conversor v2 → movimentos por lado
→ publicação atômica existente
```

Isso permite entregar o Razão antes de toda a persistência avançada do C2, sem criar um segundo modelo de movimento.

Quando C2.7 estiver disponível, a fonte passa a publicar datasets v2 diretamente sem trocar o parser.

## Plano de contas

Um Razão pode conter somente contas movimentadas.

Portanto a fonte FILE_IMPORT não declara `CHART` completo a menos que o arquivo/layout prove isso.

Quando só houver contas observadas:

- criar descriptors necessários aos movimentos;
- manter a UI indicando que não é plano completo;
- não inferir contas ausentes.

Se o cliente fornecer também um Plano de Contas em dataset próprio no futuro, isso será uma capability separada.

## Permissão

Não reutilizar `QUESTOR_SYNC`.

Permissão proposta:

```text
ACCOUNTING_IMPORT_MANAGE
```

Motivo:

- descreve a ação do usuário;
- não acopla a autorização a XLSX/CSV;
- pode cobrir futuros imports contábeis manuais;
- `QUESTOR_SYNC` continua significando sincronização Questor.

A leitura dos dados depois de publicados continua usando as permissões contábeis já existentes.

## Segurança

- arquivo real nunca vai para Git/Issue/fixture pública;
- nenhum conteúdo contábil em logs;
- erros externos usam códigos fixos;
- filename não é identidade nem fonte confiável;
- extensão, MIME e assinatura/formato são validados;
- fórmulas de XLSX nunca são executadas;
- células de fórmula devem ser rejeitadas ou lidas somente de forma explicitamente segura conforme o perfil;
- limite de arquivo/linhas/colunas;
- parser não acessa rede;
- source sempre validada contra Company;
- receipt e publicação escopados por source/Company;
- arquivo bruto não é persistido no MVP.

## Implementável antes da primeira amostra

Pode ser construído sem conhecer o layout:

1. `AccountingSourceType.FILE_IMPORT`;
2. permissão `ACCOUNTING_IMPORT_MANAGE`;
3. domínio de receipt/fingerprint;
4. lifecycle preview/confirm;
5. limites e segurança de upload;
6. registry de layout profiles;
7. API/DTOs sem um profile concreto;
8. histórico de imports;
9. integração com C2.1/publication.

Porém a feature não deve aparecer como utilizável para cliente até existir ao menos um `GeneralLedgerLayoutProfile` validado com amostra sanitizada.

## Dependência de amostra real

Para o primeiro layout suportado precisamos de 1–2 arquivos sanitizados exportados pelo sistema de origem real.

Não precisamos de:

- nome/CNPJ do cliente;
- valores reais;
- históricos reais;
- documentos reais.

Precisamos preservar estruturalmente:

- abas;
- cabeçalhos;
- ordem/nomes das colunas;
- tipos de célula;
- formatos de data/valor;
- linhas de total/cabeçalho repetido;
- quantidade aproximada de colunas.

## Decomposição proposta

### F0 — esta especificação

Documentação e decisões.

### F1 — fundação da fonte FILE_IMPORT

- source type;
- permission;
- receipt/fingerprint;
- upload limits;
- layout registry sem parser concreto.

### F2 — preview GENERAL_LEDGER

Depois da amostra:

- primeiro profile;
- XLSX ou CSV conforme evidência;
- preview determinística;
- testes com fixture sintética sanitizada.

### F3 — confirmação/publicação

- reupload + hash;
- idempotência;
- canonical v2;
- publicação atômica;
- conflito por sobreposição.

### F4 — UI

- criar fonte por arquivo;
- anexar Razão;
- preview;
- confirmar;
- histórico de imports;
- cobertura exibida como importada, não sincronizada.

### Futuro

- segundo/terceiro layout;
- substituição explícita de período;
- dataset de Plano de Contas;
- PDF, somente se necessário.

## PONTOS ABERTOS

- primeiro layout real e formato;
- tamanho típico dos arquivos;
- se o Razão traz pares D/C ou uma conta + natureza;
- existência de chave estável por lançamento;
- se o arquivo contém plano completo ou somente contas movimentadas;
- necessidade comercial de substituir período já importado no primeiro release.

Nenhum desses pontos deve ser convertido em regra antes da amostra sanitizada.
