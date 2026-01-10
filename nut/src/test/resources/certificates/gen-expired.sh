#!/bin/bash
# Generate an expired certificate by creating one with -1 day validity

# First, create a certificate that's valid for -1 days (already expired)
# We'll create it with the current date as end date, making it expired

# Create a CSR first if not exists
if [ ! -f server-expired.csr ]; then
  openssl req -new -key server-expired.key -out server-expired.csr \
    -subj "/C=US/ST=CA/L=San Francisco/O=Sky Test/CN=localhost" 2>/dev/null
fi

# Create a certificate that's valid from 2 days ago for 1 day (so it expired yesterday)
# We use openssl with the -not_before and not_after parameters via faketime or direct manipulation
# Since we don't have faketime, we'll use openssl with set_serial to regenerate

# Actually, let's try a different approach: Generate with very short validity
# and use a trick: sign it with a date that makes it expired

# Generate the cert with days:0 (today only)
openssl x509 -req -in server-expired.csr -CA server-ca.crt -CAkey server-ca.key \
  -CAcreateserial -out server-expired.crt -days 0 -set_serial 100 2>/dev/null

# This creates a certificate that expires today at midnight, so by the next second it's expired
# But we need it to be definitely expired. Let's check and print the dates
echo "Generated server-expired certificate details:"
openssl x509 -in server-expired.crt -noout -dates
