# DRE contábil: agregação autoritativa e cobertura — #446 (M6)

## FATO CONFIRMADO

- `GET /api/companies/{companyId}/dre` soma `AccountingMovement` de todas as fontes da
  Company no período, por trio DRE / DRE ABERTA / EBITDA da classificação da conta.
- Regra de sinal (inalterada): conta cujo código começa com "1" → débito positivo,
  crédito negativo; demais → crédito positivo, débito negativo.
- Classificação ausente ou incompleta fica fora das somas e conta em
  `unclassifiedAccountCount`. Há no máximo uma classificação por conta (constraint
  única), então o join não duplica movimentos.
- Antes de #446 a ordem das linhas dependia da ordem física do PostgreSQL e os
  subtotais eram somados no navegador com `number`.

## DECISÃO técnica (#446)

- Linhas ordenadas por DRE, DRE ABERTA e EBITDA (ordem técnica, alfabética).
- `groups` (DRE → DRE ABERTA → linhas) e `classifiedTotal` calculados no backend em
  `BigDecimal`, serializados como string com 4 casas (`setScale(4, UNNECESSARY)`).
  `classifiedTotal` é a soma das linhas classificadas, não "lucro" homologado.
- `coverage`: por fonte, início/fim da última execução SUCCEEDED (nulo sem execução).
  Movimentos importados pelo caminho legado não aparecem como cobertura.
- `lines[].total` numérico e `unclassifiedAccountCount` mantidos (mudança aditiva).

## PONTO ABERTO / bloqueios

- **DECISÃO DE PRODUTO NECESSÁRIA (O21):** amostra homologada para validar sinal,
  linhas e totais; ordem oficial da demonstração (O2).
- Encerramentos não são distinguíveis no canônico (C1/C2): DRE de período com
  lançamento de encerramento não é homologável.
- Mês a mês e drill-down por linha ficam para evolução posterior.
