# Claim e heartbeat do Connector — Issue #100

## FATO IMPLEMENTADO

`POST /api/connector/v1/work/claim` procura trabalho elegível somente na fonte da credencial autenticada. O agente não escolhe empresa, fonte, período ou SQL. O servidor retorna attemptId, runId, número, modo, prazo e contexto canônico construído a partir de dados persistidos. Sem trabalho elegível retorna 204 no-store; esse caso inclui lease vigente/backoff e rejeições registradas pelo lifecycle.

`POST /api/connector/v1/work/{attemptId}/heartbeat` reconstrói o claim a partir da tentativa/fonte/run no servidor. O agente não fornece fence, duração ou contadores. Exige mesma credencial e versão, lease vigente, contexto/fonte atuais e acesso do solicitante humano. Credencial autenticada não concede acesso a trabalhos de outra fonte, mesmo dentro da mesma empresa.

V23 registra credencial e versão na tentativa. Tentativas DIRECT existentes mantêm ambos nulos. Claims novos usam o mesmo lifecycle de orçamento, backoff, expiração e fence. Renovação altera somente tentativa; origem/fence não são alterados. Eventos gerais continuam emitidos pelo coordenador SYSTEM; a tentativa conserva a identidade de máquina sem guardar o segredo.

Trava da fonte precede revalidação da credencial no claim/heartbeat. Rotação/revogação usa a mesma ordem, serializando a mudança de autoridade. Além da autoridade do emissor da credencial, o solicitante humano do run continua sendo validado. Remover seu QUESTOR_SYNC faz o lifecycle rejeitar a execução, mesmo com uma credencial de outro emissor ainda válida.

O servidor SaaS pode operar este protocolo sem reader Firebird nem executor direto: o lifecycle existe quando protocolo ou executor estão habilitados. `SyncAgentAuthorization` é a porta para prova atual da máquina; ausência/ambiguidade não concede acesso. O executor direto e a publicação direta recusam claims de Connector. Não há leitura ERP neste incremento.

## DECISÃO TÉCNICA

Tentativa em andamento não é entregue duas vezes ao mesmo bearer. Se a resposta de claim se perder, o próximo trabalho só será obtido após lease e backoff, com novo fence. Isso evita compartilhar uma tentativa entre dois processos que copiaram uma credencial. Retomada de INITIAL relê a janela inteira e depende da publicação idempotente já implementada; não há cursor parcial inventado.

Rotação invalida o snapshot anterior imediatamente. Novo segredo não renova a tentativa criada com versão anterior; aguarda expiração/backoff para novo claim. Revogar não faz fallback DIRECT. Prazos e orçamento continuam os limites técnicos configurados em #92, não regras contábeis.

## PONTO ABERTO / PRÓXIMO PASSO

Envio do lote, limite de bytes, verificação da identidade observada e publicação atômica pelo protocolo serão incrementos seguintes. O CNPJ esperado retornado no contexto é uma expectativa; o adapter local precisa obter identidade real do ERP e compará-la. Não se aceita um simples eco como prova operacional.

Agente local, instalação/armazenamento de segredo, trust TLS/proxy e homologação operacional continuam gates separados. Nenhuma migração foi executada em banco operacional e nenhum dado real é usado nos testes.
