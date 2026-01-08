# Known Limitations and POC Constraints

**Document Version**: 1.0
**Last Updated**: 2026-01-07
**Classification**: Fantasy POC

---

## Overview

**Sky is a fantasy proof-of-concept (POC)** designed to demonstrate Byzantine fault-tolerant identity and secrets management capabilities using the Delos platform. This document outlines the known limitations and constraints that are **acceptable for POC scope** but would need to be addressed if this POC were promoted to production.

### What This Document Is

- ✅ A clear delineation of POC boundaries
- ✅ Rationale for design decisions appropriate to POC scope
- ✅ Production considerations if Sky is promoted beyond POC status
- ✅ Evidence-based documentation linked to actual codebase

### What This Document Is NOT

- ❌ A list of defects or bugs
- ❌ Security vulnerabilities requiring immediate remediation
- ❌ Production-critical issues

---

## POC Classification

Per [CLAUDE.md:7](CLAUDE.md#L7) and [README.md:7](README.md#L7), Sky is explicitly classified as a **fantasy POC** for:

- Demonstrating Delos platform capabilities
- Understanding BFT consensus and identity management
- Short-lived demo sessions (minutes to hours)
- Learning and exploration

**Not for**:
- Production deployments
- Long-running systems
- Security-critical applications
- Public-facing services

---

## POC Constraints

The following 7 items are **expected POC limitations**, not defects. Each has been validated in the actual codebase.

### 1. Token Logging at INFO Level

**GitHub Issue**: [#32](https://github.com/Hellblazer/a-demo/issues/32)
**Severity**: POC Constraint
**Label**: `POC constraint`

#### What It Is

Cryptographic tokens and sensitive data are logged at INFO level during token generation and validation.

#### Evidence in Codebase

- `sanctum-sanctorum/src/main/java/com/hellblazer/sky/sanctum/sanctorum/TokenGenerator.java:55`
  ```java
  log.info("Generated token: {}", token);
  ```
- `TokenGenerator.java:70`
  ```java
  log.info("Decrypted Token: {} is valid: {}", k.hash(), k.token);
  ```

#### Why Acceptable for POC

- **Debugging aid**: Helps trace token flow during demos
- **Short-lived sessions**: Demo sessions are ephemeral (minutes to hours)
- **Controlled environment**: POC runs in isolated, non-production environments
- **Learning tool**: Visibility aids understanding of the system

#### Production Considerations

If promoted to production:
- Move token logging to DEBUG level
- Implement structured logging with proper redaction
- Consider removing sensitive token values entirely
- Add audit logging with proper security controls

**Related Task**: [a-demo-a85](https://github.com/Hellblazer/a-demo/issues/a-demo-a85) (Reduce token logging verbosity)

---

### 2. Shamir Secret Sharing: Fresh SecureRandom Instance

**GitHub Issue**: [#35](https://github.com/Hellblazer/a-demo/issues/35)
**Severity**: POC Constraint
**Label**: `POC constraint`

#### What It Is

Shamir secret sharing operations create a fresh `SecureRandom` instance per operation rather than reusing a properly seeded instance.

#### Evidence in Codebase

- Initialization code in Shamir secret sharing operations
- Used only during cluster bootstrap (not hot-path code)

#### Why Acceptable for POC

- **Initialization only**: Occurs during cluster bootstrap, not runtime operations
- **Simplicity over optimization**: Prioritizes clear code over performance
- **Rare operation**: Shamir sharing happens once per cluster initialization
- **Non-critical path**: Not in performance-sensitive code

#### Production Considerations

If promoted to production:
- Reuse a properly seeded `SecureRandom` instance
- Benchmark to determine if optimization is necessary
- Consider usage patterns (may still not be worth optimizing)
- Document the choice either way

**Note**: This optimization may not be necessary even in production depending on usage patterns.

---

### 3. No Session Key Rotation

**GitHub Issue**: [#36](https://github.com/Hellblazer/a-demo/issues/36)
**Severity**: POC Constraint (Design Decision)
**Label**: `POC constraint`

#### What It Is

Session keys are not rotated during the lifetime of a session. Keys remain constant from session establishment until termination.

#### Evidence in Codebase

- No session key rotation mechanism found in codebase (expected)
- Sessions use long-lived keys for their duration

#### Why Acceptable for POC

- **Short-lived sessions**: Demo sessions last minutes to hours, not days/weeks
- **POC scope**: Feature not required for demonstrating BFT capabilities
- **Reduced complexity**: Simplifies demo code and understanding
- **Non-persistent**: POC sessions are torn down completely after demos

#### Production Considerations

If promoted to production with long-running sessions:
- Implement periodic session key rotation (recommended: hourly or daily)
- Choose rotation interval based on threat model
- Add key rotation protocol to session management
- Consider forward secrecy requirements

**Design Decision**: This was intentionally omitted from POC scope to focus on core BFT demonstrations.

---

### 4. Volatile Access Patterns with Defensive Copying

**GitHub Issue**: [#39](https://github.com/Hellblazer/a-demo/issues/39)
**Severity**: POC Constraint (Pattern Choice)
**Label**: `POC constraint`

#### What It Is

Code uses defensive copying to protect against concurrent modifications rather than more sophisticated concurrent data structures.

#### Evidence in Codebase

- Defensive copying patterns used throughout codebase
- Volatile access with proper defensive patterns

#### Why Acceptable for POC

- **Defensive copying is correct**: Pattern prevents concurrent modification issues
- **Not a concurrency bug**: Any potential NPE would be a logic error, not a race condition
- **Simplicity**: Easier to understand than concurrent collections
- **POC scope**: Performance not critical for demo sessions

#### Production Considerations

If promoted to production:
- Review concurrent access patterns for performance
- Consider using concurrent collections (e.g., `ConcurrentHashMap`) where appropriate
- Benchmark to identify actual bottlenecks
- Keep defensive copying where it makes code clearer

**Note**: Defensive copying is a valid concurrency pattern. This is not a defect, but a design choice.

---

### 5. No Certificate Revocation Mechanism

**GitHub Issue**: [#41](https://github.com/Hellblazer/a-demo/issues/41)
**Severity**: POC Constraint (Scope Limitation)
**Label**: `POC constraint`

#### What It Is

The MTLS (mutual TLS) implementation does not include certificate revocation checking via CRL (Certificate Revocation List) or OCSP (Online Certificate Status Protocol).

#### Evidence in Codebase

- No CRL checking implementation found (expected)
- No OCSP implementation found (expected)
- Certificate validation present, but no revocation checking

#### Why Acceptable for POC

- **No external PKI**: POC scope does not include external PKI infrastructure
- **Controlled environment**: All certificates are POC-generated and controlled
- **Short-lived**: Demo sessions don't persist long enough for revocation to be relevant
- **Self-contained**: POC doesn't integrate with external certificate authorities

#### Production Considerations

If promoted to production:
- Implement CRL checking for certificate validation
- Consider OCSP for real-time revocation status
- Integrate with external PKI infrastructure
- Add certificate lifecycle management
- Implement key rotation and certificate renewal

**Scope Decision**: External PKI integration is out of scope for this POC.

---

### 6. Strict Dependency Convergence Enforcement

**GitHub Issue**: [#42](https://github.com/Hellblazer/a-demo/issues/42)
**Severity**: POC Constraint (Build Discipline)
**Label**: `POC constraint`

#### What It Is

Maven enforcer plugin configured with strict `dependencyConvergence` rule requiring all transitive dependencies to converge to a single version.

#### Evidence in Codebase

- `pom.xml`: Maven enforcer plugin with `dependencyConvergence` rule
- Prevents transitive dependency version conflicts

#### Why This Is the Current State

- **Intentional discipline**: Ensures consistent dependency versions across multi-module build
- **Prevents version conflicts**: Eliminates transitive dependency version mismatches
- **Build reliability**: Makes builds reproducible and predictable

#### Consideration for POC

While strict convergence ensures consistency, it can make dependency updates more difficult. Consider whether this level of strictness is necessary for POC development velocity.

#### Production Considerations

- **Maintain for production**: Strict convergence prevents runtime version conflicts
- **Or relax for POC**: If it impedes development, consider relaxing temporarily
- **Document exceptions**: If relaxing, document any known version conflicts
- **Re-enable before production**: Ensure strict convergence for production deployments

**Trade-off**: Consistency vs. flexibility. Current choice prioritizes consistency.

---

### 7. 10GB Test Heap Allocation

**GitHub Issue**: [#43](https://github.com/Hellblazer/a-demo/issues/43)
**Severity**: POC Constraint (Possible Necessity)
**Label**: `POC constraint`

#### What It Is

Test suite configured with `-Xmx10G -Xms4G` heap allocation for running distributed system tests.

#### Evidence in Codebase

- `pom.xml:308`
  ```xml
  <argLine>-Xmx10G -Xms4G</argLine>
  ```

#### Why This Is Necessary

Documented breakdown of 10GB peak usage (see `pom.xml:308-340` for details):

1. **TestContainers orchestration overhead**: 500MB-1GB
2. **Four Docker containers** running Sky nodes: 4 × ~1.5GB = 6GB
3. **H2 in-memory databases** per node: 4 × ~500MB = 2GB
4. **Consensus state and BFT protocol buffers**: ~1GB
5. **JVM overhead and test framework**: ~500MB

**Total estimated peak usage**: 9-10GB

#### Module-Specific Requirements

Task [a-demo-7c0](https://github.com/Hellblazer/a-demo/issues/a-demo-7c0) investigated:

- **Unit tests** (nut, sanctum, grpc, etc.): Can run with `-Xmx2G`
- **Integration tests** (local-demo SmokeTest): Require full `-Xmx10G` allocation

**Recommendation**: Run unit tests and integration tests separately for faster feedback:
```bash
# Unit tests (fast, low memory)
mvn test -pl nut,sanctum,grpc

# Integration tests (slow, high memory)
mvn -P e2e test -pl local-demo
```

#### Production Considerations

- **Production runtime needs less**: Sky nodes in production require ~2-4GB heap per node
- **Test heap is for orchestration**: The 10GB is for running 4 nodes simultaneously + TestContainers
- **Docker memory allocation**: Ensure Docker Desktop has 12GB+ allocated for tests
- **Keep TestContainers context**: Document why distributed tests need significant memory

**Action**: See task a-demo-7c0 for memory requirement investigation and documentation.

---

## Additional POC Constraints

Beyond the 7 GitHub issues, the following are also POC constraints:

### Hardcoded Bootstrap Address

- **What**: Bootstrap node address hardcoded to `172.17.0.2`
- **Location**: `local-demo/` Docker Compose configurations
- **Why**: Simplifies demo setup
- **Production**: Make discoverable or configurable

### Hardcoded Shared Secret

- **What**: Shamir shared secret hardcoded for demo convenience
- **Location**: Cluster initialization code
- **Why**: Allows quick demos without interactive secret sharing
- **Production**: Implement proper Shamir bootstrapping with client tools

### No Monitoring or Observability

- **What**: No metrics, tracing, or distributed logging
- **Why**: POC focuses on core functionality
- **Production**: Add Prometheus metrics, OpenTelemetry tracing, centralized logging

### Limited Error Handling

- **What**: Error handling optimized for demo clarity, not robustness
- **Why**: Prioritizes understanding over production-grade fault tolerance
- **Production**: Comprehensive error handling, circuit breakers, retry logic

---

## Summary Table

| Issue | Constraint | POC Rationale | Production Action |
|-------|-----------|---------------|-------------------|
| [#32](https://github.com/Hellblazer/a-demo/issues/32) | Token logging at INFO | Debugging aid, short sessions | Move to DEBUG, implement redaction |
| [#35](https://github.com/Hellblazer/a-demo/issues/35) | Fresh SecureRandom | Initialization only, simplicity | Reuse instance (if benchmarks show benefit) |
| [#36](https://github.com/Hellblazer/a-demo/issues/36) | No key rotation | Short-lived sessions | Implement periodic rotation |
| [#39](https://github.com/Hellblazer/a-demo/issues/39) | Defensive copying | Correct pattern, POC scope | Consider concurrent collections |
| [#41](https://github.com/Hellblazer/a-demo/issues/41) | No cert revocation | No external PKI | Implement CRL/OCSP |
| [#42](https://github.com/Hellblazer/a-demo/issues/42) | Strict convergence | Intentional discipline | Maintain or document exceptions |
| [#43](https://github.com/Hellblazer/a-demo/issues/43) | 10GB test heap | Distributed testing | Investigate and document minimum |

---

## Path to Production

If Sky POC is promoted to production, see [HANDOFF.md](HANDOFF.md) for:

- Complete list of remaining work
- Infrastructure requirements
- Team skills needed
- Estimated timeline
- Production readiness assessment

---

## Questions or Concerns?

- **POC scope questions**: Review [CLAUDE.md](CLAUDE.md) for project classification
- **Demo issues**: See [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- **GitHub issues**: Use "POC constraint" label for expected limitations
- **Production planning**: See [HANDOFF.md](HANDOFF.md) for promotion path

---

**Document Maintenance**: Update this document when POC constraints change or new limitations are discovered during demos.
