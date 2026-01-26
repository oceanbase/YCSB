#!/bin/bash

#===============================================================================
# 下载 OpenJDK 17 (Linux x86_64)
# 在有网络的机器上运行此脚本下载 JDK
#===============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JDK_DIR="${SCRIPT_DIR}/jdk"

# OpenJDK 17 下载地址
# 使用 Oracle 官方 OpenJDK
JDK_URL="https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_linux-x64_bin.tar.gz"
JDK_FILE="openjdk-17.0.2_linux-x64_bin.tar.gz"

# 备选: Adoptium (Eclipse Temurin)
# JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz"
# JDK_FILE="OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz"

echo ""
echo "========================================"
echo "  下载 OpenJDK 17 (Linux x86_64)"
echo "========================================"
echo ""

# 创建目录
mkdir -p "$JDK_DIR"
cd "$JDK_DIR"

# 检查是否已存在
if [ -f "$JDK_FILE" ]; then
    echo "JDK 文件已存在: $JDK_FILE"
    read -p "是否重新下载? (y/N): " CONFIRM
    if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
        echo "跳过下载"
        exit 0
    fi
fi

echo "下载地址: $JDK_URL"
echo "保存位置: ${JDK_DIR}/${JDK_FILE}"
echo ""

# 下载
if command -v wget &> /dev/null; then
    wget -O "$JDK_FILE" "$JDK_URL"
elif command -v curl &> /dev/null; then
    curl -L -o "$JDK_FILE" "$JDK_URL"
else
    echo "错误: 需要 wget 或 curl 来下载文件"
    exit 1
fi

# 验证文件
if [ -f "$JDK_FILE" ]; then
    FILE_SIZE=$(ls -lh "$JDK_FILE" | awk '{print $5}')
    echo ""
    echo "✅ 下载完成!"
    echo "   文件: ${JDK_DIR}/${JDK_FILE}"
    echo "   大小: ${FILE_SIZE}"
else
    echo "❌ 下载失败"
    exit 1
fi

echo ""
echo "现在可以将整个 deploy/ 目录复制到目标机器"
echo ""

