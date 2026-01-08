# Local Demo - End-to-End Testing

**Module**: `local-demo`
**Type**: Integration Test Suite
**Purpose**: Docker Compose configurations and smoke tests for local Sky cluster testing

## Overview

**Local Demo** provides Docker Compose configurations for running a complete multi-node Sky cluster locally, along with automated end-to-end smoke tests using TestContainers. This is the primary way to see Sky in action.

## Contents

- **`bootstrap/compose.yaml`** - Bootstrap node (Genesis kernel member 1 of 4)
- **`kernel/compose.yaml`** - Kernel nodes (Genesis kernel members 2-4 of 4)
- **`nodes/compose.yaml`** - Additional nodes (join after Genesis)
- **`src/test/java/SmokeTest.java`** - Automated end-to-end test

## Quick Start

### Automated Test (Recommended)

```bash
# Run full end-to-end test with TestContainers
./mvnw -P e2e test -pl local-demo
```

**What it does**:
1. Builds Sky Docker image
2. Starts 4 nodes (bootstrap + 3 kernel) via TestContainers
3. Waits for Genesis block commitment
4. Creates organizational hierarchy (users, groups)
5. Tests relationship-based access control
6. Tests transitive permissions
7. Verifies permission revocation
8. Cleans up containers

**Expected**: `[INFO] Tests run: 1, Failures: 0, Errors: 0` (takes 2-5 minutes)

### Manual Cluster (Step-by-Step)

#### Step 1: Start Bootstrap Node

```bash
cd bootstrap
docker-compose up
```

**Wait for**: `"Waiting for kernel quorum..."` message

**Leave running**, open new terminal.

#### Step 2: Start Kernel Nodes

```bash
cd kernel
docker-compose up
```

**Wait for**: `"Genesis block committed at view 0"` in bootstrap logs

**This is critical!** Do not proceed until Genesis block is committed.

#### Step 3: (Optional) Add More Nodes

```bash
cd nodes
docker-compose up --scale node=3
```

Adds 3 additional nodes to the cluster.

## Architecture

### Minimal Quorum Bootstrap

Sky requires **4 kernel members** for minimal BFT quorum (tolerates f=1 Byzantine failure):

```
┌─────────────┐
│  Bootstrap  │  172.17.0.2
│  (Genesis)  │  Well-known address
└──────┬──────┘
       │
   ┌───┴───┬───────┬────────┐
   │       │       │        │
┌──▼───┐ ┌▼────┐ ┌▼─────┐ ┌▼──────┐
│Kernel│ │Kernel│ │Kernel│ │ Nodes │
│  1   │ │  2  │ │  3   │ │ (N+)  │
└──────┘ └─────┘ └──────┘ └───────┘
   Genesis Kernel Quorum    Joining
```

**Genesis Kernel**: 4 nodes with `GENESIS='true'`
**Joining Nodes**: Additional nodes after cluster formation

### Network Configuration

**Local Demo** uses Docker bridge network (`172.17.0.0/16`):

- **Bootstrap**: Hardcoded to `172.17.0.2` (well-known address)
- **Kernel nodes**: Discover bootstrap via `APPROACHES='172.17.0.2:50001'`
- **Joining nodes**: Use `SEEDS='172.17.0.2:50002#50000'` for discovery

## Docker Compose Files

### bootstrap/compose.yaml

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

**Purpose**: First node, generates Genesis block, well-known address

### kernel/compose.yaml

```yaml
services:
  kernel1:
    image: com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
    environment:
      GENESIS: 'true'
      APPROACHES: '172.18.0.2:50001'
      SEEDS: '172.18.0.2:50002#50000'
      BIND_INTERFACE: eth0
      HEALTH: 50005
    network_mode: bridge
  # kernel2, kernel3 similar...
```

**Purpose**: Join bootstrap to reach 4-node quorum

### nodes/compose.yaml

```yaml
services:
  node:
    image: com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
    environment:
      APPROACHES: '172.18.0.2:50001'
      SEEDS: '172.18.0.2:50002#50000'
      BIND_INTERFACE: eth0
    network_mode: bridge
```

**Purpose**: Scalable additional nodes (not part of Genesis kernel)

**Scale up**: `docker-compose up --scale node=5`

## Smoke Test

**File**: `src/test/java/com/hellblazer/sky/demo/SmokeTest.java`

**What it tests**:

### 1. Cluster Formation
- 4 nodes start and form quorum
- Genesis block committed
- All nodes healthy

### 2. Organizational Hierarchy

Creates a realistic org structure:

```
Organization (my-org)
├── Users (11 members)
│   ├── Managers (2: Fuat, Gül)
│   ├── Technicians (3: Hakan, Irmak, Jale)
│   ├── Admins
│   │   ├── HelpDesk (2: Egin, Demet)
│   │   └── Ali
│   ├── Can
│   └── Burcu
└── FlaggedTechnicians (filtered group)
```

### 3. Permission Management

Tests relationship-based access control:

```java
// Grant: Users can view Document:123
oracle.add(usersGroup.assertion(document123View));

// Check direct: Who can view directly?
// Result: 1 (Users group)

// Check transitive: Who can view through group membership?
// Result: 14 (all users + groups transitively in Users)

// Check filtered: Which flagged users can view?
// Result: 5 (only flagged: Egin, Ali, Gül, Fuat, FlaggedTechnicians)
```

### 4. Permission Revocation

```java
// Remove: ABCTechnicians from Technicians
oracle.remove(abcTechnicians, technicians);

// Verify: Jale (in ABCTechnicians) can no longer view
// Result: false (transitive path broken)

// Verify: Egin (in HelpDesk → Admins → Users) can still view
// Result: true (different path)
```

**Expected Output**:
```
[INFO] Creating namespace 'my-org'...
[INFO] Mapping 17 relationships...
[INFO] Direct viewers: 1
[INFO] Transitive viewers: 14
[INFO] Flagged viewers: 5
[INFO] After removal: Jale cannot view (expected)
[INFO] After removal: Egin can view (expected)
[INFO] Tests passed: 14/14
```

## Running Tests

### Full Test Suite

```bash
./mvnw -P e2e test -pl local-demo
```

### Single Test

```bash
./mvnw -P e2e test -pl local-demo -Dtest=SmokeTest#smokin
```

### With Debug Logging

```bash
./mvnw -P e2e test -pl local-demo -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG
```

## Memory Requirements

Tests require significant heap allocation for distributed testing:

**Configured**: `-Xmx10G -Xms4G` (in `pom.xml`)

**Why**: TestContainers + Docker + 4 nodes + H2 databases + consensus state

See [KNOWN_LIMITATIONS.md #43](../KNOWN_LIMITATIONS.md#43) for investigation notes.

**Troubleshooting**: If tests fail with OOM, increase Docker Desktop memory allocation (Preferences → Resources → Memory → 8GB+).

## Troubleshooting

### Genesis Block Not Committed

**Symptom**: Bootstrap waits indefinitely for quorum

**Solution**:
```bash
# Verify 4 containers running
docker ps | grep sky-image

# Check bootstrap logs
docker-compose -f bootstrap/compose.yaml logs bootstrap | tail -50

# Check kernel logs
docker-compose -f kernel/compose.yaml logs | grep "Joined cluster"

# Restart if needed
docker-compose down && docker-compose up
```

### Test Timeout

**Symptom**: `TimeoutException` after 120 seconds

**Solutions**:
1. Increase Docker Desktop memory (8GB minimum, 12GB recommended)
2. Check system resources: `docker stats`
3. Increase timeout: `-DtimeOut=300`

### Port Conflicts

**Symptom**: `bind: address already in use`

**Solutions**:
1. Find and kill process: `lsof -i :50000`
2. Change ports in `compose.yaml`
3. Clean up Docker: `docker system prune -f`

See [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) for comprehensive solutions.

## POC Constraints

- **Hardcoded bootstrap address**: `172.17.0.2` (not configurable yet)
- **Hardcoded shared secret**: Shamir secret for convenience (not interactive)
- **Bridge network dependency**: Requires Docker bridge network
- **Single-host only**: All nodes on same machine

See [KNOWN_LIMITATIONS.md](../KNOWN_LIMITATIONS.md) for complete constraints.

## See Also

- [DEMO_GUIDE.md](../DEMO_GUIDE.md) - Comprehensive demo walkthrough
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Cluster bootstrap sequence
- [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) - Issue resolution
- [examples/.env.example](../examples/.env.example) - Environment variable reference
