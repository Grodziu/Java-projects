. "$PSScriptRoot/env.ps1"
Import-BidRoomSecrets
New-Item -ItemType File -Force -Path (Join-Path $env:BIDROOM_DATA_DIR 'stop.signal') | Out-Null
$pidFile=Join-Path $env:BIDROOM_DATA_DIR 'server.pid'
if (Test-Path -LiteralPath $pidFile) {
    $serverProcessId=[int](Get-Content -LiteralPath $pidFile)
    Wait-Process -Id $serverProcessId -Timeout 20 -ErrorAction SilentlyContinue
    if (Get-Process -Id $serverProcessId -ErrorAction SilentlyContinue) { throw 'Serwer nie zakończył się w limicie. Sprawdź logi.' }
    Remove-Item -LiteralPath $pidFile
}
Write-Output 'Wysłano żądanie kontrolowanego zatrzymania.'
