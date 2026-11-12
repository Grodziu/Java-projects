$ErrorActionPreference = 'Continue'
$system = Get-CimInstance Win32_OperatingSystem
[pscustomobject]@{ TotalMiB = [math]::Round($system.TotalVisibleMemorySize/1024); FreeMiB = [math]::Round($system.FreePhysicalMemory/1024); FreeDiskGiB = [math]::Round((Get-PSDrive C).Free/1GB, 1) } | Format-List
$toolRoot = Join-Path $env:USERPROFILE 'BidRoomData/tools'
Get-ChildItem -LiteralPath $toolRoot -Directory -ErrorAction SilentlyContinue | Select-Object Name
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object LocalPort -in 5001,55432 | Select-Object LocalAddress,LocalPort,OwningProcess
Write-Output ('Docker na PATH: ' + [bool](Get-Command docker -ErrorAction SilentlyContinue))

