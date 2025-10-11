# Install PyTorch with CUDA Support
# This script will install PyTorch with CUDA 11.8 (most compatible)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "PyTorch CUDA Installation Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if NVIDIA GPU exists
Write-Host "Checking for NVIDIA GPU..." -ForegroundColor Yellow
try {
    $nvidiaOutput = nvidia-smi 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ NVIDIA GPU detected!" -ForegroundColor Green
        Write-Host ""
        nvidia-smi --query-gpu=name,driver_version,memory.total --format=csv,noheader
        Write-Host ""
    } else {
        Write-Host "✗ No NVIDIA GPU detected!" -ForegroundColor Red
        Write-Host "This script requires an NVIDIA GPU with CUDA support." -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ nvidia-smi not found! Please install NVIDIA drivers." -ForegroundColor Red
    exit 1
}

# Ask user for CUDA version
Write-Host "Select CUDA version to install:" -ForegroundColor Yellow
Write-Host "1. CUDA 11.8 (Recommended - Most Compatible)"
Write-Host "2. CUDA 12.1"
Write-Host "3. CUDA 12.4 (Latest)"
Write-Host ""
$choice = Read-Host "Enter choice (1-3, default: 1)"

if ([string]::IsNullOrWhiteSpace($choice)) {
    $choice = "1"
}

switch ($choice) {
    "1" { $cudaVersion = "cu118"; $cudaName = "11.8" }
    "2" { $cudaVersion = "cu121"; $cudaName = "12.1" }
    "3" { $cudaVersion = "cu124"; $cudaName = "12.4" }
    default { $cudaVersion = "cu118"; $cudaName = "11.8" }
}

Write-Host ""
Write-Host "Installing PyTorch with CUDA $cudaName..." -ForegroundColor Yellow
Write-Host ""

# Uninstall existing PyTorch
Write-Host "Removing existing PyTorch installations..." -ForegroundColor Yellow
pip uninstall torch torchvision torchaudio -y 2>&1 | Out-Null

# Install PyTorch with CUDA
Write-Host "Installing PyTorch with CUDA $cudaName..." -ForegroundColor Yellow
Write-Host ""
$indexUrl = "https://download.pytorch.org/whl/$cudaVersion"
pip install torch torchvision torchaudio --index-url $indexUrl

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "✗ Installation failed!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Verifying Installation..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Verify installation
$pythonCheck = @"
import torch
print(f'PyTorch Version: {torch.__version__}')
print(f'CUDA Available: {torch.cuda.is_available()}')
if torch.cuda.is_available():
    print(f'CUDA Version: {torch.version.cuda}')
    print(f'GPU Device: {torch.cuda.get_device_name(0)}')
    print(f'GPU Count: {torch.cuda.device_count()}')
    print(f'cuDNN Enabled: {torch.backends.cudnn.enabled}')
    print(f'cuDNN Version: {torch.backends.cudnn.version()}')
else:
    print('WARNING: CUDA not available!')
"@

python -c $pythonCheck

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "✓ Installation Complete!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "1. Run verification script: python verify_gpu.py" -ForegroundColor White
    Write-Host "2. Restart your API server" -ForegroundColor White
    Write-Host "3. Enjoy GPU-accelerated inference!" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "✗ Installation completed but verification failed!" -ForegroundColor Red
    Write-Host "Please check the error messages above." -ForegroundColor Red
    exit 1
}
