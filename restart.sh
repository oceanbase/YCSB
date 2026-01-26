#!/bin/bash

#######################################
# OBKV-HBase 压测控制台 - 重启脚本
#######################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ""
echo "========================================"
echo "  重启 OBKV-HBase 压测控制台"
echo "========================================"
echo ""

# 停止
"${SCRIPT_DIR}/stop.sh"

# 启动
"${SCRIPT_DIR}/start.sh"
