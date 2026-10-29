# Detalhamento de títulos em aberto

O detalhamento usa os mesmos critérios de data-base e competência do resumo de aging: inclui títulos sem pagamento ou pagos após a data-base e utiliza o valor nominal. A API ordena por vencimento e identificador, pagina no PostgreSQL e calcula dias de atraso e faixa no backend. Exige permissão financeira ALL e isolamento por Company; os valores são decimais exatos em texto.

O vencimento na própria data-base é a vencer, e não vencido. A consulta aplica a data-base ao estado atualmente armazenado; ela não reconstrói alterações ou estornos históricos. Agrupamento por parceiro, filtro hierárquico por centro e distinção de forma prevista/efetiva continuam em incrementos próprios. Nenhum lançamento ou baixa é alterado.
