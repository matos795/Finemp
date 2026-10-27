# Publicação e recibo do Connector — Issue #103

## FATO IMPLEMENTADO

POST `/api/connector/v1/work/{attemptId}/complete` recebe identidade observada e lote canônico; POST `/fail` admite somente código restrito da origem; GET `/result` devolve recibo sem linhas contábeis. HTTPS/bearer, budgets de bytes/concorrência e parser streaming da #105 são obrigatórios. O agente não fornece empresa, período, fence, status de sucesso ou counters.

Facade NEVER resolve contexto em transação curta e faz parsing fora de transação. A publicação abre nova transação, trava a fonte e revalida rota CONNECTOR, credencial/versão/ownership, solicitante, contexto, tentativa, lease e fence. Identidade observada precisa corresponder à expectativa. Contas/movimentos/counters/sucesso da tentativa e do run/evento terminal confirmam ou revertem juntos.

O caminho direto continua separado e recusa origem Connector. Não há fallback ao banco configurado no SaaS. Nenhum I/O ERP ocorre no servidor durante o recebimento. A identidade observada ainda é declaração do agente; o adapter local deverá obtê-la realmente no ERP.

Após rollback, uma rejeição tipada ou falha de infraestrutura só é registrada pelo lifecycle em OUTRA transação, com nova validação da posse. Não se tenta salvar falha em transação marcada rollback-only. Posse/credencial/fence alterado gera 409 sem modificar trabalho novo. READ_FAILED/PUBLICATION_FAILED continuam sujeitos ao orçamento/backoff; conflitos/identidade/payload são permanentes, sem sobrescrever dados.

## DECISÃO TÉCNICA

Formato/JSON inválido retorna 400 e limite retorna 413 sem mutar o run. Rejeição semântica/canônica pode retornar 422 com falha registrada; infraestrutura com retry agendado retorna 202. GET do recibo sempre é leitura de estado. Nenhuma resposta traz erro livre ou corpo original.

Se a resposta de upload se perder, o agente consulta result antes de reenviar. POST de tentativa encerrada retorna 409 e nunca altera dados. Novo run com linhas idênticas usa a identidade/idempotência já existente e mede skipped. Não há aceitação silenciosa de um payload modificado como se fosse uma publicação anterior.

Receipt distingue estado da tentativa e do run, além de currentAttempt. Counters só aparecem se ESTA tentativa teve sucesso. Uma tentativa antiga falhada não recebe counters do sucesso de outra tentativa. Rotação/revogação invalida o vínculo anterior também para recibos; administração humana continua podendo consultar o histórico pelo escopo autorizado.

## PONTO ABERTO / CONTINUIDADE

O agente local usará saída HTTPS, sem redirecionar segredos, e precisará verificar identidade real antes de ler movimentos. Instalação, custódia de segredos, trust TLS, timeouts e homologação operacional seguem separados. Retificações/cursor e políticas contábeis não confirmadas continuam abertas; este fluxo apenas publica INITIAL sem overwrite/delete. Nenhuma migração operacional ou dado real foi utilizado.
