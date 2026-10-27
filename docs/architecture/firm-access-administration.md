# Administração de acesso por contabilidade — #74 / #37

## FATO CONFIRMADO — contrato implementado

`/api/admin/accounting-firms/{firmId}/users` oferece GET paginado (page 0,
size 20, máximo 100, busca literal por nome/e-mail). `/{userId}` oferece GET
de detalhe, PUT `{ "permissions": [...] }` e DELETE idempotente (204).
Detalhes incluem somente resumo da identidade e permissões administrativas
da contabilidade; nunca hash, token ou configuração de conexão.

Exige `PLATFORM_USER_MANAGE` global ou `USER_ACCESS_MANAGE` explícita nessa
contabilidade. Usuário inativo não autoriza operações administrativas.
Administrador da contabilidade só altera membros existentes; a associação de
uma identidade nova usa o convite já implementado. O administrador global pode
associar explicitamente uma identidade existente. Nenhuma operação concede
acesso financeiro implicitamente. Mutações exigem sessão e CSRF.

## DECISÃO técnica — operações explícitas e reversíveis

- PUT substitui somente o conjunto administrativo desse vínculo; a repetição
  idêntica não gera novo evento. Identidade global e outras contabilidades não mudam.
- DELETE não desativa a conta global. Havendo grants de empresas pertencentes
  à contabilidade, retorna 409: revogar esses grants explicitamente antes.
  Não há remoção financeira em cascata. Empresas legadas ainda sem cliente não
  têm contabilidade inferida e seus grants não são alterados por esta operação.
- Mutações serializam na identidade de destino, como o convite; revalidam a
  autorização após o lock. O serviço usa READ_COMMITTED para observar o vínculo
  que uma operação concorrente acabou de criar.
- Alterações reais registram snapshot das permissões no diário de auditoria
  na mesma transação. Listagem pagina no banco sem fetch de coleção.
- Não foi introduzida uma regra de “último administrador da contabilidade”.
  A administração global explícita pode recuperar o vínculo administrativo.

## Testes e limites

Testes de estado, HTTP/PostgreSQL e concorrência cobrem isolamento entre firmas,
busca/paginação, CSRF, preservação de identidade, recusa de cascata e criação
concorrente do mesmo vínculo. O gerenciamento explícito de grants de empresa
é o próximo bloco da #37. Escopo por centro de custo continua fail-closed,
sem alegar filtragem que ainda não existe.
