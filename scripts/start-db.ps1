$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$BidRoomHome = Join-Path $env:USERPROFILE 'BidRoomData'
$pgRoot = Join-Path $BidRoomHome 'tools/postgresql17'
$pgBin = Join-Path $pgRoot 'pgsql/bin'
$data = Join-Path $BidRoomHome 'postgres17-data'
New-Item -ItemType Directory -Force -Path $BidRoomHome | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $pgBin 'pg_ctl.exe'))) {
    $zip = Join-Path $BidRoomHome 'tools/postgresql17.zip'
    if (!(Test-Path -LiteralPath $zip)) { Invoke-WebRequest 'https://get.enterprisedb.com/postgresql/postgresql-17.11-1-windows-x64-binaries.zip' -OutFile $zip -TimeoutSec 300 }
    Expand-Archive -LiteralPath $zip -DestinationPath $pgRoot -Force
    (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash | Set-Content (Join-Path $pgRoot 'archive.sha256')
}
$secretPath = Join-Path $BidRoomHome 'server-secrets.json'
if (!(Test-Path -LiteralPath $secretPath)) {
    function New-Secret {
        $bytes=New-Object byte[] 32
        $generator=[System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $generator.GetBytes($bytes); [Convert]::ToBase64String($bytes) } finally { $generator.Dispose() }
    }
    $secrets = @{
        BIDROOM_DB_URL='jdbc:postgresql://127.0.0.1:55432/bidroom_dev'; BIDROOM_DB_USER='bidroom'; BIDROOM_DB_PASSWORD=(New-Secret)
        BIDROOM_COMMAND_HMAC_SECRET=(New-Secret); BIDROOM_PROFILE='local'; BIDROOM_BOOTSTRAP_ADMIN_LOGIN='administrator'; BIDROOM_BOOTSTRAP_ADMIN_PASSWORD=(New-Secret)
        BIDROOM_DATA_DIR=(Join-Path $BidRoomHome 'server'); BIDROOM_DB_ADMIN_PASSWORD=(New-Secret)
    }
    $secrets | ConvertTo-Json | Set-Content -LiteralPath $secretPath -Encoding utf8
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $secretPath '/inheritance:r' '/grant:r' "${identity}:(F)" 'SYSTEM:(F)' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Nie ustawiono prywatnych uprawnień konfiguracji.' }
}
$settings = Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json
if (!(Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
    $pw = Join-Path $BidRoomHome 'init-password.tmp'
    try {
        $settings.BIDROOM_DB_ADMIN_PASSWORD | Set-Content -LiteralPath $pw -Encoding ascii
        & (Join-Path $pgBin 'initdb.exe') -D $data -U bidroom_admin --auth=scram-sha-256 "--pwfile=$pw" --encoding=UTF8 --locale=C
        if ($LASTEXITCODE -ne 0) { throw 'initdb nie powiodło się.' }
    } finally { Remove-Item -LiteralPath $pw -Force -ErrorAction SilentlyContinue }
    @"
listen_addresses = '127.0.0.1'
port = 55432
shared_buffers = '32MB'
work_mem = '2MB'
maintenance_work_mem = '32MB'
max_connections = 20
timezone = 'UTC'
log_statement = 'none'
log_min_error_statement = 'panic'
"@ | Add-Content -LiteralPath (Join-Path $data 'postgresql.conf')
}
& (Join-Path $pgBin 'pg_ctl.exe') status -D $data | Out-Null
if ($LASTEXITCODE -ne 0) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,55432)
    try { $listener.Start() } finally { $listener.Stop() }
    $pgArgs = '-D "' + $data + '" -l "' + (Join-Path $BidRoomHome 'postgres17.log') + '" -w -t 30 start'
    $process = Start-Process -FilePath (Join-Path $pgBin 'pg_ctl.exe') -ArgumentList $pgArgs -WindowStyle Hidden -PassThru
    if (!$process.WaitForExit(35000)) { throw 'pg_ctl przekroczył czas uruchamiania.' }
    if ($process.ExitCode -ne 0) { throw 'PostgreSQL nie uruchomił się. Sprawdź postgres17.log w USERPROFILE/BidRoomData.' }
}
$env:PGPASSWORD = $settings.BIDROOM_DB_ADMIN_PASSWORD
try {
    $psql = Join-Path $pgBin 'psql.exe'
    $exists = & $psql -h 127.0.0.1 -p 55432 -U bidroom_admin -d postgres -Atc "SELECT 1 FROM pg_roles WHERE rolname='bidroom'"
    if ($exists -ne '1') {
        $sql = "CREATE ROLE bidroom LOGIN PASSWORD '$($settings.BIDROOM_DB_PASSWORD)';"
        $sql | & $psql -h 127.0.0.1 -p 55432 -U bidroom_admin -d postgres -v ON_ERROR_STOP=1 -q
        if ($LASTEXITCODE -ne 0) { throw 'Nie utworzono roli aplikacji.' }
    }
    foreach ($database in @('bidroom_dev','bidroom_test')) {
        $exists = & $psql -h 127.0.0.1 -p 55432 -U bidroom_admin -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='$database'"
        if ($exists -ne '1') { & (Join-Path $pgBin 'createdb.exe') -h 127.0.0.1 -p 55432 -U bidroom_admin -O bidroom $database }
        if ($LASTEXITCODE -ne 0) { throw "Nie przygotowano bazy $database" }
    }
} finally { Remove-Item Env:PGPASSWORD }
& (Join-Path $pgBin 'pg_isready.exe') -h 127.0.0.1 -p 55432
Write-Output 'Bazy dev/test gotowe; dane i sekrety poza OneDrive. Hasło administratora aplikacji znajduje się w prywatnym server-secrets.json.'
