# Totais mensais de caixa

## FATO / DECISÃO

O protótipo agrega entradas e saídas realizadas pelo mês da baixa e pelo valor efetivo, usando o valor nominal quando não há valor efetivo distinto. Lançamentos sem baixa são agregados como em aberto no mês do vencimento, pelo valor nominal. A competência não determina o mês desse resumo de caixa.

A consulta implementa essa fatia em leitura por Company e ano explicitamente informado, com permissão financeira vigente, snapshot consistente e 12 meses, incluindo meses sem movimento. Valores permanecem decimais exatos e são serializados como texto. Nenhuma baixa ou conciliação é executada.

Os quatro totais anuais são calculados no backend pela soma dos mesmos 12 meses retornados. Preservam a separação entre realizado e em aberto, entradas e saídas; não representam saldo de caixa nem orçamento.

## Abertura por atividade

FATO CONFIRMADO: fluxo mensal homologado distingue operacional, investimento e financiamento; o catálogo já resolve a classificação atual da subcategoria, depois do grupo e, na ausência, OPERATING.

DECISÃO: activityMonths retorna 36 combinações, mês crescente e atividades OPERATING/INVESTING/FINANCING nessa ordem. Os joins de categoria e grupo exigem a mesma Company. A única consulta agrega por mês/atividade; os totais mensais e anuais são derivados desses mesmos valores no backend. Assim o frontend não precisa somar as atividades para obter o total. Abertura e totais permanecem parte do snapshot cashYear da apresentação.

## PONTO ABERTO

Esta classificação é a vigente no catálogo, não uma reconstrução histórica. Continuam fora saldo, transferências, projeções, orçamento e DRE operacional.

O resumo não inclui saldos, transferências, projeções, classificação por forma efetiva ou reconstrução fiel de estado histórico. A forma prevista e a efetiva continuam informações distintas; nenhuma é inferida aqui. Isto não substitui o fluxo completo nem a DRE operacional ou contábil. O período padrão da apresentação segue decisão separada.
