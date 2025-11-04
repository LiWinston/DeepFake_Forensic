# Python Service Startup Guide (Windows)

To avoid issues with "Run with PowerShell" right-click option (working directory, execution policy, environment differences), use one of the following methods:

## Method 1: Double-click Batch File (Easiest)
- Path: `py\run-app.bat`
- Function: Automatically invokes PowerShell 7 (if available) or system PowerShell, sets execution policy to Bypass, and runs in script directory.

## Method 2: Run from Terminal (Recommended)
From repository root or `py` directory:

```powershell
cd py
./run-app.ps1
```

## What the Script Does
`run-app.ps1` will:
- Change to script directory
- Detect/create venv in `py/server`
- Install CUDA PyTorch and torchvision only if missing (idempotent)
- Activate venv and print CUDA/Torch status
- Start Flask service

## Common Issues
- Right-click "Run with PowerShell" error: Usually caused by wrong working directory. Use one of the methods above instead.
- First run takes longer: Initial run creates venv and installs dependencies. Subsequent runs skip installation.
- Force reconfiguration (rarely needed): Delete `py/server/.venv` and run again.
