# obkv-hbase 模块

YCSB 与 OceanBase **OBKV-HBase** 兼容 API 的绑定，用于标准 load / read / scan 压测，并与 [hbase094](../hbase094/README.md) 原生 HBase 0.94 做同口径对比。

## 源码

| 文件 | 说明 |
|------|------|
| `src/main/java/com/oceanbase/obkv/ycsb/OBHBaseClient.java` | YCSB `DB` 实现（读写、scan、多版本、TimeRange） |
| `src/main/java/com/oceanbase/obkv/RunMain.java` | 入口类 |
| `src/main/java/com/oceanbase/obkv/bulkload/BulkLoadDataGenerator.java` | Bulk load 数据生成（可选） |

## Workload 配置

`workloads/` 下属性文件通过 `-P` 传入 YCSB CLI。

| 文件 | 用途 |
|------|------|
| **`workload_obkv_native_hbase`** | 标准 KQTV 多版本表（与 `hbase094/workloads/workload_native_hbase` 同口径） |
| `workload_load` / `workload_read` / `workload_put` / `workload_scan` | 分操作模板 |
| `workload_batch_put` / `workload_batch_read` | 批量读写 |
| `workload_mixed_read_write` | 混合读写 |
| `workload_template` | 空白模板 |

**推荐**：以 `workload_obkv_native_hbase` 为基准，在文件中填写 OB 连接信息，规模与 V 通过 CLI `-p` 覆盖。

## 构建与运行

见仓库根目录 [README.md](../README.md)。

## 快速示例

```bash
JAR=obkv-hbase/target/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar
WL=obkv-hbase/workloads/workload_obkv_native_hbase

# Load（V=50）
java -jar "${JAR}" -load -P "${WL}" -threads 64 \
  -p insertproportion=1 -p readproportion=0 \
  -p recordcount=10000000 -p operationcount=10000000 \
  -p hbase.oceanbase.table=<your_table> \
  -p hbase.versionsPerQualifier=50 -p hbase.versionDeltaMs=1000

# Read
java -jar "${JAR}" -P "${WL}" -threads 96 \
  -p insertproportion=0 -p readproportion=1 \
  -p recordcount=10000000 -p operationcount=0 -p maxexecutiontime=180 \
  -p hbase.oceanbase.table=<your_table>
```

表名可用 YCSB 标准 `-p table=` 或 workload 中的 `hbase.oceanbase.table`（二者取其一，见下表）。

---

## 参数说明

### YCSB 标准参数（CLI `-p`）

| 参数 | 说明 |
|------|------|
| `recordcount` | 逻辑记录数 / row key 上界 |
| `operationcount` | 本次运行操作数；纯读限时压测可设 `0` 并用 `maxexecutiontime` |
| `table` | 表名（YCSB 标准属性；与 `hbase.oceanbase.table` 二选一） |
| `insertproportion` / `readproportion` / `updateproportion` / `scanproportion` | 操作比例 |
| `zeropadding` | Row key 零填充宽度，benchmark 常用 `20` |
| `insertorder` | `ordered`（顺序 key）或 `hashed` |
| `fieldcount` / `fieldlength` | 列数与列值长度 |
| `batchput.size.per.op` | Load 时每 op 的 batch 大小，建议 `1` |
| `clientbuffering` | 写缓冲，benchmark 建议 `false` |
| `core_workload_insertion_retry_limit` | Load 失败重试次数 |
| `maxexecutiontime` | 最长运行时间（秒），纯读常用 |

### OBKV 连接

| 参数 | 必填 | 说明 |
|------|------|------|
| `isObkv` | 否 | 是否 OBKV 模式，默认 `true` |
| `hbase.oceanbase.table` | 是* | OBKV 表名（也可用 `table`） |
| `hbase.oceanbase.columnFamily` / `columnfamily` | 是 | 列族 |
| `hbase.oceanbase.fullUserName` | 是 | 租户用户，如 `user@tenant#cluster` |
| `hbase.oceanbase.password` | 否 | 用户密码 |
| `hbase.oceanbase.odpMode` | 否 | `true` 走 ODP；`false` 直连 RS（默认） |

**直连模式**（`odpMode=false`）：

| 参数 | 必填 | 说明 |
|------|------|------|
| `hbase.oceanbase.paramURL` | 是 | OCP/RootService 配置 URL，示例：`http://<ocp_host>8080/services?User_ID=alibaba&UID=test&Action=ObRootServiceInfo&<cluster_name>&database=<database>` |
| `hbase.oceanbase.sysUserName` | 是 | 系统用户名 |
| `hbase.oceanbase.sysPassword` | 否 | 系统用户密码 |

**ODP 模式**（`odpMode=true`）：

| 参数 | 必填 | 说明 |
|------|------|------|
| `hbase.oceanbase.odpAddr` | 是 | ODP 地址 |
| `hbase.oceanbase.odpPort` | 是 | ODP 端口 |
| `hbase.oceanbase.database` | 是 | 数据库名 |

此外，所有 `com.alipay.oceanbase.rpc.property.Property` 定义的 key 均可写在 workload 中（未列出的常见项）：

| 参数 | 说明 |
|------|------|
| `server.connection.pool.size` | 连接池大小 |
| `rpc.operation.timeout` | RPC 操作超时（ms） |
| `rpc.execute.timeout` | RPC 执行超时（ms） |
| `hbase.rpc.shortoperation.timeout` | 短操作超时（ms） |

### 多版本 load（与 hbase094 相同语义）

| 参数 | 必填 | 说明 |
|------|------|------|
| `hbase.versionsPerQualifier` | 否 | 每 qualifier 版本数，默认 `1` |
| `hbase.versionDeltaMs` | V>1 时必填 | 版本时间间隔（ms） |
| `hbase.versionAnchorTs` | V>1 时必填 | 时间窗锚点 T₀ 上界（ms） |
| `hbase.versionSpreadInWindow` | 否 | 是否按 key 在窗内打散 T₀，默认 `true` |
| `hbase.versionWindowMs` | 否 | 打散窗口，默认 180 天 |

### 纯读 TimeRange（与 hbase094 相同语义）

| 参数 | 必填 | 说明 |
|------|------|------|
| `hbase.readTimeRangeEnabled` | 否 | 是否对 Get 设置 TimeRange |
| `hbase.readAnchorTs` | 建议 | 窗上界；未设则用 `hbase.versionAnchorTs` |
| `hbase.readWindowMs` | 否 | 窗宽度 |
| `hbase.readMaxVersions` | TimeRange 开启时必填 | Get `maxVersions`，benchmark 常用 `2000` |

### 读路径版本校验（冒烟）

| 参数 | 说明 |
|------|------|
| `hbase.verifyReadVersionsEnabled` | 开启版本数校验 |
| `hbase.verifyReadVersionsExpected` | 期望版本数（如 `50`） |
| `hbase.verifyReadVersionsFieldCount` | 校验列数，默认 `10` |

### RPC 与 HBase 兼容项

| 参数 | 说明 |
|------|------|
| `hbase.rpc.timeout` | 同步到 `hbase.rpc.timeout` / `hbase.client.operation.timeout` / scanner 超时 |
| `hbase.htable.use.put.optimization` | OBKV Put 优化开关，未设默认 `false` |
| `hbase.zookeeper.quorum` 等 | `isObkv=false` 时走原生 HBase 连接（一般不用于 OBKV benchmark） |

### OBKV 扩展：Key / 分区 / Scan

| 参数 | 说明 |
|------|------|
| `obkv.testMode` | `default`（默认）或 `prefix`（前缀分区测试） |
| `obkv.maxKey` | Key 取模上界 |
| `obkv.prefixCount` | `prefix` 模式下前缀个数，默认 `1000` |
| `obkv.rangePartitionStartTs` | 一级 range 分区起始时间戳（ms） |
| `obkv.rangePartitionDurationMs` | 每个 range 分区时间跨度（ms） |
| `obkv.rangePartitionCount` | 一级 range 分区个数（与上两项配合二级分区场景） |
| `obkv.scan.usePageFilter` | Scan 是否加 `PageFilter`，默认 `false` |
| `obkv.scan.pageFilterSize` | PageFilter 行数；未设则用 `recordcount` |
| `obkv.debug` | 客户端调试日志 |

---

## 与 hbase094 对比清单

对比 OBKV 与原生 HBase 0.94 时，建议固定以下项一致：

1. `recordcount`、`zeropadding`、`insertorder`、`fieldcount`、`fieldlength`
2. `hbase.versionsPerQualifier` 及 version / read TimeRange 相关项
3. `-threads`、proportion、load 稳定性参数（`batchput.size.per.op`、`hbase.rpc.timeout` 等）
4. 表 schema（KQTV、列族 `v`、分区数）

原生 HBase 侧用法见 [hbase094/README.md](../hbase094/README.md)。
