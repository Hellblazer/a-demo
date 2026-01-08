# Sky Configuration Examples

This directory contains example configurations for the Sky application.

## Files

### [.env.example](.env.example)

Template for environment variable configuration. This is the **recommended** approach for Docker deployments.

**Usage**:
```bash
# Copy template
cp examples/.env.example .env

# Customize values
vim .env

# Use with docker run
docker run --env-file .env com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT

# Use with docker-compose
# Add to compose.yaml:
services:
  sky-node:
    env_file:
      - .env
```

**What's included**:
- All environment variables with descriptions
- Default values and recommendations
- Example configurations (bootstrap, kernel, joining nodes)
- Troubleshooting tips
- Production considerations

### [config-sample.yaml](config-sample.yaml)

Sample YAML configuration file demonstrating structured configuration.

**Usage**:
```bash
# Copy and customize
cp examples/config-sample.yaml config.yaml
vim config.yaml

# Mount in Docker container
docker run \
  -v $(pwd)/config.yaml:/etc/sky/config.yaml:ro \
  com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT \
  --config=/etc/sky/config.yaml
```

**What's included**:
- Cluster configuration (Genesis, kernel)
- Network configuration (interfaces, ports, discovery)
- Database configuration (H2)
- Consensus configuration (CHOAM)
- Oracle configuration (ReBAC)
- Security configuration (MTLS)
- Logging and monitoring
- JVM tuning
- Example node configurations

## Quick Start Examples

### Bootstrap Node (Genesis Kernel Member 1)

**Environment Variables** (`.env`):
```bash
GENESIS='true'
BIND_INTERFACE='eth0'
# Ports use defaults (50000-50004)
```

**Docker Compose** (`bootstrap/compose.yaml`):
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

### Kernel Node (Genesis Kernel Members 2-4)

**Environment Variables** (`.env`):
```bash
GENESIS='true'
BIND_INTERFACE='eth0'
APPROACHES='172.17.0.2:50001'
SEEDS='172.17.0.2:50002#50000'
HEALTH=50005  # Different port from bootstrap
```

**Docker Compose** (`kernel/compose.yaml`):
```yaml
services:
  kernel1:
    image: com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
    environment:
      GENESIS: 'true'
      APPROACHES: '172.17.0.2:50001'
      SEEDS: '172.17.0.2:50002#50000'
      BIND_INTERFACE: eth0
      HEALTH: 50005
    network_mode: bridge
```

### Joining Node (After Cluster Formation)

**Environment Variables** (`.env`):
```bash
GENESIS='false'  # or omit
BIND_INTERFACE='eth0'
APPROACHES='172.17.0.2:50001,172.17.0.3:50001'
SEEDS='172.17.0.2:50002#50000,172.17.0.3:50002#50000'
HEALTH=50010
```

## Configuration Priority

Sky uses this priority order for configuration:

1. **Command-line arguments** (highest priority)
2. **Environment variables**
3. **Configuration file** (--config)
4. **Default values** (lowest priority)

Example:
```bash
# API port resolution:
# 1. --api=60000 (command-line) → uses 60000
# 2. API=50001 (env var) → uses 50001
# 3. ports.api: 50002 (config.yaml) → uses 50002
# 4. Default 50000 → uses 50000
```

## Environment Variable Reference

See [.env.example](.env.example) for complete documentation. Key variables:

| Variable | Purpose | Required | Default |
|----------|---------|----------|---------|
| `GENESIS` | Genesis kernel member | Yes (kernel) | false |
| `BIND_INTERFACE` | Network interface | **Yes** | (none) |
| `API` | Oracle API port | No | 50000 |
| `APPROACH` | Fireflies approach port | No | 50001 |
| `CLUSTER` | Fireflies cluster port | No | 50002 |
| `SERVICE` | Internal service port | No | 50003 |
| `HEALTH` | Health check port | No | 50004 |
| `APPROACHES` | Discovery endpoints | No (joining) | (none) |
| `SEEDS` | Cluster endpoints | No (joining) | (none) |

## Port Reference

Sky uses these ports by default:

```
50000 - Oracle API (gRPC)           ← Client requests
50001 - Fireflies APPROACH (TCP)    ← Node discovery
50002 - Fireflies CLUSTER (TCP)     ← Membership gossip
50003 - Internal SERVICE (gRPC)     ← Internal RPC
50004 - Health Check (HTTP)         ← Monitoring
```

**Customizing ports**: Set environment variables or update `ports` section in config.yaml.

**Port conflicts**: See [TROUBLESHOOTING.md](../TROUBLESHOOTING.md#3-port-already-in-use).

## Multi-Node Deployment Example

### Local Docker Compose

```yaml
# compose.yaml
services:
  bootstrap:
    image: com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
    environment:
      GENESIS: 'true'
      BIND_INTERFACE: eth0
    networks:
      sky_network:
        ipv4_address: 172.20.0.2

  kernel1:
    image: com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
    environment:
      GENESIS: 'true'
      BIND_INTERFACE: eth0
      APPROACHES: '172.20.0.2:50001'
      SEEDS: '172.20.0.2:50002#50000'
      HEALTH: 50005
    networks:
      - sky_network

  kernel2:
    # ... similar to kernel1 ...
    environment:
      HEALTH: 50006

  kernel3:
    # ... similar to kernel1 ...
    environment:
      HEALTH: 50007

networks:
  sky_network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

### Kubernetes Deployment

```yaml
# sky-bootstrap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: sky-bootstrap-config
data:
  GENESIS: "true"
  BIND_INTERFACE: "eth0"
  API: "50000"
  APPROACH: "50001"
  CLUSTER: "50002"
  SERVICE: "50003"
  HEALTH: "50004"
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: sky-bootstrap
spec:
  serviceName: sky-bootstrap
  replicas: 1
  selector:
    matchLabels:
      app: sky-bootstrap
  template:
    metadata:
      labels:
        app: sky-bootstrap
    spec:
      containers:
      - name: sky
        image: com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
        envFrom:
        - configMapRef:
            name: sky-bootstrap-config
        ports:
        - containerPort: 50000
          name: api
        - containerPort: 50001
          name: approach
        - containerPort: 50002
          name: cluster
        - containerPort: 50003
          name: service
        - containerPort: 50004
          name: health
---
apiVersion: v1
kind: Service
metadata:
  name: sky-bootstrap
spec:
  clusterIP: None  # Headless service
  selector:
    app: sky-bootstrap
  ports:
  - name: api
    port: 50000
  - name: approach
    port: 50001
  - name: cluster
    port: 50002
```

## Common Scenarios

### Scenario 1: Single-Host Demo

Use Docker Compose with bridge network:
- See [local-demo](../local-demo) directory
- Bootstrap + 3 kernel nodes on same host
- Uses IP 172.17.0.2 for bootstrap

### Scenario 2: Multi-Host Cluster

Use explicit IPs and network configuration:
```bash
# Host 1 (bootstrap)
BIND_INTERFACE='eth0'
HOST_ADDRESS='192.168.1.100'

# Host 2 (kernel)
APPROACHES='192.168.1.100:50001'
SEEDS='192.168.1.100:50002#50000'
```

### Scenario 3: Cloud Deployment

Use Kubernetes StatefulSet:
- Headless service for stable network identity
- ConfigMap for environment variables
- Secrets for MTLS certificates (production)
- PersistentVolumeClaim for H2 database

## Security Considerations

### POC (Current)

- Self-signed certificates (ephemeral)
- No external PKI
- Token logging at INFO level (see [KNOWN_LIMITATIONS.md #32](../KNOWN_LIMITATIONS.md#32))
- Hardcoded shared secret

### Production (Future)

- External certificate management (cert-manager, Vault)
- Certificate rotation
- Secret management (HashiCorp Vault, AWS Secrets Manager)
- Token logging at DEBUG (or removed)
- Network policies (Kubernetes)
- RBAC for API access

See [KNOWN_LIMITATIONS.md](../KNOWN_LIMITATIONS.md) for complete POC constraints.

## Troubleshooting

### Configuration not applied

**Check priority order**:
```bash
# Environment variable overrides config file
docker run \
  -e API=60000 \  # This takes precedence
  -v $(pwd)/config.yaml:/etc/sky/config.yaml \
  ...
```

### Invalid configuration

**Check logs**:
```bash
docker logs <container> | grep -i "configuration\|validation"
```

**Validate YAML syntax**:
```bash
yamllint config-sample.yaml
```

### Port binding fails

See [TROUBLESHOOTING.md #3](../TROUBLESHOOTING.md#3-port-already-in-use)

### Network interface not found

**List available interfaces**:
```bash
# Inside container
docker exec -it <container> ip addr

# On host
ip addr  # Linux
ifconfig  # macOS
```

## Additional Resources

- **DEMO_GUIDE.md** - Step-by-step demo walkthrough
- **ARCHITECTURE.md** - System architecture
- **TROUBLESHOOTING.md** - Common issues and solutions
- **KNOWN_LIMITATIONS.md** - POC constraints
- **Docker Compose examples** - See [local-demo](../local-demo)

## Contributing

Found a configuration issue or have a better example? Open an issue or PR:
- https://github.com/Hellblazer/a-demo/issues

---

**Last Updated**: 2026-01-07 | **Sky Version**: 0.0.1-SNAPSHOT
