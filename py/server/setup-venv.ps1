<#
  Setup Python virtual environment for Flask server with CUDA-enabled PyTorch on Windows.
  - Creates .venv if missing
  - Activates it
  - Installs PyTorch + TorchVision with CUDA wheels (default cu118) unless TORCH_CUDA env var is set
  - Installs other requirements from requirements.txt (excluding torch/torchvision to avoid CPU wheels)
 #>

param(
  [string]$CudaFlavor = $env:TORCH_CUDA  # e.g., "cu118", "cu121", "cu124"
)

Write-Host "[server] Setting up Python venv..." -ForegroundColor Cyan
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Set-Location -Path $PSScriptRoot

$venvPath = Join-Path $PSScriptRoot ".venv"
if (-not (Test-Path $venvPath)) {
  Write-Host "Creating virtual environment at $venvPath" -ForegroundColor Yellow
  python -m venv "$venvPath"
}

# Activate venv
. "$venvPath\Scripts\Activate.ps1"
Write-Host "Python: $(python --version)" -ForegroundColor DarkGray
Write-Host "Pip:    $(pip --version)" -ForegroundColor DarkGray

# Upgrade pip/setuptools/wheels
python -m pip install --upgrade pip setuptools wheel

# Detect NVIDIA GPU (best effort)
$hasNvidiaSmi = $false
try {
  $null = nvidia-smi --help 2>$null
  if ($LASTEXITCODE -eq 0) { $hasNvidiaSmi = $true }
} catch { $hasNvidiaSmi = $false }

if ([string]::IsNullOrWhiteSpace($CudaFlavor)) {
  # Default to cu118 for widest compatibility
  $CudaFlavor = "cu118"
}

Write-Host "CUDA wheel flavor: $CudaFlavor" -ForegroundColor Yellow
$torchIndex = "https://download.pytorch.org/whl/$CudaFlavor"

# Probe existing torch installation state
$pyProbe = @'
import json
info = {"installed": False, "cuda_available": False, "torch_version": None, "cuda_version": None, "vision_installed": False}
try:
    import torch
    info.update({
        "installed": True,
        "cuda_available": bool(torch.cuda.is_available()),
        "torch_version": getattr(torch, "__version__", None),
        "cuda_version": getattr(getattr(torch, "version", None), "cuda", None),
    })
    try:
        import torchvision  # noqa: F401
        info["vision_installed"] = True
    except Exception:
        pass
except Exception:
    pass
print(json.dumps(info))
'@

$probeOut = python -c $pyProbe
try { $torchInfo = $probeOut | ConvertFrom-Json } catch { $torchInfo = @{ installed = $false; cuda_available = $false; vision_installed = $false } }

if ($torchInfo.installed -and $torchInfo.cuda_available -and $torchInfo.vision_installed) {
  Write-Host "PyTorch with CUDA is already installed (Torch=$($torchInfo.torch_version), CUDA=$($torchInfo.cuda_version)). Skipping reinstall." -ForegroundColor Green
} else {
  if ($torchInfo.installed) {
    Write-Host "Existing PyTorch detected but not CUDA-enabled or torchvision missing. Reinstalling..." -ForegroundColor DarkYellow
    pip uninstall -y torch torchvision 2>$null | Out-Null
  } else {
    Write-Host "PyTorch not installed. Installing..." -ForegroundColor Yellow
  }
  # Install CUDA-enabled torch/vision first to avoid CPU wheels from requirements
  Write-Host "Installing PyTorch + TorchVision from $torchIndex" -ForegroundColor Yellow
  pip install torch torchvision --index-url $torchIndex
}

# Prepare trimmed requirements (exclude torch/torchvision lines)
$reqFile = Join-Path $PSScriptRoot 'requirements.txt'
if (Test-Path $reqFile) {
  $trimmed = Join-Path $PSScriptRoot 'requirements.notorch.txt'
  (Get-Content $reqFile) |
    Where-Object { $_ -notmatch '^(\s*#|\s*$)' } |
    Where-Object { $_ -notmatch '^\s*(torch|torchvision)\b' } |
    Set-Content $trimmed

  Write-Host "Installing remaining requirements from requirements.notorch.txt" -ForegroundColor Yellow
  pip install -r $trimmed
}

# Verify CUDA availability (informational)
$py = @'
import torch
print(f"Torch: {torch.__version__}")
print(f"CUDA available: {torch.cuda.is_available()}")
if torch.cuda.is_available():
  print(f"CUDA version: {torch.version.cuda}")
  print(f"GPU: {torch.cuda.get_device_name(0)}")
  print(f"cuDNN: {torch.backends.cudnn.version()} enabled={torch.backends.cudnn.enabled}")
'@
python -c $py

Write-Host "[server] venv ready." -ForegroundColor Green
