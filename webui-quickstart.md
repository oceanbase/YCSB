# YCSB Web UI 使用说明

基于 YCSB 核心框架，新增 `webui` 模块，提供浏览器界面进行 OceanBase KV 性能测试。

## 前置条件

- Java 8+（`java -version` 验证）
- Maven 3.x（`mvn -v` 验证）
- 已部署的 OceanBase 集群

## 编译

### 方式一：仅编译 webui（推荐首次尝试）

适用于 `obkv-hbase` 和 `obkv-table` 的 JAR 已经编译好的情况。

```bash
cd webui
bash build.sh
```

脚本会自动检查环境，并在 `webui/build/` 目录下生成 `webui-0.18.0-SNAPSHOT.jar`。

### 方式二：一键编译所有模块

同时编译 `obkv-hbase`、`obkv-table`、`webui` 三个模块。

```bash
cd webui
bash build.sh --with-deps
```

### 方式三：通过 deploy.sh 编译（包含构建流程）

```bash
./deploy.sh build
```

### 清理构建产物

```bash
cd webui
bash build.sh clean
```

## 启动

### 一键启动（推荐）

```bash
# 首次启动：先编译再启动
./deploy.sh start --rebuild

# 后续启动：JAR 已存在，直接启动
./deploy.sh start
```

### 手动启动

```bash
java -Xmx256m -jar webui/build/webui-0.18.0-SNAPSHOT.jar
```

浏览器访问：`http://localhost:8080`

## 服务管理

```bash
# 查看运行状态
./deploy.sh status

# 实时查看日志
./deploy.sh logs --follow

# 停止服务
./deploy.sh stop

# 重启服务
./deploy.sh restart
```

## 功能

- **建表向导**：图形化配置分区参数，生成建表 SQL 并直接执行到 OceanBase
- **性能测试**：支持 obkv-hbase 和 obkv-table 两个模块，支持多测试并发运行
- **实时日志**：SSE 推流，断线自动续传，支持同时观察多个测试进度
- **结果展示**：吞吐量卡片、延迟明细表、Canvas 柱状图
- **配置管理**：保存/加载/删除测试配置，双向绑定配置预览区
- **历史记录**：每次测试的完整日志和结果持久化，支持历史回放

## 详细文档

- [使用手册](webui/doc/user-manual.md) — 界面操作、参数参考、FAQ
- [架构设计](webui/doc/architecture.md) — 整体架构、SSE 日志流、资源隔离
- [扩展指南](webui/doc/extension-guide.md) — 如何新增模块或修改参数（开发者）
- [API 参考](webui/doc/api-reference.md) — REST API 完整文档
