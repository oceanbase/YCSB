# YCSB OBKV-HBase & HBase 0.94

OceanBase OBKV-HBase 与 Apache HBase 0.94 的 [YCSB](https://github.com/brianfrankcooper/YCSB) 绑定，用于标准 load / read / scan 压测及同口径性能对比。

本仓库仅包含 **Java 源码**、**Maven 构建** 与 **workload 配置**；不含部署脚本与压测日志。

## 目录

| 路径 | 说明 |
|------|------|
| `core/` | YCSB 核心 |
| `obkv-hbase/` | OBKV-HBase 绑定；参数见 [obkv-hbase/README.md](obkv-hbase/README.md) |
| `hbase094/` | HBase 0.94 原生客户端；参数见 [hbase094/README.md](hbase094/README.md) |
| `obkv-hbase/workloads/` | OBKV workload（推荐 `workload_obkv_native_hbase`） |
| `hbase094/workloads/` | 原生 HBase workload（推荐 `workload_native_hbase`） |

两个模块的 KQTV 多版本 benchmark 使用**同一套** `hbase.versionsPerQualifier`、TimeRange 等参数语义。下文示例统一：

| 变量 | 含义 | 示例 |
|------|------|------|
| `TABLE` | 表名 | `ycsb_bench` |
| `RC` | `recordcount` / `operationcount` | `25770000` |
| `V` | 每 qualifier 版本数 | `50` |
| `PARTITIONS` | 分区 / Region 数 | **180** |

Workload 中 `zeropadding=20`、`insertorder=ordered`、`fieldcount=10`、`fieldlength=20` 与建表预分区对齐。

## 构建

需要 JDK 8+、Maven 3.x。

```bash
# 安装父 POM 与 core
mvn install -N -DskipTests -Dcheckstyle.skip=true -pl core -am

# OBKV-HBase fat jar
cd obkv-hbase && mvn package -DskipTests -Dcheckstyle.skip=true

# HBase 0.94 fat jar
cd ../hbase094 && mvn package -DskipTests -Dcheckstyle.skip=true
```

产物：

- `obkv-hbase/target/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar`
- `hbase094/target/hbase094-0.18.0-SNAPSHOT-jar-with-dependencies.jar`

`obkv-hbase/pom.xml` 默认依赖已发布 driver：`obkv-hbase-client` **2.3.0**、`obkv-table-client` **2.1.0**。若需本地 SNAPSHOT driver，先 `mvn install` 对应仓库再打包。

---

## OBKV-HBase

### 1. 建表

在 OceanBase 租户中执行（`表名$列族` 格式；列族与 workload 中 `columnfamily` / `hbase.oceanbase.columnFamily` 一致）：

```sql
CREATE TABLE ycsb_bench$v (
  K VARBINARY(1024),
  Q VARBINARY(256),
  T BIGINT,
  V VARBINARY(1048576) NOT NULL,
  PRIMARY KEY(K, Q, T))
KV_ATTRIBUTES ='{"Hbase": {"TimeToLive": 15552000, "MaxVersions": 2000}}'
PARTITION BY KEY(K) PARTITIONS 180;
```

| 项 | 说明 |
|----|------|
| `TimeToLive` | `15552000` 秒 = 180 天，与读 TimeRange 窗口一致 |
| `MaxVersions` | `2000`，与线上一致 |
| `PARTITIONS` | **180**，与 HBase 侧 180 Region 对齐 |

### 2. Load 数据

1. 编辑 `obkv-hbase/workloads/workload_obkv_native_hbase`，填写 OB 连接（`hbase.oceanbase.paramURL`、`fullUserName` 等）与 `hbase.oceanbase.table`。
2. 执行 load（单轮写入 `V` 版本 / qualifier）：

```bash
OBKV_JAR=obkv-hbase/target/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar
WL=obkv-hbase/workloads/workload_obkv_native_hbase
TABLE=ycsb_bench
RC=25770000
V=50

java -jar "${OBKV_JAR}" -load -t -s \
  -P "${WL}" -threads 64 \
  -p insertproportion=1 -p readproportion=0 \
  -p recordcount="${RC}" -p operationcount="${RC}" \
  -p table="${TABLE}" -p hbase.oceanbase.table="${TABLE}" \
  -p batchput.size.per.op=1 -p clientbuffering=false \
  -p hbase.rpc.timeout=180000 \
  -p core_workload_insertion_retry_limit=5 \
  -p hbase.versionsPerQualifier="${V}" -p hbase.versionDeltaMs=1000
```

### 3. Compaction

读压测或混合压测前建议先做 **租户级 major compaction**。

**触发**（使用 `root@sys` 连接集群）：

```sql
-- 可选：临时提高合并线程（租户名按实际修改，示例 obkv）
ALTER SYSTEM SET compaction_low_thread_score = 60;

ALTER SYSTEM MAJOR FREEZE TENANT = obkv;
```

**确认合并完成**：轮询直到先出现 `COMPACTING`、再回到 `IDLE`：

```sql
SELECT status FROM oceanbase.DBA_OB_MAJOR_COMPACTION;
```

| `status` | 含义 |
|----------|------|
| `COMPACTING` | 合并进行中 |
| `IDLE` | 空闲；在触发 freeze 且曾见过 `COMPACTING` 后变为 `IDLE`，表示本轮 major 结束 |

合并结束后可恢复线程（示例）：

```sql
ALTER SYSTEM SET compaction_low_thread_score = 6;
```

### 4. 纯读测试（可选）

读前完成 §3 compaction。Workload 已配置 180 天 TimeRange（`hbase.readTimeRangeEnabled=true`、`hbase.readMaxVersions=2000`）。

```bash
java -jar "${OBKV_JAR}" -t -s \
  -P "${WL}" -threads 96 \
  -p insertproportion=0 -p readproportion=1 \
  -p updateproportion=0 -p scanproportion=0 \
  -p recordcount="${RC}" -p operationcount=0 -p maxexecutiontime=180 \
  -p table="${TABLE}" -p hbase.oceanbase.table="${TABLE}"
```

### 5. 读写混合测试

在已完成 load + compaction 的表上运行 **7:3 读:写**（单行 Get + 单行 Put，**不要**设置 `hbase.versionsPerQualifier`，写路径为单版本）。

```bash
java -jar "${OBKV_JAR}" -t -s \
  -P "${WL}" -threads 96 \
  -p insertproportion=0 -p readproportion=0.7 -p updateproportion=0.3 \
  -p scanproportion=0 -p readmodifywriteproportion=0 \
  -p recordcount="${RC}" -p operationcount=0 -p maxexecutiontime=1800 \
  -p table="${TABLE}" -p hbase.oceanbase.table="${TABLE}"
```

也可参考 `obkv-hbase/workloads/workload_mixed_read_write`（需改连接与表名）。

---

## HBase 0.94

### 1. 建表

YCSB row key 为 `zeropadding=20` 的数字串（`insertorder=ordered`，范围 `[0, recordcount)`）。建表时按 key **等分预切 180 个 Region**，与 OBKV **180 个 KEY 分区**对齐。

在 HBase Master 上进入 `hbase shell`，先生成 split 点（`RC` 与 load 的 `recordcount` 一致）：

```bash
RC=25770000
REGIONS=180
PAD=20
SPLITS=$(python3 -c "
rc, regions, pad = ${RC}, ${REGIONS}, ${PAD}
print(', '.join(\"'\" + str((rc * i) // regions).zfill(pad) + \"'\" for i in range(1, regions)))
")
```

创建表（列族 `v`，TTL 180 天，最大版本 2000）：

```bash
hbase shell <<EOF
create 'ycsb_bench',
  {NAME => 'v', VERSIONS => 2000, TTL => 15552000,
   DATA_BLOCK_ENCODING => 'DIFF', BLOOMFILTER => 'ROW'},
  {SPLITS => [${SPLITS}]}
describe 'ycsb_bench'
exit
EOF
```

确认 `describe` 输出 Region 数为 **180**（或 `list_regions` 统计为 180）。

### 2. Load 数据

1. 编辑 `hbase094/workloads/workload_native_hbase`，填写 `hbase.zookeeper.quorum`、`hbase.master` 等。
2. 执行 load（**必须**指定 `-db site.ycsb.db.hbase094.HBaseClient94`）：

```bash
HBASE_JAR=hbase094/target/hbase094-0.18.0-SNAPSHOT-jar-with-dependencies.jar
WL=hbase094/workloads/workload_native_hbase
TABLE=ycsb_bench
RC=25770000
V=50

java -jar "${HBASE_JAR}" -load -t -s \
  -db site.ycsb.db.hbase094.HBaseClient94 \
  -P "${WL}" -threads 64 \
  -p insertproportion=1 -p readproportion=0 \
  -p recordcount="${RC}" -p operationcount="${RC}" -p table="${TABLE}" \
  -p batchput.size.per.op=1 -p clientbuffering=false \
  -p hbase.rpc.timeout=180000 \
  -p core_workload_insertion_retry_limit=5 \
  -p hbase.versionsPerQualifier="${V}" -p hbase.versionDeltaMs=1000
```

### 3. Compaction

读压测或混合压测前对目标表执行 **major compact**。

**触发**（HBase shell）：

```bash
hbase shell <<EOF
major_compact 'ycsb_bench'
exit
EOF
```

**确认合并完成**：

1. **RS 日志**：各 RegionServer 日志出现 `Completed major compaction`（全部 Region 各一条）。
2. **HBase shell**：`status 'detailed'` 中目标表各 Region 的 `compaction_queue=(0:0)`，StoreFile 数趋于稳定（major 后常见每 Region **2** 个 StoreFile）。
3. **可选**：`hbase hbck` 或监控 HDFS 占用在 major 后下降（删除 merge 前的旧 HFile）。

### 4. 纯读测试（可选）

读前完成 §3 major compact。Workload 已配置 180 天 TimeRange + `hbase.readMaxVersions=2000`。

```bash
java -jar "${HBASE_JAR}" -t -s \
  -db site.ycsb.db.hbase094.HBaseClient94 \
  -P "${WL}" -threads 96 \
  -p insertproportion=0 -p readproportion=1 \
  -p updateproportion=0 -p scanproportion=0 \
  -p recordcount="${RC}" -p operationcount=0 -p maxexecutiontime=180 \
  -p table="${TABLE}"
```

### 5. 读写混合测试

在已完成 load + major compact 的表上运行 **7:3 读:写**（**不要**设置 `hbase.versionsPerQualifier`）。

```bash
java -jar "${HBASE_JAR}" -t -s \
  -db site.ycsb.db.hbase094.HBaseClient94 \
  -P "${WL}" -threads 96 \
  -p insertproportion=0 -p readproportion=0.7 -p updateproportion=0.3 \
  -p scanproportion=0 -p readmodifywriteproportion=0 \
  -p recordcount="${RC}" -p operationcount=0 -p maxexecutiontime=1800 \
  -p table="${TABLE}"
```

读路径 TimeRange 与纯读相同，使用同一 `workload_native_hbase` 即可（见 `hbase094/workloads/workload_mixed_read_write` 注释）。

---

## 对比口径提示

| 项 | OBKV-HBase | HBase 0.94 |
|----|------------|------------|
| 分区数 | `PARTITION BY KEY(K) PARTITIONS 180` | 预切 **180** Region |
| 多版本 load | `hbase.versionsPerQualifier` + `versionDeltaMs` | 相同 |
| 纯读 | TimeRange 180d + `readMaxVersions=2000` | 相同 |
| 读前整理 | 租户 `MAJOR FREEZE` + `DBA_OB_MAJOR_COMPACTION` | 表级 `major_compact` |
| 混合写 | 单版本 Put（不设 `versionsPerQualifier`） | 相同 |

更完整的参数说明见各模块 README。

## License

Apache License 2.0 — 见 [LICENSE.txt](LICENSE.txt)。
