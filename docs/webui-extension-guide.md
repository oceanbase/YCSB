# Web UI 扩展指南

本文面向需要修改或扩展 Web UI 的开发者，重点说明**模块描述文件**机制。

> 架构总览请参考 [Web UI 架构设计](webui-architecture.md)；使用说明请参考 [Web UI 使用指南](guide-webui.md)。

---

## 1. 设计思路：元数据驱动 UI

Web UI 的表单字段、参数白名单、测试类型、连接模式等**全部来自 JSON 描述文件**，Java 和 JS 代码本身不包含任何模块专属知识。

- obkv-hbase 内部重构（不改参数）→ 零 UI 改动
- obkv-hbase 新增连接参数 → 只改 `obkv-hbase.json`，重启服务
- 新增全新 YCSB 模块 → 新建 JSON 描述文件 + 配置 JAR 路径，重启服务

## 2. 描述文件位置

```
webui/src/main/resources/modules/
├── obkv-hbase.json
└── obkv-table.json
```

`ModuleService` 启动时通过 `classpath:modules/*.json` 扫描加载。**修改后需重启服务**。

## 3. 描述文件完整格式

```json
{
  "moduleId": "obkv-hbase",
  "displayName": "OBKV HBase",
  "jarPathConfig": "webui.hbase.jar.path",
  "workdirConfig": "webui.hbase.workdir",
  "commandFlags": ["-s"],
  "loadFlag": "-load",

  "connectionModes": {
    "odp": { "fields": [ ... ] },
    "direct": { "fields": [ ... ] },
    "common": { "fields": [ ... ] }
  },

  "tableModes": [ ... ],
  "partitionTypes": [ ... ],
  "testTypes": [ ... ],
  "knownParams": [ ... ]
}
```

### 3.1 fields 字段定义

```json
{
  "key": "hbase.oceanbase.odpAddr",
  "label": "ODP 地址",
  "type": "text",
  "required": true,
  "defaultValue": "2883",
  "options": ["a", "b"],
  "value": "fixed-value"
}
```

**支持的 `type` 值**：

| type | 渲染方式 | 说明 |
|------|---------|------|
| `text` | `<input type="text">` | 普通文本 |
| `number` | `<input type="number">` | 数字 |
| `password` | `<input type="password">` | 密码 |
| `boolean` | `<input type="checkbox">` | 布尔开关 |
| `select` | `<select>` | 下拉选择，配合 `options` |
| `hidden` | 不渲染 | 固定值写入 workload |

### 3.2 tableModes 格式

```json
{
  "id": "prefix",
  "label": "Prefix 模式",
  "fields": [ ... ],
  "fixedValues": { "insertorder": "ordered" },
  "validation": [
    { "rule": "insertorder_must_be_ordered", "message": "..." }
  ],
  "dbClass": "com.example.MyDB"
}
```

### 3.3 testTypes 格式

```json
{
  "id": "scan",
  "label": "scan",
  "isLoad": false,
  "proportions": { "scanproportion": 1 },
  "extraFields": [ ... ],
  "dbClass": "..."
}
```

---

## 4. 扩展场景示例

### 场景一：新增连接参数

在 `obkv-hbase.json` 的 `connectionModes.common.fields` 追加字段，在 `knownParams` 追加参数名，重启服务。无需改代码。

### 场景二：新增测试模式

在 `tableModes` 数组追加新模式条目，在 `knownParams` 追加相关参数，重启服务。

### 场景三：新增全新模块

1. 新建 `webui/src/main/resources/modules/obkv-kv.json`
2. 在 `application.properties` 配置 JAR 路径和工作目录
3. 重启服务，新模块自动出现在 UI 下拉列表

### 场景四：修改测试命令行参数

在 `testTypes` 对应条目添加 `dbClass` 字段即可。

---

## 5. 注意事项

- **ModuleDescriptor 同步**：新增描述文件中的全新顶层字段需同步更新 `ModuleDescriptor.java`
- **不支持热加载**：描述文件在 `@PostConstruct` 一次性加载，修改后必须重启
- **knownParams 白名单**：遗漏的参数会出现在「自定义参数」区而非表单字段，功能不受影响
- **参数覆盖顺序**：通用参数 → 操作比例 → 连接配置 → 表模式 → 测试类型 → fixedValues → 自定义参数（最高优先级）

---

## 相关文档

- [Web UI 架构设计](webui-architecture.md) — 整体架构与设计决策
- [Web UI API 参考](webui-api-reference.md) — REST API 完整文档
- [Web UI 使用指南](guide-webui.md) — 用户操作说明
