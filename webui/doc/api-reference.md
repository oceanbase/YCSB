# YCSB Web UI REST API 参考

所有 API 基础路径：`http://localhost:8080`

响应格式：JSON（除日志文本接口外）

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
  },
  {
    "moduleId": "obkv-table",
    ...
  }
]
```

---

### GET /api/modules/{id}

获取指定模块的完整描述文件。

**路径参数**：
- `id`：模块 ID（如 `obkv-hbase`）

**响应**：
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

---

### GET /api/configs/{name}

加载指定配置，返回内容和元信息。

**路径参数**：
- `name`：配置名称（仅允许 `[a-zA-Z0-9_\-]`）

**响应**（200 OK）：

```json
{
  "name": "hbase-read-32thread",
  "module": "obkv-hbase",
  "testType": "read",
  "savedAt": "2024-01-01T10:00:00",
  "workloadContent": "workload=site.ycsb.workloads.CoreWorkload\nrecordcount=100000\n..."
}
```

**错误**：
- `400 Bad Request`：名称格式不合法
- `404 Not Found`：配置不存在

---

### POST /api/configs/{name}

保存配置（若同名配置存在则覆盖）。

**路径参数**：
- `name`：配置名称

**请求体**：

```json
{
  "module": "obkv-hbase",
  "testType": "read",
  "workloadContent": "workload=site.ycsb.workloads.CoreWorkload\n..."
}
```

**响应**（200 OK）：

```json
{"status": "saved", "name": "hbase-read-32thread"}
```

**错误**：
- `400 Bad Request`：名称格式不合法，body 包含 `{"error": "..."}`

---

### DELETE /api/configs/{name}

删除指定配置。

**响应**（200 OK）：

```json
{"status": "deleted", "name": "hbase-read-32thread"}
```

---

## 建表 API

### POST /api/table/generate

调用对应模块的 `create_table.sh` 生成建表 SQL。

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
    "max_key": "9223372036854775807",
    "key_length": "12"
  }
}
```

**响应**（200 OK）：

```json
{
  "success": true,
  "sql": "CREATE TABLE `ycsb_test$cf` (\n  ...\n);",
  "message": "SQL generated successfully"
}
```

**错误**（400 Bad Request）：

```json
{
  "success": false,
  "sql": null,
  "message": "create_table.sh failed (exit 1): ..."
}
```

---

### POST /api/table/execute

通过 JDBC 在 OceanBase 上执行 SQL。

**请求体**：

```json
{
  "host": "127.0.0.1",
  "port": 2881,
  "username": "root@test",
  "password": "xxx",
  "database": "test",
  "sql": "CREATE TABLE `ycsb_test$cf` ( ... );"
}
```

**响应**（200 OK / 400 Bad Request）：

```json
{
  "success": true,
  "sql": "CREATE TABLE ...",
  "message": "Table created successfully"
}
```

---

### POST /api/table/test-connection

测试 JDBC 连通性（不执行任何 SQL）。

**请求体**：同 `/api/table/execute`（`sql` 字段忽略）

**响应**：

```json
{"success": true, "sql": null, "message": "Connection successful"}
```

---

## 性能测试 API

### POST /api/tests

启动一个新的测试任务。

**请求体**：

```json
{
  "module": "obkv-hbase",
  "testType": "read",
  "tableMode": "default",
  "workloadContent": "workload=site.ycsb.workloads.CoreWorkload\nrecordcount=100000\n...",
  "configName": "my-read-test"
}
```

**响应**（200 OK）：

```json
{"testId": "3f2a1b4c-..."}
```

**错误**：
- `429 Too Many Requests`：已达并发上限，body 含 `{"error": "Max concurrent tests reached: 5"}`
- `400 Bad Request`：模块不存在

---

### GET /api/tests

分页获取历史记录列表（按 startTime 倒序）。

**查询参数**：
- `page`：页码（从 0 开始，默认 0）
- `size`：每页条数（默认 20）

**响应**（200 OK）：

```json
[
  {
    "testId": "3f2a1b4c-...",
    "module": "obkv-hbase",
    "testType": "read",
    "tableMode": "default",
    "status": "COMPLETED",
    "startTime": "2024-01-01T10:00:00",
    "endTime": "2024-01-01T10:00:35",
    "durationMs": 35200,
    "ycsbPid": 12345,
    "configName": "my-read-test"
  }
]
```

**status 枚举值**：`RUNNING` / `COMPLETED` / `FAILED` / `STOPPED` / `UNKNOWN` / `DISK_FULL` / `ERROR`

---

### GET /api/tests/{id}/stream

Server-Sent Events (SSE) 实时日志流。

- 测试运行中：实时推送日志行
- 测试已完成：回放完整 `output.log`，结束后发送 `event: done\ndata: EOF`

**请求头**（可选）：
- `Last-Event-ID`：上次断开时的字节偏移量，用于断点续传

**SSE 事件类型**：
- `log`：一行日志内容，`id` 为文件字节偏移
- `done`：日志流结束（`data: EOF`）
- `error`：读取异常告警
- `:heartbeat`：注释行心跳，每 ~15 秒发送一次（防代理超时）

**示例（EventSource）**：

```javascript
const es = new EventSource('/api/tests/3f2a1b4c-.../stream');
es.addEventListener('log', e => console.log(e.data));
es.addEventListener('done', () => es.close());
```

---

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

**其他响应**：
- `204 No Content`：测试尚未完成，或无法解析结果
- `404 Not Found`：testId 不存在

---

### GET /api/tests/{id}/log

获取原始日志文本（支持分页）。

**查询参数**：
- `offset`：起始行号（从 0 开始，默认 0）；0 且 `limit` 无效时返回末尾 2000 行
- `limit`：返回行数（默认 2000）

**响应**（200 OK，`text/plain`）：原始日志文本

---

### GET /api/tests/{id}/workload

获取本次测试的 workload.properties 原始内容。

**响应**（200 OK，`text/plain`）：完整 workload 文件内容

---

### GET /api/tests/{id}/meta

获取测试元信息（同历史列表中的条目格式）。

**响应**（200 OK）：`RunMeta` JSON 对象

---

### DELETE /api/tests/{id}

停止正在运行的测试（发送 SIGTERM，10 秒后 SIGKILL）。

**响应**（200 OK）：

```json
{"status": "stopping", "testId": "3f2a1b4c-..."}
```

**错误**：
- `404 Not Found`：testId 不存在或测试未在运行

---

### DELETE /api/tests/{id}/record

删除已结束测试的历史目录（包含 `output.log`、`result.json` 等所有文件）。

**响应**（200 OK）：

```json
{"status": "deleted", "testId": "3f2a1b4c-..."}
```

**错误**：
- `400 Bad Request`：测试正在运行中，不允许删除
- `404 Not Found`：testId 不存在

---

## 错误响应格式

所有错误响应统一格式：

```json
{"error": "错误描述信息"}
```

常见 HTTP 状态码：
- `400 Bad Request`：请求参数不合法
- `404 Not Found`：资源不存在
- `429 Too Many Requests`：并发限流
- `500 Internal Server Error`：服务端异常
