# Calendário de lançamentos — Issue129 /39

## FATO CONFIRMADO

Consulta pontual de salvarLanc775–818 e addMeses441 da referência homologada: vencimentos são deslocados desde a data original, limitando o dia ao último do mês. Janeiro31 produz fevereiro28/29 e março31; não se encadeia a data de fevereiro truncada. Parcelamento mantém documento/competência; recorrência mensal desloca também essas duas referências, cada uma desde sua própria origem. Data de lançamento permanece fixa. Somente a primeira ocorrência pode começar paga.

Uma entrada simples gera uma ocorrência; parcelamento e recorrência têm2–120. A referência não impõe ordem cronológica entre os campos de data; o calendário preserva valores explícitos sem deduzir essas restrições.

## DECISÃO

EntrySeriesCalendar usa LocalDate/YearMonth, não horários/fusos/clock. Campos são explícitos, datas limitadas tecnicamente aos anos1–9999, lista imutável e limitada a120. SINGLE exige quantidade1, INSTALLMENTS/MONTHLY exigem2–120. Overflow invalida a geração inteira antes de qualquer persistência. O componente não acessa dados, não grava lançamentos e não calcula valores.

## PONTO ABERTO MONETÁRIO ISOLADO — ISSUE128

A expressão legada de parcelas usa ponto flutuante:1.13/113 e0.29/29 geram parcelas iniciais zero e todo o total na última. Distribuição decimal exata produziria um centavo por parcela. Esses casos sintéticos foram registrados antes de transformar a fórmula em política rígida. Não substituir silenciosamente por DOWN/HALF_UP nem misturar essa decisão com regras confirmadas de calendário. A política monetária/compatibilidade deve seguir o registro de divergências exigido na matriz de paridade.

FinancialEntry, baixas/estorno, transferências, idempotência e uso real dos cadastros continuam na #39 e incrementos dependentes. Estoque/anexos/contratos têm módulos próprios. O calendário não encerra esses requisitos nem a128.

## VALIDAÇÃO

TDD de clamp, origem não cumulativa, competência/documento, ano bissexto, virada de ano, primeiro pagamento, limites/imutabilidade e anos técnicos. Datas sintéticas; nenhum dado real, valor financeiro novo ou integração operacional.
