$ErrorActionPreference = 'Stop'

chcp.com 65001 | Out-Null
$utf8 = [Text.UTF8Encoding]::new()
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8

& (Join-Path $PSScriptRoot 'Use-TaxiEnvironment.ps1')
$pom = Join-Path $PSScriptRoot '../kr-01/pom.xml'
& mvn -q -f $pom compile exec:java
exit $LASTEXITCODE
