# OBKV-HBase Benchmark Console Server

OBKV-HBase 压测控制台后端服务，提供 REST API 和 WebSocket 接口，用于管理数据库表和执行 YCSB 压测任务。

## 项目结构

```
server/
├── src/main/java/com/oceanbase/obkv/console/
│   ├── ObkvHBaseConsoleApplication.java    # 主应用入口
│   ├── config/                              # 配置类
│   │   ├── AsyncConfig.java                # 异步执行配置
│   │   ├── CorsConfig.java                 # CORS 跨域配置
│   │   ├── WebSocketConfig.java            # WebSocket 配置
│   │   └── YcsbConfig.java                 # YCSB 配置
│   ├── controller/                          # REST 控制器
│   │   ├── DdlController.java              # DDL 操作 API
│   │   ├── HealthController.java           # 健康检查 API
│   │   ├── SqlController.java              # SQL 连接 API
│   │   └── TaskController.java             # 任务管理 API
│   ├── dto/                                 # 数据传输对象
│   │   ├── ApiResponse.java                # 通用 API 响应
│   │   ├── CreateTableRequest.java         # 建表请求
│   │   ├── DdlResponse.java                # DDL 响应
│   │   ├── TableOperationRequest.java      # 表操作请求
│   │   ├── TaskRequest.java                # 任务请求
│   │   └── TaskResponse.java               # 任务响应
│   ├── exception/                           # 异常处理
│   │   └── GlobalExceptionHandler.java     # 全局异常处理器
│   ├── model/                               # 数据模型
│   │   ├── ClientConfig.java               # 客户端配置
│   │   ├── ObkvConnection.java             # OBKV 连接配置
│   │   ├── SqlConnection.java              # SQL 连接配置
│   │   ├── TableConfig.java                # 表配置
│   │   ├── Task.java                       # 任务实体
│   │   ├── TaskStatus.java                 # 任务状态枚举
│   │   └── WorkloadParams.java             # Workload 参数
│   ├── service/                             # 业务服务
│   │   ├── DdlService.java                 # DDL 服务
│   │   ├── SqlService.java                 # SQL 服务
│   │   ├── TaskService.java                # 任务服务
│   │   ├── WebSocketService.java           # WebSocket 服务
│   │   └── WorkloadService.java            # Workload 服务
│   └── websocket/                           # WebSocket 处理
│       └── TaskWebSocketHandler.java       # 任务 WebSocket 处理器
├── src/main/resources/
│   └── application.yml                      # 应用配置
├── build.sh                                 # 编译脚本
├── run.sh                                   # 运行脚本
└── pom.xml                                  # Maven 配置
```

## 快速开始

### 1. 前置条件

- Java 8+
- Maven 3.6+
- 已编译的 YCSB JAR 包（运行 `../build.sh`）

### 2. 编译

```bash
cd server
./build.sh
```

### 3. 运行

```bash
./run.sh
```

服务默认运行在 `http://localhost:8080`

## API 接口

### 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| GET | `/api/info` | API 信息 |

### SQL 连接

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/sql/test-connection` | 测试数据库连接 |

**请求示例：**

```json
POST /api/sql/test-connection
{
  "ip": "10.0.0.1",
  "port": 2883,
  "user": "root@tenant",
  "password": "xxx",
  "database": "test"
}
```

### DDL 操作

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ddl/generate-sql` | 生成建表 SQL（预览） |
| POST | `/api/ddl/create-table` | 建表 |
| POST | `/api/ddl/truncate-table` | 清空表 |
| POST | `/api/ddl/drop-table` | 删表 |

**建表请求示例：**

```json
POST /api/ddl/create-table
{
  "sqlConnection": {
    "ip": "10.0.0.1",
    "port": 2883,
    "user": "root@tenant",
    "password": "xxx",
    "database": "test"
  },
  "tableConfig": {
    "tableName": "ycsb_test",
    "columnFamily": "cf",
    "maxKey": 1000000,
    "partitionCount": 4,
    "keyLength": 12
  }
}
```

### 任务管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/task/load` | 执行 Load 操作 |
| POST | `/api/task/run` | 执行 Run 操作 |
| GET | `/api/task/status/{taskId}` | 获取任务状态 |
| POST | `/api/task/stop/{taskId}` | 停止任务 |
| GET | `/api/task/list` | 获取任务列表 |
| GET | `/api/task/workloads` | 获取可用 Workload 列表 |
| POST | `/api/task/clear` | 清理已完成任务 |

**执行任务请求示例：**

```json
POST /api/task/run
{
  "workload": "workloada",
  "tableName": "ycsb_test",
  "columnFamily": "cf",
  "obkvConnection": {
    "mode": "odp",
    "ip": "10.0.0.1",
    "port": 2885,
    "fullUserName": "user@tenant#cluster",
    "password": "xxx",
    "database": "test"
  },
  "clientConfig": {
    "connectionPoolSize": 20,
    "rpcOperationTimeout": 10000,
    "rpcExecuteTimeout": 15000,
    "debug": false
  },
  "workloadParams": {
    "recordcount": 100000,
    "operationcount": 10000,
    "threadcount": 10,
    "target": 0,
    "fieldlength": 100
  }
}
```

## WebSocket

WebSocket 端点：`ws://host:port/ws/task`

### 订阅任务

发送消息：

```json
{
  "action": "subscribe",
  "taskId": "xxx"
}
```

### 接收消息类型

**日志消息：**

```json
{
  "type": "LOG",
  "taskId": "xxx",
  "message": "[READ], Operations, 1000",
  "timestamp": 1737043200000
}
```

**状态更新：**

```json
{
  "type": "STATUS",
  "taskId": "xxx",
  "status": "COMPLETED",
  "message": "任务执行完成",
  "timestamp": 1737043200000
}
```

**结果消息：**

```json
{
  "type": "RESULT",
  "taskId": "xxx",
  "result": "[OVERALL], Throughput(ops/sec), 1000.0\n...",
  "timestamp": 1737043200000
}
```

## 支持的 Workload

| Workload | 说明 | 读写比例 |
|----------|------|---------|
| workloada | Update Heavy | 50% Read / 50% Update |
| workloadb | Read Mostly | 95% Read / 5% Update |
| workloadc | Read Only | 100% Read |
| workloadd | Read Latest | 95% Read / 5% Insert |
| workloade | Short Ranges | 95% Scan / 5% Insert |
| workloadf | Read-Modify-Write | 50% Read / 50% RMW |

## 配置说明

### application.yml

```yaml
server:
  port: 8080

ycsb:
  # YCSB JAR 包路径
  jar-path: ../build/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar
  # Workload 文件目录
  workloads-path: ../workloads/workloads_a_f
  # 临时文件目录
  temp-path: ./temp
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| PORT | 服务端口 | 8080 |
| YCSB_JAR_PATH | YCSB JAR 路径 | ../build/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar |
| YCSB_WORKLOADS_PATH | Workload 目录 | ../workloads/workloads_a_f |
| YCSB_TEMP_PATH | 临时文件目录 | ./temp |

