param([switch]$Foreground)
. "$PSScriptRoot/env.ps1"
Import-BidRoomSecrets
$jar = Join-Path $ProjectRoot 'server/target/server-1.1.1.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Najpierw wykonaj ./mvnw.cmd package.' }
$argsList = @('-Xms64m','-Xmx256m','-Dfile.encoding=UTF-8','-jar',$jar)
if ($Foreground) { & "$env:JAVA_HOME/bin/java.exe" @argsList; exit $LASTEXITCODE }
New-Item -ItemType Directory -Force -Path $env:BIDROOM_DATA_DIR | Out-Null
$pidFile = Join-Path $env:BIDROOM_DATA_DIR 'server.pid'
if (Test-Path -LiteralPath $pidFile) {
    $existingId = [int](Get-Content -LiteralPath $pidFile)
    if (Get-Process -Id $existingId -ErrorAction SilentlyContinue) { throw 'Zapisany proces serwera nadal działa. Użyj stop-server.ps1.' }
}
$arguments = '-Xms64m -Xmx256m -Dfile.encoding=UTF-8 -jar "' + $jar + '"'
$runtimeJar=Join-Path $env:BIDROOM_DATA_DIR ('server-'+[Guid]::NewGuid().ToString('N')+'.jar')
Copy-Item -LiteralPath $jar -Destination $runtimeJar
$arguments = '-Xms64m -Xmx256m -Dfile.encoding=UTF-8 -jar "' + $runtimeJar + '"'
$process = Start-Process -FilePath "$env:JAVA_HOME/bin/java.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:BIDROOM_DATA_DIR 'console.log') -RedirectStandardError (Join-Path $env:BIDROOM_DATA_DIR 'console-error.log')
$process.Id | Set-Content -LiteralPath $pidFile
$deadline = (Get-Date).AddSeconds(30)
do {
    if ($process.HasExited) { throw 'Serwer zakończył działanie. Sprawdź console-error.log w USERPROFILE/BidRoomData/server.' }
    if (Select-String -LiteralPath (Join-Path $env:BIDROOM_DATA_DIR 'console.log') -Pattern BIDROOM_READY -Quiet) { Write-Output "Serwer gotowy, PID $($process.Id)."; return }
    Start-Sleep -Milliseconds 300
} while ((Get-Date) -lt $deadline)
throw 'Brak gotowości w 30 s. Proces pozostaje do diagnostyki; sprawdź log serwera.'
