#!/usr/bin/env bash

# ==============================================================================
# Sky Local Demo - Convenience Script
# ==============================================================================
#
# This script automates the Sky local demo workflow:
# 1. Prerequisite checks (Docker, Maven, Java)
# 2. Build Sky Docker image
# 3. Run automated end-to-end test
# 4. Optional: Start manual cluster for interactive exploration
#
# Usage:
#   ./demo.sh                    # Run automated test only
#   ./demo.sh --manual           # Start manual cluster after test
#   ./demo.sh --manual-only      # Skip test, start manual cluster
#   ./demo.sh --help             # Show this help
#
# ==============================================================================

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ==============================================================================
# Helper Functions
# ==============================================================================

print_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

print_step() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}→ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ Error: $1${NC}"
}

print_timing() {
    echo -e "${YELLOW}⏱  Expected time: $1${NC}"
}

check_command() {
    if ! command -v "$1" &> /dev/null; then
        print_error "$1 is not installed or not in PATH"
        echo "  Please install $1 and try again"
        exit 1
    fi
}

show_help() {
    cat << EOF
Sky Local Demo - Convenience Script

Usage:
  ./demo.sh                    Run automated test only
  ./demo.sh --manual           Start manual cluster after test
  ./demo.sh --manual-only      Skip test, start manual cluster
  ./demo.sh --help             Show this help

The automated test (recommended):
  - Builds Sky Docker image
  - Starts 4-node cluster via TestContainers
  - Runs smoke tests (organizational hierarchy, permissions)
  - Takes 2-5 minutes

The manual cluster:
  - Starts bootstrap and kernel nodes
  - Allows interactive API exploration
  - Requires manual shutdown (Ctrl+C)

Prerequisites:
  - Docker Desktop running
  - Java 22+
  - Maven 3.8.1+
  - 8GB+ RAM for Docker

See README.md for detailed documentation.
EOF
}

# ==============================================================================
# Parse Arguments
# ==============================================================================

MODE="test"  # Default: run automated test

while [[ $# -gt 0 ]]; do
    case $1 in
        --manual)
            MODE="test-then-manual"
            shift
            ;;
        --manual-only)
            MODE="manual-only"
            shift
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo ""
            show_help
            exit 1
            ;;
    esac
done

# ==============================================================================
# Step 1: Prerequisites Check
# ==============================================================================

print_header "Step 1: Checking Prerequisites"

print_info "Checking for required commands..."
check_command docker
check_command java
check_command mvn
check_command docker-compose

print_step "All required commands found"

# Check Docker is running
print_info "Verifying Docker is running..."
if ! docker info &> /dev/null; then
    print_error "Docker is not running"
    echo "  Please start Docker Desktop and try again"
    exit 1
fi
print_step "Docker is running"

# Check Java version
print_info "Checking Java version..."
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [[ "$JAVA_VERSION" -lt 22 ]]; then
    print_error "Java version $JAVA_VERSION found, but Java 22+ is required"
    echo "  Please install Java 22 or later"
    exit 1
fi
print_step "Java $JAVA_VERSION found"

# Check Maven version
print_info "Checking Maven version..."
MVN_VERSION=$(mvn -version | head -n 1 | grep -oP '(?<=Apache Maven )\d+\.\d+' || echo "0.0")
MVN_MAJOR=$(echo "$MVN_VERSION" | cut -d'.' -f1)
MVN_MINOR=$(echo "$MVN_VERSION" | cut -d'.' -f2)
if [[ "$MVN_MAJOR" -lt 3 ]] || [[ "$MVN_MAJOR" -eq 3 && "$MVN_MINOR" -lt 8 ]]; then
    print_error "Maven version $MVN_VERSION found, but Maven 3.8.1+ is required"
    echo "  Please install Maven 3.8.1 or later"
    exit 1
fi
print_step "Maven $MVN_VERSION found"

# Check Docker memory allocation
print_info "Checking Docker memory allocation..."
DOCKER_MEM=$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo "0")
DOCKER_MEM_GB=$((DOCKER_MEM / 1024 / 1024 / 1024))
if [[ "$DOCKER_MEM_GB" -lt 8 ]]; then
    print_error "Docker has ${DOCKER_MEM_GB}GB allocated, but 8GB+ is recommended"
    echo "  Please increase Docker memory: Preferences → Resources → Memory → 8GB+"
    echo "  You can continue, but tests may fail with OutOfMemoryError"
    read -p "  Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    print_step "Docker has ${DOCKER_MEM_GB}GB allocated"
fi

print_step "All prerequisites met"

# ==============================================================================
# Step 2: Build Sky Image (if not manual-only)
# ==============================================================================

if [[ "$MODE" != "manual-only" ]]; then
    print_header "Step 2: Building Sky Docker Image"
    print_timing "2-5 minutes (first build), 30 seconds (cached)"

    print_info "Running: ./mvnw clean install -DskipTests"
    cd ..
    ./mvnw clean install -DskipTests
    cd local-demo

    print_step "Sky image built successfully"

    # Verify image exists
    print_info "Verifying Docker image..."
    if ! docker images | grep -q "com.hellblazer.sky/sky-image"; then
        print_error "Sky Docker image not found after build"
        exit 1
    fi
    IMAGE_TAG=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "com.hellblazer.sky/sky-image" | head -n 1)
    print_step "Image found: $IMAGE_TAG"
fi

# ==============================================================================
# Step 3: Run Automated Test (if test or test-then-manual)
# ==============================================================================

if [[ "$MODE" == "test" || "$MODE" == "test-then-manual" ]]; then
    print_header "Step 3: Running Automated End-to-End Test"
    print_timing "2-5 minutes"

    print_info "Running: ./mvnw -P e2e test -pl local-demo"
    print_info "This will:"
    echo "  1. Start 4 nodes via TestContainers"
    echo "  2. Wait for Genesis block commitment"
    echo "  3. Create organizational hierarchy"
    echo "  4. Test permissions and access control"
    echo "  5. Clean up containers"
    echo ""

    cd ..
    if ./mvnw -P e2e test -pl local-demo; then
        print_step "All tests passed!"
        print_info "Results summary:"
        echo "  - 14 transitive viewers verified"
        echo "  - Permission revocation working"
        echo "  - Cluster formation successful"
    else
        print_error "Tests failed"
        echo ""
        echo "Common issues:"
        echo "  1. Docker out of memory → increase Docker memory to 8GB+"
        echo "  2. Port conflicts → ensure ports 50000-50004 are available"
        echo "  3. Stale containers → run 'docker system prune -f'"
        echo ""
        echo "See TROUBLESHOOTING.md for detailed solutions"
        exit 1
    fi
    cd local-demo
fi

# ==============================================================================
# Step 4: Start Manual Cluster (if manual or manual-only)
# ==============================================================================

if [[ "$MODE" == "test-then-manual" || "$MODE" == "manual-only" ]]; then
    print_header "Step 4: Starting Manual Cluster"
    print_timing "Cluster startup: 30-60 seconds"

    print_info "This will start a 4-node cluster for interactive exploration"
    echo "  - Bootstrap node: http://localhost:50000 (API)"
    echo "  - Health check: http://localhost:50004/health"
    echo ""
    print_info "To stop the cluster: Press Ctrl+C in both terminals"
    echo ""

    # Check if bootstrap is already running
    if docker ps | grep -q "bootstrap"; then
        print_error "Bootstrap node is already running"
        echo "  Please stop it first: cd bootstrap && docker-compose down"
        exit 1
    fi

    print_info "Starting bootstrap node..."
    echo "  Terminal 1 (this window) will show bootstrap logs"
    echo "  Terminal 2 (new window) will start kernel nodes"
    echo ""

    # Create temporary script for kernel startup
    KERNEL_SCRIPT=$(mktemp)
    cat > "$KERNEL_SCRIPT" << 'KERNEL_EOF'
#!/usr/bin/env bash
set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Starting Kernel Nodes (2-4)${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

cd kernel

echo -e "${YELLOW}→ Waiting 10 seconds for bootstrap to initialize...${NC}"
sleep 10

echo -e "${YELLOW}→ Starting 3 kernel nodes...${NC}"
docker-compose up

echo ""
echo -e "${GREEN}✓ Kernel nodes stopped${NC}"
KERNEL_EOF

    chmod +x "$KERNEL_SCRIPT"

    print_info "In a NEW TERMINAL, run this command:"
    echo ""
    echo -e "${GREEN}  cd $(pwd) && bash $KERNEL_SCRIPT${NC}"
    echo ""
    print_info "Or manually:"
    echo -e "${GREEN}  cd $(pwd)/kernel && docker-compose up${NC}"
    echo ""

    read -p "Press ENTER when you've started the kernel nodes in another terminal..."

    print_info "Starting bootstrap node (Ctrl+C to stop)..."
    cd bootstrap
    docker-compose up

    # Cleanup on exit
    print_info "Stopping bootstrap node..."
    docker-compose down
    cd ..

    print_step "Manual cluster stopped"

    # Cleanup temp script
    rm -f "$KERNEL_SCRIPT"
fi

# ==============================================================================
# Done
# ==============================================================================

print_header "Demo Complete!"

case "$MODE" in
    test)
        echo "The automated test ran successfully. Your Sky cluster is working!"
        echo ""
        echo "Next steps:"
        echo "  - Read DEMO_GUIDE.md for detailed walkthrough"
        echo "  - Try manual cluster: ./demo.sh --manual"
        echo "  - Explore Oracle API examples in DEMO_GUIDE.md"
        ;;
    test-then-manual)
        echo "The automated test and manual cluster both ran successfully!"
        echo ""
        echo "Next steps:"
        echo "  - Read DEMO_GUIDE.md for API examples"
        echo "  - See examples/.env.example for configuration options"
        echo "  - Check ARCHITECTURE.md for system design details"
        ;;
    manual-only)
        echo "The manual cluster ran successfully!"
        echo ""
        echo "To verify cluster formation:"
        echo "  curl http://localhost:50004/health"
        echo ""
        echo "Next steps:"
        echo "  - Read DEMO_GUIDE.md for API usage examples"
        echo "  - Try the automated test: ./demo.sh"
        ;;
esac

echo ""
print_step "All done! 🎉"
