# 命令行使用指南

本文介绍如何通过命令行（黑屏）完成 OBKV 性能测试的完整流程。

> **快速跑通**请先阅读 [快速入门](getting-started.md) 路径 B。

---

## 1. 编译

每个模块有独立的 `build.sh`，编译后在 `build/` 目录生成 fat JAR。

```bash
# 以下命令均在项目根目录执行

# 编译 OBKV-Table
(cd obkv-table && ./build.sh)

# 编译 OBKV-HBase
(cd obkv-hbase && ./build.sh)

# 清理构建产物（在对应模块目录中执行）
(cd obkv-table && ./build.sh clean)
(cd obkv-hbase && ./build.sh clean)
```

也可通过 `deploy.sh` 一次编译所有模块：

```bash
./deploy.sh build
```

---

## 2. 建表

使用 `create_table.sh` 生成建表 SQL，然后在 OceanBase 中执行。

### OBKV-Table 建表

```bash
# 以下命令在 obkv-table/ 目录下执行
cd obkv-table

# 查看帮助
./create_table.sh --help

# range 表（默认，单分区）
./create_table.sh --mode range --fields 3 20 30000000 12
# 参数：--fields 列数 | 分区数 | 最大key | key长度(可选)

# key_range 表（二级分区，时序场景）
./create_table.sh --mode key_range --fields 2 4 48 '2024-01-01 00:00:00' 31536000000
# 参数：--fields 列数 | range分区数 | key子分区数 | 起始时间 | 分区时长(ms)
```

脚本在当前目录输出 SQL 文件（如 `kv_table_range_p20_k30000000.sql`），手动在 OceanBase 中执行即可。

### OBKV-HBase 建表

```bash
# 以下命令在 obkv-hbase/ 目录下执行
cd obkv-hbase

# 查看帮助
./create_table.sh --help

# 一级 Range 分区（默认 hbase 模型）
./create_table.sh --max_key 1000 --partition_count 4

# 一级 Key 分区
./create_table.sh --mode hbase --type first_part --partition_type key --partition_count 4

# 二级分区（Range + Key）
./create_table.sh --mode hbase --type sec_part \
  --range_partition_count 30 \
  --range_start_timestamp 1704067200000 \
  --range_partition_duration_ms 2592000000 \
  --key_subpartition_count 40
```

> 建表脚本参数的完整说明请参考 [OBKV-Table 模块详解](module-obkv-table.md) 或 [OBKV-HBase 模块详解](module-obkv-hbase.md)。

---

## 3. 配置 Workload

Workload 文件位于各模块的 `workloads/` 目录，每种操作一个文件：

| 文件 | 用途 |
|------|------|
| `workload_load` | 数据加载 |
| `workload_put` | 写入测试 |
| `workload_read` | 读取测试 |
| `workload_scan` | 扫描测试 |
| `workload_batch_put` | 批量写入 |
| `workload_batch_read` | 批量读取 |
| `workload_template` | 配置模板（参考用） |

运行前需编辑对应 workload 文件，**至少**填写以下内容：

### 连接参数（必填）

**OBKV-Table**：

```properties
obkv.isOdpMode=true
obkv.odpAddr=
obkv.odpPort=
obkv.fullUserName=
obkv.password=
obkv.database=
```

**OBKV-HBase**：

```properties
hbase.oceanbase.odpMode=true
hbase.oceanbase.odpAddr=
hbase.oceanbase.odpPort=
hbase.oceanbase.fullUserName=
hbase.oceanbase.password=
hbase.oceanbase.database=
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf
```

### 测试参数

```properties
recordcount=100000
operationcount=100000
threadcount=10
```

> 完整参数说明请查阅 [参数配置大全](params-reference.md)。

---

## 4. 运行测试

使用 `run_fast_test.sh` 运行测试，语法对两个模块一致：

```bash
./run_fast_test.sh <操作类型> [workload文件]
```

### 支持的操作类型

| 操作 | 说明 | 前置条件 |
|------|------|----------|
| `load` | 加载初始数据 | 无 |
| `put` | 持续写入测试 | 无 |
| `read` | 随机读取测试 | 需先 load |
| `scan` | 范围扫描测试 | 需先 load |
| `batch_put` | 批量写入测试 | 无 |
| `batch_read` | 批量读取测试 | 需先 load |
| `run <file>` | 使用自定义 workload 文件 | 视内容而定 |

### 典型测试流程

```bash
# 1. 先加载数据
./run_fast_test.sh load

# 2. 写入测试
./run_fast_test.sh put

# 3. 读取测试（需要先 load 数据）
./run_fast_test.sh read

# 4. 扫描测试（需要先 load 数据）
./run_fast_test.sh scan

# 5. 批量写入
./run_fast_test.sh batch_put

# 6. 批量读取（需要先 load 数据）
./run_fast_test.sh batch_read

# 使用自定义 workload 文件
./run_fast_test.sh run /path/to/my_workload
```

### 使用指定 workload 文件

默认情况下，每个操作使用对应的 `workloads/workload_<操作>` 文件。也可以显式指定：

```bash
./run_fast_test.sh put workloads/workload_put
./run_fast_test.sh load workloads/workload_read    # 为 read 测试加载数据
```

---

## 5. 结果解读

测试完成后，YCSB 在 stdout 输出结果汇总，关键指标：

```
[OVERALL], RunTime(ms), 35200
[OVERALL], Throughput(ops/sec), 28409.09

[READ], Operations, 1000000
[READ], AverageLatency(us), 823.5
[READ], 95thPercentileLatency(us), 1200
[READ], 99thPercentileLatency(us), 2100
[READ], MinLatency(us), 120
[READ], MaxLatency(us), 45000
[READ], Return=OK, 999998
[READ], Return=ERROR, 2
```

- **Throughput**：每秒操作数（ops/sec），数值越高越好
- **AverageLatency**：平均延迟（微秒），数值越低越好
- **95th/99thPercentileLatency**：P95/P99 延迟，衡量长尾
- **Return=OK/ERROR**：成功与失败计数

---

## 6. 服务端注意事项

### binlog_row_image 设置

写入操作默认使用 obkv `put` 接口（覆盖写语义），需要在 OceanBase 服务端设置：

```sql
SET GLOBAL binlog_row_image='MINIMAL';
```

如果有 CDC 下游同步组件，`put` 操作无法使用，需在 workload 中改用 `insertup` 接口：

```properties
obkv.insertType=insertup
obkv.updateType=insertup
obkv.batchPutType=insertup
```

> 此设置仅针对 OBKV-Table 模块，OBKV-HBase 不涉及。

---

## 相关文档

- [快速入门](getting-started.md) — 5 分钟跑通第一次测试
- [OBKV-Table 模块详解](module-obkv-table.md) — 表结构、分区策略、建表脚本完整说明
- [OBKV-HBase 模块详解](module-obkv-hbase.md) — 表模型、测试模式、分区策略完整说明
- [参数配置大全](params-reference.md) — 所有可配置参数一览
- [Workload 参考](workload-reference.md) — Workload 文件模板与完整示例
- [常见问题](faq.md) — 遇到问题先看这里
