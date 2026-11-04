# Imagem da API para Railway (#260)

DECISÃO: serviço `finemp-api` no projeto isolado `finemp-staging`, root de build `backend`. `Dockerfile` multi-stage usa Java 21, compila com Maven Wrapper e leva somente o JAR executável da API para runtime JRE não root (UID10001). Não copia Connector, fontes, arquivos locais ou credenciais para runtime. `.dockerignore` permite somente fontes/configuração de build; não enviar `.env`/target.

Railway config file: selecionar `/backend/railway.toml` ao conectar o repositório monorepo; confirmar root `backend`. Dockerfile relativo ao root. Ao enviar somente backend por CLI, usar `railway.toml` dessa raiz. Não assumir que mudar root altera automaticamente a localização do config file. Uma réplica, health `/actuator/health`, timeout300s e três tentativas de restart. JAR inicia diretamente via ENTRYPOINT; não configurar comando Connector.

Variáveis fornecidas pela plataforma: `SPRING_PROFILES_ACTIVE=prod,staging`, PORT e datasource JDBC privado/usuário/senha por referências do PostgreSQL próprio. Não são gravadas na imagem. O build pula testes porque o CI backend os executa com PostgreSQL; o build Railway não precisa acessar banco nem executar Flyway. Flyway ocorre no startup da API.

CI de imagem constrói o Dockerfile, verifica UID não root e executa a imagem com profile staging incompleto, provando rejeição antes do datasource. O teste não substitui health/login HTTPS com banco real no staging. Docker local indisponível: evidência de build vem do CI.

O incremento #262 acrescenta um segundo JAR, `/app/staging-provisioner.jar`, para execução explícita conforme [provisionamento sintético](staging-provisioning.md). ENTRYPOINT permanece a API; o provisionador não é executado automaticamente. Não inclui JAR Connector. Sem novos serviços ou deploy só por esse empacotamento. Fontes: [configuração Railway](https://docs.railway.com/config-as-code/reference).
