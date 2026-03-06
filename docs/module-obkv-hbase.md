# OBKV-HBase 模块详解

OBKV-HBase 绑定用于测试 OceanBase HBase 兼容模式的性能。本文详细说明表模型、测试模式、分区策略和建表脚本。

> 如需快速跑通测试，请先阅读 [快速入门](getting-started.md) 或 [命令行使用指南](guide-cli.md)。

---

## 1. 项目结构

```
obkv-hbase/
├── src/main/java/com/oceanbase/obkv/
│   ├── RunMain.java              # 程序入口
│   └── ycsb/
│       └── OBHBaseClient.java    # HBase 客户端实现
├── workloads/                    # Workload 配置文件
├── build.sh                     # 编译打包脚本
├── create_table.sh              # 建表 SQL 生成脚本
├── run_fast_test.sh             # 运行测试脚本
└── build/                       # 构建输出目录
```

---

## 2. 核心概念

### 测试模式

通过 `obkv.testMode` 参数选择：

#### 默认模式（default）

- **适用场景**：一级 Key/Range 分区表
- **Key 处理**：根据 `obkv.maxKey` 进行取余（可选）
- **Scan 方式**：范围查询或 PageFilter
- **Key 生成**：直接使用 YCSB 生成的 key

**Key 处理流程**：

```
YCSB 生成 key → [可选] key % (maxKey + 1) → 保持零填充格式 → 使用
```

#### 前缀查询模式（prefix）

- **适用场景**：需要前缀查询的场景，支持一级和二级分区表
- **Key 格式**：`prefixId_subId`
- **强制要求**：`insertorder=ordered`

**Key 生成流程**：

```
YCSB 生成 key (ordered) → prefixId = key / prefixCount
                        → subId = key % prefixCount
                        → 生成 prefixId_subId 格式
```

**一级分区表**：只需配置 `obkv.prefixCount`

**二级分区表**：需额外配置 range 分区参数（`rangePartitionCount`、`rangePartitionStartTs`、`rangePartitionDurationMs`），时间戳基于 subId 计算确保均匀分布

### 表模型

通过 `--mode` 参数选择：

| 模式 | 模型名称 | 列定义 | 主键 | 适用场景 |
|------|---------|--------|------|----------|
| `hbase` | KQTV | K, Q, T, V | (K, Q, T) | HBase 模型，支持列族和列限定符 |
| `ts` | KTSV | K, T, S, V | (K, T, S) | 时序模型，S 为序列号，V 为 JSON |

**列类型差异**：

| 特性 | hbase (KQTV) | ts (KTSV) |
|------|-------------|-----------|
| Q 列 | varbinary(256) | 无 |
| S 列 | 无 | bigint(20) |
| V 列 | varbinary(10240) | json |

### 分区类型

**一级分区**（`--type first_part`）：
- **Key 分区**：基于 K 列 Hash 分区，数据自动均匀分布
- **Range 分区**：基于 K 列范围分区，需指定分区边界

**二级分区**（`--type sec_part`）：
- 一级：Range 分区，基于 G 列（`ABS(T)`）时间范围分区
- 二级：Key 分区，基于 K_PREFIX 列（K 的前 16 字节）Hash 分区

---

## 3. 建表脚本详解

### 基本用法

```bash
./create_table.sh [OPTIONS]
./create_table.sh --help
```

### 通用参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--mode` | 表模型：`hbase` 或 `ts` | hbase |
| `--type` | 分区类型：`first_part` 或 `sec_part` | first_part |
| `--table_name` | 表名 | ycsb_test |
| `--family` | 列族名 | hbase=cf, ts=ts_cf |
| `--output_file` | 输出 SQL 文件路径 | 自动生成 |

### 一级分区参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--partition_type` | `key` 或 `range` | range |
| `--max_key` | 最大 key 值（仅 range） | Long.MAX_VALUE |
| `--partition_count` | 分区数量 | — |
| `--key_length` | Key 格式化长度（仅 range） | 12 |

### 二级分区参数

| 参数 | 说明 |
|------|------|
| `--range_partition_count` | Range 分区数量 |
| `--range_start_timestamp` | 起始时间戳(ms)或日期字符串 |
| `--range_partition_duration_ms` | 分区时间跨度(ms) |
| `--key_subpartition_count` | Key 子分区数量 |

### 使用示例

```bash
# 一级 Range 分区（默认 hbase 模型）
./create_table.sh --max_key 1000 --partition_count 4

# 一级 Key 分区
./create_table.sh --mode hbase --type first_part --partition_type key --partition_count 4

# 二级分区表
./create_table.sh --mode hbase --type sec_part \
  --range_partition_count 30 \
  --range_start_timestamp 1704067200000 \
  --range_partition_duration_ms 2592000000 \
  --key_subpartition_count 40

# ts 模型二级分区表
./create_table.sh --mode ts --type sec_part \
  --range_partition_count 30 \
  --range_start_timestamp 1704067200000 \
  --range_partition_duration_ms 2592000000 \
  --key_subpartition_count 40
```

---

## 4. 配置示例

### 默认模式

```properties
# 基础配置
recordcount=100000
operationcount=10000
fieldcount=10
fieldlength=100
threadcount=10

# 连接配置（ODP 模式）
hbase.oceanbase.odpMode=true
hbase.oceanbase.odpAddr=
hbase.oceanbase.odpPort=
hbase.oceanbase.fullUserName=
hbase.oceanbase.password=
hbase.oceanbase.database=

# 表配置
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf

# 测试模式
obkv.testMode=default
obkv.maxKey=1000

# 客户端配置
obkv.debug=false
server.connection.pool.size=20
rpc.operation.timeout=10000
rpc.execute.timeout=15000
```

### 前缀查询模式（二级分区表）

```properties
# 基础配置
recordcount=100000
operationcount=10000
fieldcount=10
fieldlength=100
threadcount=10
insertorder=ordered    # 前缀模式必须

# 连接配置
hbase.oceanbase.odpMode=true
hbase.oceanbase.odpAddr=
hbase.oceanbase.odpPort=
hbase.oceanbase.fullUserName=
hbase.oceanbase.password=
hbase.oceanbase.database=

# 表配置
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf

# 测试模式
obkv.testMode=prefix
obkv.prefixCount=100
obkv.rangePartitionCount=30
obkv.rangePartitionStartTs=1704067200000
obkv.rangePartitionDurationMs=2592000000
obkv.enablePastTime=true
obkv.pastTime=1704067200000
```

> 各参数的完整说明请查阅 [参数配置大全](params-reference.md)。

---

## 5. 运行测试

```bash
./run_fast_test.sh load          # 加载数据
./run_fast_test.sh put           # 写入测试
./run_fast_test.sh read          # 读取测试（需先 load）
./run_fast_test.sh scan          # 扫描测试（需先 load）
./run_fast_test.sh batch_put     # 批量写入
./run_fast_test.sh batch_read    # 批量读取（需先 load）
./run_fast_test.sh run <file>    # 自定义 workload
```

> 运行测试的详细说明请参考 [命令行使用指南](guide-cli.md)。

---

## 相关文档

- [快速入门](getting-started.md) — 5 分钟跑通第一次测试
- [命令行使用指南](guide-cli.md) — 完整命令行操作流程
- [Web UI 使用指南](guide-webui.md) — 图形界面操作流程
- [参数配置大全](params-reference.md) — 所有可配置参数一览
- [Workload 参考](workload-reference.md) — Workload 文件模板与示例
