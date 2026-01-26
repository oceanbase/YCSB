#!/bin/bash

#######################################
# Java 17 安装脚本
# 适用于离线环境
#######################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_PACKAGE_DIR="${SCRIPT_DIR}/packages"
INSTALL_DIR="/usr/local/java"
JAVA_VERSION="17"

# 全局变量用于函数返回值
FOUND_PACKAGE=""
JAVA_HOME_DIR=""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# 检查是否为 root 用户
check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "请使用 root 用户或 sudo 运行此脚本"
        exit 1
    fi
}

# 检测系统架构
detect_arch() {
    local arch=$(uname -m)
    case $arch in
        x86_64)
            echo "x64"
            ;;
        aarch64)
            echo "aarch64"
            ;;
        *)
            log_error "不支持的架构: $arch"
            exit 1
            ;;
    esac
}

# 查找 Java 安装包 (结果存入 FOUND_PACKAGE)
find_java_package() {
    FOUND_PACKAGE=""
    
    log_info "在 ${JAVA_PACKAGE_DIR} 中查找 Java 17 安装包..."
    
    # 检查目录是否存在
    if [ ! -d "$JAVA_PACKAGE_DIR" ]; then
        log_error "目录不存在: $JAVA_PACKAGE_DIR"
        exit 1
    fi
    
    # 列出所有 tar.gz 文件
    log_info "可用文件:"
    ls -la "$JAVA_PACKAGE_DIR"/*.tar.gz 2>/dev/null || log_warn "没有找到 .tar.gz 文件"
    
    # 遍历查找匹配的文件
    for f in "$JAVA_PACKAGE_DIR"/*.tar.gz; do
        if [ -f "$f" ]; then
            local fname="${f##*/}"  # 使用 bash 内置替代 basename
            log_info "检查文件: $fname"
            # 检查文件名是否包含 jdk 或 JDK/openjdk，以及 17
            if echo "$fname" | grep -qiE "(jdk|openjdk)" && echo "$fname" | grep -q "17"; then
                FOUND_PACKAGE="$f"
                log_info "匹配成功: $fname"
                break
            fi
        fi
    done
    
    # 如果没找到特定的，尝试任何 tar.gz
    if [ -z "$FOUND_PACKAGE" ]; then
        log_warn "未找到匹配 JDK 17 的文件，尝试使用第一个 tar.gz 文件..."
        for f in "$JAVA_PACKAGE_DIR"/*.tar.gz; do
            if [ -f "$f" ]; then
                FOUND_PACKAGE="$f"
                break
            fi
        done
    fi
    
    if [ -z "$FOUND_PACKAGE" ] || [ ! -f "$FOUND_PACKAGE" ]; then
        log_error "未找到 Java 17 安装包"
        log_error "请将 JDK 17 安装包放入 ${JAVA_PACKAGE_DIR} 目录"
        log_error "支持的格式: *.tar.gz (文件名包含 jdk/openjdk 和 17)"
        exit 1
    fi
    
    log_info "选择安装包: ${FOUND_PACKAGE##*/}"
}

# 安装 Java (结果存入 JAVA_HOME_DIR)
install_java() {
    local package="$1"
    local package_name="${package##*/}"  # 使用 bash 内置替代 basename
    
    JAVA_HOME_DIR=""
    
    log_step "安装 Java 17..."
    log_info "安装包: $package_name"
    log_info "完整路径: $package"
    
    # 验证文件存在
    if [ ! -f "$package" ]; then
        log_error "安装包文件不存在: $package"
        exit 1
    fi
    
    # 验证是 gzip 格式
    local file_type
    file_type=$(file "$package" 2>/dev/null || echo "unknown")
    log_info "文件类型: $file_type"
    
    if ! echo "$file_type" | grep -qi "gzip"; then
        log_error "文件不是 gzip 格式"
        exit 1
    fi
    
    # 显示文件大小
    local file_size
    file_size=$(stat -c%s "$package" 2>/dev/null || stat -f%z "$package" 2>/dev/null || echo "unknown")
    log_info "文件大小: $file_size bytes"
    
    # 创建安装目录
    mkdir -p "$INSTALL_DIR"
    
    # 解压
    log_info "解压安装包到 $INSTALL_DIR ..."
    if ! tar -zxf "$package" -C "$INSTALL_DIR"; then
        log_error "解压失败！"
        log_error "请检查安装包是否损坏"
        exit 1
    fi
    log_info "解压成功"
    
    # 找到解压后的目录
    JAVA_HOME_DIR=$(find "$INSTALL_DIR" -maxdepth 1 -type d -name "*jdk*17*" 2>/dev/null | head -n 1)
    
    if [ -z "$JAVA_HOME_DIR" ]; then
        JAVA_HOME_DIR=$(find "$INSTALL_DIR" -maxdepth 1 -type d -name "jdk*" 2>/dev/null | head -n 1)
    fi
    
    if [ -z "$JAVA_HOME_DIR" ]; then
        log_error "无法找到解压后的 Java 目录"
        log_error "INSTALL_DIR 内容:"
        ls -la "$INSTALL_DIR"
        exit 1
    fi
    
    log_info "Java 安装目录: $JAVA_HOME_DIR"
    
    # 创建符号链接
    ln -sf "$JAVA_HOME_DIR" "${INSTALL_DIR}/jdk17"
    log_info "创建符号链接: ${INSTALL_DIR}/jdk17 -> $JAVA_HOME_DIR"
}

# 配置环境变量
configure_env() {
    log_step "配置环境变量..."
    
    # 创建 profile 配置文件
    local profile_file="/etc/profile.d/java.sh"
    
    cat > "$profile_file" << EOF
# Java 17 环境变量
export JAVA_HOME=${INSTALL_DIR}/jdk17
export PATH=\$JAVA_HOME/bin:\$PATH
EOF
    
    chmod +x "$profile_file"
    
    # 同时配置 bashrc（适用于非登录 shell）
    local bashrc_marker="# OBKV Java Configuration"
    
    if [ -f /etc/bashrc ] && ! grep -q "$bashrc_marker" /etc/bashrc 2>/dev/null; then
        cat >> /etc/bashrc << EOF

$bashrc_marker
export JAVA_HOME=${INSTALL_DIR}/jdk17
export PATH=\$JAVA_HOME/bin:\$PATH
EOF
    fi
    
    log_info "环境变量已配置到 $profile_file"
    log_info "请执行 'source /etc/profile' 或重新登录使环境变量生效"
}

# 验证安装
verify_installation() {
    log_step "验证安装..."
    
    # 临时设置环境变量
    export JAVA_HOME="${INSTALL_DIR}/jdk17"
    export PATH="$JAVA_HOME/bin:$PATH"
    
    if command -v java &> /dev/null; then
        local version
        version=$(java -version 2>&1 | head -n 1)
        log_info "Java 版本: $version"
        
        if echo "$version" | grep -q "17"; then
            log_info "✓ Java 17 安装成功！"
            return 0
        else
            log_warn "Java 已安装，但版本可能不是 17"
            return 1
        fi
    else
        log_error "Java 安装验证失败"
        log_error "请检查 ${INSTALL_DIR}/jdk17/bin/java 是否存在"
        ls -la "${INSTALL_DIR}/jdk17/bin/" 2>/dev/null || true
        return 1
    fi
}

# 主流程
main() {
    echo ""
    echo "========================================"
    echo "  Java 17 离线安装脚本"
    echo "========================================"
    echo ""
    
    check_root
    
    # 检查是否已安装
    if command -v java &> /dev/null; then
        local current_version
        current_version=$(java -version 2>&1 | head -n 1)
        if echo "$current_version" | grep -q "17"; then
            log_warn "Java 17 已安装: $current_version"
            read -p "是否重新安装? (y/N): " confirm
            if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
                log_info "跳过安装"
                exit 0
            fi
        fi
    fi
    
    # 查找安装包 (结果存入 FOUND_PACKAGE)
    find_java_package
    
    # 安装 (结果存入 JAVA_HOME_DIR)
    install_java "$FOUND_PACKAGE"
    
    # 配置环境变量
    configure_env
    
    # 验证
    verify_installation
    
    echo ""
    echo "========================================"
    echo "  安装完成！"
    echo "========================================"
    echo ""
    echo "  JAVA_HOME: ${INSTALL_DIR}/jdk17"
    echo ""
    echo "  执行以下命令使环境变量生效："
    echo "    source /etc/profile"
    echo ""
}

main "$@"
