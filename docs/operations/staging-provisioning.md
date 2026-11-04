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
