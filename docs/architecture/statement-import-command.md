# Confirmação interna de movimentos de extrato

## DECISÃO

O comando interno recebe uma seleção explícita de movimentos canônicos, empresa, conta e identificador idempotente. Sob o bloqueio de escrita da empresa, revalida permissão, conta ativa e períodos das datas dos movimentos. Uma repetição idêntica devolve o mesmo recibo; a mesma chave com conteúdo diferente falha. O registro dos movimentos, recibo e auditoria ocorre em uma transação. Um FITID já existente ou repetido na seleção cancela a operação inteira. Identidades de semelhança sem FITID não impedem movimentos distintos.

O limite técnico é de 5.000 movimentos por confirmação e até 240 meses distintos, conforme o guarda de períodos existente. Sem prejuízo da atomicidade, um arquivo maior precisará de uma operação futura adequada; esse limite não é uma regra contábil.

## PONTO ABERTO

O comando não é exposto por API. A operação de confirmação futura precisa validar explicitamente a seleção e os alertas do arquivo, inclusive moeda, múltiplas contas e escolha da conta financeira, que não possui identificador bancário para comparação automática. Não há baixa, conciliação ou alteração de saldo neste incremento. O tratamento de FITID reutilizado depende de decisão posterior.
