# OBKV-HBase 压测控制台前端

基于 React + TypeScript + Vite 构建的现代化压测控制台前端。

## 功能模块

### 1. SQL 连接参数
用于 DDL 操作的数据库连接配置：
- IP 地址、端口、用户名、密码、数据库
- 连接测试功能

### 2. DDL 操作
表管理功能：
- **建表**：配置 总行数、分区数、Key 长度
- **预览 SQL**：生成并预览建表 SQL
- **清空表**：清空指定表数据
- **删表**：删除指定表

### 3. OBKV 连接模式
用于压测的连接配置：
- **ODP 模式**：通过代理连接（IP、Port）
- **直连模式**：直接连接（ParamUrl、SysUser）
- 公共参数：FullUserName、Password、Database

### 4. 客户端参数
- 连接池大小
- 服务端执行超时
- 客户端等待超时
- Debug 日志开关

### 5. 负载控制
支持 6 种 YCSB 标准 Workload：

| Workload | 说明 | 读写比例 |
|----------|------|---------|
| A | Update Heavy | 50% Read / 50% Update |
| B | Read Mostly | 95% Read / 5% Update |
| C | Read Only | 100% Read |
| D | Read Latest | 95% Read / 5% Insert |
| E | Short Ranges | 95% Scan / 5% Insert |
| F | RMW | 50% Read / 50% RMW |

每个 Workload 可配置：
- Record Count（记录数）
- Operation Count（操作数）
- Thread Count（线程数）
- Target（目标 ops/s）
- Field Length（字段长度）

### 6. 执行日志
实时查看任务执行状态：
- WebSocket 实时日志推送
- 任务状态监控
- 结果展示
- 任务停止功能

## 快速开始

### 前置条件
- Node.js 18+
- npm 或 yarn

### 安装依赖

```bash
cd web
npm install
```

### 开发模式

```bash
npm run dev
```

访问 http://localhost:3000

### 生产构建

```bash
npm run build
```

构建产物在 `dist/` 目录

### 预览生产构建

```bash
npm run preview
```

## 项目结构

```
web/
├── public/
│   └── vite.svg              # 网站图标
├── src/
│   ├── api/
│   │   └── index.ts          # API 请求封装
│   ├── components/
│   │   ├── common/           # 通用组件
│   │   │   ├── Button.tsx
│   │   │   ├── Card.tsx
│   │   │   └── Input.tsx
│   │   ├── SqlConnectionPanel.tsx
│   │   ├── DdlPanel.tsx
│   │   ├── ObkvConnectionPanel.tsx
│   │   ├── ClientConfigPanel.tsx
│   │   ├── WorkloadPanel.tsx
│   │   └── LogPanel.tsx
│   ├── styles/
│   │   └── index.css         # 全局样式和 CSS 变量
│   ├── types/
│   │   └── index.ts          # TypeScript 类型定义
│   ├── App.tsx               # 主应用组件
│   ├── App.css               # 主应用样式
│   └── main.tsx              # 入口文件
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## 配置

### API 代理

开发模式下，API 请求会被代理到后端服务：

```typescript
// vite.config.ts
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  },
  '/ws': {
    target: 'ws://localhost:8080',
    ws: true
  }
}
```

### 环境变量

可以通过 `.env` 文件配置：

```env
VITE_API_BASE_URL=http://localhost:8080
```

## 技术栈

- **React 18** - UI 框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **CSS Variables** - 主题系统
- **WebSocket** - 实时通信

## 设计特点

- 🌙 深色主题，科技感设计
- 📱 响应式布局
- ⚡ 快速构建和热重载
- 🎨 CSS 变量驱动的主题系统
- 🔔 实时状态推送

