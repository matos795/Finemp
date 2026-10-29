# Consulta de candidatos de conciliação

## FATO / DECISÃO

A consulta reproduz a seleção de candidatos já observada no protótipo e verificada pelo matcher: direção pelo sinal, diferença inferior a 0,011, exclusão de lançamentos já vinculados, baixados na mesma conta e até três dias da data do extrato, abertos até quinze dias do vencimento. Baixados aparecem primeiro, seguidos pela distância em dias; o identificador desempata para garantir paginação estável. Os filtros e a paginação são aplicados no PostgreSQL em um snapshot de leitura consistente. A sugestão não faz conciliação nem baixa.

A API exige permissão de visualização ou gestão financeira com escopo ALL na Company. O movimento consultado e todos os candidatos pertencem à mesma Company; a consulta não grava dados. Valores monetários são retornados como texto decimal exato.

## PONTO ABERTO

Seleção manual divergente, conciliação de lançamento aberto e tratamento do estorno ou alteração de uma baixa vinculada seguem separados na #197. A consulta de candidatos não determina essas regras.
