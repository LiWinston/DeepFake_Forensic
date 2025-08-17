# Quick Docker Cleanup Script for DeepFake Forensic
# Use this when the main script gets stuck

param(
    [switch]$Force,
    [switch]$Help
)

if ($Help) {
    Write-Host @"
Quick Docker Cleanup Script

Usage: .\quick-cleanup.ps1 [OPTIONS]

Options:
  -Force      Force cleanup without prompts
  -Help       Show this help message

This script will:
1. Kill any stuck Docker operations
2. Stop all containers
3. Remove unused resources
4. Reset Docker if needed

"@ -ForegroundColor Cyan
    exit 0
}

$LogPrefix = "[Quick-Cleanup]"

function Log-Info {
    param([string]$Message)
    Write-Host "$LogPrefix $Message" -ForegroundColor Green
}

function Log-Warn {
    param([string]$Message)
    Write-Host "$LogPrefix $Message" -ForegroundColor Yellow
}

function Log-Error {
    param([string]$Message)
    Write-Host "$LogPrefix $Message" -ForegroundColor Red
}

Write-Host "=== DeepFake Forensic Quick Docker Cleanup ===" -ForegroundColor Blue
Write-Host ""

if (!$Force) {
    $confirm = Read-Host "This will stop all Docker containers and clean resources. Continue? (y/N)"
    if ($confirm -notmatch "^[Yy]$") {
        Log-Info "Cleanup cancelled"
        exit 0
    }
}

# Step 1: Kill any stuck Docker processes
Log-Info "Killing any stuck Docker processes..."
try {
    Get-Job | Where-Object { $_.State -eq "Running" } | Stop-Job -Force
    Get-Job | Remove-Job -Force
    Log-Info "✓ Cleared PowerShell background jobs"
}
catch {
    Log-Warn "No background jobs to clear"
}

# Step 2: Force stop all containers
Log-Info "Force stopping all containers..."
try {
    $containers = docker ps -aq
    if ($containers) {
        docker stop $containers 2>$null
        docker rm $containers 2>$null
        Log-Info "✓ All containers stopped and removed"
    }
    else {
        Log-Info "✓ No containers to stop"
    }
}
catch {
    Log-Warn "Failed to stop containers: $($_.Exception.Message)"
}

# Step 3: Clean networks
Log-Info "Cleaning Docker networks..."
try {
    $networks = docker network ls --filter "name=deepfake" --format "{{.ID}}"
    if ($networks) {
        $networks | ForEach-Object { docker network rm $_ 2>$null }
        Log-Info "✓ Project networks removed"
    }
    else {
        Log-Info "✓ No project networks to clean"
    }
}
catch {
    Log-Warn "Failed to clean networks"
}

# Step 4: Clean volumes (careful!)
Log-Info "Cleaning Docker volumes..."
try {
    $volumes = docker volume ls --filter "name=deepfake" --format "{{.Name}}"
    if ($volumes) {
        Write-Host "Found volumes: $($volumes -join ', ')" -ForegroundColor Yellow
        if ($Force) {
            $removeVolumes = $true
        }
        else {
            $removeVolumes = (Read-Host "Remove these volumes? This will DELETE DATA! (y/N)") -match "^[Yy]$"
        }
        
        if ($removeVolumes) {
            $volumes | ForEach-Object { docker volume rm $_ 2>$null }
            Log-Info "✓ Project volumes removed"
        }
        else {
            Log-Info "✓ Volumes preserved"
        }
    }
    else {
        Log-Info "✓ No project volumes to clean"
    }
}
catch {
    Log-Warn "Failed to clean volumes"
}

# Step 5: Quick system prune (non-blocking)
Log-Info "Quick system cleanup..."
try {
    $pruneJob = Start-Job -ScriptBlock { docker system prune -f }
    Wait-Job $pruneJob -Timeout 30 | Out-Null
    
    if ($pruneJob.State -eq "Running") {
        Log-Warn "System prune taking too long, skipping..."
        Stop-Job $pruneJob -Force
    }
    else {
        Log-Info "✓ System cleanup completed"
    }
    
    Remove-Job $pruneJob -Force
}
catch {
    Log-Warn "System prune failed or timed out"
}

# Step 6: Check Docker status
Log-Info "Checking final Docker status..."
try {
    docker info > $null 2>&1
    Log-Info "✓ Docker is responsive"
}
catch {
    Log-Error "Docker may need to be restarted"
    Log-Info "Try restarting Docker Desktop if problems persist"
}

Write-Host ""
Log-Info "Cleanup completed! You can now run .\start-docker.ps1"
Log-Info "If Docker is still unresponsive, restart Docker Desktop"
