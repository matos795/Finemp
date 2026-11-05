# Configuração do staging financeiro (#258 / #53)

## DECISÃO

Ativar `SPRING_PROFILES_ACTIVE=prod,staging` somente no projeto Railway `finemp-staging`, ambiente staging, com PostgreSQL próprio. Profile não confirma a identidade do banco: conferir destino e referências antes da publicação. Não usar FluxFund.

`application-staging.yml` liga PORT à porta HTTP, escuta em 0.0.0.0 e habilita catálogo, lançamentos/comandos, aging, caixa diário/mensal, DRE operacional e snapshot. Usa cookies Secure/HttpOnly/Lax e expõe somente health, sem detalhes. Flyway habilitado e Hibernate validate continuam na configuração base. Uma réplica; sessões não são compartilhadas e podem ser perdidas no restart.

Datasource exige `SPRING_DATASOURCE_URL` JDBC privado, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` por referências do PostgreSQL do projeto. Não há valores/defaults de datasource no profile staging. Build/start deve executar o JAR da API, nunca Connector.

## Guarda de startup

O EnvironmentPostProcessor registrado para Spring Boot 4 executa após ConfigData e antes de inicializar beans/conexões. Em staging, recusa ausência de prod, presença de local, bootstrap local ou flags Questor/Connector/sync ligadas; também recusa URL/usuário/senha Questor presentes. Recusa cookies enfraquecidos, Flyway desligado, ddl-auto diferente de validate e forwarding de headers habilitado. Erros mencionam apenas o nome da configuração, nunca valores.

Perfis sem staging preservam comportamento anterior. As flags proibidas também constam desligadas no YAML; overrides inseguros impedem startup. A guarda não substitui controle de acesso ao banco/projeto e não detecta dados reais: ambiente dedicado continua pré-condição operacional.

## Proxy — alteração da proposta do handoff

DECISÃO: `server.forward-headers-strategy=none` inicialmente. Não confiar em Forwarded/X-Forwarded-* antes de validar a fronteira do proxy. Cookie Secure funciona no HTTPS externo independentemente da conexão interna HTTP. Os endpoints de login/logout usam respostas sem redirects e as Locations financeiras são relativas. Provar a jornada pelo domínio HTTPS Railway antes de liberar o ambiente. Se surgir necessidade de forwarding, fazer alteração específica com evidência e testes de cabeçalhos, não um override silencioso.

## Gates ainda pendentes

Definição de build, provisionamento sintético separado e publicação não são entregues aqui. Não há seed no startup nem reaproveitamento de LocalUserBootstrap. Provisão deve ser explícita, idempotente e restrita ao destino confirmado. A sessão Railway disponível na máquina precisa ser renovada por `railway login`; nenhum segredo deve ser enviado ao chat. Frontend/client existente permanece intacto.
