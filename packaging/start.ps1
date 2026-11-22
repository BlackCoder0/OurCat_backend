$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$java = Join-Path $PSScriptRoot 'runtime/bin/java.exe'
$jar = Join-Path $PSScriptRoot 'backend.jar'
if (!(Test-Path -LiteralPath $java) -or !(Test-Path -LiteralPath $jar)) {
    throw 'Incomplete package: runtime or backend.jar is missing. Extract the entire ZIP first.'
}
$data = Join-Path $PSScriptRoot 'data'
New-Item -ItemType Directory -Force -Path $data, (Join-Path $PSScriptRoot 'logs') | Out-Null
$portFile = Join-Path $data 'port.txt'
if (!(Test-Path -LiteralPath $portFile)) { [IO.File]::WriteAllText($portFile, '8080') }
$port = 0
if (![int]::TryParse(([IO.File]::ReadAllText($portFile)).Trim(), [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
    throw 'Invalid port in data/port.txt. Choose an integer from 1 to 65535.'
}
$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Any, $port)
try { $listener.Start() } catch { throw "Port $port is in use. Stop your other demo window or change data/port.txt." }
finally { $listener.Stop() }

$settings = Join-Path $data 'instance.properties'
if (!(Test-Path -LiteralPath $settings)) {
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes = New-Object byte[] 48
        $rng.GetBytes($bytes)
        $secret = [Convert]::ToBase64String($bytes)
        $rng.GetBytes($bytes)
        $password = [Convert]::ToBase64String($bytes).Substring(0, 24)
    } finally { $rng.Dispose() }
    [IO.File]::WriteAllText($settings, "ourcat.jwt.secret=$secret`nourcat.demo.admin.username=local-admin`nourcat.demo.admin.password=$password`n")
    [IO.File]::WriteAllText((Join-Path $data 'login-info.txt'), "Local instance only`nUsername: local-admin`nPassword: $password`nKeep this file private. Change the password in the app after login.`n")
}

# Only this launcher process is changed; Windows/user environment settings are untouched.
Get-ChildItem Env: | Where-Object { $_.Name -match '^(SPRING_|OURCAT_|AI_|JWT_|DB_|SERVER_)' } |
    ForEach-Object { Remove-Item -LiteralPath ("Env:" + $_.Name) }
$settingsUri = ([Uri]$settings).AbsoluteUri
$arguments = @('-jar', $jar,
    ('--spring.config.location=classpath:/application.yml,classpath:/application-demo.yml,' + $settingsUri),
    '--spring.profiles.active=demo', "--server.port=$port", '--ourcat.demo.data-dir=./data')
Write-Host ''
Write-Host 'OurCat local demo. Keep this window open.'
Write-Host "Admin login: $data/login-info.txt"
Write-Host "Android emulator: http://10.0.2.2:$port/"
Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*' } |
    ForEach-Object { Write-Host ("Phone candidate: http://" + $_.IPAddress + ":$port/") }
Write-Host 'Choose your Wi-Fi/LAN address, not a VPN or virtual adapter.'
Write-Host 'Ready when the log says: Started OurCatApplication.'
Write-Host 'Stop: press Ctrl+C in this window. Back up the entire data folder while stopped.'
Write-Host ''
& $java @arguments 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'logs/current.log')
if ($LASTEXITCODE -ne 0) { throw 'Backend stopped with an error. Check logs/current.log.' }
