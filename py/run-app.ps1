Write-Host "[py] Starting Flask app..." -ForegroundColor Cyan
Set-Location -Path $PSScriptRoot

# Ensure server venv and CUDA-enabled PyTorch are set up
Push-Location "server"
& .\setup-venv.ps1
Pop-Location

# Activate venv (prefer server/.venv)
if (Test-Path ".\server\.venv\Scripts\Activate.ps1") {
  . .\server\.venv\Scripts\Activate.ps1
} elseif (Test-Path ".\.venv\Scripts\Activate.ps1") {
  . .\.venv\Scripts\Activate.ps1
}

$env:PYTHONPATH=(Resolve-Path .).Path
if (-not (Test-Path "server\.env")) { if (Test-Path "server\.env.example") { Copy-Item "server\.env.example" "server\.env" -Force } }

# Print CUDA status
$pyCheck = @'
import torch
print(f"CUDA available: {torch.cuda.is_available()}")
print(f"Torch: {torch.__version__}")
if torch.cuda.is_available():
  print(f"GPU: {torch.cuda.get_device_name(0)} | CUDA: {torch.version.cuda} | cuDNN: {torch.backends.cudnn.version()}")
'@
python -c $pyCheck

Set-Location -Path "server"
python app.py
