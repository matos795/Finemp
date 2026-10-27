# Publicação contábil atômica — #89 / #50

## FATO CONFIRMADO — contrato implementado

A porta concreta recusa transação externa. Um serviço transacional separado abre
READ_COMMITTED, bloqueia a fonte e confere versão, período, contexto da empresa,
fonte ativa e permissão atual do solicitante. Nenhuma leitura ERP nessa transação.

O lote inteiro é validado e comparado com contas/movimentos apenas dessa fonte
antes de gravar. Movimentos novos, contadores medidos, SUCCEEDED e evento terminal
confirmam ou revertem juntos. Falha no evento também reverte contas/movimentos.
Identidade/valor já publicada e duplicata interna idêntica são skipped.

## DECISÃO técnica

- Identidade delimitada por fonte, chave e lado formaliza o contrato anterior da
  ingestão, não identidade global entre empresas/ERPs. Adapters devem fornecer
  chave de linha/movimento estável nos períodos que importam.
- V19 reforça unicidades e FK conta/fonte. Trigger preenche origem para writer
  legado que omite a coluna; origem explícita errada é negada.
- Preflight recusa duplicidades sem apagar/deduplicar dados e sem expor valores
  no erro. A migração bloqueia escrita durante a mudança: avaliar staging/janela.
  Não foi executada em base operacional.
- BigDecimal/NUMERIC(19,4), sem arredondamento/truncamento silencioso. Preserva
  sinal e texto literal; sem balanceamento artificial ou interpretação de sentinelas.
- Lookups em blocos de 500. FINEMP_SYNC_MAX_RECORDS padrão 25000 é limite de
  recursos, não contábil; medir/ajustar ou solicitar períodos menores.
- JDBC solicita limite+1 e recusa excesso com RESOURCE_LIMIT, fechando recursos.
  Nunca apresenta lote truncado como completo.

## PONTO ABERTO

Divergência de identidade existente ou metadados da conta gera DATA_CONFLICT,
preservando publicações anteriores. Quarentena conservadora não significa que
contas nunca mudam nem define reconciliação. Retificações, renomeações,
reclassificações e exclusões continuam em validação. Sem sobrescrita/exclusão automática.

INITIAL é suportado; INCREMENTAL/RECONCILIATION não foram inventados. Leitor real
de identidade e provisionamento scoped continuam gates obrigatórios antes do I/O.

## Verificação / sequência

Validador: seis RED e seis GREEN. Testes locais de dinheiro, executor e limite
JDBC passaram. PostgreSQL cobre replay, fontes independentes, payload inválido,
fence, revogação e rollback com falha injetada no evento terminal. A migração com
duplicidade deve preservar quantidade/valores ao falhar. Somente dados sintéticos.

Seguem leases/tentativas/retries/resumibilidade e FINEMP Connector na #50.
Não habilitar operação sem concluir identidade e operação do worker.
