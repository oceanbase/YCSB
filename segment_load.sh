#!/bin/bash
#
# OBKV-HBase 分段 Load 数据脚本
# 
# 支持两种连接模式：
#   1. ODP 模式：通过 ODP 代理连接
#   2. 直连模式：直接连接 OceanBase 集群
#
# 详细使用说明请参考: SEGMENT_LOAD_GUIDE.md
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
PID_FILE="${SCRIPT_DIR}/.segment_load.pids"

#######################################
# 参数定义 (无默认值的必须指定)
#######################################

# 数据参数 (必填)
TOTAL_RECORDS=""
SEGMENTS=""
THREADS=""
TABLE_NAME=""
COLUMN_FAMILY=""
MODEL_TYPE="hbase"  # 默认 hbase
CUSTOM_WORKLOAD=""

# 连接模式: odp 或 direct
CONN_MODE=""

# ODP 模式参数
ODP_IP=""
ODP_PORT=""
FULL_USER_NAME=""
PASSWORD=""
DATABASE=""

# 直连模式参数
PARAM_URL=""
SYS_USER_NAME=""
SYS_USER_PASSWORD=""
# FULL_USER_NAME 和 PASSWORD 与 ODP 模式共用

# 客户端参数
POOL_SIZE=100           # 连接池大小，每个分片100
SERVER_TIMEOUT=200000   # 服务端超时 200秒 = 200000ms
CLIENT_TIMEOUT=300000   # 客户端超时 300秒 = 300000ms
FIELD_COUNT=""          # 字段数
FIELD_LENGTH=""         # 字段长度
BATCH_PUT_SIZE=""       # Batch Put 大小

# Netty Buffer 参数 (可选)
NETTY_LOW_WATERMARK=""
NETTY_HIGH_WATERMARK=""

#######################################
# 颜色定义
#######################################
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

#######################################
# 显示帮助
#######################################
show_help() {
    cat << 'EOF'
OBKV-HBase 分段 Load 数据脚本

用法:
  ./segment_load.sh [选项] <总记录数> <分段数> <线程数> [workload文件]
  ./segment_load.sh stop     # 停止所有分段加载
  ./segment_load.sh status   # 查看运行状态

必填位置参数:
  <总记录数>                   要加载的总记录数 (必填)
  <分段数>                     分成多少段并行加载 (必填)
  <线程数>                     每段使用的线程数 (必填)
  [workload文件]               可选，自定义 workload 配置

连接模式 (必选其一):

  ODP 模式 (通过 ODP 代理):
    --odp                      使用 ODP 模式 (必填)
    --odp-ip <ip>              ODP 服务器 IP (必填)
    --odp-port <port>          ODP 服务器端口 (必填)
    --full-user <user>         完整用户名 user@tenant#cluster (必填)
    --password <pwd>           用户密码 (可选，可为空)
    --database <db>            数据库名 (必填)

  直连模式 (直接连接集群):
    --direct                   使用直连模式 (必填)
    --param-url <url>          ConfigServer URL (必填)
    --sys-user <user>          系统用户名 (必填)
    --sys-password <pwd>       系统用户密码 (可选，可为空)
    --full-user <user>         完整用户名 user@tenant#cluster (必填)
    --password <pwd>           用户密码 (可选，可为空)

必填公共选项:
    --table <name>             表名 (必填)
    --column-family <cf>       列族名 (HBase 模式必填，如: family)
    --model <type>             模型类型: hbase 或 table (默认: hbase)

可选公共选项:
    --pool-size <n>            连接池大小 (默认: 100)
    --field-count <n>          字段数 (HBase默认10, Table模式默认1)
    --field-length <n>         每个字段的长度 (字节, 默认100)
    --server-timeout <ms>      服务端超时 (默认: 200000, 即200秒)
    --client-timeout <ms>      客户端超时 (默认: 300000, 即300秒)
    --netty-low <bytes>        Netty 低水位
    --netty-high <bytes>       Netty 高水位
    --batch-put-size <n>       Batch Put 大小 (仅部分模型支持)
    -h, --help                 显示帮助

示例:

  # ODP 模式 (最小必填参数)
  ./segment_load.sh --odp \
    --odp-ip 192.168.1.100 \
    --odp-port 2883 \
    --full-user "testuser@testtenant#testcluster" \
    --password "mypassword" \
    --database "testdb" \
    --table "usertable" \
    --column-family "family" \
    100000000 10 32

  # 直连模式 (最小必填参数)
  ./segment_load.sh --direct \
    --param-url "http://192.168.1.100:8080/services" \
    --sys-user "root" \
    --sys-password "rootpwd" \
    --full-user "testuser@testtenant#testcluster" \
    --password "mypassword" \
    --table "usertable" \
    --column-family "family" \
    100000000 10 32

  # 带可选参数
  ./segment_load.sh --odp \
    --odp-ip 192.168.1.100 \
    --odp-port 2883 \
    --full-user "user@tenant#cluster" \
    --database "testdb" \
    --table "usertable" \
    --column-family "family" \
    --pool-size 200 \
    --server-timeout 300000 \
    --client-timeout 400000 \
    100000000 10 64

  # 停止加载
  ./segment_load.sh stop

详细文档: SEGMENT_LOAD_GUIDE.md
EOF
}

#######################################
# 停止所有分段加载进程
#######################################
stop_segment_load() {
    echo ""
    echo -e "${BLUE}▶${NC} 停止分段加载进程..."
    echo ""
    
    local stopped=0
    
    # 1. 从 PID 文件停止
    if [ -f "$PID_FILE" ]; then
        log_info "从 PID 文件读取进程列表..."
        while read pid; do
            if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
                echo "  停止进程 $pid..."
                kill -9 "$pid" 2>/dev/null || true
                stopped=$((stopped + 1))
            fi
        done < "$PID_FILE"
        rm -f "$PID_FILE"
    fi
    
    # 2. 查找并停止所有 YCSB load 进程
    local ycsb_pids=$(pgrep -f "obkv-hbase.*-load" 2>/dev/null || true)
    if [ -n "$ycsb_pids" ]; then
        log_info "发现遗留的 YCSB load 进程..."
        for pid in $ycsb_pids; do
            echo "  停止进程 $pid..."
            kill -9 "$pid" 2>/dev/null || true
            stopped=$((stopped + 1))
        done
    fi
    
    echo ""
    if [ $stopped -gt 0 ]; then
        log_info "✓ 已停止 $stopped 个进程"
    else
        log_info "没有正在运行的分段加载进程"
    fi
    echo ""
    
    exit 0
}

#######################################
# 查看状态
#######################################
show_status() {
    echo ""
    echo -e "${BLUE}▶${NC} 分段加载状态"
    echo ""
    
    # 从 PID 文件检查
    if [ -f "$PID_FILE" ]; then
        local running=0
        local stopped=0
        
        echo "  PID 文件中的进程:"
        while read pid; do
            if [ -n "$pid" ]; then
                if kill -0 "$pid" 2>/dev/null; then
                    echo -e "    PID $pid: ${GREEN}运行中${NC}"
                    running=$((running + 1))
                else
                    echo -e "    PID $pid: ${YELLOW}已结束${NC}"
                    stopped=$((stopped + 1))
                fi
            fi
        done < "$PID_FILE"
        
        echo ""
        echo "  运行中: $running, 已结束: $stopped"
    else
        echo "  没有正在运行的分段加载任务"
    fi
    
    # 查找 YCSB 进程
    local ycsb_pids=$(pgrep -f "obkv-hbase.*-load" 2>/dev/null || true)
    if [ -n "$ycsb_pids" ]; then
        echo ""
        echo "  发现 YCSB load 进程:"
        for pid in $ycsb_pids; do
            echo -e "    PID $pid: ${GREEN}运行中${NC}"
        done
    fi
    
    echo ""
    exit 0
}

#######################################
# 检查特殊命令
#######################################
if [ "$1" == "stop" ]; then
    stop_segment_load
fi

if [ "$1" == "status" ]; then
    show_status
fi

if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    show_help
    exit 0
fi

#######################################
# 解析选项参数
#######################################
POSITIONAL_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        # 连接模式
        --odp)
            CONN_MODE="odp"
            shift
            ;;
        --direct)
            CONN_MODE="direct"
            shift
            ;;
        # ODP 模式参数
        --odp-ip)
            ODP_IP="$2"
            shift 2
            ;;
        --odp-port)
            ODP_PORT="$2"
            shift 2
            ;;
        # 直连模式参数
        --param-url)
            PARAM_URL="$2"
            shift 2
            ;;
        --sys-user)
            SYS_USER_NAME="$2"
            shift 2
            ;;
        --sys-password)
            SYS_USER_PASSWORD="$2"
            shift 2
            ;;
        # 公共参数
        --full-user)
            FULL_USER_NAME="$2"
            shift 2
            ;;
        --password)
            PASSWORD="$2"
            shift 2
            ;;
        --database)
            DATABASE="$2"
            shift 2
            ;;
        --table)
            TABLE_NAME="$2"
            shift 2
            ;;
        --column-family)
            COLUMN_FAMILY="$2"
            shift 2
            ;;
        --model)
            MODEL_TYPE="$2"
            shift 2
            ;;
        # 客户端参数
        --pool-size)
            POOL_SIZE="$2"
            shift 2
            ;;
        --field-count)
            FIELD_COUNT="$2"
            shift 2
            ;;
        --field-length)
            FIELD_LENGTH="$2"
            shift 2
            ;;
        --server-timeout)
            SERVER_TIMEOUT="$2"
            shift 2
            ;;
        --client-timeout)
            CLIENT_TIMEOUT="$2"
            shift 2
            ;;
        # Netty 参数
        --netty-low)
            NETTY_LOW_WATERMARK="$2"
            shift 2
            ;;
        --netty-high)
            NETTY_HIGH_WATERMARK="$2"
            shift 2
            ;;
        --batch-put-size)
            BATCH_PUT_SIZE="$2"
            shift 2
            ;;
        # 帮助
        -h|--help)
            show_help
            exit 0
            ;;
        # 未知选项
        -*)
            log_error "未知选项: $1"
            echo "使用 --help 查看帮助"
            exit 1
            ;;
        # 位置参数
        *)
            POSITIONAL_ARGS+=("$1")
            shift
            ;;
    esac
done

# 恢复位置参数
set -- "${POSITIONAL_ARGS[@]}"

# 解析位置参数 (不提供默认值，必须指定)
TOTAL_RECORDS=${1:-}
SEGMENTS=${2:-}
THREADS=${3:-}
CUSTOM_WORKLOAD=${4:-}

#######################################
# 验证参数
#######################################
validate_params() {
    local errors=0
    
    # 验证必填位置参数
    if [ -z "$TOTAL_RECORDS" ]; then
        log_error "必须指定总记录数 (第1个位置参数)"
        errors=$((errors + 1))
    fi
    if [ -z "$SEGMENTS" ]; then
        log_error "必须指定分段数 (第2个位置参数)"
        errors=$((errors + 1))
    fi
    if [ -z "$THREADS" ]; then
        log_error "必须指定线程数 (第3个位置参数)"
        errors=$((errors + 1))
    fi
    if [ -z "$TABLE_NAME" ]; then
        log_error "必须指定表名 (--table 参数)"
        errors=$((errors + 1))
    fi
    if [ "$MODEL_TYPE" == "hbase" ] && [ -z "$COLUMN_FAMILY" ]; then
        log_error "HBase 模式必须指定列族名 (--column-family 参数)"
        errors=$((errors + 1))
    fi
    
    # 验证连接模式
    if [ -z "$CONN_MODE" ]; then
        log_error "必须指定连接模式: --odp 或 --direct"
        errors=$((errors + 1))
    fi
    
    # 验证 ODP 模式参数
    if [ "$CONN_MODE" == "odp" ]; then
        if [ -z "$ODP_IP" ]; then
            log_error "ODP 模式需要 --odp-ip 参数"
            errors=$((errors + 1))
        fi
        if [ -z "$ODP_PORT" ]; then
            log_error "ODP 模式需要 --odp-port 参数"
            errors=$((errors + 1))
        fi
        if [ -z "$FULL_USER_NAME" ]; then
            log_error "ODP 模式需要 --full-user 参数"
            errors=$((errors + 1))
        fi
        if [ -z "$DATABASE" ]; then
            log_error "ODP 模式需要 --database 参数"
            errors=$((errors + 1))
        fi
    fi
    
    # 验证直连模式参数
    if [ "$CONN_MODE" == "direct" ]; then
        if [ -z "$PARAM_URL" ]; then
            log_error "直连模式需要 --param-url 参数"
            errors=$((errors + 1))
        fi
        if [ -z "$SYS_USER_NAME" ]; then
            log_error "直连模式需要 --sys-user 参数"
            errors=$((errors + 1))
        fi
        if [ -z "$FULL_USER_NAME" ]; then
            log_error "直连模式需要 --full-user 参数"
            errors=$((errors + 1))
        fi
    fi
    
    if [ $errors -gt 0 ]; then
        echo ""
        echo "使用 --help 查看帮助"
        exit 1
    fi
}

validate_params

#######################################
# 查找 YCSB JAR
#######################################
find_ycsb_jar() {
    local jar=""
    # 优先 obkv-hbase/target 目录
    jar=$(find "${PROJECT_ROOT}/obkv-hbase/target" -maxdepth 1 -name "*-jar-with-dependencies.jar" 2>/dev/null | grep -v "/\._" | head -n 1)
    if [ -n "$jar" ] && [ -f "$jar" ]; then
        echo "$jar"
        return
    fi
    # 其次 target 目录 (部署包环境)
    jar=$(find "${PROJECT_ROOT}/target" -maxdepth 1 -name "*-jar-with-dependencies.jar" 2>/dev/null | grep -v "/\._" | head -n 1)
    if [ -n "$jar" ] && [ -f "$jar" ]; then
        echo "$jar"
        return
    fi
    echo ""
}

YCSB_JAR=$(find_ycsb_jar)

# 检查 JAR 文件
if [ -z "$YCSB_JAR" ] || [ ! -f "$YCSB_JAR" ]; then
    log_error "未找到 YCSB JAR 文件"
    log_info "请先编译项目: mvn clean package -DskipTests"
    exit 1
fi

# 设置 Workload 文件
if [ -n "$CUSTOM_WORKLOAD" ]; then
    WORKLOAD="$CUSTOM_WORKLOAD"
else
    WORKLOAD="${SCRIPT_DIR}/workloads/workloads_a_f/workloada"
fi

# 检查 Workload 文件
if [ ! -f "$WORKLOAD" ]; then
    log_error "Workload 文件不存在: $WORKLOAD"
    exit 1
fi

LOG_DIR="${SCRIPT_DIR}/result/segment_load_$(date +%Y%m%d_%H%M%S)"

# 创建日志目录
mkdir -p "$LOG_DIR"

# 计算每段记录数
BATCH=$((TOTAL_RECORDS / SEGMENTS))

#######################################
# 构建连接参数
#######################################
# 构建连接参数数组 (避免 URL 中特殊字符被 shell 解释)
# 参数名使用 hbase.oceanbase.* 格式 (与 OHConstants 一致)
build_conn_args() {
    CONN_ARGS=()
    
    if [ "$MODEL_TYPE" == "table" ]; then
        # Table 模式参数
        if [ "$CONN_MODE" == "odp" ]; then
            CONN_ARGS+=("-p" "obkv.isOdpMode=true")
            CONN_ARGS+=("-p" "obkv.odpAddr=$ODP_IP")
            CONN_ARGS+=("-p" "obkv.odpPort=$ODP_PORT")
            CONN_ARGS+=("-p" "obkv.fullUserName=$FULL_USER_NAME")
            CONN_ARGS+=("-p" "obkv.password=$PASSWORD")
            CONN_ARGS+=("-p" "obkv.database=$DATABASE")
        else
            CONN_ARGS+=("-p" "obkv.isOdpMode=false")
            CONN_ARGS+=("-p" "obkv.configUrl=$PARAM_URL")
            CONN_ARGS+=("-p" "obkv.sysUserName=$SYS_USER_NAME")
            CONN_ARGS+=("-p" "obkv.sysPassword=$SYS_USER_PASSWORD")
            CONN_ARGS+=("-p" "obkv.fullUserName=$FULL_USER_NAME")
            CONN_ARGS+=("-p" "obkv.password=$PASSWORD")
        fi
        CONN_ARGS+=("-p" "table=$TABLE_NAME")
        if [ -n "$FIELD_COUNT" ]; then
            CONN_ARGS+=("-p" "fieldcount=$FIELD_COUNT")
        else
            CONN_ARGS+=("-p" "fieldcount=1") # Table模式默认1列
        fi
    else
        # HBase 模式参数
        if [ "$CONN_MODE" == "odp" ]; then
            CONN_ARGS+=("-p" "hbase.oceanbase.odpMode=true")
            CONN_ARGS+=("-p" "hbase.oceanbase.odpAddr=$ODP_IP")
            CONN_ARGS+=("-p" "hbase.oceanbase.odpPort=$ODP_PORT")
            CONN_ARGS+=("-p" "hbase.oceanbase.fullUserName=$FULL_USER_NAME")
            CONN_ARGS+=("-p" "hbase.oceanbase.password=$PASSWORD")
            CONN_ARGS+=("-p" "hbase.oceanbase.database=$DATABASE")
        else
            CONN_ARGS+=("-p" "hbase.oceanbase.odpMode=false")
            CONN_ARGS+=("-p" "hbase.oceanbase.paramURL=$PARAM_URL")
            CONN_ARGS+=("-p" "hbase.oceanbase.sysUserName=$SYS_USER_NAME")
            CONN_ARGS+=("-p" "hbase.oceanbase.sysPassword=$SYS_USER_PASSWORD")
            CONN_ARGS+=("-p" "hbase.oceanbase.fullUserName=$FULL_USER_NAME")
            CONN_ARGS+=("-p" "hbase.oceanbase.password=$PASSWORD")
        fi
        CONN_ARGS+=("-p" "hbase.oceanbase.table=$TABLE_NAME")
        CONN_ARGS+=("-p" "hbase.oceanbase.columnFamily=$COLUMN_FAMILY")
        if [ -n "$FIELD_COUNT" ]; then
            CONN_ARGS+=("-p" "fieldcount=$FIELD_COUNT")
        fi
    fi
    
    if [ -n "$FIELD_LENGTH" ]; then
        CONN_ARGS+=("-p" "fieldlength=$FIELD_LENGTH")
    fi
    
    # 负载公共参数 (YCSB 原生支持，不需要前缀)
    CONN_ARGS+=("-p" "server.connection.pool.size=$POOL_SIZE")
    CONN_ARGS+=("-p" "rpc.operation.timeout=$SERVER_TIMEOUT")
    CONN_ARGS+=("-p" "rpc.execute.timeout=$CLIENT_TIMEOUT")
    
    # Netty Buffer (可选)
    if [ -n "$NETTY_LOW_WATERMARK" ]; then
        CONN_ARGS+=("-p" "bolt.netty.buffer.low.watermark=$NETTY_LOW_WATERMARK")
    fi
    if [ -n "$NETTY_HIGH_WATERMARK" ]; then
        CONN_ARGS+=("-p" "bolt.netty.buffer.high.watermark=$NETTY_HIGH_WATERMARK")
    fi
    if [ -n "$BATCH_PUT_SIZE" ]; then
        CONN_ARGS+=("-p" "batchput.size.per.op=$BATCH_PUT_SIZE")
    fi
}

build_conn_args

#######################################
# 显示配置信息
#######################################
echo ""
echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║${NC}           ${GREEN}OBKV-HBase 分段 Load 数据${NC}                        ${CYAN}║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "  数据配置:"
echo "  ────────────────────────────────────────"
echo "  总记录数:     $(printf "%'d" $TOTAL_RECORDS)"
echo "  分段数:       $SEGMENTS"
echo "  每段记录数:   $(printf "%'d" $BATCH)"
echo "  线程数:       $THREADS"
echo "  模型类型:     $MODEL_TYPE"
echo "  表名:         $TABLE_NAME"
if [ "$MODEL_TYPE" == "hbase" ]; then
    echo "  列族:         $COLUMN_FAMILY"
fi
echo "  Workload:     $(basename $WORKLOAD)"
if [ -n "$FIELD_COUNT" ]; then
    echo "  字段数:       $FIELD_COUNT"
fi
if [ -n "$FIELD_LENGTH" ]; then
    echo "  字段长度:     $FIELD_LENGTH"
fi
echo ""
echo "  连接配置 (${CONN_MODE^^} 模式):"
echo "  ────────────────────────────────────────"
if [ "$CONN_MODE" == "odp" ]; then
    echo "  ODP 地址:     $ODP_IP:$ODP_PORT"
    echo "  用户名:       $FULL_USER_NAME"
    echo "  数据库:       $DATABASE"
else
    echo "  Param URL:    $PARAM_URL"
    echo "  系统用户:     $SYS_USER_NAME"
    echo "  用户名:       $FULL_USER_NAME"
fi
echo ""
echo "  客户端配置:"
echo "  ────────────────────────────────────────"
echo "  连接池大小:   $POOL_SIZE"
echo "  服务端超时:   $SERVER_TIMEOUT ms ($(echo "scale=0; $SERVER_TIMEOUT/1000" | bc)秒)"
echo "  客户端超时:   $CLIENT_TIMEOUT ms ($(echo "scale=0; $CLIENT_TIMEOUT/1000" | bc)秒)"
if [ -n "$NETTY_LOW_WATERMARK" ]; then
    echo "  Netty 低水位: $NETTY_LOW_WATERMARK"
fi
if [ -n "$NETTY_HIGH_WATERMARK" ]; then
    echo "  Netty 高水位: $NETTY_HIGH_WATERMARK"
fi
if [ -n "$BATCH_PUT_SIZE" ]; then
    echo "  Batch Put:    $BATCH_PUT_SIZE"
fi
echo ""
echo "  输出配置:"
echo "  ────────────────────────────────────────"
echo "  YCSB JAR:     $(basename $YCSB_JAR)"
echo "  日志目录:     $LOG_DIR"
echo "  ────────────────────────────────────────"
echo ""

# 确认执行
read -p "确认开始加载? (Y/n): " confirm
if [ "$confirm" == "n" ] || [ "$confirm" == "N" ]; then
    echo "已取消"
    exit 0
fi

echo ""
log_info "开始分段加载..."
echo ""

#######################################
# 启动分段加载
#######################################
PIDS=""
START_TIME=$(date +%s)

# 清空 PID 文件
> "$PID_FILE"

for i in $(seq 0 $((SEGMENTS - 1))); do
    START=$((i * BATCH))
    
    # 最后一段处理余数
    if [ $i -eq $((SEGMENTS - 1)) ]; then
        COUNT=$((TOTAL_RECORDS - START))
    else
        COUNT=$BATCH
    fi
    
    LOG_FILE="${LOG_DIR}/segment_${i}.log"
    
    echo -e "  ${GREEN}▶${NC} 分段 $i: insertstart=$(printf "%'d" $START), insertcount=$(printf "%'d" $COUNT)"
    
    # 根据模型选择 DB 类
    DB_CLASS="com.oceanbase.obkv.ycsb.OBHBaseClient"
    if [ "$MODEL_TYPE" == "table" ]; then
        DB_CLASS="com.oceanbase.obkv.table.ycsb.ObTableClientDB"
    fi

    # 构建命令参数数组
    CMD_ARGS=(
        "java" "-jar" "$YCSB_JAR"
        "-db" "$DB_CLASS"
        "-P" "$WORKLOAD"
        "-p" "recordcount=$TOTAL_RECORDS"
        "-p" "insertstart=$START"
        "-p" "insertcount=$COUNT"
        "-p" "threadcount=$THREADS"
        "${CONN_ARGS[@]}"
        "-load"
    )
    
    # 记录命令到日志 (用于调试)
    echo "Command: ${CMD_ARGS[*]}" > "$LOG_FILE"
    echo "Started at: $(date)" >> "$LOG_FILE"
    echo "========================================" >> "$LOG_FILE"
    
    # 执行 (使用数组方式，避免特殊字符被 shell 解释)
    "${CMD_ARGS[@]}" >> "$LOG_FILE" 2>&1 &
    
    PID=$!
    PIDS="$PIDS $PID"
    echo "$PID" >> "$PID_FILE"
done

echo ""
log_info "所有分段已启动 (共 $SEGMENTS 个进程)"
log_info "停止命令: ./segment_load.sh stop"
echo ""

#######################################
# 等待所有任务完成
#######################################
FAILED=0
for pid in $PIDS; do
    if ! wait $pid; then
        FAILED=$((FAILED + 1))
    fi
done

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "  ════════════════════════════════════════"

if [ $FAILED -eq 0 ]; then
    echo -e "  ${GREEN}✓ 所有分段加载完成！${NC}"
else
    echo -e "  ${RED}✗ $FAILED 个分段失败${NC}"
fi

echo "  执行时间: ${DURATION} 秒"
echo "  日志目录: $LOG_DIR"
echo "  ════════════════════════════════════════"
echo ""

#######################################
# 汇总统计
#######################################
echo "  各分段结果汇总:"
echo "  ────────────────────────────────────────"

TOTAL_OPS=0
SEGMENT_ERRORS=0
for i in $(seq 0 $((SEGMENTS - 1))); do
    LOG_FILE="${LOG_DIR}/segment_${i}.log"
    if [ -f "$LOG_FILE" ]; then
        THROUGHPUT=$(grep -oP '\[OVERALL\], Throughput\(ops/sec\), \K[0-9.]+' "$LOG_FILE" 2>/dev/null || echo "0")
        RUNTIME=$(grep -oP '\[OVERALL\], RunTime\(ms\), \K[0-9]+' "$LOG_FILE" 2>/dev/null || echo "0")
        
        # 检查是否有异常 (Exception, Error, DBException)
        HAS_ERROR=$(grep -c "Exception\|Error\|FAILED" "$LOG_FILE" 2>/dev/null || echo "0")
        
        if [ "$HAS_ERROR" -gt 0 ]; then
            # 有异常，提取错误信息
            ERROR_MSG=$(grep -m1 "Exception:\|Error:\|DBException" "$LOG_FILE" 2>/dev/null | head -c 80)
            echo -e "  分段 $i: ${RED}失败${NC} - $ERROR_MSG"
            echo -e "           ${YELLOW}日志: $LOG_FILE${NC}"
            SEGMENT_ERRORS=$((SEGMENT_ERRORS + 1))
        elif [ "$THROUGHPUT" != "0" ] && [ "$THROUGHPUT" != "0.0" ]; then
            TOTAL_OPS=$(echo "$TOTAL_OPS + $THROUGHPUT" | bc 2>/dev/null || echo "0")
            printf "  分段 %d: ${GREEN}成功${NC} - Throughput = %s ops/sec, Runtime = %s ms\n" $i "$THROUGHPUT" "$RUNTIME"
        else
            # Throughput 为 0，可能有问题
            echo -e "  分段 $i: ${YELLOW}警告${NC} - Throughput = 0 (可能有错误)"
            echo -e "           ${YELLOW}日志: $LOG_FILE${NC}"
            SEGMENT_ERRORS=$((SEGMENT_ERRORS + 1))
        fi
    else
        echo -e "  分段 $i: ${RED}失败${NC} - 日志文件不存在"
        SEGMENT_ERRORS=$((SEGMENT_ERRORS + 1))
    fi
done

echo "  ────────────────────────────────────────"
if [ $SEGMENT_ERRORS -gt 0 ]; then
    echo -e "  ${RED}✗ $SEGMENT_ERRORS 个分段有错误！${NC}"
    echo -e "  ${YELLOW}请查看日志排查问题: tail -100 $LOG_DIR/segment_*.log${NC}"
else
    echo -e "  ${GREEN}✓ 所有分段成功！${NC}"
fi
echo -e "  ${GREEN}总吞吐量: $(printf "%.2f" $TOTAL_OPS) ops/sec${NC}"
echo ""

# 清理 PID 文件
rm -f "$PID_FILE"

# 如果有错误，返回非零退出码
if [ $SEGMENT_ERRORS -gt 0 ]; then
    exit 1
fi
