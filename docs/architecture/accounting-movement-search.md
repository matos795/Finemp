# Movimentos contábeis paginados — #444 (M5)

## FATO CONFIRMADO

`AccountingMovement` guarda conta, fonte, `sourceEntryKey`, lado, data, valor
`NUMERIC(19,4)` e histórico. Contrapartida, documento, centro de custo, flag de
encerramento e saldo inicial **não** existem no contrato canônico atual
(GAP_CONTABIL §3).

`GET /api/companies/{companyId}/accounting-movements/search` pagina o período no
servidor, com filtros opcionais de fonte e conta, e devolve:

- itens com origem (`accountingSourceId`, `accountId`) e `amount` em string decimal
  na escala armazenada;
- `totals` (débito e crédito) exatos sobre todo o filtro;
- `notSynchronized`: `COUNTERPART`, `DOCUMENT`, `COST_CENTER`, `CLOSING_ENTRY`,
  `OPENING_BALANCE`.

## DECISÃO técnica

- Ordem determinística: data, fonte, `sourceEntryKey`, lado, id.
- Valores como string com `setScale(4, UNNECESSARY)`: nada é arredondado; uma escala
  inesperada falha em vez de alterar o valor.
- Fonte/conta de outra Company → página vazia (filtros sempre combinados com a
  Company do path).
- `size` máximo 200. A listagem antiga (`/accounting-movements`) segue igual.

## PONTO ABERTO

Contrapartida, documento, CC, encerramento e saldos dependem de C1/C2 e de O3/O7/O14.
Drill-down por linha da DRE fica para M6+.
