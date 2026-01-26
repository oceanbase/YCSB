#!/bin/bash

#######################################
# OBKV-HBase 压测控制台 - 离线部署脚本
# 
# 功能：
#   1. 检查/安装 Java 环境
#   2. 检查必要文件
#   3. 配置并启动服务
#######################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DEPLOY_DIR="$SCRIPT_DIR"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 配置
REQUIRED_JAVA_VERSION=17
SERVER_PORT=8081

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

print_header() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  OBKV-HBase 压测控制台 - 离线部署${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
}

print_footer() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  部署完成！${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
}

# 检查系统信息
check_system() {
    log_step "检查系统信息..."
    
    local os=$(uname -s)
    local arch=$(uname -m)
    local hostname=$(hostname)
    
    echo "  操作系统: $os"
    echo "  CPU架构:  $arch"
    echo "  主机名:   $hostname"
    
    # 检查是否为 Linux
    if [ "$os" != "Linux" ]; then
        log_warn "当前系统为 $os，此脚本主要针对 Linux 系统设计"
    fi
    
    # 检查是否为 x86_64
    if [ "$arch" != "x86_64" ]; then
        log_warn "当前架构为 $arch，请确保 Java 安装包与架构匹配"
    fi
    
    echo ""
}

# 检查 Java 环境
check_java() {
    log_step "检查 Java 环境..."
    
    # 检查 JAVA_HOME
    if [ -n "$JAVA_HOME" ]; then
        log_info "JAVA_HOME: $JAVA_HOME"
    fi
    
    # 检查 java 命令
    if command -v java &> /dev/null; then
        local version_output=$(java -version 2>&1)
        local version=$(echo "$version_output" | head -n 1)
        log_info "当前 Java 版本: $version"
        
        # 提取版本号
        local version_number=$(echo "$version" | grep -oE '"[0-9]+' | tr -d '"')
        
        if [ "$version_number" -ge "$REQUIRED_JAVA_VERSION" ] 2>/dev/null; then
            log_info "✓ Java 版本满足要求 (>= $REQUIRED_JAVA_VERSION)"
            return 0
        else
            log_warn "Java 版本不满足要求，需要 Java $REQUIRED_JAVA_VERSION+"
            return 1
        fi
    else
        log_warn "未找到 Java 环境"
        return 1
    fi
}

# 安装 Java
install_java() {
    log_step "安装 Java 环境..."
    
    local install_script="${DEPLOY_DIR}/install_java.sh"
    
    if [ ! -f "$install_script" ]; then
        log_error "未找到 Java 安装脚本: $install_script"
        exit 1
    fi
    
    chmod +x "$install_script"
    
    # 检查是否有 Java 安装包
    local packages_dir="${DEPLOY_DIR}/packages"
    if [ ! -d "$packages_dir" ] || [ -z "$(ls -A "$packages_dir" 2>/dev/null)" ]; then
        log_error "未找到 Java 安装包"
        log_error "请将 JDK 17 安装包放入 ${packages_dir} 目录"
        log_info ""
        log_info "支持的安装包格式: *jdk*17*.tar.gz"
        log_info "推荐下载地址（联网环境）:"
        log_info "  - 华为毕昇 JDK: https://mirrors.huaweicloud.com/kunpeng/archive/compiler/bisheng_jdk/"
        log_info "  - Eclipse Temurin: https://adoptium.net/temurin/releases/"
        exit 1
    fi
    
    # 执行安装
    sudo bash "$install_script"
    
    # 加载环境变量
    if [ -f "/etc/profile.d/java.sh" ]; then
        source /etc/profile.d/java.sh
    fi
}

# 查找 JAR 文件 (排除 macOS 元数据文件和 original 文件)
find_jar() {
    local dir="$1"
    local pattern="$2"
    find "$dir" -maxdepth 1 -name "$pattern" 2>/dev/null | grep -v "^\._" | grep -v "/\._" | grep -v original | head -n 1
}

# 检查项目文件
check_project_files() {
    log_step "检查项目文件..."
    
    local errors=0
    
    # 检查后端 JAR
    SERVER_JAR=$(find_jar "${PROJECT_DIR}/benchmark-server/target" "*.jar")
    if [ -n "$SERVER_JAR" ] && [ -f "$SERVER_JAR" ]; then
        log_info "✓ 后端服务: $(basename $SERVER_JAR)"
    else
        log_error "✗ 未找到后端 JAR: benchmark-server/target/*.jar"
        errors=$((errors + 1))
    fi
    
    # 检查 YCSB JAR
    YCSB_JAR=$(find_jar "${PROJECT_DIR}/obkv-hbase/target" "*-jar-with-dependencies.jar")
    if [ -z "$YCSB_JAR" ]; then
        YCSB_JAR=$(find_jar "${PROJECT_DIR}/obkv-table/target" "*-jar-with-dependencies.jar")
    fi
    
    if [ -n "$YCSB_JAR" ] && [ -f "$YCSB_JAR" ]; then
        log_info "✓ YCSB 客户端: $(basename $YCSB_JAR)"
    else
        log_error "✗ 未找到 YCSB JAR: obkv-hbase/target/*-jar-with-dependencies.jar"
        errors=$((errors + 1))
    fi
    
    # 检查 workloads 目录
    local workloads_dir="${PROJECT_DIR}/workloads/workloads_a_f"
    if [ -d "$workloads_dir" ]; then
        local workload_count=$(ls -1 "$workloads_dir" | wc -l)
        log_info "✓ Workload 配置: ${workload_count} 个"
    else
        log_error "✗ 未找到 Workload 目录: $workloads_dir"
        errors=$((errors + 1))
    fi
    
    # 检查启动脚本
    local start_script="${PROJECT_DIR}/start.sh"
    if [ -f "$start_script" ]; then
        log_info "✓ 启动脚本: start.sh"
    else
        log_error "✗ 未找到启动脚本: $start_script"
        errors=$((errors + 1))
    fi
    
    echo ""
    
    if [ $errors -gt 0 ]; then
        log_error "项目文件检查失败，共 $errors 个错误"
        log_info ""
        log_info "请确保已在联网环境完成编译:"
        log_info "  mvn clean package -DskipTests -pl obkv-hbase,obkv-table -am"
        log_info "  cd benchmark-server && mvn clean package -DskipTests"
        return 1
    fi
    
    return 0
}

# 检查端口
check_port() {
    log_step "检查端口 ${SERVER_PORT}..."
    
    if command -v ss &> /dev/null; then
        if ss -tuln | grep -q ":${SERVER_PORT} "; then
            log_warn "端口 ${SERVER_PORT} 已被占用"
            local pid=$(ss -tulnp | grep ":${SERVER_PORT} " | grep -oP 'pid=\K[0-9]+' | head -n 1)
            if [ -n "$pid" ]; then
                log_info "占用进程 PID: $pid"
            fi
            return 1
        fi
    elif command -v netstat &> /dev/null; then
        if netstat -tuln | grep -q ":${SERVER_PORT} "; then
            log_warn "端口 ${SERVER_PORT} 已被占用"
            return 1
        fi
    fi
    
    log_info "✓ 端口 ${SERVER_PORT} 可用"
    return 0
}

# 配置环境
configure_env() {
    log_step "配置环境..."
    
    # 创建必要目录
    mkdir -p "${PROJECT_DIR}/result"
    mkdir -p "${PROJECT_DIR}/server/temp"
    
    # 设置脚本可执行权限
    chmod +x "${PROJECT_DIR}/start.sh" 2>/dev/null || true
    chmod +x "${PROJECT_DIR}/stop.sh" 2>/dev/null || true
    chmod +x "${PROJECT_DIR}/restart.sh" 2>/dev/null || true
    chmod +x "${PROJECT_DIR}/run_fast_test.sh" 2>/dev/null || true
    chmod +x "${PROJECT_DIR}/create_table.sh" 2>/dev/null || true
    
    log_info "✓ 目录和权限已配置"
}

# 强制停止旧服务
stop_old_service() {
    log_step "停止旧服务..."
    
    # 1. 尝试通过 PID 文件停止
    local pid_file="${PROJECT_DIR}/server/server.pid"
    if [ -f "$pid_file" ]; then
        local old_pid=$(cat "$pid_file" 2>/dev/null)
        if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
            log_info "停止旧服务 (PID: $old_pid)..."
            kill -9 "$old_pid" 2>/dev/null || true
            sleep 1
        fi
        rm -f "$pid_file"
    fi
    
    # 2. 查找并强制停止所有 obkv-hbase-server 相关进程
    local pids=$(pgrep -f "obkv-hbase-server" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        log_info "发现遗留进程: $pids"
        for pid in $pids; do
            log_info "  强制停止进程 $pid..."
            kill -9 "$pid" 2>/dev/null || true
        done
        sleep 1
    fi
    
    # 3. 检查端口占用并强制停止
    if command -v ss &> /dev/null; then
        local port_pid=$(ss -tulnp 2>/dev/null | grep ":${SERVER_PORT} " | grep -oP 'pid=\K[0-9]+' | head -n 1)
        if [ -n "$port_pid" ]; then
            log_info "端口 ${SERVER_PORT} 被进程 $port_pid 占用，强制停止..."
            kill -9 "$port_pid" 2>/dev/null || true
            sleep 1
        fi
    elif command -v lsof &> /dev/null; then
        local port_pid=$(lsof -ti:${SERVER_PORT} 2>/dev/null | head -n 1)
        if [ -n "$port_pid" ]; then
            log_info "端口 ${SERVER_PORT} 被进程 $port_pid 占用，强制停止..."
            kill -9 "$port_pid" 2>/dev/null || true
            sleep 1
        fi
    fi
    
    log_info "✓ 旧服务已停止"
}

# 启动服务
start_service() {
    log_step "启动服务..."
    
    # 先停止旧服务
    stop_old_service
    
    cd "$PROJECT_DIR"
    
    # 查找 JAR 文件
    local server_jar=$(find_jar "${PROJECT_DIR}/benchmark-server/target" "*.jar")
    local ycsb_jar=$(find_jar "${PROJECT_DIR}/obkv-hbase/target" "*-jar-with-dependencies.jar")
    if [ -z "$ycsb_jar" ]; then
        ycsb_jar=$(find_jar "${PROJECT_DIR}/obkv-table/target" "*-jar-with-dependencies.jar")
    fi
    
    if [ -n "$server_jar" ] && [ -f "$server_jar" ]; then
        log_info "检测到已编译的 JAR，直接启动服务..."
        log_info "  Server: $(basename $server_jar)"
        log_info "  YCSB: $(basename $ycsb_jar)"
        
        # 直接启动 Java 服务
        local pid_file="${PROJECT_DIR}/server/server.pid"
        
        # 设置环境变量
        export YCSB_JAR_PATH="$ycsb_jar"
        export YCSB_WORKLOADS_PATH="${PROJECT_DIR}/workloads/workloads_a_f"
        
        # 启动服务
        nohup java -jar "$server_jar" > "${PROJECT_DIR}/server/console.log" 2>&1 &
        local pid=$!
        echo $pid > "$pid_file"
        
        log_info "服务已启动 (PID: $pid)"
        
        # 等待服务就绪
        log_info "等待服务就绪..."
        local max_wait=30
        local waited=0
        while [ $waited -lt $max_wait ]; do
            if curl -s "http://localhost:${SERVER_PORT}/api/health" > /dev/null 2>&1; then
                log_info "✓ 服务已就绪"
                return 0
            fi
            sleep 1
            waited=$((waited + 1))
            echo -n "."
        done
        echo ""
        log_warn "服务启动超时，请检查日志: ${PROJECT_DIR}/server/console.log"
        return 1
    else
        log_error "未找到编译后的 JAR 文件"
        return 1
    fi
}

# 显示访问信息
show_access_info() {
    local ip=$(hostname -I 2>/dev/null | awk '{print $1}')
    if [ -z "$ip" ]; then
        ip="<服务器IP>"
    fi
    
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  服务已启动！${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "  本机访问: http://localhost:${SERVER_PORT}"
    echo "  远程访问: http://${ip}:${SERVER_PORT}"
    echo ""
    echo "  日志文件: ${PROJECT_DIR}/server/console.log"
    echo "  结果目录: ${PROJECT_DIR}/result/"
    echo ""
    echo "  停止服务: ${PROJECT_DIR}/stop.sh"
    echo "  重启服务: ${PROJECT_DIR}/restart.sh"
    echo ""
}

# 仅检查模式
check_only() {
    print_header
    check_system
    
    if check_java; then
        log_info "Java 环境: ✓"
    else
        log_warn "Java 环境: ✗ 需要安装"
    fi
    
    echo ""
    
    if check_project_files; then
        log_info "项目文件: ✓"
    else
        log_warn "项目文件: ✗ 需要编译"
    fi
    
    echo ""
    
    if check_port; then
        log_info "端口状态: ✓"
    else
        log_warn "端口状态: ✗ 已占用"
    fi
    
    echo ""
    log_info "检查完成"
}

# 完整部署
full_deploy() {
    print_header
    
    # 1. 检查系统
    check_system
    
    # 2. 检查/安装 Java
    if ! check_java; then
        echo ""
        read -p "是否安装 Java 环境? (Y/n): " confirm
        if [ "$confirm" != "n" ] && [ "$confirm" != "N" ]; then
            install_java
            
            # 重新检查
            if ! check_java; then
                log_error "Java 安装后仍无法使用，请手动检查"
                exit 1
            fi
        else
            log_error "Java 环境是必需的，无法继续部署"
            exit 1
        fi
    fi
    
    echo ""
    
    # 3. 检查项目文件
    if ! check_project_files; then
        log_error "项目文件不完整，请先在联网环境完成编译"
        exit 1
    fi
    
    echo ""
    
    # 4. 检查端口
    if ! check_port; then
        read -p "是否继续? (y/N): " confirm
        if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
            exit 1
        fi
    fi
    
    echo ""
    
    # 5. 配置环境
    configure_env
    
    echo ""
    
    # 6. 启动服务
    if start_service; then
        show_access_info
    else
        log_error "服务启动失败"
        exit 1
    fi
    
    print_footer
}

# 显示帮助
show_help() {
    echo "OBKV-HBase 压测控制台 - 离线部署脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  --check     仅检查环境，不进行部署"
    echo "  --java      仅安装 Java 环境"
    echo "  --start     仅启动服务（假设环境已配置）"
    echo "  --help      显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0              # 完整部署"
    echo "  $0 --check      # 检查环境"
    echo "  $0 --java       # 安装 Java"
    echo "  $0 --start      # 启动服务"
}

# 主入口
main() {
    case "${1:-}" in
        --check)
            check_only
            ;;
        --java)
            print_header
            check_system
            install_java
            ;;
        --start)
            print_header
            if check_java && check_project_files && check_port; then
                configure_env
                start_service
                show_access_info
            fi
            ;;
        --help|-h)
            show_help
            ;;
        "")
            full_deploy
            ;;
        *)
            log_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

main "$@"

