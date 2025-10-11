Write-Host "[py] Starting Kafka worker..." -ForegroundColor Cyan
Set-Location -Path $PSScriptRoot
. .\server\.venv\Scripts\Activate.ps1 2>$null
if (-not (Get-Command Activate.ps1 -ErrorAction SilentlyContinue)) {
  if (Test-Path ".venv") { . .\.venv\Scripts\Activate.ps1 } else { . .\server\.venv\Scripts\Activate.ps1 }
}
$env:PYTHONPATH=(Resolve-Path .).Path
if (-not (Test-Path "server\.env")) { if (Test-Path "server\.env.example") { Copy-Item "server\.env.example" "server\.env" -Force } }
Set-Location -Path "server"
python kafka_worker.py
