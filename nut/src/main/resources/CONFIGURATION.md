# Sky Configuration System

## Overview

The Sky application uses a hierarchical configuration system with clear precedence rules. Configuration values can be specified at multiple levels, with each level overriding the previous one.

## Configuration Precedence

Configuration is loaded in this order, with each level overriding previous values:

1. **Default Values** (Lowest precedence)
   - Hardcoded defaults in `SkyConfiguration` class instance initializer
   - Applied automatically if no other configuration is provided

2. **YAML Configuration File**
   - Loaded from file specified as first argument to `Launcher`
   - Partial configuration: only specified fields override defaults
   - Path example: `./config/sky-node.yaml`

3. **Environment Variables**
   - Detected and applied after YAML loading
   - Set before or during application startup
   - Useful for Docker containers and cloud deployments

4. **CLI Arguments** (Highest precedence)
   - Format: `--key=value`
   - Applied last, override all other configuration
   - Currently limited support (see below)

### Example Precedence

If you set:
- Default YAML: `apiPort: 8123`
- Environment: `API_PORT=9000`
- CLI: `--endpoints.apiPort=7000`

The final value will be: **7000** (CLI wins)

## Configuration Methods

### 1. YAML File Configuration

Create a YAML file with your configuration:

```yaml
endpoints:
  class: network
  interfaceName: eth0
  apiPort: 8123
  approachPort: 8124
  clusterPort: 8125
  servicePort: 8126
  healthPort: 8127

identity:
  digestAlgorithm: SHA_3_256
  signatureAlgorithm: EDDSA

choamParameters:
  gossipDuration: 5ms
  checkpointBlockDelta: 200

# Feature flag for ARCH #1 rollout
useServiceLayer: false
```

Run the application:

```bash
java -jar sky.jar ./config/sky-node.yaml
```

### 2. Environment Variables

Set environment variables to override YAML configuration. Useful for Docker/Kubernetes deployments.

#### Supported Environment Variables

| Variable | Type | Description | Default |
|----------|------|-------------|---------|
| `BIND_INTERFACE` | string | Network interface name (e.g., `eth0`, `lo`) | `eth0` |
| `API_PORT` | int | gRPC API server port | `8123` |
| `APPROACH_PORT` | int | Fireflies APPROACH port | `8124` |
| `CLUSTER_PORT` | int | Fireflies CLUSTER port | `8125` |
| `SERVICE_PORT` | int | Internal service port | `8126` |
| `HEALTH_PORT` | int | Health check port | `8127` |
| `GENESIS` | bool | Generate genesis block (true/false) | `false` |
| `USE_SERVICE_LAYER` | bool | Enable service layer (true/false) | `false` |
| `SEEDS` | string | Seed nodes for cluster join (comma-separated) | unset |
| `APPROACHES` | string | Approach nodes (comma-separated) | unset |
| `PROVISIONED_TOKEN` | string | Provisioned access token | unset |

#### Environment Variable Examples

```bash
# Single port override
export API_PORT=9000
java -jar sky.jar ./config/sky-node.yaml

# Multiple overrides (Docker style)
export BIND_INTERFACE=eth1
export API_PORT=9000
export GENESIS=true
docker run -e BIND_INTERFACE -e API_PORT -e GENESIS sky-image:latest

# Join existing cluster
export SEEDS="seed1@bootstrap.example.com"
export APPROACHES="approach.example.com:50001"
java -jar sky.jar ./config/sky-node.yaml

# Enable service layer (ARCH #1)
export USE_SERVICE_LAYER=true
java -jar sky.jar ./config/sky-node.yaml
```

### 3. CLI Arguments (Limited Support)

CLI arguments follow the format: `--key=value`

Currently supported:
- `--useServiceLayer=true|false`
- `--endpoints.apiPort=<port>`
- `--endpoints.approachPort=<port>`
- `--endpoints.clusterPort=<port>`
- `--endpoints.servicePort=<port>`
- `--endpoints.healthPort=<port>`
- `--endpoints.interfaceName=<name>`

CLI argument support can be extended as needed. Feature requests welcome.

#### CLI Argument Examples

```bash
# Enable service layer
java -jar sky.jar ./config/sky-node.yaml --useServiceLayer=true

# Override endpoint ports
java -jar sky.jar ./config/sky-node.yaml \
  --endpoints.apiPort=7000 \
  --endpoints.approachPort=7001
```

## Feature Flags

### useServiceLayer

The `useServiceLayer` flag enables the new service abstraction layer for platform decoupling (ARCH #1).

- **Default**: `false` (backward compatible, uses Delos directly)
- **Purpose**: Allows gradual rollout of service layer implementation
- **Configuration**: Can be set via YAML, environment variable, or CLI argument

#### Setting useServiceLayer

**YAML:**
```yaml
useServiceLayer: true
```

**Environment:**
```bash
export USE_SERVICE_LAYER=true
```

**CLI:**
```bash
java -jar sky.jar ./config/sky-node.yaml --useServiceLayer=true
```

## Common Scenarios

### Docker Container with Environment Variables

```dockerfile
# Dockerfile
FROM openjdk:22
COPY sky.jar /app/sky.jar
COPY default-config.yaml /config/sky-node.yaml
WORKDIR /app

# Use environment variables from docker-compose or -e flags
CMD ["java", "-jar", "sky.jar", "/config/sky-node.yaml"]
```

```yaml
# docker-compose.yaml
version: '3'
services:
  sky-node:
    image: sky-image:latest
    environment:
      BIND_INTERFACE: eth0
      API_PORT: 8123
      GENESIS: "true"
    ports:
      - "8123:8123"
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sky-node
spec:
  template:
    spec:
      containers:
      - name: sky
        image: sky-image:latest
        env:
        - name: BIND_INTERFACE
          value: "eth0"
        - name: API_PORT
          value: "8123"
        - name: GENESIS
          value: "true"
        - name: SEEDS
          value: "seed1@bootstrap-0.sky.svc:8123"
        - name: APPROACHES
          value: "approach-0.sky.svc:8124"
        ports:
        - containerPort: 8123
          name: api
```

### Local Development (Port Override)

```bash
# Run on non-standard ports to avoid conflicts
export API_PORT=9000
export APPROACH_PORT=9001
export CLUSTER_PORT=9002
export SERVICE_PORT=9003
export HEALTH_PORT=9004

java -jar sky.jar ./local-config.yaml
```

## Configuration Files

### Builtin Test Configuration

Located at: `nut/src/test/resources/sky-test.yaml`

Used by tests and for development with dynamic ports (port 0 = auto-allocate).

### Example Configuration

Located at: `examples/config-sample.yaml`

Reference configuration with all available options documented.

## Troubleshooting

### "Configuration file not found"

**Problem**: `ConfigurationLoader` throws IOException

**Solution**:
1. Verify file path is correct (relative to current directory or absolute)
2. Ensure file exists: `ls -la <config-file>`
3. Check file permissions: `chmod 644 <config-file>`

### Configuration values not being applied

**Debug steps**:
1. Check precedence - CLI args override environment variables
2. Verify environment variable names match exactly (case-sensitive on Linux)
3. Look for errors in application startup logs
4. Verify YAML is valid: `yamllint <config-file>`

### Port conflicts when running multiple nodes

**Solution**: Use environment variables to assign unique ports:

```bash
# Node 1
export API_PORT=8123 && java -jar sky.jar config.yaml

# Node 2 (different terminal)
export API_PORT=8223 && java -jar sky.jar config.yaml
```

## Migration Guide

### From Hard-Coded Configuration (pre-ARCH #5)

Previously, configuration was primarily YAML-based with limited environment variable support (ad-hoc overrides only).

**No changes required** - all existing YAML files and environment variables are fully supported. The new `ConfigurationLoader` maintains 100% backward compatibility.

### Adopting New Features

To use new configuration features:

1. **Use environment variables systematically** instead of ad-hoc script modifications
2. **Enable `useServiceLayer`** when ready to migrate to service abstraction layer (ARCH #1)
3. **Use CLI arguments** for one-off deployments or testing

## Implementation Details

Configuration loading is handled by `ConfigurationLoader` class:

- **Location**: `nut/src/main/java/com/hellblazer/nut/ConfigurationLoader.java`
- **Method**: `load(String yamlPath, String[] cliArgs)`
- **Backward compatibility**: All existing environment variables are supported
- **Type conversion**: Automatic conversion of strings to appropriate types (int, boolean, etc.)

## Adding New Configuration Options

To add a new configuration option:

1. Add field to `SkyConfiguration` class with `@JsonProperty` annotation
2. Set appropriate default value
3. Update `ConfigurationLoader` to handle environment variable overrides (if desired)
4. Add environment variable documentation above
5. Write tests in `ConfigurationLoaderTest`
6. Update this documentation

## Related Documentation

- [ARCHITECTURE.md](../../ARCHITECTURE.md) - System architecture and components
- Sky YAML Configuration Schema - See builtin test YAML files
- Delos Platform Documentation - For underlying platform configuration
