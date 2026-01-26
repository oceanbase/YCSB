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


# YCSB for OBKV (HBase & Table)

本 YCSB 发行版包含两个 OBKV 模型绑定（HBase & Table），并提供了一个统一的 Web 压测控制台和分段导入工具。

## 统一压测控制台

**快速开始：**
```sh
# 1. 编译项目 (包含 core, obkv-hbase, obkv-table, benchmark-server)
./build.sh

# 2. 启动控制台 (默认端口 8081)
./start.sh
```
访问 `http://localhost:8081` 即可使用 Web 界面进行压测管理、DDL 操作和日志查看。

## 核心工具脚本 (根目录)

- `build.sh`: 一键编译所有模块。
- `start.sh` / `stop.sh`: 启动/停止 Web 控制台。
- `create_table.sh`: 命令行 DDL 工具。
- `segment_load.sh`: 分段并行导入数据工具。
- `package.sh`: 生成离线部署包。

---

## OBKV-HBase 绑定
**obkv-hbase** 绑定用于测试 OceanBase HBase 兼容模式的性能。
详细文档请参考 [obkv-hbase/README.md](obkv-hbase/README.md)。

## OBKV-Table 绑定
**obkv-table** 绑定用于测试 OceanBase Table 模型的性能。
详细文档请参考 [obkv-table/README.md](obkv-table/README.md)。

