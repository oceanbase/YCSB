# YCSB Web UI 使用手册

## 1. 快速上手（5 分钟）

### 前置条件

- Java 8+（`java -version` 验证）
- Maven 3.x（`mvn -v` 验证）
- 已部署的 OceanBase 集群

### 一键启动

```bash
# 首次启动（自动构建所有 JAR）
./deploy.sh start --rebuild

# 后续启动（JAR 已存在，直接启动）
./deploy.sh start
```

打开浏览器访问 `http://localhost:8080`

### 最简流程

1. 顶部下拉选择模块（OBKV HBase 或 OBKV Table）
2. 点击「建表」Tab → 填写分区参数 → 生成 SQL → 填写数据库连接 → 执行建表
3. 点击「性能测试」Tab → Step 1 填写连接配置 → Step 2 选择表模式 → Step 3 选择测试类型 → 点击「▶ 发起新测试」
4. 右侧实时日志区查看输出，测试完成后切换到「测试结果」Tab 查看吞吐量和延迟

---

## 2. 界面概览

```
┌──────────────────────────────────────────────────────────────────────┐
│  YCSB Platform   模块: [OBKV HBase ▼]  [性能测试] [建表] [历史记录]  │
├──────────────────────────────────────────────────────────────────────┤
│  配置管理栏：[新建] 已保存:[my-read-test▼] [加载][另存为][删除]       │
├─────────────────────────┬────────────────────────────────────────────┤
│  左侧配置面板            │  活动测试栏：[●read#1 ×][●put#2 ×][+]      │
│  Step1 连接配置         ├────────────────────────────────────────────┤
│  Step2 表模式           │  [ 配置预览 ] [ 实时日志 ] [ 测试结果 ]    │
│  Step3 测试参数         │                                            │
│  Step4 自定义参数       │                                            │
│  [ 配置预览 textarea ]  │                                            │
│  [ ▶ 发起新测试 ]       │                                            │
└─────────────────────────┴────────────────────────────────────────────┘
```

**顶部导航**：切换性能测试、建表向导、历史记录三个功能模块

**模块下拉**：选择测试对象（OBKV HBase / OBKV Table），切换后表单自动重新渲染

**配置管理栏**：保存/加载/删除测试配置，配置以 `.properties` 文件形式持久化

**左侧配置面板**：分 4 个步骤填写测试参数，底部有配置预览区（双向绑定）

**活动测试栏**：每个测试是一个独立 Tab，支持同时运行多个测试；状态点颜色：绿色=运行中，蓝色=完成，红色=失败，灰色=停止

---

## 3. 建表向导

### OBKV HBase

| 参数 | 说明 |
|------|------|
| 表模型 | HBase(KQTV) 用于标准键值测试；TimeSeries(KTSV) 用于时间序列测试 |
| 表名 | 默认 `ycsb_test`，实际建表名为 `{表名}${列族}` |
| 列族 | HBase 模型默认 `cf`，TimeSeries 模型默认 `ts_cf` |
| 分区层级 | 单分区：按 Key 或 Range 分区；双分区：Range 为主分区，Key 为子分区（时序场景） |

**单分区参数**：
- 分区类型：`range`（按 Key 范围）或 `key`（按 Key hash）
- 分区数量：推荐与 OceanBase 节点数对齐
- 最大 Key：range 分区时指定，用于均匀分布

**双分区参数**（适用于时序场景）：
- 起始时间戳（ms）、分区时长（ms）、Range 分区数共同决定时间范围切分
- Key 子分区数：每个 Range 分区下的 Key 子分区数

### OBKV Table

| 参数 | 说明 |
|------|------|
| 字段数 | 非主键列数，对应 `field0`, `field1`, ... |
| 分区模式 | `range`：单分区，按主键 Range 切分；`key_range`：双分区，时序场景 |

### 生成与执行

1. 点击「生成 SQL」，查看建表语句
2. 填写数据库连接信息（主机/端口/用户名/密码/数据库）
3. 点击「测试连接」验证连通性
4. 点击「执行建表」在 OceanBase 上创建表

---

## 4. 性能测试向导

### Step 1：连接配置

**ODP 模式**（推荐生产环境）：
- ODP 地址和端口（默认 2883）

**直连模式**（适合开发调试）：
- OBKV HBase：paramURL / sysUserName / sysPassword
- OBKV Table：configUrl / sysUserName / sysPassword

**公共字段**：
- 用户名（格式：`user@tenant`）、密码、数据库名、表名

### Step 2：表模式

**OBKV HBase**：
- 默认模式：标准测试，不需要额外参数
- Prefix 模式：多前缀压测，需填写 `prefixCount`（注意：强制 insertorder=ordered）
- 分区类型：单分区 / 双分区（Range-Key），双分区需填写起始时间戳、分区时长、Range 分区数

**OBKV Table**：
- range 模式：标准单分区表测试
- range_key 模式：双分区时序表测试，需填写 idCount、时间分区参数

### Step 3：测试参数

**执行顺序建议**：必须先执行 `load` 写入数据，然后才能执行 `read`/`scan`/`batch_read`

| 测试类型 | 说明 |
|---------|------|
| load | 批量写入初始数据（追加 -load 标志） |
| put | 持续写入测试 |
| read | 随机读取测试 |
| scan | 范围扫描测试 |
| batch_put | 批量写入测试（每次操作写多行） |
| batch_read | 批量读取测试（每次操作读多行） |

**通用参数**：

| 参数 | 说明 | 默认值 |
|------|------|--------|
| recordcount | 表中总记录数（load 写入此数量） | 100000 |
| operationcount | 执行操作次数 | 100000 |
| threadcount | 客户端并发线程数 | 10 |
| fieldcount | 每行字段数 | 10 |
| fieldlength | 每个字段的字节长度 | 100 |
| requestdistribution | 请求分布：uniform/zipfian/hotspot | uniform |

### Step 4：自定义参数

任意追加 YCSB 或 OBKV 参数。自定义参数会覆盖上方表单中同名参数。

示例：
- `obkv.debug=true` — 开启 OBKV 调试日志
- `rpc.operation.timeout=10000` — 设置 RPC 操作超时
- `insertorder=ordered` — 按顺序写入（prefix 模式自动设置）

### 配置预览（双向绑定）

左侧表单中任何字段变化都会实时更新下方的 `workload.properties` 预览区；同样，直接编辑预览区也会反向更新表单字段。

发起测试时以**预览区的当前内容**为准，支持手动微调后再执行。

### 多测试并发

- 每次点击「▶ 发起新测试」创建一个新的测试会话（独立 Tab）
- 最多同时运行 `webui.max.concurrent.tests`（默认 5）个测试
- 切换 Tab 时 SSE 日志流自动切换，后台测试的日志持续写入磁盘
- 关闭 Tab 会停止该测试（运行中时会弹出确认框）

### 实时日志

- 黄色高亮：`[OVERALL]`/`[READ]` 等结果汇总行
- 红色高亮：`ERROR` 行
- 蓝色高亮：`sec:` 进度行
- 「自动滚动」开关：关闭后可自由滚动查看历史日志，重新勾选后跳到末尾

---

## 5. 配置管理

### 保存配置

点击顶部「另存为」，输入配置名称（仅允许字母/数字/下划线/连字符，1-100位）。配置保存在 `webui-configs/` 目录，文件名为 `{name}.properties`。

**安全提示**：配置文件以明文保存连接密码，生产环境请注意文件访问权限（`chmod 600`）。

### 加载配置

从顶部下拉选择配置名，点击「加载」。系统会：
1. 解析文件中的已知参数并填入表单
2. 未识别的参数自动填入「自定义参数」区

### 命令行兼容

保存的配置文件是标准 YCSB workload.properties，可直接在命令行使用：
```bash
java -jar obkv-hbase/build/obkv-hbase-*.jar -t -P webui-configs/my-read-test.properties -s
```

---

## 6. 历史记录

### 查看历史

切换到「历史记录」Tab，默认加载最近 200 条记录，支持按模块和状态筛选。

点击某条记录可查看：
- 测试元信息（时间、模块、类型、状态、时长）
- 完整日志（`output.log`）
- 结构化结果（吞吐量、延迟图表）

### 用此配置重新测试

在历史记录详情中点击「用此配置重新测试」，会加载该次测试的 workload 文件到左侧预览区，方便基于历史配置继续调整。

### 中间快照

长时间测试（如疲劳测试）每分钟自动保存一次中间结果到 `result_snapshot.json`。即使测试因意外中断，也能从快照中查看已完成的结果。

### 存储位置

历史记录存储在 `webui-runs/` 目录，每次测试一个子目录：

```
webui-runs/{testId}/
├── meta.json              # 测试元信息
├── workload.properties    # 完整配置快照
├── output.log             # 完整日志
├── result_snapshot.json   # 中间快照（每分钟更新）
└── result.json            # 最终结果
```

---

## 7. 常见问题（FAQ）

**Q: 测试发起后日志区没有输出**

A: 检查 `application.properties` 中的 JAR 路径配置（`webui.hbase.jar.path` / `webui.table.jar.path`），确认 JAR 文件存在。运行 `./deploy.sh logs` 查看 webui 自身日志。

**Q: 建表执行报错 "Access denied"**

A: 确认 `sysUserName` 有 `CREATE TABLE` 权限。

**Q: prefix 模式测试失败**

A: prefix 模式要求 `insertorder=ordered`，Step 2 选择 prefix 模式后会自动设置。如果手动在预览区修改了该值，服务端会在启动时校验并拒绝。

**Q: 历史记录显示 UNKNOWN 状态**

A: 服务重启时该测试正在运行，重启后状态标记为 UNKNOWN。检查对应 `output.log` 确认实际结果。

**Q: SSE 日志流断开重连**

A: 日志会从断点自动续传。若在 nginx 后面部署，需在配置中关闭缓冲：
```nginx
proxy_buffering off;
proxy_cache off;
```

**Q: 多个测试并发时系统变慢**

A: 每个 YCSB 进程占用约 512MB 堆内存。降低 `webui.ycsb.jvm.max-heap` 或减少 `webui.max.concurrent.tests`。

**Q: 点击「发起新测试」按钮变灰**

A: 已达到并发测试上限（默认 5）。等待运行中测试完成，或在 `application.properties` 中增大 `webui.max.concurrent.tests`。

**Q: 刷新页面后活动测试栏清空**

A: 这是预期行为（前端状态不持久化）。刷新后可在「历史记录」Tab 中找到 RUNNING 状态的记录，通过「查看日志」跟踪运行中的测试。

---

## 8. 注意事项

**执行顺序**：必须先运行 `load` 类型测试写入数据，再进行 `read`/`scan`/`batch_read` 测试。

**资源规划**：
- 每个 YCSB 子进程默认限制 512MB 堆内存（`webui.ycsb.jvm.max-heap`）
- webui 自身限制 256MB 堆内存（在 `deploy.sh` 启动参数中配置）
- N 个并发测试需预留 N×512MB + 256MB 内存

**疲劳测试建议**：
- 使用 `./deploy.sh logs --follow` 持续监控服务日志
- 定期检查磁盘空间：`du -sh webui-runs/`
- 历史记录超过 100 条时自动删除最旧的（可通过 `webui.runs.max-count` 配置）

**密码安全**：`webui-configs/` 目录下的配置文件包含明文密码，建议设置文件权限：
```bash
chmod 600 webui-configs/*.properties
```

**nginx 反向代理**：SSE 实时日志需要关闭 nginx 的代理缓冲：
```nginx
location / {
    proxy_pass http://localhost:8080;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 3600s;
}
```
