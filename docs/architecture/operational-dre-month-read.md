# Leitura mensal da DRE operacional — lançamentos

## FATO CONFIRMADO

F06 e o calculador de efeitos distinguem nominal assinado na competência e diferença efetiva da baixa no mês do pagamento. O catálogo possui linhas gerenciais atuais; subcategoria herda do grupo e a ausência usa NAO, conforme resolução já existente. A DRE contábil Questor permanece separada.

## DECISÃO

Serviço interno opt-in `finemp.finance.operational-dre.enabled`, desabilitado quando ausente. Company, usuário e mês AAAA-MM (anos 0001–9999) são explícitos. Exige permissão financeira VIEW ou MANAGE ALL vigente. Uma transação read-only REPEATABLE_READ lê por cursor PostgreSQL os lançamentos da Company com competência ou pagamento no mês. O cursor usa lotes de 500 linhas e os acumuladores são limitados às linhas da estrutura; não há lista em memória de todos os lançamentos nem paginação que trunque os totais.

Joins de categoria e grupo exigem a mesma Company. Cada lançamento passa pelo calculador de efeitos F06; somente efeitos do mês solicitado são agregados. Não confundir mês do vencimento com competência. A resposta contém as 12 linhas existentes exceto NAO, com competenceAmount, settlementDifference e amount, inclusive zeros. Valores exatos e textuais na serialização. Ordem segue os identificadores existentes, sem decidir hierarquia/drill-down oficial.

Report informa Company, mês, partial=true e coverage=[FINANCIAL_ENTRIES]. Não calcula LL/EBITDA ou margens nesta fatia. Classificações e estados são os atuais no snapshot da consulta; não há reconstrução histórica. Nenhuma escrita ou efeito de encerramento.

## PROPOSTA

Após validar esta leitura, expor contrato HTTP/OpenAPI separado e incorporar o bloco ao mesmo snapshot consistente da Apresentação. Fórmulas de subtotais/indicadores exigem paridade própria; não delegar cálculos ao frontend.

## PONTO ABERTO

NAO conserva exclusão observada isolada, inclusive sua diferença de baixa, sem declarar metodologia definitiva. Efeitos de bens/depreciação/alienação dependem de #47. Fontes ausentes não são representadas como implementadas. #197/#128 permanecem isoladas. Não inclui tributos automáticos, saldo, projeção, orçamento ou nova competência inferida.
