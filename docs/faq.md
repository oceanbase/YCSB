# 常见问题（FAQ）

汇总 OBKV-Table、OBKV-HBase 和 Web UI 的常见问题。

---

## 通用问题

### Q: recordcount 和 operationcount 有什么区别？

`recordcount` 是数据集总记录数，`load` 阶段写入此数量，`run` 阶段用于计算 key 范围。`operationcount` 是运行阶段的操作总数。一般令 `operationcount = recordcount` 即可。

### Q: 必须先执行 load 吗？

`read`、`scan`、`batch_read` 测试需要读取已有数据，因此**必须**先执行 `load` 写入数据。`put` 和 `batch_put` 不需要。

### Q: 如何调试问题？

设置 `obkv.debug=true` 启用调试模式，会打印传入的 key/value 和返回结果集。所有操作异常都会打印堆栈信息。

---

## OBKV-Table 问题

### Q: 使用 put 接口报错怎么办？

`put` 接口需要 OceanBase 服务端设置 `binlog_row_image='MINIMAL'`：

```sql
SET GLOBAL binlog_row_image='MINIMAL';
```

如果有 CDC 下游同步需求，改用 `insertup` 接口：

```properties
obkv.insertType=insertup
obkv.updateType=insertup
obkv.batchPutType=insertup
```

### Q: key_range 表分区参数必须和建表一致吗？

是的。`obkv.partitionStartTs`、`obkv.partitionDurationMs`、`obkv.partitionCount` 必须与 `create_table.sh` 建表时使用的值完全一致，否则数据分布会不均匀或报错。

---

## OBKV-HBase 问题

### Q: 如何选择测试模式？

- **默认模式（default）**：适用于一级分区表，与 YCSB hbase-binding 逻辑一致
- **前缀查询模式（prefix）**：适用于需要前缀查询的场景，支持一级和二级分区表

### Q: 前缀查询模式为什么必须使用 ordered？

前缀查询模式基于顺序递增的 key 计算 `prefixId` 和 `subId`。如果使用 `hashed` 模式，key 分布不均匀，无法正确计算前缀。

### Q: maxKey 参数的作用是什么？

用于限制 key 范围，对生成的 key 进行取余处理：`key % (maxKey + 1)`。如果建表时指定了 `--max_key`，workload 中也需要配置相同的值。仅默认模式使用。

### Q: 如何确保数据均匀分布在各个 range 分区？

二级分区表的前缀模式会自动根据 `subId` 计算时间戳。启用 `obkv.enablePastTime=true` 可写入过去时间，确保写入压力均匀分布。

---

## Web UI 问题

### Q: 测试发起后日志区没有输出

检查 `application.properties` 中的 JAR 路径配置（`webui.hbase.jar.path` / `webui.table.jar.path`），确认 JAR 文件存在。运行 `./deploy.sh logs` 查看 webui 自身日志。

### Q: 建表执行报错 "Access denied"

确认连接用户有 `CREATE TABLE` 权限。

### Q: prefix 模式测试失败

prefix 模式要求 `insertorder=ordered`，Step 2 选择 prefix 模式后会自动设置。如果手动修改了该值，服务端会校验并拒绝。

### Q: 历史记录显示 UNKNOWN 状态

服务重启时该测试正在运行，重启后状态标记为 UNKNOWN。检查对应 `output.log` 确认实际结果。

### Q: SSE 日志流断开重连

日志会从断点自动续传。若在 nginx 后面部署，需关闭缓冲：

```nginx
proxy_buffering off;
proxy_cache off;
```

### Q: 多个测试并发时系统变慢

每个 YCSB 进程占用约 512MB 堆内存。降低 `webui.ycsb.jvm.max-heap` 或减少 `webui.max.concurrent.tests`。

### Q: 点击「发起新测试」按钮变灰

已达并发上限（默认 5）。等待运行中测试完成，或增大 `webui.max.concurrent.tests`。

### Q: 刷新页面后活动测试栏清空

预期行为（前端状态不持久化）。刷新后可在「历史记录」Tab 中找到 RUNNING 状态的记录，通过「查看日志」跟踪运行中的测试。

---

## 相关文档

- [快速入门](getting-started.md) — 5 分钟跑通第一次测试
- [Web UI 使用指南](guide-webui.md) — 图形界面操作说明
- [命令行使用指南](guide-cli.md) — 命令行操作说明
- [参数配置大全](params-reference.md) — 所有可配置参数一览
