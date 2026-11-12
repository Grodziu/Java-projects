param([ValidateSet('Start','Server','Client','Stop')][string]$Action='Start')
$ErrorActionPreference='Stop'
. "$PSScriptRoot/env.ps1"
if($Action -eq 'Client') {
    & "$PSScriptRoot/assert-server.ps1"
    & "$PSScriptRoot/start-client.ps1"
    return
}
if($Action -eq 'Stop') {
    & "$PSScriptRoot/stop-server.ps1"
    & "$PSScriptRoot/stop-db.ps1"
    Write-Output 'Zatrzymano usługi BidRoom. Okno klienta można zamknąć osobno.'
    return
}
& "$PSScriptRoot/start-db.ps1"
Import-BidRoomSecrets
$serverIdFile=Join-Path $env:BIDROOM_DATA_DIR 'server.pid'
$running=$false
if(Test-Path -LiteralPath $serverIdFile) {
    $savedId=[int](Get-Content -LiteralPath $serverIdFile)
    $process=Get-Process -Id $savedId -ErrorAction SilentlyContinue
    if($process -and $process.ProcessName -eq 'java') {
        $information=Get-CimInstance Win32_Process -Filter "ProcessId=$savedId"
        $running=$information.CommandLine.Contains($env:BIDROOM_DATA_DIR)
    }
}
if(!$running){& "$PSScriptRoot/start-server.ps1"}
& "$PSScriptRoot/assert-server.ps1"
Write-Output 'SERWER GOTOWY. PostgreSQL i serwer BidRoom dzialaja. Mozesz teraz uruchomic 2-Start-klienta.cmd.'
if($Action -eq 'Server'){return}
& "$PSScriptRoot/start-client.ps1"
