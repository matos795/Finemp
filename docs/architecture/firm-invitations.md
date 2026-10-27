# Convites por contabilidade

## Operação

`POST /api/admin/accounting-firms/{firmId}/invitations`, habilitado junto ao transporte de e-mail. Requer sessão, CSRF e `USER_ACCESS_MANAGE` explicitamente concedida naquela contabilidade. Um administrador de outra contabilidade ou apenas da hierarquia não pode convidar nesse escopo.

Corpo: `name`, `email` e `permissions`, conjunto explícito de permissões administrativas daquela contabilidade. Conjunto vazio cria apenas o vínculo administrativo, sem conceder operações. Resposta 204 vazia; nunca devolve token, senha ou confirmação de existência global do e-mail. O novo usuário define a própria senha pelo link de ativação.

## DECISÕES técnicas de segurança

- Convite não concede grant de empresa, role financeiro nem acesso à DRE.
- Identidade global existente conserva nome, senha e estado. Conta ativa não recebe novo link de ativação; conta bloqueada não é reativada por convite.
- Reconvite adiciona somente as permissões explicitamente solicitadas, sem substituir/revogar permissões anteriores. Revogação terá operação administrativa explícita.
- A criação da identidade e seu vínculo/permissões pertencem à mesma transação; e-mail é enviado somente após commit.
- O lock da identidade serializa reconvites e a criação do vínculo, além do índice único existente por usuário/contabilidade.
- Limite de convites é separado do limite público de conclusão e aplicado por administrador autenticado (`finemp.identity.rate-limit.invitation-actor`, padrão 30 por janela).

Essas escolhas preservam acessos existentes e evitam concessões implícitas. Não interpretam escopo de contabilidade como autorização financeira. Política de revogação/gestão completa e auditoria abrangente seguem nas Issues #37/#51.

## Verificação

PostgreSQL real: escopo permitido, contabilidade externa, permissão insuficiente, preservação de identidade ativa/permissões e ausência de grant financeiro. HTTP: 401, 403, CSRF e resposta sem segredo. Limites por administrador possuem teste próprio.
