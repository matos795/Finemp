# Administração de identidades globais

## Operações

Todas exigem sessão e `PLATFORM_USER_MANAGE` explícita na plataforma. Permissão de contabilidade não autoriza desativar uma identidade global compartilhada.

| Operação | Resultado |
| --- | --- |
| GET `/api/admin/users` | Página/busca por nome ou e-mail, `page`/`size` até 100; resumo sem coleções de permissões |
| GET `/api/admin/users/{userId}` | Resumo e permissões de plataforma do alvo autorizado |
| PATCH `/api/admin/users/{userId}/status` | Corpo `active`; retorna resumo do estado |
| PUT `/api/admin/users/{userId}/platform-permissions` | Corpo `permissions`; substituição explícita; 204 |

Mutações exigem CSRF. DTOs não incluem senha, hash, token ou configuração de conexão. Campos de identidade/credenciais não são vinculados diretamente ao corpo de status/permissões. Busca/paginação acontecem no PostgreSQL, com ordem estável. O detalhe carrega permissões somente do usuário selecionado; a lista não provoca fetch paginado de coleção.

## DECISÕES técnicas

- Desativação incrementa a versão das credenciais, cancela convite pendente e revoga sessões na próxima requisição.
- Reativação de identidade já estabelecida também incrementa a versão, sem restaurar links antigos ou trocar a senha.
- Marcador de convite não é senha válida: ativação administrativa dessa identidade recebe 409. Não transformar conta sem senha definida pelo usuário em ativa.
- Permissões de plataforma não concedem grants financeiros. Alterações de permission são consultadas no banco em cada operação protegida, sem depender de authorities antigas na sessão.
- Operações sem mudança de estado são idempotentes, sem novo evento de sucesso.
- Mutações reais gravam ação, ator, alvo e snapshot de permissões no journal da mesma transação.

## Último administrador e concorrência

Não é permitido desativar/remover a permission do último administrador ativo de plataforma (409). Um lock transacional PostgreSQL em namespace estável serializa essas mutações em todas as réplicas que compartilham o banco. A autoridade do ator é verificada antes e depois do lock; contas inativas não satisfazem a autorização de plataforma. Depois disso, a linha do usuário alvo é bloqueada e a condição é reavaliada antes da alteração.

O lock é transacional, não de sessão; é liberado em commit/rollback. Nenhum lock é mantido por chamada ao provedor de e-mail. Testes concorrentes verificam que duas remoções não eliminam todos os administradores.

Essas mutações pedem explicitamente isolamento `READ_COMMITTED`, pois a revalidação após espera precisa enxergar commits anteriores. O lock verifica o isolamento real da conexão e recusa um contexto incompatível em vez de usar snapshot antigo. A espera do statement tem timeout de 10 segundos; falha reverte a operação, sem contornar a proteção.

## Limites e continuidade

Não há exclusão física nem cadastro público de administrador. Bootstrap/recuperação operacional da plataforma pertencem à configuração segura de ambiente, não a um usuário/senha padrão versionado. O fluxo específico de reconvite de conta pendente anteriormente bloqueada permanece separado: a proteção não é contornada por ativação administrativa.

Revogação só em uma contabilidade deve alterar seu vínculo, não o estado global da identidade. Gestão de vínculos de contabilidade/empresa continua em incrementos próprios de #37. Nenhum usuário real foi alterado por esta entrega.
