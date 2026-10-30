# Efeitos de lançamentos na DRE operacional

## FATO CONFIRMADO

Matriz F06 e função dreEfeitos da referência homologada (index.html, linhas 2128–2132): o nominal entra na competência, positivo para receita e negativo para despesa. Havendo baixa, a diferença efetivo − nominal recebe o mesmo sinal da direção, no mês do pagamento: RFIN quando positiva e DFIN quando negativa. Valor igual não produz diferença. A rotina retorna vazio para NAO.

## DECISÃO técnica

Calculador interno puro recebe FinancialEntryValues canônico e linha efetiva explícita. Não busca catálogo, não infere classificação ausente e não acessa banco. Retorna lista imutável de período, linha, categoria, origem e valor assinado BigDecimal. COMPETENCE e SETTLEMENT_DIFFERENCE distinguem a origem sem criar uma categoria fictícia no banco. Em centavos exatos, diferença não nula é equivalente ao limiar observado de 0,004, sem reproduzir arredondamento binário.

Não altera nominal/baixa nem recalcula tributo. Não inclui consulta/API/autorização: um futuro leitor deverá fornecer somente lançamentos da Company autorizada e a classificação efetiva. Não é DRE completa, não inclui bens/depreciação/alienação, margens ou hierarquia e não modifica a DRE contábil Questor.

## PONTO ABERTO

O handoff lista juros/descontos de itens NAO como decisão pendente. A exclusão observada foi isolada no calculador, sem declarar que é a metodologia definitiva. Se homologado tratamento diferente, deverá haver mudança explícita e novos casos de paridade. Captura monetária fora dos centavos canônicos continua na #128. Efeitos de bens dependem da #47.

## VALIDAÇÃO

Testes sintéticos de direção, juros/desconto, competência versus pagamento na virada do ano, ausência/igualdade de baixa, centavo, nominal zero, grande valor, NAO e entrada explícita. Nenhum dado real ou segredo.
