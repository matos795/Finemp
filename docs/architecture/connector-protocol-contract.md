# Contrato composto outbound — Issue #116

## ESCOPO DA VALIDAÇÃO

O contrato integrado conecta FinempConnectorClient e ConnectorAgentCycle aos controllers/filtros reais do protocolo, transações e PostgreSQL de Testcontainers. A ponte de teste usa MockMvc marcado como seguro e transporta JSON dos DTOs efetivos; não substitui a prova de TLS real nem acessa Firebird. TLS, certificados e redirects têm teste loopback separado; identidade/JDBC do factory tem contrato sintético separado.

Cenários: resposta perdida depois do commit é resolvida por receipt sem segundo POST ou movimento duplicado; replay manual é recusado; token de outra AccountingSource é recusado antes do claim/I/O; cancelamento antes do claim impede leitura; recuperação de lease com novo fence impede a publicação do lote da tentativa antiga; linhas fora do período não são enviadas. Fixture e limpeza são restritas aos IDs sintéticos criados pelo teste. Não há credenciais/dados reais nem banco operacional.

## DECISÃO TÉCNICA

Credenciais novas usam versão técnica zero no contrato persistido e na sessão emitida pelo servidor. O decoder aceita inteiro não negativo obrigatório, preservando a conferência de origem. Valores ausentes, negativos, fracionários e fora de int64 são recusados. Isso não altera o número da tentativa, que continua começando em um, nem a revalidação de credencial/fencing no servidor.

Como proteção defensiva, recibo com execução CANCELLED é terminal sem ACK, inclusive se a tentativa informada ainda aparece RUNNING. O ciclo encerra sem lote repetido ou POST de falha. Isso não declara esse estado alcançável pela API atual: o controle só cancela REQUESTED, e o contrato integrado preserva essa restrição. SUCCEEDED continua exigindo mesma tentativa/run/número e contadores coerentes. A proteção defensiva tem teste RED→GREEN do ciclo; nenhuma permissão de cancelamento de RUNNING foi acrescentada.

## LIMITES

PostgreSQL/HTTP integrado é executado na CI quando Docker não está disponível localmente. Compilar esse teste não equivale a executá-lo. Homologação Firebird/rede/credenciais do ambiente, custódia/distribuição e contratos de incremental/retificação/cursor continuam separados; nenhum comportamento contábil foi deduzido das fixtures.
