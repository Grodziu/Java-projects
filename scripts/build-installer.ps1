param([switch]$SkipBuild,[switch]$Production,[string]$ServerHost,[int]$ServerPort=5001)
. "$PSScriptRoot/env.ps1"
$ProgressPreference='SilentlyContinue'
Push-Location $ProjectRoot
try {
    if ($Production -and (!$ServerHost -or $ServerHost -match 'localhost|example\.|\.invalid$|127\.0\.0\.1')) { throw 'Profil publiczny wymaga rzeczywistej domeny serwera.' }
    if (!$SkipBuild) { & ./mvnw.cmd -B -ntp clean package; if ($LASTEXITCODE -ne 0) { throw 'Build nie powiódł się.' } }
    $wix=Join-Path $ToolRoot 'wix314'
    if (!(Test-Path -LiteralPath (Join-Path $wix 'candle.exe'))) {
        $zip=Join-Path $ToolRoot 'wix314.zip'
        Invoke-WebRequest 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile $zip -TimeoutSec 120
        Expand-Archive -LiteralPath $zip -DestinationPath $wix -Force
        (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash | Set-Content -LiteralPath (Join-Path $wix 'archive.sha256')
    }
    $env:PATH=$wix+';'+$env:PATH
    $packRoot=Join-Path $BidRoomHome ('packaging/'+[Guid]::NewGuid().ToString('N'))
    $outputDirectory=Join-Path $ProjectRoot 'dist'
    if($Production){$outputDirectory=Join-Path $outputDirectory 'internet'}
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    $input=Join-Path $packRoot 'input';$runtime=Join-Path $packRoot 'runtime';$image=Join-Path $packRoot 'image'
    New-Item -ItemType Directory -Force -Path $input,(Join-Path $input 'lib'),(Join-Path $ProjectRoot 'dist') | Out-Null
    Copy-Item -LiteralPath 'client/target/client-1.1.1.jar' -Destination $input
    if ($Production) {
        $profile=Join-Path $packRoot 'profile';New-Item -ItemType Directory -Path $profile | Out-Null
        "profile=production`nserverHost=$ServerHost`nserverPort=$ServerPort`ntls=true`nprotocolVersion=2" | Set-Content -LiteralPath (Join-Path $profile 'client.properties') -Encoding ascii
        & "$env:JAVA_HOME/bin/jar.exe" --update --file (Join-Path $input 'client-1.1.1.jar') -C $profile client.properties
        if ($LASTEXITCODE -ne 0) { throw 'Nie ustawiono profilu publicznego.' }
    }
    Get-ChildItem -LiteralPath 'client/target/lib' -Filter '*.jar' | Copy-Item -Destination (Join-Path $input 'lib')
    Copy-Item -LiteralPath 'docs/THIRD_PARTY.md' -Destination $input
    # jdeps of the packaged client requires scripting (FXML), sql (Jackson types) and jfr.
    & "$env:JAVA_HOME/bin/jlink.exe" -J-Xmx256m --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.scripting,java.sql,java.security.sasl,java.xml,jdk.crypto.ec,jdk.jfr,jdk.unsupported,jdk.charsets,jdk.zipfs --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
    if ($LASTEXITCODE -ne 0) { throw 'Nie utworzono runtime.' }
    & "$env:JAVA_HOME/bin/jpackage.exe" -J-Xmx256m --type app-image --dest $image --input $input --name BidRoom --icon (Join-Path $ProjectRoot 'client/src/main/resources/ui/assets/pk.ico') --main-jar client-1.1.1.jar --main-class pl.edu.bidroom.client.Launcher --runtime-image $runtime --app-version 1.1.1 --vendor 'BidRoom — projekt studencki' --java-options '-Xms32m' --java-options '-Xmx192m' --java-options '-Dfile.encoding=UTF-8'
    if ($LASTEXITCODE -ne 0) { throw 'Nie utworzono obrazu aplikacji.' }
    $installer=Join-Path $outputDirectory 'BidRoom-1.1.1.exe'
    if(Test-Path -LiteralPath $installer){Copy-Item -LiteralPath $installer -Destination ($installer+'.previous') -Force;Remove-Item -LiteralPath $installer}
    & "$env:JAVA_HOME/bin/jpackage.exe" -J-Xmx256m --type exe --dest $outputDirectory --app-image (Join-Path $image 'BidRoom') --name BidRoom --app-version 1.1.1 --vendor 'BidRoom — projekt studencki' --install-dir BidRoomApp --win-per-user-install --win-menu --win-shortcut --win-dir-chooser --win-upgrade-uuid '012968bb-0b4d-494a-913c-b0c8828289b6'
    if ($LASTEXITCODE -ne 0) { throw 'Nie utworzono instalatora.' }
    (Join-Path $image 'BidRoom') | Set-Content -LiteralPath (Join-Path $ProjectRoot 'dist/app-image-path.txt')
    Get-FileHash -LiteralPath $installer -Algorithm SHA256 | Format-List
    @{version='1.1.1';profile=$(if($Production){'production'}else{'local'});host=$(if($Production){$ServerHost}else{'127.0.0.1'});port=$(if($Production){$ServerPort}else{5001});tls=[bool]$Production;protocolVersion=2;sha256=(Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDirectory 'client-profile.json') -Encoding utf8
    if ($Production) { Write-Output "Instalator publiczny wskazuje $ServerHost na porcie $ServerPort z obowiązkowym TLS. Wymaga oddzielnego odbioru sieciowego." }
    else { Write-Output 'Instalator lokalny: domyślny serwer 127.0.0.1:5001. Nie jest wydaniem publicznym.' }
} finally { Pop-Location }

