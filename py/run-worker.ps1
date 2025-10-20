Write-Host "[py] Starting Kafka worker..." -ForegroundColor Cyan
Set-Location -Path $PSScriptRoot

# Prefer activating server/.venv (the same venv used by run-app), fallback to root .venv
$activated = $false
if (Test-Path "server\.venv\Scripts\Activate.ps1") {
    . "server\.venv\Scripts\Activate.ps1"
    $activated = $true
    Write-Host "[py] Activated virtual environment: server\.venv" -ForegroundColor Green
} elseif (Test-Path ".venv\Scripts\Activate.ps1") {
    . ".venv\Scripts\Activate.ps1"
    $activated = $true
    Write-Host "[py] Activated virtual environment: .venv" -ForegroundColor Green
} else {
    Write-Host "[py] Warning: Virtual environment not found. Run install.ps1 first." -ForegroundColor Yellow
}

# After activation print a small Python probe to report torch/CUDA status for debugging
if ($activated) {
    $pyProbe = @'
import json,sys
info={"installed":False,"cuda_available":False,"vision":False}
try:
    import torch
    info["installed"] = True
    info["cuda_available"] = bool(torch.cuda.is_available())
    try:
        import torchvision
        info["vision"] = True
    except Exception:
        pass
    if info["cuda_available"]:
        try:
            info["device"] = torch.cuda.get_device_name(0)
            info["cuda_version"] = torch.version.cuda
            info["cudnn_version"] = torch.backends.cudnn.version()
        except Exception:
            pass
except Exception as e:
    info["error"] = str(e)
print(json.dumps(info))
'@
    try {
        $probeOut = python -c $pyProbe 2>&1
        Write-Host "[py] Python probe output:" -ForegroundColor Cyan
        Write-Host $probeOut
    } catch {
        Write-Host "[py] Failed running python probe: $($_.Exception.Message)" -ForegroundColor Yellow
    }
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
