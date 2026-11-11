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


## Recuperação da senha do primeiro administrador

Se o bootstrap já criou o primeiro administrador, mas o primeiro smoke de login
falhar antes de qualquer onboarding operacional, usar exclusivamente o one-shot:

```text
java -jar /app/production-first-admin-password-reset.jar
```

A ferramenta não é um reset administrativo genérico. Ela só aceita o estado
inicial esperado: exatamente um usuário ativo e não pendente, conjunto exato das
quatro permissões de plataforma e nenhum grant de escritório ou Company.

Além dos guards comuns de produção, exige:

- `FINEMP_PRODUCTION_ADMIN_PASSWORD_RESET_CONFIRM=RESET_FINEMP_FIRST_ADMIN_PASSWORD`;
- `FINEMP_PRODUCTION_ADMIN_EMAIL`;
- `FINEMP_PRODUCTION_ADMIN_NEW_PASSWORD`.

A nova senha precisa cumprir `AccountPasswordPolicy`. Se já coincidir com o hash
armazenado, a operação é no-op. Se mudar, o hash é substituído e
`credentials_version` é incrementado para invalidar sessões existentes.

E-mail, senha, hash e credenciais reais nunca devem aparecer em commit, Issue,
documentação, logs ou chat. Após a execução, remover ou neutralizar imediatamente
as três variáveis temporárias de recuperação e retirar qualquer pre-deploy usado
somente para executar o one-shot.

## Primeiro administrador do sistema

O modo separado `--system-admin` eleva uma conta existente. Exige os guards comuns de produção, a confirmação dedicada `FINEMP_PRODUCTION_SYSTEM_ADMIN_CONFIRM=GRANT_FINEMP_PRODUCTION_SYSTEM_ADMIN` e a identificação coincidente por `FINEMP_PRODUCTION_SYSTEM_ADMIN_USER_ID` e `FINEMP_PRODUCTION_SYSTEM_ADMIN_EMAIL`. O alvo deve estar ativo, estabelecido e possuir as quatro permissões originais de plataforma.

A operação serializa a elevação com a administração de permissões. A repetição com o mesmo alvo e estado esperado é idempotente. Uma identidade divergente ou outro administrador global impede a elevação. A operação preserva senha, versão de credenciais e grants. Remova a confirmação e os identificadores temporários após o uso. O bootstrap original continua concedendo apenas as quatro permissões anteriores.
