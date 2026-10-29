# Totais diários por data

## FATO / DECISÃO

Esta leitura agrega os pagamentos existentes pela data e valor efetivos e os títulos sem baixa pela data de vencimento e valor nominal. O mês é obrigatório, e todos os seus dias são retornados, inclusive dias vazios e 29 de fevereiro. O cálculo ocorre no PostgreSQL em snapshot consistente e retorna dinheiro como texto decimal exato; requer permissão financeira na Company e não altera dados.

## PONTO ABERTO

Isto é uma fatia de totais por data, não o fluxo diário completo nem uma projeção de saldo. O protótipo também distribui títulos atrasados a partir de hoje e calcula saldos; essas partes, transferências e reconstrução histórica exigem incrementos próprios. O período padrão da apresentação segue decisão separada, e nenhuma forma efetiva de pagamento é inferida.
