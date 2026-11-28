$ErrorActionPreference = 'Stop'

if (-not $env:BASE_URL) { $env:BASE_URL = 'http://localhost:8080' }
if (-not $env:K6_TOKENS_FILE) { $env:K6_TOKENS_FILE = (Join-Path $PSScriptRoot 'k6/tokens.local.json') }
if (-not (Test-Path $env:K6_TOKENS_FILE)) {
    throw "No existe $env:K6_TOKENS_FILE. Copia tokens.example.json a tokens.local.json y agrega 200 JWT distintos."
}

New-Item -ItemType Directory -Force -Path 'docs/calidad/evidencia/hu14' | Out-Null
$runId = 'hu14-nominal-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$env:K6_PROMETHEUS_RW_SERVER_URL = 'http://localhost:9090/api/v1/write'
$env:K6_PROMETHEUS_RW_TREND_STATS = 'p(95),p(99),avg,max'
$env:K6_PROMETHEUS_RW_STALE_MARKERS = 'true'

k6 run -o experimental-prometheus-rw --tag "testid=$runId" --summary-export "docs/calidad/evidencia/hu14/$runId-summary.json" tests/performance/k6/nominal.js
exit $LASTEXITCODE
