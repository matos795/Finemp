# FINEMP Connector — credenciais e rota — Issue #96

## FATO IMPLEMENTADO

Com `FINEMP_CONNECTOR_ENABLED=true`, as rotas administrativas em `/api/admin/accounting-firms/{firmId}/connectors` permitem emitir/listar, `/{credentialId}/rotate` permite rotacionar e DELETE revoga idempotentemente. Sessão e CSRF continuam obrigatórios para alterações. A flag é desligada por padrão.

Cada credencial tem vínculo imutável com uma contabilidade e uma fonte. A cadeia atual Contabilidade → Cliente → Empresa → AccountingSource precisa corresponder no servidor. Emitir/rotacionar exige CONNECTOR_MANAGE explícito na contabilidade e QUESTOR_SYNC com escopo ALL na empresa; administração global ou papel nominal não concede acesso financeiro. Revogar/listar exige CONNECTOR_MANAGE, permitindo recolher uma credencial mesmo após perder acesso financeiro.

Segredo tem 256 bits de aleatoriedade, codificação opaca URL-safe e apenas SHA-256 persistido/indexado. Resposta de emissão/rotação apresenta o segredo uma vez com `Cache-Control: no-store` e `Pragma: no-cache`. Listas, DTOs de metadados, diagnóstico e eventos não contêm segredo/hash. Não há recuperação do segredo perdido: um administrador autorizado deve rotacionar. Não se envia senha Firebird/ERP ao FINEMP.

Prazo padrão 90 dias, configurável entre 1–365; instante exato de expiração já é inválido. Rotação substitui hash/emissor e incrementa versão; snapshot autenticado anterior perde validade. Revogação é terminal e não permite rotação. Eventos ISSUED/ROTATED/REVOKED registram apenas IDs/ação/UTC e confirmam na mesma transação. Não há auditoria com erro livre ou material contábil.

Verificador interno consulta valores atuais diretamente e revalida validade, emissor ativo, CONNECTOR_MANAGE, QUESTOR_SYNC e ownership/estados. Reduzir acesso do emissor impede uso enquanto a autoridade estiver ausente; isso é distinto da revogação terminal da credencial. Escritas futuras do protocolo devem manter a trava da fonte e revalidar a versão da credencial antes de publicar, serializando com rotação/revogação.

## DECISÃO TÉCNICA

Um vínculo não revogado por fonte, mesmo se expirado; rotacionar ou revogar explicitamente permite recuperar o provisionamento. São escopos mínimos: uma instalação local poderá trabalhar com múltiplas fontes usando vínculos distintos, sem chave mestra de tenant. Não há concessão implícita para todas as empresas.

`AccountingSource.connectionMode` diferencia DIRECT e CONNECTOR. Fonte existente começa DIRECT. Emitir credencial reserva CONNECTOR e recusa runs REQUESTED/RUNNING para não trocar o destino durante uma leitura. Revogar não devolve silenciosamente a fonte a DIRECT. Worker/lifecycle/publicação diretos não consomem fontes CONNECTOR; o importador legado fica indisponível quando o Connector é habilitado. Nenhum fallback pode consultar outro banco em nome da fonte.

Idempotência de requisição não repete segredo: perda da resposta pode exigir rotação. O vínculo ativo e o bloqueio da fonte impedem emissão duplicada concorrente. V22 cria tabelas/índices e a rota explícita; nenhuma migração operacional foi executada.

## PONTO ABERTO / PRÓXIMO PASSO

Este incremento é provisionamento e verificação interna. Ainda não publica endpoints de máquina, agente local ou canal operacional homologado. O próximo incremento será protocolo pull/heartbeat/upload HTTPS com autenticação de máquina isolada da sessão do browser, revalidação na publicação e o mesmo lote atômico.

Provar CNPJ/identidade no ERP continua obrigatório: vínculo/segredo não prova sozinho que uma referência digitada seleciona a empresa correta no Firebird. O adapter local deve ler identidade real e conferir a expectativa; não poderá simplesmente ecoar o CNPJ recebido. Até isso ser implementado/verificado, não declarar integração operacional pronta. Questor permanece somente leitura e o FINEMP é a camada gerencial.
