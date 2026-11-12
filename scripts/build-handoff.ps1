param([switch]$IncludeCourseMaterials)
. "$PSScriptRoot/env.ps1"
Push-Location $ProjectRoot
try {
    $dirty=@(git status --porcelain)
    if($LASTEXITCODE -ne 0 -or $dirty.Count){throw 'Najpierw zapisz zweryfikowane zrodla w commicie Git. Paczka musi odpowiadac commitowi.'}
    $version=([xml](Get-Content pom.xml -Raw)).project.version
    $destination=Join-Path $ProjectRoot 'dist'
    $installer=Join-Path $destination "BidRoom-$version.exe"
    $server=Join-Path $destination "BidRoom-serwer-$version.zip"
    if(!(Test-Path $installer) -or !(Test-Path $server)){throw 'Najpierw zbuduj lokalny instalator i pakiet serwera.'}
    $stage=Join-Path $BidRoomHome ('handoff/'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    $source=Join-Path $destination "BidRoom-zrodla-$version.zip"
    & git archive --format=zip --prefix=BidRoom/ -o $source HEAD
    if($LASTEXITCODE -ne 0){throw 'Nie przygotowano zrodel z Git.'}
    Copy-Item -LiteralPath $source,$installer,$server,'NAJPIERW-PRZECZYTAJ.txt','WIADOMOSC-DO-MARCINA.txt' -Destination $stage
    Copy-Item -LiteralPath (Join-Path $destination 'client-profile.json') -Destination $stage
    $commit=git rev-parse HEAD
    "Wersja: $version`nCommit: $commit`nProfil instalatora: lokalny (127.0.0.1:5001). Nie jest to wdrozenie internetowe." | Set-Content (Join-Path $stage 'WERSJA.txt') -Encoding utf8
    Compress-Archive -Path (Join-Path $stage '*') -DestinationPath (Join-Path $destination "BidRoom-przekazanie-$version.zip") -Force
    if($IncludeCourseMaterials){
        # Separate local archive; intentionally not included in public GitHub releases.
        Compress-Archive -Path 'docs/reference/lectures','docs/reference/design' -DestinationPath (Join-Path $destination 'BidRoom-materialy-zajec.zip') -Force
    }
    $files=@($installer,$server,$source,(Join-Path $destination "BidRoom-przekazanie-$version.zip"))
    $lines=foreach($file in $files){(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+[IO.Path]::GetFileName($file)}
    $lines | Set-Content (Join-Path $destination 'SHA256SUMS.txt') -Encoding ascii
    Write-Output "Paczki gotowe w dist; commit $commit. Materialow zajec nie publikuj automatycznie."
} finally {Pop-Location}
