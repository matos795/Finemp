# Porta canônica de leitura contábil — #81 / #50

## FATO CONFIRMADO

`AccountingSourceReader` recebe contexto imutável e devolve somente movimentos
canônicos `ImportedAccountingMovement`. O registry seleciona adapter por tipo,
recusa duplicidade na inicialização e falha fechado sem adapter disponível.
O bridge `QuestorSourceReader` preserva os argumentos e o SQL do reader Questor
já validado. Questor continua origem contábil e não recebe gravações.

A importação existente carrega fonte/empresa antes do I/O externo, valida fonte
ativa, tipo, referência e período, e só chama a ingestão depois de uma leitura
completa. Não abre uma transação PostgreSQL ao redor da consulta ao ERP.
O contexto inclui IDs internos, tipo, referência externa, CNPJ esperado e datas;
não inclui credenciais. Sua representação textual omite referência e CNPJ.

Falha de leitura não chama ingestão nem apaga importações anteriores. A API
retorna erro neutro 502, sem causa JDBC/SQL. Fonte inativa recebe 409; contexto
inválido recebe 400. O guard HTTP `QUESTOR_SYNC` continua exigindo grant e
escopo da empresa dona da fonte; a porta não é um bypass de autorização.

## DECISÃO técnica

A porta pertence ao contrato canônico, não ao domínio específico de um ERP.
O adapter Questor e seu SQL permanecem isolados. Adicionar outros tipos requer
adapter e validações próprios; este incremento não declara outros ERPs prontos.
Registry vazio é válido com integrações desabilitadas, mas não permite leitura.

## PONTO ABERTO — gate de produção multi-tenant

CNPJ esperado no request NÃO comprova a identidade da empresa devolvida pelo ERP.
Ainda é necessário verificar o catálogo real, conferir identidade externa com a
empresa FINEMP e provisionar credenciais/Connector no escopo da contabilidade.
Não foi inventada consulta de identificação nem se afirma que a integração
multi-tenant operacional esteja homologada. Testes deste incremento são
sintéticos e não acessam Firebird real ou credenciais.

Próximos blocos da #50: SyncRun/State e publicação atômica, identidade/protocolo
do Connector, retries e resumibilidade. O endpoint síncrono atual é preservado
até migração explícita para o controle de sincronização.
