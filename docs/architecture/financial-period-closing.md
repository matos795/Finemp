# Fechamento financeiro por Empresa — #158 / #167

## FATO CONFIRMADO / DECISÃO homologada

O fechamento pertence à Company. Reabrir exige motivo, permissão explícita e histórico. Mutações devem verificar os períodos realmente afetados: competência para criação/edição/exclusão; mês do pagamento para baixa/estorno; data da transferência. Alterações verificam estados anterior e novo. A implementação financeira e o PostgreSQL são a fonte de verdade.

## Incremento interno

O contrato inicial identifica um mês por YearMonth (0001-01 a9999-12), correspondente à competência e aos meses das datas financeiras. É um recorte técnico explícito; não define corte cumulativo, fechamento automático, limite para mês futuro, bloqueio contábil do Questor ou calendário fiscal.

V39 mantém um estado único Company/período, versão e responsável/data da última alteração. Ausência de estado representa mês aberto, com expectedVersion=null para o primeiro fechamento. O primeiro estado fechado recebe versão0; cada mudança posterior incrementa a versão. Reabrir mantém o mesmo recurso. Repetir estado com outra chave é conflito; reenvio da chave original recupera o recibo original sem reaplicar efeitos, inclusive após reabertura posterior. A identidade do comando é Company+ator+UUID, incluindo ação, mês, versão esperada e motivo normalizado no fingerprint. Não compartilha identidade com comandos de outro recurso.

FINANCIAL_PERIOD_CLOSE e FINANCIAL_PERIOD_REOPEN são permissões distintas e vigentes ALL, verificadas antes e depois da trava da Company. Nenhum papel administrativo ou grant de outro tenant as concede implicitamente. FINANCIAL_PERIOD_VIEW fica reservado à leitura do contrato seguinte. Não há grants automáticos. Reabertura exige motivo não vazio, até1000 caracteres, sem NUL ou Unicode inválido. A normalização remove somente espaços externos; o texto não é registrado em logs.

Fechamento e writers usam READ COMMITTED e a mesma trava da Company; isolamento ambiente incompatível é recusado. O guard consulta os meses informados de uma vez, com Company no predicado, permanece ativo mesmo com flags desligadas e deve ser chamado após a trava. Não há consulta a frontend, Excel ou banco operacional externo.

## Auditoria oficial e atomicidade

AccessAuditRecorder/Event foi ampliado com recurso, comando, estado anterior/novo e motivo. As ações FINANCIAL_PERIOD_CLOSED e FINANCIAL_PERIOD_REOPENED registram o ator e a permissão utilizada. Os campos novos são opcionais para eventos históricos de acesso, preservando seu contrato. Não foi criado outro diário de auditoria. Recibos operacionais idempotentes são separados dos eventos, assim como nos comandos financeiros existentes. Estado, auditoria e recibo confirmam na mesma transação; uma falha não deixa fechamento parcial.

## PRÓXIMO PASSO / PONTO ABERTO

#167 fornece comandos internos sob FINEMP_FINANCE_PERIODS_ENABLED=false por padrão. Ainda não libera endpoints de fechamento. A #169 integra o guard a cada mutação, incluindo todas as ocorrências mensais e todas as exclusões relacionadas, antes de disponibilizar API de fechar/reabrir. As verificações ocorrem depois do replay idempotente e antes dos efeitos: replay não reaplica mutação em mês fechado. Consulta/histórico paginados e comandos HTTP são acrescentados na #171, depois dos bloqueios.

Uma política cumulativa, de datas de corte ou de calendário fiscal não foi informada e permanece aberta; não é presumida pelo estado mensal. Nada aqui fecha ou modifica o Questor.

## Validação

Oito contratos PostgreSQL sintéticos cobrem fechar/reabrir com histórico oficial, permissão/revogação/tenant, motivo/versão, identidade do comando, rollback no recibo, concorrência de reenvios, guard de todos os meses atuais e isolamento incompatível. Compilação local não substitui sua execução PostgreSQL na CI. A #169 acrescenta sete contratos PostgreSQL/HTTP para competência anterior/nova, pagamento independente da competência, captura já paga, mudança da data de pagamento, conjuntos mensais/exclusões atômicos, transferências, replay, resposta409 e writer bloqueado atrás de fechamento concorrente.


## Integração das mutações — #169

EntryWriteTransactionGuard e BankTransferWriteGuard delegam ao guard de período sempre após a trava Company e o replay. Criação/edição/exclusão validam as competências de todos os estados envolvidos; um lançamento pago também afeta o respectivo mês de pagamento, inclusive ao remover ou mudar sua data, impedindo contorno pelo formulário de edição. Baixa/estorno verificam somente os meses de pagamento anterior/novo; não bloqueiam uma baixa em mês aberto apenas porque sua competência está fechada. Transferência verifica o mês de sua data tanto na criação quanto na exclusão.

Todos os meses são consultados em conjunto. Recorrência valida o calendário inteiro antes de persistir o grupo; exclusão relacionada valida os estados atuais de todos os alvos antes de remover vínculos/linhas. Qualquer mês fechado recusa o comando inteiro com409 FINANCIAL_PERIOD_CLOSED, sem recibo ou auditoria parcial. Recibos já existentes não mudam. O teste de concorrência mantém o fechamento sem commit enquanto um writer aguarda a trava; após commit, o writer lê o bloqueio e não persiste efeitos.


## Contrato HTTP — #171

Leitura por mês inclui estado virtual aberto quando não persistido; a coleção contém somente estados existentes e aceita intervalo inclusivo/status/paginação1–100. Histórico consulta exclusivamente eventos oficiais FINANCIAL_PERIOD vinculados ao recurso e à Company, ordenados por instante e UUID e com paginação no SQL. As consultas têm transação com timeout5s e não carregam fontes externas ou dados de acesso de outros recursos.

CLOSE e REOPEN têm autorizações separadas antes do consumo de corpo, por rota normalizada. O filtro reutiliza a infraestrutura de limites financeiros com marcador próprio,64KiB/4 comandos concorrentes, sem reinterpretar URI. O controller fica sob opt-in adicional de comandos, desligado por padrão. JSON privado/estrito recebe somente expectedVersion e, na reabertura, reason. O actor/Company vêm da sessão/rota. Serviços revalidam permissão após trava, sem confiar no controller ou frontend.

Três testes locais de decoder cobrem precondições, ambiguidades e calendário; seis contratos PostgreSQL/HTTP cobrem os dois grants, estado/diário real, paginação/filtros, replay/conflito, corpo normalizado antes/depois da autorização, CSRF e validação. Captura/transferência e bloqueios já são cobertos na #169; essa matriz não é duplicada aqui.
