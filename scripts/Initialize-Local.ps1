$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$destination = Join-Path $root '.env'
if (Test-Path -LiteralPath $destination) {
    Write-Output 'Локальный файл .env уже существует.'
    return
}

function New-RandomBytes([int]$count) {
    $bytes = New-Object byte[] $count
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

$key = New-RandomBytes 32
$adminPassword = 'Taxi!A9-' + ((New-RandomBytes 18).TrimEnd('=') -replace '[+/]', 'x')
$contents = @(
    'TAXI_DB_PASSWORD=taxi_local'
    'TAXI_HTTP_PORT=8081'
    'TAXI_ADMIN_EMAIL=admin@taxi.example'
    "TAXI_ADMIN_PASSWORD=$adminPassword"
    "TAXI_JWT_SECRET=$key"
)
[IO.File]::WriteAllLines($destination, $contents, [Text.UTF8Encoding]::new($false))
Write-Output 'Создан .env. Учебные данные администратора находятся в этом файле.'
