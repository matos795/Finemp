# Administração headless da hierarquia SaaS

## Operações e autorizações

| Escopo | Operação | Permissão explícita |
| --- | --- | --- |
| Plataforma | GET `/api/admin/accounting-firms` | `ACCOUNTING_FIRM_VIEW` |
| Plataforma | POST `/api/admin/accounting-firms` | `ACCOUNTING_FIRM_CREATE` |
| Contabilidade | GET/POST `/api/accounting-firms/{accountingFirmId}/clients` | `HIERARCHY_MANAGE` nessa contabilidade |
| Cliente | GET/POST `/api/accounting-firms/{accountingFirmId}/clients/{clientId}/companies` | `HIERARCHY_MANAGE` e relação pai/filho válida |
| Saneamento | PUT `/api/accounting-firms/{accountingFirmId}/clients/{clientId}/companies/{companyId}/assignment` | `UNASSIGNED_COMPANY_ASSIGN` na plataforma e `HIERARCHY_MANAGE` no destino |
| Empresa | GET/POST `/api/accounting-firms/{accountingFirmId}/clients/{clientId}/companies/{companyId}/accounting-sources` | `ACCOUNTING_SOURCE_MANAGE` e todas as relações válidas |

Todas exigem sessão; mutações também exigem CSRF. Não há concessão automática de acesso financeiro, nem mesmo ao criar uma empresa. Um ID recebido não prova autorização. O backend valida contabilidade → cliente → empresa antes de consultar/gravar a fonte.

## Cadastros e consultas

- Contabilidade e Cliente: nome; criados ativos.
- Empresa: razão social e CNPJ de 14 dígitos; Cliente obrigatório nas novas inclusões.
- Fonte: tipo atualmente `QUESTOR` e referência externa opcional; criada ativa.
- Não há endpoints de exclusão ou movimentação de uma empresa entre clientes.
- Configuração de conexão, senha, host e demais secrets não são aceitos nem retornados nesses DTOs.

Listagens recebem `page` (padrão 0), `size` (padrão 20; máximo 100) e `search`. Busca por nome/razão social; nas fontes, por referência externa. Busca vazia inclui fontes sem referência. A resposta tem `items`, `page`, `size`, `totalItems`, `totalPages`. Ordenação estável pelo campo principal e ID; filtragem e paginação acontecem no servidor.

Permissão insuficiente retorna 403; relação fora do caminho autorizado retorna 404; conflito cadastral ou tentativa de reassociação retorna 409; parâmetros inválidos retornam 400. Falhas de integridade não expõem mensagem do banco.

## DECISÃO: vínculo controlado de empresa legada

A operação associa somente empresa ainda sem Cliente. Repetição para o mesmo destino é idempotente e não cria outro evento. Uma empresa já ligada a outro Cliente não é movida. O domínio também rejeita uma segunda associação direta.

O serviço bloqueia a linha da empresa durante a verificação/associação. Duas requisições concorrentes para destinos diferentes não podem vencer simultaneamente. A associação e o evento de auditoria (empresa, cliente, ator e instante) são gravados na mesma transação pela migration V13. Dados contábeis, CNPJ, fontes e grants anteriores são preservados.

## Verificação e limites

Testes HTTP/PostgreSQL cobrem 401/403, autorização por contabilidade, relações cruzadas, paginação inválida, criação de hierarquia/fonte, ausência de segredos e vínculo auditado. Teste com duas transações verifica uma única associação/evento. Testes de domínio protegem contra reassociação.

Edição de cadastros/estado, gestão completa de grants, campos comerciais adicionais e auditoria geral continuam em incrementos próprios. Não declarar esses itens como entregues por este CRUD mínimo. Não foi executado saneamento de dados reais.
