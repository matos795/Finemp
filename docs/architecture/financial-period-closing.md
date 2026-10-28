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

#167 fornece comandos internos sob FINEMP_FINANCE_PERIODS_ENABLED=false por padrão. Ainda não libera endpoints nem afirma que writers já respeitam o fechamento. O próximo incremento integra o guard a cada mutação, incluindo todas as ocorrências mensais e todas as exclusões relacionadas, antes de disponibilizar API de fechar/reabrir. As verificações ocorrem depois do replay idempotente e antes dos efeitos: replay não reaplica mutação em mês fechado. Consulta/histórico paginados e documentação HTTP seguem depois dos bloqueios.

Uma política cumulativa, de datas de corte ou de calendário fiscal não foi informada e permanece aberta; não é presumida pelo estado mensal. Nada aqui fecha ou modifica o Questor.

## Validação

Oito contratos PostgreSQL sintéticos cobrem fechar/reabrir com histórico oficial, permissão/revogação/tenant, motivo/versão, identidade do comando, rollback no recibo, concorrência de reenvios, guard de todos os meses atuais e isolamento incompatível. Compilação local não substitui sua execução PostgreSQL na CI. Corrida com os writers e contratos HTTP serão validados na integração correspondente.
