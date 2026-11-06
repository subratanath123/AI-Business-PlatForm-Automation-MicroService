#!/bin/bash

set -euo pipefail

# ============================================================================
# Configuration
# ============================================================================
readonly ENVIRONMENT="${1:-prod}"
readonly COMPOSE_FILE="docker-compose.${ENVIRONMENT}.yml"
readonly WORK_DIR="${PWD}"  # Use current working directory
readonly HEALTH_CHECK_TIMEOUT=60
readonly HEALTH_CHECK_INTERVAL=5

# ============================================================================
# Functions
# ============================================================================

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"
}

log_error() {
    echo "[ERROR] $*" >&2
}

log_success() {
    echo "[SUCCESS] $*"
}

validate_environment() {
    if [[ ! "$ENVIRONMENT" =~ ^(dev|qa|prod)$ ]]; then
        log_error "Invalid environment: $ENVIRONMENT. Must be dev, qa, or prod"
        exit 1
    fi
}

build_docker_image() {
    log "Building Docker image..."
    docker compose -f "${COMPOSE_FILE}" build
}

start_containers() {
    log "Starting containers..."

    docker compose -f "${COMPOSE_FILE}" up -d

    log_success "Containers started"
}

cleanup_old_images() {
    # Each deploy leaves the previous image dangling; drop those so the droplet disk
    # doesn't fill up. Build cache (the reused dependency layers) is not touched.
    docker image prune -f > /dev/null || true
}

show_status() {
    echo ""
    echo "=========================================="
    log_success "Deployment completed!"
    echo "  Environment: ${ENVIRONMENT}"
    echo "  Compose file: ${COMPOSE_FILE}"
    echo "  Working directory: ${WORK_DIR}"
    echo "=========================================="
    echo ""
    
    echo "Container status:"
    $DOCKER_COMPOSE -f "${COMPOSE_FILE}" ps
    echo ""
    
    echo "Useful commands:"
    echo "  View logs:    $DOCKER_COMPOSE -f ${COMPOSE_FILE} logs -f"
    echo "  Stop:         $DOCKER_COMPOSE -f ${COMPOSE_FILE} down"
    echo "  Restart:      $DOCKER_COMPOSE -f ${COMPOSE_FILE} restart"
    echo "  Status:       $DOCKER_COMPOSE -f ${COMPOSE_FILE} ps"
    echo ""
}

# ============================================================================
# Main Deployment Flow
# ============================================================================

main() {
    echo "=========================================="
    echo "Docker Deployment"
    echo "Environment: ${ENVIRONMENT}"
    echo "=========================================="
    echo ""

    validate_environment
    build_docker_image
    start_containers
    cleanup_old_images

}

# Run main function
main "$@"
