# Sky API Changelog

## v1 (2026-01-15)

### Oracle_ Service (delphi.v1)

**Status**: Stable
**Stability**: Backward compatible, no breaking changes from unversioned baseline
**Clients Affected**: External clients using Oracle service

**Changes**:
- Initial v1 versioned release
- Package migrated to `com.hellblazer.delphi.v1`
- All 13 Oracle operations stable and backward compatible
- Wire format unchanged from previous release

**Operations**:
- `addAssertion` - Add new assertion (subject-relation-object)
- `addNamespace` - Register new namespace
- `addObject` - Add object to namespace
- `addRelation` - Define relation type
- `addSubject` - Register subject
- `check` - Check if assertion exists at timestamp
- `deleteAssertion` - Remove assertion
- `deleteNamespace` - Remove namespace
- `deleteObject` - Remove object
- `deleteRelation` - Remove relation
- `expandSubject` - Find subjects for object
- `expandObject` - Find objects for subject
- `mapObject` - Map object relationship
- `mapSubject` - Map subject relationship
- `mapRelation` - Map relation type
- `readSubjects` - List subjects for objects
- `readObjects` - List objects for subjects
- `readSubjectsMatching` - Query subjects

**Migration Required**: Yes
- Update imports: `com.hellblazer.delphi.proto.*` → `com.hellblazer.delphi.v1.proto.*`
- Regenerate gRPC stubs from v1 proto files
- No code logic changes required

---

### Enclave_ Service (sanctorum.internal.v1)

**Status**: Stable
**Stability**: Internal API, coordinated cluster upgrades

**Changes**:
- Initial v1 versioned release
- Package migrated to `com.hellblazer.sanctorum.internal.v1`
- Enclave operations stable

**Operations**:
- `apply` - Apply encrypted share
- `unwrap` - Unwrap encrypted data
- `sign` - Sign with enclave key
- `verify` - Verify enclave signature

---

### Sphinx Service (nut.internal.v1)

**Status**: Stable
**Stability**: Internal API, coordinated cluster upgrades

**Changes**:
- Initial v1 versioned release
- Package migrated to `com.hellblazer.nut.internal.v1`
- Sphinx bootstrap operations stable

**Operations**:
- `apply` - Apply share to Sphinx
- `unwrap` - Unwrap provisioning data
- `seal` - Seal data with node key
- `unseal` - Unseal encrypted data
- `sessionKey` - Get session key
- `identifier` - Get node identifier

---

### Provisioning Service (nut.internal.v1)

**Status**: Stable
**Stability**: Internal API

**Changes**:
- Initial v1 versioned release
- Provisioning operations stable

**Operations**:
- `provision` - Provision new node with initial keys

---

### Sanctum_ Service (sanctorum.internal.v1)

**Status**: Stable
**Stability**: Internal API

**Changes**:
- Initial v1 versioned release
- KERL (Key Event Receipt Log) operations stable

---

## Versioning

- **Semantic versioning**: v1, v2, v3 (major versions only)
- **External APIs**: Backward compatible minimum 2 versions
- **Internal APIs**: Coordinated cluster upgrade
- **Deprecation policy**: Minimum 2 releases notice before removal

## Future Versions

**v2 Timeline** (Not yet planned):
- Will be introduced only on breaking changes
- v1 and v2 services will coexist on different ports
- Migration guide provided when v2 is released

See [VERSIONING.md](./VERSIONING.md) for detailed strategy.
