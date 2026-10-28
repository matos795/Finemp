# Baixa e estorno — Issues139 e141 /39

## FATO CONFIRMADO

confirmarBaixa833–839 da referência homologada exige data e valor positivo, altera a conta e a data de pagamento e conserva o nominal. Guarda valor efetivamente pago quando a diferença supera0,004; quando não supera, remove esse override. estornarBaixa840 remove data e valor efetivamente pago, mantendo a conta atual. O handler não impede reaplicar uma baixa ou estornar registro já aberto; não foi introduzida proibição adicional.

## DECISÃO técnica — domínio canônico limitado

EntrySettlementData recebe data ISO entre anos1 e9999, valor positivo já exato em centavos e conta. Não recebe valor bruto para arredondar. Nesse domínio decimal, valor diferente do nominal implica diferença mínima de um centavo: mantém override; valor igual o remove. A política de captura bruta, os efeitos binários legados e as séries da #128 continuam abertos. Não se afirma equivalência bit a bit com IEEE754 para entradas fora desse contrato.

Baixa altera somente pagamento, valor pago e conta. Estorno limpa pagamento/valor pago e conserva a última conta; nominal, descrição, demais datas, competência e classificadores permanecem. Nominal zero já existente, permitido para parcelas geradas, não é reescrito. Essas operações registram estado financeiro interno; não movimentam dinheiro por integração bancária nem gravam no Questor.

O serviço exige FINANCIAL_ENTRY_MANAGE ALL explícito e vigente antes e depois do bloqueio da Empresa. READ COMMITTED, limite de espera e bloqueio/versão atual do lançamento usam as mesmas proteções dos comandos individuais. Versão esperada, versão do banco e versão do objeto JPA devem concordar, inclusive quando não há diferença de estado. Estorno de registro já aberto ainda gera recibo/evento, podendo conservar a versão quando não houver alteração persistida.

A conta selecionada deve existir na Empresa. Não foi criado veto universal a conta inativa, nem revalidação da categoria, papel do parceiro ou centro histórico. A exigência configurável de centro para novos lançamentos não se aplica a baixa/estorno. O lançamento e suas referências existentes continuam protegidos pelas FKs.

## DECISÃO técnica — integridade e rastreabilidade

Lançamento, evento antes/depois e recibo são atômicos. Migrations anteriores não são reescritas: V31 amplia as ações permitidas para SETTLED e SETTLEMENT_REVERSED. Impressões CREATE/UPDATE V1 e recibos já gravados não mudam. Baixa/estorno possuem formato de hash próprio versionado, com comprimentos prefixados e contexto, ação, alvo, versão esperada e entrada canônica.

Chave UUID continua compartilhada entre ações por Empresa/ator; outro conteúdo ou ação com a mesma chave conflita. Reenvio autorizado retorna o recibo original antes de consultar o estado atual, inclusive após estorno posterior, sem reaplicar pagamento. Permissão revogada impede reenvio. Histórico/recibos não possuem FK do lançamento; os endpoints retornam somente recibo mínimo. Controles de consulta/retenção do histórico pertencem à #51.

## DECISÃO técnica — exposição HTTP

POST /api/companies/{companyId}/financial-entries/{entryId}/settlements recebe values(paymentDate, paidAmount textual positivo canônico, bankAccountId) e expectedVersion. POST /settlement-reversals no mesmo item recebe somente expectedVersion. Ambos retornam200 com recibo imutável; estado atual permanece no GET do lançamento. O recibo público admite SETTLED e SETTLEMENT_REVERSED.

São exigidos os dois opt-ins atuais de escrita, MANAGE ALL vigente, sessão/CSRF e Idempotency-Key UUID. As novas rotas usam o autorizador atual e sua marca interna; o orçamento de corpo64KiB e concorrência é compartilhado, inclusive para URLs codificadas. Campos inesperados/duplicados, tipos incorretos, ator/Empresa, nominal e classificadores são rejeitados no protocolo de baixa. Estorno não aceita values. Erros usam mensagens fixas, sem payload ou diagnóstico SQL.

## VALIDAÇÃO

Seis testes de transição RED→GREEN e oito de serviço, além de três de fingerprint, passaram localmente. Onze contratos PostgreSQL do PR140 passaram na CI: estados/versões/auditoria, igualdade/reaplicação, metadados históricos, reenvio/revogação, colisão entre ações, isolamento, falha entre evento e recibo, concorrência e contexto antigo/isolamento incompatível. O contrato HTTP acrescenta cinco testes JSON RED→GREEN, compatibilidade dos seis testes JSON anteriores e sete cenários PostgreSQL/HTTP compilados para CI. Docker local indisponível: execução PostgreSQL exige CI antes do merge.

## PRÓXIMO PASSO / PONTO ABERTO

Anexos seguem #51; captura bruta/séries monetárias seguem #128. Transferências, exclusão e demais operações continuam na #39; este bloco não encerra a Issue principal.
