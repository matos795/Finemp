# Comandos internos de lançamento financeiro

Referências: #39 e #135. Fase interna: não há controller de escrita neste bloco. A flag FINANCE_ENTRIES_ENABLED continua desabilitada por padrão.

## FATO CONFIRMADO — comportamento funcional

O handler salvarLanc do FINEMP_web_1 exige descrição, valor positivo, datas, competência, banco e subcategoria do tipo correspondente. Parceiro é opcional e deve ter papel cliente para receita, fornecedor para despesa, ou ambos. A exigência configurável de centro se aplica ao novo lançamento, não retroativamente à alteração. Centro novo/alterado deve ser analítico e ativo; centro histórico inativo mantido continua aceito. Não foi criado veto geral a banco/parceiro inativo: a referência possui caminhos que preservam essas seleções.

Ao alterar um lançamento que continua pago, o valor efetivamente pago anterior é preservado. Ao retirar a data de pagamento, esse valor é limpo. Criar já pago usa o nominal como efetivo, sem inventar ajuste de baixa.

## DECISÃO técnica — contrato canônico limitado

Os comandos internos aceitam somente valores positivos já representáveis em centavos exatos, normalizados para duas casas sem arredondamento. Não recebem valor efetivamente pago: a baixa terá comando próprio. Isso delimita a representação interna; não resolve a normalização de entrada bruta/subcentavo nem a política monetária de séries da #128, que permanece PONTO ABERTO.

MANAGE ALL explícito e atual é exigido antes e depois do bloqueio da Empresa. O bloqueio serializa escritores da Empresa, inclusive manutenção dos cadastros; transação READ COMMITTED e espera de bloqueio limitada a cinco segundos. Referências são verificadas por SQL escalar tenant-scoped, sem confiar em objetos antigos do contexto JPA. UPDATE exige versão esperada; a versão JPA protege também contra escritores concorrentes que não participem do bloqueio. Uma alteração sem diferença de estado pode manter a versão, mas ainda produz recibo e evento próprios.

A chave UUID é única por Empresa e ator, compartilhada entre operações: reutilizá-la para outra ação ou conteúdo é conflito. A impressão SHA-256 inclui versão do contrato, contexto, ação, alvo, versão esperada e todos os campos canônicos, com comprimentos prefixados para evitar ambiguidades. Reenvio exige permissão vigente e retorna o recibo imutável original, mesmo se o lançamento mudar depois; não consulta a representação atual nem reaplica regras de referência alteradas posteriormente.

Lançamento, evento com estados antes/depois e recibo são gravados na mesma transação. Qualquer falha reverte todos. O recibo contém apenas identificadores, ação e versão resultante; a consulta atual é separada. Evento/recibo não possuem FK para o lançamento, preservando histórico após futura exclusão. Os vínculos de Empresa e ator permanecem protegidos. Estados de auditoria são JSON de valores canônicos e não aparecem em logs, erros ou respostas; contêm informação financeira privada e exigirão política de consulta/retenção na #51. Não há alegação de trilha imutável contra administrador do banco.

## PRÓXIMO PASSO / PONTO ABERTO

UPDATE também bloqueia a linha do lançamento e consulta sua versão atual por SQL antes de usar o estado JPA. Versão esperada, versão atual e versão do objeto devem coincidir; contexto antigo falha de forma segura. O bloqueio mantém essa garantia inclusive em alteração sem diferença de estado.

Exposição HTTP exige contrato, CSRF, autorização, controle de corpo e mapeamento seguro de erros próprios. Baixa/estorno, exclusão, transferências e séries continuam na #39. Valores brutos e efeitos binários legados da #128 não são decididos neste bloco. Consulta/exportação de auditoria e anexos pertencem à #51.
