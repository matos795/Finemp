# Consulta de movimentos de extrato

A consulta somente de leitura lista movimentos de uma conta pertencente à Company autorizada, com filtros opcionais de estado e data e paginação no PostgreSQL. Data decrescente e identificador garantem ordem determinística; contagem e página vêm de um snapshot consistente. O valor com sinal permanece decimal exato, serializado como texto. Exige FINANCIAL_ENTRY_VIEW ou FINANCIAL_ENTRY_MANAGE com escopo ALL e não altera saldos, lançamentos nem vínculos.

A consulta permite ver movimentos já importados, inclusive ignorados e conciliados. A conciliação e o tratamento de estorno de baixa vinculada permanecem separados na #197.
