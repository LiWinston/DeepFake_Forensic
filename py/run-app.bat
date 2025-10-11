@echo off
setlocal enableextensions
REM Robust launcher for py\run-app.ps1 using pwsh with ExecutionPolicy Bypass
REM Works even when double-clicked or "Run with PowerShell" causes working dir/policy issues

REM Move to the folder of this .bat (py directory)
pushd "%~dp0"

REM Prefer pwsh (PowerShell 7+), fallback to Windows PowerShell
where pwsh >nul 2>nul
if %ERRORLEVEL%==0 (
  pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-app.ps1"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-app.ps1"
)

set EXITCODE=%ERRORLEVEL%
popd
exit /b %EXITCODE%