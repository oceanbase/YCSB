# YCSB Obkv-Table 测试工具

YCSB (Yahoo! Cloud System Benchmark) 是一个用于测试云数据库性能的基准测试工具。本项目是YCSB的OBKV-Table绑定版本，支持测试OceanBase Table模型的性能测试。

## 项目结构

```
obkv-table/                     # YCSB核心模块
├── src/                        # OBKV-Table压测负载实现
├── workloads/                  # 工作负载配置文件
├── build.sh                    # 编译打包脚本
├── run_fast_test.sh            # 运行测试脚本
├── create_table.sh             # 生成建表SQL脚本
└── build/                      # 构建输出目录
```

## 表结构说明

本工具测试表统一命名为 `kv_table`。当前 `create_table.sh` 支持两种建表模型：

### 1) range 表（默认）
```sql
CREATE TABLE kv_table (
    ycsb_key varbinary(1024) NOT NULL,
    field0 varbinary(1024) NOT NULL,
    field1 varbinary(1024) NOT NULL,
    field2 varbinary(1024) NOT NULL,
    PRIMARY KEY (ycsb_key)
)
PARTITION BY RANGE COLUMNS(ycsb_key) (
    PARTITION p0 VALUES LESS THAN ('000000000250'),
    PARTITION p1 VALUES LESS THAN ('000000000500'),
    PARTITION p2 VALUES LESS THAN ('000000000750'),
    PARTITION p3 VALUES LESS THAN (MAXVALUE)
);
```
- **主键**：`ycsb_key`
- **分区**：按 `ycsb_key` 做 RANGE 分区：`PARTITION BY RANGE COLUMNS(ycsb_key)`
- **普通列**：`field0..field{N-1}`（通过 `create_table.sh --fields N` 指定数量）
- **压测数据生成逻辑：**
  - 每个 YCSB 的 key（递增整型字符串）会通过hash后取模映射为一个 `ycsb_key`,
  - `field{N-1}` 为随机生成指定长度的字符串
### 2) key_range 表（二级分区）
```sql
CREATE TABLE kv_table (
    ycsb_id CHAR(36) NOT NULL,
    ycsb_ts TIMESTAMP(6) NOT NULL,
    field0 varbinary(1024),
    field1 varbinary(1024),
    PRIMARY KEY (ycsb_id, ycsb_ts)
)
PARTITION BY RANGE COLUMNS (ycsb_ts)
SUBPARTITION BY KEY(ycsb_id) SUBPARTITIONS 3
(
    PARTITION p0 VALUES LESS THAN ('2024-12-31 00:00:00'),
    PARTITION p1 VALUES LESS THAN ('2025-12-31 00:00:00'),
    PARTITION p2 VALUES LESS THAN ('2026-12-31 00:00:00'),
    PARTITION p3 VALUES LESS THAN (MAXVALUE)
);
```
- **主键**：复合主键 `(ycsb_id, ycsb_ts)`
- **分区**：
  - 一级：按 `ycsb_ts` 做 RANGE 分区：`PARTITION BY RANGE COLUMNS (ycsb_ts)`
  - 二级：按 `ycsb_id` 做 KEY 分区：`SUBPARTITION BY KEY(ycsb_id) SUBPARTITIONS <key_subpartition_count>`
- **普通列**：`field0..field{N-1}`（通过 `create_table.sh --fields N` 指定数量）

- **压测数据生成逻辑：**
  - 每个 YCSB 的 key（递增整型字符串）会映射为一个 `(ycsb_id, ycsb_ts)` 对
  - `ycsb_id = key % idCount`，确保 ycsb_id 循环使用
  - `ycsb_ts` 基于 key 计算，确保数据均匀分布到所有 range 分区
  - `field{N-1}` 为随机生成指定长度的字符串
## 快速开始

### 1. 环境要求

- **Java**: JDK 1.8 或更高版本
- **Maven**: 3.x 版本
- **操作系统**: Linux/macOS/Windows

### 2. 编译打包

```bash
# 编译打包项目
./build.sh

# 仅清理构建产物
./build.sh clean
```

### 3. 创建测试表

使用 `create_table.sh` 脚本生成建表 SQL（支持 `range` / `key_range` 两种表模型）。脚本会**输出 建表SQL  文件到 `obkv-table/` 目录**。

#### 0) 查看帮助信息
./create_table.sh --help

#### 1) 创建 range 表（默认方式）
```
// ./create_table.sh --mode range --fields N <num_partitions> <max_key> [key_length]
./create_table.sh --mode range --fields 3 20 30000000 12
```
**参数说明：**
- `--fields N`: 普通列数量（生成 `field0..field{N-1}`），默认 1
- `--mode range`: 创建 `ycsb_key` 主键的 range 表
- `num_partitions`: 分区数量
- `max_key`: 主键key的最大值
- `key_length(可选，默认12)`：主键key的长度

#### 2) 创建 key_range 表（二级分区）
```
# ./create_table.sh --mode key_range --fields N <range_partition_count> <key_subpartition_count> <start_timestamp> <partition_duration_ms>
./create_table.sh --mode key_range --fields 2 4 48 '2024-01-01 00:00:00' 31536000000
```
**参数说明：**
- `--fields N`: 普通列数量（生成 `field0..field{N-1}`），默认 1
- `--mode key_range`: 创建 `(ycsb_id, ycsb_ts)` 主键的二级分区表
- `range_partition_count`: 一级range分区的数量
- `key_subpartition_count`: 二级key分区的数量
- `start_timestamp`: 第一个range分区的起始时间，支持毫秒时间戳或日期字符串（如 `1704067200000` / `'2024-01-01 00:00:00'`）
- `partition_duration_ms`: 每个range分区的时间跨度


### 4. 配置workload

在使用前需要填写对应测试操作workloads/workload_xxx文件中的OceanBase连接参数、分区配置参数和通用压测参数。

#### 4.1 OceanBase连接参数

| 参数名 | 说明 | 必填 |
|--------|------|------|
| `obkv.isOdpMode` | 连接模式选择（true/false） | 是 |
| `obkv.odpAddr` | ODP代理地址 | ODP模式必填 |
| `obkv.odpPort` | ODP代理端口 | ODP模式必填 |
| `obkv.configUrl` | 直连模式连接URL | 直连模式必填 |
| `obkv.sysUserName` | 系统租户用户名 | 直连模式必填 |
| `obkv.sysPassword` | 系统租户密码 | 直连模式必填 |
| `obkv.fullUserName` | 业务租户用户名 | 是 |
| `obkv.password` | 业务租户密码 | 是 |
| `obkv.database` | 数据库名 | 是 |

**注意：** 其他客户端参数设置，可以参考obkv-table-client支持的参数设置

#### 4.2 YCSB测试通用参数

| 参数名 | 说明 | 默认值 |
|--------|------|--------|
| `table` | 压测表名 | kv_table |
| `operationcount` | 操作总数 | - |
| `recordcount` | 记录总数 | - |
| `requestdistribution` | 请求分布模式 | uniform |
| `threadcount` | 并发线程数 | 1 |
**说明**
`operationcount`是生成操作的总数，用于生成每个操作；`recodcount`是导入数据的总数，用于生成每个操作key的范围；一般情况下令`operationcount`=`recodcount`即可；

#### 4.3 分区配置参数（二级分区表必须配置）

这些参数用于数据生成和分布，**必须显式指定**，否则会报错：

| 参数名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `obkv.partitionStartTs` | 第一个range分区的起始时间戳（毫秒） | `1704067200000` | 是 |
| `obkv.partitionDurationMs` | 每个range分区的时间长度（毫秒） | `31536000000`（1年） | 是 |
| `obkv.partitionCount` | 一级range分区的数量 | `4` | 是 |
| `obkv.idCount` | pmid的总数量 | `1000` | 是 |

**说明：**
- `partitionStartTs` 必须与建表时使用的起始时间戳一致
- `partitionDurationMs` 必须与建表时使用的分区时间跨度一致
- `partitionCount` 必须与建表时的一级分区数量一致
- `idCount` 决定了有多少个不同的pmid值，同一个pmid可以出现在不同的ts值上

#### 4.4 操作类型配置

| 参数名 | 说明 | 可选值 | 默认值 |
|--------|------|--------|--------|
| `obkv.insertType` | insert操作使用的obkv接口 | `insert`, `insertup`, `put` | `put` |
| `obkv.updateType` | update操作使用的obkv接口 | `update`, `insertup`, `put` | `put` |
| `obkv.batchPutType` | batch_put操作使用的obkv接口 | `insert`, `insertup`, `put` | `put` |

**接口说明：**
- `insert`: 插入操作，如果主键已存在会报错
- `insertup`: 插入或更新操作，如果主键已存在则更新
- `put`: 覆盖写操作，如果主键已存在则覆盖（需要server端测试租户下设置 `set global binlog_row_image='MINIMAL'`）

#### 4.5 客户端常用配置参数
| 参数名 | 说明 | 可选值 | 默认值 |
|--------|------|--------|--------|
| `rpc.operation.timeout` | OB内部执行RPC请求的超时时间（毫秒）| 整型 | 2000 |
| `rpc.execute.timeout` | 执行RPC请求的socket超时时间（毫秒） | 整型 | 3000 |
| `server.connection.pool.size` | 客户端句柄连接数 | 整型 | 1 |


#### 4.6 配置示例

完整的workload配置示例：

```properties
# ========== OceanBase连接配置 ==========
# 直连模式
obkv.isOdpMode=false
obkv.configUrl=xxx
obkv.sysUserName=xxx
obkv.sysPassword=xxx

# 或ODP模式
# obkv.isOdpMode=true
# obkv.odpAddr=xxx
# obkv.odpPort=xxx

# 账密（两种模式都需要）
obkv.fullUserName=xxx
obkv.password=xxx
obkv.database=test

# ========== 分区配置（必须配置） ==========
obkv.partitionStartTs=1704067200000
obkv.partitionDurationMs=31536000000
obkv.partitionCount=4
obkv.pmidCount=1000

# ========== YCSB测试参数 ==========
workload=com.yahoo.ycsb.workloads.CoreWorkload
recordcount=1000000
operationcount=1000000
threadcount=10

# ========== 操作类型配置 ==========
obkv.insertType=insertup
obkv.updateType=update
obkv.batchPutType=insertup

# ========== 其他配置 ==========
obkv.debug=false
```

### 5. 运行测试
#### 5.0. 服务端测试租户设置binlog_row_image变量
写入操作默认使用的是obkv put接口，具有覆盖写的语义，性能更好，不支持cdc全列镜像，在服务端需要显式设置binlog_row_image：
```
set global binlog_row_imag='MINIMAL';
```
**注意**
如果有cdc下游同步的组件，put操作无法使用，只能用insertOrUpdate接口来代替，此时需要在workload文件中显式指定使用insertup接口：
```
obkv.insertType=insertup
obkv.updateType=insertup
obkv.batchPutType=insertup
```


#### 5.1. Put 测试

```bash
# 运行insert测试
./run_fast_test.sh put

# 或使用自定义workload文件
./run_fast_test.sh put workloads/workload_put
```

#### 5.2. Batch Put 测试

```bash
# 运行batch_put测试
./run_fast_test.sh batch_put
```

**注意：** 如果使用 `put` 接口，需要在server端设置：
```sql
mysql> set global binlog_row_image='MINIMAL';
```

#### 5.3. Read 测试

```bash
# 先load数据
./run_fast_test.sh load workloads/workload_read

# 运行read测试
./run_fast_test.sh read
```

#### 5.4. Batch Read 测试

```bash
# 先load数据
./run_fast_test.sh load workloads/workload_batch_read

# 运行batch_read测试
./run_fast_test.sh batch_read
```

**说明：**
- 批量查询多个key对应的记录
- 可以通过 `batchread.size.per.op` 配置每批的数量

#### 5.5. Scan 测试

```bash
# 先load数据
./run_fast_test.sh load workloads/workload_scan

# 运行scan测试
./run_fast_test.sh scan
```

**说明：**
- 基于startkey生成ycsb_id，扫描该ycsb_id下的所有ycsb_ts范围
