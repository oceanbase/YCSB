# YCSB OBKV-Table 测试工具

基于 YCSB 框架的 OBKV-Table 绑定，用于测试 OceanBase Table 模型的性能。

## 快速开始

```bash
# 编译
./build.sh

# 生成建表 SQL（4 个 range 分区，最大 key 1000）
./create_table.sh --mode range 4 1000

# 在 OceanBase 中执行生成的 SQL 后：
# 编辑 workloads/workload_load 填写连接参数
./run_fast_test.sh load    # 加载数据
./run_fast_test.sh read    # 读取测试
```

## 支持的操作

```bash
./run_fast_test.sh load          # 数据加载
./run_fast_test.sh put           # 写入测试
./run_fast_test.sh read          # 读取测试（需先 load）
./run_fast_test.sh scan          # 扫描测试（需先 load）
./run_fast_test.sh batch_put     # 批量写入
./run_fast_test.sh batch_read    # 批量读取（需先 load）
```

## 详细文档

- [快速入门](../docs/getting-started.md) — 5 分钟跑通第一次测试
- [命令行使用指南](../docs/guide-cli.md) — 完整命令行操作流程
- [OBKV-Table 模块详解](../docs/module-obkv-table.md) — 表结构、分区策略、建表脚本、配置示例
- [参数配置大全](../docs/params-reference.md) — 所有可配置参数一览
- [Workload 参考](../docs/workload-reference.md) — Workload 文件模板与示例
