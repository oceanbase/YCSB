import React, { useState } from 'react';
import { Card, Input, Button } from './common';
import { taskApi } from '../api';
import type { 
  WorkloadParams, 
  ObkvConnection, 
  ClientConfig, 
  TableConfig,
  TaskResponse 
} from '../types';
import './WorkloadPanel.css';

interface WorkloadInfo {
  id: string;
  name: string;
  description: string;
  ratio: string;
}

const WORKLOADS: WorkloadInfo[] = [
  { id: 'workloada', name: 'A', description: 'Update Heavy', ratio: '50% Read / 50% Update' },
  { id: 'workloadb', name: 'B', description: 'Read Mostly', ratio: '95% Read / 5% Update' },
  { id: 'workloadc', name: 'C', description: 'Read Only', ratio: '100% Read' },
  { id: 'workloadd', name: 'D', description: 'Read Latest', ratio: '95% Read / 5% Insert' },
  { id: 'workloade', name: 'E', description: 'Short Ranges', ratio: '95% Scan / 5% Insert' },
  { id: 'workloadf', name: 'F', description: 'RMW', ratio: '50% Read / 50% RMW' },
];

interface Props {
  modelType: 'hbase' | 'table';
  tableConfig: TableConfig;
  obkvConnection: ObkvConnection;
  clientConfig: ClientConfig;
  onTaskStart: (taskId: string, workload: string, operation: string) => void;
}

export const WorkloadPanel: React.FC<Props> = ({
  modelType,
  tableConfig,
  obkvConnection,
  clientConfig,
  onTaskStart,
}: Props) => {
  const [selectedWorkload, setSelectedWorkload] = useState('workloada');
  const [params, setParams] = useState<WorkloadParams>({
    recordcount: 10000,
    operationcount: 10000,
    threadcount: 10,
    target: 0,
    fieldlength: 100,
  });
  const [loading, setLoading] = useState<'load' | 'run' | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleParamChange = (field: keyof WorkloadParams, value: number) => {
    setParams({ ...params, [field]: value });
  };

  const executeTask = async (operation: 'load' | 'run') => {
    setLoading(operation);
    setError(null);

    try {
      const request = {
        workload: selectedWorkload,
        modelType,
        tableName: tableConfig.tableName,
        columnFamily: tableConfig.columnFamily,
        obkvConnection,
        clientConfig,
        workloadParams: {
          ...params,
          rangePartitionStartTs: 1704067200000, // TODO: Make configurable or link with DDL panel
          rangePartitionDurationMs: 2592000000,
          rangePartitionCount: tableConfig.partitionCount,
          keyCount: tableConfig.maxKey,
          enableTimeRangeTestMode: modelType === 'hbase' && tableConfig.partitionType === 'range-key',
        },
      };

      const response = operation === 'load' 
        ? await taskApi.load(request)
        : await taskApi.run(request);

      if (response.success && response.data) {
        onTaskStart(response.data.taskId, selectedWorkload, operation);
      } else {
        setError(response.message || '任务启动失败');
      }
    } catch (err) {
      setError(`任务启动失败: ${err instanceof Error ? err.message : '未知错误'}`);
    } finally {
      setLoading(null);
    }
  };

  const selectedInfo = WORKLOADS.find(w => w.id === selectedWorkload);

  return (
    <Card
      title="负载控制"
      subtitle="Workload A-F 压测配置"
      icon="🚀"
    >
      <div className="workload-panel">
        <div className="workload-selector">
          {WORKLOADS.map(workload => (
            <button
              key={workload.id}
              className={`workload-btn ${selectedWorkload === workload.id ? 'active' : ''}`}
              onClick={() => setSelectedWorkload(workload.id)}
            >
              <span className="workload-name">{workload.name}</span>
              <span className="workload-desc">{workload.description}</span>
            </button>
          ))}
        </div>

        {selectedInfo && (
          <div className="workload-info">
            <span className="info-label">Workload {selectedInfo.name}:</span>
            <span className="info-value">{selectedInfo.description} - {selectedInfo.ratio}</span>
          </div>
        )}

        <div className="workload-params">
          <h4 className="params-title">参数配置</h4>
          <div className="params-grid">
            <Input
              label="Record Count"
              type="number"
              value={params.recordcount}
              onChange={(e) => handleParamChange('recordcount', parseInt(e.target.value) || 0)}
              suffix="条"
            />
            <Input
              label="Operation Count"
              type="number"
              value={params.operationcount}
              onChange={(e) => handleParamChange('operationcount', parseInt(e.target.value) || 0)}
              suffix="次"
            />
            <Input
              label="Thread Count"
              type="number"
              value={params.threadcount}
              onChange={(e) => handleParamChange('threadcount', parseInt(e.target.value) || 1)}
              suffix="线程"
            />
            <Input
              label="Target (ops/s)"
              type="number"
              value={params.target}
              onChange={(e) => handleParamChange('target', parseInt(e.target.value) || 0)}
              suffix="0=不限"
            />
            <Input
              label="Field Length"
              type="number"
              value={params.fieldlength}
              onChange={(e) => handleParamChange('fieldlength', parseInt(e.target.value) || 100)}
              suffix="bytes"
            />
          </div>
        </div>

        <div className="workload-actions">
          <Button
            variant="secondary"
            size="lg"
            onClick={() => executeTask('load')}
            loading={loading === 'load'}
            disabled={loading !== null}
          >
            📥 Load 数据
          </Button>
          <Button
            variant="primary"
            size="lg"
            onClick={() => executeTask('run')}
            loading={loading === 'run'}
            disabled={loading !== null}
          >
            ▶️ Run 测试
          </Button>
        </div>

        {error && (
          <div className="workload-error">
            ✗ {error}
          </div>
        )}
      </div>
    </Card>
  );
};

