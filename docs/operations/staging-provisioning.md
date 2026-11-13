# Provisionamento sintético explícito (#262 / #53)

## DECISÃO / contrato

O JAR `/app/staging-provisioner.jar` é executado manualmente como processo separado: `java -jar /app/staging-provisioner.jar`. Não é endpoint, componente Spring ou runner do startup. Não inicia servidor, Questor, Connector ou migrations. A API deve ter concluído Flyway antes; a migration V48 cria somente o marcador vazio.

Variáveis exigidas, sem valores secretos neste documento:

- `SPRING_PROFILES_ACTIVE`: exatamente prod e staging, nunca local.
- `RAILWAY_PROJECT_NAME`: finemp-staging; `RAILWAY_ENVIRONMENT_NAME`: staging.
- `RAILWAY_PROJECT_ID` e `FINEMP_STAGING_PROJECT_ID`: UUIDs coincidentes; confirmar o segundo no projeto criado, nunca copiar de FluxFund.
- `FINEMP_STAGING_SEED_CONFIRM`: PROVISION_SYNTHETIC_STAGING.
- `SPRING_DATASOURCE_URL`: JDBC de host `.railway.internal`, porta e banco explícitos, sem parâmetros adicionais; `SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` por referências privadas.
- `FINEMP_STAGING_SEED_PASSWORD`: segredo sintético gerado por canal seguro, 16–64 caracteres ASCII imprimíveis sem espaços. Nunca argumento de CLI, exemplo, commit ou log.

Os metadados e o opt-in são barreiras contra erro operacional, não prova criptográfica de destino; confirmar projeto, ambiente e datasource antes da execução. O comando não tenta detectar conteúdo real por inferência: recusa qualquer organização/usuário preexistente se não houver marcador próprio.

## Efeito e repetição

Uma transação cria contabilidade, cliente, Company, conta financeira zerada e grupos/subcategorias REC/DADM sintéticos. Usuário `demo@finemp.example.invalid` recebe hash no formato delegating {bcrypt}, grant CUSTOM/ALL e somente FINANCIAL_ENTRY_MANAGE, BANK_ACCOUNT_VIEW, FINANCIAL_CATEGORY_VIEW. Não há permissões de plataforma, Questor ou administração irrestrita. Não cria lançamentos/baixas: esses usam as APIs existentes na prova de jornada.

IDs determinísticos e marcador `finemp-synthetic-v1`; trava transacional e locks de tabelas serializam a verificação de banco vazio. Em falha, rollback integral. Repetição com marcador válido retorna sem modificar senha/grants/cadastros. Estado incompleto é recusado, sem tentativa de reparar ou sobrescrever dados.

Primeiro executar `--help` para verificar o artefato. Depois configurar os segredos pela interface Railway ou stdin da CLI, sem expô-los em comandos/logs, e executar o comando dentro do serviço/ambiente confirmado. Nunca usar preDeployCommand ou startup para seed. Remover confirmação e segredo temporário de provisionamento após sucesso; preservar a credencial do usuário apenas em gerenciador seguro para o smoke/login. Uma nova execução exige opt-in explícito novamente.

Saída mostra apenas conclusão e IDs sintéticos, nunca senha/hash/URL. Falha retorna exit2 e mensagem genérica; investigar a condição pelos gates/estado do ambiente, não habilitar logs de segredos. O programa não modifica a senha ao repetir com outra entrada.

## Evidências / limites

Testes de alvo validam projeto/ambiente/profiles e URL privada. Testes PostgreSQL cobrem banco ocupado, rollback tardio, concorrência e repetição sem reampliar grants/redefinir senha. Build local do JAR/help e testes de alvo são distintos de execução PostgreSQL no CI. Não houve provisionamento real só por compilar o comando.

Após aprovação e deploy isolado: provisionar → login/CSRF → criar/baixar via API → conferir DRE/caixa/títulos/indicadores/efeitos → isolamento/logout. Sem afirmar frontend conectado ou produção pronta.

## Fixture explícita de isolamento (#264)

Depois da fixture inicial, executar separadamente `java -jar /app/staging-provisioner.jar --isolation-company`. Reutiliza os gates de projeto, ambiente, profiles, datasource privado e confirmação explícita; este modo não lê nem redefine a senha. Exige marcador/Company/client da fixture original.

Cria somente uma segunda Company sintética sem grants, com marcador próprio. Não cria lançamentos, usuários ou permissões. Retry valida o marcador, vínculo ao cliente e ausência de grants; se o acesso tiver mudado, recusa sem revogar nada. Transação e locks protegem concorrência/rollback. O usuário de demonstração deve acessar a própria Company e receber 403 para a segunda Company existente.

Testes PostgreSQL verificam pré-condição, repetição, rollback e preservação de permissões alteradas; o teste HTTP usa as duas Companies reais no banco descartável, login normal e snapshot. A prova em staging usa os mesmos IDs determinísticos e nunca uma Company real. Ao concluir, remover confirmação e segredo temporários e reiniciar o serviço para retirar essas variáveis do processo.


## Reset explícito da senha sintética

Quando a credencial do operador sintético não estiver disponível, não tentar
recuperar o bcrypt, editar `user_accounts` manualmente ou criar bypass HTTP.

O provisionador aceita `--reset-password` exclusivamente para o usuário
determinístico `demo@finemp.example.invalid`. O modo reutiliza todos os gates de
staging deste documento, exige o marker `finemp-synthetic-v1` e confere o ID,
email, estado ativo e ausência de convite pendente antes de qualquer alteração.

A nova senha deve ser definida em `FINEMP_STAGING_SEED_PASSWORD` diretamente no
armazenamento seguro do ambiente, nunca em argumento de CLI, Issue, commit, log
ou chat. Em seguida executar, no alvo staging já confirmado:

```text
java -jar /app/staging-provisioner.jar --reset-password
```

O comando substitui apenas `password_hash` do operador sintético e incrementa
`credentials_version`, invalidando sessões anteriores pela mesma semântica usada
pela aplicação. Grants, permissões, Companies e dados financeiros não são
alterados. A saída confirma somente a conclusão e IDs sintéticos, nunca senha ou
hash.

Após validar o novo login, remover novamente
`FINEMP_STAGING_SEED_PASSWORD` do ambiente. Este comando não é mecanismo de
reset para usuários reais nem deve ser habilitado em production.

## Elevação explícita do operador sintético a administrador de plataforma (#334)

Serve para testar o fluxo real de identidade em staging: SMTP, convite,
ativação, criação de senha e reset. Não cria outro usuário e não usa SQL
manual: eleva somente o operador sintético `demo@finemp.example.invalid` já
criado e marcado por `finemp-synthetic-v1`.

### Confirmação própria

O modo `--platform-admin` exige os mesmos gates de alvo deste documento
(profiles `prod,staging`, projeto `finemp-staging`, ambiente `staging`,
`RAILWAY_PROJECT_ID` = `FINEMP_STAGING_PROJECT_ID` e datasource privado
`*.railway.internal`) e, no lugar do opt-in da fixture, uma confirmação
específica:

- `FINEMP_STAGING_PLATFORM_ADMIN_CONFIRM`: `GRANT_SYNTHETIC_STAGING_PLATFORM_ADMIN`.

Razão: o opt-in que permite criar fixture financeira
(`FINEMP_STAGING_SEED_CONFIRM`) não autoriza elevar privilégios de plataforma,
e a confirmação de elevação não autoriza criar fixture nem trocar senha. Cada
modo aceita somente a sua confirmação. O modo não lê
`FINEMP_STAGING_SEED_PASSWORD` e não altera a senha.

### Efeito

Em uma transação com advisory lock e locks de `user_accounts`,
`user_account_platform_permissions` e `staging_seed_receipts`:

1. exige o marker `finemp-synthetic-v1` apontando para a Company e o usuário
   determinísticos;
2. confere ID, email `demo@finemp.example.invalid`, usuário ativo e sem convite
   pendente;
3. sem permissões de plataforma: insere exatamente `ACCOUNTING_FIRM_CREATE`,
   `ACCOUNTING_FIRM_VIEW`, `UNASSIGNED_COMPANY_ASSIGN` e `PLATFORM_USER_MANAGE`;
4. já com exatamente essas quatro: não faz nada (retry seguro);
5. conjunto parcial, extra ou identidade divergente: recusa sem "reparar".

Não altera `password_hash`, `credentials_version`, grants financeiros,
Companies, clientes ou escritórios. Também não concede acesso administrativo ao
escritório contábil: esse grant continua sendo feito depois pela API/UI normal
de Administração. Falha em qualquer ponto faz rollback integral. A saída mostra
apenas a conclusão e IDs sintéticos; nunca email, senha, hash ou datasource.

Não há endpoint HTTP, runner de startup, `preDeployCommand` ou execução
automática para este modo.

### Jornada operacional

1. **Fixture** (se ainda não existir): `java -jar /app/staging-provisioner.jar`
   com `FINEMP_STAGING_SEED_CONFIRM` e `FINEMP_STAGING_SEED_PASSWORD`.
2. **Senha sintética:** usar a credencial guardada no gerenciador seguro ou,
   se indisponível, `--reset-password` com segredo temporário no ambiente
   (seção anterior). Remover `FINEMP_STAGING_SEED_PASSWORD` em seguida.
3. **Elevar:** definir `FINEMP_STAGING_PLATFORM_ADMIN_CONFIRM` diretamente no
   armazenamento seguro do ambiente staging confirmado e executar
   `java -jar /app/staging-provisioner.jar --platform-admin`. Repetir é seguro.
4. **Limpar:** remover `FINEMP_STAGING_PLATFORM_ADMIN_CONFIRM` e qualquer
   confirmação/segredo temporário (`FINEMP_STAGING_SEED_CONFIRM`) e reiniciar o
   serviço para retirá-los do processo. A confirmação de elevação nunca fica
   permanentemente no ambiente.
5. **Login pela UI** com o operador sintético; o menu Administração passa a
   aparecer (sem elevação, continua oculto e as APIs `/api/admin/**` respondem
   403).
6. **Convite:** em Administração, emitir convite oficial para um endereço
   pessoal controlado pelo responsável pelo teste (nunca de cliente real).
7. **Ativação:** abrir o link recebido, criar a senha, fazer login com o novo
   usuário e testar o reset de senha pelo fluxo normal.
8. **Nunca** executar este modo em production nem reutilizá-lo para usuários
   reais; em production a administração inicial segue o bootstrap próprio.

### Evidências

Testes de modo cobrem: cada modo exige somente a sua confirmação, todos os
gates de alvo continuam obrigatórios para a elevação e mensagens de recusa não
ecoam valores. Testes PostgreSQL cobrem: marker ausente, concessão exata, retry
no-op, conjunto parcial/extra recusado sem reparo, identidade divergente,
rollback tardio, concorrência e preservação de senha, credenciais, grants
financeiros e Companies. O teste HTTP faz login normal do mesmo operador antes
(sem Administração, 403) e depois da elevação (as quatro permissões em
`/api/auth/me` e `/api/admin/**` 200), sem grant de escritório.


## Elevação explícita do operador sintético a SYSTEM_ADMIN

Para homologar a navegação completa e as superfícies Company-scoped recém-publicadas,
o provisionador também oferece um modo separado do `--platform-admin`:

```text
java -jar /app/staging-provisioner.jar --system-admin
```

Esse modo existe porque `--platform-admin` continua deliberadamente restrito às
quatro permissões administrativas usadas nos testes de identidade. Não ampliar esse
modo por conveniência preserva o contrato da #334.

### Confirmação própria

O modo exige:

- `FINEMP_STAGING_SYSTEM_ADMIN_CONFIRM=GRANT_SYNTHETIC_STAGING_SYSTEM_ADMIN`;
- os mesmos guards de projeto, ambiente, profiles e datasource privado;
- o marker `finemp-synthetic-v1` e a identidade determinística do operador.

A confirmação de `--system-admin` não autoriza fixture, reset de senha ou
`--platform-admin`, e as confirmações desses modos não autorizam SYSTEM_ADMIN.

### Efeito

O comando adiciona somente `SYSTEM_ADMIN` ao operador sintético. Ele aceita como
estado inicial apenas:

1. nenhuma permissão de plataforma; ou
2. exatamente as quatro permissões do modo `--platform-admin`.

Depois da elevação, retry com `SYSTEM_ADMIN` sozinho ou com as quatro permissões
anteriores + `SYSTEM_ADMIN` é no-op. Qualquer conjunto divergente é recusado sem
reparo automático.

Senha, `credentials_version`, Companies, grants financeiros, escritório e dados
de negócio não são modificados.

Com `SYSTEM_ADMIN`, o `/api/auth/me` retorna todas as Companies existentes no
staging e o frontend pode homologar a navegação completa. Esse modo é exclusivo
para o operador sintético de staging e nunca deve ser usado em production.

Após a execução, remover
`FINEMP_STAGING_SYSTEM_ADMIN_CONFIRM` do ambiente e reiniciar o serviço para
retirar a confirmação do processo.
