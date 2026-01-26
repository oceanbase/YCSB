# OBKV-HBase 分段 Load 数据使用手册

## 目录

1. [概述](#概述)
2. [快速开始](#快速开始)
3. [连接模式](#连接模式)
4. [参数说明](#参数说明)
5. [使用示例](#使用示例)
6. [多机并行加载](#多机并行加载)
7. [运维操作](#运维操作)
8. [常见问题](#常见问题)

---

## 概述

`segment_load.sh` 是一个分段并行加载数据的脚本，用于快速导入大量测试数据到 OceanBase OBKV-HBase 表中。

### 核心特性

- **分段并行**：将数据分成多个段，并行加载提高效率
- **两种连接模式**：支持 ODP 代理模式和直连模式
- **灵活配置**：支持连接池、超时、Netty Buffer 等参数
- **实时监控**：支持查看状态和停止运行中的任务

### 工作原理

脚本利用 YCSB 的 `insertstart` 和 `insertcount` 参数，将数据按 Key 范围分成多段，每段启动一个独立的 Java 进程并行加载。

```
总数据: 100,000,000 条
分段数: 10

分段 0: key 0         - 9,999,999    (1000万条)
分段 1: key 10,000,000 - 19,999,999  (1000万条)
分段 2: key 20,000,000 - 29,999,999  (1000万条)
...
分段 9: key 90,000,000 - 99,999,999  (1000万条)
```

---

## 快速开始

### 前提条件

1. Java 17+ 已安装
2. YCSB JAR 已编译 (`target/*-jar-with-dependencies.jar`)
3. OceanBase 表已创建

### 最简命令

```bash
# ODP 模式
./segment_load.sh --odp \
  --odp-ip 192.168.1.100 \
  --odp-port 2883 \
  --full-user "user@tenant#cluster" \
  --password "password" \
  --database "testdb" \
  100000000 10 32

# 直连模式
./segment_load.sh --direct \
  --param-url "http://192.168.1.100:8080/services" \
  --sys-user "root" \
  --sys-password "rootpwd" \
  --full-user "user@tenant#cluster" \
  --password "password" \
  100000000 10 32
```

---

## 连接模式

### ODP 模式

通过 OceanBase Database Proxy (ODP) 代理连接，适用于生产环境。

**参数说明：**

| 参数 | 说明 | 必填 | 示例 |
|------|------|------|------|
| `--odp` | 启用 ODP 模式 | ✅ 必填 | - |
| `--odp-ip` | ODP 服务器 IP | ✅ 必填 | `192.168.1.100` |
| `--odp-port` | ODP 服务器端口 | ✅ 必填 | `2883` |
| `--full-user` | 完整用户名 | ✅ 必填 | `user@tenant#cluster` |
| `--password` | 用户密码 | 可选 | `mypassword` |
| `--database` | 数据库名 | ✅ 必填 | `testdb` |

**示例：**

```bash
./segment_load.sh --odp \
  --odp-ip 192.168.1.100 \
  --odp-port 2883 \
  --full-user "testuser@testtenant#testcluster" \
  --password "mypassword" \
  --database "testdb" \
  --table "usertable" \
  100000000 10 32
```

### 直连模式

直接连接 OceanBase 集群，适用于测试环境或内网环境。

**参数说明：**

| 参数 | 说明 | 必填 | 示例 |
|------|------|------|------|
| `--direct` | 启用直连模式 | ✅ 必填 | - |
| `--param-url` | ConfigServer URL | ✅ 必填 | `http://192.168.1.100:8080/services` |
| `--sys-user` | 系统用户名 | ✅ 必填 | `root` |
| `--sys-password` | 系统用户密码 | 可选 | `rootpassword` |
| `--full-user` | 完整用户名 | ✅ 必填 | `user@tenant#cluster` |
| `--password` | 用户密码 | 可选 | `mypassword` |

**示例：**

```bash
./segment_load.sh --direct \
  --param-url "http://192.168.1.100:8080/services" \
  --sys-user "root" \
  --sys-password "rootpwd" \
  --full-user "testuser@testtenant#testcluster" \
  --password "mypassword" \
  --table "usertable" \
  100000000 10 32
```

---

## 参数说明

### 位置参数 (必填)

| 位置 | 参数 | 说明 | 必填 |
|------|------|------|------|
| 1 | 总记录数 | 要加载的总记录数 | ✅ 必填 |
| 2 | 分段数 | 分成多少段并行加载 | ✅ 必填 |
| 3 | 线程数 | 每段使用的线程数 | ✅ 必填 |
| 4 | workload文件 | 自定义 workload 配置 | 可选 (默认 workloada) |

### 公共选项

| 参数 | 说明 | 必填 | 默认值 |
|------|------|------|--------|
| `--table <name>` | 表名 | ✅ 必填 | - |
| `--pool-size <n>` | 连接池大小 (每分片) | 可选 | 100 |
| `--server-timeout <ms>` | 服务端超时 (毫秒) | 可选 | 200000 (200秒) |
| `--client-timeout <ms>` | 客户端超时 (毫秒) | 可选 | 300000 (300秒) |
| `--netty-low <bytes>` | Netty 低水位 | 可选 | 不设置 |
| `--netty-high <bytes>` | Netty 高水位 | 可选 | 不设置 |

### Netty Buffer 参数

当需要处理大数据量或高并发时，可以调整 Netty Buffer 参数：

```bash
./segment_load.sh --odp \
  --odp-ip 192.168.1.100 \
  --odp-port 2883 \
  --full-user "user@tenant#cluster" \
  --password "password" \
  --database "testdb" \
  --netty-low 32768 \
  --netty-high 65536 \
  100000000 10 64
```

| 参数 | 说明 | 建议值 |
|------|------|--------|
| `--netty-low` | 低水位线，低于此值恢复写入 | 32768 (32KB) |
| `--netty-high` | 高水位线，高于此值暂停写入 | 65536 (64KB) |

---

## 使用示例

### 示例 1：小规模测试

加载 100 万条数据，4 分段，16 线程：

```bash
./segment_load.sh --odp \
  --odp-ip 192.168.1.100 \
  --odp-port 2883 \
  --full-user "user@tenant#cluster" \
  --password "password" \
  --database "testdb" \
  1000000 4 16
```

### 示例 2：中等规模

加载 1000 万条数据，8 分段，32 线程，自定义超时：

```bash
./segment_load.sh --odp \
  --odp-ip 192.168.1.100 \
  --odp-port 2883 \
  --full-user "user@tenant#cluster" \
  --password "password" \
  --database "testdb" \
  --pool-size 150 \
  --server-timeout 300000 \
  --client-timeout 400000 \
  10000000 8 32
```

### 示例 3：大规模压测

加载 1 亿条数据，20 分段，64 线程，完整配置：

```bash
./segment_load.sh --direct \
  --param-url "http://192.168.1.100:8080/services" \
  --sys-user "root" \
  --sys-password "rootpwd" \
  --full-user "user@tenant#cluster" \
  --password "password" \
  --table "usertable" \
  --pool-size 200 \
  --server-timeout 600000 \
  --client-timeout 900000 \
  --netty-low 65536 \
  --netty-high 131072 \
  100000000 20 64
```

### 示例 4：使用自定义 Workload

```bash
./segment_load.sh --odp \
  --odp-ip 192.168.1.100 \
  --odp-port 2883 \
  --full-user "user@tenant#cluster" \
  --password "password" \
  --database "testdb" \
  100000000 10 32 \
  workloads/workloads_a_f/workloadc
```

---

## 多机并行加载

当单机加载速度不够时，可以在多台机器上并行加载。

### 原理

每台机器负责不同的 Key 范围，通过手动指定 `insertstart` 和 `insertcount` 实现。

### 示例：4 台机器加载 1 亿数据

**机器 1 (加载 0-25%)：**
```bash
java -jar target/*-jar-with-dependencies.jar \
  -P workloads/workloads_a_f/workloada \
  -p recordcount=100000000 \
  -p insertstart=0 \
  -p insertcount=25000000 \
  -p obkv.odp.mode=true \
  -p obkv.odp.ip=192.168.1.100 \
  -p obkv.odp.port=2883 \
  -p obkv.full.user.name="user@tenant#cluster" \
  -p obkv.password="password" \
  -p obkv.odp.database="testdb" \
  -p threadcount=64 \
  -load
```

**机器 2 (加载 25%-50%)：**
```bash
java -jar target/*-jar-with-dependencies.jar \
  -P workloads/workloads_a_f/workloada \
  -p recordcount=100000000 \
  -p insertstart=25000000 \
  -p insertcount=25000000 \
  ...
  -load
```

**机器 3 (加载 50%-75%)：**
```bash
# insertstart=50000000, insertcount=25000000
```

**机器 4 (加载 75%-100%)：**
```bash
# insertstart=75000000, insertcount=25000000
```

### 分配表

| 机器 | insertstart | insertcount | Key 范围 |
|------|-------------|-------------|----------|
| 机器1 | 0 | 25000000 | 0 - 24,999,999 |
| 机器2 | 25000000 | 25000000 | 25,000,000 - 49,999,999 |
| 机器3 | 50000000 | 25000000 | 50,000,000 - 74,999,999 |
| 机器4 | 75000000 | 25000000 | 75,000,000 - 99,999,999 |

**注意：** `recordcount` 必须在所有机器上保持一致！

---

## 运维操作

### 查看运行状态

```bash
./segment_load.sh status
```

输出示例：
```
▶ 分段加载状态

  PID 文件中的进程:
    PID 12345: 运行中
    PID 12346: 运行中
    PID 12347: 已结束
```

### 停止加载

```bash
./segment_load.sh stop
```

输出示例：
```
▶ 停止分段加载进程...

[INFO] 从 PID 文件读取进程列表...
  停止进程 12345...
  停止进程 12346...

[INFO] ✓ 已停止 2 个进程
```

### 查看日志

```bash
# 查看最新日志目录
ls -lt result/segment_load_*/

# 实时查看某个分段日志
tail -f result/segment_load_20260121_100000/segment_0.log

# 查看错误
grep -i "error\|exception" result/segment_load_*/segment_*.log
```

### 查看吞吐量

```bash
# 查看各分段吞吐量
grep "Throughput" result/segment_load_*/segment_*.log

# 汇总吞吐量
grep -oP 'Throughput\(ops/sec\), \K[0-9.]+' result/segment_load_*/segment_*.log | \
  awk '{sum+=$1} END {print "Total: " sum " ops/sec"}'
```

---

## 常见问题

### Q1: 如何选择分段数？

**建议：**
- 分段数 = CPU 核心数 × 2
- 或者 分段数 = 目标并发连接数 / 每段线程数

**示例：**
- 32 核机器 → 分段数 8-16
- 需要 1000 并发，每段 64 线程 → 分段数 16

### Q2: 出现连接超时怎么办？

增加超时时间：

```bash
./segment_load.sh --odp \
  ... \
  --server-timeout 600000 \
  --client-timeout 900000 \
  ...
```

### Q3: 加载速度慢怎么优化？

1. **增加分段数**：更多分段 = 更多并发
2. **增加线程数**：每段更多线程
3. **增加连接池**：`--pool-size 200`
4. **多机并行**：在多台机器上分别加载

### Q4: 如何查看完整命令？

每个分段的日志文件开头会记录完整命令：

```bash
head -3 result/segment_load_*/segment_0.log
```

### Q5: 密码包含特殊字符怎么办？

使用引号包裹：

```bash
--password 'my$pecial@password!'
```

或者使用转义：

```bash
--password "my\$pecial\@password\!"
```

### Q6: ODP 模式和直连模式怎么选？

| 场景 | 推荐模式 |
|------|----------|
| 生产环境 | ODP 模式 |
| 测试环境 | 直连模式 |
| 跨机房访问 | ODP 模式 |
| 内网直连 | 直连模式 |

---

## 附录：完整参数速查

```bash
./segment_load.sh \
  # 连接模式 (二选一，必填)
  --odp                        # ODP 模式
  --direct                     # 直连模式
  
  # ODP 模式参数
  --odp-ip <ip>               # ODP IP (必填)
  --odp-port <port>           # ODP 端口 (必填)
  --database <db>             # 数据库名 (必填)
  
  # 直连模式参数
  --param-url <url>           # ConfigServer URL (必填)
  --sys-user <user>           # 系统用户 (必填)
  --sys-password <pwd>        # 系统密码 (可选)
  
  # 公共连接参数
  --full-user <user>          # 完整用户名 (必填)
  --password <pwd>            # 用户密码 (可选)
  --table <name>              # 表名 (必填)
  
  # 客户端参数 (可选)
  --pool-size <n>             # 连接池 (默认: 100)
  --server-timeout <ms>       # 服务端超时 (默认: 200000)
  --client-timeout <ms>       # 客户端超时 (默认: 300000)
  --netty-low <bytes>         # Netty 低水位
  --netty-high <bytes>        # Netty 高水位
  
  # 位置参数 (必填)
  <总记录数>                   # 必填
  <分段数>                     # 必填
  <线程数>                     # 必填
  [workload文件]               # 可选

# 停止/状态
./segment_load.sh stop
./segment_load.sh status
./segment_load.sh --help
```

