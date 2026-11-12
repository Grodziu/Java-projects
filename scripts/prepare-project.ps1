$ErrorActionPreference='Stop'
& "$PSScriptRoot/setup-tools.ps1"
& "$PSScriptRoot/start-db.ps1"
. "$PSScriptRoot/env.ps1"
Push-Location $ProjectRoot
try {
    & ./mvnw.cmd -B -ntp clean package
    if ($LASTEXITCODE -ne 0) { throw 'Nie zbudowano projektu.' }
} finally { Pop-Location }
Write-Output 'Projekt zbudowany. PostgreSQL gotowy. Teraz uruchom 1-Start-serwera.cmd, a po komunikacie gotowosci 2-Start-klienta.cmd.'
Write-Output 'Kazdy komputer dewelopera otrzymuje wlasne konta i hasla w USERPROFILE/BidRoomData/server-secrets.json.'
