#===============================================================================
# OBKV Benchmark 控制台 - 打包脚本
#===============================================================================
# 功能：编译项目并生成离线部署包
# 输出：obkv-benchmark-console-offline-<日期>.tar.gz
#===============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_step() {
    echo -e "${BLUE}▶${NC} $1"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 获取脚本所在目录 (即项目根目录)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# 打包配置
DATE_STR=$(date +%Y%m%d)
PACKAGE_NAME="obkv-benchmark-console-offline-${DATE_STR}"
OUTPUT_DIR="${SCRIPT_DIR}/dist"

echo ""
echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                                                            ║${NC}"
echo -e "${CYAN}║       ${GREEN}OBKV Benchmark 控制台 - 离线打包${CYAN}                     ║${NC}"
echo -e "${CYAN}║                                                            ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# 检查 Java
log_step "检查 Java 环境..."
if ! command -v java &> /dev/null; then
    log_error "未找到 Java，无法编译项目"
    exit 1
fi
java -version
echo ""

# 检查 Maven
log_step "检查 Maven..."
MVN_EXEC="mvn"
MVN_HOME_DIR=""
if ! command -v mvn &> /dev/null; then
    # 尝试查找常用的 maven 路径
    MVN_PATH=$(find /usr/local /opt -name mvn -type f 2>/dev/null | grep bin/mvn | head -n 1)
    if [ -n "$MVN_PATH" ]; then
        MVN_EXEC="$MVN_PATH"
        MVN_HOME_DIR="$(dirname $(dirname "$MVN_PATH"))"
    elif [ -n "$MAVEN_HOME" ] && [ -x "$MAVEN_HOME/bin/mvn" ]; then
        MVN_EXEC="$MAVEN_HOME/bin/mvn"
        MVN_HOME_DIR="$MAVEN_HOME"
    else
        log_error "未找到 Maven，无法编译项目"
        log_info "请安装 Maven 或设置 MAVEN_HOME 环境变量"
        exit 1
    fi
else
    # 获取 Maven 安装目录
    MVN_HOME_DIR="$(dirname $(dirname $(which mvn)))"
fi
$MVN_EXEC -version | head -1
log_info "Maven 目录: $MVN_HOME_DIR"
echo ""

# 编译 YCSB 客户端 (包含 obkv-hbase 和 obkv-table)
log_step "编译 YCSB 客户端..."
cd "$PROJECT_ROOT"
$MVN_EXEC clean package -DskipTests -pl obkv-hbase,obkv-table -am -q
log_success "YCSB 客户端编译完成"

# 编译后端服务
log_step "编译后端服务..."
cd "${PROJECT_ROOT}/benchmark-server"
$MVN_EXEC clean package -DskipTests -q
log_success "后端服务编译完成"

# 检查 JDK 安装包 (支持多个目录和文件名)
log_step "检查 JDK 安装包..."
JDK_FILE=""
# 尝试在 deploy/packages 目录查找
for f in "${PROJECT_ROOT}/deploy/packages"/*.tar.gz "${PROJECT_ROOT}/deploy/jdk"/*.tar.gz; do
    if [ -f "$f" ]; then
        fname=$(basename "$f")
        if echo "$fname" | grep -qiE "(jdk|openjdk)" && echo "$fname" | grep -q "17"; then
            JDK_FILE="$f"
            break
        fi
    fi
done
if [ -z "$JDK_FILE" ] || [ ! -f "$JDK_FILE" ]; then
    log_error "未找到 JDK 17 安装包"
    log_info "请将 JDK 17 tar.gz 安装包放入 deploy/packages/ 目录"
    exit 1
fi
JDK_SIZE=$(ls -lh "$JDK_FILE" | awk '{print $5}')
log_success "JDK 安装包: ${JDK_SIZE} ($(basename "$JDK_FILE"))"

# 创建临时打包目录
log_step "准备打包..."
TEMP_DIR=$(mktemp -d)
PACKAGE_DIR="${TEMP_DIR}/${PACKAGE_NAME}"
mkdir -p "$PACKAGE_DIR"

# 复制必要文件
log_info "复制项目文件..."

# 复制目录结构
mkdir -p "${PACKAGE_DIR}/benchmark-server/target"
mkdir -p "${PACKAGE_DIR}/obkv-hbase/target"
mkdir -p "${PACKAGE_DIR}/obkv-table/target"
mkdir -p "${PACKAGE_DIR}/deploy/packages"
mkdir -p "${PACKAGE_DIR}/result"
mkdir -p "${PACKAGE_DIR}/docs"

# 复制后端 JAR
SERVER_JAR=$(find "${PROJECT_ROOT}/benchmark-server/target" -maxdepth 1 -name "*.jar" -type f 2>/dev/null | grep -v original | grep -v "/\._" | head -n 1)
if [ -z "$SERVER_JAR" ]; then
    log_error "未找到后端 JAR 文件"
    exit 1
fi
log_info "后端 JAR: $(basename "$SERVER_JAR")"
cp "$SERVER_JAR" "${PACKAGE_DIR}/benchmark-server/target/"

# 复制前端静态文件
cp -r "${PROJECT_ROOT}/benchmark-server/src/main/resources/static" "${PACKAGE_DIR}/benchmark-server/"

# 复制 HBase JAR (查找 jar-with-dependencies)
HBASE_JAR=$(find "${PROJECT_ROOT}/obkv-hbase/target" -maxdepth 1 -name "*-jar-with-dependencies.jar" -type f 2>/dev/null | grep -v "/\._" | head -n 1)
if [ -z "$HBASE_JAR" ]; then
    log_error "未找到 OBKV-HBase JAR 文件"
    exit 1
fi
log_info "HBase JAR: $(basename "$HBASE_JAR")"
cp "$HBASE_JAR" "${PACKAGE_DIR}/obkv-hbase/target/"

# 复制 Table JAR (查找 jar-with-dependencies)
TABLE_JAR=$(find "${PROJECT_ROOT}/obkv-table/target" -maxdepth 1 -name "*-jar-with-dependencies.jar" -type f 2>/dev/null | grep -v "/\._" | head -n 1)
if [ -n "$TABLE_JAR" ]; then
    log_info "Table JAR: $(basename "$TABLE_JAR")"
    cp "$TABLE_JAR" "${PACKAGE_DIR}/obkv-table/target/"
fi

# 复制 workloads
cp -r "${PROJECT_ROOT}/workloads" "${PACKAGE_DIR}/"

# 复制 deploy 目录
cp "${PROJECT_ROOT}/deploy/deploy.sh" "${PACKAGE_DIR}/deploy/" 2>/dev/null || true
cp "${PROJECT_ROOT}/deploy/install_java.sh" "${PACKAGE_DIR}/deploy/" 2>/dev/null || true
cp "${PROJECT_ROOT}/deploy/README.md" "${PACKAGE_DIR}/deploy/" 2>/dev/null || true
cp "${PROJECT_ROOT}/deploy/setup.sh" "${PACKAGE_DIR}/deploy/" 2>/dev/null || true
cp "$JDK_FILE" "${PACKAGE_DIR}/deploy/packages/"
cp "${PROJECT_ROOT}/deploy/packages/README.md" "${PACKAGE_DIR}/deploy/packages/" 2>/dev/null || true

# 复制 Maven
if [ -n "$MVN_HOME_DIR" ] && [ -d "$MVN_HOME_DIR" ]; then
    log_info "打包 Maven: $MVN_HOME_DIR"
    cp -r "$MVN_HOME_DIR" "${PACKAGE_DIR}/maven"
    log_success "Maven 已打包"
fi

# 复制启动/停止脚本
cp "${PROJECT_ROOT}/start.sh" "${PACKAGE_DIR}/"
cp "${PROJECT_ROOT}/stop.sh" "${PACKAGE_DIR}/" 2>/dev/null || true
cp "${PROJECT_ROOT}/restart.sh" "${PACKAGE_DIR}/" 2>/dev/null || true
cp "${PROJECT_ROOT}/create_table.sh" "${PACKAGE_DIR}/" 2>/dev/null || true
cp "${PROJECT_ROOT}/segment_load.sh" "${PACKAGE_DIR}/" 2>/dev/null || true

# 复制文档
cp "${PROJECT_ROOT}/docs/"*.md "${PACKAGE_DIR}/docs/" 2>/dev/null || true
cp "${PROJECT_ROOT}/README.md" "${PACKAGE_DIR}/" 2>/dev/null || true

# 设置权限
chmod +x "${PACKAGE_DIR}"/*.sh 2>/dev/null || true
chmod +x "${PACKAGE_DIR}/deploy"/*.sh

# 创建快速启动说明
cat > "${PACKAGE_DIR}/QUICK_START.txt" << 'EOF'
╔════════════════════════════════════════════════════════════╗
║           OBKV Benchmark 控制台 - 快速启动                 ║
╚════════════════════════════════════════════════════════════╝

【部署服务】

1. 首次部署（需要 root 权限安装 Java）:
   
   sudo ./deploy/deploy.sh

2. 已有 Java 17+ 环境，直接启动:
   
   ./start.sh

3. 访问控制台:
   
   http://<服务器IP>:8081

4. 停止服务:
   
   ./stop.sh

【工具使用】

- 自动建表:      ./create_table.sh
- 分段导入数据:  ./segment_load.sh

【文档】

  - 控制台使用:      docs/USER_GUIDE.md
  - 分段导入使用:    docs/SEGMENT_LOAD_GUIDE.md

EOF

# 清理 macOS 元数据文件
log_step "清理临时文件..."
find "$PACKAGE_DIR" -name "._*" -type f -delete 2>/dev/null || true
find "$PACKAGE_DIR" -name ".DS_Store" -type f -delete 2>/dev/null || true

# 打包 (COPYFILE_DISABLE 禁止 macOS 创建 ._ 文件)
log_step "创建压缩包..."

# 清理旧的打包文件 (放在打包前，避免删除刚生成的包)
if [ -d "$OUTPUT_DIR" ]; then
    find "$OUTPUT_DIR" -name "obkv-benchmark-console-offline-*.tar.gz" -type f -delete 2>/dev/null || true
fi

mkdir -p "$OUTPUT_DIR"
cd "$TEMP_DIR"
COPYFILE_DISABLE=1 tar -czf "${OUTPUT_DIR}/${PACKAGE_NAME}.tar.gz" "$PACKAGE_NAME"

# 清理
rm -rf "$TEMP_DIR"

# 完成
PACKAGE_FILE="${OUTPUT_DIR}/${PACKAGE_NAME}.tar.gz"
PACKAGE_SIZE=$(ls -lh "$PACKAGE_FILE" 2>/dev/null | awk '{print $5}' || echo "Unknown")

echo ""
echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                                                            ║${NC}"
echo -e "${CYAN}║               ${GREEN}✅ 打包完成！${CYAN}                               ║${NC}"
echo -e "${CYAN}║                                                            ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "  输出文件: ${PACKAGE_FILE}"
echo "  文件大小: ${PACKAGE_SIZE}"
echo ""
echo "  部署步骤:"
echo "    1. 将 ${PACKAGE_NAME}.tar.gz 复制到目标机器"
echo "    2. 解压: tar -xzf ${PACKAGE_NAME}.tar.gz"
echo "    3. 进入目录: cd ${PACKAGE_NAME}"
echo "    4. 运行部署: sudo ./deploy/deploy.sh"
echo ""
echo "  或者已有 Java 17+ 环境:"
echo "    4. 直接启动: ./start.sh"
echo ""

