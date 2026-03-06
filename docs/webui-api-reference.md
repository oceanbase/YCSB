# Web UI REST API 参考

所有 API 基础路径：`http://localhost:8080`

响应格式：JSON（除日志文本接口外）

> 使用说明请参考 [Web UI 使用指南](guide-webui.md)；架构说明请参考 [Web UI 架构设计](webui-architecture.md)。

---

## 模块描述文件 API

### GET /api/modules

获取所有已加载的模块列表。

**响应**（200 OK）：

```json
[
  {
    "moduleId": "obkv-hbase",
    "displayName": "OBKV HBase",
    "testTypes": [ ... ],
    "tableModes": [ ... ],
    "connectionModes": { ... },
    "knownParams": [ ... ]
  }
]
```

### GET /api/modules/{id}

获取指定模块的完整描述文件。

- `200 OK`：返回完整 `ModuleDescriptor` JSON
- `404 Not Found`：模块不存在

---

## 配置管理 API

### GET /api/configs

列出所有已保存的配置。

**响应**（200 OK）：

```json
[
  {
    "name": "hbase-read-32thread",
    "module": "obkv-hbase",
    "testType": "read",
    "savedAt": "2024-01-01T10:00:00"
  }
]
```

### GET /api/configs/{name}

加载指定配置。名称仅允许 `[a-zA-Z0-9_\-]`。

**响应**（200 OK）：

```json
{
  "name": "hbase-read-32thread",
  "module": "obkv-hbase",
  "testType": "read",
  "savedAt": "2024-01-01T10:00:00",
  "workloadContent": "workload=site.ycsb.workloads.CoreWorkload\n..."
}
```

### POST /api/configs/{name}

保存配置（同名覆盖）。

**请求体**：

```json
{
  "module": "obkv-hbase",
  "testType": "read",
  "workloadContent": "workload=site.ycsb.workloads.CoreWorkload\n..."
}
```

### DELETE /api/configs/{name}

删除指定配置。

---

## 建表 API

### POST /api/table/generate

调用 `create_table.sh` 生成建表 SQL。

**请求体**：

```json
{
  "module": "obkv-hbase",
  "params": {
    "type": "hbase",
    "table_name": "ycsb_test",
    "family": "cf",
    "mode": "first_part",
    "partition_type": "range",
    "partition_count": "128",
    "max_key": "9223372036854775807"
  }
}
```

**响应**：

```json
{
  "success": true,
  "sql": "CREATE TABLE ...",
  "message": "SQL generated successfully"
}
```

### POST /api/table/execute

通过 JDBC 执行 SQL。

**请求体**：

```json
{
  "host": "",
  "port": ,
  "username": "",
  "password": "",
  "database": "",
  "sql": "CREATE TABLE ..."
}
```

### POST /api/table/test-connection

测试 JDBC 连通性。请求体同 execute（`sql` 字段忽略）。

---

## 性能测试 API

### POST /api/tests

启动新测试。

**请求体**：

```json
{
  "module": "obkv-hbase",
  "testType": "read",
  "tableMode": "default",
  "workloadContent": "workload=...\nrecordcount=100000\n...",
  "configName": "my-read-test"
}
```

**响应**（200 OK）：`{"testId": "3f2a1b4c-..."}`

**错误**：`429` 已达并发上限；`400` 模块不存在

### GET /api/tests

分页获取历史记录（按 startTime 倒序）。

**查询参数**：`page`（默认 0）、`size`（默认 20）

**status 枚举**：`RUNNING` / `COMPLETED` / `FAILED` / `STOPPED` / `UNKNOWN` / `DISK_FULL` / `ERROR`

### GET /api/tests/{id}/stream

SSE 实时日志流。

**SSE 事件类型**：
- `log`：日志行，`id` 为文件字节偏移
- `done`：日志结束（`data: EOF`）
- `error`：读取异常
- `:heartbeat`：每 ~15 秒心跳

支持 `Last-Event-ID` 断点续传。

### GET /api/tests/{id}/results

获取结构化测试结果。

**响应**（200 OK）：

```json
{
  "throughput": 12345.6,
  "runTimeMs": 35200,
  "totalOps": 500000,
  "operations": [
    {
      "type": "READ",
      "count": 500000,
      "avgLatencyUs": 823.5,
      "p95LatencyUs": 1200.0,
      "p99LatencyUs": 2100.0,
      "minLatencyUs": 120.0,
      "maxLatencyUs": 45000.0,
      "returnOK": 499998,
      "returnError": 2
    }
  ]
}
```

- `204 No Content`：尚未完成或无法解析
- `404 Not Found`：testId 不存在

### GET /api/tests/{id}/log

获取原始日志文本。`offset`（起始行，默认 0）、`limit`（行数，默认 2000）。

### GET /api/tests/{id}/workload

获取 workload.properties 原始内容。

### GET /api/tests/{id}/meta

获取测试元信息。

### DELETE /api/tests/{id}

停止运行中的测试（SIGTERM → 10s → SIGKILL）。

### DELETE /api/tests/{id}/record

删除已结束测试的历史目录。运行中的测试不允许删除。

---

## 错误响应格式

```json
{"error": "错误描述信息"}
```

常见状态码：`400` 参数不合法、`404` 资源不存在、`429` 并发限流、`500` 服务端异常

---

## 相关文档

- [Web UI 使用指南](guide-webui.md) — 用户操作说明
- [Web UI 架构设计](webui-architecture.md) — 整体架构
- [Web UI 扩展指南](webui-extension-guide.md) — 模块描述文件机制
