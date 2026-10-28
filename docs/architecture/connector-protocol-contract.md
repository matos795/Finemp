# Contrato composto outbound — Issue #116

## ESCOPO DA VALIDAÇÃO

O contrato integrado conecta FinempConnectorClient e ConnectorAgentCycle aos controllers/filtros reais do protocolo, transações e PostgreSQL de Testcontainers. A ponte de teste usa MockMvc marcado como seguro e transporta JSON dos DTOs efetivos; não substitui a prova de TLS real nem acessa Firebird. TLS, certificados e redirects têm teste loopback separado; identidade/JDBC do factory tem contrato sintético separado.

Cenários: resposta perdida depois do commit é resolvida por receipt sem segundo POST ou movimento duplicado; replay manual é recusado; token de outra AccountingSource é recusado antes do claim/I/O; cancelamento entre leitura e commit impede publicação; linhas fora do período não são enviadas. Fixture e limpeza são restritas aos IDs sintéticos criados pelo teste. Não há credenciais/dados reais nem banco operacional.

## DECISÃO TÉCNICA

Credenciais novas usam versão técnica zero no contrato persistido e na sessão emitida pelo servidor. O decoder aceita inteiro não negativo obrigatório, preservando a conferência de origem. Valores ausentes, negativos, fracionários e fora de int64 são recusados. Isso não altera o número da tentativa, que continua começando em um, nem a revalidação de credencial/fencing no servidor.

Recibo com execução CANCELLED é terminal sem ACK, inclusive quando a tentativa ainda aparece RUNNING. O ciclo encerra essa pendência sem lote repetido ou POST de falha. SUCCEEDED continua exigindo mesma tentativa/run/número e contadores coerentes; cancelamento não é convertido em sucesso. Regra descoberta por teste RED e corrigida com teste GREEN do ciclo.

## LIMITES

PostgreSQL/HTTP integrado é executado na CI quando Docker não está disponível localmente. Compilar esse teste não equivale a executá-lo. Homologação Firebird/rede/credenciais do ambiente, custódia/distribuição e contratos de incremental/retificação/cursor continuam separados; nenhum comportamento contábil foi deduzido das fixtures.
