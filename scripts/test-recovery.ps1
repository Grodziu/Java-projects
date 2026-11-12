. "$PSScriptRoot/env.ps1"
$directory=Join-Path $BidRoomHome ('recovery-test/'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $directory | Out-Null
$classpath=(Join-Path $ProjectRoot 'loadtest/target/loadtest-1.1.1.jar')+';'+(Join-Path $ProjectRoot 'loadtest/target/lib/*')
$output=Join-Path $ProjectRoot 'docs/validation/database-recovery.json'
$arguments='-Xmx128m -cp "'+$classpath+'" pl.edu.bidroom.loadtest.RecoveryCheck "'+$directory+'" "'+$output+'"'
$driver=Start-Process -FilePath "$env:JAVA_HOME/bin/java.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $directory 'driver.log') -RedirectStandardError (Join-Path $directory 'driver-error.log')
$stopped=$false
try {
    $deadline=(Get-Date).AddSeconds(30)
    while(!(Test-Path -LiteralPath (Join-Path $directory 'ready.signal'))) {
        if($driver.HasExited -or (Get-Date) -gt $deadline){throw "Brak gotowości próby. Logi: $directory"}
        Start-Sleep -Milliseconds 200
    }
    & "$PSScriptRoot/stop-db.ps1"
    $stopped=$true
    $deadline=(Get-Date).AddSeconds(15)
    while(!(Test-Path -LiteralPath (Join-Path $directory 'disconnected.signal'))) {
        if((Get-Date) -gt $deadline){throw 'Nie zaobserwowano niedostępności bazy.'}
        Start-Sleep -Milliseconds 200
    }
    & "$PSScriptRoot/start-db.ps1"
    $stopped=$false
    if(!$driver.WaitForExit(45000) -or $driver.ExitCode -ne 0){throw "Próba nie przeszła. Logi: $directory"}
    Get-Content -LiteralPath $output
} finally {
    if($stopped){& "$PSScriptRoot/start-db.ps1"}
    if(!$driver.HasExited){Stop-Process -Id $driver.Id}
}
