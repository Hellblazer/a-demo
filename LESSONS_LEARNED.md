# Sky POC - Lessons Learned

**Document Purpose**: Document POC design decisions, rationale, and lessons learned for future reference.

**Document Status**: v1.0 - POC Complete (January 2026)

**Audience**: Future developers, architects evaluating similar systems, and teams learning from this POC

---

## POC Objectives

### What We Set Out to Prove

1. **Self-bootstrapping cluster** using Shamir secret sharing works
2. **BFT consensus** is viable for identity/secrets management
3. **KERL-based identity** provides decentralized identifier management
4. **ReBAC permissions** can handle organizational access control
5. **Delos platform** is suitable for building distributed systems

### What We Actually Demonstrated

✅ **All objectives met** within 30-minute demo sessions
✅ **Cluster formation** achieves Genesis block reliably (4 nodes, f=1)
✅ **Permission system** handles complex organizational hierarchies (17 relationships)
✅ **Transitive permissions** correctly expand to 14 subjects/groups
✅ **Revocation** properly removes access when relationships break

**Unexpected Wins**:
- TestContainers provided excellent local testing experience
- ASCII art logging made demos engaging and easy to follow
- Documentation-first approach clarified thinking early

**Unexpected Challenges**:
- 10GB heap requirement for distributed tests (higher than expected)
- Token logging verbosity cluttered demo output
- Port conflicts required dynamic allocation in tests

---

## Design Decision 1: Hardcoded Shamir Secret

### Decision

Shamir shared secret hardcoded in code for convenience (Issue #36)

### Rationale

**POC Constraint**: Acceptable for demo purposes because:

1. **Demo Duration**: Sessions last 30-60 minutes, short enough to avoid security concerns
2. **Controlled Environment**: All demos run locally or in isolated Docker networks
3. **Simplicity**: Interactive Shamir secret sharing would complicate demo setup
4. **No Real Secrets**: POC doesn't handle actual production secrets

**Production Requirement**: External secret management (HashiCorp Vault, AWS Secrets Manager)

### What We Learned

- **Shamir secret sharing works** as intended for cluster initialization
- **Interactive secret collection** would add complexity without POC value
- **Production systems** need proper secret lifecycle management

### What We Would Change

If promoting to production:

1. Implement interactive Shamir secret collection from operators
2. Integrate with HashiCorp Vault for secret storage
3. Add secret rotation policies
4. Implement audit logging for secret access

**Estimated Effort**: 2-3 weeks

---

## Design Decision 2: Self-Signed Certificates

### Decision

Generate self-signed, ephemeral certificates at startup (Issue #41)

### Rationale

**POC Constraint**: Acceptable for demo purposes because:

1. **Demo Scope**: No external clients, all nodes trust each other
2. **Ephemeral Clusters**: Nodes destroyed after demo, no certificate persistence needed
3. **Simplicity**: External PKI would require infrastructure setup
4. **MTLS Validation**: POC demonstrates MTLS concept, not production PKI

**Production Requirement**: External PKI (cert-manager, Vault PKI, AWS ACM)

### What We Learned

- **MTLS authentication** works correctly with self-signed certs
- **Certificate rotation** is critical for long-lived systems
- **Trust bootstrapping** is simpler with ephemeral certificates

### What We Would Change

If promoting to production:

1. Integrate cert-manager for automatic certificate issuance
2. Implement certificate revocation (CRL or OCSP)
3. Add certificate expiration monitoring
4. Support external CA chains

**Estimated Effort**: 3-4 weeks

---

## Design Decision 3: Token Logging at DEBUG Level

### Decision

Log Fernet tokens at DEBUG level (originally INFO, changed during POC improvement)

### Rationale

**POC Constraint**: Initially logged at INFO for debugging, reduced to DEBUG for cleaner demos

**Original Decision** (INFO level):
- Helped trace token flow during early development
- Made debugging easier when nodes failed to authenticate
- Acceptable for short demo sessions with no real secrets

**Revised Decision** (DEBUG level):
- Cleaner demo output without sensitive data
- Still available for debugging when needed
- Aligns with production-ready logging practices

### What We Learned

- **Logging verbosity** significantly impacts demo experience
- **DEBUG level** is correct place for sensitive data
- **Structured logging** would help even more in production

### What We Would Change

If promoting to production:

1. Remove token logging entirely (or hash tokens before logging)
2. Implement structured logging with redaction
3. Add audit logging separate from debug logging
4. Use log aggregation (ELK, Loki) for production

**Estimated Effort**: 1-2 weeks

---

## Design Decision 4: H2 In-Memory Database

### Decision

Use H2 in-memory database for consensus log storage

### Rationale

**POC Constraint**: Acceptable for demo purposes because:

1. **Fast Startup**: In-memory database starts instantly
2. **No State Needed**: Demo doesn't require persistence across runs
3. **Simplicity**: No external database dependency
4. **Sufficient for POC**: 17 relationships + 14 assertions fit in memory

**Production Requirement**: PostgreSQL or CockroachDB for durability

### What We Learned

- **H2 works well** for POC scope and testing
- **Persistence is critical** for production consensus logs
- **Database choice impacts** recovery and disaster response

### What We Would Change

If promoting to production:

1. Migrate to PostgreSQL (single-region) or CockroachDB (multi-region)
2. Implement automated backup and point-in-time recovery
3. Add database connection pooling
4. Implement database migration strategy (Liquibase/Flyway)

**Estimated Effort**: 2-3 weeks

---

## Design Decision 5: Fixed 4-Node Kernel

### Decision

Hardcode minimal BFT quorum (4 nodes, f=1 Byzantine tolerance)

### Rationale

**POC Constraint**: Acceptable for demo purposes because:

1. **Minimal Quorum**: 4 nodes is minimum for BFT (3f+1 where f=1)
2. **Demo Resources**: 4 Docker containers manageable on laptop
3. **Quick Startup**: Smaller cluster forms faster
4. **Sufficient Validation**: Demonstrates consensus without scale complexity

**Production Requirement**: 7+ nodes (f=2 tolerance) with dynamic membership

### What We Learned

- **Genesis block formation** works reliably with 4 nodes
- **BFT overhead** is noticeable even at small scale
- **Dynamic membership** would be valuable for operational flexibility

### What We Would Change

If promoting to production:

1. Increase to 7 nodes minimum (f=2 tolerance)
2. Implement dynamic membership (add/remove nodes at runtime)
3. Add rebalancing when nodes join/leave
4. Support multi-region deployment (3+ regions for availability)

**Estimated Effort**: 3-4 weeks

---

## Design Decision 6: Bootstrap Node Hardcoded to 172.17.0.2

### Decision

Hardcode bootstrap node IP to `172.17.0.2` in Docker bridge network

### Rationale

**POC Constraint**: Acceptable for demo purposes because:

1. **Docker Bridge Network**: Predictable IP assignment in local Docker
2. **Single-Host Deployment**: All nodes on same machine
3. **Simplicity**: Avoids service discovery complexity
4. **Sufficient for POC**: Demonstrates cluster formation

**Production Requirement**: Service discovery (Kubernetes DNS, Consul)

### What We Learned

- **Well-known address** simplifies bootstrap process
- **Service discovery** is critical for production deployments
- **Kubernetes DNS** would eliminate hardcoded IPs

### What We Would Change

If promoting to production:

1. Use Kubernetes StatefulSet with stable DNS names
2. Implement service discovery (Kubernetes DNS or Consul)
3. Support multi-region bootstrap (multiple well-known addresses)
4. Add bootstrap node election if primary fails

**Estimated Effort**: 1-2 weeks (Kubernetes simplifies this)

---

## Design Decision 7: 10GB Test Heap Allocation

### Decision

Configure maven-surefire-plugin with `-Xmx10G -Xms4G`

### Rationale

**POC Constraint**: Necessary for distributed testing because:

1. **TestContainers Overhead**: 500MB-1GB for orchestration
2. **Four Docker Containers**: 4 × 1.5GB per Sky node = 6GB
3. **H2 Databases**: 4 × 500MB per node = 2GB
4. **Consensus State**: ~1GB for protocol buffers and BFT state
5. **JVM Overhead**: ~500MB for test framework

**Total**: 9-10GB peak usage

**Validated During POC**: Unit tests run with 2GB, integration tests need full 10GB

### What We Learned

- **Distributed testing** requires significant resources
- **Module-by-module testing** is more efficient (unit tests separately)
- **Docker memory allocation** must be 12GB+ for tests to pass

### What We Would Change

If promoting to production:

1. Keep 10GB for integration tests (necessary for distributed testing)
2. Run unit tests separately with lower heap (2GB)
3. Add memory profiling to CI/CD to detect regressions
4. Consider test parallelization to reduce total runtime

**Estimated Effort**: Already optimized for POC scope

---

## Design Decision 8: No Session Key Rotation

### Decision

Session keys never rotate (Issue #36)

### Rationale

**POC Constraint**: Acceptable for demo purposes because:

1. **Short Sessions**: Demo sessions last 30-60 minutes
2. **Controlled Environment**: No adversarial actors in local Docker
3. **Complexity vs Value**: Key rotation adds complexity without POC benefit
4. **Demonstrates Concept**: MTLS session establishment works

**Production Requirement**: Periodic key rotation (hourly/daily configurable)

### What We Learned

- **Session establishment** works reliably without rotation
- **Key rotation** is operational overhead not needed for POC
- **Production systems** must rotate keys for defense-in-depth

### What We Would Change

If promoting to production:

1. Implement configurable session key rotation interval
2. Add graceful session migration during rotation
3. Monitor rotation failures and alert
4. Document rotation policy in security guidelines

**Estimated Effort**: 1-2 weeks

---

## Design Decision 9: TestContainers for Integration Testing

### Decision

Use TestContainers to orchestrate multi-node clusters in tests

### Rationale

**Design Choice**: Excellent decision because:

1. **Realistic Testing**: Tests run against actual Docker images
2. **Isolation**: Each test gets fresh containers
3. **CI/CD Ready**: Works in GitHub Actions with Docker support
4. **Cleanup Automatic**: Containers destroyed after test

**Not a POC Constraint**: This approach works equally well for production

### What We Learned

- **TestContainers is production-quality** for integration testing
- **Docker-based tests** provide high confidence in deployments
- **Memory requirements** are significant but manageable

### What We Would Keep

No changes needed - TestContainers is the right choice for this use case

---

## Design Decision 10: Documentation-First Approach

### Decision

Create comprehensive documentation (README, ARCHITECTURE, DEMO_GUIDE, etc.) early

### Rationale

**Design Choice**: Excellent decision because:

1. **Clarified Thinking**: Writing forced clear understanding of system
2. **Demo Success**: Documentation enabled smooth demos
3. **Onboarding**: New developers can understand system quickly
4. **Handoff Ready**: POC is promotion-ready documentation-wise

**Not a POC Constraint**: Good practice for any project

### What We Learned

- **Documentation early** saves time later
- **Mermaid diagrams** clarify architecture effectively
- **Examples and troubleshooting** are high-value documentation

### What We Would Keep

Continue documentation-first approach:
- README with quick start
- ARCHITECTURE with diagrams
- DEMO_GUIDE with step-by-step walkthrough
- TROUBLESHOOTING for common issues
- KNOWN_LIMITATIONS for transparency

---

## Architecture Lessons

### What Worked Well

1. **Delos Platform Integration**
   - Fireflies (membership), CHOAM (consensus), Stereotomy (identity) worked seamlessly
   - BFT protocols provided by Delos eliminated complex implementation
   - Modular design made it easy to understand each component

2. **gRPC for Communication**
   - Type-safe communication via protocol buffers
   - Excellent error handling and retry mechanisms
   - MTLS integration straightforward

3. **Maven Multi-Module Structure**
   - Clear separation of concerns (nut, sanctum, sky-image, local-demo)
   - Easy to understand dependencies
   - Module-by-module testing works well

4. **Docker Compose for Local Demos**
   - Simple cluster orchestration
   - Predictable networking
   - Easy to explain to non-technical stakeholders

### What We Would Change

1. **Configuration Management**
   - **Current**: Environment variables + YAML files
   - **Better**: Centralized configuration service (Consul, etcd)
   - **Why**: Easier to manage at scale

2. **Observability**
   - **Current**: Basic logging to stdout
   - **Better**: Prometheus metrics + structured logging
   - **Why**: Production systems need visibility

3. **Error Handling**
   - **Current**: Basic retry logic
   - **Better**: Circuit breakers, bulkheads, timeouts
   - **Why**: Resilience patterns prevent cascading failures

4. **API Design**
   - **Current**: Oracle API focused on demo scenarios
   - **Better**: Versioned APIs with backward compatibility
   - **Why**: Production systems evolve over time

---

## Testing Lessons

### What Worked Well

1. **SmokeTest End-to-End Validation**
   - Single test validates entire system
   - High confidence in cluster formation
   - Catches integration issues early

2. **Descriptive Logging in Tests**
   - 13-step smoke test tells a story
   - Easy to debug when tests fail
   - Makes test output useful for documentation

3. **Module READMEs with Test Instructions**
   - Each module documents how to test
   - Clear troubleshooting guidance
   - Examples help developers get started

### What We Would Add

If promoting to production:

1. **Unit Test Coverage**
   - **Current**: Minimal unit tests
   - **Target**: 80%+ code coverage
   - **Why**: Catch bugs early, faster feedback

2. **Performance Tests**
   - **Current**: No load testing
   - **Need**: TPS benchmarks, latency percentiles
   - **Why**: Understand capacity limits

3. **Chaos Engineering**
   - **Current**: No failure injection
   - **Need**: Network partitions, node crashes, slow nodes
   - **Why**: Validate BFT tolerance

4. **Contract Tests**
   - **Current**: Integration tests only
   - **Need**: API contract tests (Pact, Spring Cloud Contract)
   - **Why**: Prevent breaking changes

---

## Operational Lessons

### What Worked Well

1. **Demo Script (demo.sh)**
   - Automated prerequisite checks
   - Clear progress indicators
   - Troubleshooting hints on failure

2. **TROUBLESHOOTING.md**
   - Top 5 issues with solutions
   - Diagnostic checklists
   - Quick reference commands

3. **Version Banner on Startup**
   - Immediately visible version
   - Configuration summary
   - Professional appearance

### What We Would Add

If promoting to production:

1. **Monitoring Dashboards**
   - **Current**: None
   - **Need**: Grafana dashboards for metrics
   - **Why**: Visibility into cluster health

2. **Runbooks**
   - **Current**: Basic troubleshooting
   - **Need**: Detailed incident response procedures
   - **Why**: On-call engineers need guidance

3. **Backup/Recovery Procedures**
   - **Current**: Not implemented
   - **Need**: Automated backup, point-in-time recovery
   - **Why**: Data durability is critical

4. **Upgrade Procedures**
   - **Current**: None
   - **Need**: Rolling upgrade scripts, rollback procedures
   - **Why**: Zero-downtime upgrades

---

## Key Takeaways

### For POC Success

1. **Documentation First**: Write architecture docs early to clarify thinking
2. **Clear Scope**: Explicitly document POC constraints vs production requirements
3. **Demo-Driven**: Optimize for 30-minute demo sessions, not production scale
4. **Realistic Testing**: Use TestContainers for high-confidence integration tests
5. **Logging Matters**: Good logging makes demos engaging and debugging easy

### For Production Adoption

1. **Security Is Hard**: Don't underestimate security hardening effort (12-16 weeks)
2. **Operational Maturity**: Monitoring, logging, backup/recovery are critical (18-26 weeks)
3. **Team Skills**: Distributed systems expertise is essential
4. **Cost Awareness**: 7-node BFT cluster is expensive (~$16K/year minimum)
5. **Alternative Evaluation**: Consider if BFT is necessary vs simpler crash-tolerant systems

### For Future POCs

1. **Start Simple**: Begin with minimal quorum (4 nodes), add complexity later
2. **Test Early**: TestContainers from day 1 prevents integration surprises
3. **Document Constraints**: KNOWN_LIMITATIONS.md prevents misunderstandings
4. **ASCII Art**: Makes demos memorable and engaging
5. **Handoff Planning**: Think about production path from the start

---

## Recommended Reading

### Distributed Systems

- **"Designing Data-Intensive Applications"** by Martin Kleppmann
  - Consensus, replication, distributed transactions

- **"Distributed Systems"** by Maarten van Steen & Andrew S. Tanenbaum
  - Fundamental distributed system concepts

### Byzantine Fault Tolerance

- **"Practical Byzantine Fault Tolerance"** by Castro & Liskov (1999)
  - Original PBFT paper, foundational for BFT understanding

- **Delos Platform Documentation**
  - https://github.com/Hellblazer/Delos
  - Fireflies, CHOAM, Stereotomy protocol details

### Kubernetes & Operations

- **"Kubernetes Patterns"** by Bilgin Ibryam & Roland Huß
  - StatefulSets, Operators, deployment patterns

- **"Site Reliability Engineering"** by Google
  - Operational best practices, monitoring, incident response

---

## Acknowledgments

**Delos Platform**: Hal Hildebrand's Delos platform provided the foundation (Fireflies, CHOAM, Stereotomy, Gorgoneion)

**TestContainers**: Excellent framework for realistic integration testing

**Sky POC Contributors**: Everyone who provided feedback during demos and reviewed this documentation

---

## Conclusion

This POC successfully demonstrated that BFT identity and secrets management is feasible using the Delos platform. The design decisions made were appropriate for a 30-minute demo POC, with clear understanding of what would need to change for production.

**If promoting to production**: Expect 1-1.5 years of engineering effort across security, operations, and scalability.

**If keeping as POC**: This documentation ensures the work is not lost and lessons can inform future projects.

Either way, this POC achieved its objectives and provides a solid foundation for decision-making.

---

**Document Version**: 1.0
**Last Updated**: January 2026
**Author**: Hal Hildebrand
**Repository**: https://github.com/Hellblazer/a-demo
