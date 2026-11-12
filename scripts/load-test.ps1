. "$PSScriptRoot/env.ps1"
Import-BidRoomSecrets
$env:BIDROOM_DB_URL='jdbc:postgresql://127.0.0.1:55432/bidroom_test'
$env:BIDROOM_PORT='5002'
$env:BIDROOM_DATA_DIR=Join-Path $BidRoomHome 'loadtest-server'
New-Item -ItemType Directory -Force -Path $env:BIDROOM_DATA_DIR,(Join-Path $ProjectRoot 'docs/validation') | Out-Null
$jar=Join-Path $ProjectRoot 'server/target/server-1.1.1.jar'
$metrics=Join-Path $ProjectRoot 'docs/validation/loadtest-server.jsonl'
if (Test-Path -LiteralPath $metrics) { Clear-Content -LiteralPath $metrics }
$server=Start-Process -FilePath "$env:JAVA_HOME/bin/java.exe" -ArgumentList ('-Xms64m -Xmx256m -Dfile.encoding=UTF-8 "-Dbidroom.metricsFile='+$metrics+'" -jar "'+$jar+'"') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:BIDROOM_DATA_DIR 'console.log') -RedirectStandardError (Join-Path $env:BIDROOM_DATA_DIR 'error.log')
try {
    $deadline=(Get-Date).AddSeconds(25)
    while (!(Select-String -LiteralPath (Join-Path $env:BIDROOM_DATA_DIR 'console.log') -Pattern BIDROOM_READY -Quiet)) {
        if ($server.HasExited -or (Get-Date) -gt $deadline) { throw 'Serwer pomiarowy nie osiągnął gotowości.' }
        Start-Sleep -Milliseconds 250
    }
    $output=Join-Path $ProjectRoot 'docs/validation/loadtest-result.json'
    $classpath=(Join-Path $ProjectRoot 'loadtest/target/loadtest-1.1.1.jar')+';'+(Join-Path $ProjectRoot 'loadtest/target/lib/*')
    $driverArgs='-Xms32m -Xmx128m -cp "'+$classpath+'" pl.edu.bidroom.loadtest.LoadTest --confirm-local-test 5002 10 30 "'+$output+'"'
    $driver=Start-Process -FilePath "$env:JAVA_HOME/bin/java.exe" -ArgumentList $driverArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:BIDROOM_DATA_DIR 'driver.log') -RedirectStandardError (Join-Path $env:BIDROOM_DATA_DIR 'driver-error.log')
    $samples=[Collections.Generic.List[object]]::new()
    $deadline=(Get-Date).AddSeconds(90)
    while (!$driver.HasExited) {
        if ((Get-Date) -gt $deadline) { Stop-Process -Id $driver.Id; throw 'Test przekroczył 90 s.' }
        $server.Refresh();$driver.Refresh()
        $samples.Add([pscustomobject]@{ time=(Get-Date).ToUniversalTime().ToString('o');serverWorkingSetBytes=$server.WorkingSet64;serverCpuSeconds=$server.TotalProcessorTime.TotalSeconds;driverWorkingSetBytes=$driver.WorkingSet64 })
        Start-Sleep -Seconds 1
    }
    $samples | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ProjectRoot 'docs/validation/loadtest-processes.json') -Encoding utf8
    Get-Content -LiteralPath (Join-Path $env:BIDROOM_DATA_DIR 'driver.log')
    if ($driver.ExitCode -ne 0) { throw 'Test obciążenia zgłosił błąd.' }
} finally {
    New-Item -ItemType File -Force -Path (Join-Path $env:BIDROOM_DATA_DIR 'stop.signal') | Out-Null
    if (!$server.WaitForExit(20000)) { Write-Warning 'Serwer pomiarowy nie zakończył się w 20 s. Pozostawiono do diagnostyki.' }
}
