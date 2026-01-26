# OBKV-HBase 压测控制台 - 打包与部署指南

## 📋 概述

本工具支持离线部署，打包后可直接复制到无网络环境的服务器运行。

---

## 🔧 第一步：打包（在开发机器上执行）

### 前置条件

| 依赖 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | 编译和运行 |
| Maven | 3.6+ | 编译项目 |

### 准备 JDK 安装包

打包前需要先下载 Linux x64 版本的 JDK 17 安装包：

```bash
# 进入 deploy/packages 目录
cd obkv-hbase/deploy/packages

# 下载 Eclipse Temurin JDK 17
curl -L -O https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz
```

### 执行打包

```bash
cd obkv-hbase
./package.sh
```

打包完成后，输出文件位于：
```
obkv-hbase/dist/obkv-hbase-console-offline-<日期>.tar.gz
```

---

## 📦 第二步：部署（在目标服务器上执行）

### 2.1 复制文件到服务器

```bash
scp dist/obkv-hbase-console-offline-*.tar.gz user@server:/home/user/
```

### 2.2 解压

```bash
ssh user@server
cd /home/user
tar -xzf obkv-hbase-console-offline-*.tar.gz
cd obkv-hbase-console-offline-*
```

### 2.3 部署方式

#### 方式一：完整部署（推荐首次使用）

包含 Java 环境安装，需要 root 权限：

```bash
sudo ./deploy/deploy.sh
```

此命令会自动：
1. ✅ 检测系统环境
2. ✅ 安装 Java 17（如果不存在）
3. ✅ 配置环境变量
4. ✅ 启动服务

#### 方式二：直接启动（已有 Java 17+）

如果服务器已安装 Java 17+，可直接启动：

```bash
./start.sh
```

---

## 🌐 第三步：访问控制台

启动成功后，访问：

```
http://<服务器IP>:8081
```

---

## 🛠️ 常用命令

| 操作 | 命令 |
|------|------|
| 启动服务 | `./start.sh` |
| 停止服务 | `./stop.sh` |
| 重启服务 | `./restart.sh` |
| 查看日志 | `tail -f /tmp/obkv-backend.log` |
| 诊断信息 | `curl http://localhost:8081/api/diag` |

---

## 🔍 问题排查

### 1. 服务启动失败

```bash
# 查看日志
cat /tmp/obkv-backend.log

# 查看诊断信息
curl http://localhost:8081/api/diag
```

### 2. 任务执行失败

```bash
# 查看后端日志
tail -100 /tmp/obkv-backend.log | grep -i error

# 检查 workloads 目录
ls -la workloads/workloads_a_f/
```

### 3. Java 环境问题

```bash
# 检查 Java 版本
java -version

# 手动安装 Java
sudo ./deploy/deploy.sh --java
source /etc/profile
```

### 4. 端口被占用

```bash
# 查看端口占用
ss -tulnp | grep 8081

# 杀死占用进程
./stop.sh
```

---

## 📁 目录结构

```
obkv-hbase-console-offline-*/
├── deploy/                    # 部署脚本
│   ├── deploy.sh             # 主部署脚本
│   ├── install_java.sh       # Java 安装脚本
│   └── packages/             # JDK 安装包
├── server/
│   └── target/               # 后端 JAR
├── target/                    # YCSB 客户端 JAR
├── workloads/                 # Workload 配置
│   └── workloads_a_f/        # Workload A-G
├── docs/                      # 文档
├── result/                    # 测试结果
├── start.sh                   # 启动脚本
├── stop.sh                    # 停止脚本
├── restart.sh                 # 重启脚本
├── QUICK_START.txt           # 快速启动指南
└── DEPLOY.md                 # 本文件
```

---

## 📊 日志文件位置

| 日志 | 位置 |
|------|------|
| 后端服务日志 | `/tmp/obkv-backend.log` |
| 测试结果日志 | `result/` 目录 |

---

## ⚙️ 配置修改

### 修改服务端口

编辑 `server/src/main/resources/application.yml`（需要重新打包）：

```yaml
server:
  port: 8081  # 修改为其他端口
```

或者临时修改（不需要重新打包）：

```bash
java -jar server/target/*.jar --server.port=9090
```

