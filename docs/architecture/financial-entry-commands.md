# Comandos internos de lançamento financeiro

Referências: #39, #135 e #136. Serviço interno e exposição HTTP são incrementos separados. As flags de leitura e de escrita continuam desabilitadas por padrão.

## FATO CONFIRMADO — comportamento funcional

O handler salvarLanc do FINEMP_web_1 exige descrição, valor positivo, datas, competência, banco e subcategoria do tipo correspondente. Parceiro é opcional e deve ter papel cliente para receita, fornecedor para despesa, ou ambos. A exigência configurável de centro se aplica ao novo lançamento, não retroativamente à alteração. Centro novo/alterado deve ser analítico e ativo; centro histórico inativo mantido continua aceito. Não foi criado veto geral a banco/parceiro inativo: a referência possui caminhos que preservam essas seleções.

Ao alterar um lançamento que continua pago, o valor efetivamente pago anterior é preservado. Ao retirar a data de pagamento, esse valor é limpo. Criar já pago usa o nominal como efetivo, sem inventar ajuste de baixa.

## DECISÃO técnica — contrato canônico limitado

Os comandos internos aceitam somente valores positivos já representáveis em centavos exatos, normalizados para duas casas sem arredondamento. Não recebem valor efetivamente pago: a baixa terá comando próprio. Isso delimita a representação interna; não resolve a normalização de entrada bruta/subcentavo nem a política monetária de séries da #128, que permanece PONTO ABERTO.

MANAGE ALL explícito e atual é exigido antes e depois do bloqueio da Empresa. O bloqueio serializa escritores da Empresa, inclusive manutenção dos cadastros; transação READ COMMITTED e espera de bloqueio limitada a cinco segundos. Referências são verificadas por SQL escalar tenant-scoped, sem confiar em objetos antigos do contexto JPA. UPDATE exige versão esperada; a versão JPA protege também contra escritores concorrentes que não participem do bloqueio. Uma alteração sem diferença de estado pode manter a versão, mas ainda produz recibo e evento próprios.

A chave UUID é única por Empresa e ator, compartilhada entre operações: reutilizá-la para outra ação ou conteúdo é conflito. A impressão SHA-256 inclui versão do contrato, contexto, ação, alvo, versão esperada e todos os campos canônicos, com comprimentos prefixados para evitar ambiguidades. Reenvio exige permissão vigente e retorna o recibo imutável original, mesmo se o lançamento mudar depois; não consulta a representação atual nem reaplica regras de referência alteradas posteriormente.

Lançamento, evento com estados antes/depois e recibo são gravados na mesma transação. Qualquer falha reverte todos. O recibo contém apenas identificadores, ação e versão resultante; a consulta atual é separada. Evento/recibo não possuem FK para o lançamento, preservando histórico após futura exclusão. Os vínculos de Empresa e ator permanecem protegidos. Estados de auditoria são JSON de valores canônicos e não aparecem em logs, erros ou respostas; contêm informação financeira privada e exigirão política de consulta/retenção na #51. Não há alegação de trilha imutável contra administrador do banco.

## DECISÃO técnica — API individual opt-in

POST na coleção cria e responde201; PUT no UUID altera e responde200. Ambos recebem Idempotency-Key UUID e JSON com values; UPDATE exige expectedVersion, CREATE exige sua ausência ou null. Reenvio retorna o mesmo recibo e status, sem representar o estado atual. POST inclui Location para o GET do lançamento. Alteração sem diferenças pode manter versão, com recibo/evento próprios.

FINEMP_FINANCE_ENTRIES_ENABLED e FINEMP_FINANCE_ENTRY_COMMANDS_ENABLED devem estar explicitamente ativos. O catálogo versão2026-10-28.2 adiciona editAvailabilityCondition: condição adicional, somada à disponibilidade base. Esses metadados não são grants nem comprovam que todas as operações futuras da #39 já estejam prontas.

A sessão/CSRF e MANAGE ALL vigente são validados antes de ler o corpo; o serviço revalida após bloquear a Empresa. As rotas de escrita financeira recebem o filtro após AuthorizationFilter; Connector e outras rotas não mudam. Buffer limitado a64KiB, inclusive sem Content-Length ou com tamanho enganoso, até quatro requisições em processamento por instância. Não aceita compressão. Excesso retorna413; falta de capacidade429 com Retry-After1; encoding/tipo não suportado415. Capacidade é liberada mesmo em falha posterior. Esses limites são escolhas operacionais reversíveis.

JSON rejeita chaves duplicadas, campos desconhecidos, profundidade/token/tamanho excedido e dados não tipados. Nominal é texto decimal positivo com até17 dígitos inteiros e duas casas, sem expoente ou arredondamento. Ator/Empresa/valor efetivamente pago não podem ser injetados pelo corpo. Valores nulos opcionais têm semântica explícita, inclusive paymentDate null retira pagamento na alteração. Erros têm códigos/mensagens fixos, sem payload ou SQL. Baixa e estorno possuem contrato específico em [baixa e estorno](financial-entry-settlement.md). Exclusão, transferências e séries seguem incrementos próprios.

A seleção do filtro reutiliza a autorização de rota do Spring e uma marca interna da requisição; não repete comparação com o URI bruto. URLs codificadas e aliases de binding recebem os mesmos limites. A marca é definida apenas pelo servidor após permissão vigente, nunca por cabeçalho/corpo, e removida a cada reautorização. O gerenciador é construído pela própria configuração de segurança, inclusive nos testes isolados de MVC.

UPDATE também bloqueia a linha do lançamento e consulta sua versão atual por SQL antes de usar o estado JPA. Versão esperada, versão atual e versão do objeto devem coincidir; contexto antigo falha de forma segura. O bloqueio mantém essa garantia inclusive em alteração sem diferença de estado.

## PRÓXIMO PASSO / PONTO ABERTO

Exclusão individual possui [contrato próprio](financial-entry-deletion.md), e transferências possuem [persistência, comandos e API próprios](bank-transfers.md). DELETE não marca nem lê corpo; POST/PUT/baixa/estorno mantêm seus limites. Séries/lotes continuam na #39. Valores brutos e efeitos binários legados da #128 não são decididos neste bloco. Consulta/exportação de auditoria e anexos pertencem à #51.
