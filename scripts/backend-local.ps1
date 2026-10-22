$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot ".env.local"
$backendDir = Join-Path $projectRoot "backend"

if (-not (Test-Path $envFile)) {
    Write-Error ".env.local não encontrado. Copie .env.example para .env.local e configure os valores."
}

$loadedVariables = @{}

Get-Content $envFile |
    Where-Object {
        $_.Trim() -ne "" -and
        -not $_.Trim().StartsWith("#")
    } |
    ForEach-Object {

        $parts = $_ -split "=", 2

        if ($parts.Length -ne 2) {
            return
        }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()

        $loadedVariables[$name] =
            [Environment]::GetEnvironmentVariable(
                $name,
                "Process"
            )

        [Environment]::SetEnvironmentVariable(
            $name,
            $value,
            "Process"
        )
    }

Push-Location $backendDir

try {
    ./mvnw spring-boot:run
}
finally {
    Pop-Location

    foreach ($name in $loadedVariables.Keys) {
        [Environment]::SetEnvironmentVariable(
            $name,
            $loadedVariables[$name],
            "Process"
        )
    }
}