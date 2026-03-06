# 快速入门

本文帮你在 5 分钟内跑通第一次 OBKV 性能测试。提供两条路径：**Web UI（图形界面）** 和**命令行（黑屏）**，选择你习惯的方式即可。

## 前置条件

- Java 8+（运行 `java -version` 验证）
- Maven 3.x（运行 `mvn -v` 验证）
- 已部署的 OceanBase 集群（需要 ODP 地址或直连 configUrl）

## 路径 A：Web UI 方式

适合希望**图形化操作**、不想手动编辑配置文件的用户。

### 1. 启动服务

```bash
# 首次启动（自动编译所有模块 + 启动 Web 服务）
./deploy.sh start --rebuild

# 后续启动（JAR 已存在，直接启动）
./deploy.sh start
```

### 2. 打开浏览器

访问 `http://localhost:8080`

![Web UI 界面概览](images/webui.png)

### 3. 建表

1. 顶部下拉选择模块（OBKV HBase 或 OBKV Table）
2. 点击「建表」Tab
3. 填写分区参数 → 点击「生成 SQL」
4. 填写数据库连接信息 → 点击「测试连接」 → 点击「执行建表」

### 4. 运行测试

1. 点击「性能测试」Tab
2. **Step 1**：填写 OceanBase 连接信息（ODP 地址/端口/用户名/密码/数据库）
3. **Step 2**：选择表模式（默认即可）
4. **Step 3**：选择测试类型（首次选 `load` 写入数据）
5. 点击「▶ 发起新测试」

### 5. 查看结果

- 右侧「实时日志」Tab 实时查看输出
- 测试完成后切换到「测试结果」Tab 查看吞吐量和延迟

> **提示**：`load` 完成后，再次发起 `read` / `scan` 测试即可测试读取性能。

若需了解建表向导、配置管理、历史记录等完整功能，请阅读 [Web UI 使用指南](guide-webui.md)。

---

## 路径 B：命令行方式

适合习惯**终端操作**、需要脚本化批量测试的用户。

### 以 OBKV-Table 为例

```bash
# 1. 编译
cd obkv-table
./build.sh

# 2. 生成建表 SQL（4 个 range 分区，最大 key 1000）
./create_table.sh --mode range 4 1000
# 生成的 SQL 文件保存在当前目录，文件名类似 kv_table_range_p4_k1000.sql

# 3. 在 OceanBase 中执行生成的 SQL 文件
#    mysql -h <host> -P <port> -u <user> -p < kv_table_range_p4_k1000.sql

# 4. 编辑 workloads/workload_load，填写连接参数
#    至少设置：obkv.isOdpMode, obkv.odpAddr, obkv.odpPort,
#             obkv.fullUserName, obkv.password, obkv.database

# 5. 加载数据
./run_fast_test.sh load

# 6. 读取测试
./run_fast_test.sh read
```

### 以 OBKV-HBase 为例

```bash
# 1. 编译
cd obkv-hbase
./build.sh

# 2. 生成建表 SQL（4 个 range 分区，最大 key 1000）
./create_table.sh --max_key 1000 --partition_count 4
# 生成的 SQL 文件保存在当前目录，文件名类似 ycsb_hbase_range_p4.sql

# 3. 在 OceanBase 中执行生成的 SQL 文件

# 4. 编辑 workloads/workload_load，填写连接参数
#    至少设置：hbase.oceanbase.odpMode, hbase.oceanbase.odpAddr,
#             hbase.oceanbase.odpPort, hbase.oceanbase.fullUserName,
#             hbase.oceanbase.password, hbase.oceanbase.database

# 5. 加载数据
./run_fast_test.sh load

# 6. 读取测试
./run_fast_test.sh read
```

若需了解 `create_table.sh` 各模式、`run_fast_test.sh` 各子命令及 workload 配置细节，请阅读 [命令行使用指南](guide-cli.md)。

---

## 下一步

根据你的需求，选择深入阅读：

- [Web UI 使用指南](guide-webui.md) — 图形界面完整操作说明
- [命令行使用指南](guide-cli.md) — 黑屏方式完整操作说明
- [参数配置大全](params-reference.md) — 所有可配置参数的含义、类型、默认值
- [常见问题](faq.md) — 遇到问题先看这里
