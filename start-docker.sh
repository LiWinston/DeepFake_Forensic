#!/bin/bash

# DeepFake Forensic Docker Environment Startup Script
# Automated Docker container management with health checks and initialization

set -e  # Exit on error

# Configuration
COMPOSE_FILE="docker-compose.yml"
PROJECT_NAME="deepfake_forensic"
LOG_PREFIX="[DeepFake-Forensic]"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== DeepFake Forensic Docker Environment Startup ===${NC}"
echo -e "${BLUE}Current Time: $(date)${NC}"
echo -e "${BLUE}Working Directory: $(pwd)${NC}"
echo

# Function: Print colored log message
log_info() {
    echo -e "${GREEN}${LOG_PREFIX} $1${NC}"
}

log_warn() {
    echo -e "${YELLOW}${LOG_PREFIX} $1${NC}"
}

log_error() {
    echo -e "${RED}${LOG_PREFIX} $1${NC}"
}

# Function: Check if Docker is running
check_docker() {
    echo -e "${BLUE}=== Step 1: Checking Docker Status ===${NC}"
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker is not running. Please start Docker Desktop first."
        exit 1
    fi
    log_info "✓ Docker is running"

    if ! docker-compose --version > /dev/null 2>&1; then
        log_error "Docker Compose is not available"
        exit 1
    fi
    log_info "✓ Docker Compose is available"
}

# Function: Check if compose file exists
check_compose_file() {
    if [ ! -f "$COMPOSE_FILE" ]; then
        log_error "Docker Compose file not found: $COMPOSE_FILE"
        exit 1
    fi
    log_info "✓ Docker Compose file found: $COMPOSE_FILE"
}

# Function: Clean up existing containers and volumes (optional)
cleanup_environment() {
    echo
    echo -e "${BLUE}=== Step 2: Environment Cleanup ===${NC}"
    
    read -p "Do you want to clean up existing containers and volumes? (y/N): " cleanup_choice
    if [[ $cleanup_choice =~ ^[Yy]$ ]]; then
        perform_cleanup
    else
        log_info "Skipping cleanup"
        
        # Stop existing containers gracefully
        log_info "Stopping existing containers..."
        timeout 30 docker-compose -f "$COMPOSE_FILE" down 2>/dev/null || {
            log_warn "Container stop timed out or failed"
        }
    fi
}

# Function: Perform cleanup operations with timeouts
perform_cleanup() {
    log_warn "Stopping and removing existing containers..."
    
    # Stop and remove containers with timeout
    timeout 60 docker-compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || {
        log_warn "Container cleanup timed out, forcing removal..."
        docker ps -aq --filter "name=${PROJECT_NAME}" | xargs -r docker rm -f 2>/dev/null || true
    }
    
    log_warn "Pruning unused Docker resources..."
    
    # Prune with timeout
    timeout 120 docker system prune -f --volumes 2>/dev/null || {
        log_warn "System prune timed out, skipping..."
    }
    
    # Clean specific project volumes
    log_info "Cleaning project volumes..."
    docker volume ls --filter name="${PROJECT_NAME}_" -q | xargs -r docker volume rm 2>/dev/null || true
    
    log_info "✓ Environment cleaned up"
}

# Function: Create necessary directories
create_directories() {
    echo
    echo -e "${BLUE}=== Step 3: Creating Directories ===${NC}"
    
    directories=(
        "docker/mysql/conf"
        "docker/mysql/init"
        "logs"
        "uploads"
    )
    
    for dir in "${directories[@]}"; do
        if [ ! -d "$dir" ]; then
            mkdir -p "$dir"
            log_info "✓ Created directory: $dir"
        else
            log_info "✓ Directory exists: $dir"
        fi
    done
}

# Function: Create MySQL configuration
create_mysql_config() {
    echo
    echo -e "${BLUE}=== Step 4: MySQL Configuration ===${NC}"
    
    mysql_conf_file="docker/mysql/conf/my.cnf"
    if [ ! -f "$mysql_conf_file" ]; then
        cat > "$mysql_conf_file" << EOF
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
EOF
        log_info "✓ Created MySQL configuration: $mysql_conf_file"
    else
        log_info "✓ MySQL configuration exists: $mysql_conf_file"
    fi
}

# Function: Start Docker services
start_services() {
    echo
    echo -e "${BLUE}=== Step 5: Starting Docker Services ===${NC}"
    
    log_info "Building and starting services..."
    docker-compose -f "$COMPOSE_FILE" up -d --build
    
    echo
    log_info "Services started. Checking health status..."
}

# Function: Wait for services to be healthy
wait_for_services() {
    echo
    echo -e "${BLUE}=== Step 6: Waiting for Services to be Ready ===${NC}"
    
    services=("forensic_mysql" "forensic_redis" "forensic_kafka" "forensic_minio")
    max_wait=300  # 5 minutes
    wait_interval=10
    
    for service in "${services[@]}"; do
        log_info "Waiting for $service to be healthy..."
        elapsed=0
        
        while [ $elapsed -lt $max_wait ]; do
            if docker inspect --format='{{.State.Health.Status}}' "$service" 2>/dev/null | grep -q "healthy"; then
                log_info "✓ $service is healthy"
                break
            fi
            
            if [ $elapsed -eq 0 ]; then
                echo -n "    Waiting"
            fi
            echo -n "."
            
            sleep $wait_interval
            elapsed=$((elapsed + wait_interval))
        done
        
        if [ $elapsed -ge $max_wait ]; then
            echo
            log_error "✗ $service failed to become healthy within ${max_wait}s"
            log_error "Please check the logs: docker logs $service"
            exit 1
        fi
        echo
    done
}

# Function: Initialize services
initialize_services() {
    echo
    echo -e "${BLUE}=== Step 7: Initializing Services ===${NC}"
    
    # Wait for initialization containers to complete
    log_info "Running Kafka topic initialization..."
    docker-compose -f "$COMPOSE_FILE" up forensic_kafka_init
    
    log_info "Running MinIO bucket initialization..."
    docker-compose -f "$COMPOSE_FILE" up forensic_minio_init
    
    log_info "✓ Service initialization completed"
}

# Function: Show service status and endpoints
show_status() {
    echo
    echo -e "${BLUE}=== Step 8: Service Status and Endpoints ===${NC}"
    
    log_info "Checking container status..."
    docker-compose -f "$COMPOSE_FILE" ps
    
    echo
    log_info "Service Endpoints:"
    echo -e "${GREEN}  📊 MySQL Database:     localhost:3306${NC}"
    echo -e "${GREEN}  🚀 Redis Cache:        localhost:6379${NC}"
    echo -e "${GREEN}  📨 Kafka Broker:       localhost:9092${NC}"
    echo -e "${GREEN}  💾 MinIO Storage:      localhost:9000${NC}"
    echo -e "${GREEN}  🎛️  MinIO Console:      localhost:9001${NC}"
    
    echo
    log_info "Default Credentials:"
    echo -e "${YELLOW}  MySQL: root / lyc980820${NC}"
    echo -e "${YELLOW}  MinIO: minioadmin / minioadmin${NC}"
    
    echo
    log_info "Kafka Topics:"
    docker exec forensic_kafka kafka-topics.sh --list --bootstrap-server localhost:9092 2>/dev/null | sed 's/^/  /'
}

# Function: Show logs
show_logs() {
    echo
    read -p "Do you want to follow the logs? (y/N): " follow_logs
    if [[ $follow_logs =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}=== Following Service Logs (Ctrl+C to stop) ===${NC}"
        docker-compose -f "$COMPOSE_FILE" logs -f
    fi
}

# Function: Cleanup on exit
cleanup_on_exit() {
    echo -e "\n${YELLOW}${LOG_PREFIX} Received stop signal...${NC}"
    echo -e "${YELLOW}${LOG_PREFIX} To stop all services, run: docker-compose -f $COMPOSE_FILE down${NC}"
    exit 0
}

# Main execution function
main() {
    check_docker
    check_compose_file
    cleanup_environment
    create_directories
    create_mysql_config
    start_services
    wait_for_services
    initialize_services
    show_status
    show_logs
}

# Trap signals for graceful shutdown
trap cleanup_on_exit INT TERM

# Execute main function
main
