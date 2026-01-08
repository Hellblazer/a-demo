# Sky Application

![Build Status](https://github.com/Hellblazer/a-demo/actions/workflows/maven.yml/badge.svg)

**Sky** is a fantasy proof-of-concept (POC) for a self-bootstrapping, Byzantine fault-tolerant identity and secrets management cluster built on the [Delos distributed systems platform](https://github.com/Hellblazer/Delos).

> **Note**: This is a demonstration POC designed for understanding Delos capabilities through short-lived demo sessions. This is not production-ready code. See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) for POC constraints.

## What is Sky?

Sky demonstrates a minimal viable Byzantine fault-tolerant (BFT) system that:

- **Self-bootstraps** using Shamir secret sharing for cluster initialization
- **Manages identities** via KERL (Key Event Receipt Log) protocol
- **Handles secrets** with cryptographic enclave operations
- **Provides MTLS** mutual authentication for inter-node communication
- **Achieves consensus** using the CHOAM protocol from Delos

The system showcases how Delos components (Fireflies membership, CHOAM consensus, Stereotomy identity, Gorgoneion crypto) work together in a distributed application.

## Quick Start

### Prerequisites

- Java 22+
- Maven 3.8.1+
- Docker (for end-to-end testing)

### Build

```bash
# Full build
./mvnw clean install

# Build with Docker-based end-to-end tests
./mvnw -P e2e clean install

# Build without tests (faster)
./mvnw clean install -DskipTests
```

### Run Local Demo

See the [local-demo](local-demo) directory for Docker Compose configurations that run a multi-node Sky cluster locally:

1. **Start bootstrap node**: `cd local-demo/bootstrap && docker-compose up`
2. **Start kernel nodes**: Wait for bootstrap, then `cd local-demo/kernel && docker-compose up`
3. **Add more nodes**: After kernel generates Genesis block, `cd local-demo/nodes && docker-compose up --scale node=3`

For detailed walkthrough, see [DEMO_GUIDE.md](DEMO_GUIDE.md).

## Architecture

Sky uses a multi-module Maven structure with clear separation of concerns:

### Core Modules

- **[nut](nut/)** - Core runtime container managing bootstrapping, provisioning, and node orchestration
  - Cluster initialization via Shamir secret sharing
  - Configuration and provisioning services
  - API servers and client communications
  - Entry point: `com.hellblazer.nut.Launcher`

- **[sky](sky/)** - Main application module that shades (bundles) nut runtime into a single executable JAR

- **[sanctum](sanctum/)** - Identity and cryptographic operations wrapper for enclave signing/verification

- **[sanctum-sanctorum](sanctum-sanctorum/)** - Server implementation for enclave operations, managing tokens and KERL protocols

- **[grpc](grpc/)** - Protocol buffer definitions and gRPC service interfaces for inter-node communication

- **[constants](constants/)** - Shared constants across modules

### Supporting Modules

- **[sky-image](sky-image/)** - Docker container image builder with environment variable configuration

- **[local-demo](local-demo/)** - End-to-end test suite using Docker Compose for local cluster testing

For detailed architecture documentation, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Key Technologies

- **[Delos Platform](https://github.com/Hellblazer/Delos)** - Distributed systems framework
  - **Fireflies** - Byzantine fault-tolerant membership protocol
  - **CHOAM** - Byzantine fault-tolerant consensus
  - **Stereotomy** - Decentralized identifier (DID) and KERL protocol
  - **Gorgoneion** - Cryptographic infrastructure
- **gRPC** - Inter-node communication
- **Protocol Buffers** - Service and message definitions
- **H2 Database** - In-memory and persistent storage
- **Shamir Secret Sharing** - Cryptographic bootstrap mechanism
- **TestContainers** - Docker-based integration testing

## Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture and module details
- **[DEMO_GUIDE.md](DEMO_GUIDE.md)** - Step-by-step demo walkthrough
- **[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md)** - POC constraints and boundaries
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Common issues and solutions
- **[CLAUDE.md](CLAUDE.md)** - Development guidance for Claude Code

## Development

### Build Commands

```bash
# Compile only
./mvnw clean compile

# Run tests for specific module
./mvnw test -pl nut

# Run single test
./mvnw test -pl nut -Dtest=LauncherTest

# Run end-to-end smoke tests
./mvnw -P e2e test -pl local-demo
```

### Maven Profiles

- **e2e** - Runs end-to-end smoke tests using TestContainers and Docker Compose
- **native** - Includes native image compilation (requires GraalVM)
- **native-agent** - Generates native-image configuration

### Memory Requirements

Tests require significant heap allocation for distributed system testing:
- Default: `-Xmx10G -Xms4G`
- See [test memory documentation](docs/test-memory.md) for details

## Testing

- **Unit tests**: `src/test/java` in each module
- **Integration tests**: End-to-end tests in `local-demo` module
- **Smoke test**: `local-demo/src/test/java/com/hellblazer/sky/demo/SmokeTest.java`

Run all tests: `./mvnw clean test`

Run e2e tests: `./mvnw -P e2e clean test`

## POC Limitations

This is a **fantasy POC** for demonstration purposes. Known limitations include:

- No session key rotation (short-lived demo sessions)
- Token logging at INFO level (debugging aid)
- No certificate revocation mechanism
- Hardcoded bootstrap host address (172.17.0.2)
- Hardcoded shared secret (for demo convenience)

See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) for complete list and production considerations.

## Contributing

This is a demonstration POC. For production use cases or contributions to the underlying Delos platform, see the [Delos repository](https://github.com/Hellblazer/Delos).

## License

See [LICENSE](LICENSE) file for details.

## Related Projects

- **[Delos](https://github.com/Hellblazer/Delos)** - Distributed systems platform
- **[Fireflies](https://github.com/Hellblazer/Delos/tree/main/fireflies)** - BFT membership
- **[CHOAM](https://github.com/Hellblazer/Delos/tree/main/choam)** - BFT consensus
- **[Stereotomy](https://github.com/Hellblazer/Delos/tree/main/stereotomy)** - DID and KERL
- **[Gorgoneion](https://github.com/Hellblazer/Delos/tree/main/gorgoneion)** - Cryptographic infrastructure

## Support

For questions or issues:
- Review [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- Check existing [GitHub issues](https://github.com/Hellblazer/a-demo/issues)
- Create a new issue with the "POC constraint" label if reporting expected POC limitations

---

**Sky Application** - A fantasy POC demonstrating Byzantine fault-tolerant identity and secrets management on Delos.
