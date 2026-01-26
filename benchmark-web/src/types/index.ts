// SQL 连接配置
export interface SqlConnection {
  ip: string;
  port: number;
  user: string;
  password: string;
  database: string;
}

// OBKV 连接配置
export interface ObkvConnection {
  mode: 'odp' | 'direct';
  // ODP 模式
  ip?: string;
  port?: number;
  // 直连模式
  paramUrl?: string;
  sysUserName?: string;
  sysPassword?: string;
  // 公共参数
  fullUserName: string;
  password: string;
  database: string;
}

// 客户端配置
export interface ClientConfig {
  connectionPoolSize: number;
  rpcOperationTimeout: number;
  rpcExecuteTimeout: number;
  debug: boolean;
}

// 表配置
export interface TableConfig {
  partitionType: string;
  tableName: string;
  columnFamily: string;
  maxKey: number;
  partitionCount: number;
  keyLength: number;
  keyPartitionCount?: number;
}

// Workload 参数
export interface WorkloadParams {
  recordcount: number;
  operationcount: number;
  threadcount: number;
  target: number;
  fieldlength: number;
  // 分区相关
  rangePartitionStartTs?: number;
  rangePartitionDurationMs?: number;
  rangePartitionCount?: number;
  keyCount?: number;
  enableTimeRangeTestMode?: boolean;
}

// 任务请求
export interface TaskRequest {
  workload: string;
  modelType: 'hbase' | 'table';
  tableName: string;
  columnFamily: string;
  obkvConnection: ObkvConnection;
  clientConfig: ClientConfig;
  workloadParams: WorkloadParams;
}

// 任务状态
export type TaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'STOPPED';

// 任务响应
export interface TaskResponse {
  success: boolean;
  message?: string;
  taskId: string;
  workload: string;
  operation: string;
  status: TaskStatus;
  startTime?: number;
  endTime?: number;
  duration?: number;
  result?: string;
  logs?: string[];
}

// API 响应
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  errorCode?: string;
}

// DDL 响应
export interface DdlResponse {
  success: boolean;
  message: string;
  sql: string;
  executionTime?: number;
}

// Workload 信息
export interface WorkloadInfo {
  name: string;
  description: string;
}

// WebSocket 消息
export interface WsMessage {
  type: 'LOG' | 'STATUS' | 'RESULT' | 'ACK' | 'ERROR';
  taskId?: string;
  message?: string;
  status?: string;
  result?: string;
  timestamp: number;
}

