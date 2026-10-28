# Baixa e estorno internos — Issue139 /39

## FATO CONFIRMADO

confirmarBaixa833–839 da referência homologada exige data e valor positivo, altera a conta e a data de pagamento e conserva o nominal. Guarda valor efetivamente pago quando a diferença supera0,004; quando não supera, remove esse override. estornarBaixa840 remove data e valor efetivamente pago, mantendo a conta atual. O handler não impede reaplicar uma baixa ou estornar registro já aberto; não foi introduzida proibição adicional.

## DECISÃO técnica — domínio canônico limitado

EntrySettlementData recebe data ISO entre anos1 e9999, valor positivo já exato em centavos e conta. Não recebe valor bruto para arredondar. Nesse domínio decimal, valor diferente do nominal implica diferença mínima de um centavo: mantém override; valor igual o remove. A política de captura bruta, os efeitos binários legados e as séries da #128 continuam abertos. Não se afirma equivalência bit a bit com IEEE754 para entradas fora desse contrato.

Baixa altera somente pagamento, valor pago e conta. Estorno limpa pagamento/valor pago e conserva a última conta; nominal, descrição, demais datas, competência e classificadores permanecem. Nominal zero já existente, permitido para parcelas geradas, não é reescrito. Essas operações registram estado financeiro interno; não movimentam dinheiro por integração bancária nem gravam no Questor.

O serviço exige FINANCIAL_ENTRY_MANAGE ALL explícito e vigente antes e depois do bloqueio da Empresa. READ COMMITTED, limite de espera e bloqueio/versão atual do lançamento usam as mesmas proteções dos comandos individuais. Versão esperada, versão do banco e versão do objeto JPA devem concordar, inclusive quando não há diferença de estado. Estorno de registro já aberto ainda gera recibo/evento, podendo conservar a versão quando não houver alteração persistida.

A conta selecionada deve existir na Empresa. Não foi criado veto universal a conta inativa, nem revalidação da categoria, papel do parceiro ou centro histórico. A exigência configurável de centro para novos lançamentos não se aplica a baixa/estorno. O lançamento e suas referências existentes continuam protegidos pelas FKs.

## DECISÃO técnica — integridade e rastreabilidade

Lançamento, evento antes/depois e recibo são atômicos. Migrations anteriores não são reescritas: V31 amplia as ações permitidas para SETTLED e SETTLEMENT_REVERSED. Impressões CREATE/UPDATE V1 e recibos já gravados não mudam. Baixa/estorno possuem formato de hash próprio versionado, com comprimentos prefixados e contexto, ação, alvo, versão esperada e entrada canônica.

Chave UUID continua compartilhada entre ações por Empresa/ator; outro conteúdo ou ação com a mesma chave conflita. Reenvio autorizado retorna o recibo original antes de consultar o estado atual, inclusive após estorno posterior, sem reaplicar pagamento. Permissão revogada impede reenvio. Histórico/recibos não possuem FK do lançamento e não são expostos por novos endpoints neste bloco; controles de consulta/retenção pertencem à #51.

## VALIDAÇÃO

Seis testes de transição RED→GREEN e oito de serviço, além de três de fingerprint, passaram localmente. Os contratos PostgreSQL cobrem estados/versões/auditoria, igualdade/recálculo da baixa, metadados históricos alterados, reenvio/revogação, colisão entre ações, isolamento/permissões, falha entre evento e recibo, concorrência e contexto JPA antigo/isolamento incompatível. Docker local indisponível: execução PostgreSQL exige CI antes do merge.

## PRÓXIMO PASSO / PONTO ABERTO

Exposição HTTP terá incremento próprio, aproveitando autorização e limites atuais. Anexos seguem #51; captura bruta/séries monetárias seguem #128. Transferências, exclusão e demais operações continuam na #39; este bloco não encerra a Issue principal.
