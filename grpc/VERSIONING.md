# Sky Proto API Versioning Strategy

## Overview

Sky uses systematic proto API versioning to support production-ready API evolution with backward compatibility guarantees.

## Versioning Philosophy

### External APIs (Client-Facing)
- **Package naming**: `com.hellblazer.<service>.v<N>`
- **Example**: `com.hellblazer.delphi.v1` (Oracle service on port 50000)
- **Compatibility guarantee**: Backward compatible for minimum 2 major versions
- **Version bump**: Only on breaking changes (field removal, type change, RPC signature change)

### Internal APIs (Cluster-Internal)
- **Package naming**: `com.hellblazer.<service>.internal.v<N>`
- **Examples**: `com.hellblazer.sanctorum.internal.v1`, `com.hellblazer.nut.internal.v1`
- **Coordinated upgrade**: All nodes upgrade simultaneously (no multi-version coexistence)
- **Version bump**: On breaking changes or major refactoring

## Version Introduction Triggers

| Trigger | External API | Internal API |
|---------|--------------|--------------|
| Add optional field | NO (backward compatible) | NO |
| Add new RPC | NO (backward compatible) | NO |
| Remove field | YES → v2 | YES → v2 |
| Change field type | YES → v2 | YES → v2 |
| Change RPC signature | YES → v2 | YES → v2 |

## Proto Files Structure

```
grpc/src/main/proto/
├── delphi/v1/delphi.proto          (External: com.hellblazer.delphi.v1)
├── sanctorum/v1/sanctorum.proto    (Internal: com.hellblazer.sanctorum.internal.v1)
├── nut/v1/nut.proto                (Internal: com.hellblazer.nut.internal.v1)
└── geb/v1/geb.proto                (Internal: com.hellblazer.geb.internal.v1)
```

## Generated Code

Proto classes are generated to:
```
grpc/target/generated-sources/protobuf/java/com/hellblazer/<service>/v1/proto/
```

## Field Numbering Strategy

- **1-15**: Most frequently used fields (1-byte encoding)
- **16-100**: Common fields
- **101-200**: Reserved for v2 additions
- **201-300**: Reserved for v3 additions

## Java Package Changes

| Proto File | Previous Package | V1 Package |
|-----------|------------------|-----------|
| delphi.proto | `com.hellblazer.delphi.proto` | `com.hellblazer.delphi.v1.proto` |
| sanctorum.proto | `com.hellblazer.sanctorum.proto` | `com.hellblazer.sanctorum.internal.v1.proto` |
| nut.proto | `com.hellblazer.nut.proto` | `com.hellblazer.nut.internal.v1.proto` |
| geb.proto | `com.hellblazer.geb.proto` | `com.hellblazer.geb.internal.v1.proto` |

## Import Pattern Changes

```java
// Old
import com.hellblazer.delphi.proto.*;

// New
import com.hellblazer.delphi.v1.proto.*;
```

## Deprecation Policy

1. Mark feature as `[deprecated = true]` in v1 proto
2. Provide minimum 2 release cycles with deprecation warning
3. Introduce v2 in separate package
4. Migrate clients gradually
5. Remove v1 after client adoption > 95%

## Future V2 Support

When v2 is introduced:

1. Create new proto files: `delphi/v2/delphi.proto`, etc.
2. Run v1 and v2 services on different ports
3. Implement dual-version server support
4. Provide client migration guide

## Backward Compatibility

**Proto3 guarantees**:
- New optional fields: backward compatible
- Unknown fields: preserved and re-serialized
- Field removal: handled via `reserved` keyword

**What requires v2**:
- Changing field type
- Removing required fields
- Changing RPC signatures
- Package renames

## Testing

All version changes tested for:
- Wire format compatibility
- Forward compatibility (v1 client → v2 server)
- Backward compatibility (v2 client → v1 server)
- Performance (no regression > 5%)

## References

- [ARCHITECTURE.md](../ARCHITECTURE.md) - System architecture
- [API_CHANGELOG.md](./API_CHANGELOG.md) - API change history
