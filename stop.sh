#!/bin/bash

#######################################
# OBKV-HBase 压测控制台 - 停止脚本
#######################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="${SCRIPT_DIR}/server/server.pid"

echo ""
echo "========================================"
echo "  停止 OBKV Benchmark (HBase & Table)"
echo "========================================"
echo ""

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        echo "▶ 停止服务 (PID: $PID)..."
        kill $PID
        sleep 2
        
        # 强制结束（如果还在运行）
        if ps -p $PID > /dev/null 2>&1; then
            echo "▶ 强制停止..."
            kill -9 $PID 2>/dev/null || true
        fi
        
        rm -f "$PID_FILE"
        echo "✓ 服务已停止"
    else
        echo "✓ 服务未运行"
        rm -f "$PID_FILE"
    fi
else
    echo "✓ 服务未运行 (无 PID 文件)"
fi

# 尝试查找并停止所有相关进程
echo "▶ 检查遗留进程..."
PIDS=$(pgrep -f "obkv-.*-server" 2>/dev/null || true)
if [ -n "$PIDS" ]; then
    echo "  发现遗留进程: $PIDS"
    for pid in $PIDS; do
        echo "  停止进程 $pid..."
        kill $pid 2>/dev/null || true
    done
    sleep 2
    # 强制停止
    PIDS=$(pgrep -f "obkv-.*-server" 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
        echo "  强制停止..."
        kill -9 $PIDS 2>/dev/null || true
    fi
    echo "✓ 遗留进程已清理"
else
    echo "✓ 无遗留进程"
fi

echo ""
