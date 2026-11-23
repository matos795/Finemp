# Estado de sincronização por empresa — #440 (M3)

## FATO CONFIRMADO

`GET /api/companies/{companyId}/accounting-sources` lista as AccountingSources da
Company com o estado registrado em SyncRun:

| Campo | Significado |
| --- | --- |
| `latestRun` | Última execução solicitada, qualquer status (REQUESTED, RUNNING, SUCCEEDED, FAILED, CANCELLED). |
| `latestSucceededRun` | Última execução SUCCEEDED: período (`startDate`–`endDate`) e contagens efetivamente publicados. |

Sem execução, os dois campos são `null`. A resposta não traz `externalReference`
nem configuração externa. Ordem: tipo e ID. `Cache-Control: no-store`.

## DECISÃO técnica

- Autorização: `QUESTOR_SYNC` na Company com escopo ALL, a mesma da API de SyncRun.
  Rota explícita no `SecurityConfiguration` e checagem fresca no serviço, que vale
  na mesma sessão após revogação.
- Permissões da contabilidade (`HIERARCHY_*`, `ACCOUNTING_SOURCE_*`) não leem este
  estado: navegar ou administrar fontes não é acesso a dados da Company (M1, #435).
- Somente leitura de histórico: funciona com `FINEMP_SYNC_CONTROL_ENABLED` desligado.
  Solicitar, cancelar e ver eventos/tentativas continuam na API de SyncRun.
- "Última SUCCEEDED" é por ordem de solicitação; há no máximo uma execução ativa
  por fonte, então coincide com a ordem de término.

## PONTO ABERTO

- Presença/versão do Connector: o protocolo atual não comprova presença contínua
  (#432). Não exibir "online" a partir deste contrato.
- Cobertura contínua (meses sem movimento, alterações/exclusões na origem) depende
  de O3 do [GAP](../reference/GAP_CONTABIL.md). A janela SUCCEEDED é o período pedido
  e publicado, não prova de completude da origem.
