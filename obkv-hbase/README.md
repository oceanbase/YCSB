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

`create_table.sh` 用于生成 HBase 风格的 `ycsb_test$cf` 表建表 SQL，支持：
- Range 分区（基于 G 列，G = ABS(T)）
- Key 子分区（基于 K_PREFIX 列，K_PREFIX = substring(K, 1, 18)）
- 动态分区策略（自动创建和过期分区）

### 使用方法

```bash
./create_table.sh <range_partition_count> <key_subpartition_count> [start_timestamp] [partition_duration_ms] [output_file]
```

### 参数说明

| 参数 | 类型 | 必填 | 说明 | 默认值 |
|------|------|------|------|--------|
| `range_partition_count` | 整数 | 是 | Range 分区数量 | - |
| `key_subpartition_count` | 整数 | 是 | 每个 Range 分区的 Key 子分区数量 | - |
| `start_timestamp` | 时间戳/日期 | 否 | 起始时间戳（毫秒）或日期字符串（YYYY-MM-DD HH:MM:SS） | 当前时间 |
| `partition_duration_ms` | 整数 | 否 | 每个 Range 分区的时间跨度（毫秒） | 2592000000（1个月） |
| `output_file` | 文件路径 | 否 | 输出 SQL 文件路径 | 自动生成文件名 |

### 使用示例

```bash
# 使用默认值（当前时间，1个月跨度）
./create_table.sh 1 40

# 指定起始时间戳
./create_table.sh 1 40 1704067200000

# 指定起始时间和分区跨度
./create_table.sh 1 40 1704067200000 2592000000

# 使用日期字符串并指定输出文件
./create_table.sh 1 40 '2024-01-01 00:00:00' 2592000000 my_table.sql
```

### 生成的表结构

生成的 SQL 会创建以下表结构：

- **表名**: `ycsb_test$cf`
- **表组**: `ycsb_test`
- **列定义**:
  - `K`: varbinary(1024) - Row Key
  - `Q`: varbinary(256) - Column Qualifier
  - `T`: bigint(20) - Timestamp
  - `V`: varbinary(10240) - Value
  - `G`: bigint(20) GENERATED ALWAYS AS (ABS(T)) - 用于 Range 分区
  - `K_PREFIX`: varbinary(1024) GENERATED ALWAYS AS (substring(K, 1, 18)) - 用于 Key 子分区
- **主键**: (K, Q, T)
- **分区策略**: 
  - Range 分区基于 G 列
  - Key 子分区基于 K_PREFIX 列
  - 动态分区策略：每月自动创建和过期

### 分区边界说明

- 第一个分区边界 = 起始时间 + 1 * 分区跨度
- 第二个分区边界 = 起始时间 + 2 * 分区跨度
- 以此类推...
- 最后一个分区使用 MAXVALUE

### 输出说明

脚本默认同时输出到：
1. **标准输出 (stdout)**: 可以直接查看或通过管道处理
2. **SQL 文件**: 自动生成文件名格式为 `ycsb_test_cf_r<range>_k<key>_d<duration>.sql`

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

```properties
# 记录数量（数据加载阶段）
recordcount=100000

# 操作数量（运行阶段）
operationcount=10000

# 字段数量和长度
fieldcount=10
fieldlength=100

# 线程数
threadcount=10
```

#### 2. 操作比例配置

```properties
# 操作比例（总和应为 1.0）
readproportion=0.5          # 读取比例
insertproportion=0.1        # 插入比例
scanproportion=0.05         # 扫描比例
batchputproportion=0        # 批量写入比例
batchreadproportion=0       # 批量读取比例
```

#### 3. 批量操作配置

```properties
# 批量操作大小
batchput.size.per.op=10     # 每次批量写入的记录数
batchread.size.per.op=10    # 每次批量读取的记录数
```

**注意**: 对于 batchput，如果要避免重复记录，需要确保 `recordcount >= batchput.size.per.op * operationcount`

#### 4. 连接配置（OBKV 模式）

```properties
# 连接模式（true=ODP模式, false=直连模式）
hbase.oceanbase.odpMode=true

##### ODP 模式必填 #####
hbase.oceanbase.odpAddr=your_odp_address
hbase.oceanbase.odpPort=your_odp_port

##### 直连模式必填 #####
hbase.oceanbase.paramURL=your_param_url
hbase.oceanbase.sysUserName=your_sys_user
hbase.oceanbase.sysPassword=your_sys_password

##### 账户密码（两种模式都需要）#####
hbase.oceanbase.fullUserName=your_full_user_name
hbase.oceanbase.password=your_password
hbase.oceanbase.database=your_database_name
```

#### 5. 表配置

```properties
# 表名和列族
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf
```

#### 6. 分区配置

```properties

# Range 分区配置
obkv.rangePartitionStartTs=1767703570000        # 起始时间戳（毫秒）
obkv.rangePartitionDurationMs=2592000000       # 分区时间跨度（毫秒，默认1个月）
obkv.rangePartitionCount=1                     # Range 分区数量
obkv.keyCount=10000                            # Key 数量（用于循环使用）
```

**分区配置说明**:
- `rangePartitionStartTs`: 第一个 Range 分区的起始时间戳（毫秒）
- `rangePartitionDurationMs`: 每个 Range 分区的时间跨度（毫秒），默认 2592000000（30天）
- `rangePartitionCount`: Range 分区的数量，应与建表时的分区数量一致
- `keyCount`: Key 的总数量，用于确保 Key 在指定范围内循环使用

#### 7. 其他配置

```properties
# 调试模式
obkv.debug=false

# 连接池大小
server.connection.pool.size=20

# 超时配置（毫秒）
rpc.operation.timeout=10000
rpc.execute.timeout=15000
```

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
# 生成建表 SQL（默认输出到 stdout 和文件）
./create_table.sh 1 40

# 或者指定参数
./create_table.sh 1 40 1704067200000 2592000000

# 查看生成的 SQL 文件
cat ycsb_test_cf_r1_k40_d2592000000.sql

# 在数据库中执行 SQL（根据实际情况调整）
# mysql -h your_host -u your_user -p < ycsb_test_cf_r1_k40_d2592000000.sql
```

### 步骤 3: 配置 Workload 文件

编辑 `workloads/workload_load` 文件，配置：
- 连接信息（ODP 地址或直连参数）
- 账户密码
- 表名和列族
- 分区配置（如果使用多版本模式）
- 记录数量和操作数量

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

