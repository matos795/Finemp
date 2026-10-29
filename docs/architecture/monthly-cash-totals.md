# Totais mensais de caixa

## FATO / DECISÃO

O protótipo agrega entradas e saídas realizadas pelo mês da baixa e pelo valor efetivo, usando o valor nominal quando não há valor efetivo distinto. Lançamentos sem baixa são agregados como em aberto no mês do vencimento, pelo valor nominal. A competência não determina o mês desse resumo de caixa.

A consulta implementa essa fatia em leitura por Company e ano explicitamente informado, com permissão financeira vigente, snapshot consistente e 12 meses, incluindo meses sem movimento. Valores permanecem decimais exatos e são serializados como texto. Nenhuma baixa ou conciliação é executada.

Os quatro totais anuais são calculados no backend pela soma dos mesmos 12 meses retornados. Preservam a separação entre realizado e em aberto, entradas e saídas; não representam saldo de caixa nem orçamento.

## PONTO ABERTO

O resumo não inclui saldos, transferências, projeções, classificação por forma efetiva ou reconstrução fiel de estado histórico. A forma prevista e a efetiva continuam informações distintas; nenhuma é inferida aqui. Isto não substitui o fluxo completo nem a DRE operacional ou contábil. O período padrão da apresentação segue decisão separada.
