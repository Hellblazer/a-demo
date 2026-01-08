# Sky Image - Docker Container Image

**Module**: `sky-image`
**Type**: Container Image Builder
**Purpose**: Builds Docker container image for Sky application with environment-based configuration

## Overview

**Sky Image** packages the Sky application into a Docker container image with environment variable configuration. It creates a production-ready (for POC) container that runs the shaded Sky JAR with configurable ports, interfaces, and cluster settings.

## Responsibilities

- **Container Build**: Create Docker image with Sky shaded JAR
- **Environment Configuration**: Support environment variable-based configuration
- **Port Exposure**: Expose all necessary service ports
- **Entrypoint**: Provide startup script for Sky nodes

## Image Details

**Image Name**: `com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT`

**Base Image**: OpenJDK or compatible Java 22+ runtime

**Exposed Ports**:
- `50000` - Oracle API (gRPC)
- `50001` - Fireflies APPROACH (TCP)
- `50002` - Fireflies CLUSTER (TCP)
- `50003` - Internal SERVICE (gRPC)
- `50004` - Health Check (HTTP)

## Build Process

The image is built using Maven and the `dockerfile-maven-plugin`:

```bash
# Build Sky application and Docker image
./mvnw clean install -pl sky-image

# Verify image
docker images | grep sky-image
```

**Build Sequence**:
1. Maven compiles and packages `sky` module (shaded JAR)
2. Dockerfile copies shaded JAR into image
3. Entrypoint script configured for environment-based configuration
4. Image tagged as `com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT`

## Usage

### Basic Docker Run

```bash
docker run \
  -e GENESIS='true' \
  -e BIND_INTERFACE='eth0' \
  -e API=50000 \
  -e APPROACH=50001 \
  -e CLUSTER=50002 \
  -e SERVICE=50003 \
  -e HEALTH=50004 \
  -p 50000:50000 \
  -p 50004:50004 \
  com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
```

### With Environment File

```bash
# Create .env from template
cp examples/.env.example .env

# Edit configuration
vim .env

# Run with env file
docker run --env-file .env com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
```

### Docker Compose

See [local-demo](../local-demo) for complete Docker Compose examples:

```yaml
services:
  bootstrap:
    image: com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
    environment:
      GENESIS: 'true'
      BIND_INTERFACE: eth0
      API: 50000
      APPROACH: 50001
      CLUSTER: 50002
      SERVICE: 50003
      HEALTH: 50004
```

## Environment Variables

See [examples/.env.example](../examples/.env.example) for complete documentation. Key variables:

**Required**:
- `BIND_INTERFACE` - Network interface to bind (e.g., `eth0`)
- `GENESIS` - Set to `'true'` for kernel members

**Optional**:
- `APPROACHES` - Discovery endpoints (comma-separated)
- `SEEDS` - Cluster endpoints (comma-separated, format: `host:cluster_port#api_port`)
- `API`, `APPROACH`, `CLUSTER`, `SERVICE`, `HEALTH` - Port configuration
- `LOG_LEVEL` - Logging verbosity (TRACE, DEBUG, INFO, WARN, ERROR)
- `JAVA_OPTS` - Additional JVM options

## Container Configuration

### Volumes

Recommended persistent storage for database:

```bash
docker run \
  -v /data/sky:/data \
  com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
```

### Health Check

Built-in HTTP health endpoint:

```bash
# Check health
curl http://<container-ip>:50004/health

# Expected response
{"status":"healthy","members":4}
```

### Logging

Logs written to stdout/stderr (Docker-friendly):

```bash
# View logs
docker logs <container-id>

# Follow logs
docker logs -f <container-id>
```

## Dockerfile Structure

```dockerfile
FROM openjdk:22-jdk
COPY sky-<version>-shaded.jar /app/sky.jar
EXPOSE 50000 50001 50002 50003 50004
ENTRYPOINT ["java", "-jar", "/app/sky.jar"]
```

(Actual Dockerfile may vary - see source)

## Testing

Sky Image is tested via the `local-demo` module using TestContainers:

```bash
# Run end-to-end smoke tests
./mvnw -P e2e test -pl local-demo
```

**What the test does**:
- Starts 4 Sky containers (bootstrap + 3 kernel nodes)
- Verifies cluster formation and Genesis block commitment
- Runs Oracle API tests (relationship management)
- Validates cluster health

## Troubleshooting

### Image Not Found

```bash
# Rebuild image
./mvnw clean install -pl sky,sky-image -DskipTests

# Verify
docker images | grep sky-image
```

### Container Exits Immediately

```bash
# Check logs for errors
docker logs <container-id>

# Common issues:
# - Missing BIND_INTERFACE
# - Invalid network configuration
# - Port conflicts
```

See [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) for detailed solutions.

## POC Constraints

- Hardcoded shared secret for Shamir bootstrapping
- Self-signed certificates (ephemeral, generated at startup)
- Bootstrap node hardwired to 172.17.0.2 (in local-demo)
- No persistent secret management

See [KNOWN_LIMITATIONS.md](../KNOWN_LIMITATIONS.md) for complete POC constraints.

## Production Considerations

If promoting to production:

- External certificate management (cert-manager, Vault)
- Secret management (HashiCorp Vault, AWS Secrets Manager)
- Monitoring integration (Prometheus, Grafana)
- Proper logging aggregation (ELK, Loki)
- Resource limits (CPU, memory)
- Security scanning (Trivy, Clair)
- Multi-stage builds for smaller images

## See Also

- [DEMO_GUIDE.md](../DEMO_GUIDE.md) - Running Sky with Docker
- [examples/.env.example](../examples/.env.example) - Complete environment variable reference
- [local-demo/](../local-demo) - Docker Compose examples
- [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) - Common Docker issues
