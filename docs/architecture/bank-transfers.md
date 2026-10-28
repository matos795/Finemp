# Transferências internas entre contas financeiras

Referências: #39, #143 e #145. Persistência e integridade foram integradas no PR144. Os comandos internos têm recibos e auditoria próprios; a exposição HTTP permanece em incremento separado.

## FATO CONFIRMADO

No FINEMP_web_1, abrirTransf exige pelo menos duas contas ativas para abrir o formulário. salvarTransf exige origem e destino diferentes, data e valor bruto positivo, e guarda descrição opcional normalizada. O registro pertence a transf, separado dos lançamentos financeiros. excluirTransf remove a transferência; os saldos apresentados são derivados. Não há evidência de exigência de data posterior à abertura da conta, nem de veto universal a uma conta inativa que já esteja selecionada.

## DECISÃO técnica — persistência independente

BankTransfer pertence a uma Empresa, com origem, destino, data, valor canônico, descrição, ator/data de criação e versão. Ambas as contas possuem chaves estrangeiras compostas com Empresa: nenhuma ponta pode referenciar conta de outra Empresa, Cliente ou Contabilidade. Não há criação de lançamentos financeiros artificiais, alteração de saldo inicial, transferência bancária externa ou gravação no Questor.

Valores são decimais exatos NUMERIC(19,2), sem arredondamento na entrada canônica. O estado admite zero para representação, como a fundação de lançamentos; isso não define o contrato operacional de criação. Datas admitem anos de 1 a 9999. A descrição opcional é limitada a 255 caracteres Java, normalizada e rejeita NUL ou UTF-16 inválido. Diagnósticos não exibem valores privados.

O adaptador real de referências de contas consulta origem OU destino por Empresa em SQL atual, sem confiar no estado antigo de um objeto JPA. Permanece ativo mesmo se os produtores operacionais estiverem desabilitados. A exclusão de uma conta usada segue a política já existente do catálogo: desativar e preservar o histórico. Versão otimista protege também a exclusão de um estado desatualizado. O repositório não é uma superfície pública nem substitui a autorização dos próximos comandos.

## DECISÃO técnica — comandos internos opt-in

FINEMP_FINANCE_TRANSFERS_ENABLED é desabilitado por padrão e ativa somente o serviço interno. BANK_ACCOUNT_MANAGE ALL explícito e vigente é exigido antes e depois do bloqueio por Empresa, conforme a matriz funcional da tela de contas. Nenhum papel recebe acesso implícito. A transação exige READ COMMITTED, com espera de bloqueio limitada a cinco segundos. A criação recebe somente valor positivo já exato em centavos; valida as duas contas em SQL atual e a precondição de pelo menos duas contas ativas, sem transformar isso em veto universal a uma seleção histórica inativa.

Excluir exige versão esperada não negativa. A linha é bloqueada e sua versão atual é comparada à versão esperada e ao objeto gerenciado; contexto JPA antigo falha de forma segura. Não revalida a precondição de captura nem altera bancos. O recibo DELETED informa a última versão persistida, e não uma versão nova de recurso já removido.

O namespace de comandos de transferência possui chave UUID por Empresa e ator, compartilhada entre criar e excluir. SHA-256 com esquema versionado e comprimentos prefixados inclui contexto, ação, alvo, versão e dados canônicos. Reenvio exige permissão atual e retorna o recibo original antes de consultar bancos ou alvo, inclusive após exclusão. Conteúdo/ação diferente com a mesma chave conflita. O namespace é independente dos comandos de lançamentos.

Registro/exclusão, evento e recibo são uma transação. Criar registra estado posterior; excluir registra estado anterior e estado posterior ausente. Não há FK para a transferência no diário: excluir não elimina o histórico. Empresa e ator têm FKs. Estados são JSON canônico privado e não aparecem em logs/respostas; consulta e retenção da auditoria continuam na #51. Não é uma alegação de imutabilidade contra administrador do banco.

## DECISÃO técnica — consulta HTTP

O incremento #147 expõe apenas GET da coleção e item por Empresa, com o opt-in de transferências. Exige BANK_ACCOUNT_VIEW ou MANAGE ALL vigente. A projeção consulta valores e nomes atuais das duas contas no mesmo comando JPQL, inclusive se o contexto JPA já tiver entidades antigas. Não retorna atores ou estados privados de auditoria. A coleção pagina no servidor, de 1 a 100 itens por página, com data decrescente e UUID como desempate estável. A resposta mantém dinheiro como texto decimal exato. Não adiciona cálculo de saldo ou escrita HTTP.

## PONTO ABERTO / PRÓXIMO PASSO

A normalização de valores brutos e os efeitos monetários legados continuam na #128. Não foram resolvidos por uma coluna decimal. O próximo incremento exporá a escrita HTTP com limites e contrato próprios. Não existe operação de alteração homologada no formulário de transferências; não será inventada.

Os testes usam somente dados sintéticos: precisão e limites, persistência PostgreSQL, isolamento das duas pontas, proteção real de contas, referências atuais sem cache, identificadores iguais em tipos diferentes e conflito de versão. PostgreSQL é validado no CI com Testcontainers.
