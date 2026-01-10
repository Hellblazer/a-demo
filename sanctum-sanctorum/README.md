# Sanctum Sanctorum - Enclave Server Implementation

**Module**: `sanctum-sanctorum`
**Type**: Server Implementation
**Purpose**: Token generation and KERL protocol management for enclave operations

## Overview

**Sanctum Sanctorum** (Latin: "Holy of Holies") is the server implementation for enclave cryptographic operations. It manages token generation using Fernet encryption and implements the KERL (Key Event Receipt Log) protocol for identity management.

## Responsibilities

- **Token Generation**: Generate Fernet tokens for node recognition and authentication
- **KERL Protocol**: Implement Key Event Receipt Log for identity events
- **Enclave Operations**: Manage cryptographic enclave-based signing
- **Key Events**: Handle inception, rotation, and interaction events

## Key Classes

- **`TokenGenerator.java`** - Generates and validates Fernet tokens for node identity
  - Line 55: `log.info("Generated token: {}", token);` (see KNOWN_LIMITATIONS.md #32)
  - Line 70: Token validation and decryption

## Features

### Token Generation

- **Fernet Tokens**: Symmetric encryption for provisioning and recognition
- **Time-based Validation**: Tokens include timestamp for freshness
- **Cryptographic Security**: Uses strong encryption for token generation

### KERL Protocol

- **Inception Events**: Create initial decentralized identifiers
- **Rotation Events**: Rotate cryptographic keys without changing identifier
- **Interaction Events**: Sign data proving control of identifier

## Architecture

```
Sky Node
   ↓
sanctum-sanctorum (server)
   ├── TokenGenerator (Fernet tokens)
   └── KERL Protocol (identity events)
         ↓
   Stereotomy (DID management)
```

## Dependencies

- **Delos Stereotomy**: DID and KERL protocol implementation
- **Fernet**: Symmetric encryption library (v1.4.2)
- **Delos Gorgoneion**: Cryptographic primitives

## Usage

Sanctum Sanctorum is used by `nut` for token generation during node initialization and for managing cryptographic identity throughout the node lifecycle.

**Token Generation Example** (conceptual):
```java
var generator = new TokenGenerator(identity);
var token = generator.generateToken(nodeData);
// Token used for node recognition in cluster
```

## Testing

**Unit Tests**:
```bash
./mvnw test -pl sanctum-sanctorum
```

**Integration**: Tested via `local-demo` smoke tests

## POC Constraints

### Token Logging (Issue #32)

Tokens are logged at INFO level for debugging purposes during POC demos:

```java
// TokenGenerator.java:55
log.info("Generated token: {}", token);
```

**Why acceptable for POC**:
- Short-lived demo sessions (minutes to hours)
- Debugging aid for understanding token flow
- Controlled, non-production environment

**Production consideration**: Move to DEBUG level or remove sensitive logging.

See [KNOWN_LIMITATIONS.md #32](../KNOWN_LIMITATIONS.md#32) for details.

### Fresh SecureRandom (Issue #35)

Shamir secret sharing operations create fresh `SecureRandom` instances:

**Why acceptable for POC**:
- Initialization code only (not hot-path)
- Simplicity over optimization
- Rare operation (once per cluster bootstrap)

**Production consideration**: Reuse properly seeded instance if profiling shows benefit.

See [KNOWN_LIMITATIONS.md #35](../KNOWN_LIMITATIONS.md#35) for details.

## See Also

- **sanctum** - Identity wrapper module
- [ARCHITECTURE.md](../ARCHITECTURE.md) - KERL protocol explanation
- [KNOWN_LIMITATIONS.md](../KNOWN_LIMITATIONS.md) - POC constraints (#32, #35)
- **Delos Stereotomy** - https://github.com/Hellblazer/Delos (KERL protocol details)
