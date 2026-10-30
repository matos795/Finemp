# Snapshot parcial de Apresentação de Resultados

## FATO CONFIRMADO

O domínio atual suporta aging de pagar/receber e resumos parciais de caixa diário, mensal e anual. Cada bloco já calcula seus números no backend. Consultas HTTP separadas não formam um snapshot único entre blocos.

## DECISÃO

O primeiro agregador interno reúne esses quatro blocos numa única transação de leitura REPEATABLE_READ. Os serviços existentes participam da mesma transação; não há fórmulas duplicadas ou somas delegadas ao React. Uma transação externa com isolamento insuficiente é rejeitada antes da leitura dos blocos.

Mês do caixa, data-base do aging e escopo ALL/COMPETENCE são obrigatórios e independentes: nenhum é inferido do outro. O caixa anual usa o ano do mês informado. A resposta informa schemaVersion=1 e partial=true; isso não representa relatório completo ou fechamento. A autorização financeira ALL e a Company são verificadas no servidor.

Opt-in: finemp.finance.presentation.enabled e os três produtores aging, daily-cash e monthly-cash devem estar habilitados. Sem eles, o agregador e a rota não são registrados. Não persiste ou compartilha apresentações.

GET /api/companies/{companyId}/financial-reports/presentation-snapshot exige month, asOf e scope explícitos. Responde sem cache com cashMonth, cashYear, payables e receivables, além dos parâmetros de contexto. Dinheiro mantém texto decimal exato. A composição usa os mesmos esquemas dos endpoints individuais. Parâmetros ausentes/inválidos retornam 400; Company sem permissão financeira retorna 403.

## PONTO ABERTO

Período padrão e comportamento sem período fechado. A escolha de último mês fechado não é fixada por este contrato. DRE operacional, indicadores, saldo, projeção, orçamento, transferências e forma efetiva não são inventados. A DRE contábil Questor permanece separada. O aging aplica a data-base ao estado armazenado, sem reconstruir alterações históricas.

## PROPOSTA

Reutilizar futuramente os mesmos dados na geração backend do PDF. Blocos adicionais só entram quando seus domínios e regras estiverem implementados e validados.

## DRE operacional parcial opcional (#244)

DECISÃO: quando finemp.finance.operational-dre.enabled=true, o mesmo snapshot inclui operationalDre usando ator, Company e mês explícitos. O serviço participa da transação existente; o teste de alteração de baixa confirmada entre blocos verifica também que a diferença financeira da DRE continua no estado anterior, enquanto a leitura posterior isolada já observa a mudança.

Sem o opt-in, o endpoint anterior continua disponível e o campo é omitido, não substituído por zeros. schemaVersion=1 e partial=true permanecem; mudança aditiva. O bloco mantém coverage=FINANCIAL_ENTRIES e não afirma DRE completa. NAO e fontes ausentes continuam pontos abertos. Não são adicionados indicadores ou fórmulas no frontend.
