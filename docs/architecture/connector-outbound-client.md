# Cliente outbound do FINEMP Connector — Issue #110

## FATO IMPLEMENTADO

Cliente Java sem listener HTTP, sessão de navegador, JPA ou inicialização automática. Consome session, claim, heartbeat, complete, fail e result. A fonte local é um identificador configurado: a sessão é conferida antes do claim, contexto recebido precisa pertencer à fonte e recibos precisam corresponder a tentativa, execução e número da tentativa.

Só aceita origem HTTPS (host e porta), sem usuário/senha/query/fragmento ou caminho arbitrário. O prefixo /api/connector/v1 é fixo. Cliente de produção usa confiança TLS padrão da JVM, verificação HTTPS do nome e redirects NEVER; nenhum trust-all. Credencial opaca vem de supplier local a cada pedido, com diagnóstico constante e sem serialização/log. Configuração de proxy/JVM/trust-store continua responsabilidade operacional.

Deadline total inclui recebimento do corpo, além do timeout HTTP e de conexão. Corpo recebido usa subscriber limitado a 64 KiB antes de copiar buffers; tamanho declarado, compressão e redirects são recusados. Timeout/interrupção cancela exchange e subscription; interrupção é preservada. Resposta privada/headers/URL/exceções de rede não entram no erro lançado.

Lote usa BigDecimal exato e datas ISO, sem double/rounding e sem regras para 0/N/A/Verificar. Validação canônica limita registros/campos/precisão antes da escrita; geração JSON é limitada a 8 MiB. Duplicatas consistentes são preservadas para que o servidor mantenha os contadores de leitura. Metadados têm limite de tokens/profundidade/tamanho, detecção de duplicatas, enum conhecido e rejeição de trailing tokens.

Complete/fail nunca repetem POST automaticamente. Timeout/erro de transporte, 409 ou 5xx levam a GET result antes de qualquer decisão posterior. Recibo RUNNING continua pendente: não é confirmação de publicação. Somente SUCCEEDED da mesma tentativa, com contadores coerentes, é sucesso. 202/422 retornam falha/retry explícitos; 401 e 429/Retry-After permanecem categorias distintas. Retry-After numérico é limitado a 300s; data HTTP ou valor inválido não é interpretado como autorização para retry imediato.

## DECISÃO TÉCNICA

Work/Lease passam a incluir serverTime, usando o mesmo Clock do lifecycle. O agente poderá calcular duração relativa do lease e agendar via tempo monotônico, sem assumir sincronização do relógio local. A latência ainda deve ser descontada no loop do agente; o cliente não promete renovar lease sozinho.

Transport deadline configurável entre 1ms e 120s; conexão até min(deadline,10s). Valores são limites técnicos, não regra de negócio. O cliente não mantém fila, não instala serviço e não resolve sozinho custódia/reinício. Loop, heartbeat concorrente, armazenamento local de estado/segredos e distribuição são próximos incrementos da #50.

## EVIDÊNCIA / PONTO ABERTO

Testes sintéticos cobrem precisão, vínculo de origem, recibo perdido/incorreto, erros, bytes e cancelamento. Teste HTTPS loopback usa certificado efêmero criado no teste e prova trust TLS, ausência de redirect e prazo corporal após headers. Não instala certificado no computador nem usa credencial/dado real. Isso não substitui homologação de rede/Firebird/ambiente operacional. Questor permanece somente leitura; FINEMP mantém publicação transacional própria.
