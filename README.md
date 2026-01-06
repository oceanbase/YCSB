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
本 YCSB 发行版包含两个 OBKV 模型绑定：

## OBKV-HBase 绑定

**obkv-hbase** 绑定用于测试 OceanBase HBase 兼容模式的性能。

**功能特性：**
- 支持 ODP 模式和直连模式
- Range 分区配合 Key 子分区
- 多版本模式支持
- 批量操作（batchPut、batchRead）
- 自动建表脚本

**快速开始：**
```sh
cd obkv-hbase
./build.sh
./create_table.sh 1 40
./run_fast_test.sh load
./run_fast_test.sh read
```

详细文档请参考 [obkv-hbase/README.md](obkv-hbase/README.md)。

## OBKV-Table 绑定

**obkv-table** 绑定用于测试 OceanBase Table 模型的性能。

**功能特性：**
- 支持 Range 分区和 Range+Key 分区
- 灵活的表创建配置，支持多种分区策略
- 支持多种工作负载类型

**快速开始：**
```sh
cd obkv-table
./build.sh
./create_table.sh --mode range 4 1000
./run_fast_test.sh load
./run_fast_test.sh read
```

详细文档请参考 [obkv-table/README.md](obkv-table/README.md)。

