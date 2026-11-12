$ErrorActionPreference='Stop'
$root=Join-Path $env:USERPROFILE 'BidRoomData'
& (Join-Path $root 'tools/postgresql17/pgsql/bin/pg_ctl.exe') -D (Join-Path $root 'postgres17-data') -m fast -w -t 30 stop
if ($LASTEXITCODE -ne 0) { throw 'Nie zatrzymano projektowej bazy.' }
