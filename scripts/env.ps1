$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BidRoomHome = Join-Path $env:USERPROFILE 'BidRoomData'
$ToolRoot = Join-Path $BidRoomHome 'tools'
$java21=$false
if($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'release'))) {
    $java21=[bool](Select-String -LiteralPath (Join-Path $env:JAVA_HOME 'release') -Pattern '^JAVA_VERSION="21\.' -Quiet)
}
if (!$java21 -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    $jdkDirectory = Get-ChildItem -LiteralPath (Join-Path $ToolRoot 'jdk21') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if (!$jdkDirectory) { throw 'Uruchom scripts/setup-tools.ps1, aby przygotować JDK 21.' }
    $env:JAVA_HOME = $jdkDirectory.FullName
}
$env:MAVEN_OPTS = '-Xms32m -Xmx384m -Dfile.encoding=UTF-8'
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
function Import-BidRoomSecrets {
    $secretPath = Join-Path $BidRoomHome 'server-secrets.json'
    if (!(Test-Path -LiteralPath $secretPath)) { throw 'Brak konfiguracji lokalnej. Uruchom scripts/start-db.ps1.' }
    $config = Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json
    foreach ($property in $config.PSObject.Properties) { [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process') }
}
