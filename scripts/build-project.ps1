. "$PSScriptRoot/env.ps1"
Push-Location $ProjectRoot
try {
    & ./mvnw.cmd -B -ntp clean package
    if($LASTEXITCODE -ne 0){throw 'Build nie powiodl sie.'}
} finally { Pop-Location }
