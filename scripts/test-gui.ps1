param([switch]$LayoutScales)
. "$PSScriptRoot/env.ps1"
Import-BidRoomSecrets
$token=[Guid]::NewGuid().ToString('N')
$database='bidroom_gui_'+$token.Substring(0,12)
$pgBin=Join-Path $ToolRoot 'postgresql17/pgsql/bin'
$env:PGPASSWORD=$env:BIDROOM_DB_ADMIN_PASSWORD
$service=$null;$databaseCreated=$false
Push-Location $ProjectRoot
try {
    & (Join-Path $pgBin 'createdb.exe') -h 127.0.0.1 -p 55432 -U bidroom_admin -O bidroom $database
    if($LASTEXITCODE -ne 0){throw 'Nie utworzono oddzielnej bazy GUI.'};$databaseCreated=$true
    $env:BIDROOM_DB_URL='jdbc:postgresql://127.0.0.1:55432/'+$database
    $env:BIDROOM_PORT='5011';$env:BIDROOM_PROFILE='local'
    $env:BIDROOM_DATA_DIR=Join-Path $BidRoomHome ('gui-validation/'+$token+'/server')
    New-Item -ItemType Directory -Force -Path $env:BIDROOM_DATA_DIR | Out-Null
    $jar=Join-Path $ProjectRoot 'server/target/server-1.1.1.jar'
    $arguments='-Xms32m -Xmx192m -Dfile.encoding=UTF-8 -jar "'+$jar+'"'
    $service=Start-Process -FilePath "$env:JAVA_HOME/bin/java.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:BIDROOM_DATA_DIR 'console.log') -RedirectStandardError (Join-Path $env:BIDROOM_DATA_DIR 'console-error.log')
    $deadline=(Get-Date).AddSeconds(30)
    while(!(Select-String -LiteralPath (Join-Path $env:BIDROOM_DATA_DIR 'console.log') -Pattern BIDROOM_READY -Quiet)){
        if($service.HasExited -or (Get-Date) -gt $deadline){throw 'Nie uruchomiono serwera GUI. Sprawdz prywatne logi gui-validation.'}
        Start-Sleep -Milliseconds 250
    }
    & "$PSScriptRoot/assert-server.ps1" -Port 5011
    $classpath=(Join-Path $ProjectRoot 'client/target/test-classes')+';'+(Join-Path $ProjectRoot 'client/target/classes')+';'+(Join-Path $ProjectRoot 'client/target/lib/*')
    $scales=@('1.0');if($LayoutScales){$scales=@('1.0','1.25','1.5')}
    foreach($scale in $scales){
        $data=Join-Path $BidRoomHome ('gui-validation/'+$token+'/client-'+$scale)
        $screenshots=Join-Path $ProjectRoot 'docs/screenshots-1.1.1'
        if($LayoutScales){$screenshots=Join-Path $screenshots ('scale-'+$scale)}
        & "$env:JAVA_HOME/bin/java.exe" -Xms32m -Xmx192m '-Dfile.encoding=UTF-8' '-Dbidroom.port=5011' "-Dglass.win.uiScale=$scale" "-Dbidroom.layoutOnly=$($LayoutScales.IsPresent.ToString().ToLowerInvariant())" "-Dbidroom.dataDir=$data" "-Dbidroom.screenshots=$screenshots" -cp $classpath pl.edu.bidroom.client.GuiSmoke
        if($LASTEXITCODE -ne 0){throw "Weryfikacja GUI $scale niezaliczona."}
    }
} finally {
    if($service -and !$service.HasExited){
        New-Item -ItemType File -Force -Path (Join-Path $env:BIDROOM_DATA_DIR 'stop.signal') | Out-Null
        $null=$service.WaitForExit(20000)
    }
    if($databaseCreated -and (!$service -or $service.HasExited) -and $database -match '^bidroom_gui_[a-f0-9]{12}$'){
        & (Join-Path $pgBin 'dropdb.exe') -h 127.0.0.1 -p 55432 -U bidroom_admin $database
        if($LASTEXITCODE -ne 0){Write-Warning "Pozostala testowa baza $database."}
    }
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
