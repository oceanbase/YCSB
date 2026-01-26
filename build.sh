#!/bin/bash

# OBKV Benchmark 编译打包脚本
# 功能：编译 YCSB 客户端（HBase & Table）和后端控制台
set -e

# 获取脚本所在目录 (即项目根目录)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}==========================================${NC}"
echo -e "${BLUE}  ⚡ 开始编译 OBKV Benchmark 项目...${NC}"
echo -e "${BLUE}==========================================${NC}"

# 1. 编译 YCSB 客户端模块 (core, obkv-table, obkv-hbase)
echo -e "${YELLOW}▶ 编译 YCSB 客户端模块 (HBase & Table)...${NC}"
mvn clean install -DskipTests -pl obkv-hbase,obkv-table -am -q
echo -e "${GREEN}✓ YCSB 客户端编译完成${NC}"

# 2. 编译后端控制台
echo -e "${YELLOW}▶ 编译后端控制台...${NC}"
cd benchmark-server
mvn clean package -DskipTests -q
cd ..
echo -e "${GREEN}✓ 后端控制台编译完成${NC}"

echo ""
echo -e "${GREEN}==========================================${NC}"
echo -e "${GREEN}  🎉 所有模块编译完成！${NC}"
echo -e "${GREEN}==========================================${NC}"
echo ""
echo -e "  启动控制台: ${BLUE}./start.sh${NC}"
echo ""
