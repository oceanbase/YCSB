#!/bin/bash

# YCSB Web UI 编译打包脚本
# 用法：
#   ./build.sh              仅构建 webui JAR（依赖 JAR 不存在时会警告）
#   ./build.sh --with-deps  同时构建 obkv-hbase 和 obkv-table 再构建 webui
#   ./build.sh clean        清理构建产物

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SEP="=========================================="

echo "$SEP"
echo "  YCSB Web UI 编译打包脚本"
echo "$SEP"

# ============================================================
# 参数解析
# ============================================================
WITH_DEPS=false
CLEAN_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --with-deps) WITH_DEPS=true ;;
        clean)       CLEAN_ONLY=true ;;
    esac
done

# ============================================================
# 清理模式
# ============================================================
if $CLEAN_ONLY; then
    echo "[$SEP] 执行清理操作..."
    cd "$SCRIPT_DIR"
    mvn clean -q 2>/dev/null || true
    OUTPUT_JAR="$SCRIPT_DIR/build/webui-0.18.0-SNAPSHOT.jar"
    if [ -f "$OUTPUT_JAR" ]; then
        rm -f "$OUTPUT_JAR"
        echo "已删除：$OUTPUT_JAR"
    fi
    echo "清理完成！"
    exit 0
fi

# ============================================================
# 环境检查
# ============================================================
echo ""
echo "--- [1/4] 环境检查 ---"

# 检查 Java
if ! command -v java &>/dev/null; then
    echo "✗ 错误：未找到 Java，请安装 JDK 8 或更高版本"
    exit 1
fi

# 解析 Java 主版本号（兼容 1.8.x 和 11/17/21 格式）
JAVA_VERSION_RAW=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
JAVA_MAJOR=$(echo "$JAVA_VERSION_RAW" | cut -d'.' -f1)
if [ "$JAVA_MAJOR" = "1" ]; then
    # 旧格式：1.8.0_xxx → 主版本是第二段
    JAVA_MAJOR=$(echo "$JAVA_VERSION_RAW" | cut -d'.' -f2)
fi
if [ "$JAVA_MAJOR" -lt 8 ] 2>/dev/null; then
    echo "✗ 错误：Java 版本过低（检测到 $JAVA_VERSION_RAW），需要 Java 8+"
    exit 1
fi
echo "✓ Java：$JAVA_VERSION_RAW（主版本 $JAVA_MAJOR）"

# 检查 Maven
if ! command -v mvn &>/dev/null; then
    echo "✗ 错误：未找到 Maven，请安装 Maven 3.x"
    exit 1
fi
MAVEN_VERSION=$(mvn -version 2>&1 | head -1 | awk '{print $3}')
MAVEN_MAJOR=$(echo "$MAVEN_VERSION" | cut -d'.' -f1)
if [ "$MAVEN_MAJOR" != "3" ]; then
    echo "✗ 错误：需要 Maven 3.x，当前版本为 $MAVEN_VERSION"
    exit 1
fi
echo "✓ Maven：$MAVEN_VERSION"

# 检查项目根目录结构
if [ ! -f "$PROJECT_ROOT/pom.xml" ]; then
    echo "✗ 错误：找不到根目录 pom.xml：$PROJECT_ROOT/pom.xml"
    exit 1
fi
if [ ! -d "$PROJECT_ROOT/core" ]; then
    echo "✗ 错误：找不到 core 模块目录：$PROJECT_ROOT/core"
    exit 1
fi
echo "✓ 项目结构：$PROJECT_ROOT"

# 检查 webui 模块是否已添加到根 pom.xml
if ! grep -q "<module>webui</module>" "$PROJECT_ROOT/pom.xml"; then
    echo "✗ 错误：根 pom.xml 中未注册 webui 模块，请检查"
    exit 1
fi
echo "✓ webui 已注册到根 pom.xml"

# 检查运行时依赖 JAR 是否存在
HBASE_JAR="$PROJECT_ROOT/obkv-hbase/build/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
# obkv-table/build.sh 将产物复制为 1.0-SNAPSHOT 命名，同时检查两种文件名
TABLE_JAR="$PROJECT_ROOT/obkv-table/build/obkv-table-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
TABLE_JAR_ALT="$PROJECT_ROOT/obkv-table/build/obkv-table-1.0-SNAPSHOT-jar-with-dependencies.jar"

HBASE_OK=false
TABLE_OK=false

if [ -f "$HBASE_JAR" ]; then
    HBASE_OK=true
    echo "✓ obkv-hbase JAR 已存在：$(ls -sh "$HBASE_JAR" | awk '{print $1}')"
else
    echo "! obkv-hbase JAR 未找到：$HBASE_JAR"
fi

if [ -f "$TABLE_JAR" ]; then
    TABLE_OK=true
    echo "✓ obkv-table JAR 已存在：$(ls -sh "$TABLE_JAR" | awk '{print $1}')"
elif [ -f "$TABLE_JAR_ALT" ]; then
    TABLE_OK=true
    TABLE_JAR="$TABLE_JAR_ALT"
    echo "✓ obkv-table JAR 已存在：$(ls -sh "$TABLE_JAR" | awk '{print $1}')"
else
    echo "! obkv-table JAR 未找到：$TABLE_JAR"
fi

if ! $HBASE_OK || ! $TABLE_OK; then
    if ! $WITH_DEPS; then
        echo ""
        echo "  提示：运行时缺少依赖 JAR，webui 可以正常编译，"
        echo "  但启动后无法执行测试。如需同时编译依赖模块，请使用："
        echo "    ./build.sh --with-deps"
    fi
fi

# ============================================================
# 构建依赖模块（可选）
# ============================================================
if $WITH_DEPS; then
    echo ""
    echo "--- [2/4] 构建依赖模块 ---"

    if ! $HBASE_OK; then
        echo "构建 obkv-hbase..."
        ( cd "$PROJECT_ROOT/obkv-hbase" && bash build.sh )
        if [ ! -f "$HBASE_JAR" ]; then
            echo "✗ 错误：obkv-hbase 构建失败"
            exit 1
        fi
        echo "✓ obkv-hbase 构建完成"
    else
        echo "✓ obkv-hbase 已是最新，跳过"
    fi

    if ! $TABLE_OK; then
        echo "构建 obkv-table..."
        ( cd "$PROJECT_ROOT/obkv-table" && bash build.sh )
        if [ ! -f "$TABLE_JAR" ] && [ ! -f "$TABLE_JAR_ALT" ]; then
            echo "✗ 错误：obkv-table 构建失败"
            exit 1
        fi
        echo "✓ obkv-table 构建完成"
    else
        echo "✓ obkv-table 已是最新，跳过"
    fi
else
    echo ""
    echo "--- [2/4] 构建依赖模块 --- 跳过（使用 --with-deps 开启）"
fi

# ============================================================
# 安装根 POM
# ============================================================
echo ""
echo "--- [3/4] 安装根 POM ---"
cd "$PROJECT_ROOT"
mvn install -N -DskipTests -Dcheckstyle.skip=true -q
echo "✓ 根 POM 安装成功"

# ============================================================
# 构建 webui
# ============================================================
echo ""
echo "--- [4/4] 构建 webui ---"
cd "$SCRIPT_DIR"

BUILD_START=$(date +%s)
mvn clean package -DskipTests -Dcheckstyle.skip=true
BUILD_END=$(date +%s)
BUILD_ELAPSED=$((BUILD_END - BUILD_START))

TARGET_JAR="$SCRIPT_DIR/target/webui-0.18.0-SNAPSHOT.jar"
if [ ! -f "$TARGET_JAR" ]; then
    echo "✗ 错误：未生成预期的 JAR 包：$TARGET_JAR"
    exit 1
fi

# 复制到 build/ 目录
OUTPUT_DIR="$SCRIPT_DIR/build"
mkdir -p "$OUTPUT_DIR"
OUTPUT_JAR="$OUTPUT_DIR/webui-0.18.0-SNAPSHOT.jar"
if [ -f "$OUTPUT_JAR" ]; then
    mv "$OUTPUT_JAR" "${OUTPUT_JAR}.old"
    echo "已备份旧包：${OUTPUT_JAR}.old"
fi
cp "$TARGET_JAR" "$OUTPUT_JAR"

echo ""
echo "$SEP"
echo "  构建成功！"
echo ""
echo "  JAR 包位置：$OUTPUT_JAR"
echo "  文件大小  ：$(ls -sh "$OUTPUT_JAR" | awk '{print $1}')"
echo "  构建耗时  ：${BUILD_ELAPSED} 秒"
echo ""
echo "  启动命令："
echo "    java -Xmx256m -jar $OUTPUT_JAR"
echo "  或使用一键部署脚本："
echo "    $PROJECT_ROOT/deploy.sh start"
echo "$SEP"
