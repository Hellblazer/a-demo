# Test Certificates for CRITICAL #2 - Certificate Validator

This directory contains test certificates for validating the mTLS certificate validator implementation.

## Generated Certificates

### CA Certificates
- **root-ca.crt** / **root-ca.key**: Root Certificate Authority (self-signed)
- **server-ca.crt** / **server-ca.key**: Server Certificate Authority (signed by Root CA)

### Server Certificates
- **server-valid.crt** / **server-valid.key**: Valid server certificate (signed by Server CA)
- **server-expired.crt** / **server-expired.key**: Expired server certificate (for testing expired cert handling)

### Client Certificates
- **client-valid.crt** / **client-valid.key**: Valid client certificate (signed by Server CA)

## Certificate Hierarchy

```
Root CA (self-signed)
├── Server CA (intermediate)
│   ├── server-valid.crt (valid)
│   ├── server-expired.crt (expired - 1 day validity)
│   └── client-valid.crt (valid)
└── Unknown CA (for testing untrusted signers)
```

## Generation

To regenerate certificates:
```bash
./generate-test-certs.sh
```

## Usage in Tests

See `SkyApplicationCertificateValidationTest.java` for test usage examples.

### Test Scenarios

1. **Valid Server Certificate**: Should pass validation
   - Uses: server-valid.crt with server-ca.crt chain

2. **Valid Client Certificate**: Should pass validation
   - Uses: client-valid.crt with server-ca.crt chain

3. **Expired Certificate**: Should fail validation
   - Uses: server-expired.crt (1-day validity)

4. **Unknown CA**: Should fail validation
   - Simulated by not including the CA in trusted chain

5. **Invalid Signature**: Should fail validation
   - Detected during certificate verification

## Notes

- All private keys are in this directory for testing only - NEVER use in production
- Certificates are valid for 365 days (except server-expired which is 1 day)
- Root CA valid for 3650 days (10 years)
- For production, use real certificates from trusted CAs or infrastructure like Delos/Stereotomy
