param([Parameter(Mandatory=$true)][string]$Backup)
. "$PSScriptRoot/env.ps1"
Import-BidRoomSecrets
$settings=Get-Content -LiteralPath (Join-Path $BidRoomHome 'server-secrets.json') -Raw | ConvertFrom-Json
$pgBin=Join-Path $ToolRoot 'postgresql17/pgsql/bin'
$database='bidroom_restore_test'
$env:PGPASSWORD=$settings.BIDROOM_DB_ADMIN_PASSWORD
try {
    $exists=& (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p 55432 -U bidroom_admin -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='$database'"
    if ($exists -eq '1') { throw 'Baza bidroom_restore_test już istnieje. Skrypt nie nadpisuje danych.' }
    & (Join-Path $pgBin 'createdb.exe') -h 127.0.0.1 -p 55432 -U bidroom_admin -O bidroom $database
    if ($LASTEXITCODE -ne 0) { throw 'Nie utworzono bazy odtworzeniowej.' }
    $env:PGPASSWORD=$env:BIDROOM_DB_PASSWORD
    & (Join-Path $pgBin 'pg_restore.exe') -h 127.0.0.1 -p 55432 -U bidroom -d $database --no-owner --exit-on-error $Backup
    if ($LASTEXITCODE -ne 0) { throw 'Odtworzenie nie powiodło się.' }
    & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p 55432 -U bidroom -d $database -c 'SELECT (SELECT count(*) FROM app_user) users,(SELECT count(*) FROM auction) auctions,(SELECT count(*) FROM bid) bids;'
    if ($LASTEXITCODE -ne 0) { throw 'Nie odczytano odtworzonej bazy.' }
} finally { Remove-Item Env:PGPASSWORD }
