# Nut - Sky Node Runtime

**Module**: `nut`
**Type**: Core Runtime
**Purpose**: Node lifecycle management, bootstrapping, and provisioning for Sky nodes

## Overview

**Nut** is the core runtime container for Sky nodes. It manages the complete lifecycle of a Sky node from initialization through shutdown, including cluster bootstrapping via Shamir secret sharing, membership coordination, and service orchestration.

## Responsibilities

- **Cluster Bootstrapping**: Initialize Genesis cluster using Shamir secret sharing
- **Node Lifecycle**: Start, configure, and gracefully shutdown Sky nodes
- **Provisioning**: Load/generate cryptographic identities and configuration
- **Service Coordination**: Manage Oracle API, Fireflies membership, CHOAM consensus
- **Configuration Management**: Parse environment variables and configuration files

## Key Classes

### Core

- **`Launcher.java`** - Main entry point, parses configuration and initializes `SkyApplication`
- **`SkyApplication.java`** - Main coordinator managing all node services and lifecycle
- **`Provisioner.java`** - Configuration and provisioning logic, identity management
- **`BootstrapService.java`** - Cluster initialization via Shamir secret sharing

### Services

- **`OracleAdapter.java`** - Oracle API adapter for ReBAC operations
- **`MtlsClient.java`** - MTLS client for inter-node communication

## Architecture

```
Nut Runtime
├── Launcher (entry point)
└── SkyApplication (coordinator)
    ├── Provisioner (config + identity)
    ├── Oracle (ReBAC engine)
    ├── Fireflies (membership)
    ├── CHOAM (consensus)
    └── gRPC API Server
```

## Dependencies

- **Delos Platform**: Fireflies (membership), CHOAM (consensus), Stereotomy (identity)
- **gRPC**: Inter-node communication
- **H2 Database**: Consensus log storage
- **Fernet**: Token encryption

## Usage

Nut is packaged into the shaded JAR by the `sky` module and run via Docker in the `sky-image` module. It is not meant to be run standalone.

**Entry Point**:
```bash
java -jar sky-<version>-shaded.jar
```

**Configuration**: Via environment variables (see [examples/.env.example](../examples/.env.example))

## Bootstrap Sequence

1. **Initialization** (Launcher → SkyApplication → Provisioner)
   - Parse environment variables
   - Load/generate cryptographic identity
   - Initialize configuration

2. **Membership** (Fireflies)
   - If `GENESIS=true`: Create Genesis context, wait for kernel quorum
   - If `GENESIS=false`: Join via APPROACHES/SEEDS discovery

3. **Consensus** (CHOAM)
   - Initialize consensus protocol
   - Bootstrap node generates Genesis block
   - Joining nodes synchronize consensus log

4. **Services**
   - Start Oracle API (gRPC, default port 50000)
   - Start health check (HTTP, default port 50004)
   - Node ready for requests

## Configuration

**Required Environment Variables**:
- `BIND_INTERFACE` - Network interface to bind (e.g., `eth0`)
- `GENESIS` - Set to `'true'` for kernel members (4 nodes minimum)

**Optional Environment Variables**:
- `APPROACHES` - Discovery endpoints for joining nodes
- `SEEDS` - Cluster endpoints for membership
- `API`, `APPROACH`, `CLUSTER`, `SERVICE`, `HEALTH` - Port configuration

See [ARCHITECTURE.md](../ARCHITECTURE.md) for complete bootstrap sequence.

## Testing

**Unit Tests**:
```bash
./mvnw test -pl nut
```

**Integration Tests**: Run via `local-demo` module with Docker

## POC Constraints

- Shamir shared secret is hardcoded (see [KNOWN_LIMITATIONS.md](../KNOWN_LIMITATIONS.md))
- Certificates are self-signed and ephemeral
- Token logging at INFO level (#32)

## See Also

- [ARCHITECTURE.md](../ARCHITECTURE.md) - Complete system architecture
- [DEMO_GUIDE.md](../DEMO_GUIDE.md) - Running Sky nodes
- [examples/.env.example](../examples/.env.example) - Configuration template
