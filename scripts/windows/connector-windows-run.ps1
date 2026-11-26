param(
    [Parameter(Mandatory = $true)]
    [string]$RuntimeConfigPath,

    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$runtime = $null
$secretNames = @()

function Set-ProcessSecret([string]$Name, [Security.SecureString]$Secret) {
    $bstr = [IntPtr]::Zero
    try {
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
        $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        [Environment]::SetEnvironmentVariable($Name, $value, "Process")
        $value = $null
    } finally {
        if ($bstr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }
}

function Clear-ProcessSecrets([string[]]$Names) {
    foreach ($name in $Names) {
        [Environment]::SetEnvironmentVariable($name, $null, "Process")
    }
}

try {
    $runtime = Get-Content -LiteralPath $RuntimeConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($runtime.version -ne 1) { throw "Unsupported runtime config." }

    $secrets = Import-Clixml -LiteralPath $runtime.secretsPath
    $secretNames = @($secrets.Keys)
    foreach ($name in $secretNames) {
        if ($name -notmatch '^FINEMP_CONNECTOR_[A-Z0-9_]{1,100}$') {
            throw "Invalid secret reference."
        }
        Set-ProcessSecret $name $secrets[$name]
    }

    if ($ValidateOnly) {
        & $runtime.javaExePath -jar $runtime.connectorJarPath --validate $runtime.connectorPropertiesPath
        $exitCode = $LASTEXITCODE
        Clear-ProcessSecrets $secretNames
        exit $exitCode
    }

    New-Item -ItemType Directory -Force -Path $runtime.logDirectory | Out-Null
    $logPath = Join-Path $runtime.logDirectory ("connector-" + (Get-Date -Format "yyyyMMdd") + ".log")
    Add-Content -LiteralPath $logPath -Value ("[" + (Get-Date -Format "s") + "] runtime-start")

    & $runtime.javaExePath -jar $runtime.connectorJarPath --run $runtime.connectorPropertiesPath *>> $logPath
    $exitCode = $LASTEXITCODE

    Add-Content -LiteralPath $logPath -Value ("[" + (Get-Date -Format "s") + "] runtime-exit code=" + $exitCode)
    Clear-ProcessSecrets $secretNames
    exit $exitCode
} catch {
    try {
        if ($null -ne $secretNames) { Clear-ProcessSecrets $secretNames }
        if ($null -ne $runtime -and $null -ne $runtime.logDirectory) {
            New-Item -ItemType Directory -Force -Path $runtime.logDirectory | Out-Null
            Add-Content -LiteralPath (Join-Path $runtime.logDirectory ("connector-" + (Get-Date -Format "yyyyMMdd") + ".log")) -Value ("[" + (Get-Date -Format "s") + "] runtime-failed")
        }
    } catch { }
    exit 3
}
