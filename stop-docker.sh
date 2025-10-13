#!/bin/bash

# DeepFake Forensic Docker Environment Stop Script
# Gracefully stop and manage Docker containers

# Configuration
COMPOSE_FILE="docker-compose.yml"
PROJECT_NAME="deepfake_forensic"
LOG_PREFIX="[DeepFake-Forensic]"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Command line options
CLEAN=false
VOLUMES=false
HELP=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--clean)
            CLEAN=true
            shift
            ;;
        -v|--volumes)
            VOLUMES=true
            shift
            ;;
        -h|--help)
            HELP=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use -h or --help for usage information"
            exit 1
            ;;
    esac
done

# Function: Show help
show_help() {
    cat << EOF
${CYAN}DeepFake Forensic Docker Environment Stop Script

${WHITE}Usage:${NC} ./stop-docker.sh [OPTIONS]

${WHITE}Options:${NC}
  -c, --clean      Remove containers and networks (keeps volumes)
  -v, --volumes    Remove containers, networks, and volumes (DATA LOSS!)
  -h, --help       Show this help message

${WHITE}Examples:${NC}
  ./stop-docker.sh           # Just stop containers
  ./stop-docker.sh --clean   # Stop and remove containers
  ./stop-docker.sh --volumes # Stop and remove everything (including data)

EOF
}

if [ "$HELP" = true ]; then
    show_help
    exit 0
fi

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

log_step() {
    echo -e "${BLUE}$1${NC}"
}

echo -e "${BLUE}=== DeepFake Forensic Docker Environment Stop Script ===${NC}"
echo -e "${BLUE}Current Time: $(date)${NC}"
echo

# Function: Check if Docker is running
check_docker() {
    log_step "=== Checking Docker Status ==="
    
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker is not running."
        exit 1
    fi
    log_info "✓ Docker is running"

    if [ ! -f "$COMPOSE_FILE" ]; then
        log_error "Docker Compose file not found: $COMPOSE_FILE"
        exit 1
    fi
    log_info "✓ Docker Compose file found: $COMPOSE_FILE"
}

# Function: Show current status
show_current_status() {
    log_step "=== Current Container Status ==="
    
    if docker-compose -f "$COMPOSE_FILE" ps 2>/dev/null | grep -q "Up\|Exit"; then
        docker-compose -f "$COMPOSE_FILE" ps
    else
        log_warn "No containers found or compose file not accessible"
    fi
    
    echo
}

# Function: Stop services
stop_services() {
    if [ "$VOLUMES" = true ]; then
        log_step "=== Stopping and Removing All (INCLUDING VOLUMES) ==="
        log_warn "⚠️  WARNING: This will DELETE ALL DATA in volumes!"
        read -p "Are you absolutely sure? Type 'DELETE' to confirm: " confirm
        
        if [ "$confirm" = "DELETE" ]; then
            log_warn "Removing containers, networks, and volumes..."
            docker-compose -f "$COMPOSE_FILE" down -v --remove-orphans
            log_info "✓ All services stopped and data volumes removed"
        else
            log_info "Operation cancelled"
            exit 0
        fi
    elif [ "$CLEAN" = true ]; then
        log_step "=== Stopping and Removing Containers ==="
        log_info "Removing containers and networks (keeping volumes)..."
        docker-compose -f "$COMPOSE_FILE" down --remove-orphans
        log_info "✓ Services stopped and containers removed"
    else
        log_step "=== Stopping Services ==="
        log_info "Stopping containers (keeping containers and volumes)..."
        docker-compose -f "$COMPOSE_FILE" stop
        log_info "✓ Services stopped (containers preserved)"
    fi
}

# Function: Show cleanup options
show_cleanup_options() {
    echo
    log_step "=== Cleanup Options ==="
    
    echo -e "${CYAN}Available cleanup commands:${NC}"
    echo -e "${WHITE}  ./stop-docker.sh           # Stop containers (can restart easily)${NC}"
    echo -e "${WHITE}  ./stop-docker.sh --clean   # Remove containers (keeps data)${NC}"
    echo -e "${WHITE}  ./stop-docker.sh --volumes # Remove everything (DELETES DATA!)${NC}"
    echo
    echo -e "${CYAN}Manual Docker commands:${NC}"
    echo -e "${WHITE}  docker-compose -f $COMPOSE_FILE start   # Restart stopped containers${NC}"
    echo -e "${WHITE}  docker-compose -f $COMPOSE_FILE logs -f # View logs${NC}"
    echo -e "${WHITE}  docker system prune -f                 # Clean unused Docker resources${NC}"
    echo
}

# Function: Show final status
show_final_status() {
    log_step "=== Final Status ==="
    
    if docker-compose -f "$COMPOSE_FILE" ps -q 2>/dev/null | grep -q .; then
        log_info "Remaining containers:"
        docker-compose -f "$COMPOSE_FILE" ps
    else
        log_info "No containers running for this project"
    fi
    
    echo
    log_info "To restart the environment, run: ./start-docker.sh"
}

# Main execution
main() {
    check_docker
    show_current_status
    stop_services
    show_cleanup_options
    show_final_status
}

# Run main function
main

