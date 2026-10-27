# Tentativas, leases e retomada — Issue #92

## FATO IMPLEMENTADO

Cada tentativa tem número, fence, prazo UTC, última renovação, estado e código de falha sanitizado. `SyncRun` representa o ciclo completo: continua RUNNING durante o backoff; os eventos gerais REQUESTED/RUNNING/terminal continuam únicos por fase. `GET /api/accounting-sources/{sourceId}/sync-runs/{runId}/attempts` apresenta tentativas paginadas com o mesmo escopo QUESTOR_SYNC, sem expor payload, credenciais ou mensagens de exceção.

Falha, expiração, claim e renovação usam a mesma trava da fonte, sob READ_COMMITTED. O prazo exato já é vencido. Nenhuma operação de um worker vencido/substituído pode publicar, renovar ou registrar falha. Cada novo claim incrementa o contador do run e seu optimistic version; renovar altera somente a tentativa. SUCCEEDED da tentativa confirma na mesma transação que movimentos/contadores/sucesso/evento do run. Rollback preserva o prazo e estado anteriores.

## DECISÃO TÉCNICA

Somente READ_FAILED, PUBLICATION_FAILED e LEASE_EXPIRED podem gerar nova tentativa. Conflito, identidade, acesso, payload, limite de recursos e modo não confirmado não geram retry. Defaults reversíveis: 3 tentativas, lease 300s, atraso exponencial 30s/60s limitado a 300s. Configuração permite 1–10 tentativas, lease 5–3600s, atrasos positivos até 3600s. São limites de recursos, não regras contábeis. Reduzir o orçamento não cria tentativas extras nem reinicia a contagem.

Exaustão com política de retries registra RETRY_LIMIT no run; a tentativa preserva a causa específica. Com orçamento 1, a falha mantém o código original para permitir diagnóstico do caminho sem retries. Perda de lease é primeiro registrada como EXPIRED e só depois aguarda o backoff; não há novo claim no mesmo passo. O novo claim revalida fonte, solicitante e modo.

Retomar INITIAL relê toda a janela explicitamente solicitada. A publicação idempotente compara identidades/valores e publica tudo ou nada. Não há checkpoint parcial de linhas, truncamento, alteração de lançamentos anteriores, exclusão, nem cursor incremental inventado. Falhas permanentes exigem diagnóstico e nova solicitação autorizada; repetir a mesma Idempotency-Key apenas consulta a execução original.

V20 acrescenta tentativas sem alterar movimentos. Runs já RUNNING no upgrade recebem tentativa recuperável e fence incrementado, invalidando workers anteriores. Implantação deve coordenar a parada dos workers antigos antes da migração. A migração é versionada e testada em PostgreSQL descartável; não foi executada em banco operacional.

## PRÓXIMO PASSO / PONTO ABERTO

O worker/scheduler e o protocolo HTTPS do Connector serão incrementos separados. Esta entrega disponibiliza claim/renovação/retomada, não habilita um agendador. O executor permanece desligado por padrão e depende de prova real da identidade externa. A renovação periódica por worker ainda não está ligada ao I/O. Nenhum segredo Questor deve transitar na trilha da sincronização.

INCREMENTAL/RECONCILIATION, política de retificação e identidade operacional continuam gates explícitos. Não há alegação de integração produtiva completa ou substituição do Questor; FINEMP permanece camada gerencial.
