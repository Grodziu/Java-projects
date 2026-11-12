param([int]$Port=5001)
$ErrorActionPreference='Stop'
# Check the actual BidRoom protocol, not merely an open TCP port.
$tcp=[Net.Sockets.TcpClient]::new()
try {
    $connect=$tcp.ConnectAsync('127.0.0.1',$Port)
    if(!$connect.Wait(2500)) { throw 'Timeout' }
    $stream=$tcp.GetStream();$stream.ReadTimeout=2500;$stream.WriteTimeout=2500
    $requestId=[Guid]::NewGuid().ToString()
    $request=@{protocolVersion=2;kind='REQUEST';operation='HELLO';requestId=$requestId;payload=@{}} | ConvertTo-Json -Compress
    $bytes=[Text.Encoding]::UTF8.GetBytes($request)
    $size=[BitConverter]::GetBytes([Net.IPAddress]::HostToNetworkOrder([int]$bytes.Length))
    $stream.Write($size,0,4);$stream.Write($bytes,0,$bytes.Length)
    function Read-FrameBytes([int]$Count) {
        $buffer=New-Object byte[] $Count;$offset=0
        while($offset -lt $Count){$read=$stream.Read($buffer,$offset,$Count-$offset);if($read -eq 0){throw 'Koniec odpowiedzi'};$offset+=$read}
        return ,$buffer
    }
    $prefix=Read-FrameBytes 4
    $length=[Net.IPAddress]::NetworkToHostOrder([BitConverter]::ToInt32($prefix,0))
    if($length -lt 1 -or $length -gt 1048576){throw 'Nieprawidlowa ramka'}
    $response=[Text.Encoding]::UTF8.GetString((Read-FrameBytes $length)) | ConvertFrom-Json
    if($response.protocolVersion -ne 2 -or $response.requestId -ne $requestId -or $response.kind -ne 'RESPONSE' -or $response.ok -ne $true){throw 'Nieprawidlowa odpowiedz BidRoom'}
} catch {
    throw 'Serwer BidRoom nie jest gotowy na porcie lokalnym. Najpierw uruchom 1-Start-serwera.cmd i poczekaj na SERWER GOTOWY. Szczegoly awarii: USERPROFILE/BidRoomData/server/console-error.log.'
} finally { $tcp.Dispose() }
