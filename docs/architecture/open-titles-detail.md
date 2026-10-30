# Detalhamento de títulos em aberto

O detalhamento usa os mesmos critérios de data-base e competência do resumo de aging: inclui títulos sem pagamento ou pagos após a data-base e utiliza o valor nominal. A API ordena por vencimento e identificador, pagina no PostgreSQL e calcula dias de atraso e faixa no backend. Exige permissão financeira ALL e isolamento por Company; os valores são decimais exatos em texto.

O vencimento na própria data-base é a vencer, e não vencido. A consulta aplica a data-base ao estado atualmente armazenado; ela não reconstrói alterações ou estornos históricos. Filtro hierárquico por centro e distinção de forma prevista/efetiva continuam em incrementos próprios. Nenhum lançamento ou baixa é alterado.

O parâmetro opcional `band` permite abrir uma das oito faixas do aging sem filtrar no frontend. O PostgreSQL aplica os mesmos limites de dias à contagem e às linhas antes de paginar. Sem o parâmetro, o comportamento anterior é preservado; valores desconhecidos são rejeitados como consulta inválida.

## Subtotais por parceiro

FATO CONFIRMADO: o modelo homologado de pagar/receber prevê subtotal por cliente/fornecedor (HANDOFF_FINEMP_1, seções 1.5 e 4.4).

DECISÃO: GET `open-titles/by-partner` usa a mesma seleção do detalhamento e agrega por identificador do parceiro no PostgreSQL. Homônimos permanecem separados, parceiros inativos com títulos são incluídos e títulos sem parceiro formam um grupo com ID/nome nulos. O nome exibido usa nome fantasia quando preenchido, senão nome cadastral. O join também exige a Company.

A paginação e totalItems contam grupos; titleCount conta os títulos de cada grupo. Valores nominais são somados no backend e serializados como texto. A ordem determinística usa a chave normalizada do catálogo e ID, com sem-parceiro ao final; não se afirma equivalência integral à ordenação por locale do navegador. Filtros de data-base, competência e faixa são aplicados antes de agrupar.

PONTO ABERTO: hierarquia de centros, percentuais/prazos e reconstrução histórica não são implementados aqui.

## Drill-down de grupo

Cada título também retorna documentNumber, documentDate e categoryId já persistidos, na mesma projeção SQL. Documento pode ser vazio quando não informado. Esses campos não alteram seleção por data-base, competência ou vencimento, não acrescentam consultas por linha e não inferem forma efetiva de pagamento.

Em incremento separado, o GET `open-titles` aceita `partnerId` opcional ou `unassignedPartner=true`. A combinação é rejeitada com 400. O filtro é aplicado na mesma consulta de Company, antes de contar e paginar, cumulativamente com data-base, escopo e faixa. Não existe consulta ao parceiro fora da Company: identificadores alheios ou inexistentes resultam em página vazia. Sem esses parâmetros, a lista anterior é preservada. A resposta não altera lançamentos nem a baixa.
