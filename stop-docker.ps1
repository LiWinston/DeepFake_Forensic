# DeepFake Forensic Docker Environment Stop Script (PowerShell)
# Gracefully stop and manage Docker containers

param(
    [switch]$Clean,
    [switch]$Volumes,
    [switch]$Help
)

# Configuration
$ComposeFile = "docker-compose.yml"
$ProjectName = "deepfake_forensic"
$LogPrefix = "[DeepFake-Forensic]"

# Function: Show help
function Show-Help {
    Write-Host @"
DeepFake Forensic Docker Environment Stop Script

Usage: .\stop-docker.ps1 [OPTIONS]

Options:
  -Clean      Remove containers and networks (keeps volumes)
  -Volumes    Remove containers, networks, and volumes (DATA LOSS!)
  -Help       Show this help message

Examples:
  .\stop-docker.ps1           # Just stop containers
  .\stop-docker.ps1 -Clean    # Stop and remove containers
  .\stop-docker.ps1 -Volumes  # Stop and remove everything (including data)

"@ -ForegroundColor Cyan
}

if ($Help) {
    Show-Help
    exit 0
}

# Function: Print colored log message
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

function Log-Step {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Blue
}

Write-Host "=== DeepFake Forensic Docker Environment Stop Script ===" -ForegroundColor Blue
Write-Host "Current Time: $(Get-Date)" -ForegroundColor Blue
Write-Host ""

# Function: Check if Docker is running
function Test-Docker {
    Log-Step "=== Checking Docker Status ==="
    
    try {
        $null = docker info 2>$null
        Log-Info "✓ Docker is running"
    }
    catch {
        Log-Error "Docker is not running."
        exit 1
    }

    if (!(Test-Path $ComposeFile)) {
        Log-Error "Docker Compose file not found: $ComposeFile"
        exit 1
    }
    Log-Info "✓ Docker Compose file found: $ComposeFile"
}

# Function: Show current status
function Show-CurrentStatus {
    Log-Step "=== Current Container Status ==="
    
    try {
        docker-compose -f $ComposeFile ps
    }
    catch {
        Log-Warn "No containers found or compose file not accessible"
    }
    
    Write-Host ""
}

# Function: Stop services
function Stop-Services {
    if ($Volumes) {
        Log-Step "=== Stopping and Removing All (INCLUDING VOLUMES) ==="
        Log-Warn "⚠️  WARNING: This will DELETE ALL DATA in volumes!"
        $confirm = Read-Host "Are you absolutely sure? Type 'DELETE' to confirm"
        
        if ($confirm -eq "DELETE") {
            Log-Warn "Removing containers, networks, and volumes..."
            docker-compose -f $ComposeFile down -v --remove-orphans
            Log-Info "✓ All services stopped and data volumes removed"
        }
        else {
            Log-Info "Operation cancelled"
            exit 0
        }
    }
    elseif ($Clean) {
        Log-Step "=== Stopping and Removing Containers ==="
        Log-Info "Removing containers and networks (keeping volumes)..."
        docker-compose -f $ComposeFile down --remove-orphans
        Log-Info "✓ Services stopped and containers removed"
    }
    else {
        Log-Step "=== Stopping Services ==="
        Log-Info "Stopping containers (keeping containers and volumes)..."
        docker-compose -f $ComposeFile stop
        Log-Info "✓ Services stopped (containers preserved)"
    }
}

# Function: Show cleanup options
function Show-CleanupOptions {
    Write-Host ""
    Log-Step "=== Cleanup Options ==="
    
    Write-Host "Available cleanup commands:" -ForegroundColor Cyan
    Write-Host "  .\stop-docker.ps1          # Stop containers (can restart easily)" -ForegroundColor White
    Write-Host "  .\stop-docker.ps1 -Clean   # Remove containers (keeps data)" -ForegroundColor White
    Write-Host "  .\stop-docker.ps1 -Volumes # Remove everything (DELETES DATA!)" -ForegroundColor White
    Write-Host ""
    Write-Host "Manual Docker commands:" -ForegroundColor Cyan
    Write-Host "  docker-compose -f $ComposeFile start   # Restart stopped containers" -ForegroundColor White
    Write-Host "  docker-compose -f $ComposeFile logs -f # View logs" -ForegroundColor White
    Write-Host "  docker system prune -f                # Clean unused Docker resources" -ForegroundColor White
    Write-Host ""
}

# Function: Show final status
function Show-FinalStatus {
    Log-Step "=== Final Status ==="
    
    try {
        $containers = docker-compose -f $ComposeFile ps -q
        if ($containers) {
            Log-Info "Remaining containers:"
            docker-compose -f $ComposeFile ps
        }
        else {
            Log-Info "No containers running for this project"
        }
    }
    catch {
        Log-Info "No containers found"
    }
    
    Write-Host ""
    Log-Info "To restart the environment, run: .\start-docker.ps1"
}

# Main execution
try {
    Test-Docker
    Show-CurrentStatus
    Stop-Services
    Show-CleanupOptions
    Show-FinalStatus
}
catch {
    Log-Error "An error occurred: $($_.Exception.Message)"
    exit 1
}
