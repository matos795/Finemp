# Convite e recuperação de senha

## Estado da entrega

O primeiro incremento da Issue #36 implementa o núcleo transacional de tokens e as transições de credenciais. Os endpoints de convite/ativação/recuperação e a entrega de e-mail são incrementos posteriores da mesma Issue. Nenhuma nova operação pública de recuperação é exposta por este incremento.

## Tokens

- 32 bytes aleatórios por `SecureRandom`, codificados em Base64 URL sem padding;
- somente SHA-256 do token é persistido;
- finalidade explícita: `INVITATION` ou `PASSWORD_RESET`;
- emissão, expiração e consumo registrados no PostgreSQL;
- expira no instante exato de `expires_at`;
- uma nova emissão invalida os links anteriores do usuário;
- uma alteração de senha invalida todos os links ainda pendentes;
- cada token também guarda a versão das credenciais no momento da emissão; alterações posteriores de senha ou desativação invalidam o link, mesmo se a conta voltar a ser ativada;
- token bruto existe apenas no evento transitório de entrega e nunca no schema ou em logs;
- representações de diagnóstico do token/evento são redigidas.

O hash simples é apropriado para um segredo aleatório de 256 bits. Senhas continuam passando pelo `PasswordEncoder` forte existente, com política diferente.

## Estados da conta

```text
convite → inactive + invitationPending
        → link válido + senha definida
        → active + invitationPending=false + credentialsVersion incrementada

conta ativa → recuperação → nova senha + credentialsVersion incrementada
```

Uma conta inativa por bloqueio não recebe reset e não é reativada por convite. Convidar um e-mail já ativo preserva nome, senha e estado da identidade existente. A concessão de acessos organizacionais continua sendo uma operação administrativa explícita.

`credentialsVersion` será verificada contra a sessão pelo próximo incremento HTTP, para revogar sessões anteriores após redefinição/desativação. Não é uma permission nem um papel de usuário.

## Transação e concorrência

Emissão e consumo adquirem lock de escrita na linha do usuário antes de alterar tokens. O lookup inicial do link retorna apenas IDs, para que uma entidade de token antiga não fique no contexto JPA enquanto outra transação o consome. O lookup inicial de e-mail também retorna apenas o ID.

A criação de uma identidade convidada usa `INSERT ... ON CONFLICT (LOWER(email)) DO NOTHING`, apoiado no índice único de e-mail já existente. Assim, dois convites simultâneos para um e-mail novo convergem para a mesma conta pendente antes de adquirir o lock; o nome e a senha de identidades existentes não são sobrescritos. Esse caminho é coberto por teste concorrente PostgreSQL.

Depois do lock, o serviço lê o token, valida finalidade/estado/expiração, altera o hash da senha e invalida links na mesma transação. Duas transações concorrentes não podem aceitar o mesmo convite. O teste de concorrência usa PostgreSQL real e duas threads.

## Política de senha e configuração

O comportamento de referência exige pelo menos 8 caracteres, uma letra ASCII e um dígito. O backend acrescenta o limite técnico de 72 bytes UTF-8 do encoder BCrypt para rejeitar entradas incompatíveis antes de codificá-las.

Validades configuráveis:

| Propriedade | Padrão |
| --- | --- |
| `finemp.identity.invitation-ttl` | `PT24H` |
| `finemp.identity.password-reset-ttl` | `PT30M` |

O relógio é injetado por `Clock`, usando UTC em execução e instantes controlados em teste.

## Próximo incremento

- autorização administrativa para convites;
- resposta neutra de recuperação, sem revelar existência do e-mail;
- e-mail via port/adapter, enviado somente após commit, sem retornar o token em respostas HTTP;
- revogação efetiva de sessão pela versão de credenciais;
- limitação de tentativas de login/recuperação;
- DTOs/OpenAPI e testes HTTP 401/403/400 e de replay.
