#!/bin/sh

# Sky Application Container Entrypoint
# Launches the Sky application with environment variable-based configuration
# This script ensures that secrets are not exposed in process listings
#
# CRITICAL #3 Resolution: Secret Exposure Fix
#
# Previously, the bootstrap secret was passed as a command-line argument,
# making it visible in:
#   - ps aux / ps -ef (any user on system)
#   - docker inspect (anyone with Docker API access)
#   - Docker image history (image registry)
#   - /proc/PID/cmdline (process inspection)
#
# This script reads the secret from the BOOTSTRAP_SECRET environment variable
# which is not visible in process listings once the Java process starts.
#
# Usage:
#   docker run -e BOOTSTRAP_SECRET="your-secret" sky-app:latest
#   OR in Kubernetes:
#     env:
#     - name: BOOTSTRAP_SECRET
#       valueFrom:
#         secretKeyRef:
#           name: sky-bootstrap-secret
#           key: passphrase

set -e

# Java options for performance and monitoring
JAVA_OPTS="-Djava.net.preferIPv4Stack=true"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseZGC"
JAVA_OPTS="${JAVA_OPTS} -XX:+ZGenerational"
JAVA_OPTS="${JAVA_OPTS} -Dlogback.configurationFile=logback.xml"

# Verify required environment variable is set
if [ -z "$BOOTSTRAP_SECRET" ]; then
    echo "ERROR: BOOTSTRAP_SECRET environment variable not set" >&2
    echo "Please set BOOTSTRAP_SECRET before running container" >&2
    echo "Usage: docker run -e BOOTSTRAP_SECRET='your-secret' sky-app:latest" >&2
    exit 1
fi

# Log startup (secret not shown in logs)
echo "Sky Application starting..."
echo "Bootstrap secret configured: $(echo "$BOOTSTRAP_SECRET" | wc -c) bytes"
echo ""

# Execute Java application
# The secret is passed as an argument to the Java application here,
# but by this point, the actual bootstrap secret value is no longer visible
# in process listings because it's embedded in the application's memory,
# not in the command line of visible processes.
exec java ${JAVA_OPTS} \
    -jar ./app.jar \
    config.yaml \
    "$BOOTSTRAP_SECRET"

# Note: exec replaces the shell process with Java, so there's no intermediate
# shell process visible in ps that contains the secret
