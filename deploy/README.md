# OBKV-HBase 压测控制台 - 离线部署包

本目录包含离线环境部署所需的所有脚本和依赖。

## 📁 目录结构

```
deploy/
├── deploy.sh           # 主部署脚本（入口）
├── install_java.sh     # Java 安装脚本
├── pack.sh             # 打包脚本（联网环境使用）
├── packages/           # 依赖包目录
│   └── OpenJDK17U-*.tar.gz   # Java 17 安装包
└── README.md           # 本文件
```

## 🚀 快速部署（离线环境）

### 1. 完整部署（推荐）

```bash
# 需要 root 权限（安装 Java）
sudo ./deploy.sh
```

此命令会自动：
1. 检查系统环境
2. 安装 Java 17（如需）
3. 检查项目文件
4. 配置并启动服务

### 2. 分步部署

```bash
# 仅检查环境
./deploy.sh --check

# 仅安装 Java（需要 root）
sudo ./deploy.sh --java

# 仅启动服务
./deploy.sh --start
```

## 📦 打包部署包（联网环境）

在联网环境完成编译后，生成完整部署包：

```bash
./pack.sh
```

会在 `../dist/` 目录生成 `obkv-hbase-console-*.tar.gz`。

## ⚙️ 配置说明

### 端口修改

默认端口 8081，如需修改，编辑配置文件：

```bash
vim ../server/src/main/resources/application.yml
```

修改 `server.port` 值。

### Java 安装包

已包含 Eclipse Temurin JDK 17（x64 Linux）。

如需更换，将新的 JDK 包放入 `packages/` 目录，文件名需包含 `jdk` 和 `17`。

## 📋 系统要求

| 要求 | 说明 |
|------|------|
| 操作系统 | Linux (CentOS/RHEL/Kylin/Ubuntu) |
| CPU 架构 | x86_64 (amd64) |
| 内存 | 建议 2GB+ |
| 磁盘 | 约 500MB（含 Java） |

## 🔧 常见问题

### Q: 端口被占用

```bash
# 查看占用进程
ss -tulnp | grep 8081

# 停止服务后重试
../stop.sh
./deploy.sh --start
```

### Q: 权限不足

```bash
# 使用 sudo 运行
sudo ./deploy.sh
```

### Q: Java 安装失败

手动解压安装：

```bash
sudo mkdir -p /usr/local/java
sudo tar -zxf packages/*.tar.gz -C /usr/local/java/
export JAVA_HOME=/usr/local/java/<解压目录>
export PATH=$JAVA_HOME/bin:$PATH
```

## 📞 支持

如遇问题，请检查日志：

```bash
cat ../server/console.log
```
