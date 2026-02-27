# YCSB Web UI 扩展指南

本文档面向需要修改或扩展 Web UI 的开发者，重点说明**模块描述文件**机制。

## 1. 设计思路：元数据驱动 UI

Web UI 的表单字段、参数白名单、测试类型、连接模式等**全部来自 JSON 描述文件**，Java 和 JS 代码本身不包含任何模块专属知识。

这意味着：
- **obkv-hbase 内部实现重构**（不改参数）→ 零 UI 改动
- **obkv-hbase 新增一个连接参数** → 只改 `obkv-hbase.json`，重启服务
- **新增一个全新的 YCSB 模块** → 新建 JSON 描述文件 + 配置 JAR 路径，重启服务

## 2. 描述文件位置

```
webui/src/main/resources/modules/
├── obkv-hbase.json
└── obkv-table.json
```

`ModuleService` 在 Spring Boot 启动时通过 `classpath:modules/*.json` 扫描加载所有文件。**修改后需要重启服务**（不支持热加载）。

## 3. 描述文件完整格式

```json
{
  "moduleId": "obkv-hbase",        // 唯一模块 ID，用于 API 路径和参数识别
  "displayName": "OBKV HBase",     // UI 下拉显示名
  "jarPathConfig": "webui.hbase.jar.path",   // application.properties 中的 JAR 路径配置键
  "workdirConfig": "webui.hbase.workdir",    // application.properties 中的工作目录配置键
  "commandFlags": ["-s"],          // 追加到 java -jar 命令末尾的固定标志
  "loadFlag": "-load",             // load 类型测试时追加的标志（非 load 测试追加 -t）

  "connectionModes": {
    "odp": {
      "fields": [ ... ]   // ODP 模式专属字段
    },
    "direct": {
      "fields": [ ... ]   // 直连模式专属字段
    },
    "common": {
      "fields": [ ... ]   // 两种模式都显示的公共字段
    }
  },

  "tableModes": [ ... ],     // 表模式列表（Step 2）
  "partitionTypes": [ ... ], // 分区类型列表（Step 2，部分模块为空数组）
  "testTypes": [ ... ],      // 测试类型列表（Step 3）
  "knownParams": [ ... ]     // 已知参数白名单（其余参数归入「自定义参数」区）
}
```

### 3.1 fields 字段定义

每个 `fields` 数组项是一个字段描述对象：

```json
{
  "key": "hbase.oceanbase.odpAddr",  // YCSB properties 中的参数键名
  "label": "ODP 地址",              // UI 显示的标签
  "type": "text",                   // 字段类型（见下表）
  "required": true,                 // 是否必填（前端显示 * 提示）
  "defaultValue": "2883",           // 默认值
  "options": ["a", "b"],            // select 类型的选项列表
  "value": "fixed-value"            // hidden 类型的固定值
}
```

**支持的 `type` 值**：

| type | 渲染方式 | 说明 |
|------|---------|------|
| `text` | `<input type="text">` | 普通文本输入 |
| `number` | `<input type="number">` | 数字输入 |
| `password` | `<input type="password">` | 密码输入，内容不可见 |
| `boolean` | `<input type="checkbox">` | 布尔值开关 |
| `select` | `<select>` | 下拉选择，配合 `options` 使用 |
| `hidden` | 不渲染 | 不在 UI 显示，但 `value` 会写入 workload 文件 |

### 3.2 tableModes 格式

```json
{
  "id": "prefix",                    // 模式 ID
  "label": "Prefix 模式",           // UI 显示名
  "fields": [ ... ],                // 选中此模式时额外显示的字段
  "fixedValues": {                  // 选中此模式时强制写入的参数值
    "insertorder": "ordered"
  },
  "validation": [                   // 服务端校验规则（目前支持 insertorder 检查）
    {
      "rule": "insertorder_must_be_ordered",
      "message": "prefix 模式强制要求 insertorder=ordered"
    }
  ],
  "dbClass": "com.example.MyDB"     // 非空时，运行命令追加 -db 参数（obkv-table 用）
}
```

### 3.3 testTypes 格式

```json
{
  "id": "scan",
  "label": "scan",
  "isLoad": false,                  // true 表示这是 load 类型，追加 -load 而非 -t
  "proportions": {                  // 自动写入 workload 的操作比例
    "scanproportion": 1
  },
  "extraFields": [ ... ],           // 选中此测试类型时额外显示的字段
  "dbClass": "..."                  // 非空时追加 -db 参数（优先于 tableMode 的 dbClass）
}
```

---

## 4. 扩展场景示例

### 场景一：给 obkv-hbase 新增一个连接参数

假设 obkv-hbase 新增了 `hbase.oceanbase.connectTimeout` 参数：

**步骤 1**：在 `obkv-hbase.json` 的 `connectionModes.common.fields` 中追加：

```json
{
  "key": "hbase.oceanbase.connectTimeout",
  "label": "连接超时(ms)",
  "type": "number",
  "defaultValue": "5000"
}
```

**步骤 2**：在 `knownParams` 列表中追加 `"hbase.oceanbase.connectTimeout"`

**步骤 3**：重启服务 `./deploy.sh restart`

无需修改任何 Java 或 JS 代码。

---

### 场景二：给 obkv-hbase 新增一种 testMode

假设新增 `time_range` 模式：

**步骤 1**：在 `obkv-hbase.json` 的 `tableModes` 数组中追加：

```json
{
  "id": "time_range",
  "label": "时间范围模式",
  "fields": [
    {"key": "obkv.enableTimeRangeTestMode", "label": "启用时间范围", "type": "hidden", "value": "true"},
    {"key": "obkv.keyCount", "label": "Key 数量", "type": "number", "required": true}
  ]
}
```

**步骤 2**：在 `knownParams` 中追加 `"obkv.enableTimeRangeTestMode"` 和 `"obkv.keyCount"`（如果未加入）

**步骤 3**：重启服务

---

### 场景三：新增一个全新的 YCSB 模块（如 obkv-kv）

**步骤 1**：新建 `webui/src/main/resources/modules/obkv-kv.json`，按格式填写完整描述

```json
{
  "moduleId": "obkv-kv",
  "displayName": "OBKV KV",
  "jarPathConfig": "webui.kv.jar.path",
  "workdirConfig": "webui.kv.workdir",
  "commandFlags": ["-s"],
  "loadFlag": "-load",
  "connectionModes": { ... },
  "tableModes": [ ... ],
  "partitionTypes": [],
  "testTypes": [ ... ],
  "knownParams": [ ... ]
}
```

**步骤 2**：在 `application.properties` 中配置新模块的 JAR 路径和工作目录：

```properties
webui.kv.jar.path=obkv-kv/build/obkv-kv-0.18.0-SNAPSHOT-jar-with-dependencies.jar
webui.kv.workdir=obkv-kv
```

**步骤 3**：重启服务。新模块会自动出现在 UI 顶部的模块下拉列表中。

无需修改任何 Java Controller/Service 代码或前端 JS 代码。

---

### 场景四：修改测试类型的命令行参数

假设某模块的 `scan` 测试需要追加 `-db ScanSpecialDB`：

在该模块的 `testTypes` 中找到 `scan` 条目，添加 `dbClass`：

```json
{
  "id": "scan",
  "label": "scan",
  "isLoad": false,
  "proportions": {"scanproportion": 1},
  "dbClass": "com.example.ScanSpecialDB"
}
```

---

## 5. 注意事项与限制

### ModuleDescriptor.java 同步

如果需要在描述文件中添加**全新的顶层字段**（当前 schema 中不存在的），需要同步更新 `ModuleDescriptor.java` POJO。现有字段定义已标注 `@JsonIgnoreProperties(ignoreUnknown = true)`，新增子字段（如在 `TableMode` 中加一个新属性）同样需要在对应的内部类中添加 getter/setter。

### 不支持热加载

描述文件在 `@PostConstruct` 中一次性加载到内存，修改后必须重启服务才能生效。如需热加载，可参考 `ModuleService.loadDescriptors()` 方法，添加文件监听机制（标准实现不包含此功能）。

### knownParams 白名单的重要性

`knownParams` 列表决定了哪些参数会被识别为「已知参数」（填充到表单中），哪些归入「自定义参数」区。

**遗漏参数的影响**：加载旧版本保存的配置文件时，被遗漏的参数会出现在「自定义参数」区而非对应表单字段，功能不受影响，只是 UI 显示位置不同。

建议将模块所有公开参数都加入 `knownParams`，包括那些只在特定模式下生效的参数。

### 参数覆盖顺序

workload.properties 最终内容的写入顺序：
1. 通用参数（workload class、recordcount 等）
2. 操作比例（来自 testTypes.proportions）
3. 连接配置参数
4. 表模式参数
5. 测试类型额外参数
6. fixedValues（强制值，如 prefix 模式的 insertorder=ordered）
7. 自定义参数（最后写入，覆盖前面所有同名参数）

Java Properties 读取时，后出现的同名键会覆盖前面的值，这与直觉一致：自定义参数优先级最高。
