#!/bin/bash

# =============================================================================
# OBKV-HBase Console Server Run Script
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "OBKV-HBase Console Server"
echo "=========================================="

# JAR file path
JAR_FILE="target/obkv-hbase-server-1.0.0-SNAPSHOT.jar"

# Check if JAR file exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Please run ./build.sh first"
    exit 1
fi

# Check if YCSB jar exists
YCSB_JAR="../build/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$YCSB_JAR" ]; then
    echo "Warning: YCSB JAR not found: $YCSB_JAR"
    echo "Please run ../build.sh to build the YCSB module first"
fi

# Default port
PORT=${PORT:-8080}

echo "Starting server on port $PORT..."
echo ""

# Run the server
java -jar "$JAR_FILE" \
    --server.port=$PORT \
    --ycsb.jar-path="$YCSB_JAR" \
    --ycsb.workloads-path="../workloads/workloads_a_f" \
    --ycsb.temp-path="./temp" \
    "$@"

