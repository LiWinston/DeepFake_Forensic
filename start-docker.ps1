# DeepFake Forensic Docker Environment Startup Script (PowerShell)
# Automated Docker container management with health checks and initialization

param(
    [switch]$Clean,
    [switch]$NoLogs,
    [switch]$Help
)

# Configuration
$ComposeFile = "docker-compose.yml"
$ProjectName = "deepfake_forensic"
$LogPrefix = "[DeepFake-Forensic]"

# Function: Show help
function Show-Help {
    Write-Host @"
DeepFake Forensic Docker Environment Startup Script

Usage: .\start-docker.ps1 [OPTIONS]

Options:
  -Clean      Clean up existing containers and volumes before starting
  -NoLogs     Don't follow logs after startup
  -Help       Show this help message

Examples:
  .\start-docker.ps1                    # Normal startup
  .\start-docker.ps1 -Clean             # Clean startup
  .\start-docker.ps1 -Clean -NoLogs     # Clean startup without logs

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

Write-Host "=== DeepFake Forensic Docker Environment Startup ===" -ForegroundColor Blue
Write-Host "Current Time: $(Get-Date)" -ForegroundColor Blue
Write-Host "Working Directory: $(Get-Location)" -ForegroundColor Blue
Write-Host ""

# Function: Check if Docker is running
function Test-Docker {
    Log-Step "=== Step 1: Checking Docker Status ==="
    
    try {
        $null = docker info 2>$null
        Log-Info "✓ Docker is running"
    }
    catch {
        Log-Error "Docker is not running. Please start Docker Desktop first."
        exit 1
    }

    try {
        $null = docker-compose --version 2>$null
        Log-Info "✓ Docker Compose is available"
    }
    catch {
        Log-Error "Docker Compose is not available"
        exit 1
    }
}

# Function: Check if compose file exists
function Test-ComposeFile {
    if (!(Test-Path $ComposeFile)) {
        Log-Error "Docker Compose file not found: $ComposeFile"
        exit 1
    }
    Log-Info "✓ Docker Compose file found: $ComposeFile"
}

# Function: Clean up existing containers and volumes
function Clear-Environment {
    Write-Host ""
    Log-Step "=== Step 2: Environment Cleanup ==="
    
    if ($Clean) {
        Log-Warn "Cleaning up existing containers and volumes..."
        try {
            docker-compose -f $ComposeFile down -v --remove-orphans 2>$null
        }
        catch {
            # Ignore errors
        }
        
        Log-Warn "Pruning unused Docker resources..."
        try {
            docker system prune -f 2>$null | Out-Null
        }
        catch {
            # Ignore errors
        }
        
        Log-Info "✓ Environment cleaned up"
    }
    else {
        $cleanupChoice = Read-Host "Do you want to clean up existing containers and volumes? (y/N)"
        if ($cleanupChoice -match "^[Yy]$") {
            Log-Warn "Stopping and removing existing containers..."
            try {
                docker-compose -f $ComposeFile down -v --remove-orphans 2>$null
            }
            catch {
                # Ignore errors
            }
            
            Log-Warn "Pruning unused Docker resources..."
            try {
                docker system prune -f 2>$null | Out-Null
            }
            catch {
                # Ignore errors
            }
            
            Log-Info "✓ Environment cleaned up"
        }
        else {
            Log-Info "Skipping cleanup"
            
            # Stop existing containers gracefully
            Log-Info "Stopping existing containers..."
            try {
                docker-compose -f $ComposeFile down 2>$null
            }
            catch {
                # Ignore errors
            }
        }
    }
}

# Function: Create necessary directories
function New-Directories {
    Write-Host ""
    Log-Step "=== Step 3: Creating Directories ==="
    
    $directories = @(
        "docker\mysql\conf",
        "docker\mysql\init",
        "logs",
        "uploads"
    )
    
    foreach ($dir in $directories) {
        if (!(Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
            Log-Info "✓ Created directory: $dir"
        }
        else {
            Log-Info "✓ Directory exists: $dir"
        }
    }
}

# Function: Create MySQL configuration
function New-MySQLConfig {
    Write-Host ""
    Log-Step "=== Step 4: MySQL Configuration ==="
    
    $mysqlConfFile = "docker\mysql\conf\my.cnf"
    if (!(Test-Path $mysqlConfFile)) {
        $mysqlConf = @"
[mysqld]
# Basic Settings
default-storage-engine=INNODB
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci

# Connection Settings
max_connections=200
max_connect_errors=10

# Buffer Settings
innodb_buffer_pool_size=256M
key_buffer_size=32M

# Log Settings
slow_query_log=1
slow_query_log_file=/var/log/mysql/slow.log
long_query_time=2

# Security Settings
local_infile=0

[mysql]
default-character-set=utf8mb4

[client]
default-character-set=utf8mb4
"@
        $mysqlConf | Out-File -FilePath $mysqlConfFile -Encoding UTF8
        Log-Info "✓ Created MySQL configuration: $mysqlConfFile"
    }
    else {
        Log-Info "✓ MySQL configuration exists: $mysqlConfFile"
    }
}

# Function: Start Docker services
function Start-Services {
    Write-Host ""
    Log-Step "=== Step 5: Starting Docker Services ==="
    
    Log-Info "Building and starting services..."
    docker-compose -f $ComposeFile up -d --build
    
    Write-Host ""
    Log-Info "Services started. Checking health status..."
}

# Function: Wait for services to be healthy
function Wait-ForServices {
    Write-Host ""
    Log-Step "=== Step 6: Waiting for Services to be Ready ==="
    
    $services = @("forensic_mysql", "forensic_redis", "forensic_kafka", "forensic_minio")
    $maxWait = 300  # 5 minutes
    $waitInterval = 10
    
    foreach ($service in $services) {
        Log-Info "Waiting for $service to be healthy..."
        $elapsed = 0
        
        while ($elapsed -lt $maxWait) {
            try {
                $healthStatus = docker inspect --format='{{.State.Health.Status}}' $service 2>$null
                if ($healthStatus -eq "healthy") {
                    Log-Info "✓ $service is healthy"
                    break
                }
            }
            catch {
                # Continue waiting
            }
            
            if ($elapsed -eq 0) {
                Write-Host "    Waiting" -NoNewline
            }
            Write-Host "." -NoNewline
            
            Start-Sleep $waitInterval
            $elapsed += $waitInterval
        }
        
        if ($elapsed -ge $maxWait) {
            Write-Host ""
            Log-Error "✗ $service failed to become healthy within ${maxWait}s"
            Log-Error "Please check the logs: docker logs $service"
            exit 1
        }
        Write-Host ""
    }
}

# Function: Initialize services
function Initialize-Services {
    Write-Host ""
    Log-Step "=== Step 7: Initializing Services ==="
    
    # Wait for initialization containers to complete
    Log-Info "Running Kafka topic initialization..."
    docker-compose -f $ComposeFile up forensic_kafka_init
    
    Log-Info "Running MinIO bucket initialization..."
    docker-compose -f $ComposeFile up forensic_minio_init
    
    Log-Info "✓ Service initialization completed"
}

# Function: Show service status and endpoints
function Show-Status {
    Write-Host ""
    Log-Step "=== Step 8: Service Status and Endpoints ==="
    
    Log-Info "Checking container status..."
    docker-compose -f $ComposeFile ps
    
    Write-Host ""
    Log-Info "Service Endpoints:"
    Write-Host "  📊 MySQL Database:     localhost:3306" -ForegroundColor Green
    Write-Host "  🚀 Redis Cache:        localhost:6379" -ForegroundColor Green
    Write-Host "  📨 Kafka Broker:       localhost:9092" -ForegroundColor Green
    Write-Host "  💾 MinIO Storage:      localhost:9000" -ForegroundColor Green
    Write-Host "  🎛️  MinIO Console:      localhost:9001" -ForegroundColor Green
    
    Write-Host ""
    Log-Info "Default Credentials:"
    Write-Host "  MySQL: root / lyc980820" -ForegroundColor Yellow
    Write-Host "  MinIO: minioadmin / minioadmin" -ForegroundColor Yellow
    
    Write-Host ""
    Log-Info "Kafka Topics:"
    try {
        $topics = docker exec forensic_kafka kafka-topics.sh --list --bootstrap-server localhost:9092 2>$null
        $topics | ForEach-Object { Write-Host "  $_" }
    }
    catch {
        Write-Host "  Failed to retrieve topics" -ForegroundColor Red
    }
}

# Function: Show logs
function Show-Logs {
    if (!$NoLogs) {
        Write-Host ""
        $followLogs = Read-Host "Do you want to follow the logs? (y/N)"
        if ($followLogs -match "^[Yy]$") {
            Log-Step "=== Following Service Logs (Ctrl+C to stop) ==="
            docker-compose -f $ComposeFile logs -f
        }
    }
}

# Function: Cleanup on exit
function Exit-Gracefully {
    Write-Host ""
    Log-Warn "To stop all services, run: docker-compose -f $ComposeFile down"
    exit 0
}

# Main execution
try {
    Test-Docker
    Test-ComposeFile
    Clear-Environment
    New-Directories
    New-MySQLConfig
    Start-Services
    Wait-ForServices
    Initialize-Services
    Show-Status
    Show-Logs
}
catch {
    Log-Error "An error occurred: $($_.Exception.Message)"
    exit 1
}

Exit-Gracefully
