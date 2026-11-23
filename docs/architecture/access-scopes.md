# Escopos de acesso do FINEMP

## Decisão

O backend possui três escopos independentes de autorização:

```text
Plataforma FINEMP
└── permission global

Contabilidade
└── AccountingFirmAccessGrant + permission organizacional

Empresa
└── CompanyAccessGrant + permission funcional + data scope
```

Um grant mais amplo na hierarquia cadastral não concede automaticamente acesso a dados financeiros dos níveis inferiores.

## Plataforma

Permissions globais são reservadas para operações que não pertencem a uma Contabilidade específica:

- `ACCOUNTING_FIRM_CREATE`;
- `PLATFORM_USER_MANAGE`.

Elas são persistidas para o usuário, mas não representam uma role de negócio definitiva.

## Contabilidade

`AccountingFirmAccessGrant` vincula um usuário a uma Contabilidade. A combinação usuário/Contabilidade é única e pode conter:

- `HIERARCHY_VIEW` (leitura de Clients e Companies);
- `HIERARCHY_MANAGE` (administração; também satisfaz leitura);
- `ACCOUNTING_SOURCE_VIEW` (leitura de fontes);
- `ACCOUNTING_SOURCE_MANAGE` (administração; também satisfaz leitura);
- `USER_ACCESS_MANAGE`;
- `CONNECTOR_MANAGE`.

A navegação operacional (quais contabilidades, clientes e empresas o usuário vê) é derivada destes grants e dos CompanyAccessGrants em [carteira operacional](accounting-portfolio.md). Navegar não autoriza ler dados de Company.

As verificações consultam diretamente a permission requerida no banco, sem carregar coleções para depois filtrar em memória.

## Empresa

`CompanyAccessGrant` continua sendo a autoridade para endpoints financeiros. Ele mantém role, permissions funcionais e o escopo de centro de custo.

Portanto:

- administrar a estrutura de uma Contabilidade não autoriza ler a DRE de todas as suas Empresas;
- receber acesso à Empresa não autoriza criar Clientes nem alterar a Contabilidade;
- o frontend pode adaptar a navegação, mas não participa da decisão de autorização.

## Evolução

- **PROPOSTA:** roles administrativas poderão ser presets de permissions, sem substituir a verificação explícita.
- **PONTO ABERTO:** o conjunto final de perfis comerciais e operacionais depende de validação de negócio.
- **DECISÃO:** novos endpoints devem declarar o escopo e a permission exatos antes da implementação.


