# Administração explícita de acesso por empresa — #77 / #37

## FATO CONFIRMADO — contrato implementado

Base: `/api/admin/accounting-firms/{firmId}/clients/{clientId}/companies/{companyId}/users`.
GET pagina e busca membros da empresa no banco (page 0, size 20, máximo 100).
`/{userId}` oferece GET de detalhe, PUT e DELETE idempotente (204).

PUT recebe somente `role`, `costCenterScope` e `permissions` explícitas.
DTOs nunca aceitam identidade, empresa, vínculo de contabilidade, senha ou
permissões globais no corpo. O servidor usa a identidade da sessão e a cadeia
contabilidade → cliente → empresa verificada nas consultas.

## DECISÃO técnica — nenhuma concessão implícita

- Administração exige `USER_ACCESS_MANAGE` na contabilidade ou
  `PLATFORM_USER_MANAGE` global; isso não concede acesso financeiro ao gestor.
- O destinatário deve ter vínculo explícito com a contabilidade, inclusive
  quando o ator é administrador global. Convite ou associação administrativa
  devem preceder a concessão financeira. Empresas sem cliente não têm dono inferido.
- `role` é rótulo; ADMIN não expande permissions. Conjunto vazio não lê razão,
  DRE ou classificações. Cada API verifica sua capability no servidor.
- `RESTRICTED` retorna 409 enquanto não houver filtro efetivo por centro de
  custo; nunca é convertido silenciosamente para ALL.
- DELETE afeta somente o grant dessa empresa. Não altera outras empresas,
  contabilidades ou identidade global. Sem grant é idempotente e não gera evento.
- Mutações serializam na identidade do destinatário e revalidam o ator após
  o lock. Esse protocolo é compartilhado com convite/revogação de contabilidade,
  evitando criação concorrente de acesso órfão. Usa READ_COMMITTED.
- Mudanças reais registram ator, destinatário, firma, empresa e snapshot de
  permissions em auditoria transacional. Role é rótulo, sem efeito implícito;
  trilha expandida de metadados poderá evoluir na #51.

## Verificação

Testes de estado, HTTP/PostgreSQL e concorrência cobrem isolamento pai/filho,
401/403, CSRF, whitelist do payload, ausência de escalada por ADMIN, paginação,
revogação idempotente e corrida entre concessão de empresa e remoção da firma.
Presets view/edit por tela serão publicados separadamente; não foram inventadas
políticas financeiras por papel para antecipar esse contrato.
