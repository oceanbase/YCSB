# Workload 参考

本文介绍 Workload 文件的结构和作用，并提供各操作类型的完整示例模板。

> 各参数的含义、类型和默认值请查阅 [参数配置大全](params-reference.md)。

---

## 1. Workload 文件说明

Workload 文件是标准的 Java Properties 格式，每行一个 `key=value` 配置项。文件位于各模块的 `workloads/` 目录：

| 文件 | 用途 | 前置条件 |
|------|------|----------|
| `workload_load` | 数据加载 | 无 |
| `workload_put` | 写入测试 | 无 |
| `workload_read` | 读取测试 | 需先 load |
| `workload_scan` | 扫描测试 | 需先 load |
| `workload_batch_put` | 批量写入 | 无 |
| `workload_batch_read` | 批量读取 | 需先 load |
| `workload_template` | 配置模板（参考用） | — |

Web UI 保存的配置同样是标准 workload 文件，可直接用于命令行。

---

## 2. 文件结构

一个典型的 workload 文件包含以下几个部分：

```properties
# 1. Workload 类（通常不需修改）
workload=site.ycsb.workloads.CoreWorkload

# 2. 数据集与操作配置
recordcount=100000
operationcount=100000
threadcount=10
fieldcount=10
fieldlength=100

# 3. 操作比例（总和为 1.0）
readproportion=1
updateproportion=0
insertproportion=0
scanproportion=0

# 4. 连接配置（根据模块不同，参数前缀不同）
# ... ODP / 直连参数

# 5. 模块特有参数
# ... 分区参数、测试模式参数等

# 6. 客户端配置
# ... 超时、连接池等
```

---

## 3. OBKV-Table 示例

### Load（数据加载）

```properties
workload=site.ycsb.workloads.CoreWorkload
table=kv_table
recordcount=1000000
operationcount=1000000
threadcount=10
fieldcount=3
fieldlength=100
insertproportion=1

# 连接（ODP 模式）
obkv.isOdpMode=true
obkv.odpAddr=
obkv.odpPort=
obkv.fullUserName=
obkv.password=
obkv.database=

# 操作类型
obkv.insertType=put
```

### Read（读取测试）

```properties
workload=site.ycsb.workloads.CoreWorkload
table=kv_table
recordcount=1000000
operationcount=1000000
threadcount=10
fieldcount=3
fieldlength=100
readproportion=1

# 连接
obkv.isOdpMode=true
obkv.odpAddr=
obkv.odpPort=
obkv.fullUserName=
obkv.password=
obkv.database=
```

### Put（写入测试）

```properties
workload=site.ycsb.workloads.CoreWorkload
table=kv_table
recordcount=1000000
operationcount=1000000
threadcount=10
fieldcount=3
fieldlength=100
updateproportion=1

obkv.isOdpMode=true
obkv.odpAddr=
obkv.odpPort=
obkv.fullUserName=
obkv.password=
obkv.database=

obkv.updateType=put
```

### Scan（扫描测试，key_range 表）

```properties
workload=site.ycsb.workloads.CoreWorkload
table=kv_table
recordcount=1000000
operationcount=1000000
threadcount=10
fieldcount=2
fieldlength=100
scanproportion=1

obkv.isOdpMode=true
obkv.odpAddr=
obkv.odpPort=
obkv.fullUserName=
obkv.password=
obkv.database=

# 二级分区参数（必须与建表一致）
obkv.rangePartitionStartTs=1704067200000
obkv.rangePartitionDurationMs=31536000000
obkv.rangePartitionCount=4
obkv.prefixCount=1000
```

### Batch Read（批量读取）

```properties
workload=site.ycsb.workloads.CoreWorkload
table=kv_table
recordcount=1000000
operationcount=1000000
threadcount=10
fieldcount=3
fieldlength=100
batchreadproportion=1
batchread.size.per.op=10

obkv.isOdpMode=true
obkv.odpAddr=
obkv.odpPort=
obkv.fullUserName=
obkv.password=
obkv.database=
```

---

## 4. OBKV-HBase 示例

### Load（数据加载，默认模式）

```properties
workload=site.ycsb.workloads.CoreWorkload
recordcount=100000
operationcount=100000
threadcount=10
fieldcount=10
fieldlength=100
insertproportion=1

# 连接
hbase.oceanbase.odpMode=true
hbase.oceanbase.odpAddr=
hbase.oceanbase.odpPort=
hbase.oceanbase.fullUserName=
hbase.oceanbase.password=
hbase.oceanbase.database=

# 表
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf

# 模式
obkv.testMode=default
obkv.maxKey=1000
```

### Read（读取测试）

```properties
workload=site.ycsb.workloads.CoreWorkload
recordcount=100000
operationcount=10000
threadcount=10
readproportion=1

hbase.oceanbase.odpMode=true
hbase.oceanbase.odpAddr=
hbase.oceanbase.odpPort=
hbase.oceanbase.fullUserName=
hbase.oceanbase.password=
hbase.oceanbase.database=
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf

obkv.testMode=default
obkv.maxKey=1000
```

### Load + Read（前缀查询模式，二级分区表）

```properties
workload=site.ycsb.workloads.CoreWorkload
recordcount=100000
operationcount=10000
threadcount=10
fieldcount=10
fieldlength=100
insertorder=ordered    # 前缀模式必须

hbase.oceanbase.odpMode=true
hbase.oceanbase.odpAddr=
hbase.oceanbase.odpPort=
hbase.oceanbase.fullUserName=
hbase.oceanbase.password=
hbase.oceanbase.database=
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf

obkv.testMode=prefix
obkv.prefixCount=100
obkv.rangePartitionCount=30
obkv.rangePartitionStartTs=1704067200000
obkv.rangePartitionDurationMs=2592000000
obkv.enablePastTime=true
obkv.pastTime=1704067200000
```

### Scan（使用 PageFilter）

```properties
workload=site.ycsb.workloads.CoreWorkload
recordcount=100000
operationcount=10000
threadcount=10
scanproportion=1

hbase.oceanbase.odpMode=true
hbase.oceanbase.odpAddr=
hbase.oceanbase.odpPort=
hbase.oceanbase.fullUserName=
hbase.oceanbase.password=
hbase.oceanbase.database=
hbase.oceanbase.table=ycsb_test
hbase.oceanbase.columnFamily=cf

obkv.testMode=default
obkv.maxKey=1000
obkv.scan.usePageFilter=true
obkv.scan.pageFilterSize=100
```

---

## 相关文档

- [参数配置大全](params-reference.md) — 各参数的含义、类型、默认值
- [OBKV-Table 模块详解](module-obkv-table.md) — 表结构与分区策略
- [OBKV-HBase 模块详解](module-obkv-hbase.md) — 表模型与测试模式
- [命令行使用指南](guide-cli.md) — 如何运行测试
- [Web UI 使用指南](guide-webui.md) — 图形界面使用方法
