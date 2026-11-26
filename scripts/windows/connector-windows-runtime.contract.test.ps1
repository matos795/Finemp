Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$paths = @(
    "scripts/windows/connector-windows-install.ps1",
    "scripts/windows/connector-windows-run.ps1",
    "scripts/windows/connector-windows-control.ps1"
)

foreach ($relative in $paths) {
    $path = Join-Path $repoRoot $relative
    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
    if ($errors.Count -gt 0) {
        throw ("PowerShell syntax error in " + $relative + ": " + $errors[0].Message)
    }
}

$installer = Get-Content -LiteralPath (Join-Path $repoRoot "scripts/windows/connector-windows-install.ps1") -Raw
$runner = Get-Content -LiteralPath (Join-Path $repoRoot "scripts/windows/connector-windows-run.ps1") -Raw
$control = Get-Content -LiteralPath (Join-Path $repoRoot "scripts/windows/connector-windows-control.ps1") -Raw

$installerRequired = @(
    'New-ScheduledTaskTrigger -AtStartup',
    '-MultipleInstances IgnoreNew',
    '-RestartCount 5',
    '-AllowStartIfOnBatteries',
    '-DontStopIfGoingOnBatteries',
    'Export-Clixml',
    'WindowsIdentity]::GetCurrent()',
    'System.Diagnostics.ProcessStartInfo',
    'Formato não suportado no connector.properties. Use somente chave=valor nesta distribuição Windows.',
    '-ExecutionPolicy Bypass',
    'version "21',
    'Register-ScheduledTask',
    'Copy-Item -LiteralPath $jar -Destination $runtimeJarPath -Force',
    'Copy-Item -LiteralPath $config -Destination $runtimePropertiesPath -Force',
    'connectorJarPath = $runtimeJarPath',
    'connectorPropertiesPath = $runtimePropertiesPath',
    'Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue',
    'Protect-DirectoryForCurrentAccount $RuntimeRoot $currentIdentity',
    'Protect-FileForCurrentAccount $runnerPath $currentIdentity',
    '*S-1-5-32-544:(F)'
)
foreach ($needle in $installerRequired) {
    if (-not $installer.Contains($needle)) {
        throw "Installer contract missing: $needle"
    }
}


$distributionCheck = 'Distribuição incompleta: scripts de runtime Windows ausentes.'
$stopExistingTask = 'Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue'
if ($installer.IndexOf($distributionCheck, [System.StringComparison]::Ordinal) -lt 0) {
    throw "Installer contract missing distribution completeness check."
}
if ($installer.IndexOf($distributionCheck, [System.StringComparison]::Ordinal) -gt
    $installer.IndexOf($stopExistingTask, [System.StringComparison]::Ordinal)) {
    throw "Installer must validate the complete distribution before inspecting/stopping an installed task."
}

$validateConfig = '& $java -jar $jar --validate $config'
if ($installer.IndexOf($validateConfig, [System.StringComparison]::Ordinal) -lt 0 -or
    $installer.IndexOf($validateConfig, [System.StringComparison]::Ordinal) -gt
    $installer.IndexOf($stopExistingTask, [System.StringComparison]::Ordinal)) {
    throw "Installer must validate the new connector configuration before inspecting/stopping an installed task."
}

if ($installer -match '(?i)sc\.exe') {
    throw "Installer must not register java.exe directly with sc.exe."
}

if ($installer -match '(?i)PASSWORD\s*=\s*["''][^"'']+["'']') {
    throw "Installer appears to contain a hard-coded password."
}

$runnerRequired = @(
    'Import-Clixml',
    'Get-Content -LiteralPath $RuntimeConfigPath -Raw -Encoding UTF8',
    'SetEnvironmentVariable($Name, $value, "Process")',
    '[switch]$ValidateOnly',
    '-jar $runtime.connectorJarPath --validate $runtime.connectorPropertiesPath',
    '-jar $runtime.connectorJarPath --run $runtime.connectorPropertiesPath',
    'Clear-ProcessSecrets'
)
foreach ($needle in $runnerRequired) {
    if (-not $runner.Contains($needle)) {
        throw "Runner contract missing: $needle"
    }
}

if ($runner -match '(?i)FINEMP_CONNECTOR_[A-Z0-9_]+\s*=') {
    throw "Runner must not embed connector secret values."
}

$validateOnly = '-jar $runtime.connectorJarPath --validate $runtime.connectorPropertiesPath'
$importSecrets = 'Import-Clixml'
if ($runner.IndexOf($validateOnly, [System.StringComparison]::Ordinal) -lt 0 -or
    $runner.IndexOf($importSecrets, [System.StringComparison]::Ordinal) -gt
    $runner.IndexOf($validateOnly, [System.StringComparison]::Ordinal)) {
    throw "ValidateOnly must reuse the installed DPAPI secret environment before Connector --validate."
}

foreach ($action in @('"Start"', '"Stop"', '"Restart"', '"Status"', '"Uninstall"')) {
    if (-not $control.Contains($action)) {
        throw "Control contract missing action: $action"
    }
}

Write-Host "Windows Connector runtime contract OK."
