# Ciclo de vida persistente da sincronização — #83 / #50

## FATO CONFIRMADO — incremento de persistência

SyncRun registra fonte, solicitante por UUID, chave idempotente, modo, período
explícito, status e tempos UTC. A V16 exige unicidade fonte/chave e permite
somente uma execução REQUESTED/RUNNING por fonte, inclusive entre processos.
Consultas do repositório são delimitadas pela fonte; autorização HTTP será
entregue junto ao controlador, não é alegada como pronta neste incremento.

Fluxo de estado:

`REQUESTED → RUNNING → SUCCEEDED | FAILED`

`REQUESTED → CANCELLED`

Estados terminais não são reabertos. Cancelamento repetido é idempotente;
cancelamento de trabalho RUNNING não é suportado por este contrato. Versionamento
otimista evita alteração silenciosa de um estado carregado anteriormente.

## DECISÃO técnica

- Contadores ficam nulos antes de sucesso: desconhecido não é zero medido.
  No sucesso, read = imported + skipped, todos não negativos e sem overflow.
  O executor deverá informar métricas medidas na mesma publicação transacional.
- Falhas usam somente enum sanitizado; não há mensagem livre, SQL, credencial,
  payload bruto ou histórico contábil no registro de controle.
- Solicitação e intervalos são imutáveis. Repetição da chave deve comparar modo
  e período no serviço antes de devolver a execução existente.
- Serialização por fonte reduz risco de concorrência sobre o mesmo conjunto
  contábil. Não é uma restrição contábil sobre empresas ou períodos.

## PONTO ABERTO / sucessores

INITIAL, INCREMENTAL e RECONCILIATION são intenções registradas, não prova de
algoritmos diferenciados já implementados. Cursor, retificações/exclusões da
origem, validação da identidade externa e política de reconciliação não foram
inventados. Nenhum movimento é alterado por este incremento.

A API de solicitação/consulta, executor, publicação atômica com métricas,
SyncState, leases/retries e Connector serão PRs separados. Não existe scheduler
ou endpoint novo de sincronização neste PR; a importação síncrona anterior
continua disponível sob seus guards. A #50 permanece aberta.

## Testes

Transições, cancelamento, idempotência semântica, tempos e overflow têm testes
de domínio. PostgreSQL verifica isolamento por fonte, índices de exclusão mútua,
chave persistente após estado terminal e rejeição de métricas inconsistentes.
Dados são sintéticos e transacionais; nenhum ambiente contábil real é acessado.
