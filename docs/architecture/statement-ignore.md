# Ignorar movimento de extrato

## FATO / DECISÃO

A referência permite marcar um movimento pendente como ignorado. O comando exige autorização financeira vigente, versão esperada e mês do extrato aberto; sob bloqueio da Company, altera apenas o estado do movimento e grava auditoria e recibo na mesma transação. Não altera baixa, lançamento, valor ou saldo. Um movimento já vinculado ou ignorado não é transformado silenciosamente.

Uma reexecução idêntica devolve o recibo, mesmo após fechamento; reutilizar a chave com outro conteúdo conflita. A rota usa o filtro financeiro existente para autorização antes da leitura do corpo, JSON limitado, sessão e CSRF. Os opt-ins de lançamentos/comandos financeiros e da ação de extrato devem estar ativos.

## PONTO ABERTO

Reativação de movimento ignorado, conciliação/desconciliação e o tratamento de alterações ou estornos de baixas vinculadas permanecem separados. A ação de ignorar não resolve a #197.
