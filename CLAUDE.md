# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Sky Application** is a distributed systems project built on the [Delos platform](https://github.com/Hellblazer/Delos). It's a fantasy POC (proof of concept) for a minimal viable system implementing a self-bootstrapping, Byzantine fault tolerant identity and secrets management cluster.

## Architecture

The Sky application uses a multi-module Maven structure with clear separation of concerns:

### Core Modules

- **nut**: Core runtime container for Sky nodes, responsible for bootstrapping, provisioning, and node orchestration
  - Manages cluster initialization via Shamir secret sharing
  - Handles configuration and provisioning services
  - Implements API servers and client communications
  - Entry point: `com.hellblazer.nut.Launcher`

- **sky**: Main application module that shades (bundles) the nut runtime with all dependencies into a single executable JAR

- **sanctum**: Identity and cryptographic operations wrapper providing enclave signing and verification capabilities

- **sanctum-sanctorum**: Server implementation for enclave operations, managing tokens and KERL (Key Event Receipt Log) protocols

- **grpc**: Protocol buffer definitions and gRPC service interfaces for inter-node communication

- **constants**: Shared constants across modules

- **sky-image**: Docker container image builder for the Sky application with environment variable configuration

- **local-demo**: End-to-end test suite using Docker Compose for local cluster testing and smoke testing

## Build and Development

### Build Commands

```bash
# Full clean build
./mvnw clean install

# Build with end-to-end tests (Docker required)
./mvnw -P e2e clean install

# Build without tests
./mvnw clean install -DskipTests

# Compile only (no tests/packaging)
./mvnw clean compile

# Run specific module tests
./mvnw test -pl <module-name>

# Run a single test class
./mvnw test -pl <module-name> -Dtest=<TestClassName>

# Run a single test method
./mvnw test -pl <module-name> -Dtest=<TestClassName>#<methodName>
```

### Maven Profiles

- **e2e**: Runs end-to-end smoke tests using TestContainers and Docker Compose (activates SmokeTest.java in local-demo)
- **native**: Includes native image compilation modules (sky-native, native-test) - requires GraalVM setup
- **native-agent**: Generates native-image configuration by running tests with the native-image-agent

### Key Properties

- Java version: 22 (with `--enable-preview` for preview features)
- Maven: 3.8.1+
- Surefire memory settings: `-Xmx10G -Xms4G` (10GB heap for test suite)

## Testing

### Test Structure

Tests follow standard Maven conventions:
- Unit tests: `src/test/java` in each module
- Integration tests: End-to-end tests in `local-demo` (SmokeTest.java)
- Docker-based testing: Uses TestContainers for orchestrating multi-node clusters locally

### Local E2E Testing

The `local-demo` module provides Docker Compose configurations:
- **bootstrap/compose.yaml**: Starts the bootstrap node (well-known host: 172.17.0.2)
- **kernel/compose.yaml**: Starts three additional kernel nodes to achieve minimal quorum
- **nodes/compose.yaml**: Adds additional nodes to scale the cluster

**Important**: Allow the kernel to generate the Genesis block before starting non-kernel nodes.

### Running Tests

```bash
# Run all tests (excluding e2e)
./mvnw clean test

# Run e2e smoke tests only
./mvnw -P e2e clean test

# Run tests for a single module
./mvnw test -pl nut

# Run with native-image-agent for GraalVM configuration
./mvnw -P native-agent test -pl nut
```

## Dependencies and Key Technologies

- **Delos**: Distributed systems platform (dependency management via BOM)
- **gRPC 1.64.0**: Inter-node communication protocol
- **Protocol Buffers 4.27.0**: Service and message definitions
- **H2 Database 2.2.224**: In-memory and persistent database
- **JOOQ 3.18.15**: Type-safe database access
- **Jackson 2.15.2**: JSON/YAML serialization
- **Liquibase 4.8.0**: Database schema management
- **Shamir Secret Sharing (0.7.0)**: Cryptographic bootstrap mechanism
- **AWS SDK 2.26.25**: Cloud integration capabilities
- **Fernet (1.4.2)**: Symmetric encryption for provisioning
- **TestContainers 1.20.1**: Docker-based integration testing

## Key Code Patterns

### Generated Code

Multiple plugins generate code during Maven's `generate-sources` phase:
- **Protocol Buffers**: gRPC services and message classes in `target/generated-sources/protobuf/`
- **JOOQ**: Database access classes in `target/generated-sources/jooq/`

These generated sources are automatically added to compilation by the `build-helper-maven-plugin`.

### Cryptography and Secrets

- Shamir secret sharing used for cluster bootstrap
- Fernet encryption for provisioning credentials
- MTLS (mutual TLS) for inter-node communication with certificate validation

### Configuration

- Jackson YAML support for configuration files
- Environment variable injection in Docker images
- Guava utilities for immutable collections and functional patterns

## IDE Configuration

The repository includes `.idea` configuration for IntelliJ IDEA and `.run` configurations for running and debugging the application.

## Build Repositories

The build pulls dependencies from:
1. Maven Central (repo.maven.org and repo1.maven.org)
2. Custom Hellblazer repository: `https://raw.githubusercontent.com/Hellblazer/repo-hell/main/mvn-artifact` (snapshot artifacts)

## Common Development Tasks

### Debugging Tests

Tests run with large heap allocation. If you encounter `OutOfMemoryError`, the test may need more memory:
```bash
./mvnw test -DargLine="-Xmx12G -Xms6G"
```

### Building Native Images

Requires GraalVM 23.1.2 or later:
```bash
# Generate native-image configuration
./mvnw -P native-agent test -pl nut

# Build native executable (includes sky-native module)
./mvnw -P native clean install

# Test native executable
./mvnw -P native test -pl native-test
```

### Docker Image Build

The sky-image module builds a container image. Configuration via environment variables (see `SmokeTest.java` for examples).

### Protocol Buffer Changes

After modifying `.proto` files in `src/main/proto/` or `src/test/proto/`:
```bash
./mvnw clean compile  # Regenerates gRPC and protobuf classes
```

## Troubleshooting

### "dependencyConvergence" Errors

The enforcer plugin requires all transitive dependencies to converge to a single version. If you add a dependency with version conflicts:
```bash
./mvnw dependency:tree -DoutputFile=tree.txt  # See full dependency tree
```

### Test Memory Issues

Default memory (-Xmx10G) is required for the test suite. Reduce with:
```bash
./mvnw test -DskipTests=false -Dargument=-Xmx4G
```

### GraalVM Native Image Failures

Some reflection or dynamic loading may not be captured automatically. Use the native-agent profile and inspect generated configuration in `src/main/resources/META-INF/native-image/`.

## Release Process

**IMPORTANT**: Use the GitHub Actions release workflow, NOT Maven release plugin (`mvnw release:prepare release:perform`).

To cut a new release:
1. Go to GitHub Actions and trigger the release workflow
2. The workflow will handle version bumping, tagging, and publishing
3. Do NOT manually run Maven release commands or create tags

**Why**: The automated release action ensures consistent versioning, proper artifact publishing to GitHub Packages, and CI/CD integration.

## CI/CD

GitHub Actions workflow (`.github/workflows/maven.yml`) runs:
1. Checkout code
2. Setup GraalVM 22
3. Run `./mvnw -batch-mode clean install`

The build runs on every push. E2E tests require Docker, so the GitHub runner must have Docker available.

The project uses a GitHub Actions release workflow (not Maven release plugin) to cut releases. See Release Process section above.
