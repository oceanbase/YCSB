# Web UI 架构设计

本文面向开发者，介绍 YCSB Web UI 的整体架构、SSE 日志流设计、数据持久化、资源隔离和进程管理。

> 使用层面的操作说明请参考 [Web UI 使用指南](guide-webui.md)。

---

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│  modules/                                                           │
│  ├── obkv-hbase.json    ← 模块描述文件（唯一的模块专属知识来源）      │
│  └── obkv-table.json                                                │
└────────────────┬────────────────────────────────────────────────────┘
                 │ 启动时加载
                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Spring Boot 2.7.x (embedded Tomcat, port 8080)                    │
│                                                                     │
│  ModuleService   ← 加载描述文件，提供白名单和元信息查询              │
│  ConfigService   ← webui-configs/ CRUD（白名单来自 ModuleService）   │
│  TableService    ← create_table.sh + JDBC                          │
│  TestService     ← ProcessBuilder + tail-follower + SseEmitter     │
└────────────┬────────────────────────────────────────────────────────┘
             │ REST API / SSE
             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  浏览器 (纯 HTML5/CSS3/ES6，无 CDN 依赖)                            │
│                                                                     │
│  config.js  ← 描述文件动态渲染表单，参数白名单解析                   │
│  test.js    ← 测试会话管理，双向绑定，SSE 日志流                    │
│  table.js   ← 建表向导                                              │
│  history.js ← 历史记录                                              │
│  chart.js   ← Canvas 延迟图表（无外部依赖）                         │
│  sse.js     ← SSE 连接管理（Last-Event-ID 断点续传）                │
└─────────────────────────────────────────────────────────────────────┘
```

**技术选型理由**：
- Spring Boot 2.7.x：最后支持 Java 8 的大版本
- 纯原生 JS：无 CDN 依赖，离线/内网可用
- Canvas API：替代 Chart.js，减少外部依赖
- 唯一运行时外部依赖：`mysql-connector-j`（建表 JDBC 执行）

---

## 2. 核心模块职责

### ModuleService

- 启动时扫描 `classpath:modules/*.json`，反序列化为 `ModuleDescriptor`
- 提供 `getKnownParams(moduleId)` 供 ConfigService 做参数白名单过滤
- 提供 `getModule(moduleId)` 供 TestService 构建命令行参数

### ConfigService

- 管理 `webui-configs/` 目录下的 `.properties` 文件
- 文件头部写入 `__webui.module` / `__webui.testType` / `__webui.savedAt` 元信息
- 加载时过滤 `__webui.*` 行，返回纯 YCSB workload 内容
- 名称通过 `[a-zA-Z0-9_\-]{1,100}` 正则校验

### TableService

- 通过 `ProcessBuilder` 调用各模块的 `create_table.sh`
- JDBC 执行 SQL 时支持分号分隔的多语句批量执行

### TestService

- 通过 `ProcessBuilder` 启动 YCSB JAR，`redirectErrorStream(true)` 合并 stderr
- 启动时注入 `-Xmx{webui.ycsb.jvm.max-heap}` 限制子进程内存
- 根据 `tableMode` 从 `ModuleDescriptor` 获取 `dbClass`，动态拼接 `-db` 参数

---

## 3. SSE 日志流架构（零内存堆积）

核心目标：**无论测试时长多长、并发数多少，webui JVM 堆内存始终有界**。

```
YCSB 进程 stdout
       │
       ▼  （无间隙消费，防 64KB OS 管道缓冲区撑满）
  reader 线程
       │  每行立即写入
       ▼
  output.log ─────────────────────────────────────────────────────┐
       │                                                           │
       │  tail-follower 线程（RandomAccessFile）                   │
       │  Last-Event-ID = 文件字节偏移量                           │
       ▼                                                           │
  SseEmitter (per browser tab)                                     │
       │                                                           │
       ▼                                                           │
  浏览器 DOM（只保留最近 1000 行，向上滚动时 GET /log?offset 分页）
```

**写路径**：`PrintWriter(autoFlush=true)` 确保每行立即落盘

**读路径**：`RandomAccessFile` 轮询文件长度变化；断线重连时 `Last-Event-ID` 自动续传

**内存边界**：历史日志完全在磁盘；内存中只有当前活跃 `TestRun` 引用 + 近期历史索引

**心跳**：每 ~15 秒发送 `:heartbeat` SSE comment，防代理超时

---

## 4. 数据持久化设计

### webui-configs/（配置文件）

```properties
__webui.module=obkv-hbase
__webui.testType=read
__webui.savedAt=2024-01-01T10:00:00
workload=site.ycsb.workloads.CoreWorkload
recordcount=100000
...
```

元信息以 `__webui.` 前缀写在文件头，不干扰 YCSB 解析。

### webui-runs/（运行记录）

```
webui-runs/{testId}/
├── meta.json           ← 元信息，含 ycsbPid
├── workload.properties ← 配置快照
├── output.log          ← 完整日志，实时追加
├── result_snapshot.json← 中间快照（每 ~60 行更新）
└── result.json         ← 最终结果
```

**写入时序**：创建目录 → 写 meta + workload → 实时追加 log → 解析 snapshot → 进程退出后写 result + 更新 meta

---

## 5. 资源隔离策略

| 组件 | 内存限制 | 配置方式 |
|------|---------|---------|
| webui JVM | 256MB | `deploy.sh` 启动参数 |
| 每个 YCSB 子进程 | 512MB | `webui.ycsb.jvm.max-heap` |
| SSE 线程池 | 上限 20 | `webui.sse.thread-pool-size` |
| 并发测试数 | 默认 5 | `webui.max.concurrent.tests` |
| 历史记录 | 超 100 条自动删旧 | `webui.runs.max-count` |

SSE 推送在独立线程池执行，与 Tomcat 请求线程完全隔离。

---

## 6. 进程生命周期管理

### 正常停止

前端「停止」→ `DELETE /api/tests/{id}` → `process.destroy()`（SIGTERM）→ 10 秒后 `destroyForcibly()`（SIGKILL）

### 服务优雅关闭

`@PreDestroy` 遍历 RUNNING 状态的 TestRun，依次 SIGTERM → 等待 → SIGKILL → 更新 meta 为 STOPPED

### 崩溃恢复

重启时扫描 `webui-runs/`，RUNNING 状态标记为 UNKNOWN，`meta.json` 中 `ycsbPid` 可手动 kill 孤儿进程

### 磁盘满

写 log 时捕获 IOException → SSE 推送告警 → 更新 meta 为 DISK_FULL

---

## 7. 安全注意事项

- **路径遍历防护**：配置名 `[a-zA-Z0-9_\-]{1,100}` 严格校验；testId 使用 UUID v4
- **密码明文**：配置文件明文存储密码，建议 `chmod 600 webui-configs/*.properties`
- **网络**：默认监听 `0.0.0.0:8080`，建议防火墙或 nginx 限制访问
- **无认证**：当前版本无内置认证，建议通过 nginx basic auth 或 IP 白名单保护

---

## 相关文档

- [Web UI 使用指南](guide-webui.md) — 用户操作说明
- [Web UI 扩展指南](webui-extension-guide.md) — 如何新增模块或修改参数
- [Web UI API 参考](webui-api-reference.md) — REST API 完整文档
