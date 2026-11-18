param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Start", "Stop", "Restart", "Status", "Uninstall")]
    [string]$Action,

    [string]$TaskName = "FINEMP Connector"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

switch ($Action) {
    "Start" {
        Start-ScheduledTask -TaskName $TaskName
        Write-Host "FINEMP Connector iniciado."
    }
    "Stop" {
        Stop-ScheduledTask -TaskName $TaskName
        Write-Host "FINEMP Connector interrompido."
    }
    "Restart" {
        Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
        Start-ScheduledTask -TaskName $TaskName
        Write-Host "FINEMP Connector reiniciado."
    }
    "Status" {
        $task = Get-ScheduledTask -TaskName $TaskName
        $info = Get-ScheduledTaskInfo -TaskName $TaskName
        [pscustomobject]@{
            TaskName = $TaskName
            State = $task.State
            LastRunTime = $info.LastRunTime
            LastTaskResult = $info.LastTaskResult
            NextRunTime = $info.NextRunTime
        } | Format-List
    }
    "Uninstall" {
        Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Write-Host "Task removida. Configuração, secrets DPAPI e logs locais foram preservados para rollback/auditoria."
    }
}
