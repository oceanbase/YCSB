# Java 安装包目录

请将 JDK 17 安装包放入此目录。

## 支持的安装包格式

- `*jdk*17*.tar.gz`

## 推荐下载地址

### 1. Eclipse Temurin (推荐通用环境)

- 官网: https://adoptium.net/temurin/releases/
- 选择 JDK 17，Linux x64，tar.gz 格式

下载命令:
```bash
wget https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz
```

### 2. 华为毕昇 JDK (推荐国产化环境)

- 官网: https://mirrors.huaweicloud.com/kunpeng/archive/compiler/bisheng_jdk/

下载命令:
```bash
wget https://mirrors.huaweicloud.com/kunpeng/archive/compiler/bisheng_jdk/bisheng-jdk-17.0.10-linux-x64.tar.gz
```

### 3. Amazon Corretto

- 官网: https://aws.amazon.com/corretto/

下载命令:
```bash
wget https://corretto.aws/downloads/latest/amazon-corretto-17-x64-linux-jdk.tar.gz
```

## 验证安装包

放入安装包后，可以执行检查:

```bash
ls -la *.tar.gz
```

确认文件名包含 `jdk` 和 `17`。

