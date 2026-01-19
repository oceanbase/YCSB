# YCSB OBKV-HBase 性能测试工具

本工具基于 YCSB (Yahoo! Cloud Serving Benchmark) 框架，专门用于测试 OBKV-HBase 的性能。

## 项目结构

```
obkv-hbase/
├── src/main/java/com/oceanbase/obkv/
│   ├── RunMain.java              # 程序入口，封装了YCSB Client调用
│   └── ycsb/
│       └── OBHBaseClient.java    # HBase客户端实现，支持OBKV和标准HBase
├── workloads/                    # Workload配置文件目录
│   ├── workload_load            # 数据加载workload
│   ├── workload_put             # 写入测试workload
│   ├── workload_read            # 读取测试workload
│   ├── workload_scan            # 扫描测试workload
│   ├── workload_batch_put       # 批量写入测试workload
│   ├── workload_batch_read      # 批量读取测试workload
│   └── workload_template        # Workload配置模板
├── build.sh                     # 编译打包脚本
├── create_table.sh              # 建表SQL生成脚本
├── run_fast_test.sh             # 快速测试运行脚本
└── README.md                    # 本文档
```

---

## 建表脚本说明

### 功能概述

`create_table.sh` 用于生成 HBase 和时序模型的建表 SQL，支持：
- **模型类型**：HBase 模型和时序模型
- **分区类型**：
  - 一级 range 分区（first_part）：基于 K 列进行分区
  - 二级 range-key 分区（sec_part）：基于 G 列（ABS(T)）进行 range 分区，基于 K_PREFIX 列进行 key 子分区
- **默认行为**：默认使用 hbase 模式和 first_part 分区类型

### 使用方法

```bash
./create_table.sh [OPTIONS]
```

使用 `--help` 或 `-h` 查看完整帮助信息：

```bash
./create_table.sh --help
```

### 参数说明

#### 通用参数

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `--mode` | 字符串 | 否 | 模型类型：'hbase' 或 'timeseries' | hbase |
| `--type` | 字符串 | 否 | 分区类型：'first_part' 或 'sec_part' | first_part |
| `--table_name` | 字符串 | 否 | 表名 | hbase=ycsb_test, timeseries=ycsb_test |
| `--family` | 字符串 | 否 | 列族名 | hbase=cf, timeseries=ts_cf |
| `--output_file` | 文件路径 | 否 | 输出 SQL 文件路径 | 自动生成文件名 |
| `--help` / `-h` | - | - | 显示帮助信息 | - |

#### 一级分区参数（--type first_part）

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `--max_key` | 整数 | 是 | 最大 key 值 | - |
| `--partition_count` | 整数 | 是 | 分区数量 | - |
| `--key_length` | 整数 | 是 | Key 格式化长度（左补零） | - |

#### 二级分区参数（--type sec_part）

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `--key_subpartition_count` | 整数 | 是 | Key 子分区数量 | - |
| `--start_timestamp` | 时间戳/日期 | 否 | 起始时间戳（毫秒）或日期字符串（YYYY-MM-DD HH:MM:SS） | 当前时间 |
| `--partition_duration_ms` | 整数 | 否 | 分区时间跨度（毫秒） | 86400000（1天） |

### 使用示例

#### 一级分区示例

```bash
# HBase 一级分区（使用默认值）
./create_table.sh --max_key 1000 --partition_count 4

# HBase 一级分区（显式指定）
./create_table.sh --mode hbase --type first_part --max_key 1000 --partition_count 4

# 时序模型一级分区
./create_table.sh --mode timeseries --type first_part --max_key 1000 --partition_count 4

# 自定义表名和列族
./create_table.sh --max_key 500 --partition_count 3 --table_name mytable --family mycf
```

#### 二级分区示例

```bash
# HBase 二级分区（使用默认时间戳）
./create_table.sh --mode hbase --type sec_part --key_subpartition_count 40 --partition_duration_ms 2592000000

# HBase 二级分区（指定时间戳）
./create_table.sh --mode hbase --type sec_part --key_subpartition_count 40 --start_timestamp 1704067200000 --partition_duration_ms 2592000000

# HBase 二级分区（使用日期字符串）
./create_table.sh --mode hbase --type sec_part --key_subpartition_count 40 --start_timestamp '2024-01-01 00:00:00' --partition_duration_ms 2592000000

# 时序模型二级分区
./create_table.sh --mode timeseries --type sec_part --key_subpartition_count 40 --start_timestamp 1704067200000 --partition_duration_ms 2592000000

# 指定输出文件
./create_table.sh --max_key 1000 --partition_count 4 --key_length 12 --output_file my_table.sql
```

---

## 编译脚本说明

### 环境依赖

在运行编译脚本之前，请确保已安装以下工具：

1. **Java JDK 8+**
   ```bash
   java -version  # 检查 Java 版本
   ```

2. **Maven 3.8.6**
   ```bash
   mvn -version  # 检查 Maven 版本
   ```

### 使用方法

```bash
# 编译打包（包含依赖）
./build.sh

# 清理构建产物
./build.sh clean
```

---

## Workload 文件配置说明

### 文件位置

Workload 配置文件位于 `workloads/` 目录下，包括：
- `workload_load`: 数据加载
- `workload_put`: 写入测试
- `workload_read`: 读取测试
- `workload_scan`: 扫描测试
- `workload_batch_put`: 批量写入测试
- `workload_batch_read`: 批量读取测试
- `workload_template`: 配置模板

### 核心配置项

#### 1. 基础配置

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `recordcount` | int | 是 | 数据加载阶段的记录数量 | - |
| `operationcount` | int | 是 | 运行阶段的操作数量 | - |
| `fieldcount` | int | 否 | 每条记录的字段数量 | 10 |
| `fieldlength` | int | 否 | 每个字段的长度（字节） | 100 |
| `threadcount` | int | 否 | 并发线程数 | 1 |

#### 2. 操作比例配置

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `readproportion` | double | 否 | 读取操作比例 | 0.95 |
| `insertproportion` | double | 否 | 插入操作比例 | 0.05 |
| `scanproportion` | double | 否 | 扫描操作比例 | 0 |
| `batchputproportion` | double | 否 | 批量写入操作比例 | 0 |
| `batchreadproportion` | double | 否 | 批量读取操作比例 | 0 |

**注意**: 所有操作比例的总和应为 1.0

#### 3. 批量操作配置

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `batchput.size.per.op` | int | 否 | 每次批量写入的记录数 | 1 |
| `batchread.size.per.op` | int | 否 | 每次批量读取的记录数 | 1 |

**注意**: 对于 batchput，如果要避免重复记录，需要确保 `recordcount >= batchput.size.per.op * operationcount`

#### 4. 连接配置（OBKV 模式）

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `hbase.oceanbase.odpMode` | boolean | 是 | 连接模式，true=ODP模式，false=直连模式 | - |
| `hbase.oceanbase.odpAddr` | string | 是（ODP模式） | ODP 服务器地址 | - |
| `hbase.oceanbase.odpPort` | int | 是（ODP模式） | ODP 服务器端口 | - |
| `hbase.oceanbase.paramURL` | string | 是（直连模式） | 直连模式的参数 URL | - |
| `hbase.oceanbase.sysUserName` | string | 是（直连模式） | 系统用户名 | - |
| `hbase.oceanbase.sysPassword` | string | 是（直连模式） | 系统用户密码 | - |
| `hbase.oceanbase.fullUserName` | string | 是 | 完整用户名（格式：用户名@租户名#集群名） | - |
| `hbase.oceanbase.password` | string | 是 | 用户密码 | - |
| `hbase.oceanbase.database` | string | 是 | 数据库名 | - |

#### 5. 表配置

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `hbase.oceanbase.table` | string | 是 | 表名（不含列族） | - |
| `hbase.oceanbase.columnFamily` | string | 是 | 列族名 | - |

#### 6. 分区配置与时间范围测试模式

时间范围测试模式（`obkv.enableTimeRangeTestMode`）用于测试基于时间戳的分区表性能。启用该模式后，系统会：

1. **基于分区配置生成时间戳**: 根据分区参数计算时间戳，确保数据均匀分布到不同的 Range 分区
2. **写入时指定时间戳**: 在写入数据时，为每个 cell 指定计算出的时间戳（而非使用当前系统时间）
3. **扫描时使用时间范围**: 在扫描操作时，自动设置时间范围查询，只查询特定时间窗口内的数据
4. **Key 生成策略**: 生成 key 时会将时间戳附加到 key 上，确保数据按时间分布

**配置项**:

测试负载根据 `obkv.enableTimeRangeTestMode` 参数分为两种模式，分别适用于不同的表类型：

**测试模式说明**：

- `obkv.enableTimeRangeTestMode=false`：标准模式，适合测试一级 range 分区表
  - Key 生成：直接使用 YCSB 生成的 key
  - Timestamp 生成：使用当前系统时间
  - 数据分布：基于 K 列进行分区路由
  - 适用场景：测试基于 key 的一级分区表性能，无需时间范围查询
  
- `obkv.enableTimeRangeTestMode=true`：时间范围测试模式，适合测试二级 range-key 分区表
  - Key 生成：生成包含 key prefix 和 timestamp 的复合 key（格式：`user_%012d_<timestamp>`）
  - Timestamp 生成：根据分区配置计算，确保数据均匀分布在各个 range 分区
  - 数据分布：基于 G 列（ABS(T)）和 K_PREFIX 列进行分区路由
  - 时间范围查询：扫描操作会自动设置时间范围，只查询特定时间窗口内的数据
  - 适用场景：测试基于时间戳的二级分区表性能，需要时间范围查询的场景

**参数说明**：

| 参数 | 类型 | 必填 | 适用模式 | 说明 |
|------|------|------|----------|------|
| `obkv.enableTimeRangeTestMode` | boolean | 是 | 所有模式 | 时间范围测试模式开关，false=标准模式（一级分区），true=时间范围测试模式（二级分区） |
| `obkv.rangePartitionStartTs` | long | 是（时间范围模式） | 时间范围测试模式 | 第一个 Range 分区的起始时间戳（毫秒），需与建表时的 `start_timestamp` 一致 |
| `obkv.rangePartitionDurationMs` | long | 是（时间范围模式） | 时间范围测试模式 | 每个 Range 分区的时间跨度（毫秒），需与建表时的 `partition_duration_ms` 一致 |
| `obkv.rangePartitionCount` | int | 是（时间范围模式） | 时间范围测试模式 | Range 分区的数量，需与建表时的分区数量一致 |
| `obkv.keyCount` | int | 是（时间范围模式） | 时间范围测试模式 | Key 的总数量，用于确保 Key 在指定范围内循环使用 |

**使用场景**:
- 测试基于时间戳的 Range 分区表性能
- 验证数据在不同时间分区中的分布情况
- 测试时间范围查询的性能
- 测试二级 range-key 分区表的数据分布和查询性能

**工作原理**:
1. **时间戳计算**：根据 `rangePartitionStartTs`、`rangePartitionDurationMs` 和 `rangePartitionCount` 计算每个 key 应该写入的时间戳，确保数据均匀分布到各个时间分区
2. **Key 生成**：在时间范围测试模式下，key 会包含时间戳信息，格式为 `user_%012d_<timestamp>`，便于按时间范围进行查询
3. **写入优化**：写入时会为每个 cell 指定计算出的时间戳，而不是使用当前系统时间，确保数据按预期分布
4. **查询优化**：扫描操作会自动根据分区配置设置时间范围，只查询相关时间窗口内的数据，提高查询效率

**注意事项**:
- 启用 `obkv.enableTimeRangeTestMode=true` 时，必须同时配置所有分区相关参数（`rangePartitionStartTs`、`rangePartitionDurationMs`、`rangePartitionCount`、`keyCount`）
- 分区配置应与建表时的分区策略保持一致，特别是 `rangePartitionStartTs` 和 `rangePartitionDurationMs` 必须与建表 SQL 中的 `start_timestamp` 和 `partition_duration_ms` 完全一致
- 该模式主要用于测试场景，生产环境请根据实际需求选择是否启用

#### 7. Scan 操作配置（PageFilter）

Scan 操作支持使用 HBase 的 PageFilter 来控制每次扫描返回的记录数量。

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `obkv.scan.usePageFilter` | boolean | 否 | 是否在 scan 操作中使用 PageFilter。当设置为 `true` 时，scan 操作会使用 PageFilter 来限制每次扫描返回的记录数 | false |
| `obkv.scan.pageFilterSize` | int | 否 | PageFilter 的页面大小。如果设置了此值，PageFilter 会使用该值作为 pageSize；如果未设置，PageFilter 会使用 scan 操作的 `recordcount` 参数作为 pageSize。必须为正整数，否则会在初始化时抛出异常 | 使用 `recordcount` 参数 |

**使用场景**:
- 需要控制每次 scan 操作返回的记录数量
- 减少网络传输数据量，提高 scan 性能
- 测试不同 pageSize 对 scan 性能的影响

**注意事项**:
- PageFilter 不保证返回的记录数严格等于 pageSize，可能会略多于 pageSize
- 代码中已经实现了额外的检查，当返回的记录数达到 `recordcount` 时会停止扫描
- 建议在测试场景中根据实际需求调整 `pageFilterSize` 的值

#### 8. 其他配置

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `obkv.debug` | boolean | 否 | 调试模式开关 | false |
| `server.connection.pool.size` | int | 否 | 连接池大小 | 20 |
| `rpc.operation.timeout` | int | 否 | RPC 操作超时时间（毫秒） | 10000 |
| `rpc.execute.timeout` | int | 否 | RPC 执行超时时间（毫秒） | 15000 |

---

## 快速开始测试

### 步骤 1: 编译项目

```bash
cd obkv-hbase
./build.sh
```

### 步骤 2: 创建表

使用建表脚本生成 SQL 并执行：

```bash
# 生成一级分区表 SQL（使用默认值）
./create_table.sh --max_key 1000 --partition_count 4 --key_length 12

# 生成二级分区表 SQL
./create_table.sh --mode hbase --type sec_part --key_subpartition_count 40 --start_timestamp 1704067200000 --partition_duration_ms 2592000000

# 查看生成的 SQL 文件（文件名会自动生成）
# 在数据库中执行 SQL（根据实际情况调整）
# mysql -h your_host -u your_user -p < <生成的sql文件>
```

### 步骤 3: 配置 Workload 文件

编辑 `workloads/workload_load` 文件，根据你的表类型选择相应的配置，以ODP模式的一级分区表为例：

**一级分区表配置示例（ODP 模式）**：

```properties
# ==========================================
# 1. 基础配置
# ==========================================
recordcount=100000              # 数据加载阶段的记录数量
operationcount=10000            # 运行阶段的操作数量
fieldcount=10                   # 每条记录的字段数量
fieldlength=100                 # 每个字段的长度（字节）
threadcount=10                  # 并发线程数

# ==========================================
# 2. 操作比例配置（总和应为 1.0）
# ==========================================
readproportion=0.5             # 读取操作比例
insertproportion=0.1            # 插入操作比例
scanproportion=0.05             # 扫描操作比例
batchputproportion=0            # 批量写入操作比例
batchreadproportion=0           # 批量读取操作比例

# ==========================================
# 3. 批量操作配置
# ==========================================
batchput.size.per.op=10         # 每次批量写入的记录数
batchread.size.per.op=10        # 每次批量读取的记录数

# ==========================================
# 4. 连接配置（ODP 模式）
# ==========================================
hbase.oceanbase.odpMode=true             # 连接模式：true=ODP模式, false=直连模式
hbase.oceanbase.odpAddr=your_odp_address
hbase.oceanbase.odpPort=your_odp_port
hbase.oceanbase.fullUserName=your_full_user_name
hbase.oceanbase.password=your_password
hbase.oceanbase.database=your_database_name

# ==========================================
# 5. 表配置
# ==========================================
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf

# ==========================================
# 6. 分区配置（一级分区表）
# ==========================================
obkv.enableTimeRangeTestMode=false   # 标准模式，适合一级分区表

# ==========================================
# 7. 其他配置
# ==========================================
obkv.debug=false                # 调试模式开关
server.connection.pool.size=20  # 连接池大小
rpc.operation.timeout=10000     # RPC 操作超时时间（毫秒）
rpc.execute.timeout=15000       # RPC 执行超时时间（毫秒）
```

**二级分区表配置示例（ODP 模式，启用时间范围测试模式）**：

```properties
# ==========================================
# 1. 基础配置
# ==========================================
recordcount=100000              # 数据加载阶段的记录数量
operationcount=10000            # 运行阶段的操作数量
fieldcount=10                   # 每条记录的字段数量
fieldlength=100                 # 每个字段的长度（字节）
threadcount=10                  # 并发线程数

# ==========================================
# 2. 操作比例配置（总和应为 1.0）
# ==========================================
readproportion=0.5             # 读取操作比例
insertproportion=0.1            # 插入操作比例
scanproportion=0.05             # 扫描操作比例（时间范围测试模式下会自动使用时间范围查询）
batchputproportion=0            # 批量写入操作比例
batchreadproportion=0           # 批量读取操作比例

# ==========================================
# 3. 批量操作配置
# ==========================================
batchput.size.per.op=10         # 每次批量写入的记录数
batchread.size.per.op=10        # 每次批量读取的记录数

# ==========================================
# 4. 连接配置（ODP 模式）
# ==========================================
hbase.oceanbase.odpMode=true             # 连接模式：true=ODP模式, false=直连模式
hbase.oceanbase.odpAddr=your_odp_address
hbase.oceanbase.odpPort=your_odp_port
hbase.oceanbase.fullUserName=your_full_user_name
hbase.oceanbase.password=your_password
hbase.oceanbase.database=your_database_name

# ==========================================
# 5. 表配置
# ==========================================
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf

# ==========================================
# 6. 分区配置（二级分区表，启用时间范围测试模式）
# ==========================================
obkv.enableTimeRangeTestMode=true        # 启用时间范围测试模式，适合二级分区表
obkv.rangePartitionStartTs=1704067200000 # 第一个 Range 分区的起始时间戳（毫秒），需与建表时的 start_timestamp 一致
obkv.rangePartitionDurationMs=2592000000 # 每个 Range 分区的时间跨度（毫秒），需与建表时的 partition_duration_ms 一致
obkv.rangePartitionCount=30              # Range 分区的数量，需与建表时的分区数量一致
obkv.keyCount=100000                     # Key 的总数量，用于确保 Key 在指定范围内循环使用

# ==========================================
# 7. 其他配置
# ==========================================
obkv.debug=false                # 调试模式开关
server.connection.pool.size=20  # 连接池大小
rpc.operation.timeout=10000     # RPC 操作超时时间（毫秒）
rpc.execute.timeout=15000       # RPC 执行超时时间（毫秒）
```

### 步骤 4: 运行测试

#### 方式一：使用快速测试脚本

```bash

# 写入测试
./run_fast_test.sh put

# 批量写入测试
./run_fast_test.sh batch_put

# 数据加载
./run_fast_test.sh load

# 读取测试
./run_fast_test.sh read

# 扫描测试
./run_fast_test.sh scan

# 批量读取测试
./run_fast_test.sh batch_read

# 使用自定义 workload 文件
./run_fast_test.sh run /path/to/custom/workload
```

### 步骤 5: 查看结果

测试完成后，会输出性能统计信息，包括：
- 操作吞吐量（ops/sec）
- 延迟统计（平均、最小、最大、P99等）
- 错误统计

