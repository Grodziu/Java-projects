param([switch]$SkipBuild)
. "$PSScriptRoot/env.ps1"
Push-Location $ProjectRoot
try {
    if(!$SkipBuild) { & ./mvnw.cmd -B -ntp -pl server -am package; if($LASTEXITCODE -ne 0){throw 'Nie zbudowano serwera.'} }
    $stage=Join-Path $BidRoomHome ('server-packages/'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path (Join-Path $stage 'server/target'),(Join-Path $stage 'docs'),(Join-Path $ProjectRoot 'dist') | Out-Null
    Copy-Item -LiteralPath 'server/target/server-1.1.1.jar' -Destination (Join-Path $stage 'server/target')
    Copy-Item -LiteralPath 'deploy' -Destination $stage -Recurse
    Copy-Item -LiteralPath '.dockerignore' -Destination $stage
    Copy-Item -LiteralPath 'docs/DEPLOYMENT.md','docs/DATABASE_SCHEMA.md','docs/database-schema.svg','docs/THIRD_PARTY.md' -Destination (Join-Path $stage 'docs')
    $archive=Join-Path $ProjectRoot 'dist/BidRoom-serwer-1.1.1.zip'
    Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $archive -Force
    Get-FileHash -LiteralPath $archive -Algorithm SHA256 | Format-List
} finally {Pop-Location}
