# 参数配置大全

本文列出 YCSB for OBKV 所有可配置参数的含义、类型、默认值和适用模块。Web UI 和命令行使用同一套参数。

> 如需查看 workload 文件模板和完整示例，请参考 [Workload 参考](workload-reference.md)。

---

## 1. YCSB 核心参数

### 基础配置

| 参数名 | 说明 | 类型 | 默认值 | 备注 |
|--------|------|------|--------|------|
| `workload` | Workload 实现类 | string | `site.ycsb.workloads.CoreWorkload` | 通常不需修改 |
| `recordcount` | 数据集总记录数 | int | — | load 阶段写入此数量；run 阶段用于计算 key 范围 |
| `operationcount` | 运行阶段操作总数 | int | — | 一般令 operationcount = recordcount |
| `threadcount` | 并发线程数 | int | 1 | 根据压力需求调整 |
| `fieldcount` | 每条记录的字段数 | int | 10 | 对应建表中的 field0..field{N-1} |
| `fieldlength` | 每个字段的字节长度 | int | 100 | 影响行大小和网络传输量 |
| `table` | 压测目标表名 | string | `usertable` | OBKV-Table 一般设为 `kv_table`；OBKV-HBase 通过 `hbase.oceanbase.table` 设置 |

### 操作比例

所有比例之和应为 1.0。

| 参数名 | 说明 | 类型 | 默认值 |
|--------|------|------|--------|
| `readproportion` | 读取操作比例 | double | 0.95 |
| `updateproportion` | 更新操作比例 | double | 0.05 |
| `insertproportion` | 插入操作比例 | double | 0 |
| `scanproportion` | 扫描操作比例 | double | 0 |
| `batchputproportion` | 批量写入比例 | double | 0 |
| `batchreadproportion` | 批量读取比例 | double | 0 |

### 请求分布

| 参数名 | 说明 | 类型 | 默认值 | 可选值 |
|--------|------|------|--------|--------|
| `requestdistribution` | 请求的 key 分布模式 | string | uniform | `uniform`：均匀分布；`zipfian`：热点倾斜；`hotspot`：热点集中；`latest`：最新数据优先 |
| `insertorder` | Key 生成顺序 | string | hashed | `hashed`：哈希打散；`ordered`：顺序递增（prefix 模式必须为 ordered） |

### 批量操作

| 参数名 | 说明 | 类型 | 默认值 | 适用模块 |
|--------|------|------|--------|----------|
| `batchread.size.per.op` | 每次 batch_read 读取的 key 数量 | int | — | 两模块通用 |

---

## 2. OBKV-Table 专用参数

### 连接配置

| 参数名 | 说明 | 类型 | 必填 | 备注 |
|--------|------|------|------|------|
| `obkv.isOdpMode` | 连接模式 | boolean | 是 | `true`=ODP 模式，`false`=直连模式 |
| `obkv.odpAddr` | ODP 代理地址 | string | ODP 模式必填 | |
| `obkv.odpPort` | ODP RPC 端口 | int | ODP 模式必填 | 默认 2883 |
| `obkv.configUrl` | 直连模式连接 URL | string | 直连模式必填 | |
| `obkv.sysUserName` | 系统租户用户名 | string | 直连模式必填 | |
| `obkv.sysPassword` | 系统租户密码 | string | 直连模式必填 | |
| `obkv.fullUserName` | 业务租户用户名 | string | 是 | 格式：`user@tenant` |
| `obkv.password` | 业务租户密码 | string | 是 | |
| `obkv.database` | 数据库名 | string | 是 | |

### 分区配置（二级分区表必填）

| 参数名 | 说明 | 类型 | 必填 | 示例值 |
|--------|------|------|------|--------|
| `obkv.rangePartitionStartTs` | 第一个 range 分区起始时间戳（毫秒） | long | 是 | `1704067200000` |
| `obkv.rangePartitionDurationMs` | 每个 range 分区时间跨度（毫秒） | long | 是 | `31536000000`（1 年） |
| `obkv.rangePartitionCount` | 一级 range 分区数量 | int | 是 | `4` |
| `obkv.prefixCount` | 前缀 ID 总数（对应 key_range 表的 ycsb_id 循环数量） | int | 是 | `1000` |

> 以上参数必须与 `create_table.sh` 建表时使用的值一致。

### 操作类型配置

| 参数名 | 说明 | 可选值 | 默认值 |
|--------|------|--------|--------|
| `obkv.insertType` | insert 操作使用的 obkv 接口 | `insert`, `insertup`, `put` | `put` |
| `obkv.updateType` | update 操作使用的 obkv 接口 | `update`, `insertup`, `put` | `put` |
| `obkv.batchPutType` | batch_put 操作使用的 obkv 接口 | `insert`, `insertup`, `put` | `put` |

**接口说明**：
- `insert`：纯插入，主键存在则报错
- `insertup`：插入或更新，主键存在则更新（兼容 CDC）
- `put`：覆盖写，性能最好，但需要服务端 `binlog_row_image='MINIMAL'`

### 客户端配置

| 参数名 | 说明 | 类型 | 默认值 |
|--------|------|------|--------|
| `rpc.operation.timeout` | OB 内部 RPC 超时（毫秒） | int | 2000 |
| `rpc.execute.timeout` | RPC socket 超时（毫秒） | int | 3000 |
| `server.connection.pool.size` | 连接池大小 | int | 1 |
| `obkv.debug` | 调试模式 | boolean | false |

---

## 3. OBKV-HBase 专用参数

### 连接配置

| 参数名 | 说明 | 类型 | 必填 | 备注 |
|--------|------|------|------|------|
| `hbase.oceanbase.odpMode` | 连接模式 | boolean | 是 | `true`=ODP，`false`=直连 |
| `hbase.oceanbase.odpAddr` | ODP 地址 | string | ODP 模式必填 | |
| `hbase.oceanbase.odpPort` | ODP 端口 | int | ODP 模式必填 | 默认 2883 |
| `hbase.oceanbase.paramURL` | 直连模式参数 URL | string | 直连模式必填 | |
| `hbase.oceanbase.sysUserName` | 系统用户名 | string | 直连模式必填 | |
| `hbase.oceanbase.sysPassword` | 系统用户密码 | string | 直连模式必填 | |
| `hbase.oceanbase.fullUserName` | 完整用户名 | string | 是 | 格式：`user@tenant#cluster` |
| `hbase.oceanbase.password` | 用户密码 | string | 是 | |
| `hbase.oceanbase.database` | 数据库名 | string | 是 | |

### 表配置

| 参数名 | 说明 | 类型 | 必填 | 默认值 |
|--------|------|------|------|--------|
| `hbase.oceanbase.table` | 表名（不含列族） | string | 是 | `ycsb_test` |
| `hbase.oceanbase.columnFamily` | 列族名 | string | 是 | `cf` |

### 测试模式配置

| 参数名 | 说明 | 类型 | 必填 | 适用模式 | 默认值 |
|--------|------|------|------|----------|--------|
| `obkv.testMode` | 测试模式 | string | 否 | 所有 | `default` |
| `obkv.maxKey` | Key 最大值（取余处理） | long | 否 | default | Long.MAX_VALUE |
| `obkv.prefixCount` | 前缀 ID 总数 | int | prefix 模式必填 | prefix | — |
| `obkv.rangePartitionCount` | Range 分区数量 | int | 二级分区必填 | prefix | — |
| `obkv.rangePartitionStartTs` | Range 分区起始时间戳(ms) | long | 二级分区必填 | prefix | — |
| `obkv.rangePartitionDurationMs` | Range 分区时间跨度(ms) | long | 二级分区必填 | prefix | — |
| `obkv.enablePastTime` | 启用过去时间 | boolean | 否 | prefix（二级分区） | false |
| `obkv.pastTime` | 过去时间基准点(ms) | long | 否 | prefix（二级分区） | 当前时间 |

### Scan 操作配置

| 参数名 | 说明 | 类型 | 默认值 |
|--------|------|------|--------|
| `obkv.scan.usePageFilter` | 是否使用 PageFilter | boolean | false |
| `obkv.scan.pageFilterSize` | PageFilter 页面大小 | int | 使用 recordcount |

### 客户端配置

| 参数名 | 说明 | 类型 | 默认值 |
|--------|------|------|--------|
| `server.connection.pool.size` | 连接池大小 | int | 20 |
| `rpc.operation.timeout` | RPC 操作超时（毫秒） | int | 10000 |
| `rpc.execute.timeout` | RPC 执行超时（毫秒） | int | 15000 |
| `obkv.debug` | 调试模式 | boolean | false |

---

## 相关文档

- [Workload 参考](workload-reference.md) — Workload 文件模板与完整示例
- [OBKV-Table 模块详解](module-obkv-table.md) — 表结构、分区策略详细说明
- [OBKV-HBase 模块详解](module-obkv-hbase.md) — 表模型、测试模式详细说明
- [命令行使用指南](guide-cli.md) — 命令行操作流程
- [Web UI 使用指南](guide-webui.md) — 图形界面操作流程
