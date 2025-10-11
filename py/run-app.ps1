Write-Host "[py] Starting Flask app..." -ForegroundColor Cyan
$ErrorActionPreference = 'Stop'
try { Set-Location -Path $PSScriptRoot } catch { Write-Error "Failed to set location to script root: $($_.Exception.Message)"; exit 1 }

# Ensure server venv and CUDA-enabled PyTorch are set up (idempotent)
Push-Location (Join-Path $PSScriptRoot "server")

# Quick probe to decide if setup is needed
$needsSetup = $true
if (Test-Path (Join-Path $PWD ".venv\Scripts\python.exe")) {
  $pyProbe = @'
import json
info = {"installed": False, "cuda_available": False, "vision": False}
try:
    import torch
    info.update({"installed": True, "cuda_available": bool(torch.cuda.is_available())})
    try:
        import torchvision  # noqa: F401
        info["vision"] = True
    except Exception:
        pass
except Exception:
    pass
print(json.dumps(info))
'@
  $probeOut = & .\.venv\Scripts\python.exe -c $pyProbe
  try { $inf = $probeOut | ConvertFrom-Json } catch { $inf = @{ installed = $false; cuda_available = $false; vision = $false } }
  if ($inf.installed -and $inf.cuda_available -and $inf.vision) { $needsSetup = $false }
}

if ($needsSetup) { & .\setup-venv.ps1 } else { Write-Host "[server] venv ready (skipped setup)." -ForegroundColor Green }

Pop-Location

# Activate venv (prefer server/.venv)
if (Test-Path (Join-Path $PSScriptRoot "server\.venv\Scripts\Activate.ps1")) {
  . (Join-Path $PSScriptRoot "server\.venv\Scripts\Activate.ps1")
} elseif (Test-Path (Join-Path $PSScriptRoot ".venv\Scripts\Activate.ps1")) {
  . (Join-Path $PSScriptRoot ".venv\Scripts\Activate.ps1")
}

$env:PYTHONPATH = (Resolve-Path $PSScriptRoot).Path
if (-not (Test-Path (Join-Path $PSScriptRoot "server\.env"))) {
  if (Test-Path (Join-Path $PSScriptRoot "server\.env.example")) {
    Copy-Item (Join-Path $PSScriptRoot "server\.env.example") (Join-Path $PSScriptRoot "server\.env") -Force
  }
}

# Print CUDA status
$pyCheck = @'
import torch
print(f"CUDA available: {torch.cuda.is_available()}")
print(f"Torch: {torch.__version__}")
if torch.cuda.is_available():
  print(f"GPU: {torch.cuda.get_device_name(0)} | CUDA: {torch.version.cuda} | cuDNN: {torch.backends.cudnn.version()}")
'@
python -c $pyCheck

Set-Location -Path (Join-Path $PSScriptRoot "server")
python app.py
