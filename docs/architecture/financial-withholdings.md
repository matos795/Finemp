# Retenções informadas — #159 / #173

## DECISÃO homologada

Registrar retenções vinculadas ao lançamento/documento e distinguir valores a recolher de valores a compensar. O backend e o PostgreSQL são a fonte de verdade. O frontend não define regras tributárias, cálculos ou persistência.

## Fundação de persistência

FinancialWithholding pertence à Company e aponta para um FinancialEntry persistido da mesma Company. WithholdingValues registra identificador informado do tributo, tratamento explícito PAYABLE (a recolher) ou RECOVERABLE (a compensar), valor canônico exato e referência documental informativa opcional. Não infere tratamento pela direção do lançamento. O texto documental não é uma nova entidade fiscal, emissão de nota ou chave única validada de documento eletrônico.

O estado monetário usa centavos exatos não negativos, até NUMERIC(19,2), sem arredondar entradas com fração residual de centavo. Zero é representável no estado, assim como nos outros valores financeiros canônicos; isso não define um motor de sugestão ou de cálculo tributário. Taxas, base de cálculo e bruto/líquido não são calculados. Não se presume limite pela soma/nominal: essa regra não foi homologada. Múltiplas retenções com mesmo identificador/tratamento não são proibidas por unicidade arbitrária. Reexecuções operacionais serão protegidas por chave idempotente nos comandos seguintes.

V40 contém FK composta Company/entry, referências aos responsáveis, timestamps e versão. A FK restringe exclusão do lançamento enquanto houver retenção; não há cascade que apague fatos silenciosamente. A proteção permanece mesmo quando a captura financeira estiver desabilitada. Atualizar retenção não muda nominal, valor pago, datas, classificações ou saldos do lançamento.

## PONTO ABERTO / próximo incremento

Esta fundação não expõe endpoints nem comandos operacionais. A #159 permanece aberta para escrita com autorização vigente, versões, recibos, auditoria oficial e verificação dos períodos afetados, além do contrato de leitura/API. O domínio não aplica o mock percentual de NFS-e do HANDOFF (p_ctr.js/calcNF/nfCalc) nem suas fórmulas de líquido. Validação legal, calendário fiscal, vencimentos, títulos derivados e contabilização/sugestão automática seguem separados e abertos. Não há acesso ou escrita no Questor.

## Comandos internos — #175

CREATE/UPDATE/DELETE usam FINANCIAL_ENTRY_MANAGE ALL vigente antes e depois da trava Company, READ COMMITTED e timeout de trava5s. Cada comando informa versão atual do lançamento; atualização/exclusão também informam versão da retenção. SQL atual sob trava evita confiar em entidades antigas no contexto JPA. Vínculo ao lançamento é imutável. Não há alteração de valores ou versão do lançamento pelo comando de retenção.

A competência atual do lançamento é conferida no guard oficial de fechamento. Retenção informada não movimenta caixa nem altera pagamento; não se cria efeito no mês de caixa por suposição. Recibo usa Company/ator/UUID e fingerprint do conteúdo/ação/precondições. Replay autorizado antecede revalidação do recurso, preservando a resposta inclusive após exclusão da retenção/lançamento, sem recriar dados.

V41 guarda somente recibos operacionais. Os eventos CREATED/UPDATED/DELETED ampliam AccessAuditRecorder/Event oficial com recurso e estado anterior/novo. Exclusão mantém auditoria com afterState nulo; recibos não têm FK ao recurso vivo. Estado, auditoria e recibo são atômicos. FINEMP_FINANCE_WITHHOLDINGS_ENABLED é opt-in desabilitado por padrão; ainda não há contrato HTTP neste incremento.

Nove contratos PostgreSQL cobrem transições exatas sem efeito no pai, versões atuais, Company/permissões/revogação, fechamento/replay, remoção do recurso, identidade do comando, rollback de cada ação no recibo e concorrência. A API seguirá o mesmo contrato, com JSON estrito, sessão/CSRF e limites compartilhados.

## API — #177

/api/companies/{companyId}/financial-entries/{entryId}/withholdings oferece POST de criação, GET paginado com treatment e GET/PUT no recurso. POST /{id}/deletions recebe entryVersion/expectedVersion; não depende de corpo de DELETE. POST/PUT recebem JSON estrito, entryVersion e values; PUT também exige expectedVersion. Amount é texto decimal exato. A criação retorna201/Location; demais comandos retornam200 com recibo. Não há mudança de vínculo ao pai.

Leitura exige FINANCIAL_ENTRY_VIEW ou MANAGE ALL. Escrita reutiliza exatamente o gate da família financeira, FINANCIAL_ENTRY_MANAGE ALL, sessão/CSRF e Idempotency-Key UUID, inclusive rotas codificadas. Disponibilidade exige FINEMP_FINANCE_WITHHOLDINGS_ENABLED; mutações também exigem FINEMP_FINANCE_ENTRIES_ENABLED e FINEMP_FINANCE_ENTRY_COMMANDS_ENABLED. Não há outro orçamento/marker que possa contornar os limites ou a autorização existentes.

GET /{id}/history pagina o diário oficial pelo recurso, Company e entryId do snapshot. Não depende da existência atual do pai/filho; mantém visibilidade autorizada do histórico após exclusão. Não mistura eventos de outro recurso nem de outra Empresa. Consultas têm timeout5s e paginação1–100 no servidor.409 distingue versões obsoletas, conteúdo de chave divergente e competência fechada; as mensagens não contêm SQL ou payload privado.

Dois testes locais do parser e cinco contratos PostgreSQL/HTTP verificam representação/alteração/consulta, histórico e replay após remoção do pai, autorização antes do corpo na rota normalizada, tenant, competência e precondições estritas. As regras transacionais e de concorrência já são verificadas na #175.

## Validação da persistência

Três testes locais cobrem dinheiro exato/limites, tratamento declarado e entradas textuais inválidas. Seis contratos PostgreSQL verificam persistência exata sem efeito no lançamento, FK de outra Company, consultas paginadas isoladas, versão concorrente, proteção da exclusão e checks monetários/tipo/código. Dados exclusivamente sintéticos; compilação local não substitui execução PostgreSQL na CI.
