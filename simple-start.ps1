# Simple Docker Startup Script for DeepFake Forensic
# Simplified version without complex health checks

param(
    [switch]$NoCleanup
)

$ComposeFile = "docker-compose.yml"
$LogPrefix = "[Simple-Start]"

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

Write-Host "=== DeepFake Forensic Simple Docker Startup ===" -ForegroundColor Blue
Write-Host ""

# Step 1: Check Docker
Log-Info "Checking Docker..."
try {
    docker info > $null 2>&1
    Log-Info "✓ Docker is running"
}
catch {
    Log-Error "Docker is not running. Please start Docker Desktop."
    exit 1
}

# Step 2: Optional cleanup
if (!$NoCleanup) {
    Log-Info "Cleaning up existing containers..."
    docker-compose -f $ComposeFile down 2>$null
    Log-Info "✓ Cleanup completed"
}

# Step 3: Create directories
Log-Info "Creating directories..."
$directories = @("docker\mysql\conf", "docker\mysql\init", "logs", "uploads")
foreach ($dir in $directories) {
    if (!(Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}
Log-Info "✓ Directories ready"

# Step 4: Start services
Log-Info "Starting Docker services..."
docker-compose -f $ComposeFile up -d

# Step 5: Wait a bit
Log-Info "Waiting for services to start..."
Start-Sleep 30

# Step 6: Check status
Log-Info "Checking service status..."
docker-compose -f $ComposeFile ps

# Step 7: Show endpoints
Write-Host ""
Log-Info "Service Endpoints:"
Write-Host "  📊 MySQL Database:     localhost:3306" -ForegroundColor Green
Write-Host "  🚀 Redis Cache:        localhost:6379" -ForegroundColor Green
Write-Host "  📨 Kafka Broker:       localhost:9092" -ForegroundColor Green
Write-Host "  💾 MinIO Storage:      localhost:9000" -ForegroundColor Green
Write-Host "  🎛️  MinIO Console:      localhost:9001" -ForegroundColor Green
Write-Host ""
Write-Host "Default Credentials:" -ForegroundColor Yellow
Write-Host "  MySQL: root / lyc980820" -ForegroundColor Yellow
Write-Host "  MinIO: minioadmin / minioadmin" -ForegroundColor Yellow
Write-Host ""

Log-Info "Simple startup completed!"
Log-Info "Use 'docker-compose logs -f' to view logs"
Log-Info "Use '.\stop-docker.ps1' to stop services"
