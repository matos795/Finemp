# Worker de sincronização — Issue #94

## FATO IMPLEMENTADO

Worker interno opt-in busca apenas IDs REQUESTED, RUNNING sem tentativa, tentativa vencida ou retry já devido. A busca tem limite 1–8, ordem estável e query timeout de 5s; não carrega lotes contábeis. ID candidato não concede autorização: o claim da #92 revalida fonte, solicitante e modo sob trava. Instâncias diferentes continuam arbitradas pelo banco.

Semáforo + pool com SynchronousQueue impedem fila ilimitada. IDs locais em andamento são excluídos na consulta antes do LIMIT, impedindo que retry de leitura travada ocupe o início da fila e deixe outra vaga ociosa. A lista de exclusão tem no máximo 8 UUIDs parametrizados. Rejeição devolve capacidade. Polling é serializado e falha de infraestrutura permite um próximo tick. Contadores internos, sem textos externos, distinguem falhas de infraestrutura, rejeições de capacidade e timeouts.

Heartbeat usa pool separado e renova somente tentativa vigente. Perder a renovação interrompe os próximos heartbeats; publicação ainda exige lease/fence. No tempo máximo da tentativa, registra READ_FAILED pelo lifecycle para encerrar/agendar retry de forma limitada. Não interrompe uma thread capturada por heartbeat: ela pode já ter sido reutilizada. A posse antiga fica impedida de publicar pelo estado/fence, inclusive se o driver retornar tardiamente.

`close()` é idempotente, encerra polling/heartbeats/pool e sinaliza interrupção às tarefas. Lease e fence mantêm o trabalho recuperável mesmo quando um driver ignora interrupção. Recursos externos devem ter seus próprios timeouts de conexão/leitura; Java não garante matar uma chamada nativa bloqueada. Não se alega essa garantia.

## DECISÃO TÉCNICA

Default worker desligado; concorrência 2 (máximo 8), polling 5s (1–60s), execução 900s (pelo menos um lease, máximo 3600s). Heartbeat a cada um terço do lease, mínimo 1s. Todos são limites de recursos reversíveis, não regras de negócio. Habilitar sem executor controlado configurado falha na inicialização; não há fallback ao importador legado.

I/O ERP fica fora de transação PostgreSQL. `executeClaim` é um ponto interno do serviço, sem rota HTTP; a mesma checagem de identidade e publicação é usada. O browser só solicita intenção via API autenticada/CSRF existente. Não há endpoint para fornecer SQL ou marcar um run como sucesso.

## PONTO ABERTO / CONTINUIDADE

Prova operacional de identidade externa, credenciais/provisionamento scoped do Connector e políticas incremental/retificação continuam separados. Esta entrega não cria credenciais e não habilita execução operacional. O Connector terá saída HTTPS a partir da contabilidade; segredos ERP permanecem locais. Questor segue somente leitura e FINEMP permanece camada gerencial.
