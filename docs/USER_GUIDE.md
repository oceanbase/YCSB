# OBKV-HBase 压测控制台使用指南

## 目录

1. [概述](#概述)
2. [快速开始](#快速开始)
3. [Web 控制台使用](#web-控制台使用)
4. [命令行使用](#命令行使用)
5. [分段 Load 数据](#分段-load-数据)
6. [Workload 配置说明](#workload-配置说明)
7. [常见问题](#常见问题)

---

## 概述

OBKV-HBase 压测控制台是一个基于 YCSB 的 OceanBase OBKV-HBase 性能测试工具，提供：
- **Web 界面**：可视化配置和执行测试
- **命令行**：灵活的黑屏操作方式

## 快速开始

### 1. 启动 Web 控制台

```bash
cd obkv-hbase
./start.sh
```

访问：http://localhost:8081

### 2. 停止服务

```bash
./stop.sh
```

---

## Web 控制台使用

Web 控制台提供以下功能：

1. **SQL 连接配置** - 用于建表等 DDL 操作
2. **DDL 操作** - 创建/清空/删除测试表
3. **OBKV 连接配置** - 配置 OBKV 客户端连接
4. **客户端参数** - 配置线程数、连接池等
5. **Workload 控制** - 选择并执行 Load/Run 测试
6. **实时日志** - 查看测试执行日志

---

## 命令行使用

### 环境变量

```bash
# 设置 YCSB JAR 路径
export YCSB_JAR="target/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"

# 或者使用 build 目录
export YCSB_JAR="build/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
```

### 基本命令格式

```bash
java -jar $YCSB_JAR -P <workload_file> [-load | -t]
```

参数说明：
- `-P <workload_file>`: 指定 workload 配置文件
- `-load`: 执行数据加载（Load 阶段）
- `-t`: 执行事务测试（Run 阶段，默认）

### 常用命令示例

```bash
# 加载数据
java -jar $YCSB_JAR -P workloads/workloads_a_f/workloada -load

# 运行测试
java -jar $YCSB_JAR -P workloads/workloads_a_f/workloada -t

# 使用命令行参数覆盖配置
java -jar $YCSB_JAR -P workloads/workloads_a_f/workloada \
  -p recordcount=1000000 \
  -p operationcount=100000 \
  -p threadcount=32 \
  -load
```

---

## 分段 Load 数据

当需要加载大量数据时，可以分段并行加载以提高效率。

### 核心参数

| 参数 | 说明 | 示例 |
|------|------|------|
| `recordcount` | 总记录数 | 100000000 |
| `insertstart` | 起始 key 编号 | 0 |
| `insertcount` | 本次插入的记录数 | 10000000 |

### 单机分段 Load 示例

假设需要加载 1 亿条数据，分 10 段加载：

```bash
# 设置 JAR 路径
YCSB_JAR="target/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
WORKLOAD="workloads/workloads_a_f/workloada"

# 总记录数
TOTAL=100000000
# 每段记录数
BATCH=10000000

# 分段加载（后台执行）
for i in $(seq 0 9); do
  START=$((i * BATCH))
  echo "启动分段 $i: insertstart=$START, insertcount=$BATCH"
  
  java -jar $YCSB_JAR -P $WORKLOAD \
    -p recordcount=$TOTAL \
    -p insertstart=$START \
    -p insertcount=$BATCH \
    -p threadcount=32 \
    -load > load_segment_${i}.log 2>&1 &
done

# 等待所有任务完成
wait
echo "所有分段加载完成"
```

### 多机分段 Load 示例

在多台机器上并行加载数据：

**机器 1 (加载 0-25%)：**
```bash
java -jar $YCSB_JAR -P $WORKLOAD \
  -p recordcount=100000000 \
  -p insertstart=0 \
  -p insertcount=25000000 \
  -p threadcount=64 \
  -load
```

**机器 2 (加载 25%-50%)：**
```bash
java -jar $YCSB_JAR -P $WORKLOAD \
  -p recordcount=100000000 \
  -p insertstart=25000000 \
  -p insertcount=25000000 \
  -p threadcount=64 \
  -load
```

**机器 3 (加载 50%-75%)：**
```bash
java -jar $YCSB_JAR -P $WORKLOAD \
  -p recordcount=100000000 \
  -p insertstart=50000000 \
  -p insertcount=25000000 \
  -p threadcount=64 \
  -load
```

**机器 4 (加载 75%-100%)：**
```bash
java -jar $YCSB_JAR -P $WORKLOAD \
  -p recordcount=100000000 \
  -p insertstart=75000000 \
  -p insertcount=25000000 \
  -p threadcount=64 \
  -load
```

### 分段 Load 脚本 (segment_load.sh)

创建一个通用的分段加载脚本：

```bash
#!/bin/bash
#
# 分段 Load 数据脚本
# 用法: ./segment_load.sh <总记录数> <分段数> <线程数>
#
# 示例: ./segment_load.sh 100000000 10 32

set -e

TOTAL_RECORDS=${1:-10000000}
SEGMENTS=${2:-4}
THREADS=${3:-32}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YCSB_JAR="${SCRIPT_DIR}/target/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
WORKLOAD="${SCRIPT_DIR}/workloads/workloads_a_f/workloada"
LOG_DIR="${SCRIPT_DIR}/result/segment_load_$(date +%Y%m%d_%H%M%S)"

# 检查 JAR 文件
if [ ! -f "$YCSB_JAR" ]; then
    YCSB_JAR="${SCRIPT_DIR}/build/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar"
fi

if [ ! -f "$YCSB_JAR" ]; then
    echo "错误: 未找到 YCSB JAR 文件"
    exit 1
fi

# 创建日志目录
mkdir -p "$LOG_DIR"

# 计算每段记录数
BATCH=$((TOTAL_RECORDS / SEGMENTS))

echo "=========================================="
echo "分段 Load 配置"
echo "=========================================="
echo "总记录数:     $TOTAL_RECORDS"
echo "分段数:       $SEGMENTS"
echo "每段记录数:   $BATCH"
echo "线程数:       $THREADS"
echo "日志目录:     $LOG_DIR"
echo "=========================================="
echo ""

# 启动分段加载
PIDS=""
for i in $(seq 0 $((SEGMENTS - 1))); do
    START=$((i * BATCH))
    
    # 最后一段处理余数
    if [ $i -eq $((SEGMENTS - 1)) ]; then
        COUNT=$((TOTAL_RECORDS - START))
    else
        COUNT=$BATCH
    fi
    
    LOG_FILE="${LOG_DIR}/segment_${i}.log"
    
    echo "启动分段 $i: insertstart=$START, insertcount=$COUNT"
    
    java -jar "$YCSB_JAR" -P "$WORKLOAD" \
        -p recordcount=$TOTAL_RECORDS \
        -p insertstart=$START \
        -p insertcount=$COUNT \
        -p threadcount=$THREADS \
        -load > "$LOG_FILE" 2>&1 &
    
    PIDS="$PIDS $!"
done

echo ""
echo "所有分段已启动，等待完成..."
echo "PID 列表: $PIDS"
echo ""

# 等待所有任务完成
FAILED=0
for pid in $PIDS; do
    if ! wait $pid; then
        echo "分段 (PID: $pid) 失败"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
echo "=========================================="
if [ $FAILED -eq 0 ]; then
    echo "✓ 所有分段加载完成！"
else
    echo "✗ $FAILED 个分段失败"
fi
echo "日志目录: $LOG_DIR"
echo "=========================================="

# 汇总统计
echo ""
echo "各分段结果汇总:"
for i in $(seq 0 $((SEGMENTS - 1))); do
    LOG_FILE="${LOG_DIR}/segment_${i}.log"
    if [ -f "$LOG_FILE" ]; then
        THROUGHPUT=$(grep -oP '\[OVERALL\], Throughput\(ops/sec\), \K[0-9.]+' "$LOG_FILE" 2>/dev/null || echo "N/A")
        echo "  分段 $i: Throughput = $THROUGHPUT ops/sec"
    fi
done
```

### 使用分段加载脚本

```bash
# 加载 1 亿条数据，分 10 段，每段 32 线程
./segment_load.sh 100000000 10 32

# 加载 1000 万条数据，分 4 段，每段 64 线程  
./segment_load.sh 10000000 4 64
```

---

## Workload 配置说明

### Workload 文件位置

```
workloads/
├── workloads_a_f/          # 标准 YCSB Workload A-F
│   ├── workloada           # 50% 读 / 50% 更新
│   ├── workloadb           # 95% 读 / 5% 更新
│   ├── workloadc           # 100% 读
│   ├── workloadd           # 95% 读 / 5% 插入 (最新数据)
│   ├── workloade           # 95% 扫描 / 5% 插入
│   └── workloadf           # 50% 读-修改-写 / 50% 读
├── workload_load           # 数据加载配置
├── workload_put            # 纯写入测试
├── workload_read           # 纯读取测试
└── workload_scan           # 扫描测试
```

### 关键配置参数

```properties
# 数据量
recordcount=1000000        # 总记录数
operationcount=100000      # 操作次数

# 客户端配置
threadcount=32             # 客户端线程数

# 操作比例 (总和为 1)
readproportion=0.5         # 读操作比例
updateproportion=0.5       # 更新操作比例
scanproportion=0           # 扫描操作比例
insertproportion=0         # 插入操作比例

# 数据分布
requestdistribution=zipfian   # 请求分布 (uniform/zipfian/latest/sequential/hotspot)

# 时间限制
maxexecutiontime=600       # 最大执行时间(秒)

# 数据格式
fieldcount=10              # 每条记录的字段数
fieldlength=100            # 每个字段的长度(字节)

# 分段加载
insertstart=0              # 起始 key 编号
insertcount=1000000        # 本次插入记录数

# OBKV 连接
obkv.full.user.name=user@tenant#cluster
obkv.param.url=http://xxx:8080/services
obkv.password=xxx
obkv.sys.user.name=root
obkv.sys.password=xxx
table=test_table
```

### 数据分布类型

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| `uniform` | 均匀随机分布 | 负载均衡测试 |
| `zipfian` | Zipfian 热点分布 | 模拟真实热点访问 |
| `latest` | 最新数据优先 | 时序数据场景 |
| `sequential` | 顺序访问 | 范围扫描测试 |
| `hotspot` | 热点区域 | 极端热点场景 |

---

## 常见问题

### 1. 如何查看测试结果？

```bash
# 查看最新日志
ls -lt result/

# 查看日志内容
cat result/xxx.log

# 提取吞吐量
grep "Throughput" result/xxx.log
```

### 2. 如何调整性能？

```bash
# 增加线程数
-p threadcount=128

# 增加连接池
-p obkv.rpc.connect.pool.size=100

# 调整超时时间
-p obkv.rpc.execute.timeout=10000
```

### 3. 如何监控进度？

```bash
# 实时查看日志
tail -f result/segment_load_xxx/segment_0.log

# 查看进程状态
ps aux | grep ycsb
```

### 4. 如何停止正在运行的测试？

```bash
# 停止所有 YCSB 进程
pkill -f "obkv-hbase.*jar"

# 或者使用 Web 控制台的停止按钮
```

### 5. Load 和 Run 的区别？

- **Load (-load)**: 插入初始数据，只有 INSERT 操作
- **Run (-t)**: 执行混合操作测试（读/写/更新/扫描）

---

## 附录：完整命令行示例

### 场景 1：快速功能验证

```bash
# 加载 1000 条数据
java -jar $YCSB_JAR -P workloads/workloads_a_f/workloada \
  -p recordcount=1000 \
  -p threadcount=4 \
  -load

# 运行 1000 次操作
java -jar $YCSB_JAR -P workloads/workloads_a_f/workloada \
  -p recordcount=1000 \
  -p operationcount=1000 \
  -p threadcount=4 \
  -t
```

### 场景 2：大规模压测

```bash
# 加载 1 亿条数据（分 10 段）
./segment_load.sh 100000000 10 64

# 运行 30 分钟压测
java -jar $YCSB_JAR -P workloads/workloads_a_f/workloada \
  -p recordcount=100000000 \
  -p operationcount=999999999 \
  -p maxexecutiontime=1800 \
  -p threadcount=128 \
  -t
```

### 场景 3：纯写入压测

```bash
java -jar $YCSB_JAR -P workloads/workloads_a_f/workloada \
  -p recordcount=10000000 \
  -p operationcount=10000000 \
  -p readproportion=0 \
  -p updateproportion=0 \
  -p insertproportion=1 \
  -p threadcount=64 \
  -p requestdistribution=uniform \
  -t
```

### 场景 4：纯读取压测

```bash
# 先加载数据
java -jar $YCSB_JAR -P workloads/workloads_a_f/workloadc \
  -p recordcount=10000000 \
  -p threadcount=64 \
  -load

# 100% 读取测试
java -jar $YCSB_JAR -P workloads/workloads_a_f/workloadc \
  -p recordcount=10000000 \
  -p operationcount=10000000 \
  -p threadcount=128 \
  -t
```

