#!/bin/bash

# =============================================================================
# OBKV-HBase Console Server Build Script
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "OBKV-HBase Console Server Build"
echo "=========================================="

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH"
    exit 1
fi

# Check Maven version
MVN_VERSION=$(mvn -version 2>&1 | head -n 1)
echo "Maven: $MVN_VERSION"

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1)
echo "Java: $JAVA_VERSION"

echo ""
echo "Building server..."

# Clean and package
mvn clean package -DskipTests

# Check if build was successful
JAR_FILE="target/obkv-hbase-server-1.0.0-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    echo ""
    echo "=========================================="
    echo "Build successful!"
    echo "JAR file: $JAR_FILE"
    echo "=========================================="
    echo ""
    echo "To run the server:"
    echo "  ./run.sh"
    echo ""
else
    echo "Error: Build failed, JAR file not found"
    exit 1
fi

