# Snapshot parcial de Apresentação de Resultados

## FATO CONFIRMADO

O domínio atual suporta aging de pagar/receber e resumos parciais de caixa diário, mensal e anual. Cada bloco já calcula seus números no backend. Consultas HTTP separadas não formam um snapshot único entre blocos.

## DECISÃO

O primeiro agregador interno reúne esses quatro blocos numa única transação de leitura REPEATABLE_READ. Os serviços existentes participam da mesma transação; não há fórmulas duplicadas ou somas delegadas ao React. Uma transação externa com isolamento insuficiente é rejeitada antes da leitura dos blocos.

Mês do caixa, data-base do aging e escopo ALL/COMPETENCE são obrigatórios e independentes: nenhum é inferido do outro. O caixa anual usa o ano do mês informado. A resposta informa schemaVersion=1 e partial=true; isso não representa relatório completo ou fechamento. A autorização financeira ALL e a Company são verificadas no servidor.

Opt-in: finemp.finance.presentation.enabled e os três produtores aging, daily-cash e monthly-cash devem estar habilitados. Sem eles, o agregador não é registrado. Este incremento não abre rota HTTP nem persiste ou compartilha apresentações.

## PONTO ABERTO

Período padrão e comportamento sem período fechado. A escolha de último mês fechado não é fixada por este contrato. DRE operacional, indicadores, saldo, projeção, orçamento, transferências e forma efetiva não são inventados. A DRE contábil Questor permanece separada. O aging aplica a data-base ao estado armazenado, sem reconstruir alterações históricas.

## PROPOSTA

Expor API aditiva do agregador e reutilizar futuramente seus mesmos dados na geração backend do PDF. Blocos adicionais só entram quando seus domínios e regras estiverem implementados e validados.
