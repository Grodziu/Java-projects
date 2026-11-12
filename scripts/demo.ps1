. "$PSScriptRoot/env.ps1"
$directory=Join-Path $BidRoomHome ('demo/'+[Guid]::NewGuid().ToString('N'))
$output=Join-Path $ProjectRoot 'docs/screenshots/demo'
New-Item -ItemType Directory -Force -Path $directory,$output | Out-Null
$loadCp=(Join-Path $ProjectRoot 'loadtest/target/loadtest-1.1.1.jar')+';'+(Join-Path $ProjectRoot 'loadtest/target/lib/*')
& "$env:JAVA_HOME/bin/java.exe" -Xmx128m -cp $loadCp pl.edu.bidroom.loadtest.DemoSetup 5001 (Join-Path $directory 'scenario.json')
if ($LASTEXITCODE -ne 0) { throw 'Nie przygotowano pokazu.' }
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe (Join-Path $directory 'scenario.json') '/inheritance:r' '/grant:r' "${identity}:(F)" 'SYSTEM:(F)' | Out-Null
$clientCp=(Join-Path $ProjectRoot 'client/target/test-classes')+';'+(Join-Path $ProjectRoot 'client/target/classes')+';'+(Join-Path $ProjectRoot 'client/target/lib/*')
$clients=@()
try {
    foreach($index in 0..2) {
        $argsLine='-Xms32m -Xmx192m "-Dbidroom.demoDir='+$directory+'" "-Dbidroom.demoOutput='+$output+'" -Dbidroom.demoIndex='+$index+' -cp "'+$clientCp+'" pl.edu.bidroom.client.DemoClient'
        $clients+=Start-Process -FilePath "$env:JAVA_HOME/bin/javaw.exe" -ArgumentList $argsLine -WindowStyle Normal -PassThru -RedirectStandardOutput (Join-Path $directory "client-$index.log") -RedirectStandardError (Join-Path $directory "client-$index-error.log")
        Start-Sleep -Milliseconds 500
    }
    $deadline=(Get-Date).AddSeconds(30)
    while(@(Get-ChildItem -LiteralPath $directory -Filter 'ready-*').Count -lt 3) {
        if ((Get-Date) -gt $deadline) { throw "Nie wszystkie okna osiągnęły gotowość. Logi: $directory" }
        Start-Sleep -Milliseconds 200
    }
    $clients | ForEach-Object { $_.Refresh(); [pscustomobject]@{pid=$_.Id;title=$_.MainWindowTitle;workingSetBytes=$_.WorkingSet64} } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'three-processes.json') -Encoding utf8
    New-Item -ItemType File -Path (Join-Path $directory 'go.signal') | Out-Null
    foreach($client in $clients) { if(!$client.WaitForExit(30000)) { throw 'Klient pokazu przekroczył czas.' } }
    foreach($index in 0..2) { if(!(Select-String -LiteralPath (Join-Path $directory "client-$index.log") -Pattern DEMO_CAPTURED -Quiet)) { throw "Brak potwierdzenia klienta $index." } }
    Write-Output 'Pokaz trzech klientów i dwóch aukcji zakończony; zrzuty i odczyty zapisane.'
} finally {
    foreach($client in $clients) { if(!$client.HasExited) { Stop-Process -Id $client.Id } }
}
