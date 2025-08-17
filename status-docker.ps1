# DeepFake Forensic Docker Environment Status Script (PowerShell)
# Check the status of all services and show useful information

param(
    [switch]$Logs,
    [switch]$Health,
    [switch]$Help
)

# Configuration
$ComposeFile = "docker-compose.yml"
$LogPrefix = "[DeepFake-Forensic]"

# Function: Show help
function Show-Help {
    Write-Host @"
DeepFake Forensic Docker Environment Status Script

Usage: .\status-docker.ps1 [OPTIONS]

Options:
  -Logs      Show recent logs from all services
  -Health    Show detailed health information
  -Help      Show this help message

Examples:
  .\status-docker.ps1          # Basic status
  .\status-docker.ps1 -Logs    # Status with logs
  .\status-docker.ps1 -Health  # Detailed health check

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

Write-Host "=== DeepFake Forensic Docker Environment Status ===" -ForegroundColor Blue
Write-Host "Current Time: $(Get-Date)" -ForegroundColor Blue
Write-Host ""

# Function: Check Docker status
function Test-Docker {
    try {
        $null = docker info 2>$null
        Log-Info "✓ Docker is running"
    }
    catch {
        Log-Error "✗ Docker is not running"
        return $false
    }

    if (!(Test-Path $ComposeFile)) {
        Log-Error "✗ Docker Compose file not found: $ComposeFile"
        return $false
    }
    
    return $true
}

# Function: Show container status
function Show-ContainerStatus {
    Log-Step "=== Container Status ==="
    
    try {
        $containers = docker-compose -f $ComposeFile ps
        if ($containers) {
            docker-compose -f $ComposeFile ps
        }
        else {
            Log-Warn "No containers found for this project"
        }
    }
    catch {
        Log-Error "Failed to get container status"
    }
    
    Write-Host ""
}

# Function: Show service endpoints
function Show-ServiceEndpoints {
    Log-Step "=== Service Endpoints ==="
    
    $services = @(
        @{ Name = "MySQL Database"; Port = 3306; Icon = "📊" },
        @{ Name = "Redis Cache"; Port = 6379; Icon = "🚀" },
        @{ Name = "Kafka Broker"; Port = 9092; Icon = "📨" },
        @{ Name = "MinIO Storage"; Port = 9000; Icon = "💾" },
        @{ Name = "MinIO Console"; Port = 9001; Icon = "🎛️" }
    )
    
    foreach ($service in $services) {
        $status = Test-NetConnection -ComputerName localhost -Port $service.Port -InformationLevel Quiet
        $statusText = if ($status) { "✓ ACTIVE" } else { "✗ INACTIVE" }
        $color = if ($status) { "Green" } else { "Red" }
        
        Write-Host "  $($service.Icon) $($service.Name):" -NoNewline
        Write-Host " localhost:$($service.Port) " -NoNewline
        Write-Host $statusText -ForegroundColor $color
    }
    
    Write-Host ""
}

# Function: Show detailed health information
function Show-HealthInfo {
    if (!$Health) { return }
    
    Log-Step "=== Detailed Health Information ==="
    
    $containers = @("forensic_mysql", "forensic_redis", "forensic_kafka", "forensic_minio")
    
    foreach ($container in $containers) {
        try {
            $healthStatus = docker inspect --format='{{.State.Health.Status}}' $container 2>$null
            $statusIcon = switch ($healthStatus) {
                "healthy" { "✅" }
                "unhealthy" { "❌" }
                "starting" { "🔄" }
                default { "❓" }
            }
            
            Write-Host "  $statusIcon $container" -NoNewline
            if ($healthStatus) {
                Write-Host ": $healthStatus" -ForegroundColor $(if ($healthStatus -eq "healthy") { "Green" } else { "Yellow" })
            }
            else {
                Write-Host ": No health check" -ForegroundColor Gray
            }
        }
        catch {
            Write-Host "  ❓ $container : Not found" -ForegroundColor Red
        }
    }
    
    Write-Host ""
}

# Function: Show volume information
function Show-VolumeInfo {
    Log-Step "=== Volume Information ==="
    
    try {
        $volumes = docker volume ls --filter name="${ProjectName}_" --format "table {{.Name}}\t{{.Driver}}\t{{.Size}}"
        if ($volumes) {
            $volumes
        }
        else {
            Log-Warn "No volumes found for this project"
        }
    }
    catch {
        Log-Error "Failed to get volume information"
    }
    
    Write-Host ""
}

# Function: Show Kafka topics
function Show-KafkaTopics {
    Log-Step "=== Kafka Topics ==="
      try {
        $topics = docker exec forensic_kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092 2>$null
        if ($topics) {
            Log-Info "Available topics:"
            $topics | ForEach-Object { Write-Host "  📨 $_" }
        }
        else {
            Log-Warn "No topics found or Kafka not accessible"
        }
    }
    catch {
        Log-Warn "Failed to retrieve Kafka topics (container may not be running)"
    }
    
    Write-Host ""
}

# Function: Show recent logs
function Show-RecentLogs {
    if (!$Logs) { return }
    
    Log-Step "=== Recent Logs (last 50 lines per service) ==="
    
    $services = @("forensic_mysql", "forensic_redis", "forensic_kafka", "forensic_minio")
    
    foreach ($service in $services) {
        Write-Host "--- $service ---" -ForegroundColor Cyan
        try {
            docker logs --tail 50 $service 2>$null
        }
        catch {
            Write-Host "No logs available for $service" -ForegroundColor Yellow
        }
        Write-Host ""
    }
}

# Function: Show useful commands
function Show-UsefulCommands {
    Log-Step "=== Useful Commands ==="
    
    Write-Host "Management:" -ForegroundColor Cyan
    Write-Host "  .\start-docker.ps1           # Start all services" -ForegroundColor White
    Write-Host "  .\stop-docker.ps1            # Stop all services" -ForegroundColor White
    Write-Host "  .\status-docker.ps1 -Logs    # Show status with logs" -ForegroundColor White
    Write-Host ""
    
    Write-Host "Docker Commands:" -ForegroundColor Cyan
    Write-Host "  docker-compose -f $ComposeFile logs -f service_name  # Follow specific service logs" -ForegroundColor White
    Write-Host "  docker-compose -f $ComposeFile restart service_name  # Restart specific service" -ForegroundColor White
    Write-Host "  docker exec -it forensic_kafka bash                  # Access Kafka container" -ForegroundColor White
    Write-Host ""
    
    Write-Host "Database Access:" -ForegroundColor Cyan
    Write-Host "  mysql -h localhost -P 3306 -u root -plyc980820 forensic_db" -ForegroundColor White
    Write-Host "  redis-cli -h localhost -p 6379" -ForegroundColor White
    Write-Host ""
}

# Main execution
if (!(Test-Docker)) {
    exit 1
}

Show-ContainerStatus
Show-ServiceEndpoints
Show-HealthInfo
Show-VolumeInfo
Show-KafkaTopics
Show-RecentLogs
Show-UsefulCommands

Log-Info "Status check completed!"
