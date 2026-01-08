# Sky Application Demo Guide

**Version**: 1.0
**Last Updated**: 2026-01-07
**Estimated Time**: 30-45 minutes

---

## Overview

This guide walks you through running a complete demonstration of the Sky application, a Byzantine fault-tolerant (BFT) identity and secrets management cluster built on the Delos platform.

### What You'll Learn

- How to bootstrap a BFT cluster using Shamir secret sharing
- How Sky manages identity relationships using the KERL protocol
- How transitive permissions work in a distributed system
- How to interact with the Oracle API for relationship management
- How Sky achieves consensus using CHOAM

### What This Demo Shows

The Sky demo showcases a **relationship-based access control (ReBAC) system** where:

1. **Subjects** (users, groups) have **relationships** to each other
2. **Objects** (resources) can be accessed based on **transitive relationships**
3. **Predicates** (flags) filter subjects based on properties
4. **Permissions** are checked across a distributed, fault-tolerant cluster

Example: If User A is a member of Team B, and Team B can view Document C, then User A can transitively view Document C.

---

## Prerequisites

### Required Software

- **Docker Desktop** or Docker Engine (v20.10+)
- **Java 22+** (for building from source)
- **Maven 3.8.1+** (for building from source)
- **8GB+ RAM** (for running multi-node cluster)
- **Terminal/Command Line** access

### Optional Tools

- **Docker Compose V2** (included with Docker Desktop)
- **jq** (for pretty-printing JSON output)
- **curl** or **httpie** (for API interaction)

### Environment Setup

Ensure Docker is running:
```bash
docker info
```

Expected: Docker version, no connection errors.

---

## Quick Start (5 Minutes)

For the impatient, here's the fastest path to seeing Sky in action:

```bash
# 1. Build the Sky image
./mvnw clean install -DskipTests

# 2. Run automated end-to-end test
./mvnw -P e2e test -pl local-demo

# Expected: SmokeTest passes with "14 inferred viewers" message
```

If this works, congratulations! You've run Sky. Now let's understand what happened.

---

## Full Demo Walkthrough

### Step 1: Build Sky Application (10-15 minutes)

#### 1.1 Clone the Repository

```bash
git clone https://github.com/Hellblazer/a-demo.git
cd a-demo
```

#### 1.2 Build Without Tests (Faster)

```bash
./mvnw clean install -DskipTests
```

**Expected output**:
```
[INFO] BUILD SUCCESS
[INFO] Total time: 2-5 minutes
```

**What's happening**: Maven compiles all modules and creates the Sky Docker image at `com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT`.

#### 1.3 Verify Docker Image

```bash
docker images | grep sky-image
```

**Expected**:
```
com.hellblazer.sky/sky-image   0.0.1-SNAPSHOT   ...   2 minutes ago   ...
```

### Step 2: Understand the Cluster Architecture (5 minutes)

Sky uses a **4-node minimal quorum** for BFT consensus:

```
┌─────────────┐
│  Bootstrap  │  ← Genesis member (172.17.0.2)
│   (Node 0)  │     Well-known address
└──────┬──────┘     Initializes cluster
       │
   ┌───┴───────────────────┐
   │                       │
┌──▼──────┐  ┌──────────┐  ┌──────────┐
│ Kernel1 │  │ Kernel2  │  │ Kernel3  │
│ (Node 1)│  │ (Node 2) │  │ (Node 3) │
└─────────┘  └──────────┘  └──────────┘
         Minimal Quorum
      (3f+1 with f=1 faults)
```

**Key Concepts**:
- **Bootstrap**: First node, known address, generates Genesis block
- **Kernel nodes**: Join to reach minimal quorum (4 nodes for BFT with f=1)
- **Genesis block**: First block in the consensus log, establishes cluster identity
- **Approach/Seed**: Discovery mechanism for new nodes joining the cluster

### Step 3: Manual Cluster Bootstrap (15 minutes)

#### 3.1 Start Bootstrap Node

Open a terminal:

```bash
cd local-demo/bootstrap
docker-compose up
```

**Expected output**:
```
bootstrap_1  | Bootstrapping Genesis cluster member...
bootstrap_1  | Sky node started on 172.17.0.2
bootstrap_1  | Approach port: 50001
bootstrap_1  | Cluster port: 50002
bootstrap_1  | API port: 50000
bootstrap_1  | Waiting for kernel quorum...
```

**What's happening**:
- Bootstrap node starts with `GENESIS='true'`
- Binds to well-known address `172.17.0.2`
- Waits for 3 more kernel members to reach quorum

**Leave this terminal running.**

#### 3.2 Start Kernel Nodes

Open a **second terminal**:

```bash
cd local-demo/kernel
docker-compose up
```

**Expected output** (watch for these messages):
```
kernel1_1  | Connecting to bootstrap at 172.17.0.2:50001
kernel2_1  | Connecting to bootstrap at 172.17.0.2:50001
kernel3_1  | Connecting to bootstrap at 172.17.0.2:50001
...
kernel1_1  | Joined cluster, member ID: <UUID>
...
bootstrap_1  | Genesis block committed at view 0
bootstrap_1  | Minimal quorum achieved: 4 members
```

**What's happening**:
- 3 kernel nodes connect to bootstrap via APPROACHES/SEEDS
- Each node performs Fireflies membership protocol
- Once 4 nodes are active, Genesis block is committed via CHOAM consensus
- Cluster is now operational

**⚠️ Important**: Wait for "Genesis block committed" before proceeding!

**Leave both terminals running.**

#### 3.3 Verify Cluster Health

Open a **third terminal**:

```bash
# Check bootstrap health
curl http://172.17.0.2:50004/health

# Check kernel1 health (adjust IP as shown in docker logs)
curl http://<kernel1-ip>:50005/health
```

**Expected**: `{"status":"healthy","members":4}`

### Step 4: Run the Smoke Test (10 minutes)

The smoke test demonstrates relationship-based access control:

#### 4.1 Understanding the Smoke Test Scenario

The test creates an **organizational hierarchy**:

```
Organization: my-org
├── Users (group)
│   ├── Managers (group)
│   │   ├── Fuat
│   │   └── Gül
│   ├── Technicians (group)
│   │   ├── ABCTechnicians (group)
│   │   │   └── Jale
│   │   ├── Hakan
│   │   └── Irmak
│   ├── Admins (group)
│   │   ├── HelpDesk (group)
│   │   │   ├── Egin (flagged)
│   │   │   └── Demet
│   │   └── Ali (flagged)
│   ├── Can
│   └── Burcu
└── FlaggedTechnicians (flagged group)
```

And a **protected resource**:

```
Document:123
  └── View permission
        ├── Users (group) can view
        └── FlaggedTechnicians (flagged) can view
```

#### 4.2 Run Automated Smoke Test

```bash
./mvnw -P e2e test -pl local-demo -Dtest=SmokeTest#smokin
```

**What's happening**:
1. **TestContainers** starts 4 Sky nodes automatically
2. Test waits for cluster to achieve quorum
3. Test creates relationships (group memberships)
4. Test grants permissions (Users can view Document:123)
5. Test queries **transitive viewers** (who can view the document?)
6. Test checks **filtered viewers** (which flagged users can view?)
7. Test removes relationships and verifies permission revocation

**Expected output highlights**:
```
[INFO] Creating namespace 'my-org' with relations...
[INFO] Mapping 17 relationships...
[INFO] Granting Users -> Document:123 View permission
[INFO] Direct viewers: 1 (Users)
[INFO] Transitive viewers: 14 (all users transitively)
[INFO] Flagged viewers: 5 (flagged users only)
[INFO] Removing ABCTechnicians from Technicians...
[INFO] Jale can no longer view (transitive path broken)
[INFO] Egin can still view (still in HelpDesk → Admins → Users)
[INFO] Tests passed: 14/14
```

#### 4.3 Interpreting Results

**Direct viewers (1)**: Only the `Users` group has a direct grant.

**Transitive viewers (14)**: All 11 individual users + 3 groups can transitively view the document because they are members (directly or indirectly) of `Users`:
- Ali, Jale, Egin, Irmak, Hakan, Gül, Fuat, Can, Burcu (individual users)
- Managers, Technicians, ABCTechnicians, FlaggedTechnicians (groups)

**Flagged viewers (5)**: Only subjects with the `flag` predicate:
- Egin, Ali, Gül, Fuat (individual flagged users)
- FlaggedTechnicians (flagged group)

**After removal**: When `ABCTechnicians` is removed from `Technicians`, Jale loses transitive membership in `Users`, so she can no longer view the document.

### Step 5: Manual Interaction (Optional, 10 minutes)

#### 5.1 Access the Oracle API

The Oracle API provides relationship management. While the cluster is running from Step 3:

```bash
# Assuming bootstrap node API on port 50000
BASE_URL="http://172.17.0.2:50000"

# Create a namespace (requires client certificate in production)
curl -X POST $BASE_URL/oracle/namespace/demo-ns

# Note: Full API examples require MTLS client certificates
# See examples/ directory for certificate generation
```

#### 5.2 Explore Environment Variables

Check how Sky nodes are configured:

```bash
docker exec -it bootstrap_bootstrap_1 env | grep -E "(GENESIS|BIND|PORT|SEED)"
```

**Variables**:
- `GENESIS='true'`: This node participates in Genesis block generation
- `BIND_INTERFACE=eth0`: Network interface to bind services
- `API=50000`: Oracle API port
- `APPROACH=50001`: Fireflies membership approach port
- `CLUSTER=50002`: Fireflies membership cluster port
- `SERVICE=50003`: Internal service port
- `HEALTH=50004`: Health check port
- `APPROACHES/SEEDS`: Discovery endpoints for joining nodes

See [examples/.env.example](examples/.env.example) for full configuration options.

### Step 6: Scale the Cluster (Optional, 5 minutes)

Add more nodes to the cluster:

```bash
cd local-demo/nodes
docker-compose up --scale node=3
```

**Expected**:
```
node_1  | Joining cluster via seed 172.17.0.2:50002
node_2  | Joining cluster via seed 172.17.0.2:50002
node_3  | Joining cluster via seed 172.17.0.2:50002
...
node_1  | Joined cluster, member ID: <UUID>
```

**What's happening**: New nodes use the SEED configuration to discover and join the existing cluster. They participate in consensus but are not part of the Genesis kernel.

### Step 7: Shutdown (2 minutes)

Gracefully stop all nodes:

```bash
# Terminal 1 (bootstrap)
Ctrl+C
docker-compose down

# Terminal 2 (kernel)
Ctrl+C
docker-compose down

# Terminal 3 (nodes, if running)
Ctrl+C
docker-compose down
```

**Clean up Docker resources**:
```bash
docker system prune -f
```

---

## Common Demo Scenarios

### Scenario 1: Simple Membership Check

**Goal**: Check if a user is a member of a group.

**API Call** (pseudo-code):
```javascript
// Add Alice to Admins group
oracle.map(alice, adminGroup)

// Check if Alice is in Admins
oracle.check(adminGroup.assertion(alice))
// Returns: true
```

### Scenario 2: Transitive Permission

**Goal**: Grant permission to a group, check individual user access.

**API Call** (pseudo-code):
```javascript
// Grant Admins -> Document:456 View
oracle.add(adminGroup.assertion(document456View))

// Check if Alice (member of Admins) can view
oracle.check(document456View.assertion(alice))
// Returns: true (transitive)
```

### Scenario 3: Filtered Query

**Goal**: Find all flagged users who can access a resource.

**API Call** (pseudo-code):
```javascript
// Get all flagged subjects who can view Document:456
var flaggedViewers = oracle.expand(flagPredicate, document456View)
// Returns: [Alice, Bob, ...] (only flagged users)
```

---

## Understanding the Output

### Bootstrap Node Output

Key log messages:

```
Sky node started on 172.17.0.2          # Node is up
Bootstrapping Genesis cluster member    # Initializing Genesis
Minimal quorum achieved: 4 members      # Cluster operational
Genesis block committed at view 0       # First consensus checkpoint
```

### Kernel Node Output

```
Connecting to bootstrap at 172.17.0.2:50001   # Discovery
Joined cluster, member ID: <UUID>             # Successful join
Synchronized to view 0                        # Caught up with Genesis
```

### Smoke Test Output

```
Creating namespace 'my-org' with relations     # Setup
Mapping 17 relationships                       # Group memberships
Transitive viewers: 14                         # Permission check
Flagged viewers: 5                             # Filtered check
Jale can no longer view                        # Revocation test
Tests passed: 14/14                            # Success
```

---

## Troubleshooting

For detailed troubleshooting, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md). Common issues:

### Issue: "Cannot connect to Docker daemon"

**Solution**:
```bash
# macOS/Linux
sudo systemctl start docker

# macOS with Docker Desktop
open -a Docker
```

### Issue: "Genesis block not committed after 5 minutes"

**Solution**:
- Check all 4 nodes are running: `docker ps`
- Check logs: `docker-compose logs bootstrap`
- Verify network connectivity: `docker network inspect local-demo_default`
- Restart cluster: `docker-compose down && docker-compose up`

### Issue: "Port already in use"

**Solution**:
```bash
# Find process using port 50000
lsof -i :50000

# Kill process or change port in compose.yaml
```

### Issue: "SmokeTest fails with timeout"

**Solution**:
- Ensure you have 8GB+ RAM available
- Increase Docker Desktop memory allocation (Preferences → Resources)
- Check test heap allocation in `pom.xml` (-Xmx10G)

### Issue: "TestContainers can't pull image"

**Solution**:
```bash
# Build image locally first
./mvnw clean install -pl sky-image

# Verify image exists
docker images | grep sky-image
```

---

## Next Steps

After completing this demo:

1. **Explore the code**: Start with `nut/src/main/java/com/hellblazer/nut/Launcher.java`
2. **Read architecture**: See [ARCHITECTURE.md](ARCHITECTURE.md) for module details
3. **Understand limitations**: See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) for POC constraints
4. **Learn Delos**: Visit [Delos platform](https://github.com/Hellblazer/Delos) for underlying components
5. **Experiment**: Modify the smoke test to add your own relationships and permissions

---

## Demo Success Criteria

You've successfully completed the demo if:

- ✅ Sky Docker image built successfully
- ✅ Bootstrap node started and reached "minimal quorum achieved"
- ✅ Kernel nodes joined cluster
- ✅ Smoke test passed with "14 transitive viewers"
- ✅ You understand the ReBAC example (users, groups, permissions)

---

## API Reference (Quick Reference)

**Oracle Operations**:

| Operation | Description | Example |
|-----------|-------------|---------|
| `map(subject, target)` | Add subject to target group | `map(alice, admins)` |
| `add(assertion)` | Grant permission | `add(users.assertion(doc123View))` |
| `check(assertion, timestamp)` | Verify permission | `check(doc123View.assertion(alice))` |
| `read(object)` | Get direct subjects | `read(doc123View)` |
| `expand(object)` | Get transitive subjects | `expand(doc123View)` |
| `expand(predicate, object)` | Get filtered transitive subjects | `expand(flag, doc123View)` |
| `remove(subject, target)` | Remove from group | `remove(alice, admins)` |
| `delete(assertion)` | Revoke permission | `delete(users.assertion(doc123View))` |

---

## Environment Variables Reference

See [examples/.env.example](examples/.env.example) for complete list. Key variables:

| Variable | Purpose | Example |
|----------|---------|---------|
| `GENESIS` | Genesis kernel member | `'true'` |
| `BIND_INTERFACE` | Network interface | `eth0` |
| `APPROACHES` | Discovery endpoints | `172.17.0.2:50001` |
| `SEEDS` | Cluster endpoints | `172.17.0.2:50002#50000` |
| `API` | Oracle API port | `50000` |
| `HEALTH` | Health check port | `50004` |

---

## FAQ

**Q: Can I run Sky without Docker?**
A: Yes, but you'll need to manually configure ports, network interfaces, and provide MTLS certificates. Docker is recommended for demos.

**Q: How many nodes can I run?**
A: POC tested with 4-10 nodes. Performance degrades with more nodes due to BFT overhead.

**Q: Can I use Sky in production?**
A: No, this is a fantasy POC. See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) and [HANDOFF.md](HANDOFF.md) for production considerations.

**Q: Where are the node certificates?**
A: Generated at runtime in-memory. POC uses ephemeral, self-signed certificates.

**Q: What consensus algorithm does Sky use?**
A: CHOAM, a Byzantine fault-tolerant consensus protocol from Delos. Similar to Raft but with BFT guarantees.

**Q: How is Sky different from Zanzibar/SpiceDB?**
A: Sky demonstrates BFT consensus + identity + secrets management together. Zanzibar focuses on ReBAC at scale. Sky shows how Delos components integrate for distributed systems.

---

## Additional Resources

- **Delos Platform**: https://github.com/Hellblazer/Delos
- **Fireflies Paper**: (see Delos docs)
- **CHOAM Paper**: (see Delos docs)
- **Stereotomy/KERL**: (see Delos docs)
- **Sky Issues**: https://github.com/Hellblazer/a-demo/issues

---

**End of Demo Guide** - Happy exploring! If you encounter issues, check [TROUBLESHOOTING.md](TROUBLESHOOTING.md) or open an issue with the "POC constraint" label if it's an expected limitation.
