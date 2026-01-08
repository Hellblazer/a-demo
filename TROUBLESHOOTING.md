# Sky Application Troubleshooting Guide

**Version**: 1.0
**Last Updated**: 2026-01-07

---

## Overview

This guide helps you diagnose and resolve common issues when running the Sky application. Issues are organized by category with step-by-step solutions.

### Quick Diagnostic Commands

```bash
# Check Docker is running
docker info

# Check running containers
docker ps

# Check Sky images
docker images | grep sky-image

# Check logs
docker-compose logs bootstrap
docker-compose logs kernel1

# Check network
docker network ls
docker network inspect bridge
```

---

## Top 5 Common Issues

### 1. Genesis Block Not Committed

**Symptom**:
```
bootstrap_1  | Waiting for kernel quorum...
bootstrap_1  | (timeout after 5+ minutes)
```

**Root Cause**: Cluster hasn't reached minimal quorum (4 nodes)

**Solution**:

```bash
# Step 1: Verify all 4 nodes are running
docker ps | grep sky-image
# Expected: 4 containers (1 bootstrap + 3 kernel)

# Step 2: Check bootstrap logs
docker-compose -f bootstrap/compose.yaml logs bootstrap | tail -50
# Look for: "Minimal quorum achieved: 4 members"

# Step 3: Check kernel logs
docker-compose -f kernel/compose.yaml logs | grep "Joined cluster"
# Expected: 3 "Joined cluster" messages

# Step 4: If fewer than 4 nodes, restart cluster
docker-compose -f bootstrap/compose.yaml down
docker-compose -f kernel/compose.yaml down
docker-compose -f bootstrap/compose.yaml up -d
# Wait 30 seconds
docker-compose -f kernel/compose.yaml up -d

# Step 5: Verify Genesis block committed
docker-compose -f bootstrap/compose.yaml logs bootstrap | grep "Genesis block committed"
```

**Expected Output**: "Genesis block committed at view 0"

**Still Failing?** Check network connectivity (see Issue #2)

---

### 2. Cannot Connect to Docker Daemon

**Symptom**:
```
ERROR: Cannot connect to the Docker daemon at unix:///var/run/docker.sock
```

**Root Cause**: Docker Desktop/Engine not running

**Solution**:

**macOS/Windows** (Docker Desktop):
```bash
# Start Docker Desktop
open -a Docker  # macOS
# Or: Windows Start Menu → Docker Desktop
```

Wait for Docker Desktop to fully start (whale icon stops animating).

**Linux** (Docker Engine):
```bash
# Start Docker daemon
sudo systemctl start docker

# Enable on boot
sudo systemctl enable docker

# Verify
docker info
```

**Permission Issue (Linux)**:
```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Log out and back in, then:
docker ps  # Should work without sudo
```

---

### 3. Port Already in Use

**Symptom**:
```
Error starting userland proxy: listen tcp 0.0.0.0:50000: bind: address already in use
```

**Root Cause**: Another process is using the port

**Solution**:

**Find the process**:
```bash
# macOS/Linux
lsof -i :50000
# Example output:
# COMMAND  PID USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
# java    1234 hal    45u  IPv6 123456      0t0  TCP *:50000 (LISTEN)

# Kill the process
kill -9 1234

# Or find all Sky processes
ps aux | grep sky-image
killall java  # Be careful!
```

**Windows**:
```powershell
# Find process
netstat -ano | findstr :50000

# Kill process (replace PID)
taskkill /PID 1234 /F
```

**Alternative**: Change ports in `compose.yaml`
```yaml
# bootstrap/compose.yaml
environment:
  API: 51000      # Changed from 50000
  APPROACH: 51001  # Changed from 50001
  # ... etc
```

---

### 4. Smoke Test Fails with Timeout

**Symptom**:
```
[ERROR] SmokeTest.smokin:123 - Test timed out after 120 seconds
java.util.concurrent.TimeoutException
```

**Root Cause**: Insufficient memory or slow Docker startup

**Solution**:

**Step 1**: Increase Docker Desktop memory allocation

```
Docker Desktop → Preferences → Resources → Memory
Set to: 8GB or higher (12GB recommended)
Restart Docker Desktop
```

**Step 2**: Verify heap allocation in `pom.xml`

```bash
grep -A 2 "argLine" pom.xml
# Expected: -Xmx10G -Xms4G
```

**Step 3**: Increase test timeout

```bash
# Run with longer timeout
./mvnw -P e2e test -pl local-demo -DtimeOut=300
```

**Step 4**: Run with fewer nodes

Edit `local-demo/src/test/java/SmokeTest.java`:
```java
// Reduce from 4 to 3 nodes if memory constrained
// (less reliable BFT, but faster for testing)
```

**Step 5**: Check system resources

```bash
# Check available memory
free -g  # Linux
top      # macOS/Linux

# Check Docker stats
docker stats
```

**Still Failing?** Check Docker logs for OOM (out of memory) errors:
```bash
docker-compose logs | grep -i "memory\|oom"
```

---

### 5. TestContainers Can't Pull Image

**Symptom**:
```
Caused by: org.testcontainers.containers.ContainerFetchException:
Can't get Docker image: RemoteDockerImage(imageNameFuture=com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT)
```

**Root Cause**: Docker image not built locally

**Solution**:

**Step 1**: Build Sky image

```bash
./mvnw clean install -DskipTests
```

**Step 2**: Verify image exists

```bash
docker images | grep sky-image
# Expected: com.hellblazer.sky/sky-image   0.0.1-SNAPSHOT   ...
```

**Step 3**: If image still missing, build sky-image module specifically

```bash
./mvnw clean install -pl sky-image -DskipTests
```

**Step 4**: Clear Docker image cache (if stale)

```bash
docker rmi com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT
./mvnw clean install -pl sky-image -DskipTests
```

**Step 5**: Verify TestContainers can access Docker

```bash
# Check Docker socket permissions
ls -la /var/run/docker.sock
# Should be readable by your user
```

---

## Category: Build Issues

### Maven Build Fails: Dependency Convergence

**Symptom**:
```
[ERROR] Rule 0: org.apache.maven.plugins.enforcer.DependencyConvergence failed
Dependency convergence error for ...
```

**Root Cause**: Strict dependency convergence enforcer rule (see [KNOWN_LIMITATIONS.md #42](KNOWN_LIMITATIONS.md#42))

**Solution**:

**Option 1**: Update dependency versions to converge
```bash
./mvnw dependency:tree -Dverbose > dep-tree.txt
# Find conflicting versions
# Update in root pom.xml <dependencyManagement>
```

**Option 2**: Temporarily disable for POC development
```xml
<!-- pom.xml -->
<plugin>
  <groupId>org.apache.maven.enforcer</groupId>
  <configuration>
    <skip>true</skip>  <!-- Add this -->
  </configuration>
</plugin>
```

**Option 3**: Use Maven dependency plugin
```bash
./mvnw dependency:analyze
./mvnw dependency:resolve
```

### Maven Build Fails: Out of Memory

**Symptom**:
```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin
java.lang.OutOfMemoryError: Java heap space
```

**Solution**:

```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx12G -Xms6G"
./mvnw clean install

# Or skip tests
./mvnw clean install -DskipTests

# Or build specific modules
./mvnw clean install -pl nut,sky,sky-image -DskipTests
```

### Protocol Buffer Compilation Fails

**Symptom**:
```
[ERROR] Failed to execute goal org.xolstice.maven.plugins:protobuf-maven-plugin
```

**Solution**:

```bash
# Clean generated sources
./mvnw clean

# Ensure protoc is available
protoc --version
# If not found, it will be downloaded by Maven

# Rebuild
./mvnw generate-sources

# If still failing, manually clean
rm -rf target/generated-sources/protobuf
./mvnw compile
```

---

## Category: Docker Issues

### Docker Network Issues

**Symptom**:
```
bootstrap_1  | Cannot bind to 172.17.0.2:50001
ERROR: Network bridge has encountered an error
```

**Solution**:

**Check bridge network**:
```bash
docker network inspect bridge
# Verify 172.17.0.0/16 subnet

# If corrupted, recreate
docker network rm bridge  # May fail if in use
docker network create bridge
```

**Check firewall** (Linux):
```bash
sudo iptables -L DOCKER
# Ensure Docker chains are present

# Restart Docker to reset iptables
sudo systemctl restart docker
```

**macOS Docker Desktop**:
```
Docker Desktop → Preferences → Reset → Reset to factory defaults
(This will delete all containers and images!)
```

### Node Cannot Join Cluster

**Symptom**:
```
kernel1_1  | Timeout connecting to bootstrap at 172.17.0.2:50001
```

**Solution**:

**Step 1**: Verify bootstrap is running

```bash
docker ps | grep bootstrap
# Should show running container

docker-compose -f bootstrap/compose.yaml logs | tail -20
# Look for: "Sky node started on 172.17.0.2"
```

**Step 2**: Verify network connectivity

```bash
# From kernel container
docker exec -it kernel_kernel1_1 ping -c 3 172.17.0.2
# Should succeed

# Check if APPROACH port is listening
docker exec -it bootstrap_bootstrap_1 netstat -tuln | grep 50001
```

**Step 3**: Check environment variables

```bash
docker inspect kernel_kernel1_1 | grep -A 10 "Env"
# Verify APPROACHES='172.17.0.2:50001'
# Verify SEEDS='172.17.0.2:50002#50000'
```

**Step 4**: Check for IP conflicts

```bash
# macOS/Linux: check bridge network IPs
docker network inspect bridge | grep IPv4Address
# Ensure 172.17.0.2 is only assigned to bootstrap
```

### Container Exits Immediately

**Symptom**:
```
docker ps -a
CONTAINER ID   STATUS
abc123         Exited (1) 2 seconds ago
```

**Solution**:

**Check logs**:
```bash
docker logs abc123
# Look for error messages
```

**Common causes**:

1. **Missing environment variables**:
   ```bash
   # Verify required env vars in compose.yaml
   BIND_INTERFACE: eth0  # Required
   GENESIS: 'true'        # Required for kernel
   ```

2. **Invalid configuration**:
   ```bash
   # Check for typos in compose.yaml
   # Ensure ports don't conflict
   ```

3. **Java errors**:
   ```bash
   docker logs abc123 | grep -i "exception\|error"
   # Common: ClassNotFoundException, NoSuchMethodError
   ```

---

## Category: Runtime Issues

### Node Health Check Fails

**Symptom**:
```bash
curl http://172.17.0.2:50004/health
# No response or "Connection refused"
```

**Solution**:

**Step 1**: Verify container is running

```bash
docker ps | grep bootstrap
# STATUS should be "Up X minutes"
```

**Step 2**: Check health port binding

```bash
docker logs bootstrap_bootstrap_1 | grep -i "health"
# Look for: "Health check started on port 50004"

# Check port mapping
docker port bootstrap_bootstrap_1
```

**Step 3**: Access from inside container

```bash
docker exec -it bootstrap_bootstrap_1 curl http://localhost:50004/health
# Should return: {"status":"healthy","members":4}
```

**Step 4**: Check firewall (if accessing from host)

```bash
# Linux
sudo ufw status
sudo ufw allow 50004/tcp

# macOS
# Check System Preferences → Security → Firewall
```

### High CPU Usage

**Symptom**: Docker containers using 100% CPU

**Root Cause**: Busy-wait loops or gossip storms

**Solution**:

**Check logs for rapid output**:
```bash
docker-compose logs --tail=100 | wc -l
# If hundreds of lines per second, investigate

docker-compose logs | grep -i "warn\|error"
```

**Reduce cluster size**:
```bash
# Scale down nodes
docker-compose -f nodes/compose.yaml down

# Run with minimal quorum only (4 nodes)
```

**Check for configuration errors**:
```bash
# Verify reasonable timeout values
# Verify nodes aren't stuck in view changes
docker-compose logs | grep "view change"
```

### Memory Leak

**Symptom**: Container memory grows unbounded

**Solution**:

**Monitor memory**:
```bash
docker stats
# Watch RSS and cache growth
```

**Heap dump** (if Java OOM):
```bash
# Get container ID
docker ps | grep sky-image

# Trigger heap dump
docker exec -it abc123 jmap -dump:format=b,file=/tmp/heap.hprof 1

# Copy out
docker cp abc123:/tmp/heap.hprof ./heap.hprof

# Analyze with VisualVM or Eclipse MAT
```

**Restart containers**:
```bash
docker-compose restart
```

### Slow Performance

**Symptom**: Oracle API requests take >5 seconds

**Solution**:

**Check cluster size**:
```bash
curl http://172.17.0.2:50004/health | jq .members
# BFT overhead is O(n²), keep small for POC
```

**Check network latency**:
```bash
docker exec -it kernel1 ping -c 10 172.17.0.2
# Should be <1ms for local Docker
```

**Check database size**:
```bash
docker exec -it bootstrap du -sh /data/h2
# If >1GB, consensus log is large
```

**Profile**:
```bash
# Enable JMX in container
# Connect with VisualVM or JProfiler
```

---

## Category: Test Issues

### Test Compilation Fails

**Symptom**:
```
[ERROR] cannot find symbol: class SmokeTest
```

**Solution**:

```bash
# Clean and rebuild
./mvnw clean compile test-compile -pl local-demo

# If still fails, check Java version
java -version
# Expected: Java 22+

# Update JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 22)  # macOS
```

### Test Hangs Indefinitely

**Symptom**: Test runs but never completes

**Solution**:

**Enable debug logging**:
```bash
./mvnw -P e2e test -pl local-demo -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG
```

**Check for deadlocks**:
```bash
# Get JVM thread dump
jps  # Find PID
jstack <PID> | grep -A 20 "deadlock"
```

**Timeout the test**:
```bash
./mvnw -P e2e test -pl local-demo -Dtest.timeout=300000
```

---

## Category: API Issues

### Oracle API Returns 500 Error

**Symptom**:
```bash
curl -X POST http://172.17.0.2:50000/oracle/add
# HTTP 500 Internal Server Error
```

**Solution**:

**Check logs**:
```bash
docker-compose logs bootstrap | grep -i "error\|exception"
```

**Verify cluster is operational**:
```bash
curl http://172.17.0.2:50004/health
# Should return: {"status":"healthy","members":4}
```

**Check request format**:
```bash
# Ensure JSON is valid
# Ensure MTLS certificate is valid (if required)
```

### Oracle API Timeout

**Symptom**: Request takes >30 seconds, then times out

**Root Cause**: Consensus not reaching quorum

**Solution**:

**Check view stability**:
```bash
docker-compose logs | grep "view change"
# Frequent view changes indicate instability
```

**Check node failures**:
```bash
docker ps
# All kernel nodes should be running
```

**Restart cluster if unstable**:
```bash
docker-compose down && docker-compose up -d
```

---

## Diagnostic Checklist

When reporting issues, provide:

- [ ] Sky version: `git rev-parse HEAD`
- [ ] Docker version: `docker --version`
- [ ] OS: `uname -a` (Linux/macOS)
- [ ] Java version: `java -version`
- [ ] Maven version: `./mvnw --version`
- [ ] Output of: `docker ps`
- [ ] Output of: `docker-compose logs | tail -100`
- [ ] Output of: `curl http://172.17.0.2:50004/health`
- [ ] Steps to reproduce the issue
- [ ] Expected vs actual behavior

---

## Getting Help

If your issue isn't covered here:

1. **Search existing issues**: https://github.com/Hellblazer/a-demo/issues
2. **Check KNOWN_LIMITATIONS.md**: Your issue may be an expected POC constraint
3. **Create new issue**: Use template, attach logs, use "POC constraint" label if applicable
4. **Delos platform**: For underlying platform issues, see https://github.com/Hellblazer/Delos

---

## Quick Reference Commands

```bash
# Full restart
docker-compose -f bootstrap/compose.yaml down
docker-compose -f kernel/compose.yaml down
docker-compose -f bootstrap/compose.yaml up -d
sleep 30
docker-compose -f kernel/compose.yaml up -d

# View logs
docker-compose -f bootstrap/compose.yaml logs -f bootstrap

# Health check all nodes
curl http://172.17.0.2:50004/health  # Bootstrap
# (kernel node IPs vary, check docker ps)

# Clean everything
docker-compose down -v
docker system prune -af
./mvnw clean

# Rebuild from scratch
./mvnw clean install -DskipTests
./mvnw -P e2e test -pl local-demo
```

---

## See Also

- **DEMO_GUIDE.md** - Step-by-step demo walkthrough
- **KNOWN_LIMITATIONS.md** - Expected POC constraints
- **ARCHITECTURE.md** - System architecture overview
- **README.md** - Quick start guide

---

**Last Updated**: 2026-01-07 | **Maintainer**: Sky POC Team
