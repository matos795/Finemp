# Ciclo outbound do Connector — Issue #111

## DECISÃO TÉCNICA

Um `ConnectorAgentCycle` pertence a um perfil local confiável e a um cliente já vinculado à AccountingSource. É opt-in: não cria servidor HTTP, não inicia via Spring e não instala serviço. O ciclo usa o contexto recebido para claim → snapshot → complete/recibo; não aceita JDBC, SQL ou segredos no trabalho. Para Questor, a composição operacional deve usar o snapshot verificado da #108, com identidade efetivamente observada e consulta somente leitura. Nenhum CNPJ esperado pode servir como prova.

Questor continua o sistema contábil, somente leitura para o FINEMP. Fencing, revalidação de credencial/autoridade e publicação atômica continuam no servidor; o agente não substitui esses controles. Somente INITIAL é executado. Não há cursor, retificação, classificação ou regra contábil nova.

## GATES DO CICLO

Claim/heartbeat incluem serverTime. A duração restante é convertida para tempo monotônico, usando o instante local anterior ao pedido: a latência inteira é conservadoramente consumida do lease. O relógio de calendário local pode divergir. Heartbeat tem uma chamada por vez, agendada a um terço do orçamento restante; falha/identidade de tentativa divergente invalida o lease e cancela a leitura. Antes da publicação o lease precisa continuar vivo; o servidor volta a conferir a tentativa no commit.

Há um worker de leitura sem fila e um worker de heartbeat por instância. `runOnce` não se sobrepõe; a leitura permanece ocupada até o runnable terminar de fato, independentemente de Future.cancel. Driver que ignora interrupção deixa o perfil em BUSY: não se cria nova thread para contorná-lo. Shutdown é cancelamento cooperativo, não promessa de matar chamada nativa. Reiniciar instâncias para contornar BUSY não é política de recuperação; registro de perfis/distribuição deve preservar exclusividade local.

Orçamento de leitura configurável entre 1ms e 10min, verificado também após o retorno. Estouro de orçamento/linhas reporta RESOURCE_LIMIT, divergência de identidade reporta IDENTITY_MISMATCH, falha de leitura reporta READ_FAILED. Diagnósticos do driver são descartados. Mapeamento local não comprovado e tipo/modo incompatível interrompem o perfil sem disfarçar configuração como falha transitória.

Erro depois do início de complete/fail mantém a tentativa pendente. Não há POST de falha conflitante nem replay automático. Próximo ciclo consulta o recibo antes de outro claim. RUNNING nunca é ACK; SUCCEEDED exige a mesma tentativa/run/número e contadores coerentes. Execução explicitamente CANCELLED é terminal sem ACK, mesmo se a tentativa ainda aparece RUNNING: não reenvia lote nem cria POST de falha. Tentativa expirada/substituída só libera novo claim pela autoridade do servidor. Heartbeat para enquanto se resolve uma publicação incerta; o lease pode expirar naturalmente.

Credencial indisponível interrompe o perfil. 429 respeita Retry-After numérico limitado a 300s; demais falhas de comunicação aguardam 5s. Isso inclui o heartbeat: falhas assíncronas são preservadas e aplicadas antes de permitir novo claim, sem encurtar espera já exigida por outra chamada. Cancelamento intencional do heartbeat no fechamento não cria falha de autenticação. O componente retorna estado e não faz polling infinito. Chamador deve respeitar a espera e não registrar objetos de credencial/payload. O fechamento cancela I/O/heartbeat e impede novo trabalho.

## ESTADO E LIMITES

Pendência local guarda apenas contexto/identificadores e prazo, sem lote ou segredo em disco. Reinício perde esse contexto: o servidor conserva tentativas, leases, idempotência e fencing; trabalho sem confirmação expira antes de nova leitura. Não se afirma retomada de bytes/chunks nem armazenamento durável local. Artefato executável, configuração/custódia de segredos, registro multi-source e homologação Firebird/rede são incrementos seguintes da #50.

Testes usam fontes, credenciais e recibos sintéticos. Cobrem cancelamento, fonte incompatível, identidade divergente, falha de leitura/configuração, orçamento, leitura que ignora interrupt, resposta perdida, heartbeat renovado/perdido, RTT, rate limit e shutdown. Não equivalem à homologação operacional nem executam escrita no Questor.
