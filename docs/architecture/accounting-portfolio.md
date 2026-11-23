# Carteira operacional da Área da Contabilidade (M1)

Filha da #433. Contrato mínimo para a navegação AccountingFirm → Client → Company da Área da Contabilidade.

## DECISÃO de produto

- Cada usuário enxerga somente as contabilidades, clientes e empresas a que tem acesso.
- Navegar até uma Company e ler dados dela são autorizações diferentes.
- SYSTEM_ADMIN mantém o comportamento global, sem AccountingFirmAccessGrant explícito.

## Permissões de contabilidade

| Permissão | Leitura | Mutação |
| --- | --- | --- |
| `HIERARCHY_VIEW` (nova) | Clients e Companies da contabilidade | nenhuma |
| `HIERARCHY_MANAGE` | Clients e Companies (também satisfaz leitura) | criar, excluir, atribuir (comportamento anterior) |
| `ACCOUNTING_SOURCE_VIEW` (nova) | AccountingSources no escopo | nenhuma |
| `ACCOUNTING_SOURCE_MANAGE` | AccountingSources (também satisfaz leitura) | criar, alterar referência externa |
| `USER_ACCESS_MANAGE` | sem mudança | sem mudança |
| `CONNECTOR_MANAGE` | sem mudança | sem mudança |

- **FATO CONFIRMADO:** as leituras `GET /api/accounting-firms/{id}/clients` e `GET .../clients/{clientId}/companies` aceitam `HIERARCHY_VIEW` ou `HIERARCHY_MANAGE`. `GET .../companies/{companyId}/accounting-sources` aceita `ACCOUNTING_SOURCE_VIEW` ou `ACCOUNTING_SOURCE_MANAGE`.
- **FATO CONFIRMADO:** POST, PUT e DELETE continuam exigindo `*_MANAGE`.
- **FATO CONFIRMADO:** `HIERARCHY_VIEW` não lê fontes, e `ACCOUNTING_SOURCE_VIEW` não lista a hierarquia.
- **FATO CONFIRMADO:** a coluna de permissões é texto sem CHECK no banco, então não há migration. O catálogo de acesso passou à versão `2026-11-23.1` (6 permissões de contabilidade).

## `GET /api/auth/accounting-portfolio`

- Identidade somente pela sessão; não recebe `userId`, `firmId` nem filtros.
- A resposta é recalculada a cada chamada e marcada `Cache-Control: no-store`. Uma revogação de grant vale já na chamada seguinte, na mesma sessão.
- A resposta é só leitura: não cria CompanyAccessGrant nem permissão de Company ou de contabilidade.

Regras:

| Usuário | Contabilidades | Clients | Companies | `scope` |
| --- | --- | --- | --- | --- |
| SYSTEM_ADMIN ativo | todas | todos | todas com Client | `FULL_HIERARCHY` |
| `HIERARCHY_VIEW` ou `HIERARCHY_MANAGE` na contabilidade | essa | todos dela, inclusive sem Company | todas dela | `FULL_HIERARCHY` |
| Demais (inclusive membro sem essas permissões) | só as que têm Company com grant | só os que têm Company com grant | só as com CompanyAccessGrant | `GRANTED_COMPANIES` |

- Company sem Client (legado não saneado) não aparece. Ela continua acessível pelos grants de `/api/auth/me`.
- Ordenação: nome sem diferenciar maiúsculas, depois id, em cada nível.

Resposta (exemplo fictício):

```json
{
  "accountingFirms": [
    {
      "accountingFirmId": "00000000-0000-0000-0000-0000000000a1",
      "name": "Contabilidade Demonstração",
      "active": true,
      "scope": "GRANTED_COMPANIES",
      "clients": [
        {
          "clientId": "00000000-0000-0000-0000-0000000000c1",
          "name": "Cliente Demonstração",
          "active": true,
          "companies": [
            { "companyId": "00000000-0000-0000-0000-000000000101", "legalName": "Empresa Demonstração" }
          ]
        }
      ]
    }
  ]
}
```

## Consumo pelo frontend

1. A Área da Contabilidade aparece quando `accountingFirms` não está vazio. `organization-access` continua sendo a fonte das permissões administrativas, não da navegação.
2. O seletor Contabilidade → Cliente → Empresa é montado só desta resposta. As listagens administrativas paginadas ficam para a administração.
3. Para ler dados de uma Company selecionada, usar as permissões dessa Company em `/api/auth/me`. Sem grant, a tela mostra "sem acesso aos dados". O backend responde 403 de qualquer forma.
4. Não inferir acesso a dados a partir de `scope`.

## Testes

- `AccountingPortfolioHttpIntegrationTest` cobre:
  - grant só na A;
  - Client e contabilidade sem Company autorizada;
  - Clients diferentes;
  - `HIERARCHY_VIEW` e `HIERARCHY_MANAGE`;
  - revogação na mesma sessão;
  - SYSTEM_ADMIN sem grant;
  - navegação sem acesso a dados e sem permissão criada.
- `HierarchyReadPermissionHttpIntegrationTest` cobre:
  - leitura e mutação com VIEW e MANAGE;
  - isolamento entre contabilidades;
  - rota cruzada (404).
- `AccountingFirmPermissionAuthorizationServiceTest` cobre a verificação "qualquer uma de".

## PONTO ABERTO

- Resposta sem paginação. Para SYSTEM_ADMIN ela inclui todas as hierarquias. Se o volume crescer, evoluir para listagem por nível ou filtro por contabilidade, sem quebrar o contrato.
- Escopo `RESTRICTED` de centro de custo não altera a navegação. Ele continua aplicado na leitura de dados.
