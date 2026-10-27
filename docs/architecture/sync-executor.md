# Executor controlado — #88 / #50

## FATO CONFIRMADO — incremento implementado

Fluxo interno, sem novo endpoint de execução:

`claim curto no PostgreSQL → identidade externa → movimentos canônicos → porta de publicação atômica`

O claim serializa pela fonte, revalida fonte ativa e permissão/conta do
solicitante, registra RUNNING e materializa contexto/versão antes de liberar a
transação. Só um worker obtém o claim. Uma falha só altera uma execução ainda
RUNNING com a mesma versão, fonte e modo; contexto obsoleto não afeta outro trabalho.

O executor recusa uma transação externa (`NEVER`), em vez de suspendê-la e deixar
conexões/locks abertos durante o I/O. Identidade e movimentos são lidos fora da
transação PostgreSQL. A porta de publicação deverá persistir movimentos,
contadores e SUCCEEDED na mesma transação: implementação concreta é o próximo PR.

## DECISÃO técnica — falhar fechado

- Identidade externa é obrigatória antes dos movimentos. Referência e CNPJ são
  comparados; pontuação convencional do CNPJ é normalizada, sem inventar dígitos.
  O port não pode simplesmente devolver o CNPJ esperado do cadastro FINEMP.
- Erros externos não viram mensagem livre no histórico; somente enum sanitizado.
  Falha de identidade/leitura não chama a publicação.
- INITIAL é o caminho inicial. Sem estratégia validada, INCREMENTAL e
  RECONCILIATION terminam com UNSUPPORTED_MODE, sem inferir cursor, exclusão ou
  retificação. O status persistido é a verdade para o consumidor; NOT_CLAIMED é
  resultado interno também para um claim rejeitado que já registrou FAILED.
- O histórico tem fase causal 0/1/2 (solicitação, processamento, terminal),
  preservando ordem mesmo com timestamps UTC iguais. A V18 mantém dados existentes
  e adiciona restrições; não descarta eventos inconsistentes silenciosamente.
- Com execução controlada habilitada, controller/serviço da importação síncrona
  legada ficam desativados para não contornar a exclusão mútua. O reader/SQL
  Questor continua disponível ao adapter. O perfil prod também bloqueia o caminho
  legado não verificado, mesmo com executor desligado. Fora de prod e com executor
  desligado, o contrato legado de estudo é preservado.

## Configuração e gates

`FINEMP_SYNC_EXECUTOR_ENABLED=false` e controle desligado por padrão. Habilitar
executor requer controle habilitado, leitor de identidade real e publicador
atômico. Sem esses ports, a configuração falha; não existe fallback permissivo.
Não habilitar em produção apenas para tornar a fila visível.

## PONTO ABERTO / próximo incremento

Não há publicador concreto nem leitor operacional de identidade neste PR. Os
ports são obrigatórios e os testes usam doubles explícitos, não prova de acesso
operacional. A consulta de identidade/provisionamento scoped continua em validação.
Publicação atômica, leases/retries/resumibilidade, scheduler e Connector seguem
na #50. Questor permanece contábil e somente leitura; FINEMP é camada gerencial.

## Verificação

TDD local do executor: cinco falhas comportamentais no RED; seis cenários verdes
após implementação. Testes de configuração e identidade protegem gates/redação.
PostgreSQL cobre claim concorrente, versão obsoleta, solicitante revogado,
fonte inativa, ordem causal e ausência de transação durante I/O. A publicação
dos testes de decomposição é uma fake explícita para lote vazio; não substitui
os testes do publicador concreto do próximo incremento.
> Atualização #89: [publicação atômica concreta](atomic-accounting-publication.md).
> O leitor operacional de identidade continua obrigatório e em validação; o
> incremento #88 abaixo registra sua etapa de coordenação/ports.

