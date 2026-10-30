# Rastreabilidade dos efeitos da DRE operacional

## FATO CONFIRMADO

F06 produz nominal assinado na competência e, havendo diferença de baixa, outro efeito no mês do pagamento. O mesmo lançamento pode gerar dois efeitos na mesma linha/mês. A leitura mensal já usa esse cálculo; a exclusão observada de NAO permanece isolada como ponto aberto.

## DECISÃO

GET /api/companies/{companyId}/financial-reports/operational-dre/effects exige sessão, permissão financeira VIEW/MANAGE ALL vigente, mês explícito e o opt-in operacional-dre. Linha é opcional, page/size têm limites usuais. Retorna lista plana e cobertura parcial FINANCIAL_ENTRIES; não define hierarquia nova. A identidade de um efeito é entryId + source no mês consultado. Não deduplicar somente pelo lançamento.

Para filtrar e paginar efeitos no PostgreSQL, uma CTE reproduz a fórmula canônica com UNION ALL. Valores vêm de numeric exato, sem conversão para ponto flutuante ou arredondamento. Testes de integração comparam a soma de cada linha com o relatório que usa o calculador Java F06. A classificação é a mesma resolução atual subcategoria/grupo/NAO; os joins exigem a Company.

Contagem é de efeitos. Contagem e página compartilham REPEATABLE_READ; ordem estável por entryId/source. O efeito da baixa conserva categoria original e possui origem explícita, sem inserir uma categoria fictícia. O frontend usa valores calculados e pode navegar ao lançamento por sua API autorizada; uma página incompleta não é o total da DRE.

## PROPOSTA

Usar este detalhe para conferência das linhas do relatório. Não carregar listas ilimitadas no snapshot da Apresentação.

## PONTO ABERTO / limites

Não há token de snapshot entre requisições: uma alteração posterior pode mudar resumo e detalhe requisitados em momentos diferentes. A Apresentação continua obtendo os seus blocos agregados num snapshot único. Hierarquia oficial, efeitos de bens, histórico reconstruído, tributos e novas interpretações de NAO não são introduzidos. #197 e #128 permanecem isoladas.
