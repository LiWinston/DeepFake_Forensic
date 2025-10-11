Write-Host "[py] Starting Kafka worker..." -ForegroundColor Cyan
Set-Location -Path $PSScriptRoot

# Activate virtual environment
if (Test-Path ".venv\Scripts\Activate.ps1") {
    . .\.venv\Scripts\Activate.ps1
    Write-Host "[py] Virtual environment activated" -ForegroundColor Green
} else {
    Write-Host "[py] Warning: Virtual environment not found. Run install.ps1 first." -ForegroundColor Yellow
}

$env:PYTHONPATH = (Resolve-Path .).Path
if (-not (Test-Path "server\.env")) { 
    if (Test-Path "server\.env.example") { 
        Copy-Item "server\.env.example" "server\.env" -Force 
        Write-Host "[py] Created .env from .env.example" -ForegroundColor Green
    }
}

Set-Location -Path "server"
python kafka_worker.py
