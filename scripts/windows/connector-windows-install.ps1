param(
    [Parameter(Mandatory = $true)]
    [string]$JavaExePath,

    [Parameter(Mandatory = $true)]
    [string]$ConnectorJarPath,

    [Parameter(Mandatory = $true)]
    [string]$ConfigPath,

    [string]$TaskName = "FINEMP Connector",

    [string]$RuntimeRoot = "$env:ProgramData\FINEMP\Connector"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-FullPath([string]$Path) {
    return [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $Path).Path)
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Get-ReferencedSecretNames([string]$PropertiesPath) {
    $names = @()
    foreach ($line in Get-Content -LiteralPath $PropertiesPath) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) { continue }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) {
            throw "Formato não suportado no connector.properties. Use somente chave=valor nesta distribuição Windows."
        }

        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($key.EndsWith("-env", [System.StringComparison]::Ordinal)) {
            if ($value -notmatch '^FINEMP_CONNECTOR_[A-Z0-9_]{1,100}$') {
                throw "Referência de ambiente inválida no connector.properties."
            }
            $names += $value
        }
    }
    return @($names | Sort-Object -Unique)
}

function Protect-FileForCurrentAccount([string]$Path, [string]$Identity) {
    & icacls.exe $Path "/inheritance:r" "/grant:r" "${Identity}:(F)" "/grant:r" "SYSTEM:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível proteger o arquivo local do Connector."
    }
}

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw "A instalação automática do FINEMP Connector exige Windows."
}

$java = Resolve-FullPath $JavaExePath
$jar = Resolve-FullPath $ConnectorJarPath
$config = Resolve-FullPath $ConfigPath

$javaProbe = New-Object System.Diagnostics.ProcessStartInfo
$javaProbe.FileName = $java
$javaProbe.Arguments = "-version"
$javaProbe.UseShellExecute = $false
$javaProbe.RedirectStandardOutput = $true
$javaProbe.RedirectStandardError = $true
$javaProbe.CreateNoWindow = $true

$javaProcess = New-Object System.Diagnostics.Process
$javaProcess.StartInfo = $javaProbe
[void]$javaProcess.Start()
$javaStdout = $javaProcess.StandardOutput.ReadToEnd()
$javaStderr = $javaProcess.StandardError.ReadToEnd()
$javaProcess.WaitForExit()
$versionOutput = $javaStdout + $javaStderr

if ($javaProcess.ExitCode -ne 0 -or $versionOutput -notmatch 'version "21(?:\.|")') {
    throw "O FINEMP Connector exige Java 21 no caminho informado."
}

$currentIdentity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
if ([string]::IsNullOrWhiteSpace($currentIdentity)) {
    throw "Não foi possível identificar a conta Windows atual."
}

$secretNames = Get-ReferencedSecretNames $config
if ($secretNames.Count -eq 0) {
    throw "Nenhuma referência FINEMP_CONNECTOR_* foi encontrada na configuração."
}

$sealedSecrets = @{}
$missing = @()
foreach ($name in $secretNames) {
    $value = [Environment]::GetEnvironmentVariable($name, "Process")
    if ([string]::IsNullOrEmpty($value)) {
        $missing += $name
        continue
    }
    $sealedSecrets[$name] = ConvertTo-SecureString $value -AsPlainText -Force
}

if ($missing.Count -gt 0) {
    throw ("Variáveis locais ausentes: " + ($missing -join ", ") + ". Carregue-as nesta sessão sem exibir os valores e execute novamente.")
}

$validation = (& $java -jar $jar --validate $config 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw "O Connector recusou a configuração local. Corrija o --validate antes de instalar o runtime automático."
}

New-Item -ItemType Directory -Force -Path $RuntimeRoot | Out-Null
$logRoot = Join-Path $RuntimeRoot "logs"
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$runnerSource = Join-Path $scriptRoot "connector-windows-run.ps1"
$controlSource = Join-Path $scriptRoot "connector-windows-control.ps1"
if (-not (Test-Path -LiteralPath $runnerSource) -or -not (Test-Path -LiteralPath $controlSource)) {
    throw "Distribuição incompleta: scripts de runtime Windows ausentes."
}

$runnerPath = Join-Path $RuntimeRoot "connector-windows-run.ps1"
$controlPath = Join-Path $RuntimeRoot "connector-windows-control.ps1"
Copy-Item -LiteralPath $runnerSource -Destination $runnerPath -Force
Copy-Item -LiteralPath $controlSource -Destination $controlPath -Force

$secretPath = Join-Path $RuntimeRoot "connector-secrets.clixml"
$sealedSecrets | Export-Clixml -LiteralPath $secretPath -Force
Protect-FileForCurrentAccount $secretPath $currentIdentity

$runtimeConfigPath = Join-Path $RuntimeRoot "connector-windows-runtime.json"
$runtimeConfig = [ordered]@{
    version = 1
    javaExePath = $java
    connectorJarPath = $jar
    connectorPropertiesPath = $config
    secretsPath = $secretPath
    logDirectory = $logRoot
}
Write-Utf8NoBom $runtimeConfigPath ($runtimeConfig | ConvertTo-Json -Depth 3)
Protect-FileForCurrentAccount $runtimeConfigPath $currentIdentity

$actionArgs = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$runnerPath`" -RuntimeConfigPath `"$runtimeConfigPath`""
$action = New-ScheduledTaskAction -Execute "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -Argument $actionArgs -WorkingDirectory $RuntimeRoot
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)

$accountPassword = Read-Host "Senha da conta Windows $currentIdentity para execução sem login" -AsSecureString
$bstr = [IntPtr]::Zero
$plainPassword = $null
try {
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($accountPassword)
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -User $currentIdentity -Password $plainPassword -RunLevel Highest -Force | Out-Null
} finally {
    $plainPassword = $null
    if ($bstr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

Start-ScheduledTask -TaskName $TaskName
Write-Host "FINEMP Connector instalado para inicialização automática."
Write-Host "Conta operacional: $currentIdentity"
Write-Host "Controle: $controlPath -Action Status"
