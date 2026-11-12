. "$PSScriptRoot/env.ps1"
Import-BidRoomSecrets
$pgBin=Join-Path $ToolRoot 'postgresql17/pgsql/bin'
$env:PGPASSWORD=$env:BIDROOM_DB_PASSWORD
$env:PGOPTIONS='-c default_transaction_read_only=on'
Write-Output 'BidRoom: lokalny podgląd bazy, domyślnie tylko odczyt.'
Write-Output 'Tabele: \dt   Struktura tabeli: \d app_user   Wyjście: \q'
Write-Output 'Przykład: SELECT id,login_normalized,display_name,role FROM app_user ORDER BY id;'
try { & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p 55432 -U $env:BIDROOM_DB_USER -d bidroom_dev }
finally {Remove-Item Env:PGPASSWORD;Remove-Item Env:PGOPTIONS}
