# hbase094 模块

YCSB 与 **Apache HBase 0.94.x** 原生客户端（`HConnection` / `HTable`）的绑定，用于与 OBKV-HBase 压测结果做同口径对比。

| 模块 | 客户端 API | 适用服务端 |
|------|------------|------------|
| **`hbase094`**（本模块） | HBase 0.94 | HBase 0.94.x |
| `obkv-hbase` | OBKV-HBase Java client | OceanBase OBKV-HBase |

**不要用 HBase 1.x 客户端连接 0.94 集群**——协议与 API 不兼容。

## 源码

| 文件 | 说明 |
|------|------|
| `src/main/java/site/ycsb/db/hbase094/HBaseClient94.java` | YCSB `DB` 实现（读写、多版本 load、TimeRange Get） |
| `src/main/java/site/ycsb/db/hbase094/RunMain.java` | 入口类 |
| `src/main/java/.../bulkload/` | Bulk load TSV 生成（可选） |

## Workload 配置

| 文件 | 用途 |
|------|------|
| **`workloads/workload_native_hbase`** | 标准 KQTV 多版本表（与 `obkv-hbase/workloads/workload_obkv_native_hbase` 同口径） |
| `workload_native_hbase_smoke_put` | 小规模写入冒烟 |
| `workload_native_hbase_read_version_verify_smoke` | 读路径版本数校验冒烟 |
| `workload_mixed_read_write` | 混合读写 |

使用前在 workload 或 CLI 中配置 ZK、`hbase.master`、表名等连接项；规模参数通过 `-p` 传入（见下文）。

## 构建

需要 **JDK 8**（Java 11+ 运行 HBase 0.94 客户端可能报 `Unexpected version format`）、Maven 3.x。

```bash
# 在仓库根目录
mvn install -N -DskipTests -Dcheckstyle.skip=true -pl core -am

cd hbase094
mvn package -DskipTests -Dcheckstyle.skip=true
```

产物：`hbase094/target/hbase094-0.18.0-SNAPSHOT-jar-with-dependencies.jar`

## 运行

1. 在 HBase 0.94 集群创建 KQTV 多版本表（列族 `v`，10 个 qualifier，按 YCSB `zeropadding=20` 的 row key 预分区）。
2. 复制并编辑 `workloads/workload_native_hbase`，填写 ZK、`hbase.master`、`table` 等。
3. 执行 YCSB（`-db` 必须指定本模块客户端）：

```bash
JAR=hbase094/target/hbase094-0.18.0-SNAPSHOT-jar-with-dependencies.jar
WL=hbase094/workloads/workload_native_hbase

# Load（多版本 V=50 示例）
java -jar "${JAR}" -load -db site.ycsb.db.hbase094.HBaseClient94 -P "${WL}" -threads 64 \
  -p insertproportion=1 -p readproportion=0 \
  -p recordcount=10000000 -p operationcount=10000000 -p table=<your_table> \
  -p batchput.size.per.op=1 -p clientbuffering=false \
  -p hbase.rpc.timeout=180000 \
  -p core_workload_insertion_retry_limit=5 \
  -p hbase.versionsPerQualifier=50 -p hbase.versionDeltaMs=1000

# Read（纯读；读前建议 major compact，与 OBKV 对比时保持相同 V、recordcount、threads）
java -jar "${JAR}" -db site.ycsb.db.hbase094.HBaseClient94 -P "${WL}" -threads 96 \
  -p insertproportion=0 -p readproportion=1 \
  -p recordcount=10000000 -p operationcount=0 \
  -p maxexecutiontime=180 \
  -p table=<your_table>
```

YCSB 的 **`-p` 会覆盖** `-P` 文件中的同名属性；完整命令建议记入压测记录以便复现。

## 与 obkv-hbase 对齐

以下 workload / CLI 参数在两个模块中**语义相同**，对比压测时应保持一致：

- YCSB 标准：`recordcount`、`operationcount`、`table`、`zeropadding`、`insertorder`、`fieldcount`、`fieldlength`、proportion、`-threads`
- 多版本 load：`hbase.versionsPerQualifier`、`hbase.versionDeltaMs`、`hbase.versionAnchorTs`、`hbase.versionSpreadInWindow`、`hbase.versionWindowMs`
- 纯读 TimeRange：`hbase.readTimeRangeEnabled`、`hbase.readAnchorTs`、`hbase.readWindowMs`、`hbase.readMaxVersions`
- 稳定性：`batchput.size.per.op`、`clientbuffering`、`hbase.rpc.timeout`、`core_workload_insertion_retry_limit`

OBKV 侧连接与 `obkv.*` 扩展参数见 [obkv-hbase/README.md](../obkv-hbase/README.md)。

## 参数说明

### 连接（workload 或 `-p`）

| 参数 | 必填 | 说明 |
|------|------|------|
| `table` | 是 | HBase 表名 |
| `columnfamily` | 是 | 列族（默认 workload 为 `v`） |
| `hbase.zookeeper.quorum` | 是 | ZK 地址列表，逗号分隔 |
| `hbase.zookeeper.property.clientPort` | 否 | ZK 端口，默认 `2181` |
| `hbase.master` | 建议 | 集群外压测时显式指定 HMaster 地址 |
| `clientbuffering` | 否 | 客户端写缓冲；benchmark load 建议 `false` |
| `hbase.rpc.timeout` | 否 | RPC 超时（ms）；大表 load 建议 `180000` |
| `ipc.socket.timeout` | 否 | Socket 超时（ms） |
| `hbase.skipTableCheck` | 否 | `true` 跳过建连时表存在性检查 |
| `debug` | 否 | 客户端调试日志，默认 `false` |

Kerberos（可选）：`principal`、`keytab`。

### 多版本 load（`hbase.versionsPerQualifier` > 1）

一次 `-load` 为每个 `(row, qualifier)` 写入 V 个带显式时间戳的版本：

| 参数 | 必填 | 说明 |
|------|------|------|
| `hbase.versionsPerQualifier` | 否 | 每 qualifier 版本数；`1` 为单版本 Put |
| `hbase.versionDeltaMs` | V>1 时必填 | 版本间隔（ms）；`t_i = T0 - i × delta`，`i=0` 为最新 |
| `hbase.versionAnchorTs` | V>1 时必填 | 时间窗上界（ms）；最新版本 T0 不晚于此时间戳 |
| `hbase.versionSpreadInWindow` | 否 | `true` 时按 row key 在窗内打散 T0，默认 `true` |
| `hbase.versionWindowMs` | 否 | 打散窗口宽度，默认 `15552000000`（180 天） |

约束：`(V - 1) × versionDeltaMs < versionWindowMs`。

Load 建议同时设置：`batchput.size.per.op=1`（YCSB load 内部走 batchPut）。

### 纯读 TimeRange

| 参数 | 必填 | 说明 |
|------|------|------|
| `hbase.readTimeRangeEnabled` | 否 | `true` 时对每次 Get 设置 `setTimeRange` |
| `hbase.readAnchorTs` | 建议 | 窗上界（ms）；未设则回退 `hbase.versionAnchorTs` |
| `hbase.readWindowMs` | 否 | 窗宽度，默认 180 天；`minTs = anchor - window` |
| `hbase.readMaxVersions` | TimeRange 开启时必填 | Get 最大版本数；benchmark 常用 **2000** |

`readTimeRangeEnabled=false` 时可不设 `readMaxVersions`（使用 HBase 客户端默认）。

### 读路径版本校验（冒烟）

| 参数 | 说明 |
|------|------|
| `hbase.verifyReadVersionsEnabled` | `true` 开启校验 |
| `hbase.verifyReadVersionsExpected` | 期望每个 qualifier 的版本数 |
| `hbase.verifyReadVersionsFieldCount` | 校验的 field 数，默认 `10` |

默认关闭，不影响正式 benchmark。

## 冒烟测试

```bash
java -jar hbase094/target/hbase094-0.18.0-SNAPSHOT-jar-with-dependencies.jar \
  -db site.ycsb.db.hbase094.HBaseClient94 \
  -P hbase094/workloads/workload_native_hbase_smoke_put \
  -p table=<your_table>
```

更多构建与运行说明见仓库根目录 [README.md](../README.md)。
