# FINEMP Connector — rollout de homologação no cliente

## Escopo

Este runbook prepara a **homologação operacional** do FINEMP Connector em uma máquina que alcança o Questor/Firebird localmente.

Ele não declara o Connector homologado apenas porque o CI ficou verde.

### VALIDADO EM CI

O Backend CI valida que:

- o projeto compila e os testes automatizados passam;
- o JAR standalone do Connector é produzido;
- `java -jar ...-connector.jar --help` executa sem iniciar API, JPA, Flyway ou PostgreSQL;
- em merge verde para `main`, o CI gera um SHA-256 e publica um artifact temporário de homologação.

### AINDA EXIGE HOMOLOGAÇÃO NO CLIENTE

- Firebird/Questor real;
- usuário Firebird somente leitura;
- mapeamento real da identidade da Company;
- acesso de rede local ao Firebird;
- saída HTTPS da máquina para o FINEMP;
- primeiro ciclo claim → heartbeat → snapshot → complete → receipt;
- conferência de contagens e ausência de duplicação;
- start/stop/restart no ambiente operacional escolhido.

## 1. Pré-requisitos

Na máquina do cliente:

- Java 21 disponível para a conta operacional;
- acesso local/rede privada ao Firebird;
- usuário Firebird dedicado e **somente leitura**;
- saída HTTPS permitida apenas para a origem FINEMP esperada;
- diretório local protegido para o Connector;
- conta de serviço definida pelo operador.

Nunca abrir a porta Firebird 3050 para a internet.

## 2. Obter o artifact

Após merge verde na `main`:

1. abrir a execução **Backend CI** correspondente ao commit;
2. baixar o artifact `finemp-connector-<commit>`;
3. extrair em diretório operacional protegido.

O pacote contém:

- `finemp-api-0.0.1-SNAPSHOT-connector.jar`;
- `finemp-api-0.0.1-SNAPSHOT-connector.jar.sha256`;
- `connector.properties.example`;
- documentação operacional.

O artifact é de homologação e possui retenção limitada. Ele não é uma release pública definitiva.

## 3. Verificar integridade

Linux:

```bash
sha256sum -c finemp-api-0.0.1-SNAPSHOT-connector.jar.sha256
```

PowerShell:

```powershell
$expected = (Get-Content .\finemp-api-0.0.1-SNAPSHOT-connector.jar.sha256).Split()[0].ToLower()
$actual = (Get-FileHash .\finemp-api-0.0.1-SNAPSHOT-connector.jar -Algorithm SHA256).Hash.ToLower()
if ($actual -ne $expected) { throw "Checksum do FINEMP Connector inválido." }
```

Checksum confirma integridade do arquivo obtido. Não substitui uma política futura de assinatura de código.

## 4. Preparar configuração local

Copiar `connector.properties.example` para um arquivo local fora do repositório.

O arquivo contém **referências** aos secrets, não seus valores.

Configurar somente valores previamente homologados:

- `source-id`;
- origem HTTPS do FINEMP;
- nomes das referências de ambiente;
- mapeamento real da identidade Questor;
- limites operacionais validados.

Não colocar no properties:

- token;
- senha Firebird;
- usuário Firebird;
- JDBC URL com credencial;
- CNPJ ou dados reais em documentação pública.

Os valores secretos devem ser disponibilizados no ambiente seguro da conta de serviço usando os nomes referenciados pelo properties.

## 5. Validar configuração sem conectar

Executar:

```text
java -jar finemp-api-0.0.1-SNAPSHOT-connector.jar --validate connector.properties
```

Resultado esperado:

```text
Configuração local válida. Conexões e permissões externas não foram testadas.
```

Este passo **não** prova acesso ao Firebird nem ao FINEMP.

## 6. Primeiro run controlado

Antes do primeiro run:

- credencial machine da AccountingSource já provisionada no FINEMP;
- Company/AccountingSource corretas confirmadas;
- Firebird read-only testado pelo operador;
- identidade Questor real mapeada e conferida;
- janela de homologação combinada.

Executar em foreground:

```text
java -jar finemp-api-0.0.1-SNAPSHOT-connector.jar --run connector.properties
```

Não habilitar debug HTTP/JDBC que possa imprimir headers ou propriedades sensíveis.

## 7. Evidências do smoke

Confirmar sem copiar dados reais para Issue/chat:

- perfil iniciou sem expor secrets;
- Connector alcançou somente a origem HTTPS esperada;
- claim foi obtido;
- heartbeat permaneceu vigente;
- identidade observada correspondeu à Company esperada;
- sessão Firebird permaneceu read-only;
- snapshot foi publicado;
- complete/receipt concluíram;
- contagens conferem com o escopo controlado;
- reexecução não duplicou movimentos;
- falha controlada não corrompeu estado;
- logs permanecem sanitizados.

Registrar na #310 somente o resultado sanitizado.

## 8. Stop e restart durante homologação

No primeiro smoke, o processo deve permanecer em foreground para que stop/restart sejam observáveis.

Stop:

- enviar interrupção normal ao processo;
- aguardar encerramento;
- confirmar que o lock local foi liberado pelo processo/SO.

Restart:

- iniciar novamente com o mesmo properties protegido;
- confirmar novo claim vigente;
- verificar que fencing/idempotência impedem reaproveitamento indevido de tentativa antiga.

A instalação permanente como serviço do sistema operacional deve usar o mecanismo aprovado pelo ambiente do cliente. Este repositório ainda não declara um service manager específico como padrão.

## 9. Critério para chamar de homologado

Somente marcar o Connector como homologado quando os itens da #310 forem executados no ambiente real e a evidência sanitizada estiver registrada.

Até lá, a formulação correta é:

> Connector validado sinteticamente e empacotado para homologação; integração Questor real ainda pendente.
