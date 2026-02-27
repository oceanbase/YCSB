# YCSB Web UI 架构设计

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
- Spring Boot 2.7.x：最后支持 Java 8 的大版本，无需升级 JDK
- 纯原生 JS：无 CDN 依赖，离线/内网环境可正常使用
- Canvas API：替代 Chart.js，减少外部依赖
- 唯一运行时外部依赖：`mysql-connector-j`（建表 JDBC 执行）

---

## 2. 核心模块职责

### ModuleService

- 启动时通过 `PathMatchingResourcePatternResolver` 扫描 `classpath:modules/*.json`
- 将 JSON 反序列化为 `ModuleDescriptor` POJO，存储在内存 Map 中
- 提供 `getKnownParams(moduleId)` 供 ConfigService 做参数白名单过滤
- 提供 `getModule(moduleId)` 供 TestService 构建命令行参数

### ConfigService

- 管理 `webui-configs/` 目录下的 `.properties` 文件
- 配置文件头部写入 `__webui.module` / `__webui.testType` / `__webui.savedAt` 元信息前缀
- 加载时过滤掉 `__webui.*` 元信息行，返回纯 YCSB workload 内容
- 配置名称通过正则 `[a-zA-Z0-9_\-]{1,100}` 校验，防止路径遍历

### TableService

- 通过 `ProcessBuilder` 调用各模块的 `create_table.sh`，设置正确的工作目录（`ProcessBuilder.directory()`）
- obkv-hbase 脚本输出 SQL 到 stdout；obkv-table 脚本写文件，通过扫描目录中最新的 `.sql` 文件获取
- JDBC 执行 SQL 时支持分号分隔的多语句批量执行

### TestService

- 通过 `ProcessBuilder` 启动 YCSB JAR，`redirectErrorStream(true)` 合并 stderr 防止管道死锁
- 启动时强制注入 `-Xmx{webui.ycsb.jvm.max-heap}` 限制子进程内存
- 对 obkv-table，根据 `tableMode` 从 `ModuleDescriptor.TableMode.dbClass` 获取对应类名，动态拼接 `-db` 参数

---

## 3. SSE 日志流架构（零内存堆积）

日志流设计的核心目标：**无论测试时长多长、并发数多少，webui JVM 堆内存始终有界**。

```
YCSB 进程 stdout
       │
       ▼  （无间隙消费，防 64KB OS 管道缓冲区撑满导致 YCSB 阻塞）
  reader 线程
       │  每行立即写入
       ▼
  output.log  ─────────────────────────────────────────────────────┐
       │                                                           │
       │  tail-follower 线程（RandomAccessFile）                   │
       │  持续读取文件新增内容                                      │
       │  Last-Event-ID = 文件字节偏移量                           │
       ▼                                                           │
  SseEmitter (per browser tab)                                     │
       │                                                           │
       ▼                                                           │
  浏览器 DOM（只保留最近 1000 行，向上滚动时 GET /log?offset 分页加载）
```

关键实现细节：

**写路径**：
- `PrintWriter(new FileWriter(path, true), true)` — `true` 开启 auto-flush，确保每行立即落盘
- reader 线程从 `Process.getInputStream()` 无间隙读取，写完立即继续读下一行

**读路径**：
- tail-follower 使用 `RandomAccessFile` 持续轮询文件长度变化
- 新建 SSE 连接时，将 `Last-Event-ID`（字节偏移）传给服务端，从该位置开始重放
- 浏览器断线重连时，`EventSource` 自动携带 `Last-Event-ID` 头

**内存边界**：
- 历史日志：完全在磁盘，不在内存
- 内存中只有：当前活跃 `TestRun` 对象引用 + 近期历史索引（`List<RunMeta>`）

**SSE 心跳**：每 ~15 秒发送 SSE comment（`:heartbeat\n\n`），防止代理超时关闭连接

---

## 4. 数据持久化设计

### webui-configs/ （配置文件）

```properties
__webui.module=obkv-hbase        ← 元信息（加载时解析后从 Properties 移除）
__webui.testType=read
__webui.savedAt=2024-01-01T10:00:00
workload=site.ycsb.workloads.CoreWorkload
recordcount=100000
hbase.oceanbase.fullUserName=root@test
...
```

- 标准 YCSB workload 格式，可直接用于命令行
- 元信息以 `__webui.` 前缀写在文件头，不会干扰 YCSB 解析（YCSB 忽略未知参数）

### webui-runs/ （运行记录）

```
webui-runs/{testId}/
├── meta.json           ← 测试元信息，含 ycsbPid（用于孤儿进程管理）
├── workload.properties ← 本次完整配置快照（无 __webui.* 元信息）
├── output.log          ← 完整 stdout/stderr，实时追加，不等进程结束
├── result_snapshot.json← 中间快照，每 ~60 个状态行更新一次（防止长测试结果全丢）
└── result.json         ← 最终结构化结果，进程退出后解析写入
```

**写入时序**：
1. 进程启动 → 创建目录 → 写 `meta.json`（status=RUNNING）+ `workload.properties`
2. reader 线程实时追加 `output.log`
3. tail-follower 解析 `[STATUS]` 行，每 ~60 行写一次 `result_snapshot.json`
4. 进程退出 → 解析 `output.log` 写 `result.json` → 更新 `meta.json`（status=COMPLETED/FAILED）

**历史索引**：服务启动时扫描磁盘，构建内存中的有序列表（按 startTime 倒序）；之后增量更新，避免每次请求都扫盘。

---

## 5. 资源隔离策略

webui 本身是「管理平面」，不能挤占 YCSB 客户端（「数据平面」）的资源。

| 组件 | 内存限制 | 配置方式 |
|------|---------|---------|
| webui JVM | 256MB 堆 | `deploy.sh` 启动参数 `-Xmx256m -Xms128m` |
| 每个 YCSB 子进程 | 512MB 堆 | `application.properties: webui.ycsb.jvm.max-heap=512m` |
| SSE 线程池 | 上限 20 线程（可配） | `webui.sse.thread-pool-size=20` |
| 并发测试数 | 默认上限 5 | `webui.max.concurrent.tests=5` |
| 历史记录 | 超过 100 条自动删最旧 | `webui.runs.max-count=100` |

**SSE 线程隔离**：SSE 的 `SseEmitter` 推送在独立的 `CachedThreadPool` 中执行，与 Tomcat HTTP 请求线程池完全隔离，防止大量 SSE 连接耗尽 Tomcat 线程。

---

## 6. 进程生命周期管理

### 正常停止

1. 前端点击「停止」→ `DELETE /api/tests/{id}`
2. `TestService.stopTest()` 调用 `process.destroy()`（SIGTERM）
3. 新线程等待 10 秒，仍存活则 `process.destroyForcibly()`（SIGKILL）

### 服务优雅关闭

`@PreDestroy` 注解的 `shutdown()` 方法：
1. 遍历所有 `RUNNING` 状态的 `TestRun`
2. 依次发送 SIGTERM，等待 10s，超时则 SIGKILL
3. 更新对应 `meta.json` 状态为 STOPPED

### 服务崩溃恢复

如果 webui 进程被 OOM Kill 或异常崩溃（YCSB 子进程可能成为孤儿）：
1. 重启时 `TestService.init()` 扫描所有 `webui-runs/` 目录
2. 发现 `status=RUNNING` 的记录 → 标记为 `UNKNOWN`
3. `meta.json` 中保存了 `ycsbPid`，可手动 `kill {ycsbPid}` 处理孤儿进程

### 磁盘满处理

写 `output.log` 时捕获 `IOException`：
1. 通过 SSE 推送告警事件到前端
2. 更新 `meta.json` 状态为 `DISK_FULL`
3. 不强制停止 YCSB 进程（让用户决定）

---

## 7. 安全注意事项

**路径遍历防护**：
- 配置名称通过正则 `[a-zA-Z0-9_\-]{1,100}` 严格校验
- `testId` 使用 UUID v4，无法伪造路径

**密码明文存储**：
- 配置文件以明文保存连接密码
- 推荐 `chmod 600 webui-configs/*.properties`

**网络访问控制**：
- webui 默认监听所有网卡（`0.0.0.0:8080`）
- 内网部署时建议通过防火墙限制访问范围，或使用 nginx 添加认证

**无认证机制**：
- 当前版本无内置认证，所有 API 均无鉴权
- 生产环境建议通过 nginx basic auth 或 IP 白名单保护
