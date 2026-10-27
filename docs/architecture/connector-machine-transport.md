# Transporte de máquina do FINEMP Connector — Issue #98

## FATO IMPLEMENTADO

`GET /api/connector/v1/session` permite conferir o vínculo de uma credencial vigente: IDs da credencial/contabilidade/fonte e versão. Não retorna o emissor, segredo/hash, empresas adicionais ou dados contábeis. Resposta no-store, sem criação de sessão/cookie.

A cadeia de segurança de `/api/connector/v1/**` é stateless e exclusiva. Exige HTTPS efetivo e um único Authorization Bearer opaco, com tamanho/sintaxe limitados. Cookies/contexto de usuário não são fallback; contexto anterior é descartado e o bearer revalidado pelo verificador #96 a cada chamada. O contexto é limpo ao final e não guarda o segredo em Authentication.

CSRF é dispensado somente nessa cadeia, que não autentica por cookie. A administração e as APIs do browser conservam sessão/CSRF; um bearer de Connector não autentica essas APIs. O único handler deste incremento é leitura de escopo; POST nessa rota não executa trabalho.

HTTP simples, cabeçalho duplicado/inválido, query string e segredo desconhecido/expirado/revogado geram 401 genérico. Falha de infraestrutura gera 503 sanitizado. Nenhuma resposta inclui SQL, erro livre ou material da credencial. Não há token em URL ou redirecionamento automático para transportar um segredo já enviado.

## DECISÃO TÉCNICA

Flags independentes `FINEMP_CONNECTOR_ENABLED` e `FINEMP_CONNECTOR_PROTOCOL_ENABLED`, ambas desligadas por padrão. Protocolo habilitado sem verificador de credencial configurado falha fechado na inicialização. Não há fallback para sessão de usuário.

Limiter local e limitado em memória usa somente hashes de endereço/segredo: padrão 600 chamadas/endereço/minuto, 120/credencial/minuto, até 10.000 chaves; capacidade esgotada recusa novas chaves. 429 tem Retry-After 60s. Esses números são limites técnicos reversíveis, não regras de negócio. Atrás de NAT/proxy, os limites precisam ser dimensionados; rate limit distribuído no gateway permanece trabalho operacional. Nenhum endereço é registrado na documentação ou nos logs deste código.

HTTPS usa `request.isSecure()`. Cabeçalho X-Forwarded-Proto enviado por cliente não é uma prova de TLS. A implantação precisa de TLS no servidor ou terminação em proxy com forwarding/trust restritos e origem do backend inacessível a clientes. Este incremento não habilita confiança indiscriminada em cabeçalhos nem altera configuração de proxy.

## PONTO ABERTO / PRÓXIMO PASSO

Claim/heartbeat, envio/recebimento de lotes e agente local serão incrementos seguintes. A autenticação do vínculo não prova sozinha a identidade no ERP; o adapter deverá ler CNPJ/referência reais e validar a expectativa. Não há alegação de canal operacional homologado ou execução produtiva habilitada.

Persistência dos segredos do agente, distribuição de credenciais, trust TLS, timeouts de driver e observabilidade/gateway precisam de validação de implantação. Esses gates não bloqueiam os módulos independentes. Questor continua somente leitura; FINEMP permanece camada gerencial.
