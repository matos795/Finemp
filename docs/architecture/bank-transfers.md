# Transferências internas entre contas financeiras

Referências: #39 e #143. Este incremento entrega persistência e integridade das referências; comandos, autorização operacional, recibos e API serão entregues antes de disponibilizar a escrita.

## FATO CONFIRMADO

No FINEMP_web_1, abrirTransf exige pelo menos duas contas ativas para abrir o formulário. salvarTransf exige origem e destino diferentes, data e valor bruto positivo, e guarda descrição opcional normalizada. O registro pertence a transf, separado dos lançamentos financeiros. excluirTransf remove a transferência; os saldos apresentados são derivados. Não há evidência de exigência de data posterior à abertura da conta, nem de veto universal a uma conta inativa que já esteja selecionada.

## DECISÃO técnica — persistência independente

BankTransfer pertence a uma Empresa, com origem, destino, data, valor canônico, descrição, ator/data de criação e versão. Ambas as contas possuem chaves estrangeiras compostas com Empresa: nenhuma ponta pode referenciar conta de outra Empresa, Cliente ou Contabilidade. Não há criação de lançamentos financeiros artificiais, alteração de saldo inicial, transferência bancária externa ou gravação no Questor.

Valores são decimais exatos NUMERIC(19,2), sem arredondamento na entrada canônica. O estado admite zero para representação, como a fundação de lançamentos; isso não define o contrato operacional de criação. Datas admitem anos de 1 a 9999. A descrição opcional é limitada a 255 caracteres Java, normalizada e rejeita NUL ou UTF-16 inválido. Diagnósticos não exibem valores privados.

O adaptador real de referências de contas consulta origem OU destino por Empresa em SQL atual, sem confiar no estado antigo de um objeto JPA. Permanece ativo mesmo se os produtores operacionais estiverem desabilitados. A exclusão de uma conta usada segue a política já existente do catálogo: desativar e preservar o histórico. Versão otimista protege também a exclusão de um estado desatualizado. O repositório não é uma superfície pública nem substitui a autorização dos próximos comandos.

## PONTO ABERTO / PRÓXIMO PASSO

A normalização de valores brutos e os efeitos monetários legados continuam na #128. Não foram resolvidos por uma coluna decimal. O próximo incremento terá comandos de criar/excluir, permissão vigente, bloqueio por Empresa, contrato positivo em centavos, versão esperada para excluir, evento e recibo idempotente na mesma transação. Só depois será exposta API limitada e documentada. Não existe operação de alteração homologada no formulário de transferências; não será inventada.

Os testes usam somente dados sintéticos: precisão e limites, persistência PostgreSQL, isolamento das duas pontas, proteção real de contas, referências atuais sem cache, identificadores iguais em tipos diferentes e conflito de versão. PostgreSQL é validado no CI com Testcontainers.
