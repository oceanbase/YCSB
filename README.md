<!--
Copyright (c) 2010 Yahoo! Inc., 2012 - 2016 YCSB contributors.
All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You
may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. See accompanying
LICENSE file.
-->

# YCSB for OBKV

基于 YCSB (Yahoo! Cloud Serving Benchmark) 框架的 OceanBase KV 性能测试工具，支持 **OBKV-HBase** 和 **OBKV-Table** 两种模型，提供 **Web UI** 和**命令行**两种使用方式。

## 两种使用方式

### Web UI（图形界面）

```bash
./deploy.sh start --rebuild     # 首次启动（自动编译 + 启动）
# 浏览器访问 http://localhost:8080
```

通过浏览器完成建表、配置、运行测试、查看结果。

### 命令行（黑屏）

```bash
cd obkv-table && ./build.sh
./create_table.sh --mode range 4 1000
# 在 OceanBase 中执行生成的 SQL
./run_fast_test.sh load
./run_fast_test.sh read
```

## 文档索引

| 文档 | 说明 | 适合读者 |
|------|------|----------|
| [快速入门](docs/getting-started.md) | 5 分钟跑通第一次测试 | 所有人（首先阅读） |
| [Web UI 使用指南](docs/guide-webui.md) | 图形界面完整操作说明 | Web UI 用户 |
| [命令行使用指南](docs/guide-cli.md) | 黑屏方式完整操作说明 | 命令行用户 |
| [参数配置大全](docs/params-reference.md) | 所有参数的含义、类型、默认值 | 需要调参的用户 |
| [Workload 参考](docs/workload-reference.md) | Workload 文件模板与示例 | 需要写配置的用户 |
| [OBKV-Table 模块详解](docs/module-obkv-table.md) | 表结构、分区策略、建表脚本 | 深入 OBKV-Table |
| [OBKV-HBase 模块详解](docs/module-obkv-hbase.md) | 表模型、测试模式、分区策略 | 深入 OBKV-HBase |
| [Web UI 架构设计](docs/webui-architecture.md) | 架构、SSE、资源隔离 | 开发者 |
| [Web UI 扩展指南](docs/webui-extension-guide.md) | 新增模块或修改参数 | 开发者 |
| [Web UI API 参考](docs/webui-api-reference.md) | REST API 完整文档 | 开发者 |
| [常见问题](docs/faq.md) | FAQ 汇总 | 遇到问题时查阅 |
