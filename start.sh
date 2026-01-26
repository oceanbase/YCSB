#!/bin/bash

# =============================================================================
# OBKV-HBase Console 启动脚本
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  ⚡ OBKV Benchmark (HBase & Table)${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 查找 JAR 文件
SERVER_JAR=$(find "$SCRIPT_DIR/benchmark-server/target" -maxdepth 1 -name "*.jar" -type f 2>/dev/null | grep -v original | grep -v "/\._" | head -n 1)
if [ -z "$SERVER_JAR" ] || [ ! -f "$SERVER_JAR" ]; then
    echo -e "${RED}✗ 未找到后端 JAR 文件${NC}"
    echo -e "  请先编译项目或使用预编译的部署包"
    exit 1
fi

# 查找 YCSB JAR 并设置环境变量
YCSB_JAR=$(find "$SCRIPT_DIR/obkv-hbase/target" -maxdepth 1 -name "*-jar-with-dependencies.jar" -type f 2>/dev/null | grep -v "/\._" | head -n 1)
if [ -n "$YCSB_JAR" ]; then
    export YCSB_JAR_PATH="$YCSB_JAR"
    echo -e "  YCSB JAR: $(basename "$YCSB_JAR")"
fi

# 设置 workloads 路径
if [ -d "$SCRIPT_DIR/workloads/workloads_a_f" ]; then
    export YCSB_WORKLOADS_PATH="$SCRIPT_DIR/workloads/workloads_a_f"
elif [ -d "$SCRIPT_DIR/workloads" ]; then
    # 检查 workloads 目录下是否直接有 workloada 等文件
    if [ -f "$SCRIPT_DIR/workloads/workloada" ]; then
        export YCSB_WORKLOADS_PATH="$SCRIPT_DIR/workloads"
    else
        export YCSB_WORKLOADS_PATH="$SCRIPT_DIR/workloads/workloads_a_f"
    fi
else
    echo -e "${RED}✗ 未找到 workloads 目录${NC}"
    exit 1
fi
echo -e "  Workloads: $YCSB_WORKLOADS_PATH"

# 停止现有进程
echo -e "${YELLOW}▶ 停止现有服务...${NC}"
pkill -f "obkv-.*-server" 2>/dev/null || true
sleep 1
echo -e "${GREEN}✓ 服务已停止${NC}"

# 启动后端
echo -e "${YELLOW}▶ 启动后端服务...${NC}"
echo -e "  JAR: $(basename "$SERVER_JAR")"
nohup java -jar "$SERVER_JAR" > /tmp/obkv-backend.log 2>&1 &
BACKEND_PID=$!

# 等待服务就绪
echo -n "  等待服务启动"
for i in {1..10}; do
    if curl -s http://localhost:8081/health > /dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}✓ 后端已启动 (PID: $BACKEND_PID, Port: 8081)${NC}"
        break
    fi
    echo -n "."
    sleep 1
done

# 最终检查
if ! curl -s http://localhost:8081/health > /dev/null 2>&1; then
    echo ""
    echo -e "${RED}✗ 后端启动超时，查看日志: /tmp/obkv-backend.log${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  服务启动完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "  控制台: ${BLUE}http://localhost:8081${NC}"
echo ""
echo -e "  日志: /tmp/obkv-backend.log"
echo -e "  停止: ${YELLOW}./stop.sh${NC}"
echo ""
