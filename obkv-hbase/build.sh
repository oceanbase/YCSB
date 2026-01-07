#!/bin/bash

# YCSB Hbase 编译打包脚本
set -e  # 遇到错误时退出

echo "=========================================="
echo "开始编译打包 YCSB Hbase..."
echo "=========================================="

# 检查Java是否安装
if ! command -v java &> /dev/null; then
    echo "错误：Java未安装或不在PATH中"
    echo "请先安装Java JDK"
    exit 1
fi

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1-2)
echo "检测到Java版本：$JAVA_VERSION"

# 检查Maven是否安装
if ! command -v mvn &> /dev/null; then
    echo "错误：Maven未安装或不在PATH中"
    echo "请先安装Maven"
    exit 1
fi

# 检查Maven版本
MAVEN_VERSION=$(mvn -version 2>&1 | head -n 1 | cut -d' ' -f3)
echo "检测到Maven版本：$MAVEN_VERSION"

# 检查是否为Maven 3
MAVEN_MAJOR_VERSION=$(echo "$MAVEN_VERSION" | cut -d'.' -f1)
if [ "$MAVEN_MAJOR_VERSION" != "3" ]; then
    echo "错误：需要Maven 3.x版本，当前版本为：$MAVEN_VERSION"
    echo "请升级到Maven 3.x版本"
    exit 1
fi

# 检查命令行参数
if [ "$1" = "clean" ]; then
    echo "=========================================="
    echo "执行清理操作..."
    echo "=========================================="
    
    # 执行Maven清理
    echo "清理Maven构建产物..."
    mvn clean
    
    # 清理build目录
    BUILD_DIR="build"
    if [ -d "$BUILD_DIR" ]; then
        echo "清理build目录..."
        rm -rf "$BUILD_DIR"
        echo "build目录已清理"
    else
        echo "build目录不存在，无需清理"
    fi
    
    echo "清理完成！"
    exit 0
fi

# 检查当前目录是否包含pom.xml
if [ ! -f "pom.xml" ]; then
    echo "错误：当前目录不包含pom.xml文件"
    echo "请确保在YCSB项目根目录下运行此脚本"
    exit 1
fi

# 获取项目根目录
PROJECT_ROOT=$(pwd)
ROOT_DIR=".."
CORE_DIR="../core"

# 检查根目录是否存在pom.xml
if [ ! -f "$ROOT_DIR/pom.xml" ]; then
    echo "错误：找不到根目录的pom.xml文件：$ROOT_DIR/pom.xml"
    echo "请确保在正确的项目结构下运行此脚本"
    exit 1
fi

# 检查core模块是否存在
if [ ! -d "$CORE_DIR" ]; then
    echo "错误：找不到core模块目录：$CORE_DIR"
    echo "请确保在正确的项目结构下运行此脚本"
    exit 1
fi

# 先安装父POM（root pom.xml）
echo "=========================================="
echo "安装父POM..."
echo "=========================================="
cd "$ROOT_DIR"
ROOT_ABS_PATH=$(pwd)  # 保存根目录的绝对路径
echo "在根目录安装父POM..."
mvn install -N -DskipTests -Dcheckstyle.skip=true -Dmaven.test.skip=true
if [ $? -ne 0 ]; then
    echo "错误：父POM安装失败"
    exit 1
fi
echo "父POM安装成功！"

# 构建core模块
echo "=========================================="
echo "开始构建core模块..."
echo "=========================================="
cd "$ROOT_ABS_PATH/core"  # 在根目录下，core是子目录

# 检查core模块的pom.xml
if [ ! -f "pom.xml" ]; then
    echo "错误：core模块不包含pom.xml文件"
    exit 1
fi

# 构建core模块
echo "构建core模块..."
mvn clean install -DskipTests -Dcheckstyle.skip=true -Dmaven.test.skip=true

# 检查core模块构建是否成功
CORE_JAR="target/core-0.18.0-SNAPSHOT.jar"
if [ ! -f "$CORE_JAR" ]; then
    echo "错误：core模块构建失败，未找到jar包：$CORE_JAR"
    exit 1
fi

echo "core模块构建成功！"
echo "core jar包位置: $CORE_JAR"

# 回到当前模块目录
cd "$PROJECT_ROOT"

# 清理之前的构建
echo "=========================================="
echo "开始构建obkv-hbase模块..."
echo "=========================================="
echo "清理之前的构建..."
mvn clean

# 编译打包，跳过测试和checkstyle
echo "开始编译打包..."
mvn clean install -DskipTests -Dcheckstyle.skip=true -Dmaven.test.skip=true

# 检查生成的jar包
JAR_PATH="target/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
if [ -f "$JAR_PATH" ]; then
    echo "=========================================="
    echo "编译打包成功！"
    echo "生成的jar包位置：$JAR_PATH"
    echo "文件大小：$(ls -lh $JAR_PATH | awk '{print $5}')"
    echo "=========================================="
    
    # 创建输出目录
    OUTPUT_DIR="build"
    echo "创建输出目录：$OUTPUT_DIR"
    mkdir -p "$OUTPUT_DIR"
    
    # 复制jar包到输出目录
    OUTPUT_JAR="$OUTPUT_DIR/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
    
    # 如果目标文件已存在，先重命名为.old后缀
    if [ -f "$OUTPUT_JAR" ]; then
        OLD_JAR="${OUTPUT_JAR}.old"
        echo "发现同名文件，重命名为：$OLD_JAR"
        mv "$OUTPUT_JAR" "$OLD_JAR"
    fi
    
    echo "复制jar包到：$OUTPUT_JAR"
    cp "$JAR_PATH" "$OUTPUT_JAR"
    
    # 验证复制是否成功
    if [ -f "$OUTPUT_JAR" ]; then
        echo "=========================================="
        echo "Jar包复制成功！"
        echo "最终jar包位置：$OUTPUT_JAR"
        echo "文件大小：$(ls -lh $OUTPUT_JAR | awk '{print $5}')"
        echo "=========================================="
        
    else
        echo "错误：jar包复制失败"
        exit 1
    fi
    
else
    echo "=========================================="
    echo "错误：未找到生成的jar包"
    echo "请检查编译过程是否有错误"
    echo "=========================================="
    exit 1
fi

echo "构建完成！"
