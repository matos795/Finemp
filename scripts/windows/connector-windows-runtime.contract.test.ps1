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
    'Export-Clixml',
    'WindowsIdentity]::GetCurrent()',
    'version "21',
    'Register-ScheduledTask'
)
foreach ($needle in $installerRequired) {
    if (-not $installer.Contains($needle)) {
        throw "Installer contract missing: $needle"
    }
}

if ($installer -match '(?i)sc\.exe') {
    throw "Installer must not register java.exe directly with sc.exe."
}

if ($installer -match '(?i)PASSWORD\s*=\s*["''][^"'']+["'']') {
    throw "Installer appears to contain a hard-coded password."
}

$runnerRequired = @(
    'Import-Clixml',
    'SetEnvironmentVariable($Name, $value, "Process")',
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

foreach ($action in @('"Start"', '"Stop"', '"Restart"', '"Status"', '"Uninstall"')) {
    if (-not $control.Contains($action)) {
        throw "Control contract missing action: $action"
    }
}

Write-Host "Windows Connector runtime contract OK."
