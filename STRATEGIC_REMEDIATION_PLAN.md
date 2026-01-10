# Sky Application Strategic Remediation Plan

**Version**: 2.0
**Created**: 2026-01-09
**Based On**: COMPREHENSIVE_CODE_REVIEW.md
**Status**: Ready for plan-auditor review

---

## Executive Summary

This strategic plan addresses all 39 issues identified in the comprehensive code review of the Sky Application, a Byzantine fault-tolerant identity and secrets management system built on the Delos platform.

### Issue Summary
| Severity | Count | Focus |
|----------|-------|-------|
| Critical Security | 5 | Must fix before any production use |
| High Priority | 10 | Security and reliability |
| Architectural | 6 | Foundation for maintainability |
| Medium Priority | 10 | Code quality |
| Low Priority | 14 | Improvements |
| **Total** | **39** | |

### Timeline Overview
| Phase | Duration | Effort | Team Size |
|-------|----------|--------|-----------|
| Phase 1: Critical Security | 4 weeks | 80-100 hours | 2 |
| Phase 2: Architectural Foundation | 8 weeks | 160-200 hours | 2-3 |
| Phase 3: Testing Infrastructure | 6 weeks | 120-150 hours | 2 |
| Phase 4: Code Quality & Performance | 6 weeks | 100-140 hours | 2 |
| Phase 5: Operational Readiness | 6 weeks | 120-160 hours | 2-3 |
| **Total** | **30 weeks** | **580-750 hours** | |

**Critical Path**: Phase 1 -> Phase 2 -> Phase 3 -> Phase 5
**Parallelization**: Phase 3 (late) || Phase 4, Phase 4 || Phase 5 (early)

---

## Phase 1: Critical Security Fixes

**Duration**: 4 weeks (2 sprints)
**Effort**: 80-100 hours
**Team**: 1 Security Engineer + 1 Senior Backend Developer
**Gate Criteria**: ALL issues resolved before proceeding

This phase addresses the 5 critical security vulnerabilities that **disqualify the system from production use**.

### 1.1 Certificate Validator with NO Validation (CRITICAL #2)

**Priority**: P0 - Blocking
**Effort**: 3-5 days
**Risk**: CRITICAL - Enables MITM attacks on all inter-node communication

#### Description
The current implementation uses no-op certificate validators that accept ANY certificate without validation.

#### Affected Files
- `/Users/hal.hildebrand/git/a-demo/nut/src/main/java/com/hellblazer/nut/SkyApplication.java:495-505`
- `/Users/hal.hildebrand/git/a-demo/nut/src/main/java/com/hellblazer/nut/Sphinx.java:419-429`

#### Current Code
```java
private CertificateValidator validator() {
    return new CertificateValidator() {
        @Override
        public void validateClient(X509Certificate[] chain) {
            // NO-OP - ACCEPTS ANY CERTIFICATE
        }

        @Override
        public void validateServer(X509Certificate[] chain) {
            // NO-OP - ACCEPTS ANY CERTIFICATE
        }
    };
}
```

#### Implementation Steps
1. **Analysis** (4 hours)
   - Identify all CertificateValidator usage points
   - Document trust model requirements
   - Review StereotomyValidator from Delos

2. **Design** (4 hours)
   - Define validation strategy (StereotomyValidator vs CA chain)
   - Document configuration options
   - Create test plan

3. **Implementation** (16 hours)
   - Create `SkyApplicationCertificateValidator` class
   - Implement `validateClient()` with proper chain validation
   - Implement `validateServer()` with proper chain validation
   - Add configurable trust store support
   - Remove all `CertificateValidator.NONE` references

4. **Testing** (8 hours)
   - Unit tests: valid cert accepted, invalid rejected
   - Integration tests: self-signed rejected, expired rejected
   - Security tests: tampered cert rejected
   - Performance tests: < 5ms validation latency

#### Acceptance Criteria
- [ ] All certificate validators perform actual validation
- [ ] Invalid certificates rejected with proper error
- [ ] Self-signed certificates rejected (unless explicitly trusted)
- [ ] Expired certificates rejected
- [ ] Test coverage: 95%+
- [ ] No performance regression (< 5ms P99)

#### Dependencies
- None (can start immediately)

#### Testing Requirements
```java
// Required test cases
@Test void testValidCertificateAccepted()
@Test void testInvalidCertificateRejected()
@Test void testSelfSignedRejected()
@Test void testExpiredCertRejected()
@Test void testTamperedCertRejected()
@Test void testUnknownCARejected()
@Test void testValidationPerformance()
```

---

### 1.2 Hardcoded Test Secrets in Production Code (CRITICAL #1)

**Priority**: P0 - Blocking
**Effort**: 1-2 days
**Risk**: HIGH - Credential exposure in source control

#### Description
Hardcoded password "foo" used in database configuration and JOOQ code generation.

#### Affected Files
- `/Users/hal.hildebrand/git/a-demo/nut/pom.xml:14`

#### Current Code
```xml
<db.password>foo</db.password>
```

#### Implementation Steps
1. **Identify all hardcoded secrets** (2 hours)
   - Search codebase for hardcoded strings
   - Document all secret locations
   - Categorize: test-only vs production

2. **Create configuration abstraction** (4 hours)
   - Add Maven properties with secure defaults
   - Create environment variable mappings
   - Update JOOQ configuration

3. **Update documentation** (2 hours)
   - Document required environment variables
   - Add setup instructions

#### Acceptance Criteria
- [ ] No hardcoded passwords in source code
- [ ] Secrets read from environment variables
- [ ] Clear documentation for secret setup
- [ ] Test databases use generated passwords

#### Dependencies
- None

---

### 1.3 Development Secret Exposure via Command-Line (CRITICAL #3)

**Priority**: P0 - Blocking
**Effort**: 1-2 days
**Risk**: HIGH - Secrets visible in process listings and logs

#### Description
Development secrets can be passed as command-line arguments, which are visible in `ps aux` output, shell history, and container logs.

#### Affected Files
- `/Users/hal.hildebrand/git/a-demo/nut/src/main/java/com/hellblazer/nut/Launcher.java:172`

#### Current Code
```java
Sphinx sphinx = argv.length == 1 ? new Sphinx(config) : new Sphinx(config, argv[1]);
```

#### Implementation Steps
1. **Refactor secret loading** (8 hours)
   - Remove command-line argument for secrets
   - Implement environment variable loading
   - Add secure file-based secret loading option
   - Add explicit warning in logs when dev mode enabled

2. **Update entry point** (4 hours)
   - Modify Launcher.java to use new secret loading
   - Add validation that secrets are properly sourced
   - Log warning if secrets appear to be test values

#### Acceptance Criteria
- [ ] No secrets accepted via command-line arguments
- [ ] Secrets loaded from environment variables or secure files
- [ ] Warning logged if dev mode is detected
- [ ] Documentation updated with secure secret handling

#### Dependencies
- 1.2 (Hardcoded secrets removal)

---

### 1.4 Missing Null Safety on Cryptographic Master Key (CRITICAL #4)

**Priority**: P0 - Blocking
**Effort**: 1-2 days
**Risk**: HIGH - NullPointerException crashes enclave service

#### Description
The `master` key can be null before provisioning, but code doesn't check before use.

#### Affected Files
- `/Users/hal.hildebrand/git/a-demo/sanctum-sanctorum/src/main/java/com/hellblazer/sky/sanctum/sanctorum/SanctumSanctorum.java:300`

#### Current Code
```java
var encrypted = SanctumSanctorum.encrypt(master.getEncoded(), secretKey, request.getNonce().toByteArray());
// master can be null - no check!
```

#### Implementation Steps
1. **Add null checks to all master key usages** (4 hours)
   - Identify all locations where master key is accessed
   - Add null checks with appropriate error handling
   - Return gRPC FAILED_PRECONDITION status when not provisioned

2. **Add provisioning state management** (4 hours)
   - Create explicit provisioning state enum
   - Add state transitions with logging
   - Validate state before cryptographic operations

3. **Testing** (4 hours)
   - Test operations before provisioning
   - Test operations during provisioning
   - Test operations after provisioning

#### Acceptance Criteria
- [ ] No NullPointerException on unprovisioned master key
- [ ] gRPC FAILED_PRECONDITION returned when not provisioned
- [ ] Clear error messages for provisioning state issues
- [ ] Test coverage for all state transitions

#### Dependencies
- None

---

### 1.5 Insufficient Key Derivation Validation (CRITICAL #5)

**Priority**: P0 - Blocking
**Effort**: 2-3 days
**Risk**: CRITICAL - Assertions can be disabled in production

#### Description
Uses assertion for cryptographic key length validation, which can be disabled with `-da` flag.

#### Affected Files
- `/Users/hal.hildebrand/git/a-demo/sanctum-sanctorum/src/main/java/com/hellblazer/sky/sanctum/sanctorum/SanctumSanctorum.java:327`

#### Current Code
```java
this.master = new SecretKeySpec(parameters.algorithm.digest(root).getBytes(), "AES");
assert master.getEncoded().length == 32 : "Must result in a 32 byte AES key: " + master.getEncoded().length;
```

#### Implementation Steps
1. **Replace all cryptographic assertions** (8 hours)
   - Identify all assertion-based crypto validation
   - Replace with explicit runtime checks
   - Add proper exception types for crypto failures

2. **Implement HKDF key derivation** (8 hours)
   - Replace direct hash with HKDF-SHA256 per RFC 5869
   - Add proper salt and context parameters
   - Document key derivation in security architecture

3. **Testing** (4 hours)
   - Test with assertions disabled (-da)
   - Verify runtime checks catch all issues
   - Vector tests against RFC 5869

#### Acceptance Criteria
- [ ] No assertions for security-critical validation
- [ ] Runtime checks throw appropriate exceptions
- [ ] Key derivation follows NIST SP 800-56C
- [ ] All tests pass with -da flag

#### Dependencies
- None

---

### Phase 1 Dependency Graph

```
     +---------+
     |  1.1    |  Certificate Validator
     +---------+
          |
          v
     +---------+
     |  1.2    |  Hardcoded Secrets
     +---------+
          |
          v
     +---------+
     |  1.3    |  CLI Secret Exposure
     +---------+


     +---------+        +---------+
     |  1.4    |        |  1.5    |   (Independent - can run parallel)
     +---------+        +---------+
     Null Safety        Key Derivation

PARALLEL STREAMS:
Stream A: 1.1 -> 1.2 -> 1.3   (Certificate & Secrets)
Stream B: 1.4                  (Null Safety)
Stream C: 1.5                  (Key Derivation)
```

### Phase 1 Sprint Structure

**Sprint 1 (Weeks 1-2)**
- 1.1 Certificate Validator (critical path)
- 1.4 Null Safety (parallel)
- 1.5 Key Derivation (parallel)

**Sprint 2 (Weeks 3-4)**
- 1.2 Hardcoded Secrets
- 1.3 CLI Secret Exposure
- Security review and testing
- Phase gate verification

### Phase 1 Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Certificate validation breaks existing mTLS | Medium | High | Comprehensive testing, staged rollout |
| Key derivation changes incompatible with existing data | Low | Critical | Migration script, backward compatibility |
| Secret loading changes break Docker deployment | Medium | Medium | Test Docker Compose configurations |

### Phase 1 Success Criteria
- [ ] All 5 critical issues resolved
- [ ] External security review passed
- [ ] Test coverage > 95% for security code
- [ ] No hardcoded secrets remaining
- [ ] All tests pass with -da flag

---

## Phase 2: Architectural Foundation

**Duration**: 8 weeks (4 sprints)
**Effort**: 160-200 hours
**Team**: 2-3 Senior Distributed Systems Engineers
**Dependencies**: Phase 1 complete

This phase establishes proper architectural boundaries that enable testability and maintainability.

### 2.1 Create Abstraction Layer from Delos (Architectural Issue #1)

**Priority**: P1 - High
**Effort**: 3-4 weeks
**Risk**: HIGH - Deep coupling may require significant refactoring

#### Description
The core application runtime directly imports and instantiates Delos platform components without an abstraction layer.

#### Affected Files
- `/Users/hal.hildebrand/git/a-demo/nut/src/main/java/com/hellblazer/nut/SkyApplication.java:24-50`
- `/Users/hal.hildebrand/git/a-demo/nut/src/main/java/com/hellblazer/nut/Sphinx.java`
- Throughout nut module

#### Current Code
```java
import com.hellblazer.delos.archipelago.*;
import com.hellblazer.delos.choam.Parameters;
import com.hellblazer.delos.fireflies.View;
import com.hellblazer.delos.gorgoneion.Gorgoneion;
import com.hellblazer.delos.stereotomy.Stereotomy;
```

#### Implementation Steps
1. **Inventory Delos interface points** (16 hours)
   - Document all Delos imports and usages
   - Categorize by component (CHOAM, Fireflies, etc.)
   - Identify required abstractions

2. **Design domain service interfaces** (16 hours)
   - ConsensusService interface
   - MembershipService interface
   - IdentityService interface
   - Document contracts and behaviors

3. **Implement adapters** (40 hours)
   - DelosConsensusAdapter
   - DelossMembershipAdapter
   - DelosIdentityAdapter
   - Wire adapters in configuration

4. **Migrate code to abstractions** (24 hours)
   - Replace direct Delos usage with interfaces
   - Verify behavior unchanged
   - Update tests to use mocks

5. **Testing** (16 hours)
   - Integration tests with real adapters
   - Unit tests with mock adapters
   - Performance validation

#### Proposed Interface Design
```java
public interface ConsensusService {
    CompletableFuture<CommitResult> commit(Transaction tx);
    View getCurrentView();
    boolean isLeader();
}

public interface MembershipService {
    Set<Member> getActiveMembers();
    boolean isMember(Digest id);
    void registerMembershipListener(MembershipListener listener);
}

public interface IdentityService {
    Identifier getCurrentIdentifier();
    boolean verify(Signature signature, byte[] data);
    Signature sign(byte[] data);
}

// Delos-specific implementation
public class DelosConsensusAdapter implements ConsensusService {
    private final CHOAM choam;
    // Delos-specific implementation details isolated here
}
```

#### Acceptance Criteria
- [ ] No direct Delos imports in core business logic
- [ ] All Delos usage through defined interfaces
- [ ] Unit tests work with mock implementations
- [ ] No functional regression
- [ ] Documentation of abstraction layer

#### Dependencies
- Phase 1 complete

---

### 2.2 Clarify Sanctum Module Boundaries (Architectural Issue #2)

**Priority**: P1 - High
**Effort**: 1-2 weeks
**Risk**: Medium - Module restructuring may affect builds

#### Description
The boundary between `sanctum` (wrapper) and `sanctum-sanctorum` (server implementation) is unclear. Actual responsibilities don't match documentation.

#### Implementation Steps
1. **Document current responsibilities** (4 hours)
   - Analyze what each module actually does
   - Document dependencies between modules
   - Identify boundary violations

2. **Define target architecture** (8 hours)
   - Option A: Merge into single `identity` module
   - Option B: Make sanctum true abstraction with interfaces
   - Document chosen approach with rationale

3. **Refactor if needed** (24 hours)
   - Move code to appropriate locations
   - Update module dependencies
   - Fix import statements

4. **Update documentation** (8 hours)
   - Update module READMEs
   - Update ARCHITECTURE.md
   - Add package-info.java with documentation

#### Acceptance Criteria
- [ ] Each module has single clear purpose
- [ ] Module boundaries documented in ARCHITECTURE.md
- [ ] No circular dependencies
- [ ] READMEs comprehensive

#### Dependencies
- None (can start early in Phase 2)

---

### 2.3 Implement Application-Level Byzantine Failure Handling (Architectural Issue #3)

**Priority**: P1 - High
**Effort**: 3-4 weeks
**Risk**: HIGH - Core to system integrity

#### Description
For a system claiming Byzantine fault tolerance, there is no comprehensive error handling strategy for detecting or responding to Byzantine behaviors at the application layer.

#### Implementation Steps
1. **Document Byzantine handling requirements** (8 hours)
   - What Delos provides vs what application must verify
   - Threat model for application layer
   - Required detection mechanisms

2. **Design failure handling strategy** (16 hours)
   - Quorum verification in application layer
   - Signature validation chains
   - Timeout-based suspicion mechanisms
   - Recovery procedures

3. **Implement detection mechanisms** (32 hours)
   - Consensus result verification
   - Signature validation before trusting data
   - Node suspicion tracking
   - Timeout monitoring

4. **Add Byzantine failure tests** (24 hours)
   - Network partition scenarios
   - Slow/silent node scenarios
   - Conflicting message injection
   - Signature forgery attempts

5. **Document in ARCHITECTURE.md** (8 hours)
   - Byzantine handling strategy
   - Delos vs application responsibilities
   - Layer contract definitions

#### Acceptance Criteria
- [ ] Application-level quorum verification implemented
- [ ] All cryptographic signatures validated before trust
- [ ] Timeout and suspicion mechanisms functional
- [ ] Byzantine failure tests passing
- [ ] Architecture documented

#### Dependencies
- 2.1 (Abstraction layer needed for testing)

---

### 2.4 Add API Versioning Strategy (Architectural Issue #4)

**Priority**: P2 - Medium
**Effort**: 1 week
**Risk**: Low - Proto changes only

#### Description
Proto service definitions have no versioning strategy, making backward compatibility impossible.

#### Affected Files
- `/Users/hal.hildebrand/git/a-demo/grpc/src/main/proto/*.proto`

#### Current Code
```protobuf
service Sphynx {
  rpc apply(sanctorum.EncryptedShare) returns(sanctorum.Status) {}
  rpc unwrap(google.protobuf.Empty) returns(sanctorum.UnwrapStatus){}
  // No version field, no service versioning
}
```

#### Implementation Steps
1. **Design versioning strategy** (8 hours)
   - Version field in all messages
   - Versioned package names
   - Compatibility guarantees

2. **Update proto definitions** (16 hours)
   - Add version field to messages
   - Add reserved field ranges
   - Add error code enums
   - Document compatibility in comments

3. **Create API evolution policy** (8 hours)
   - Document versioning policy
   - Define deprecation process
   - Create migration guide template

#### Acceptance Criteria
- [ ] All proto messages have version field
- [ ] Reserved field ranges defined
- [ ] API evolution policy documented
- [ ] Backward compatibility tested

#### Dependencies
- None

---

### 2.5 Fix Configuration Management (Architectural Issue #5)

**Priority**: P2 - Medium
**Effort**: 1-2 weeks
**Risk**: Low - Configuration changes

#### Description
Configuration loaded from both environment variables and YAML file, but precedence order is unclear and undocumented.

#### Affected Files
- `/Users/hal.hildebrand/git/a-demo/nut/src/main/java/com/hellblazer/nut/Launcher.java:46-100`
- `/Users/hal.hildebrand/git/a-demo/nut/src/main/java/com/hellblazer/nut/SkyConfiguration.java`

#### Implementation Steps
1. **Document current behavior** (4 hours)
   - Map all configuration sources
   - Document current precedence

2. **Implement explicit precedence** (16 hours)
   - CLI args > Env vars > YAML > defaults
   - Add validation on startup
   - Log effective configuration

3. **Add configuration validation** (8 hours)
   - Validate all values on load
   - Clear error messages
   - Fail fast on invalid config

4. **Documentation** (8 hours)
   - Create docs/configuration.md
   - Document all options with examples
   - Add troubleshooting section

#### Acceptance Criteria
- [ ] Clear precedence: CLI > ENV > YAML > defaults
- [ ] Configuration validated at startup
- [ ] Effective config logged (redacted)
- [ ] Comprehensive documentation

#### Dependencies
- None

---

### 2.6 Standardize Naming Conventions (Architectural Issue #6)

**Priority**: P3 - Low
**Effort**: 2-3 days
**Risk**: Low - Naming changes only

#### Description
Project uses "Sky", "Nut", "Sphinx", "Sphynx" inconsistently.

#### Implementation Steps
1. **Define naming conventions** (2 hours)
   - Standardize on "Sky" as primary term
   - Document mythology references if intentional

2. **Fix proto typo** (2 hours)
   - Rename `Sphynx` to `Sphinx` or document etymology

3. **Update documentation** (8 hours)
   - Consistent naming in all docs
   - Add naming conventions to ARCHITECTURE.md

#### Acceptance Criteria
- [ ] Consistent naming throughout
- [ ] Naming documented with rationale
- [ ] No typos in service names

#### Dependencies
- 2.4 (Proto updates)

---

### Phase 2 Dependency Graph

```
Phase 1 Complete
       |
       v
  +---------+        +---------+
  |   2.1   |------->|   2.3   |
  | Delos   |        | Byzantine|
  | Abstract|        | Handling |
  +---------+        +---------+
       |
       v
  +---------+
  |   2.2   |
  | Module  |
  | Bounds  |
  +---------+


  +---------+        +---------+
  |   2.4   |------->|   2.6   |
  | API     |        | Naming  |
  | Version |        | Std     |
  +---------+        +---------+


  +---------+
  |   2.5   |
  | Config  |
  | Mgmt    |
  +---------+

PARALLEL OPPORTUNITIES:
- 2.1 and 2.4 can start together
- 2.2 and 2.5 can start together
- 2.3 requires 2.1
- 2.6 requires 2.4
```

### Phase 2 Sprint Structure

**Sprint 3 (Weeks 5-6)**
- 2.1 Delos Abstraction (start)
- 2.2 Module Boundaries
- 2.4 API Versioning

**Sprint 4 (Weeks 7-8)**
- 2.1 Delos Abstraction (continue)
- 2.5 Configuration Management
- 2.6 Naming Standards

**Sprint 5 (Weeks 9-10)**
- 2.1 Delos Abstraction (complete)
- 2.3 Byzantine Handling (start)

**Sprint 6 (Weeks 11-12)**
- 2.3 Byzantine Handling (complete)
- Phase 2 integration testing
- Phase gate verification

### Phase 2 Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Delos coupling deeper than visible | High | High | Incremental extraction, feature flags |
| Module refactoring breaks builds | Medium | Medium | CI/CD verification, incremental changes |
| Byzantine handling incomplete | Medium | Critical | External review, chaos testing |

### Phase 2 Success Criteria
- [ ] All 6 architectural issues addressed
- [ ] Delos abstraction layer complete
- [ ] Module boundaries clear and documented
- [ ] Byzantine failure handling implemented
- [ ] API versioning strategy in place
- [ ] Configuration management documented

---

## Phase 3: Testing Infrastructure

**Duration**: 6 weeks (3 sprints)
**Effort**: 120-150 hours
**Team**: 2 Engineers (Backend + QA)
**Dependencies**: Phase 2 substantially complete

This phase establishes comprehensive testing infrastructure to support production quality.

### 3.1 Add Unit Test Framework and Coverage Tooling

**Priority**: P1 - High
**Effort**: 1-2 weeks

#### Implementation Steps
1. **Configure JaCoCo for coverage** (8 hours)
   - Add JaCoCo Maven plugin
   - Configure coverage thresholds (80% overall, 95% security)
   - Set up CI/CD coverage gates

2. **Add test utilities** (16 hours)
   - Create test fixtures for common scenarios
   - Add test data builders
   - Create mock implementations for abstractions

3. **Establish testing patterns** (8 hours)
   - Document test naming conventions
   - Create test templates
   - Add example tests for each pattern

#### Current State
- Only 6 test files in nut module
- ~120 Java files in codebase
- No coverage tooling configured

#### Target State
- 80%+ overall coverage
- 95%+ coverage for security code
- JaCoCo configured in CI/CD
- Coverage reports generated on every build

#### Acceptance Criteria
- [ ] JaCoCo configured and working
- [ ] Coverage gates enforced in CI/CD
- [ ] Test utilities and fixtures created
- [ ] Testing patterns documented

---

### 3.2 Unit Tests for Core Cryptography (ISSUE #16)

**Priority**: P1 - High
**Effort**: 2-3 weeks

#### Description
Core cryptography modules have insufficient test coverage.

#### Test Requirements
- TokenGenerator: generation, validation, expiration, edge cases
- Fernet token handling: TTL, revocation, format validation
- Shamir secret sharing: threshold, reconstruction, validation
- Key derivation: HKDF correctness, key length validation

#### Implementation Steps
1. **TokenGenerator tests** (16 hours)
   - Valid token generation
   - Token expiration
   - Invalid token format
   - Cache hit/miss scenarios

2. **Fernet token tests** (12 hours)
   - TTL enforcement
   - Invalid token rejection
   - Replay attack prevention

3. **Shamir tests** (16 hours)
   - Threshold reconstruction
   - Below-threshold failure
   - Share validation
   - Edge cases (min/max shares)

4. **Key derivation tests** (8 hours)
   - RFC 5869 vector tests
   - Key length validation
   - Salt and context handling

#### Acceptance Criteria
- [ ] 95%+ coverage for cryptographic code
- [ ] All edge cases tested
- [ ] RFC test vectors passing
- [ ] Security test suite passing

---

### 3.3 Byzantine Failure Scenario Tests (ISSUE #17)

**Priority**: P1 - High
**Effort**: 2-3 weeks

#### Description
Integration tests only validate happy path. Missing failure scenarios.

#### Test Scenarios Required
1. **Node Failure During Consensus**
   - Node crashes during block production
   - Node crashes during transaction commit
   - Recovery after node restart

2. **Network Partition Scenarios**
   - Minority partition
   - Majority partition
   - Partition heals

3. **Byzantine Behavior Simulation**
   - Conflicting message injection
   - Signature forgery attempts
   - Replay attacks

4. **Certificate Revocation**
   - Certificate rotation
   - Revoked certificate rejection

#### Implementation Steps
1. **Enhance TestContainers setup** (16 hours)
   - Add failure injection capabilities
   - Create network partition simulation
   - Add node restart utilities

2. **Implement failure tests** (32 hours)
   - Node failure scenarios
   - Network partition scenarios
   - Byzantine behavior tests
   - Recovery tests

3. **Add chaos engineering utilities** (16 hours)
   - Random failure injection
   - Network delay simulation
   - Resource exhaustion tests

#### Acceptance Criteria
- [ ] Node failure tests passing
- [ ] Network partition tests passing
- [ ] Byzantine behavior tests passing
- [ ] Chaos engineering tests implemented

---

### 3.4 Chaos Engineering Tests

**Priority**: P2 - Medium
**Effort**: 1-2 weeks

#### Implementation Steps
1. **Design chaos scenarios** (8 hours)
   - Random node failures
   - Network latency injection
   - Resource exhaustion
   - Clock skew simulation

2. **Implement chaos framework** (24 hours)
   - Create chaos test runner
   - Integrate with TestContainers
   - Add result validation

3. **Execute chaos tests** (8 hours)
   - Run chaos test suite
   - Document results
   - Fix discovered issues

#### Acceptance Criteria
- [ ] Chaos test framework operational
- [ ] Random failure injection working
- [ ] System recovers from chaos scenarios
- [ ] Results documented and issues tracked

---

### Phase 3 Dependency Graph

```
Phase 2 Substantially Complete
            |
            v
      +---------+
      |   3.1   |
      | Coverage|
      | Tooling |
      +---------+
            |
            v
      +---------+
      |   3.2   |
      | Crypto  |
      | Tests   |
      +---------+
            |
            v
      +---------+        +---------+
      |   3.3   |------->|   3.4   |
      | Byzantine|        | Chaos   |
      | Tests    |        | Tests   |
      +---------+        +---------+
```

### Phase 3 Sprint Structure

**Sprint 7 (Weeks 13-14)**
- 3.1 Coverage Tooling
- 3.2 Crypto Tests (start)

**Sprint 8 (Weeks 15-16)**
- 3.2 Crypto Tests (complete)
- 3.3 Byzantine Tests (start)

**Sprint 9 (Weeks 17-18)**
- 3.3 Byzantine Tests (complete)
- 3.4 Chaos Engineering Tests
- Phase gate verification

### Phase 3 Success Criteria
- [ ] JaCoCo coverage at 80%+ overall
- [ ] Cryptographic code at 95%+ coverage
- [ ] Byzantine failure tests comprehensive
- [ ] Chaos engineering tests operational
- [ ] No flaky tests

---

## Phase 4: Code Quality & Performance

**Duration**: 6 weeks (3 sprints)
**Effort**: 100-140 hours
**Team**: 2 Senior Developers
**Dependencies**: Phase 2 complete, can overlap with late Phase 3

This phase improves code quality and addresses performance issues.

### 4.1 Refactor God Classes (ISSUE #14)

**Priority**: P2 - Medium
**Effort**: 2 weeks

#### Description
SkyApplication.java (507 lines) handles initialization, networking, cryptography, provisioning, and shutdown.

#### Target Decomposition
- `SkyNetworking` - Router and communication setup
- `SkyProvisioning` - Provisioner and attestation logic
- `SkyLifecycle` - Startup/shutdown coordination
- `SkyApplication` - Orchestration (< 100 lines)

#### Implementation Steps
1. **Analyze responsibilities** (8 hours)
2. **Extract SkyNetworking** (16 hours)
3. **Extract SkyProvisioning** (16 hours)
4. **Extract SkyLifecycle** (16 hours)
5. **Refactor SkyApplication** (8 hours)
6. **Testing and validation** (8 hours)

#### Acceptance Criteria
- [ ] SkyApplication < 150 lines
- [ ] Each extracted class has single responsibility
- [ ] All tests passing
- [ ] No functional regression

---

### 4.2 Extract Duplicated Code (ISSUE #10)

**Priority**: P2 - Medium
**Effort**: 1 week

#### Description
Identical AES-GCM encryption/decryption logic duplicated in Sphinx.java and SanctumSanctorum.java.

#### Implementation Steps
1. **Create CryptoUtils module** (8 hours)
2. **Extract encryption logic** (8 hours)
3. **Update callers** (8 hours)
4. **Testing** (4 hours)

#### Acceptance Criteria
- [ ] Single implementation of AES-GCM
- [ ] No code duplication
- [ ] All tests passing

---

### 4.3 Apply Java 24 Modern Patterns (ISSUE #9, #7)

**Priority**: P2 - Medium
**Effort**: 1 week

#### Implementation Steps
1. **Systematic `var` usage** (8 hours)
2. **Replace ReentrantLock with AtomicReference** (8 hours)
3. **Apply modern patterns throughout** (8 hours)
4. **Validate with spotbugs** (4 hours)

#### Acceptance Criteria
- [ ] Consistent `var` usage
- [ ] Modern concurrency patterns
- [ ] No deprecated patterns

---

### 4.4 Fix Race Conditions (ISSUE #8)

**Priority**: P1 - High
**Effort**: 1 week

#### Description
Race condition in shutdown sequence - non-atomic nullification of token.

#### Implementation Steps
1. **Analyze shutdown sequence** (4 hours)
2. **Implement atomic shutdown** (12 hours)
3. **Add stress tests** (8 hours)

#### Acceptance Criteria
- [ ] Thread-safe shutdown
- [ ] No race conditions in tests
- [ ] Stress tests passing

---

### 4.5 Optimize Performance Bottlenecks

**Priority**: P2 - Medium
**Effort**: 1 week

#### Implementation Steps
1. **Profile critical paths** (8 hours)
2. **Optimize hot spots** (16 hours)
3. **Establish baselines** (8 hours)

#### Acceptance Criteria
- [ ] Performance baselines established
- [ ] No regressions from changes
- [ ] Critical path latency documented

---

### Phase 4 Sprint Structure

**Sprint 10 (Weeks 19-20)**
- 4.1 God Class Refactoring (start)
- 4.4 Race Condition Fixes

**Sprint 11 (Weeks 21-22)**
- 4.1 God Class Refactoring (complete)
- 4.2 Duplicated Code Extraction
- 4.3 Java 24 Patterns

**Sprint 12 (Weeks 23-24)**
- 4.5 Performance Optimization
- Phase gate verification

### Phase 4 Success Criteria
- [ ] No god classes (< 200 lines each)
- [ ] No code duplication
- [ ] Java 24 patterns applied consistently
- [ ] No race conditions
- [ ] Performance baselines established

---

## Phase 5: Operational Readiness

**Duration**: 6 weeks (3 sprints)
**Effort**: 120-160 hours
**Team**: 2-3 Engineers (Backend + DevOps/SRE)
**Dependencies**: Phases 1-4 complete

This phase prepares the system for production operation.

### 5.1 Complete Documentation Updates

**Priority**: P2 - Medium
**Effort**: 2 weeks

#### Deliverables
- Module READMEs (comprehensive)
- Configuration documentation
- Architecture documentation with C4 diagrams
- API documentation (JavaDoc)
- Troubleshooting guide
- Developer setup guide

---

### 5.2 Add Monitoring and Observability

**Priority**: P2 - Medium
**Effort**: 2 weeks

#### Implementation Steps
1. **Add Micrometer/Prometheus metrics** (16 hours)
2. **Instrument key operations** (16 hours)
3. **Add health checks** (8 hours)
4. **Create Grafana dashboards** (16 hours)

#### Metrics to Implement
- Certificate validation latency (histogram)
- Token cache hit rate (gauge)
- Consensus round time (histogram)
- Error counts by type (counter)
- Thread pool utilization (gauge)
- Memory usage (gauge)

---

### 5.3 Establish Deployment Procedures

**Priority**: P1 - High
**Effort**: 1 week

#### Deliverables
- Docker Compose for development
- Kubernetes manifests for production
- Helm chart (optional)
- Deployment checklist
- Rollback procedures

---

### 5.4 Create Runbooks and Operational Guides

**Priority**: P1 - High
**Effort**: 1 week

#### Deliverables
- Operations runbook
- Incident response procedures
- Common troubleshooting guides
- Backup and recovery procedures
- Security incident response

---

### Phase 5 Sprint Structure

**Sprint 13 (Weeks 25-26)**
- 5.1 Documentation
- 5.3 Deployment Procedures (start)

**Sprint 14 (Weeks 27-28)**
- 5.2 Monitoring and Observability
- 5.3 Deployment Procedures (complete)

**Sprint 15 (Weeks 29-30)**
- 5.4 Runbooks and Operational Guides
- Final validation and acceptance
- Production readiness review

### Phase 5 Success Criteria
- [ ] Comprehensive documentation
- [ ] Monitoring dashboards operational
- [ ] Deployment procedures tested
- [ ] Runbooks complete
- [ ] Production readiness review passed

---

## Overall Dependency Graph

```
 PHASE 1: Critical Security (Weeks 1-4)
 ========================================
 +---------+        +---------+        +---------+
 |  1.1    |------->|  1.2    |------->|  1.3    |
 | Cert    |        | Secrets |        | CLI     |
 +---------+        +---------+        +---------+

 +---------+        +---------+
 |  1.4    |        |  1.5    |       (Parallel with 1.1)
 | Null    |        | Key     |
 +---------+        +---------+
       |                |
       v                v
 +---------------------------+
 |     Phase 1 Complete      |
 +---------------------------+
              |
              v
 PHASE 2: Architectural Foundation (Weeks 5-12)
 ==============================================
       +---------------+
       |     2.1       |
       | Delos Abstract|
       +---------------+
              |
              +---> 2.3 Byzantine Handling
              |
       +------+------+
       |             |
    2.2 Module    2.5 Config
       |             |
       v             v
    2.4 API -----> 2.6 Naming
       |
       v
 +---------------------------+
 |    Phase 2 Complete       |
 +---------------------------+
              |
              v
 PHASE 3: Testing Infrastructure (Weeks 13-18)
 =============================================
       3.1 Coverage --> 3.2 Crypto --> 3.3 Byzantine --> 3.4 Chaos
                                                              |
                                                              v
                                           +---------------------------+
                                           |    Phase 3 Complete       |
                                           +---------------------------+
                                                         |
              +-----------------------------------------+
              |
              v
 PHASE 4: Code Quality (Weeks 19-24)      | Can overlap with late Phase 3
 =========================================
       4.1 Refactor --> 4.2 DRY --> 4.3 Java24 --> 4.4 Race --> 4.5 Perf
                                                                    |
                                                                    v
                                           +---------------------------+
                                           |    Phase 4 Complete       |
                                           +---------------------------+
                                                         |
                                                         v
 PHASE 5: Operational Readiness (Weeks 25-30)
 ============================================
       5.1 Docs <------+
                       | Parallel
       5.2 Monitoring <+
                       |
       5.3 Deployment <+
                       |
       5.4 Runbooks <--+
              |
              v
 +---------------------------+
 |   PRODUCTION READY        |
 +---------------------------+
```

---

## Critical Path Analysis

**Critical Path**: 1.1 -> 1.2 -> 1.3 -> 2.1 -> 2.3 -> 3.3 -> 5.3 -> Production

**Duration**: 30 weeks (7.5 months)

**Parallel Work Opportunities**:
1. Phase 1: Streams A (1.1-1.3), B (1.4), C (1.5) run in parallel
2. Phase 2: 2.1+2.4 start together; 2.2+2.5 start together
3. Phase 3: Late Phase 3 overlaps with Phase 4 start
4. Phase 4/5: Can have partial overlap

---

## Team Composition Recommendations

### Phase 1 Team (Critical Security)
| Role | Allocation | Responsibilities |
|------|------------|------------------|
| Security Engineer | 100% | Certificate validation, crypto review |
| Senior Backend Dev | 50% | Implementation, testing |
| External Reviewer | On-demand | Security audit |

### Phase 2 Team (Architecture)
| Role | Allocation | Responsibilities |
|------|------------|------------------|
| Senior Backend Dev 1 | 100% | Delos abstraction, Byzantine handling |
| Senior Backend Dev 2 | 75% | Module boundaries, configuration |
| Architect | 25% | Design review, documentation |

### Phase 3 Team (Testing)
| Role | Allocation | Responsibilities |
|------|------------|------------------|
| Backend Developer | 75% | Unit tests, integration |
| QA Engineer | 100% | Test strategy, chaos engineering |

### Phase 4 Team (Code Quality)
| Role | Allocation | Responsibilities |
|------|------------|------------------|
| Senior Backend Dev 1 | 100% | Refactoring, race conditions |
| Senior Backend Dev 2 | 50% | Code review, performance |

### Phase 5 Team (Operations)
| Role | Allocation | Responsibilities |
|------|------------|------------------|
| Backend Developer | 50% | Documentation, examples |
| DevOps/SRE Engineer | 100% | Monitoring, deployment |
| Technical Writer | 50% | Runbooks, operational docs |

---

## Sprint Structure Recommendation

**Sprint Duration**: 2 weeks
**Sprint Ceremonies**:
- Sprint Planning: Day 1 (2 hours)
- Daily Standup: 15 minutes
- Sprint Review: Last day (1 hour)
- Sprint Retrospective: Last day (1 hour)

| Sprint | Weeks | Phase | Focus |
|--------|-------|-------|-------|
| Sprint 1 | 1-2 | Phase 1 | Critical issues 1.1, 1.4, 1.5 |
| Sprint 2 | 3-4 | Phase 1 | Critical issues 1.2, 1.3, gate |
| Sprint 3 | 5-6 | Phase 2 | Delos abstraction start, modules |
| Sprint 4 | 7-8 | Phase 2 | Delos cont., config, naming |
| Sprint 5 | 9-10 | Phase 2 | Delos complete, Byzantine start |
| Sprint 6 | 11-12 | Phase 2 | Byzantine complete, gate |
| Sprint 7 | 13-14 | Phase 3 | Coverage tooling, crypto tests start |
| Sprint 8 | 15-16 | Phase 3 | Crypto complete, Byzantine tests |
| Sprint 9 | 17-18 | Phase 3 | Byzantine tests, chaos engineering |
| Sprint 10 | 19-20 | Phase 4 | God class refactor, race conditions |
| Sprint 11 | 21-22 | Phase 4 | DRY, Java 24 patterns |
| Sprint 12 | 23-24 | Phase 4 | Performance, gate |
| Sprint 13 | 25-26 | Phase 5 | Documentation, deployment start |
| Sprint 14 | 27-28 | Phase 5 | Monitoring, deployment complete |
| Sprint 15 | 29-30 | Phase 5 | Runbooks, final validation |

---

## Release Checkpoints

### Checkpoint 1: Security Hardened (Week 4)
- All 5 critical security issues resolved
- External security review passed
- Test coverage > 95% for security code

### Checkpoint 2: Architecturally Sound (Week 12)
- Delos abstraction complete
- Byzantine handling implemented
- API versioned

### Checkpoint 3: Testable (Week 18)
- 80%+ test coverage
- Byzantine failure tests passing
- Chaos engineering operational

### Checkpoint 4: Quality Assured (Week 24)
- No god classes
- No code duplication
- Performance baselines met

### Checkpoint 5: Production Ready (Week 30)
- All documentation complete
- Monitoring operational
- Runbooks validated
- Production readiness review passed

---

## Stakeholder Communications

### Weekly Status Report
**Audience**: Project Sponsors, Tech Lead
**Cadence**: Weekly (Friday)
**Content**:
- Sprint progress (burndown)
- Blockers and risks
- Key decisions made
- Next week focus

### Phase Gate Reviews
**Audience**: All stakeholders
**Cadence**: End of each phase
**Content**:
- Phase deliverables review
- Success criteria verification
- Risk assessment
- Go/no-go decision

### Technical Deep Dives
**Audience**: Engineering team
**Cadence**: As needed
**Content**:
- Architecture decisions
- Implementation approaches
- Technical challenges

---

## Risk Mitigation Strategies

### Risk 1: Delos Coupling Deeper Than Visible
**Probability**: High | **Impact**: High
**Mitigation**:
- Start abstraction layer early in Phase 2
- Use feature flags for incremental migration
- Plan for 50% schedule buffer

### Risk 2: Security Review Findings
**Probability**: Medium | **Impact**: Critical
**Mitigation**:
- Engage external reviewer before Phase 1 complete
- Plan for remediation sprint
- No gate advancement without security sign-off

### Risk 3: Test Instability
**Probability**: Medium | **Impact**: Medium
**Mitigation**:
- Quarantine flaky tests immediately
- Dedicated test stabilization time in sprints
- Require test stability before merge

### Risk 4: Performance Regression
**Probability**: Low | **Impact**: High
**Mitigation**:
- Establish baselines in Phase 4
- CI/CD performance gates
- Automated regression detection

### Risk 5: Team Knowledge Gaps
**Probability**: Medium | **Impact**: Medium
**Mitigation**:
- Documentation-first approach
- Pair programming for complex changes
- Knowledge sharing sessions

---

## Success Metrics Summary

### Security Metrics
- [ ] 0 critical security issues remaining
- [ ] 100% certificate validation in network paths
- [ ] No hardcoded secrets in codebase
- [ ] External security audit passing

### Quality Metrics
- [ ] 80%+ overall test coverage
- [ ] 95%+ security code coverage
- [ ] 0 high/critical static analysis findings
- [ ] No god classes (< 200 lines each)

### Performance Metrics
- [ ] Consensus latency < 500ms (P99)
- [ ] Token validation < 50ms
- [ ] Certificate validation < 5ms
- [ ] No memory leaks under load

### Operational Metrics
- [ ] Monitoring dashboards operational
- [ ] Deployment procedures documented
- [ ] Runbooks validated
- [ ] MTTR < 5 minutes for node failure

---

## Appendix A: Issue Reference Table

| Issue # | Title | Phase | Priority | Effort |
|---------|-------|-------|----------|--------|
| CRITICAL #1 | Hardcoded Test Secrets | Phase 1 | P0 | 1-2 days |
| CRITICAL #2 | Certificate Validator NO Validation | Phase 1 | P0 | 3-5 days |
| CRITICAL #3 | Development Secret Exposure | Phase 1 | P0 | 1-2 days |
| CRITICAL #4 | Missing Null Safety | Phase 1 | P0 | 1-2 days |
| CRITICAL #5 | Insufficient Key Derivation Validation | Phase 1 | P0 | 2-3 days |
| ISSUE #6 | Token Cache Missing TTL | Phase 4 | P1 | 1 day |
| ISSUE #7 | ReentrantLock Misuse | Phase 4 | P2 | 1 day |
| ISSUE #8 | Race Condition in Shutdown | Phase 4 | P1 | 3-5 days |
| ISSUE #9 | Java 24 Compliance | Phase 4 | P2 | 2-3 days |
| ISSUE #10 | Code Duplication | Phase 4 | P2 | 3-5 days |
| ISSUE #11 | Swallowed Exceptions | Phase 4 | P1 | 2-3 days |
| ISSUE #12 | Resource Leaks | Phase 4 | P1 | 2-3 days |
| ISSUE #13 | Magic Numbers | Phase 4 | P2 | 1-2 days |
| ISSUE #14 | God Class | Phase 4 | P2 | 3-5 days |
| ISSUE #15 | Inconsistent Logging | Phase 4 | P2 | 1-2 days |
| ISSUE #16 | Unit Test Coverage | Phase 3 | P1 | 2-3 weeks |
| ISSUE #17 | Integration Tests | Phase 3 | P1 | 2-3 weeks |
| ISSUE #18 | Mock Sanctum | Phase 3 | P2 | 2-3 days |
| ISSUE #19 | In-Memory H2 | Phase 5 | P1 | 2-3 days |
| ISSUE #20 | Virtual Thread Adoption | Phase 4 | P3 | 1-2 days |
| ISSUE #21 | Cache Sizing | Phase 5 | P3 | 1 day |
| ISSUE #23 | gRPC Missing Deadlines | Phase 2 | P1 | 2-3 days |
| ISSUE #24 | Interceptor Chain Docs | Phase 5 | P3 | 1 day |
| Arch #1 | Direct Delos Coupling | Phase 2 | P1 | 3-4 weeks |
| Arch #2 | Sanctum Module Boundaries | Phase 2 | P1 | 1-2 weeks |
| Arch #3 | Missing Byzantine Handling | Phase 2 | P1 | 3-4 weeks |
| Arch #4 | API Versioning | Phase 2 | P2 | 1 week |
| Arch #5 | Configuration Management | Phase 2 | P2 | 1-2 weeks |
| Arch #6 | Inconsistent Naming | Phase 2 | P3 | 2-3 days |

---

## Appendix B: Key File Reference

| File | Issues | Phase |
|------|--------|-------|
| `nut/src/main/java/com/hellblazer/nut/SkyApplication.java` | 2, 6, 7, 8, 11, 12, 14 | 1, 2, 4 |
| `nut/src/main/java/com/hellblazer/nut/Sphinx.java` | 2, 3, 10 | 1, 4 |
| `nut/src/main/java/com/hellblazer/nut/Launcher.java` | 1, 3, 13 | 1, 4 |
| `sanctum-sanctorum/.../SanctumSanctorum.java` | 4, 5, 10 | 1, 4 |
| `grpc/src/main/proto/*.proto` | Arch #4 | 2 |
| `nut/pom.xml` | 1 | 1 |

---

## Document Control

**Version**: 2.0
**Created**: 2026-01-09
**Author**: Strategic Planning Agent
**Status**: Ready for plan-auditor review
**Review Cycle**: Weekly during execution

---

## Next Steps

1. **MANDATORY**: Submit this plan to plan-auditor agent for review
2. Create beads for Phase 1 tasks using `bd create`
3. Schedule Phase 1 kickoff meeting
4. Assign Phase 1 team members
5. Begin Phase 1 Sprint 1

---

*This plan was generated based on the comprehensive code review (COMPREHENSIVE_CODE_REVIEW.md) and aligned with the existing .pm/ infrastructure.*
