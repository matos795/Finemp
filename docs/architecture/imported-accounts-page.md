# Plano de contas importado, paginado — #442 (M4)

## FATO CONFIRMADO

- `AccountingAccount` nasce dos descritores que chegam com os movimentos sincronizados.
  Não há leitor do plano completo da origem: contas sem movimento não existem no FINEMP.
- `GET /api/companies/{companyId}/accounting-accounts` pagina esse universo no servidor,
  com busca (código/descrição), filtro de status e de fonte, e devolve
  `universe: IMPORTED_ACCOUNTS` e `summary` (total, classificadas, pendentes).
- Status segue a listagem existente: `CLASSIFIED` quando há vínculo com o catálogo
  `DreClassification`; senão `PENDING`.

## DECISÃO técnica

- Ordem determinística `accountKey`, fonte, id: o mesmo código em duas fontes da Company
  não troca de posição entre páginas.
- `summary` aplica fonte e busca, não `status` nem página, para a tela mostrar as
  pendências do recorte atual.
- `accountingSourceId` de outra Company resulta em página vazia (o filtro é sempre
  combinado com a Company do path), sem 404 que revele a existência da fonte.
- Página e `summary` são lidos na mesma transação `REPEATABLE_READ`: uma publicação
  confirmada entre as consultas não gera resposta incoerente.
- A listagem antiga `/classifications` e o `PUT .../classification` seguem sem mudança.

## PONTO ABERTO

Plano completo (inclusive contas sem movimento), classificação patrimonial e regras por
prefixo dependem de C1/C2/C3 e de O2/O21 no [GAP](../reference/GAP_CONTABIL.md).
