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
- documentação operacional;
- `windows/connector-windows-install.ps1`;
- `windows/connector-windows-run.ps1`;
- `windows/connector-windows-control.ps1`.

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
- mapeamento real da identidade Questor, incluindo estabelecimento fiscal quando a tabela não for 1:1 por empresa;
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

## 9. Runtime automático no Windows

### DECISÃO

A primeira instalação permanente suportada usa o **Agendador de Tarefas do Windows**. O Connector continua sendo o mesmo JAR Java 21; não é registrado com `sc.exe`, porque uma JVM comum não implementa o protocolo do Windows Service Control Manager. Também não é adicionado wrapper executável de terceiros nesta fase.

O task:
- dispara em `AtStartup`;
- roda sob a mesma conta Windows que executou o instalador;
- pode executar mesmo sem sessão interativa;
- usa Java 21 por caminho explícito, sem alterar o Java 8/PATH/JAVA_HOME do Questor;
- ignora segunda instância do próprio task e o Connector mantém o lock por AccountingSource;
- reinicia após saída com falha conforme política do Task Scheduler.

### Secrets locais

Os valores referenciados por `*-env` no `connector.properties` precisam existir apenas no ambiente do processo que executa o instalador. O instalador os converte para `SecureString` e grava `connector-secrets.clixml`.

No Windows, `Export-Clixml` protege `SecureString` com DPAPI da conta atual. Por isso:
- execute o instalador **logado como a conta operacional que executará o Connector**;
- outra conta Windows não deve conseguir reaproveitar o arquivo;
- o arquivo recebe ACL restrita à conta operacional e SYSTEM;
- o task não recebe token, senha ou JDBC em argumentos;
- o bootstrap recupera os secrets somente para o ambiente do processo Java.

O ambiente local continua não sendo declarado cofre definitivo; a política de vault pode evoluir sem alterar o protocolo Connector.

### Instalação

Em PowerShell elevado, com as variáveis `FINEMP_CONNECTOR_*` já carregadas na sessão e sem exibir seus valores:

```powershell
.\windows\connector-windows-install.ps1 `
  -JavaExePath "C:\caminho\java21\bin\java.exe" `
  -ConnectorJarPath "C:\caminho\finemp-api-0.0.1-SNAPSHOT-connector.jar" `
  -ConfigPath "C:\caminho\connector.properties"
```

O instalador:
1. confirma Windows e Java 21;
2. executa `--validate`;
3. sela as referências de ambiente por DPAPI;
4. registra o task `FINEMP Connector` em `AtStartup`;
5. inicia o task.

A senha solicitada é a senha da conta Windows atual para o Task Scheduler armazenar a credencial de logon; ela não é escrita em arquivo nem passada para o Java.

### Controle

```powershell
C:\ProgramData\FINEMP\Connector\connector-windows-control.ps1 -Action Status
C:\ProgramData\FINEMP\Connector\connector-windows-control.ps1 -Action Start
C:\ProgramData\FINEMP\Connector\connector-windows-control.ps1 -Action Stop
C:\ProgramData\FINEMP\Connector\connector-windows-control.ps1 -Action Restart
```

Uninstall remove apenas o task e preserva configuração runtime, secrets DPAPI e logs para rollback/auditoria:

```powershell
C:\ProgramData\FINEMP\Connector\connector-windows-control.ps1 -Action Uninstall
```

### Homologação obrigatória no Windows real

Antes de marcar este mecanismo como aceito:
- `Status` deve mostrar task em execução;
- stop precisa encerrar o Connector e liberar o lock;
- start/restart precisam recuperar polling;
- logout do usuário não pode interromper o Connector;
- reboot precisa iniciar o Connector sem login interativo;
- falha do processo deve ser observável em `LastTaskResult`/log e reiniciada pela política;
- Java 8 do Questor deve permanecer inalterado;
- secrets não podem aparecer na linha de comando nem nos logs.

## 10. Critério para chamar de homologado

Somente marcar o Connector como homologado quando os itens da #310 forem executados no ambiente real e a evidência sanitizada estiver registrada.

Até lá, a formulação correta é:

> Fluxo Questor/Firebird real homologado para leitura e publicação idempotente; runtime automático Windows ainda depende do aceite operacional de start/stop/restart/logout/reboot.
