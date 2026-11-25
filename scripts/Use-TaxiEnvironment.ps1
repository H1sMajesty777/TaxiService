$envFile = Join-Path (Split-Path $PSScriptRoot) '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^(TAXI_[A-Z_]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
if (-not $env:TAXI_DB_PASSWORD) {
    $env:TAXI_DB_PASSWORD = 'taxi_local'
}
if ($env:TAXI_HTTP_PORT) {
    $env:APP_PORT = $env:TAXI_HTTP_PORT
}
