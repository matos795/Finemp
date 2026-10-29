# Resumo de títulos em aberto e aging

## FATO / DECISÃO

O resumo é calculado no backend para uma Company, direção e data-base informadas. A referência inclui títulos sem pagamento ou com data de pagamento posterior à data-base; usa sempre o valor nominal e não filtra por data de emissão. A opção de competência limita os títulos ao mês da data-base. O vencimento na data-base pertence à faixa a vencer em até 30 dias; há quatro faixas a vencer e quatro vencidas, com cortes em 30, 60 e 90 dias.

O PostgreSQL agrega contagem e valores em uma consulta de leitura consistente. O backend também fornece os totais de vencidos e a vencer em até 30 dias, derivados das mesmas faixas. Os valores são decimais exatos serializados como texto, e a API exige permissão financeira com escopo ALL. O relatório é somente leitura, sem alteração de títulos ou baixas. Data-base é obrigatória; isso não decide o período padrão da apresentação de resultados.

## PONTO ABERTO

A consulta aplica a data-base ao estado atualmente armazenado. Ela não reconstrói alterações ou estornos históricos: uma visão histórica fiel precisa de decisão e fonte adequadas. Filtros por centro, percentuais, prazo médio e demais métricas permanecem em incrementos posteriores. A DRE operacional continua distinta da DRE contábil do Questor.
