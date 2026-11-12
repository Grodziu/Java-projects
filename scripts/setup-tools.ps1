$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$toolRoot = Join-Path $env:USERPROFILE 'BidRoomData/tools'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
function Install-Zip($Name, $Url, $Sha256) {
    $archive = Join-Path $toolRoot "$Name.zip"
    if (!(Test-Path -LiteralPath $archive)) { Invoke-WebRequest $Url -OutFile $archive -TimeoutSec 300 }
    if ($Sha256 -and (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $Sha256) { throw "Błędna suma kontrolna: $Name" }
    $destination = Join-Path $toolRoot $Name
    if (!(Test-Path -LiteralPath $destination)) {
        New-Item -ItemType Directory -Path $destination | Out-Null
        Expand-Archive -LiteralPath $archive -DestinationPath $destination
    }
    Write-Output "Gotowe narzędzie: $Name"
}
Install-Zip 'jdk21' 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip' 'a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9'
$mavenUrl = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip'
$mavenHash = (Invoke-RestMethod "$mavenUrl.sha512" -TimeoutSec 30).Trim()
$mavenZip = Join-Path $toolRoot 'maven.zip'
if (!(Test-Path -LiteralPath $mavenZip)) { Invoke-WebRequest $mavenUrl -OutFile $mavenZip -TimeoutSec 120 }
if ((Get-FileHash -LiteralPath $mavenZip -Algorithm SHA512).Hash -ne $mavenHash) { throw 'Błędna suma Maven' }
if (!(Test-Path -LiteralPath (Join-Path $toolRoot 'apache-maven-3.9.16'))) { Expand-Archive -LiteralPath $mavenZip -DestinationPath $toolRoot }
Write-Output 'Gotowe: JDK 21.0.13 i Maven 3.9.16. Globalny PATH bez zmian.'
