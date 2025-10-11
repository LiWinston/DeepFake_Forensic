param(
  [switch]$CpuOnly
)

Write-Host "[py] Creating venv and installing dependencies..." -ForegroundColor Cyan

Set-Location -Path $PSScriptRoot
if (-not (Test-Path ".venv")) {
  python -m venv .venv
}

. .\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip wheel setuptools

# Install PyTorch + Torchvision first with proper index (CPU by default)
$torchIndex = "https://download.pytorch.org/whl/cpu"
if (-not $CpuOnly) {
  # Try default first; fallback to CPU wheels on failure
  try {
    Write-Host "Installing torch/torchvision from PyPI..." -ForegroundColor Yellow
    pip install torch torchvision
  } catch {
    Write-Host "Falling back to CPU wheels..." -ForegroundColor Yellow
    pip install --index-url $torchIndex torch torchvision
  }
} else {
  Write-Host "Installing CPU-only torch/torchvision..." -ForegroundColor Yellow
  pip install --index-url $torchIndex torch torchvision
}

# Install server requirements (includes flask/redis/kafka/minio/opencv/etc.)
pip install -r server/requirements.txt

Write-Host "[py] Dependencies installed successfully." -ForegroundColor Green
