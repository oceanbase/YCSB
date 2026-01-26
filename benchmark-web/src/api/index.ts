import type { 
  ApiResponse, 
  SqlConnection, 
  TableConfig, 
  TaskRequest, 
  TaskResponse, 
  DdlResponse,
  WorkloadInfo 
} from '../types';

const API_BASE = '/api';

async function request<T>(url: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const response = await fetch(`${API_BASE}${url}`, {
    headers: {
      'Content-Type': 'application/json',
    },
    ...options,
  });
  
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  
  return response.json();
}

// SQL 连接 API
export const sqlApi = {
  testConnection: (connection: SqlConnection) => 
    request<boolean>('/sql/test-connection', {
      method: 'POST',
      body: JSON.stringify(connection),
    }),
};

// DDL API
export const ddlApi = {
  generateSql: (config: TableConfig) =>
    request<string>('/ddl/generate-sql', {
      method: 'POST',
      body: JSON.stringify(config),
    }),
    
  createTable: (sqlConnection: SqlConnection, tableConfig: TableConfig) =>
    request<DdlResponse>('/ddl/create-table', {
      method: 'POST',
      body: JSON.stringify({ sqlConnection, tableConfig }),
    }),
    
  truncateTable: (sqlConnection: SqlConnection, tableName: string, columnFamily: string, modelType: string) =>
    request<DdlResponse>('/ddl/truncate-table', {
      method: 'POST',
      body: JSON.stringify({ sqlConnection, tableName, columnFamily, modelType }),
    }),
    
  dropTable: (sqlConnection: SqlConnection, tableName: string, columnFamily: string, modelType: string) =>
    request<DdlResponse>('/ddl/drop-table', {
      method: 'POST',
      body: JSON.stringify({ sqlConnection, tableName, columnFamily, modelType }),
    }),
};

// 任务 API
export const taskApi = {
  load: (taskRequest: TaskRequest) =>
    request<TaskResponse>('/task/load', {
      method: 'POST',
      body: JSON.stringify(taskRequest),
    }),
    
  run: (taskRequest: TaskRequest) =>
    request<TaskResponse>('/task/run', {
      method: 'POST',
      body: JSON.stringify(taskRequest),
    }),
    
  getStatus: (taskId: string) =>
    request<TaskResponse>(`/task/status/${taskId}`),
    
  stop: (taskId: string) =>
    request<boolean>(`/task/stop/${taskId}`, {
      method: 'POST',
    }),
    
  list: () =>
    request<TaskResponse[]>('/task/list'),
    
  getWorkloads: () =>
    request<WorkloadInfo[]>('/task/workloads'),
    
  clear: () =>
    request<number>('/task/clear', {
      method: 'POST',
    }),
};

// 健康检查 API
export const healthApi = {
  check: () => request<{ status: string; timestamp: number }>('/health'),
  info: () => request<object>('/info'),
};

