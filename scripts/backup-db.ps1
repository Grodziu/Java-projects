param([string]$Destination)
. "$PSScriptRoot/env.ps1"
Import-BidRoomSecrets
if (!$Destination) { $Destination=Join-Path $BidRoomHome ('backups/bidroom-dev-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.dump') }
$full=[IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $full) { throw 'Plik kopii już istnieje. Wybierz nową nazwę.' }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $full) | Out-Null
$env:PGPASSWORD=$env:BIDROOM_DB_PASSWORD
try {
    & (Join-Path $ToolRoot 'postgresql17/pgsql/bin/pg_dump.exe') -h 127.0.0.1 -p 55432 -U $env:BIDROOM_DB_USER -d bidroom_dev --format=custom --no-owner --file $full
    if ($LASTEXITCODE -ne 0) { throw 'Kopia nie została wykonana.' }
} finally { Remove-Item Env:PGPASSWORD }
Write-Output "Kopia: $full"
