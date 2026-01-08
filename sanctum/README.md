# Sanctum - Identity and Cryptographic Operations

**Module**: `sanctum`
**Type**: Cryptographic Wrapper
**Purpose**: Enclave signing and verification operations for Sky identity management

## Overview

**Sanctum** provides a wrapper around identity and cryptographic operations, offering enclave-based signing and verification capabilities for Sky nodes. It serves as the interface layer between Sky application code and the underlying Delos Stereotomy identity management system.

## Responsibilities

- **Identity Operations**: Wrapper for cryptographic identity operations
- **Enclave Signing**: Provide enclave-based signing capabilities
- **Verification**: Verify signatures and identity assertions
- **KERL Integration**: Interface with Key Event Receipt Log protocol

## Key Features

- Enclave-based cryptographic operations
- Integration with Delos Stereotomy (DID/KERL)
- Signing and verification of identity assertions
- Key event management

## Dependencies

- **Delos Stereotomy**: Decentralized identifier (DID) and KERL protocol
- **Delos Gorgoneion**: Cryptographic infrastructure (Ed25519 signatures)
- **sanctum-sanctorum**: Server implementation for enclave operations

## Usage

Sanctum is used by the `nut` module for identity operations. It is not meant to be used standalone.

**Integration**:
```java
// Example (pseudocode)
var sanctum = new Sanctum(identity);
var signature = sanctum.sign(data);
var verified = sanctum.verify(signature, publicKey);
```

## Architecture

Sanctum acts as a thin wrapper providing a clean interface to complex cryptographic operations implemented by Delos components.

```
Sky Application
     ↓
  Sanctum (wrapper)
     ↓
  Stereotomy (DID/KERL)
     ↓
  Gorgoneion (crypto primitives)
```

## Testing

**Unit Tests**:
```bash
./mvnw test -pl sanctum
```

## POC Constraints

- Self-signed certificates (ephemeral, POC only)
- No external PKI integration
- Simplified key management for demo purposes

## See Also

- **sanctum-sanctorum** - Server implementation for enclave operations
- [ARCHITECTURE.md](../ARCHITECTURE.md) - System architecture and KERL explanation
- [KNOWN_LIMITATIONS.md](../KNOWN_LIMITATIONS.md) - POC constraints (#41 - No certificate revocation)
- **Delos Stereotomy** - https://github.com/Hellblazer/Delos (DID/KERL protocol)
