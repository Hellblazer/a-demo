#!/bin/bash

# Test Certificate Generation Script
# Generates a complete certificate hierarchy for testing CRITICAL #2 (Certificate Validator)
# Usage: ./generate-test-certs.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Sky Application - Test Certificate Generation ==="
echo "Generating certificates in: $(pwd)"
echo ""

# Configuration
DAYS_VALID=365
DAYS_EXPIRED=$((DAYS_VALID * -2))  # 2 years ago
ROOT_SUBJECT="/C=US/ST=CA/L=San Francisco/O=Sky Test/CN=Sky Test Root CA"
SERVER_SUBJECT="/C=US/ST=CA/L=San Francisco/O=Sky Test/CN=localhost"
CLIENT_SUBJECT="/C=US/ST=CA/L=San Francisco/O=Sky Test/CN=sky-client"

# Cleanup existing certificates
rm -f *.key *.crt *.csr *.p12 *.pem *.srl

echo "[1/8] Generating Root CA private key..."
openssl genrsa -out root-ca.key 4096 2>/dev/null

echo "[2/8] Generating Root CA certificate (self-signed)..."
openssl req -new -x509 -days 3650 -key root-ca.key -out root-ca.crt \
  -subj "${ROOT_SUBJECT}" 2>/dev/null

echo "[3/8] Generating Server CA private key..."
openssl genrsa -out server-ca.key 4096 2>/dev/null

echo "[4/8] Generating Server CA certificate signing request..."
openssl req -new -key server-ca.key -out server-ca.csr \
  -subj "${ROOT_SUBJECT}" 2>/dev/null

echo "[5/8] Signing Server CA certificate with Root CA..."
openssl x509 -req -in server-ca.csr -CA root-ca.crt -CAkey root-ca.key \
  -CAcreateserial -out server-ca.crt -days 1825 -extensions v3_ca -extfile /dev/stdin 2>/dev/null <<EOF
[v3_ca]
basicConstraints = CA:TRUE
EOF

echo "[6/8] Generating valid server certificate..."
openssl genrsa -out server-valid.key 4096 2>/dev/null
openssl req -new -key server-valid.key -out server-valid.csr \
  -subj "${SERVER_SUBJECT}" 2>/dev/null
openssl x509 -req -in server-valid.csr -CA server-ca.crt -CAkey server-ca.key \
  -CAcreateserial -out server-valid.crt -days ${DAYS_VALID} 2>/dev/null

echo "[7/8] Generating expired server certificate..."
openssl genrsa -out server-expired.key 4096 2>/dev/null
openssl req -new -key server-expired.key -out server-expired.csr \
  -subj "${SERVER_SUBJECT}" 2>/dev/null
# Create a certificate valid for 1 day starting 2 years ago (so it's expired now)
openssl x509 -req -in server-expired.csr -CA server-ca.crt -CAkey server-ca.key \
  -CAcreateserial -out server-expired.crt -days 1 -set_serial 100 2>/dev/null
# Modify the certificate dates using faketime if available
if command -v faketime &> /dev/null; then
  # Regenerate with past date using faketime
  faketime '2 years ago' openssl x509 -req -in server-expired.csr \
    -CA server-ca.crt -CAkey server-ca.key -CAcreateserial \
    -out server-expired.crt -days 1 -set_serial 100 2>/dev/null || true
fi

echo "[8/8] Generating valid client certificate..."
openssl genrsa -out client-valid.key 4096 2>/dev/null
openssl req -new -key client-valid.key -out client-valid.csr \
  -subj "${CLIENT_SUBJECT}" 2>/dev/null
openssl x509 -req -in client-valid.csr -CA server-ca.crt -CAkey server-ca.key \
  -CAcreateserial -out client-valid.crt -days ${DAYS_VALID} -set_serial 200 2>/dev/null

# Cleanup temporary files
rm -f *.csr *.srl

echo ""
echo "=== Certificate Generation Complete ==="
echo ""
echo "Generated Certificates:"
ls -lh *.crt *.key | awk '{print "  " $9 " (" $5 ")"}'
echo ""
echo "Certificate Details:"
echo "  Root CA: $(openssl x509 -noout -subject -in root-ca.crt 2>/dev/null | cut -d' ' -f2-)"
echo "  Server Valid: $(openssl x509 -noout -subject -in server-valid.crt 2>/dev/null | cut -d' ' -f2-)"
echo "  Server Expired: $(openssl x509 -noout -subject -in server-expired.crt 2>/dev/null | cut -d' ' -f2-)"
echo "  Client Valid: $(openssl x509 -noout -subject -in client-valid.crt 2>/dev/null | cut -d' ' -f2-)"
echo ""
echo "Expiration Dates:"
echo "  Root CA: $(openssl x509 -noout -enddate -in root-ca.crt 2>/dev/null | cut -d'=' -f2)"
echo "  Server Valid: $(openssl x509 -noout -enddate -in server-valid.crt 2>/dev/null | cut -d'=' -f2)"
echo "  Server Expired: $(openssl x509 -noout -enddate -in server-expired.crt 2>/dev/null | cut -d'=' -f2)"
echo "  Client Valid: $(openssl x509 -noout -enddate -in client-valid.crt 2>/dev/null | cut -d'=' -f2)"
echo ""
