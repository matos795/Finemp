# FINEMP Connector — runtime local opt-in

## DECISÃO

O artefato `finemp-api-0.0.1-SNAPSHOT-connector.jar` tem entry point próprio e não chama SpringApplication: não inicializa servidor HTTP, JPA, Flyway ou PostgreSQL. A aplicação API mantém seu entry point e artefato principal. O classifier separado é produzido a partir de um JAR próprio antes do repackage, conforme a [documentação oficial do Spring Boot](https://docs.spring.io/spring-boot/4.0/maven-plugin/packaging.html#goals-repackage-parameters-details-classifier). A CI verifica `--help` do artefato empacotado; não executa `--run` contra ambiente operacional.

```text
java -jar finemp-api-0.0.1-SNAPSHOT-connector.jar --help
java -jar finemp-api-0.0.1-SNAPSHOT-connector.jar --validate connector.properties
java -jar finemp-api-0.0.1-SNAPSHOT-connector.jar --run connector.properties
```

`--validate` verifica configuração e disponibilidade/formato dos segredos locais, sem testar conexão/permissões remotas. `--run` é a ação explícita que inicia polling e leitura. Saída0 em validação/help/interrupt;2 em configuração/argumentos inválidos;3 quando todos os perfis foram interrompidos. Não passar token, senha, JDBC URL ou login como argumento. Não há instalação automática de serviço nem deploy incluído.

## CONFIGURAÇÃO LOCAL CONFIÁVEL

Copiar `connector.properties.example` e substituir identificadores/mapeamento com os valores homologados localmente. O exemplo é sintético e não declara nomes reais de tabelas de identidade do Questor. Arquivo aceita versão1, até8 perfis, 32KiB e somente as chaves conhecidas; duplicatas e chaves desconhecidas falham fechado. AccountingSource e referência de token precisam ser únicos. Token não pode compartilhar referência com nenhum segredo JDBC. Origem é HTTPS fixa, sem credencial/query/fragmento/caminho arbitrário; redirects são recusados.

Valores de token/JDBC ficam no ambiente da conta de serviço, referenciados por nomes `FINEMP_CONNECTOR_*`. Não são escritos no properties, repositório, argumentos ou logs. Referências são resolvidas novamente por pedido/conexão; o ambiente herdado do processo exige reinício para mudança externa. A porta ConnectorSecretSource permite futura integração com cofre local sem alterar protocolo. Ambiente não é declarado cofre definitivo. Configuração, ambiente, trust-store e diretório da conta de serviço devem ser protegidos pelo operador; não habilitar debug HTTP/JDBC que exponha headers/propriedades.

O factory sempre compõe `VerifiedQuestorSnapshotReader`: identidade efetivamente observada e movimentos na mesma sessão solicitada read-only, comparados antes do envio. Conexão não é aberta no startup/validate. Sem mapeamento real comprovado, o perfil interrompe na leitura; não ecoa CNPJ esperado nem troca para reader legado. Permissões do usuário Firebird e limites do driver precisam de homologação operacional.

## EXCLUSIVIDADE, ISOLAMENTO E SHUTDOWN

Lock de arquivo mantido pelo SO em `<user.home>/.finemp/connector/locks/<source-id>.lock`. O diretório é fixo por conta de serviço, não vem do SaaS/properties. Arquivo vazio nunca é apagado: isso evita troca de inode durante disputa. Perfil em outro processo da mesma conta/diretório não pode adquirir o mesmo lock. Contas de serviço distintas e hosts distintos exigem coordenação operacional; não se afirma exclusividade global por esse lock. Fencing de tentativas continua obrigatório no servidor.

Há um poller por perfil com atraso após o ciclo, sem acumular tarefas a taxa fixa; cada ciclo mantém um reader e um heartbeat no máximo. Perfil com segredo/lock indisponível é isolado; os demais continuam. Configuração estrutural inválida é rejeitada antes do startup. Runtime informa apenas contagem de perfis localmente prontos, sem credenciais ou diagnósticos privados. Isso não significa conexão externa homologada.

`close` cancela o ciclo e o transporte. Se um driver ignora interrupt, o lock continua retido até os workers realmente terminarem; thread de liberação é limitada a uma por perfil fechado. Não se cria leitor substituto para contornar BUSY. Encerramento de processo libera locks pelo SO; ele não é declarado retomada de bytes. Estado da tentativa/lease/idempotência permanece no FINEMP, e o próximo processo precisa obter novo claim vigente.

## PONTOS ABERTOS OPERACIONAIS

Distribuição/assinatura do binário, política de atualização, cofre definitivo, conta de serviço, configuração real do schema, permissões Firebird, prazos de socket/login e homologação de rede ainda dependem do ambiente de destino. Nenhuma credencial ou instalação real está embutida. Questor continua somente leitura para o FINEMP. Incremental/retificação/cursor não foram inventados; o ciclo atual executa INITIAL.
