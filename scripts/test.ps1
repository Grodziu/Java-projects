param([switch]$Integration)
. "$PSScriptRoot/env.ps1"
Push-Location $ProjectRoot
try {
    if ($Integration) {
        Import-BidRoomSecrets
        $env:BIDROOM_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55432/bidroom_test'
        $env:BIDROOM_TEST_DATA_DIR=Join-Path $BidRoomHome 'integration-server'
        & ./mvnw.cmd -B -ntp -Pintegration verify
    } else { & ./mvnw.cmd -B -ntp test }
    if ($LASTEXITCODE -ne 0) { throw 'Testy niezaliczone. Sprawdź raporty Surefire/Failsafe.' }
} finally { Pop-Location }
