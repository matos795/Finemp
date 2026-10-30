# Detalhamento dos títulos do fluxo mensal

## FATO CONFIRMADO

A matriz homologada prevê abertura do fluxo mensal até títulos. O resumo existente separa realizado (data da baixa, valor efetivo ou nominal quando ausente) e aberto (sem baixa, vencimento, nominal). Atividade é a classificação atual da subcategoria, herdada do grupo quando ausente, com fallback OPERATING já existente.

## DECISÃO

`GET /api/companies/{companyId}/financial-reports/monthly-cash-totals/titles` recebe month, direction e state (REALIZED/OPEN) explícitos; activity é opcional. Reutiliza a flag mensal e autorização financeira vigente ALL. Os filtros, contagem e paginação são executados no PostgreSQL, com joins restritos à Company. Contagem e página usam a mesma transação REPEATABLE_READ. Ordenação por data de caixa e ID. Dinheiro é decimal exato serializado como texto; resposta no-store.

amount corresponde à medida do subtotal selecionado; nominalAmount é apresentado separadamente. Não confundir nominal com realizado. Referências permitem navegação ao título existente, sujeita à autorização da API de destino. Nenhuma mutação, baixa, cálculo tributário ou alteração de período é executada.

## PONTO ABERTO / limites

Resumo e detalhe requisitados em momentos distintos podem observar mudanças de dados ou classificação. Não há token de snapshot entre requisições. Sem reconstrução histórica, saldo, orçamento, transferências, centro hierárquico, forma efetiva inferida ou DRE. #197 e #128 permanecem isoladas.

## Subtotais por categoria (#234)

DECISÃO: /monthly-cash-totals/by-category usa os mesmos filtros de mês, direção, estado e atividade e agrupa por identidade da categoria. Nomes iguais não unem categorias diferentes. Retorna identidade e rótulo atuais do grupo, atividade, titleCount e amount. Contagem/paginação são de categorias e usam a chave de ordenação do catálogo e UUID; filtros e somas permanecem no PostgreSQL. O detalhe aceita categoryId cumulativo; identidade externa/desconhecida não revela existência e produz vazio. O construtor de seleção é compartilhado entre detalhe e subtotais para preservar as mesmas regras. Isto não define uma hierarquia de DRE.
