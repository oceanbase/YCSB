# OBKV-Table 模块详解

OBKV-Table 绑定用于测试 OceanBase Table 模型的性能。本文详细说明表结构设计、建表脚本、分区策略和配置参数。

> 如需快速跑通测试，请先阅读 [快速入门](getting-started.md) 或 [命令行使用指南](guide-cli.md)。

---

## 1. 项目结构

```
obkv-table/
├── src/                        # OBKV-Table 压测负载实现
├── workloads/                  # 工作负载配置文件
├── build.sh                    # 编译打包脚本
├── run_fast_test.sh            # 运行测试脚本
├── create_table.sh             # 生成建表 SQL 脚本
└── build/                      # 构建输出目录
```

---

## 2. 表结构说明

测试表统一命名为 `kv_table`。`create_table.sh` 支持三种建表模型。

### range 表（默认）

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
- **分区**：按 `ycsb_key` 做 RANGE 分区
- **普通列**：`field0..field{N-1}`（通过 `--fields N` 指定数量）
- **数据生成**：YCSB 的 key（递增整型字符串）经 hash 取模映射为 `ycsb_key`；各 field 为随机字符串

### key 表

与 range 表结构相同，但使用 KEY 分区：`PARTITION BY KEY(ycsb_key)`

### key_range 表（二级分区）

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
  - 一级：按 `ycsb_ts` 做 RANGE 分区
  - 二级：按 `ycsb_id` 做 KEY 分区
- **数据生成**：`ycsb_id = key % idCount`（循环使用）；`ycsb_ts` 基于 key 计算，确保均匀分布到所有 range 分区

---

## 3. 建表脚本详解

### 查看帮助

```bash
./create_table.sh --help
```

### range 表

```bash
./create_table.sh --mode range --fields N <num_partitions> <max_key> [key_length]
```

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--fields N` | 普通列数量 | 1 |
| `--mode range` | range 分区模式 | range |
| `num_partitions` | 分区数量 | — |
| `max_key` | 主键最大值 | — |
| `key_length` | 主键长度 | 12 |

示例：

```bash
./create_table.sh --mode range --fields 3 20 30000000 12
```

### key 表

```bash
./create_table.sh --mode key --fields N <num_partitions>
```

### key_range 表

```bash
./create_table.sh --mode key_range --fields N <range_partition_count> <key_subpartition_count> <start_timestamp> <partition_duration_ms>
```

| 参数 | 说明 |
|------|------|
| `range_partition_count` | 一级 range 分区数量 |
| `key_subpartition_count` | 二级 key 分区数量 |
| `start_timestamp` | 起始时间，支持毫秒时间戳或日期字符串 |
| `partition_duration_ms` | 每个 range 分区的时间跨度（毫秒） |

示例：

```bash
./create_table.sh --mode key_range --fields 2 4 48 '2024-01-01 00:00:00' 31536000000
```

脚本会输出 SQL 文件到 `obkv-table/` 目录，需手动在 OceanBase 中执行。

---

## 4. 操作类型说明

| 接口 | 说明 | 适用场景 |
|------|------|----------|
| `insert` | 纯插入，主键存在则报错 | 确保无重复写入 |
| `insertup` | 插入或更新，主键存在则更新 | 兼容 CDC 下游同步 |
| `put` | 覆盖写，性能最好 | 无 CDC 需求（需 `binlog_row_image='MINIMAL'`） |

通过以下参数控制：

```properties
obkv.insertType=put       # insert 操作使用的接口
obkv.updateType=put       # update 操作使用的接口
obkv.batchPutType=put     # batch_put 操作使用的接口
```

### 服务端 binlog_row_image 设置

使用 `put` 接口时，需在 OceanBase 服务端执行：

```sql
SET GLOBAL binlog_row_image='MINIMAL';
```

如有 CDC 下游同步组件，改用 `insertup`：

```properties
obkv.insertType=insertup
obkv.updateType=insertup
obkv.batchPutType=insertup
```

---

## 5. 完整配置示例

```properties
# ========== OceanBase 连接配置 ==========
obkv.isOdpMode=false
obkv.configUrl=
obkv.sysUserName=
obkv.sysPassword=
obkv.fullUserName=
obkv.password=
obkv.database=

# ========== 分区配置（key_range 表必须） ==========
obkv.rangePartitionStartTs=1704067200000
obkv.rangePartitionDurationMs=31536000000
obkv.rangePartitionCount=4
obkv.prefixCount=1000

# ========== YCSB 测试参数 ==========
workload=site.ycsb.workloads.CoreWorkload
table=kv_table
recordcount=1000000
operationcount=1000000
threadcount=10

# ========== 操作类型配置 ==========
obkv.insertType=insertup
obkv.updateType=update
obkv.batchPutType=insertup

# ========== 客户端配置 ==========
rpc.operation.timeout=2000
rpc.execute.timeout=3000
server.connection.pool.size=1
obkv.debug=false
```

> 各参数的完整说明请查阅 [参数配置大全](params-reference.md)。

---

## 6. 运行测试

```bash
./run_fast_test.sh load          # 加载数据
./run_fast_test.sh put           # 写入测试
./run_fast_test.sh read          # 读取测试（需先 load）
./run_fast_test.sh scan          # 扫描测试（需先 load）
./run_fast_test.sh batch_put     # 批量写入
./run_fast_test.sh batch_read    # 批量读取（需先 load）
```

> 运行测试的详细说明请参考 [命令行使用指南](guide-cli.md)。

---

## 相关文档

- [快速入门](getting-started.md) — 5 分钟跑通第一次测试
- [命令行使用指南](guide-cli.md) — 完整命令行操作流程
- [Web UI 使用指南](guide-webui.md) — 图形界面操作流程
- [参数配置大全](params-reference.md) — 所有可配置参数一览
- [Workload 参考](workload-reference.md) — Workload 文件模板与示例
