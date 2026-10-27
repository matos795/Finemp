# API de controle de sincronização — #85 / #50

## FATO CONFIRMADO

Base: `/api/accounting-sources/{accountingSourceId}/sync-runs`.

| Operação | Contrato |
| --- | --- |
| POST | `Idempotency-Key` UUID obrigatório; corpo `mode`, `startDate`, `endDate`; 202 com a execução |
| GET | página por fonte, filtro opcional `status`; page 0, size 20, máximo 100 |
| GET `/{runId}` | detalhe apenas quando o run pertence à fonte autorizada |
| DELETE `/{runId}` | cancela somente REQUESTED, sem apagar registro; 204 idempotente para CANCELLED |
| GET `/{runId}/events` | histórico paginado de status/ator/tempo, sem payload contábil ou configuração externa |

Sessão, grant ALL e `QUESTOR_SYNC` na empresa dona da fonte são obrigatórios;
administração global não os substitui. O serviço revalida o acesso após o lock.
Essa revalidação usa consulta escalar ao banco (inclusive conta ativa), sem
reutilizar permissões que o contexto JPA possa ter carregado antes da espera.
Mutações exigem CSRF. O tipo atualmente suportado é Questor; outros adapters
terão provisionamento e capabilities explícitos, não um bypass genérico.

## DECISÃO técnica

- A mesma chave/fonte com modo e período iguais devolve a execução existente,
  sem evento ou trabalho duplicado. Parâmetros diferentes retornam 409.
- Uma solicitação ativa por fonte. Lock no registro da fonte e índice parcial
  trabalham juntos; duas solicitações idênticas concorrentes retornam o mesmo ID.
- A mutação exige READ_COMMITTED na conexão real, recusando uma transação externa
  incompatível. `lock_timeout` local de 5 segundos limita espera sem mudar a
  configuração global do PostgreSQL. Conflitos retornam 409 sanitizado.
- A janela padrão de 366 dias é limite de recursos configurável por solicitação,
  não regra contábil. Um replay válido é preservado mesmo se a política mudar.
- Solicitante, status, contadores, fonte e ator não são aceitos do corpo JSON.
  O servidor registra eventos e alterações na mesma transação. Falhas não recebem
  mensagem SQL livre. A trilha não possui API de alteração/exclusão; não é WORM.
- Métricas não medidas ficam nulas. REQUESTED não significa leitura ou importação
  realizada. DELETE não interrompe uma leitura RUNNING nem remove movimentos.

## Configuração / entrega incremental

`FINEMP_SYNC_CONTROL_ENABLED=false` por padrão; `FINEMP_SYNC_MAX_WINDOW_DAYS=366`.
O catálogo de capabilities publica `sync-history` condicionado a
`SYNC_CONTROL_ENABLED`; metadados não concedem acesso.

Este incremento entrega controle persistente, não executor. Não habilitar a
fila em produção antes do executor e da publicação atômica. INITIAL,
INCREMENTAL e RECONCILIATION continuam intenções explícitas, sem inferir cursor
ou regra de retificação/exclusão. A importação síncrona legada permanece separada;
antes de habilitar execução, ela também deverá respeitar exclusão mútua por fonte.

## PONTO ABERTO / próximos blocos

Identidade externa/CNPJ e provisionamento scoped continuam gates de produção.
Executor, publicação atômica, leases/retries, SyncState e FINEMP Connector seguem
na #50, sem bloquearem os módulos independentes do roadmap.

## Testes

Domínio/serviço protegem idempotência, bounds e ausência de concessão implícita.
HTTP/PostgreSQL cobre sessão, CSRF, escopo, whitelist, fonte inativa, cancelamento,
paginação e feature flag. Transações concorrentes verificam mesma chave,
solicitações distintas e recusa de isolamento externo incompatível. Sem Firebird
real ou dados de clientes nos testes regulares.
