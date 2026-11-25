# Gestão de pessoas — primeira fatia operacional (#462)

## Escopo

Cadastro operacional por Company, sem regra financeira ou trabalhista:

| Tela | Rota | API | Permissão |
| --- | --- | --- | --- |
| Funcionários | `/people/employees` | `GET/POST /api/companies/{id}/employees`, `PUT …/{employeeId}` | `EMPLOYEE_VIEW` lê; `EMPLOYEE_MANAGE` lê e grava |
| Cargos e descrições (DRC) | `/people/job-positions` | `GET/POST /api/companies/{id}/job-positions`, `PUT …/{positionId}` | idem |

As permissões `EMPLOYEE_VIEW/MANAGE` são os nomes já reservados na matriz de paridade (`func`).

## Modelo

- **Cargo:** título (único por Company, sem diferenciar maiúsculas nem espaços), descrição livre (DRC) e situação ativa/inativa.
  - Cargo inativo continua em quem já o tem, mas não é atribuído de novo.
- **Funcionário:**
  - nome;
  - regime (`CLT`, `APR`, `PRO`, `EST`, `PJ`, os códigos homologados de F20; só código e rótulo);
  - cargo e centro de resultado opcionais;
  - admissão;
  - desligamento opcional, nunca antes da admissão.
  - A situação (ativo/desligado) é **derivada** da data de desligamento, pelo `Clock`.
- Sem exclusão física: cargo é desativado e funcionário recebe desligamento.
- Edição com versão otimista: versão divergente → 409 `PEOPLE_STALE_VERSION`.

## Minimização de dados

Não existem campos de documento pessoal, data de nascimento, salário, endereço, contato ou dados bancários.

- Remuneração, custo por funcionário, benefícios, encargos e integração com orçamento continuam no roadmap (#45). Exigem permissão própria e regras homologadas (F20).
- Avaliação, PDI e 9-box não estão na referência homologada e seguem sem rota.

## Isolamento e autorização

- Toda leitura e escrita exige permissão fresca na Company do caminho.
- A escrita trava a Company e revalida a permissão.
- Cargo e centro de resultado são buscados por `(id, companyId)`: referência de outra Company → 404 `PEOPLE_NOT_FOUND`, mesmo com acesso a ela.
- Centros de resultado referenciados por funcionários ficam protegidos pelo mesmo guard usado por bens.

Testes: `PeopleSecurityHttpIntegrationTest` (viewer × manager, Company alheia, referência cruzada, datas, regime inválido, versão, cargo duplicado e inativo, ausência de campos pessoais) e `frontend/tests/people.test.mjs`.
