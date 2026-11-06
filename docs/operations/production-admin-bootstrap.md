# Bootstrap do primeiro administrador

O banco principal começa vazio. O primeiro administrador de plataforma é criado
por um processo standalone one-shot; nunca por startup automático, usuário padrão
versionado ou endpoint público.

Executável:

```text
java -jar /app/production-admin-bootstrap.jar
```

## Guards

O comando exige:
- `SPRING_PROFILES_ACTIVE=prod`;
- projeto Railway `finemp-production`;
- ambiente `production`;
- `FINEMP_PRODUCTION_PROJECT_ID` igual a `RAILWAY_PROJECT_ID`;
- datasource JDBC privado `*.railway.internal`;
- confirmação `FINEMP_PRODUCTION_BOOTSTRAP_CONFIRM=BOOTSTRAP_FINEMP_PRODUCTION`.

Nome, e-mail e senha do primeiro administrador são fornecidos apenas por
`FINEMP_PRODUCTION_ADMIN_NAME`, `FINEMP_PRODUCTION_ADMIN_EMAIL` e
`FINEMP_PRODUCTION_ADMIN_PASSWORD` no armazenamento seguro do Railway.
Nenhum valor real deve aparecer em commit, Issue, documentação, log ou chat.

## Efeito

Em banco sem identidades, cria exatamente um usuário ativo com as permissões:
- `ACCOUNTING_FIRM_CREATE`;
- `ACCOUNTING_FIRM_VIEW`;
- `UNASSIGNED_COMPANY_ASSIGN`;
- `PLATFORM_USER_MANAGE`.

Não cria contabilidade, cliente, Company, fonte contábil ou grant financeiro.
Execuções concorrentes são serializadas. Repetição com a mesma identidade e
conjunto exato de permissões não altera a senha; identidade divergente ou estado
incompleto é recusado.

Depois do primeiro login, remover do ambiente a confirmação e as três variáveis
temporárias do administrador. O restante do onboarding deve usar as APIs
auditadas de administração e acesso.
