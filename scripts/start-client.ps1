param([string]$Session='default',[int]$Port=5001)
. "$PSScriptRoot/env.ps1"
if ($Session -notmatch '^[A-Za-z0-9_-]{1,40}$') { throw 'Nieprawidłowa nazwa sesji.' }
$data=Join-Path $BidRoomHome "clients/$Session"
New-Item -ItemType Directory -Force -Path $data | Out-Null
$run=Join-Path $data ('run-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $run,(Join-Path $run 'lib') | Out-Null
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'client/target/client-1.1.1.jar') -Destination $run
Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'client/target/lib') -Filter '*.jar' | Copy-Item -Destination (Join-Path $run 'lib')
$jar=Join-Path $run 'client-1.1.1.jar'
$lib=Join-Path $run 'lib/*'
$arguments='-Xms32m -Xmx192m -Dfile.encoding=UTF-8 "-Dbidroom.dataDir='+$data+'" -Dbidroom.port='+$Port+' -cp "'+$jar+';'+$lib+'" pl.edu.bidroom.client.Launcher'
# This is the interactive application window. Only background servers/helpers run hidden.
$process=Start-Process -FilePath "$env:JAVA_HOME/bin/javaw.exe" -ArgumentList $arguments -PassThru -WindowStyle Normal -RedirectStandardOutput (Join-Path $data 'console.log') -RedirectStandardError (Join-Path $data 'console-error.log')
Write-Output "Uruchomiono klienta: $Session, PID $($process.Id)."
$process.Id | Set-Content -LiteralPath (Join-Path $data 'client.pid')

