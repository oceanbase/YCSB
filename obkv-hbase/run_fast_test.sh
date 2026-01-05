#!/bin/bash

# YCSB OBKV-HBASE 运行脚本
# 用途：运行YCSB测试，支持put、read、scan、load四种操作

set -e  # 遇到错误时退出

# 检查build目录是否存在
BUILD_DIR="build"
if [ ! -d "$BUILD_DIR" ]; then
    echo "错误：build目录不存在"
    echo "请先运行 ./build.sh 编译打包项目"
    exit 1
fi

# 检查jar包是否存在
JAR_FILE="$BUILD_DIR/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "错误：jar包不存在：$JAR_FILE"
    echo "请先运行 ./build.sh 编译打包项目"
    exit 1
fi

echo "=========================================="
echo "YCSB OBKV-HBASE 运行脚本"
echo "Jar包位置：$JAR_FILE"
echo "=========================================="

print_usage() {
    echo "用法：$0 {put|read|scan|load [workload_file]|batch_put|batch_read|run <workload_file>}"
    echo ""
    echo "操作说明："
    echo "  put        - 执行写入测试"
    echo "  read       - 执行读取测试"
    echo "  scan       - 执行扫描测试"
    echo "  load [workload_file] - 执行数据加载，可指定workload文件"
    echo "  batch_put  - 执行批量写入测试"
    echo "  batch_read - 执行批量读取测试"
    echo "  run <workload_file> - 使用指定的workload文件执行测试"
    echo ""
    echo "示例："
    echo "  $0 load                           # 交互式选择workload文件"
    echo "  $0 load workloads/my_workload     # 直接指定workload文件"
    echo "  $0 put                            # 运行put测试"
    echo "  $0 run /path/to/custom/workload"
}

# 解析全局选项（必须放在 OPERATION 之前）
while [[ $# -gt 0 ]]; do
    case "$1" in
        --table-mode)
            TABLE_MODE="${2:-}"
            shift 2
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            break
            ;;
    esac
done

# 检查Java是否可用
if ! command -v java &> /dev/null; then
    echo "错误：Java未安装或不在PATH中"
    echo "请先安装Java"
    exit 1
fi

# 显示Java版本
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1-2)
echo "检测到Java版本：$JAVA_VERSION"

# 检查命令行参数
if [ $# -eq 0 ]; then
    print_usage
    exit 1
fi

OPERATION="$1"

# 根据操作类型执行相应的测试
case "$OPERATION" in
    "put")
        WORKLOAD_FILE="workloads/workload_put"
        if [ ! -f "$WORKLOAD_FILE" ]; then
            echo "错误：workload文件不存在：$WORKLOAD_FILE"
            exit 1
        fi
        echo "=========================================="
        echo "执行写入测试..."
        echo "=========================================="
        java -jar "$JAR_FILE" -P "$WORKLOAD_FILE"
        ;;
    "read")
        WORKLOAD_FILE="workloads/workload_read"
        if [ ! -f "$WORKLOAD_FILE" ]; then
            echo "错误：workload文件不存在：$WORKLOAD_FILE"
            exit 1
        fi
        echo "=========================================="
        echo "执行读取测试..."
        echo "=========================================="
        java -jar "$JAR_FILE" -P "$WORKLOAD_FILE"
        ;;
    "scan")
        WORKLOAD_FILE="workloads/workload_scan"
        if [ ! -f "$WORKLOAD_FILE" ]; then
            echo "错误：workload文件不存在：$WORKLOAD_FILE"
            exit 1
        fi
        echo "=========================================="
        echo "执行扫描测试..."
        echo "=========================================="
        java -jar "$JAR_FILE" -P "$WORKLOAD_FILE"
        ;;
    "load")
        # 检查是否提供了workload文件参数
        if [ $# -ge 2 ]; then
            WORKLOAD_FILE="$2"
            if [ ! -f "$WORKLOAD_FILE" ]; then
                echo "错误：指定的workload文件不存在：$WORKLOAD_FILE"
                exit 1
            fi
            echo "=========================================="
            echo "使用指定的workload文件执行数据加载..."
            echo "Workload文件：$WORKLOAD_FILE"
            echo "=========================================="
            java -jar "$JAR_FILE" -P "$WORKLOAD_FILE" -load
        else
            # 交互式选择workload文件
            echo "=========================================="
            echo "请选择要加载的数据类型："
            echo "1) read       - 加载read测试数据"
            echo "2) batch_read - 加载batch_read测试数据"
            echo "3) scan       - 加载scan测试数据"
            echo "=========================================="
            read -p "请输入选择 (1/2/3): " load_choice
            
            case "$load_choice" in
                "1")
                    WORKLOAD_FILE="workloads/workload_read"
                    echo "选择：read"
                    ;;
                "2")
                    WORKLOAD_FILE="workloads/workload_batch_read"
                    echo "选择：batch_read"
                    ;;
                "3")
                    WORKLOAD_FILE="workloads/workload_scan"
                    echo "选择：scan"
                    ;;
                *)
                    echo "错误：无效的选择，请输入 1、2 或 3"
                    exit 1
                    ;;
            esac
            
            if [ ! -f "$WORKLOAD_FILE" ]; then
                echo "错误：workload文件不存在：$WORKLOAD_FILE"
                exit 1
            fi
            echo "=========================================="
            echo "执行数据加载..."
            echo "=========================================="
            java -jar "$JAR_FILE" -db "$DB_CLASS" -P "$WORKLOAD_FILE" -load
        fi
        ;;
    "batch_put")
        WORKLOAD_FILE="workloads/workload_batch_put"
        if [ ! -f "$WORKLOAD_FILE" ]; then
            echo "错误：workload文件不存在：$WORKLOAD_FILE"
            exit 1
        fi
        echo "=========================================="
        echo "执行批量写入测试..."
        echo "=========================================="
        java -jar "$JAR_FILE" -P "$WORKLOAD_FILE"
        ;;
    "batch_read")
        WORKLOAD_FILE="workloads/workload_batch_read"
        if [ ! -f "$WORKLOAD_FILE" ]; then
            echo "错误：workload文件不存在：$WORKLOAD_FILE"
            exit 1
        fi
        echo "=========================================="
        echo "执行批量读取测试..."
        echo "=========================================="
        java -jar "$JAR_FILE" -P "$WORKLOAD_FILE"
        ;;
    "run")
        if [ $# -lt 2 ]; then
            echo "错误：run选项需要指定workload文件路径"
            echo "用法：$0 run <workload_file>"
            exit 1
        fi
        WORKLOAD_FILE="$2"
        if [ ! -f "$WORKLOAD_FILE" ]; then
            echo "错误：指定的workload文件不存在：$WORKLOAD_FILE"
            exit 1
        fi
        echo "=========================================="
        echo "使用自定义workload文件执行测试..."
        echo "Workload文件：$WORKLOAD_FILE"
        echo "=========================================="
        java -jar "$JAR_FILE" -P "$WORKLOAD_FILE"
        ;;
    *)
        echo "错误：不支持的操作类型：$OPERATION"
        echo "支持的操作：put, read, scan, load, batch_put, batch_read, run"
        exit 1
        ;;
esac

echo "=========================================="
echo "测试完成！"
echo "=========================================="