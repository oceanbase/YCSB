#!/bin/bash

#===============================================================================
# OBKV-HBase 压测控制台 - 离线部署脚本
#===============================================================================
# 功能：在离线 Linux x86_64 环境中完成所有部署工作
# 包括：Java 环境检查/安装、环境验证、服务启动
#===============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}▶${NC} $1"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# 配置
REQUIRED_JAVA_VERSION=17
SERVICE_PORT=8081

#===============================================================================
# 函数定义
#===============================================================================

print_banner() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║                                                            ║${NC}"
    echo -e "${CYAN}║      ${GREEN}OBKV-HBase 压测控制台 - 离线部署${CYAN}                     ║${NC}"
    echo -e "${CYAN}║                                                            ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

check_system() {
    log_step "检查系统环境..."
    
    # 检查操作系统
    OS_TYPE=$(uname -s)
    if [ "$OS_TYPE" != "Linux" ]; then
        log_error "此脚本仅支持 Linux 系统，当前系统: $OS_TYPE"
        exit 1
    fi
    log_success "操作系统: Linux"
    
    # 检查 CPU 架构
    ARCH=$(uname -m)
    if [ "$ARCH" != "x86_64" ]; then
        log_warn "当前 CPU 架构: $ARCH，预期: x86_64"
        log_warn "Java 安装包可能不兼容"
    else
        log_success "CPU 架构: x86_64"
    fi
    
    # 显示系统信息
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        log_success "操作系统版本: $PRETTY_NAME"
    fi
    
    # 检查内存
    TOTAL_MEM=$(free -m | awk '/^Mem:/{print $2}')
    log_success "总内存: ${TOTAL_MEM} MB"
    
    if [ "$TOTAL_MEM" -lt 2048 ]; then
        log_warn "内存较小，建议至少 4GB 以获得最佳性能"
    fi
    
    # 检查磁盘空间
    DISK_AVAIL=$(df -m "$PROJECT_DIR" | awk 'NR==2{print $4}')
    log_success "可用磁盘空间: ${DISK_AVAIL} MB"
    
    if [ "$DISK_AVAIL" -lt 1024 ]; then
        log_warn "磁盘空间不足，建议至少 1GB 可用空间"
    fi
    
    echo ""
}

check_java() {
    log_step "检查 Java 环境..."
    
    # 首先检查本地环境变量配置
    if [ -f "${SCRIPT_DIR}/java_env.sh" ]; then
        source "${SCRIPT_DIR}/java_env.sh"
        log_info "加载本地 Java 环境配置"
    fi
    
    if ! command -v java &> /dev/null; then
        log_warn "未找到 Java 环境"
        return 1
    fi
    
    # 获取 Java 版本
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
    MAJOR_VERSION=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
    
    log_info "检测到 Java 版本: ${JAVA_VERSION}"
    
    if [ "$MAJOR_VERSION" -lt "$REQUIRED_JAVA_VERSION" ] 2>/dev/null; then
        log_warn "Java 版本不满足要求 (需要 >= ${REQUIRED_JAVA_VERSION})"
        return 1
    fi
    
    log_success "Java 版本满足要求"
    log_success "JAVA_HOME: ${JAVA_HOME:-未设置}"
    echo ""
    return 0
}

install_java() {
    log_step "安装 Java 环境..."
    
    if [ ! -f "${SCRIPT_DIR}/install_java.sh" ]; then
        log_error "找不到 Java 安装脚本"
        exit 1
    fi
    
    # 运行 Java 安装脚本
    bash "${SCRIPT_DIR}/install_java.sh"
    
    # 加载新的 Java 环境
    if [ -f "${SCRIPT_DIR}/java_env.sh" ]; then
        source "${SCRIPT_DIR}/java_env.sh"
    fi
    
    echo ""
}

check_project_files() {
    log_step "检查项目文件..."
    
    # 检查关键文件
    local files_ok=true
    
    # 检查后端 JAR
    SERVER_JAR=$(find "${PROJECT_DIR}/benchmark-server/target" -maxdepth 1 -name "*.jar" -type f 2>/dev/null | grep -v original | grep -v "/\._" | head -n 1)
    if [ -n "$SERVER_JAR" ] && [ -f "$SERVER_JAR" ]; then
        log_success "后端服务 JAR: 已编译"
    else
        log_warn "后端服务 JAR: 未找到 (预期在 benchmark-server/target/)"
        files_ok=false
    fi
    
    # 检查 YCSB JAR
    YCSB_JAR=$(find "${PROJECT_DIR}/obkv-hbase/target" -maxdepth 1 -name "*-jar-with-dependencies.jar" -type f 2>/dev/null | grep -v "/\._" | head -n 1)
    if [ -z "$YCSB_JAR" ]; then
        YCSB_JAR=$(find "${PROJECT_DIR}/obkv-table/target" -maxdepth 1 -name "*-jar-with-dependencies.jar" -type f 2>/dev/null | grep -v "/\._" | head -n 1)
    fi
    
    if [ -n "$YCSB_JAR" ] && [ -f "$YCSB_JAR" ]; then
        log_success "YCSB 客户端 JAR: 已编译"
    else
        log_warn "YCSB 客户端 JAR: 未找到 (预期在 obkv-hbase/target/)"
        files_ok=false
    fi
    
    # 检查 workloads
    WORKLOADS_DIR="${PROJECT_DIR}/workloads/workloads_a_f"
    if [ -d "$WORKLOADS_DIR" ]; then
        WORKLOAD_COUNT=$(ls -1 "$WORKLOADS_DIR" | wc -l)
        log_success "Workload 配置: ${WORKLOAD_COUNT} 个"
    else
        log_warn "Workload 目录: 未找到"
        files_ok=false
    fi
    
    # 检查启动脚本
    if [ -f "${PROJECT_DIR}/start.sh" ]; then
        log_success "启动脚本: 存在"
    else
        log_warn "启动脚本: 未找到"
        files_ok=false
    fi
    
    echo ""
    
    if [ "$files_ok" = false ]; then
        return 1
    fi
    return 0
}

check_port() {
    log_step "检查服务端口..."
    
    if command -v lsof &> /dev/null; then
        if lsof -i:$SERVICE_PORT &> /dev/null; then
            log_warn "端口 ${SERVICE_PORT} 已被占用"
            lsof -i:$SERVICE_PORT
            echo ""
            read -p "是否终止占用进程并继续? (y/N): " CONFIRM
            if [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
                lsof -i:$SERVICE_PORT -t | xargs kill -9 2>/dev/null || true
                log_info "已终止占用进程"
            else
                log_error "端口被占用，无法启动服务"
                return 1
            fi
        else
            log_success "端口 ${SERVICE_PORT} 可用"
        fi
    elif command -v netstat &> /dev/null; then
        if netstat -tln | grep -q ":$SERVICE_PORT "; then
            log_warn "端口 ${SERVICE_PORT} 已被占用"
            return 1
        else
            log_success "端口 ${SERVICE_PORT} 可用"
        fi
    else
        log_warn "无法检查端口状态（缺少 lsof 或 netstat）"
    fi
    
    echo ""
    return 0
}

start_service() {
    log_step "启动服务..."
    
    cd "$PROJECT_DIR"
    
    # 使用 start.sh 启动
    if [ -f "start.sh" ]; then
        ./start.sh
    else
        # 直接启动 JAR
        log_info "直接启动后端服务..."
        
        # 查找 JAR
        local server_jar=$(find "${PROJECT_DIR}/benchmark-server/target" -maxdepth 1 -name "*.jar" -type f 2>/dev/null | grep -v original | grep -v "/\._" | head -n 1)
        local ycsb_jar=$(find "${PROJECT_DIR}/obkv-hbase/target" -maxdepth 1 -name "*-jar-with-dependencies.jar" -type f 2>/dev/null | grep -v "/\._" | head -n 1)
        
        export YCSB_JAR_PATH="$ycsb_jar"
        export WORKLOADS_PATH="${PROJECT_DIR}/workloads/workloads_a_f"
        
        nohup java -jar "$server_jar" \
            --ycsb.jar-path="$YCSB_JAR_PATH" \
            --ycsb.workloads-path="$WORKLOADS_PATH" \
            > "${PROJECT_DIR}/server.log" 2>&1 &
        
        log_info "等待服务启动..."
        sleep 5
        
        if curl -s "http://localhost:${SERVICE_PORT}/health" > /dev/null 2>&1; then
            log_success "服务启动成功"
        else
            log_error "服务启动失败，请查看日志: ${PROJECT_DIR}/server.log"
            return 1
        fi
    fi
    
    echo ""
}

print_summary() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║                                                            ║${NC}"
    echo -e "${CYAN}║               ${GREEN}✅ 部署完成！${CYAN}                               ║${NC}"
    echo -e "${CYAN}║                                                            ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo "  访问控制台: http://localhost:${SERVICE_PORT}"
    echo ""
    echo "  常用命令："
    echo "    启动服务: cd ${PROJECT_DIR} && ./start.sh"
    echo "    停止服务: cd ${PROJECT_DIR} && ./stop.sh"
    echo "    查看日志: tail -f ${PROJECT_DIR}/server.log"
    echo ""
}

show_help() {
    echo "OBKV-HBase 压测控制台 - 离线部署脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help       显示帮助信息"
    echo "  -c, --check      仅检查环境，不启动服务"
    echo "  -j, --java-only  仅安装 Java 环境"
    echo "  -s, --start      仅启动服务（假设环境已就绪）"
    echo ""
}

#===============================================================================
# 主流程
#===============================================================================

# 解析参数
CHECK_ONLY=false
JAVA_ONLY=false
START_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -c|--check)
            CHECK_ONLY=true
            shift
            ;;
        -j|--java-only)
            JAVA_ONLY=true
            shift
            ;;
        -s|--start)
            START_ONLY=true
            shift
            ;;
        *)
            log_error "未知参数: $1"
            show_help
            exit 1
            ;;
    esac
done

# 打印 Banner
print_banner

# 仅安装 Java
if [ "$JAVA_ONLY" = true ]; then
    install_java
    exit 0
fi

# 仅启动服务
if [ "$START_ONLY" = true ]; then
    check_java || { log_error "Java 环境检查失败"; exit 1; }
    check_port || exit 1
    start_service
    print_summary
    exit 0
fi

# 完整部署流程
echo "开始部署检查..."
echo ""

# 1. 检查系统
check_system

# 2. 检查/安装 Java
if ! check_java; then
    echo ""
    read -p "是否安装 Java 17? (Y/n): " INSTALL_JAVA
    if [ "$INSTALL_JAVA" != "n" ] && [ "$INSTALL_JAVA" != "N" ]; then
        install_java
        
        # 重新检查
        if ! check_java; then
            log_error "Java 安装后仍无法正常使用"
            exit 1
        fi
    else
        log_error "Java 环境是必需的，无法继续"
        exit 1
    fi
fi

# 3. 检查项目文件
if ! check_project_files; then
    log_error "项目文件不完整，请确保已正确复制所有编译产物"
    exit 1
fi

# 仅检查模式
if [ "$CHECK_ONLY" = true ]; then
    echo ""
    log_success "环境检查完成，所有条件满足"
    exit 0
fi

# 4. 检查端口
check_port || exit 1

# 5. 启动服务
start_service

# 6. 打印摘要
print_summary
